/*
 * Copyright (C) 2008, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
			tw.setFilter(PathFilterGroup
					.createFromStrings(Collections.singleton(paths[expIdx])));

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
