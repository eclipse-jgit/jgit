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
import org.eclipse.jgit.lib.ObjectId;
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
		put(ID, content);

		assertArrayEquals(content, read(ID));
	}

	@Test
	public void putUsesFanOutDirectory() throws IOException {
		put(ID, "x".getBytes(UTF_8));

		File expected = new File(new File(root, "01"),
				"23456789abcdef0123456789abcdef01234567");
		assertTrue(expected.isFile());
		assertEquals(expected, store.fileFor(ID));
	}

	@Test
	public void openMissingObjectThrowsMissingObjectException() {
		assertThrows(MissingObjectException.class, () -> store.open(ID));
	}

	@Test
	public void putSameObjectTwiceIsAllowed() throws IOException {
		byte[] content = "same".getBytes(UTF_8);
		put(ID, content);
		put(ID, content);

		assertArrayEquals(content, read(ID));
	}

	@Test
	public void putWithShortContentFailsAndStoresNothing() {
		byte[] content = "short".getBytes(UTF_8);

		assertThrows(IOException.class, () -> store.put(ID,
				content.length + 1, new ByteArrayInputStream(content)));
		assertThrows(MissingObjectException.class, () -> store.open(ID));
		assertNoTemporaryFiles();
	}

	@Test
	public void putWithLongContentFailsAndStoresNothing() {
		byte[] content = "too long".getBytes(UTF_8);

		assertThrows(IOException.class, () -> store.put(ID,
				content.length - 1, new ByteArrayInputStream(content)));
		assertThrows(MissingObjectException.class, () -> store.open(ID));
		assertNoTemporaryFiles();
	}

	@Test
	public void putEmptyContent() throws IOException {
		put(ID, new byte[0]);

		assertArrayEquals(new byte[0], read(ID));
	}

	@Test
	public void deleteRemovesObject() throws IOException {
		put(ID, "gone".getBytes(UTF_8));

		store.delete(ID);

		assertThrows(MissingObjectException.class, () -> store.open(ID));
	}

	@Test
	public void deleteMissingObjectIsNotAnError() throws IOException {
		store.delete(ID);
		assertFalse(store.fileFor(ID).exists());
	}

	private void put(ObjectId id, byte[] content) throws IOException {
		store.put(id, content.length, new ByteArrayInputStream(content));
	}

	private byte[] read(ObjectId id) throws IOException {
		try (InputStream in = store.open(id)) {
			return in.readAllBytes();
		}
	}

	private void assertNoTemporaryFiles() {
		String[] names = root.list((dir, name) -> name.endsWith(".tmp"));
		assertTrue(names == null || names.length == 0);
	}
}
