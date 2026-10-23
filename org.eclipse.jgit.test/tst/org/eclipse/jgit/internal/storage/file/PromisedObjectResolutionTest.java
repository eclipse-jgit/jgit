/*
 * Copyright (C) 2026 Shawn Hartsock <shartsock@nvidia.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PromisedObjectContent;
import org.eclipse.jgit.lib.PromisedObjectException;
import org.eclipse.jgit.lib.PromisedObjectException.Reason;
import org.eclipse.jgit.lib.PromisedObjectInventory;
import org.eclipse.jgit.lib.PromisedObjectRequest;
import org.eclipse.jgit.lib.PromisedObjectResolver;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Test;

/**
 * Tests for opt-in resolution of promised blobs through ordinary object reads.
 * <p>
 * Each test deletes a loose blob from the fixture repository to simulate a
 * blob offloaded to an external store, registers a resolver with an explicit
 * inventory, and verifies the behavior of the ordinary reader paths.
 */
public class PromisedObjectResolutionTest extends RepositoryTestCase {

	private static final int PAYLOAD_SIZE = 16 * 1024;

	private ObjectDirectory odb;

	private byte[] payload;

	private ObjectId blobId;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		odb = db.getObjectDatabase();
		payload = randomPayload(PAYLOAD_SIZE);
		blobId = createLooseBlob(payload);
		deleteLooseObject(blobId);
	}

	@Test
	public void openResolvesPromisedBlob() throws Exception {
		try (WindowCursor curs = new WindowCursor(odb)) {
			assertThrows(MissingObjectException.class,
					() -> curs.open(blobId, Constants.OBJ_BLOB));
		}

		FixtureResolver resolver = new FixtureResolver(payload);
		register(resolver);

		try (WindowCursor curs = new WindowCursor(odb)) {
			ObjectLoader ldr = curs.open(blobId, Constants.OBJ_BLOB);
			assertEquals(Constants.OBJ_BLOB, ldr.getType());
			assertEquals(payload.length, ldr.getSize());
			assertArrayEquals(payload, ldr.getCachedBytes());
		}
		assertEquals(1, resolver.openCount.get());
		assertTrue(odb.fileFor(blobId).exists());

		// The installed object serves local reads without the resolver.
		FixtureResolver down = new FixtureResolver(payload);
		down.failureReason = Reason.NOT_FOUND;
		register(down);
		try (WindowCursor curs = new WindowCursor(odb)) {
			assertArrayEquals(payload,
					curs.open(blobId, Constants.OBJ_BLOB).getCachedBytes());
			assertTrue(curs.has(blobId));
		}
		assertEquals(0, down.openCount.get());
	}

	@Test
	public void wrongBytesRejectedAndNothingInstalled() throws Exception {
		FixtureResolver resolver = new FixtureResolver(payload);
		resolver.corruptBytes = true;
		register(resolver);

		try (WindowCursor curs = new WindowCursor(odb)) {
			MissingObjectException e = assertThrows(
					MissingObjectException.class,
					() -> curs.open(blobId, Constants.OBJ_BLOB));
			assertTrue(e.getCause() instanceof PromisedObjectException);
			assertEquals(Reason.INTEGRITY,
					((PromisedObjectException) e.getCause()).getReason());
		}
		assertFalse(odb.fileFor(blobId).exists());
		assertEquals(1, resolver.openCount.get());

		// A retried read fetches again instead of caching the failure.
		try (WindowCursor curs = new WindowCursor(odb)) {
			assertThrows(MissingObjectException.class,
					() -> curs.open(blobId, Constants.OBJ_BLOB));
		}
		assertEquals(2, resolver.openCount.get());
	}

	@Test
	public void lengthMismatchRejectedBeforeStreaming() throws Exception {
		FixtureResolver resolver = new FixtureResolver(payload);
		resolver.declaredLength = payload.length + 1;
		register(resolver);

		try (WindowCursor curs = new WindowCursor(odb)) {
			MissingObjectException e = assertThrows(
					MissingObjectException.class,
					() -> curs.open(blobId, Constants.OBJ_BLOB));
			assertTrue(e.getCause() instanceof PromisedObjectException);
			assertEquals(Reason.INTEGRITY,
					((PromisedObjectException) e.getCause()).getReason());
		}
		assertFalse(odb.fileFor(blobId).exists());
	}

	@Test
	public void unknownObjectIsNotResolved() throws Exception {
		FixtureResolver resolver = new FixtureResolver(payload);
		register(resolver);

		ObjectId unknown = ObjectId.zeroId();
		try (WindowCursor curs = new WindowCursor(odb)) {
			assertFalse(curs.has(unknown));
			assertThrows(MissingObjectException.class,
					() -> curs.open(unknown, Constants.OBJ_BLOB));
		}
		assertEquals(0, resolver.openCount.get());
	}

	@Test
	public void concurrentReadsCoalesceToOneFetch() throws Exception {
		FixtureResolver resolver = new FixtureResolver(payload);
		resolver.delayMillis = 100;
		register(resolver);

		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			CountDownLatch start = new CountDownLatch(1);
			CountDownLatch done = new CountDownLatch(threads);
			AtomicLong failures = new AtomicLong();
			for (int i = 0; i < threads; i++) {
				pool.execute(() -> {
					try {
						start.await();
						try (WindowCursor curs = new WindowCursor(odb)) {
							ObjectLoader ldr = curs.open(blobId,
									Constants.OBJ_BLOB);
							if (!Arrays.equals(payload,
									ldr.getCachedBytes())) {
								failures.incrementAndGet();
							}
						}
					} catch (IOException
							| InterruptedException e) {
						failures.incrementAndGet();
					} finally {
						done.countDown();
					}
				});
			}
			start.countDown();
			assertTrue(done.await(30, TimeUnit.SECONDS));
			assertEquals(0, failures.get());
			assertEquals(1, resolver.openCount.get());
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	public void presenceAndSizeAnsweredFromInventory() throws Exception {
		FixtureResolver resolver = new FixtureResolver(payload);
		register(resolver);

		try (WindowCursor curs = new WindowCursor(odb)) {
			assertTrue(curs.has(blobId));
			assertEquals(payload.length,
					curs.getObjectSize(blobId, Constants.OBJ_BLOB));
			assertEquals(payload.length,
					curs.getObjectSize(blobId, ObjectReader.OBJ_ANY));
		}
		assertEquals(0, resolver.openCount.get());
	}

	@Test
	public void resolverFailureSurfacesTypedCause() throws Exception {
		FixtureResolver resolver = new FixtureResolver(payload);
		resolver.failureReason = Reason.AUTHORIZATION;
		register(resolver);

		try (WindowCursor curs = new WindowCursor(odb)) {
			MissingObjectException e = assertThrows(
					MissingObjectException.class,
					() -> curs.open(blobId, Constants.OBJ_BLOB));
			assertTrue(e.getCause() instanceof PromisedObjectException);
			assertEquals(Reason.AUTHORIZATION,
					((PromisedObjectException) e.getCause()).getReason());
		}
		assertFalse(odb.fileFor(blobId).exists());
	}

	@Test
	public void wrongTypeHintIsNotResolved() throws Exception {
		FixtureResolver resolver = new FixtureResolver(payload);
		register(resolver);

		try (WindowCursor curs = new WindowCursor(odb)) {
			assertThrows(MissingObjectException.class,
					() -> curs.open(blobId, Constants.OBJ_TREE));
			assertThrows(MissingObjectException.class,
					() -> curs.open(blobId, Constants.OBJ_COMMIT));
			assertEquals(0, resolver.openCount.get());

			// A read without a type hint resolves and yields the blob.
			ObjectLoader ldr = curs.open(blobId, ObjectReader.OBJ_ANY);
			assertEquals(Constants.OBJ_BLOB, ldr.getType());
			assertArrayEquals(payload, ldr.getCachedBytes());
		}
		assertEquals(1, resolver.openCount.get());
	}

	@Test
	public void deregistrationRestoresMissingObjectBehavior() throws Exception {
		register(new FixtureResolver(payload));
		odb.setPromisedObjects(null, null);

		try (WindowCursor curs = new WindowCursor(odb)) {
			assertThrows(MissingObjectException.class,
					() -> curs.open(blobId, Constants.OBJ_BLOB));
		}
	}

	private void register(FixtureResolver resolver) {
		PromisedObjectInventory inventory = PromisedObjectInventory
				.builder("test").add(blobId, payload.length).build();
		odb.setPromisedObjects(resolver, inventory);
	}

	private ObjectId createLooseBlob(byte[] data) throws IOException {
		try (ObjectDirectoryInserter inserter = odb.newInserter()) {
			ObjectId id = inserter.insert(Constants.OBJ_BLOB, data, 0,
					data.length);
			inserter.flush();
			return id;
		}
	}


	private void deleteLooseObject(ObjectId id) throws IOException {
		File obj = odb.fileFor(id);
		assertTrue("loose object should exist: " + obj, obj.exists());
		FileUtils.delete(obj);
	}

	private static byte[] randomPayload(int size) {
		byte[] data = new byte[size];
		new Random(42).nextBytes(data);
		return data;
	}

	/** Deterministic fixture backend for tests. */
	private static final class FixtureResolver
			implements PromisedObjectResolver {

		final byte[] payload;

		final AtomicLong openCount = new AtomicLong();

		long delayMillis;

		long declaredLength = -1;

		boolean corruptBytes;

		Reason failureReason;

		FixtureResolver(byte[] payload) {
			this.payload = payload;
		}

		@Override
		public PromisedObjectContent open(PromisedObjectRequest request)
				throws PromisedObjectException, IOException {
			openCount.incrementAndGet();
			if (failureReason != null) {
				throw new PromisedObjectException(failureReason,
						"fixture failure: " + failureReason);
			}
			if (delayMillis > 0) {
				try {
					Thread.sleep(delayMillis);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IOException(e);
				}
			}
			byte[] served = corruptBytes ? corrupted(payload) : payload;
			long length = declaredLength >= 0 ? declaredLength
					: served.length;
			return new PromisedObjectContent() {
				@Override
				public long getLength() {
					return length;
				}

				@Override
				public InputStream openStream() {
					return new ByteArrayInputStream(served);
				}

				@Override
				public void close() {
					// Nothing to release.
				}
			};
		}

		private static byte[] corrupted(byte[] in) {
			byte[] out = in.clone();
			out[0] ^= 0x5a;
			return out;
		}
	}
}
