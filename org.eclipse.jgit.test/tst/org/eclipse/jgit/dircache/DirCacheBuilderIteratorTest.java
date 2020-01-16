/*
 * Copyright (C) 2008, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.dircache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.junit.Test;

public class DirCacheBuilderIteratorTest extends RepositoryTestCase {
	@Test
	public void testPathFilterGroup_DoesNotSkipTail() throws Exception {
		final DirCache dc = db.readDirCache();

		final FileMode mode = FileMode.REGULAR_FILE;
		final String[] paths = { "a-", "a/b", "a/c", "a/d", "a0b" };
		final DirCacheEntry[] ents = new DirCacheEntry[paths.length];
		for (int i = 0; i < paths.length; i++) {
			ents[i] = new DirCacheEntry(paths[i]);
			ents[i].setFileMode(mode);
		}
		{
			final DirCacheBuilder b = dc.builder();
			for (DirCacheEntry ent : ents) {
				b.add(ent);
			}
			b.finish();
		}

		final int expIdx = 2;
		final DirCacheBuilder b = dc.builder();
		try (TreeWalk tw = new TreeWalk(db)) {
			tw.addTree(new DirCacheBuildIterator(b));
			tw.setRecursive(true);
			tw.setFilter(PathFilterGroup.createFromStrings(Collections
					.singleton(paths[expIdx])));

			assertTrue("found " + paths[expIdx], tw.next());
			final DirCacheIterator c = tw.getTree(0, DirCacheIterator.class);
			assertNotNull(c);
			assertEquals(expIdx, c.ptr);
			assertSame(ents[expIdx], c.getDirCacheEntry());
			assertEquals(paths[expIdx], tw.getPathString());
			assertEquals(mode.getBits(), tw.getRawMode(0));
			assertSame(mode, tw.getFileMode(0));
			b.add(c.getDirCacheEntry());

			assertFalse("no more entries", tw.next());
		}

		b.finish();
		assertEquals(ents.length, dc.getEntryCount());
		for (int i = 0; i < ents.length; i++)
			assertSame(ents[i], dc.getEntry(i));
	}
}
