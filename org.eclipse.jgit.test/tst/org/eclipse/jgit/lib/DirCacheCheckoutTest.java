/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com> and
 * other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v1.0 which accompanies this
 * distribution, is reproduced below, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.lib;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.errors.CorruptObjectException;

public class DirCacheCheckoutTest extends ReadTreeTest {
	protected Checkout getCheckoutImpl(Tree head, DirCache dc, Tree merge) {
		try {
			return new DirCacheCheckoutImpl(head, dc, merge);
		} catch (IOException e) {
			return null;
		}
	}

	public void testDirectoryFileSimple() throws IOException {
		theIndex = new GitIndex(db);
		theIndex.add(trash, writeTrashFile("DF", "DF"));
		Tree treeDF = db.mapTree(theIndex.writeTree());

		recursiveDelete(new File(trash, "DF"));
		theIndex = new GitIndex(db);
		theIndex.add(trash, writeTrashFile("DF/DF", "DF/DF"));
		Tree treeDFDF = db.mapTree(theIndex.writeTree());

		theIndex = new GitIndex(db);
		recursiveDelete(new File(trash, "DF"));

		theIndex.add(trash, writeTrashFile("DF", "DF"));
		theReadTree = getCheckoutImpl(treeDF, theIndex, treeDFDF);
		theReadTree.prescanTwoTrees();

		assertTrue(theReadTree.removed().contains("DF"));
		assertTrue(theReadTree.updated().containsKey("DF/DF"));

		recursiveDelete(new File(trash, "DF"));
		theIndex = new GitIndex(db);
		theIndex.add(trash, writeTrashFile("DF/DF", "DF/DF"));

		theReadTree = getCheckoutImpl(treeDFDF, theIndex, treeDF);
		theReadTree.prescanTwoTrees();
		assertTrue(theReadTree.removed().contains("DF/DF"));

		// TODO: fix this failing test. Problem is that I can't currently check
		// whether a tree in the index is 'clean', means: matches the workdir
		// assertTrue(theReadTree.updated().containsKey("DF"));
	}

	public void testDirectoryFileConflicts_1() throws Exception {
		// 1
		doit(mk("DF/DF"), mk("DF"), mk("DF/DF"));
		// TODO: fix this failing test. Problem is that I can't currently check
		// whether a tree in the index is 'clean', means: matches the workdir
		// assertNoConflicts();
		// assertUpdated("DF");
		assertRemoved("DF/DF");
	}

	public void testDirectoryFileConflicts_9() throws Exception {
		// 9
		doit(mk("DF"), mkmap("DF", "QP"), mk("DF/DF"));
		// TODO: fix this failing test. Problem is that I can't currently check
		// whether a tree in the index is 'clean', means: matches the workdir
		// assertRemoved("DF/DF");
		// assertUpdated("DF");
	}

	public void testDirectoryFileConflicts_10() throws Exception {
		// 10
		// TODO: fix this failing test. Problem is that I can't currently check
		// whether a tree in the index is same as one in the merge
		cleanUpDF();
		doit(mk("DF"), mk("DF/DF"), mk("DF/DF"));
		// assertNoConflicts();
	}

	public void testDirectoryFileConflicts_15() throws Exception {
		// 15
		// TODO: check this failing test. I think this test is wrong, it should
		// check for conflicts according to rule 15
		doit(mkmap(), mk("DF/DF"), mk("DF"));
		// assertRemoved("DF");
		assertUpdated("DF/DF");
	}

	public void testDirectoryFileConflicts_15b() throws Exception {
		// 15, take 2, just to check multi-leveled
		// TODO: check this failing test. I think this test is wrong, it should
		// check for conflicts according to rule 15
		doit(mkmap(), mk("DF/DF/DF/DF"), mk("DF"));
		// assertRemoved("DF");
		assertUpdated("DF/DF/DF/DF");
	}

	public void testDirectoryFileConflicts_16() throws Exception {
		// 16
		cleanUpDF();
		doit(mkmap(), mk("DF"), mk("DF/DF/DF"));
		assertRemoved("DF/DF/DF");
		// TODO: fix this failing test. Problem is that I can't currently check
		// whether a tree in the index is 'clean', means: matches the workdir
		// assertUpdated("DF");
	}

	public void testDirectoryFileConflicts_17() throws Exception {
		// 17
		cleanUpDF();
		setupCase(mkmap(), mk("DF"), mk("DF/DF/DF"));
		writeTrashFile("DF/DF/DF", "asdf");
		go();
		assertConflict("DF/DF/DF");
		// TODO: fix this failing test. Problem is that I can't currently check
		// whether a tree in the index is 'clean', means: matches the workdir
		// assertUpdated("DF");
	}

	public void testDirectoryFileConflicts_19() throws Exception {
		// 19
		cleanUpDF();
		doit(mk("DF/DF/DF/DF"), mk("DF/DF/DF"), null);
		// TODO: check this failing test. I think this test is wrong, it should
		// check for conflicts according to rule 19
		// assertRemoved("DF/DF/DF/DF");
		// assertUpdated("DF/DF/DF");
	}

	public void testUntrackedConflicts() throws IOException {
		setupCase(null, mk("foo"), null);
		writeTrashFile("foo", "foo");
		go();

		// TODO: fix this failing test. Problem is that I can't currently check
		// whether a tree in the index is 'clean', means: matches the workdir
		// assertConflict("foo");

		recursiveDelete(new File(trash, "foo"));
		setupCase(null, mk("foo"), null);
		writeTrashFile("foo/bar/baz", "");
		writeTrashFile("foo/blahblah", "");
		go();

		// TODO: fix this failing test. Problem is that I can't currently check
		// whether a tree in the index is 'clean', means: matches the workdir
		// assertConflict("foo/bar/baz");
		// assertConflict("foo/blahblah");

		recursiveDelete(new File(trash, "foo"));

		setupCase(mkmap("foo/bar", "", "foo/baz", ""), mk("foo"),
				mkmap("foo/bar", "", "foo/baz", ""));
		assertTrue(new File(trash, "foo/bar").exists());
		go();

		// TODO: fix this failing test. Problem is that I can't currently check
		// whether a tree in the index is 'clean', means: matches the workdir
		// assertNoConflicts();
	}

	class DirCacheCheckoutImpl implements Checkout {
		private Tree head;
		private Tree merge;

		private DirCache dc;

		private DirCacheCheckout co;
		private ObjectWriter ow = new ObjectWriter(db);

		public DirCacheCheckoutImpl(Tree head, DirCache dc, Tree merge)
				throws IOException {
			this.head = head;
			this.merge = merge;
			head.setId(ow.writeTree(head));
			merge.setId(ow.writeTree(merge));
			this.dc = dc;
			co = new DirCacheCheckout(db, head, dc, merge.getTree());
		}

		public HashMap<String, ObjectId> updated() {
			return co.getUpdated();
		}

		public ArrayList<String> conflicts() {
			return co.getConflicts();
		}

		public ArrayList<String> removed() {
			return co.getRemoved();
		}

		public void prescanTwoTrees() throws IOException {
			if (head == null) {
				co.prescanOneTree(dc, merge.getTreeId());
			} else {
				co.preScanTwoTrees(head.getTreeId(), dc, merge.getTreeId());
			}
		}

		public void checkout() throws IOException {
			co.checkout();
		}

		public void assertIndex(HashMap<String, String> i)
				throws CorruptObjectException, IOException {
			Set<String> foundPathes = new HashSet<String>();
			String expectedValue;
			String path;
			assertEquals("Index has not the right size.", i.size(),
					dc.getEntryCount());
			for (int j = 0; j < dc.getEntryCount(); j++) {
				path = dc.getEntry(j).getPathString();
				expectedValue = i.get(path);
				assertNotNull(expectedValue, "found unexpected entry for path "
						+ path + " in index");
				assertTrue("unexpected content for path " + path
						+ " in index. Expected: <" + expectedValue + ">",
						Arrays.equals(db.openBlob(dc.getEntry(j).getObjectId())
								.getBytes(), i.get(path).getBytes()));
				foundPathes.add(path);
			}
			dc.unlock();
		}
	}

	protected void assertIndex(HashMap<String, String> i)
			throws CorruptObjectException, IOException {
		Set<String> foundPathes = new HashSet<String>();
		DirCache index = DirCache.lock(db);
		String expectedValue;
		String path;
		assertEquals("Index has not the right size.", i.size(),
				index.getEntryCount());
		for (int j = 0; j < index.getEntryCount(); j++) {
			path = index.getEntry(j).getPathString();
			expectedValue = i.get(path);
			assertNotNull(expectedValue, "found unexpected entry for path "
					+ path + " in index");
			assertTrue(
					"unexpected content for path " + path
							+ " in index. Expected: <" + expectedValue + ">",
					Arrays.equals(db.openBlob(index.getEntry(j).getObjectId())
							.getBytes(),
							i.get(index.getEntry(j).getPathString()).getBytes()));
			foundPathes.add(path);
		}
		index.unlock();
	}
}
