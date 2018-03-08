/*
 * Copyright (C) 2008-2010, Google Inc.
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

import static org.eclipse.jgit.lib.Constants.CHARSET;
import static org.eclipse.jgit.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.IO;
import org.junit.Test;

public class DirCacheCGitCompatabilityTest extends LocalDiskRepositoryTestCase {
	private final File index = pathOf("gitgit.index");

	@Test
	public void testReadIndex_LsFiles() throws Exception {
		final Map<String, CGitIndexRecord> ls = readLsFiles();
		final DirCache dc = new DirCache(index, FS.DETECTED);
		assertEquals(0, dc.getEntryCount());
		dc.read();
		assertEquals(ls.size(), dc.getEntryCount());
		{
			final Iterator<CGitIndexRecord> rItr = ls.values().iterator();
			for (int i = 0; rItr.hasNext(); i++)
				assertEqual(rItr.next(), dc.getEntry(i));
		}
	}

	@Test
	public void testTreeWalk_LsFiles() throws Exception {
		final Repository db = createBareRepository();
		final Map<String, CGitIndexRecord> ls = readLsFiles();
		final DirCache dc = new DirCache(index, db.getFS());
		assertEquals(0, dc.getEntryCount());
		dc.read();
		assertEquals(ls.size(), dc.getEntryCount());
		{
			final Iterator<CGitIndexRecord> rItr = ls.values().iterator();
			try (final TreeWalk tw = new TreeWalk(db)) {
				tw.setRecursive(true);
				tw.addTree(new DirCacheIterator(dc));
				while (rItr.hasNext()) {
					final DirCacheIterator dcItr;

					assertTrue(tw.next());
					dcItr = tw.getTree(0, DirCacheIterator.class);
					assertNotNull(dcItr);

					assertEqual(rItr.next(), dcItr.getDirCacheEntry());
				}
			}
		}
	}

	@Test
	public void testUnsupportedOptionalExtension() throws Exception {
		final DirCache dc = new DirCache(pathOf("gitgit.index.ZZZZ"),
				FS.DETECTED);
		dc.read();
		assertEquals(1, dc.getEntryCount());
		assertEquals("A", dc.getEntry(0).getPathString());
	}

	@Test
	public void testUnsupportedRequiredExtension() throws Exception {
		final DirCache dc = new DirCache(pathOf("gitgit.index.aaaa"),
				FS.DETECTED);
		try {
			dc.read();
			fail("Cache loaded an unsupported extension");
		} catch (CorruptObjectException err) {
			assertEquals("DIRC extension 'aaaa'"
					+ " not supported by this version.", err.getMessage());
		}
	}

	@Test
	public void testCorruptChecksumAtFooter() throws Exception {
		final DirCache dc = new DirCache(pathOf("gitgit.index.badchecksum"),
				FS.DETECTED);
		try {
			dc.read();
			fail("Cache loaded despite corrupt checksum");
		} catch (CorruptObjectException err) {
			assertEquals("DIRC checksum mismatch", err.getMessage());
		}
	}

	private static void assertEqual(final CGitIndexRecord c,
			final DirCacheEntry j) {
		assertNotNull(c);
		assertNotNull(j);

		assertEquals(c.path, j.getPathString());
		assertEquals(c.id, j.getObjectId());
		assertEquals(c.mode, j.getRawMode());
		assertEquals(c.stage, j.getStage());
	}

	@Test
	public void testReadIndex_DirCacheTree() throws Exception {
		final Map<String, CGitIndexRecord> cList = readLsFiles();
		final Map<String, CGitLsTreeRecord> cTree = readLsTree();
		final DirCache dc = new DirCache(index, FS.DETECTED);
		assertEquals(0, dc.getEntryCount());
		dc.read();
		assertEquals(cList.size(), dc.getEntryCount());

		final DirCacheTree jTree = dc.getCacheTree(false);
		assertNotNull(jTree);
		assertEquals("", jTree.getNameString());
		assertEquals("", jTree.getPathString());
		assertTrue(jTree.isValid());
		assertEquals(ObjectId
				.fromString("698dd0b8d0c299f080559a1cffc7fe029479a408"), jTree
				.getObjectId());
		assertEquals(cList.size(), jTree.getEntrySpan());

		final ArrayList<CGitLsTreeRecord> subtrees = new ArrayList<>();
		for (final CGitLsTreeRecord r : cTree.values()) {
			if (FileMode.TREE.equals(r.mode))
				subtrees.add(r);
		}
		assertEquals(subtrees.size(), jTree.getChildCount());

		for (int i = 0; i < jTree.getChildCount(); i++) {
			final DirCacheTree sj = jTree.getChild(i);
			final CGitLsTreeRecord sc = subtrees.get(i);
			assertEquals(sc.path, sj.getNameString());
			assertEquals(sc.path + "/", sj.getPathString());
			assertTrue(sj.isValid());
			assertEquals(sc.id, sj.getObjectId());
		}
	}

	@Test
	public void testReadWriteV3() throws Exception {
		final File file = pathOf("gitgit.index.v3");
		final DirCache dc = new DirCache(file, FS.DETECTED);
		dc.read();

		assertEquals(10, dc.getEntryCount());
		assertV3TreeEntry(0, "dir1/file1.txt", false, false, dc);
		assertV3TreeEntry(1, "dir2/file2.txt", true, false, dc);
		assertV3TreeEntry(2, "dir3/file3.txt", false, false, dc);
		assertV3TreeEntry(3, "dir3/file3a.txt", true, false, dc);
		assertV3TreeEntry(4, "dir4/file4.txt", true, false, dc);
		assertV3TreeEntry(5, "dir4/file4a.txt", false, false, dc);
		assertV3TreeEntry(6, "file.txt", true, false, dc);
		assertV3TreeEntry(7, "newdir1/newfile1.txt", false, true, dc);
		assertV3TreeEntry(8, "newdir1/newfile2.txt", false, true, dc);
		assertV3TreeEntry(9, "newfile.txt", false, true, dc);

		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		dc.writeTo(null, bos);
		final byte[] indexBytes = bos.toByteArray();
		final byte[] expectedBytes = IO.readFully(file);
		assertArrayEquals(expectedBytes, indexBytes);
	}

	private static void assertV3TreeEntry(int indexPosition, String path,
			boolean skipWorkTree, boolean intentToAdd, DirCache dc) {
		final DirCacheEntry entry = dc.getEntry(indexPosition);
		assertEquals(path, entry.getPathString());
		assertEquals(skipWorkTree, entry.isSkipWorkTree());
		assertEquals(intentToAdd, entry.isIntentToAdd());
	}

	private static File pathOf(final String name) {
		return JGitTestUtil.getTestResourceFile(name);
	}

	private static Map<String, CGitIndexRecord> readLsFiles() throws Exception {
		final LinkedHashMap<String, CGitIndexRecord> r = new LinkedHashMap<>();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(
				new FileInputStream(pathOf("gitgit.lsfiles")), CHARSET))) {
			String line;
			while ((line = br.readLine()) != null) {
				final CGitIndexRecord cr = new CGitIndexRecord(line);
				r.put(cr.path, cr);
			}
		}
		return r;
	}

	private static Map<String, CGitLsTreeRecord> readLsTree() throws Exception {
		final LinkedHashMap<String, CGitLsTreeRecord> r = new LinkedHashMap<>();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(
				new FileInputStream(pathOf("gitgit.lstree")), CHARSET))) {
			String line;
			while ((line = br.readLine()) != null) {
				final CGitLsTreeRecord cr = new CGitLsTreeRecord(line);
				r.put(cr.path, cr);
			}
		}
		return r;
	}

	private static class CGitIndexRecord {
		final int mode;

		final ObjectId id;

		final int stage;

		final String path;

		CGitIndexRecord(final String line) {
			final int tab = line.indexOf('\t');
			final int sp1 = line.indexOf(' ');
			final int sp2 = line.indexOf(' ', sp1 + 1);
			mode = Integer.parseInt(line.substring(0, sp1), 8);
			id = ObjectId.fromString(line.substring(sp1 + 1, sp2));
			stage = Integer.parseInt(line.substring(sp2 + 1, tab));
			path = line.substring(tab + 1);
		}
	}

	private static class CGitLsTreeRecord {
		final int mode;

		final ObjectId id;

		final String path;

		CGitLsTreeRecord(final String line) {
			final int tab = line.indexOf('\t');
			final int sp1 = line.indexOf(' ');
			final int sp2 = line.indexOf(' ', sp1 + 1);
			mode = Integer.parseInt(line.substring(0, sp1), 8);
			id = ObjectId.fromString(line.substring(sp2 + 1, tab));
			path = line.substring(tab + 1);
		}
	}
}
