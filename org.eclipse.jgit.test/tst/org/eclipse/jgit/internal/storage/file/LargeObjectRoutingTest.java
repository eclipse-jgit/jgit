/*
 * Copyright (C) 2026, NVIDIA Corporation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.ReceivedObjectFilter;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.TestProtocol;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LargeObjectRoutingTest extends LocalDiskRepositoryTestCase {
	private static final String LINK = "s3://bucket/project";

	private static final String BRANCH = "refs/heads/master";

	private static final byte[] BIG = "large blob routed to S3"
			.getBytes(UTF_8);

	private static final ReceivedObjectFilter LARGE_BLOBS = (id, type,
			size) -> type == Constants.OBJ_BLOB && size >= 10;

	private FileRepository server;

	private ObjectDirectory main;

	private ObjectDirectory store;

	private final List<String> inserterCalls = new ArrayList<>();

	private ReceivePack lastReceive;

	private TestProtocol<Object> testProtocol;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		server = createBareRepository();
		main = server.getObjectDatabase();
		store = createBareRepository().getObjectDatabase();
		setOpener((cfg, dir, link) -> new StoreDatabase());
	}

	@Override
	@After
	public void tearDown() throws Exception {
		if (testProtocol != null) {
			Transport.unregister(testProtocol);
		}
		super.tearDown();
	}

	@Test
	public void pushRoutesLargeBlobsToTheListedAlternate() throws Exception {
		writeS3Alternates(LINK);
		try (TestRepository<InMemoryRepository> client = newClient()) {
			RevBlob big = client.blob(BIG);
			RevBlob small = client.blob("small");
			RevCommit commit = client.commit(client.tree(
					client.file("big.bin", big), client.file("small", small)));

			assertEquals(RemoteRefUpdate.Status.OK,
					push(client.getRepository(), commit, false));

			assertEquals(commit, server.resolve(BRANCH));
			assertTrue(store.fileFor(big).isFile());
			assertFalse(store.fileFor(small).exists());
			assertFalse(inMain(big));
			assertTrue(inMain(small));
			assertEquals(List.of("insert", "flush", "close"), inserterCalls);
			try (ObjectReader reader = server.newObjectReader()) {
				assertArrayEquals(BIG, reader.open(big).getCachedBytes());
			}
		}
	}

	@Test
	public void thinPushCanUseRoutedBlobAsDeltaBase() throws Exception {
		writeS3Alternates(LINK);
		byte[] v1 = text("");
		byte[] v2 = text("one more line\n");
		try (TestRepository<InMemoryRepository> client = newClient()) {
			RevCommit first = client
					.commit(client.tree(client.file("big.bin", client.blob(v1))));
			assertEquals(RemoteRefUpdate.Status.OK,
					push(client.getRepository(), first, false));
			RevBlob big2 = client.blob(v2);
			RevCommit second = client
					.commit(client.tree(client.file("big.bin", big2)), first);

			assertEquals(RemoteRefUpdate.Status.OK,
					push(client.getRepository(), second, true));

			assertEquals(1, lastReceive.getReceivedPackStatistics()
					.getNumDeltaBlob());
			assertEquals(second, server.resolve(BRANCH));
			assertTrue(store.fileFor(big2).isFile());
			assertFalse(inMain(big2));
			try (ObjectReader reader = server.newObjectReader()) {
				assertArrayEquals(v2, reader.open(big2).getCachedBytes());
			}
		}
	}

	@Test
	public void withoutLinkRoutingIsRejected() throws Exception {
		IOException e = assertThrows(IOException.class,
				() -> main.newLargeObjectRoutingInserter(LARGE_BLOBS));
		assertTrue(e.getMessage(),
				e.getMessage().contains(S3Alternates.INFO_S3_ALTERNATES));
	}

	@Test
	public void severalLinksAreRejected() throws Exception {
		writeS3Alternates(LINK, "s3://bucket/other");

		IOException e = assertThrows(IOException.class,
				() -> main.newLargeObjectRoutingInserter(LARGE_BLOBS));
		assertTrue(e.getMessage(), e.getMessage().contains("s3://bucket/other"));
	}

	@Test
	public void invalidLineIsRejected() throws Exception {
		writeS3Alternates(LINK, "s3:/bucket/typo");

		IOException e = assertThrows(IOException.class,
				() -> main.newLargeObjectRoutingInserter(LARGE_BLOBS));
		assertTrue(e.getMessage(), e.getMessage().contains("s3:/bucket/typo"));
	}

	@Test
	public void linkWithoutDatabaseIsRejected() throws Exception {
		writeS3Alternates(LINK);
		setOpener((cfg, dir, link) -> null);

		IOException e = assertThrows(IOException.class,
				() -> main.newLargeObjectRoutingInserter(LARGE_BLOBS));
		assertTrue(e.getMessage(), e.getMessage().contains(LINK));
		assertNull(e.getCause());
	}

	@Test
	public void failedOpenIsReportedAsTheCause() throws Exception {
		writeS3Alternates(LINK);
		setOpener((cfg, dir, link) -> {
			throw new IOException("unreachable");
		});

		IOException e = assertThrows(IOException.class,
				() -> main.newLargeObjectRoutingInserter(LARGE_BLOBS));
		assertTrue(e.getMessage(), e.getMessage().contains(LINK));
		assertEquals("unreachable", e.getCause().getMessage());
	}

	private RemoteRefUpdate.Status push(Repository client, RevCommit commit,
			boolean thin) throws Exception {
		if (testProtocol == null) {
			testProtocol = new TestProtocol<>(null,
					(Object req, Repository db) -> {
						ReceivePack rp = new ReceivePack(db) {
							@Override
							protected ObjectInserter newObjectInserter()
									throws IOException {
								return main.newLargeObjectRoutingInserter(
										LARGE_BLOBS);
							}
						};
						rp.setCheckReceivedObjects(true);
						lastReceive = rp;
						return rp;
					});
		}
		URIish uri = testProtocol.register(new Object(), server);
		try (Transport tn = testProtocol.open(uri, client, "server")) {
			tn.setPushThin(thin);
			PushResult result = tn.push(NullProgressMonitor.INSTANCE,
					Collections.singleton(new RemoteRefUpdate(client,
							commit.name(), BRANCH, false, null, null)));
			return result.getRemoteUpdate(BRANCH).getStatus();
		}
	}

	private boolean inMain(AnyObjectId id) throws IOException {
		if (main.fileFor(id).exists()) {
			return true;
		}
		for (Pack pack : main.getPacks()) {
			if (pack.hasObject(id)) {
				return true;
			}
		}
		return false;
	}

	private void setOpener(LargeObjectAlternates.Opener opener) {
		for (LargeObjectAlternates large : main.getLargeObjectAlternates()) {
			if (large instanceof S3Alternates) {
				large.setOpener(opener);
			}
		}
	}

	private void writeS3Alternates(String... lines) throws IOException {
		File file = new File(main.getDirectory(),
				S3Alternates.INFO_S3_ALTERNATES);
		Files.write(file.toPath(),
				(String.join("\n", lines) + "\n").getBytes(UTF_8));
	}

	private static byte[] text(String last) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < 300; i++) {
			b.append("line ").append(i).append(" of a large file\n");
		}
		return b.append(last).toString().getBytes(UTF_8);
	}

	private static TestRepository<InMemoryRepository> newClient()
			throws IOException {
		return new TestRepository<>(
				new InMemoryRepository(new DfsRepositoryDescription("client")));
	}

	/** Writes to the store without being an ObjectDirectory, like S3. */
	private class StoreDatabase extends ObjectDatabase {
		@Override
		public ObjectInserter newInserter() {
			ObjectInserter delegate = store.newInserter();
			return new ObjectInserter.Filter() {
				@Override
				protected ObjectInserter delegate() {
					return delegate;
				}

				@Override
				public ObjectId insert(int type, long length, InputStream in)
						throws IOException {
					inserterCalls.add("insert");
					return delegate.insert(type, length, in);
				}

				@Override
				public void flush() throws IOException {
					inserterCalls.add("flush");
					delegate.flush();
				}

				@Override
				public void close() {
					inserterCalls.add("close");
					delegate.close();
				}
			};
		}

		@Override
		public ObjectReader newReader() {
			return store.newReader();
		}

		@Override
		public boolean has(AnyObjectId id) throws IOException {
			return store.has(id);
		}

		@Override
		public long getApproximateObjectCount() {
			return store.getApproximateObjectCount();
		}

		@Override
		public void close() {
			// The test owns the store.
		}
	}
}
