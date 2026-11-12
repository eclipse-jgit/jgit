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
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * {@link S3ObjectStore} against a real S3-compatible server.
 * <p>
 * Skipped unless these environment variables are set:
 * <ul>
 * <li>{@code JGIT_S3_TEST_ENDPOINT}, for example {@code http://localhost:9090}
 * for Adobe S3Mock started with
 * {@code docker run -p 9090:9090 adobe/s3mock}</li>
 * <li>{@code JGIT_S3_TEST_BUCKET}, an existing bucket</li>
 * <li>{@code JGIT_S3_TEST_ACCESS_KEY} and {@code JGIT_S3_TEST_SECRET_KEY}</li>
 * <li>optionally {@code JGIT_S3_TEST_REGION}, default {@code us-east-1}</li>
 * </ul>
 * With Bazel, pass them with {@code --test_env}. Every run uses a fresh key
 * prefix and deletes what it stored.
 */
public class S3ObjectStoreS3MockIT {

	private static final byte[] CONTENT = "large content".getBytes(UTF_8);

	private static final ObjectId ID = blobId(CONTENT);

	private S3ObjectStore store;

	private final List<ObjectId> stored = new ArrayList<>();

	@Before
	public void setUp() throws IOException {
		String endpoint = System.getenv("JGIT_S3_TEST_ENDPOINT");
		String bucket = System.getenv("JGIT_S3_TEST_BUCKET");
		String accessKey = System.getenv("JGIT_S3_TEST_ACCESS_KEY");
		String secretKey = System.getenv("JGIT_S3_TEST_SECRET_KEY");
		assumeTrue("JGIT_S3_TEST_* environment variables not set",
				endpoint != null && bucket != null && accessKey != null
						&& secretKey != null);
		String region = System.getenv("JGIT_S3_TEST_REGION");
		store = new S3ObjectStore(bucket,
				region != null ? region : "us-east-1",
				"jgit-it/" + UUID.randomUUID(), URI.create(endpoint).toURL(),
				accessKey, secretKey.toCharArray());
	}

	@After
	public void tearDown() throws IOException {
		if (store != null) {
			for (ObjectId id : stored) {
				store.delete(id);
			}
			store.close();
		}
	}

	@Test
	public void putFindOpenRoundTrip() throws IOException {
		put(ID, CONTENT);

		assertEquals(CONTENT.length, store.find(ID));
		assertArrayEquals(CONTENT, read(ID));
	}

	@Test
	public void putWithContentNotMatchingIdIsRejected() throws IOException {
		ObjectId wrong = blobId("other".getBytes(UTF_8));

		assertThrows(IOException.class, () -> put(wrong, CONTENT));
		assertEquals(-1, store.find(wrong));
	}

	@Test
	public void putWithShortContentStoresNothing() throws IOException {
		stored.add(ID);
		assertThrows(IOException.class, () -> store.put(ID,
				CONTENT.length + 1, new ByteArrayInputStream(CONTENT)));
		assertEquals(-1, store.find(ID));
	}

	@Test
	public void putWithLongContentStoresNothing() throws IOException {
		byte[] declared = Arrays.copyOf(CONTENT, CONTENT.length - 1);
		ObjectId id = blobId(declared);
		stored.add(id);

		assertThrows(IOException.class, () -> store.put(id, declared.length,
				new ByteArrayInputStream(CONTENT)));
		assertEquals(-1, store.find(id));
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
		byte[] large = new byte[8 * 1024 * 1024];
		for (int i = 0; i < large.length; i++) {
			large[i] = (byte) (i * 31 % 251);
		}
		ObjectId id = blobId(large);
		put(id, large);

		assertEquals(large.length, store.find(id));
		assertArrayEquals(large, read(id));
	}

	@Test
	public void missingObjectIsReportedAsMissing() throws IOException {
		assertEquals(-1, store.find(ID));
		assertThrows(MissingObjectException.class, () -> store.open(ID));
	}

	@Test
	public void deleteRemovesObject() throws IOException {
		put(ID, CONTENT);

		store.delete(ID);

		assertEquals(-1, store.find(ID));
		store.delete(ID);
	}

	private void put(ObjectId id, byte[] content) throws IOException {
		stored.add(id);
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
}
