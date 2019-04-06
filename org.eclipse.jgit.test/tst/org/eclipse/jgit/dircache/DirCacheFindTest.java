/*
 * Copyright (C) 2008-2009, Google Inc.
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
