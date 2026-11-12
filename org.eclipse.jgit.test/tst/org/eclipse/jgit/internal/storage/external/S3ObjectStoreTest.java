/*
 * Copyright (C) 2026, Digital.ai and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.external;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Arrays;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class S3ObjectStoreTest {

	private static final String BUCKET = "jgit-test";

	private static final String PREFIX = "repos/foo.git";

	private static final String SECRET = "very-secret-key";

	private static final byte[] CONTENT = "large content".getBytes(UTF_8);

	private static final ObjectId ID = blobId(CONTENT);

	private FakeS3Server server;

	private S3ObjectStore store;

	@Before
	public void setUp() throws IOException {
		server = new FakeS3Server();
		store = newStore(PREFIX);
	}

	@After
	public void tearDown() {
		store.close();
		server.close();
	}

	@Test
	public void putFindOpenRoundTrip() throws IOException {
		put(ID, CONTENT);

		assertEquals(CONTENT.length, store.find(ID));
		assertArrayEquals(CONTENT, read(ID));
	}

	@Test
	public void storedObjectIsBlobInGitFormatKeyedById() throws Exception {
		put(ID, CONTENT);

		byte[] stored = server.object(BUCKET, PREFIX + '/' + ID.name());
		byte[] header = ("blob " + CONTENT.length + "\0").getBytes(UTF_8);
		assertEquals(header.length + CONTENT.length, stored.length);
		for (int i = 0; i < header.length; i++) {
			assertEquals(header[i], stored[i]);
		}
		byte[] sha1 = MessageDigest.getInstance("SHA-1").digest(stored);
		assertEquals(ID, ObjectId.fromRaw(sha1));
	}

	@Test
	public void putSendsChecksumAndUnsignedPayload() throws IOException {
		put(ID, CONTENT);

		assertEquals("UNSIGNED-PAYLOAD",
				server.lastRequestHeaders().getFirst("x-amz-content-sha256"));
		assertTrue(server.lastRequestHeaders()
				.getFirst("x-amz-checksum-sha1") != null);
		assertNull("streamed with a fixed length, not chunked",
				server.lastRequestHeaders().getFirst("Transfer-encoding"));
	}

	@Test
	public void requestsAreSignedForRegionAndService() throws IOException {
		store.find(ID);

		String auth = server.lastRequestHeaders().getFirst("Authorization");
		assertTrue(auth, auth.startsWith(
				"AWS4-HMAC-SHA256 Credential=test-access-key/"));
		assertTrue(auth, auth.contains("/eu-central-1/s3/aws4_request"));
	}

	@Test
	public void putWithContentNotMatchingIdIsRejected() {
		ObjectId wrong = blobId("other".getBytes(UTF_8));

		IOException e = assertThrows(IOException.class,
				() -> put(wrong, CONTENT));
		assertTrue(e.getMessage(), e.getMessage().contains("BadDigest"));
		assertEquals(0, server.objectCount());
	}

	@Test
	public void putWithShortContentFailsAndStoresNothing() throws Exception {
		assertThrows(IOException.class, () -> store.put(ID,
				CONTENT.length + 1, new ByteArrayInputStream(CONTENT)));
		server.awaitFinished(1);
		assertEquals(0, server.objectCount());
	}

	@Test
	public void putWithLongContentFailsAndStoresNothing() throws Exception {
		// The id matches the declared size, so only the size check can
		// stop the upload: S3 would accept the first size bytes.
		byte[] declared = Arrays.copyOf(CONTENT, CONTENT.length - 1);
		ObjectId id = blobId(declared);

		assertThrows(IOException.class, () -> store.put(id, declared.length,
				new ByteArrayInputStream(CONTENT)));
		// No request may reach the server complete; wait for anything it
		// did receive to be handled.
		Thread.sleep(200);
		server.awaitFinished(server.requests("PUT"));
		assertEquals(0, server.objectCount());
	}

	@Test
	public void putEmptyBlob() throws IOException {
		ObjectId empty = blobId(new byte[0]);
		put(empty, new byte[0]);

		assertEquals(0, store.find(empty));
		assertArrayEquals(new byte[0], read(empty));
	}

	@Test
	public void putStreamsLargeBlob() throws IOException {
		int size = 8 * 1024 * 1024;
		ObjectId id;
		try (InputStream in = generated(size)) {
			id = new ObjectInserter.Formatter().idFor(OBJ_BLOB, size, in);
		}
		try (InputStream in = generated(size)) {
			store.put(id, size, in);
		}

		assertEquals(size, store.find(id));
	}

	@Test
	public void findMissingObjectReturnsMinusOne() throws IOException {
		assertEquals(-1, store.find(ID));
	}

	@Test
	public void openMissingObjectThrowsMissingObjectException() {
		assertThrows(MissingObjectException.class, () -> store.open(ID));
	}

	@Test
	public void findRetriesAfterServerError() throws IOException {
		put(ID, CONTENT);
		int before = server.requests("HEAD");
		server.failNext(2);

		assertEquals(CONTENT.length, store.find(ID));
		assertEquals(before + 3, server.requests("HEAD"));
	}

	@Test
	public void openRetriesAfterServerError() throws IOException {
		put(ID, CONTENT);
		server.failNext(1);

		assertArrayEquals(CONTENT, read(ID));
	}

	@Test
	public void findThrowsWhenServerKeepsFailing() {
		server.failNext(3);

		IOException e = assertThrows(IOException.class,
				() -> store.find(ID));
		assertFalse(e instanceof MissingObjectException);
		assertTrue(e.getMessage(), e.getMessage().contains("500"));
	}

	@Test
	public void putIsNotRetried() {
		server.failNext(1);

		assertThrows(IOException.class, () -> put(ID, CONTENT));
		assertEquals(1, server.requests("PUT"));
		assertEquals(0, server.objectCount());
	}

	@Test
	public void deniedRequestFailsWithoutRevealingSecret() {
		server.denyAll(true);

		IOException e = assertThrows(IOException.class,
				() -> put(ID, CONTENT));
		assertTrue(e.getMessage(), e.getMessage().contains("AccessDenied"));
		assertTrue(e.getMessage(), e.getMessage().contains(BUCKET));
		assertFalse(e.getMessage(), e.getMessage().contains(SECRET));

		e = assertThrows(IOException.class, () -> store.find(ID));
		assertFalse(e instanceof MissingObjectException);
		assertTrue(e.getMessage(), e.getMessage().contains("403"));
	}

	@Test
	public void deleteRemovesObject() throws IOException {
		put(ID, CONTENT);

		store.delete(ID);

		assertEquals(-1, store.find(ID));
	}

	@Test
	public void deleteMissingObjectIsNotAnError() throws IOException {
		store.delete(ID);
	}

	@Test
	public void keysUsePrefixWithoutSurroundingSlashes() {
		assertEquals(PREFIX + '/' + ID.name(), store.keyFor(ID));
		assertEquals(PREFIX + '/' + ID.name(),
				newStore("/" + PREFIX + "/").keyFor(ID));
		assertEquals(ID.name(), newStore(null).keyFor(ID));
		assertEquals(ID.name(), newStore("").keyFor(ID));
	}

	@Test
	public void prefixWithUnsafeCharactersIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> newStore("repos/my repo"));
		assertThrows(IllegalArgumentException.class,
				() -> newStore("repos//foo"));
		assertThrows(IllegalArgumentException.class,
				() -> newStore("repos/f%6fo"));
	}

	@Test
	public void urlIsPathStyleWithEndpointAndVirtualHostedOnAws()
			throws IOException {
		assertEquals(server.endpoint() + BUCKET + "/key",
				store.urlFor("key").toExternalForm());

		S3ObjectStore aws = new S3ObjectStore(BUCKET, "eu-central-1", PREFIX,
				null, "test-access-key", SECRET.toCharArray());
		assertEquals("https://jgit-test.s3.eu-central-1.amazonaws.com/key",
				aws.urlFor("key").toExternalForm());

		S3ObjectStore noSlash = new S3ObjectStore(BUCKET, "eu-central-1",
				PREFIX, URI.create("http://localhost:9090").toURL(),
				"test-access-key", SECRET.toCharArray());
		assertEquals("http://localhost:9090/jgit-test/key",
				noSlash.urlFor("key").toExternalForm());
	}

	@Test
	public void blobSizeFromStoredLength() {
		for (long size : new long[] { 0, 1, 9, 10, 99, 100, 12345,
				999_999_999L, 1_000_000_000L, 5_000_000_000L }) {
			long total = "blob ".length() + Long.toString(size).length() + 1
					+ size;
			assertEquals(size, S3ObjectStore.blobSize(total));
		}
		assertEquals(-1, S3ObjectStore.blobSize(6));
	}

	private S3ObjectStore newStore(String prefix) {
		try {
			S3ObjectStore s = new S3ObjectStore(BUCKET, "eu-central-1",
					prefix, server.endpoint(), "test-access-key",
					SECRET.toCharArray());
			s.setRetries(3, 0);
			return s;
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private void put(ObjectId id, byte[] content) throws IOException {
		store.put(id, content.length, new ByteArrayInputStream(content));
	}

	private byte[] read(ObjectId id) throws IOException {
		try (InputStream in = store.open(id)) {
			return in.readAllBytes();
		}
	}

	private static ObjectId blobId(byte[] content) {
		return new ObjectInserter.Formatter().idFor(OBJ_BLOB, content);
	}

	/** Repeatable content of the given size, generated on the fly. */
	private static InputStream generated(long size) {
		return new InputStream() {
			private long pos;

			@Override
			public int read() {
				return pos < size ? (int) (pos++ * 31 % 251) : -1;
			}

			@Override
			public int read(byte[] b, int off, int len) {
				if (pos >= size) {
					return -1;
				}
				int n = (int) Math.min(len, size - pos);
				for (int i = 0; i < n; i++) {
					b[off + i] = (byte) (pos++ * 31 % 251);
				}
				return n;
			}
		};
	}
}
