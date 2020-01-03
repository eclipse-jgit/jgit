/*
 * Copyright (C) 2008-2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.dircache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.junit.Test;

public class DirCacheLargePathTest extends RepositoryTestCase {
	@Test
	public void testPath_4090() throws Exception {
		testLongPath(4090);
	}

	@Test
	public void testPath_4094() throws Exception {
		testLongPath(4094);
	}

	@Test
	public void testPath_4095() throws Exception {
		testLongPath(4095);
	}

	@Test
	public void testPath_4096() throws Exception {
		testLongPath(4096);
	}

	@Test
	public void testPath_16384() throws Exception {
		testLongPath(16384);
	}

	private void testLongPath(int len) throws CorruptObjectException,
			IOException {
		final String longPath = makeLongPath(len);
		final String shortPath = "~~~ shorter-path";

		final DirCacheEntry longEnt = new DirCacheEntry(longPath);
		final DirCacheEntry shortEnt = new DirCacheEntry(shortPath);

		longEnt.setFileMode(FileMode.REGULAR_FILE);
		shortEnt.setFileMode(FileMode.REGULAR_FILE);

		assertEquals(longPath, longEnt.getPathString());
		assertEquals(shortPath, shortEnt.getPathString());

		{
			final DirCache dc1 = db.lockDirCache();
			{
				final DirCacheBuilder b = dc1.builder();
				b.add(longEnt);
				b.add(shortEnt);
				assertTrue(b.commit());
			}
			assertEquals(2, dc1.getEntryCount());
			assertSame(longEnt, dc1.getEntry(0));
			assertSame(shortEnt, dc1.getEntry(1));
		}
		{
			final DirCache dc2 = db.readDirCache();
			assertEquals(2, dc2.getEntryCount());

			assertNotSame(longEnt, dc2.getEntry(0));
			assertEquals(longPath, dc2.getEntry(0).getPathString());

			assertNotSame(shortEnt, dc2.getEntry(1));
			assertEquals(shortPath, dc2.getEntry(1).getPathString());
		}
	}

	private static String makeLongPath(int len) {
		final StringBuilder r = new StringBuilder(len);
		for (int i = 0; i < len; i++)
			r.append('a' + (i % 26));
		return r.toString();
	}
}
