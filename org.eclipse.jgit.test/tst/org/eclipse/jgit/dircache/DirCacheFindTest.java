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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.junit.Test;

public class DirCacheFindTest extends RepositoryTestCase {
	@Test
	public void testEntriesWithin() throws Exception {
		final DirCache dc = db.readDirCache();

		final String[] paths = { "a-", "a/b", "a/c", "a/d", "a0b" };
		final DirCacheEntry[] ents = new DirCacheEntry[paths.length];
		for (int i = 0; i < paths.length; i++) {
			ents[i] = new DirCacheEntry(paths[i]);
			ents[i].setFileMode(FileMode.REGULAR_FILE);
		}
		final int aFirst = 1;
		final int aLast = 3;

		final DirCacheBuilder b = dc.builder();
		for (DirCacheEntry ent : ents) {
			b.add(ent);
		}
		b.finish();

		assertEquals(paths.length, dc.getEntryCount());
		for (int i = 0; i < ents.length; i++)
			assertSame(ents[i], dc.getEntry(i));

		{
			final DirCacheEntry[] aContents = dc.getEntriesWithin("a");
			assertNotNull(aContents);
			assertEquals(aLast - aFirst + 1, aContents.length);
			for (int i = aFirst, j = 0; i <= aLast; i++, j++)
				assertSame(ents[i], aContents[j]);
		}
		{
			final DirCacheEntry[] aContents = dc.getEntriesWithin("a/");
			assertNotNull(aContents);
			assertEquals(aLast - aFirst + 1, aContents.length);
			for (int i = aFirst, j = 0; i <= aLast; i++, j++)
				assertSame(ents[i], aContents[j]);
		}
		{
			final DirCacheEntry[] aContents = dc.getEntriesWithin("");
			assertNotNull(aContents);
			assertEquals(ents.length, aContents.length);
			for (int i = 0; i < ents.length; i++)
				assertSame(ents[i], aContents[i]);
		}

		assertNotNull(dc.getEntriesWithin("a-"));
		assertEquals(0, dc.getEntriesWithin("a-").length);

		assertNotNull(dc.getEntriesWithin("a0b"));
		assertEquals(0, dc.getEntriesWithin("a0b-").length);

		assertNotNull(dc.getEntriesWithin("zoo"));
		assertEquals(0, dc.getEntriesWithin("zoo-").length);
	}
}
