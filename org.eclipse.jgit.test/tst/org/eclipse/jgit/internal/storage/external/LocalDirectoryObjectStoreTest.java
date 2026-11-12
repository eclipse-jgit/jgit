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
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LocalDirectoryObjectStoreTest {

	private static final ObjectId ID = ObjectId
			.fromString("0123456789abcdef0123456789abcdef01234567");

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File root;

	private LocalDirectoryObjectStore store;

	@Before
	public void setUp() {
		root = new File(tmp.getRoot(), "store");
		store = new LocalDirectoryObjectStore(root);
	}

	@Test
	public void putThenOpenReturnsContent() throws IOException {
		byte[] content = "large content".getBytes(UTF_8);
		ObjectId id = put(content);

		assertArrayEquals(content, read(id));
	}

	@Test
	public void putUsesFanOutDirectory() throws IOException {
		ObjectId id = put("x".getBytes(UTF_8));

		String name = id.name();
		File expected = new File(new File(root, name.substring(0, 2)),
				name.substring(2));
		assertTrue(expected.isFile());
		assertEquals(expected, store.fileFor(id));
	}

	@Test
	public void findReturnsSizeOfStoredObject() throws IOException {
		byte[] content = "sized content".getBytes(UTF_8);
		ObjectId id = put(content);

		assertEquals(content.length, store.find(id));
	}

	@Test
	public void findReturnsMinusOneForMissingObject() throws IOException {
		assertEquals(-1, store.find(ID));
	}

	@Test
	public void openMissingObjectThrowsMissingObjectException() {
		assertThrows(MissingObjectException.class, () -> store.open(ID));
	}

	@Test
	public void putSameObjectTwiceIsAllowed() throws IOException {
		byte[] content = "same".getBytes(UTF_8);
		ObjectId id = put(content);
		put(content);

		assertArrayEquals(content, read(id));
	}

	@Test
	public void putWithShortContentFailsAndStoresNothing() throws IOException {
		byte[] content = "short".getBytes(UTF_8);

		assertThrows(IOException.class, () -> store.put(ID,
				content.length + 1, new ByteArrayInputStream(content)));
		assertEquals(-1, store.find(ID));
		assertNoTemporaryFiles();
	}

	@Test
	public void putWithLongContentFailsAndStoresNothing() throws IOException {
		byte[] content = "too long".getBytes(UTF_8);

		assertThrows(IOException.class, () -> store.put(ID,
				content.length - 1, new ByteArrayInputStream(content)));
		assertEquals(-1, store.find(ID));
		assertNoTemporaryFiles();
	}

	@Test
	public void putWithContentNotMatchingIdFailsAndStoresNothing()
			throws IOException {
		ObjectId id = blobId("expected".getBytes(UTF_8));
		byte[] other = "imposter".getBytes(UTF_8);

		IOException e = assertThrows(IOException.class, () -> store.put(id,
				other.length, new ByteArrayInputStream(other)));
		assertTrue(e.getMessage().contains(blobId(other).name()));
		assertEquals(-1, store.find(id));
		assertNoTemporaryFiles();
	}

	@Test
	public void putWithContentNotMatchingIdKeepsStoredObject()
			throws IOException {
		byte[] content = "original".getBytes(UTF_8);
		ObjectId id = put(content);
		byte[] other = "replaced".getBytes(UTF_8);

		assertThrows(IOException.class, () -> store.put(id, other.length,
				new ByteArrayInputStream(other)));
		assertArrayEquals(content, read(id));
	}

	@Test
	public void putEmptyContent() throws IOException {
		ObjectId id = put(new byte[0]);

		assertEquals(0, store.find(id));
		assertArrayEquals(new byte[0], read(id));
	}

	@Test
	public void deleteRemovesObject() throws IOException {
		ObjectId id = put("gone".getBytes(UTF_8));

		store.delete(id);

		assertEquals(-1, store.find(id));
		assertThrows(MissingObjectException.class, () -> store.open(id));
	}

	@Test
	public void deleteMissingObjectIsNotAnError() throws IOException {
		store.delete(ID);
		assertFalse(store.fileFor(ID).exists());
	}

	private ObjectId put(byte[] content) throws IOException {
		ObjectId id = blobId(content);
		store.put(id, content.length, new ByteArrayInputStream(content));
		return id;
	}

	private byte[] read(ObjectId id) throws IOException {
		try (InputStream in = store.open(id)) {
			return in.readAllBytes();
		}
	}

	private static ObjectId blobId(byte[] content) {
		return new ObjectInserter.Formatter().idFor(Constants.OBJ_BLOB,
				content);
	}

	private void assertNoTemporaryFiles() {
		String[] names = root.list((dir, name) -> name.endsWith(".tmp"));
		assertTrue(names == null || names.length == 0);
	}
}
