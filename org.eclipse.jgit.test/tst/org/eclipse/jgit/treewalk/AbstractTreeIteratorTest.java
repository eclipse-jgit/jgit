/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2009, Tor Arne Vestbø <torarnv@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.treewalk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectReader;
import org.junit.Test;


public class AbstractTreeIteratorTest {
	private static String prefix(String path) {
		final int s = path.lastIndexOf('/');
		return s > 0 ? path.substring(0, s) : "";
	}

	public static class FakeTreeIterator extends WorkingTreeIterator {
		public FakeTreeIterator(String pathName, FileMode fileMode) {
			super(prefix(pathName), new Config().get(WorkingTreeOptions.KEY));
			mode = fileMode.getBits();

			final int s = pathName.lastIndexOf('/');
			final byte[] name = Constants.encode(pathName.substring(s + 1));
			ensurePathCapacity(pathOffset + name.length, pathOffset);
			System.arraycopy(name, 0, path, pathOffset, name.length);
			pathLen = pathOffset + name.length;
		}

		@Override
		public AbstractTreeIterator createSubtreeIterator(ObjectReader reader)
				throws IncorrectObjectTypeException, IOException {
			return null;
		}
	}

	@Test
	public void testPathCompare() throws Exception {
		assertTrue(new FakeTreeIterator("a", FileMode.REGULAR_FILE).pathCompare(
				new FakeTreeIterator("a", FileMode.TREE)) < 0);

		assertTrue(new FakeTreeIterator("a", FileMode.TREE).pathCompare(
				new FakeTreeIterator("a", FileMode.REGULAR_FILE)) > 0);

		assertTrue(new FakeTreeIterator("a", FileMode.REGULAR_FILE).pathCompare(
				new FakeTreeIterator("a", FileMode.REGULAR_FILE)) == 0);

		assertTrue(new FakeTreeIterator("a", FileMode.TREE).pathCompare(
				new FakeTreeIterator("a", FileMode.TREE)) == 0);
	}

	@Test
	public void testGrowPath() throws Exception {
		final FakeTreeIterator i = new FakeTreeIterator("ab", FileMode.TREE);
		final byte[] origpath = i.path;
		assertEquals(i.path[0], 'a');
		assertEquals(i.path[1], 'b');

		i.growPath(2);

		assertNotSame(origpath, i.path);
		assertEquals(origpath.length * 2, i.path.length);
		assertEquals(i.path[0], 'a');
		assertEquals(i.path[1], 'b');
	}

	@Test
	public void testEnsurePathCapacityFastCase() throws Exception {
		final FakeTreeIterator i = new FakeTreeIterator("ab", FileMode.TREE);
		final int want = 50;
		final byte[] origpath = i.path;
		assertEquals(i.path[0], 'a');
		assertEquals(i.path[1], 'b');
		assertTrue(want < i.path.length);

		i.ensurePathCapacity(want, 2);

		assertSame(origpath, i.path);
		assertEquals(i.path[0], 'a');
		assertEquals(i.path[1], 'b');
	}

	@Test
	public void testEnsurePathCapacityGrows() throws Exception {
		final FakeTreeIterator i = new FakeTreeIterator("ab", FileMode.TREE);
		final int want = 384;
		final byte[] origpath = i.path;
		assertEquals(i.path[0], 'a');
		assertEquals(i.path[1], 'b');
		assertTrue(i.path.length < want);

		i.ensurePathCapacity(want, 2);

		assertNotSame(origpath, i.path);
		assertEquals(512, i.path.length);
		assertEquals(i.path[0], 'a');
		assertEquals(i.path[1], 'b');
	}

	@Test
	public void testEntryFileMode() {
		for (FileMode m : new FileMode[] { FileMode.TREE,
				FileMode.REGULAR_FILE, FileMode.EXECUTABLE_FILE,
				FileMode.GITLINK, FileMode.SYMLINK }) {
			final FakeTreeIterator i = new FakeTreeIterator("a", m);
			assertEquals(m.getBits(), i.getEntryRawMode());
			assertSame(m, i.getEntryFileMode());
		}
	}

	@Test
	public void testEntryPath() {
		FakeTreeIterator i = new FakeTreeIterator("a/b/cd", FileMode.TREE);
		assertEquals("a/b/cd", i.getEntryPathString());
		assertEquals(2, i.getNameLength());
		byte[] b = new byte[3];
		b[0] = 0x0a;
		i.getName(b, 1);
		assertEquals(0x0a, b[0]);
		assertEquals('c', b[1]);
		assertEquals('d', b[2]);
	}

	@Test
	public void testCreateEmptyTreeIterator() {
		FakeTreeIterator i = new FakeTreeIterator("a/b/cd", FileMode.TREE);
		EmptyTreeIterator e = i.createEmptyTreeIterator();
		assertNotNull(e);
		assertEquals(i.getEntryPathString() + "/", e.getEntryPathString());
	}
}
