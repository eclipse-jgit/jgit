/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008-2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
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
package org.eclipse.jgit.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.errors.CheckoutConflictException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

public abstract class ReadTreeTest extends RepositoryTestCase {
	protected Tree theHead;
	protected Tree theMerge;

	// Each of these rules are from the read-tree manpage
	// go there to see what they mean.
	// Rule 0 is left out for obvious reasons :)
	@Test
	public void testRules1thru3_NoIndexEntry() throws IOException {
		Tree head = new Tree(db);
		head = buildTree(mk("foo"));
		ObjectId objectId = head.findBlobMember("foo").getId();
		Tree merge = new Tree(db);

		prescanTwoTrees(head, merge);

		assertTrue(getRemoved().contains("foo"));

		prescanTwoTrees(merge, head);

		assertEquals(objectId, getUpdated().get("foo"));

		merge = buildTree(mkmap("foo", "a"));
		ObjectId anotherId = merge.findBlobMember("foo").getId();

		prescanTwoTrees(head, merge);

		assertEquals(anotherId, getUpdated().get("foo"));
	}

	void setupCase(HashMap<String, String> headEntries,
			HashMap<String, String> mergeEntries,
			HashMap<String, String> indexEntries) throws IOException {
		theHead = buildTree(headEntries);
		theMerge = buildTree(mergeEntries);
		buildIndex(indexEntries);
	}

	private void buildIndex(HashMap<String, String> indexEntries) throws IOException {
		GitIndex index = new GitIndex(db);

		if (indexEntries != null) {
			for (java.util.Map.Entry<String,String> e : indexEntries.entrySet()) {
				index.add(trash, writeTrashFile(e.getKey(), e.getValue())).forceRecheck();
			}
		}

		index.write();
		db.getIndex().read();
	}

	private Tree buildTree(HashMap<String, String> headEntries) throws IOException {
		Tree tree = new Tree(db);
		if (headEntries == null)
			return tree;
		FileTreeEntry fileEntry;
		Tree parent;
		ObjectInserter oi = db.newObjectInserter();
		try {
			for (java.util.Map.Entry<String, String> e : headEntries.entrySet()) {
				fileEntry = tree.addFile(e.getKey());
				fileEntry.setId(genSha1(e.getValue()));
				parent = fileEntry.getParent();
				while (parent != null) {
					parent.setId(oi.insert(Constants.OBJ_TREE, parent.format()));
					parent = parent.getParent();
				}
			}
			oi.flush();
		} finally {
			oi.release();
		}
		return tree;
	}

	ObjectId genSha1(String data) {
		ObjectInserter w = db.newObjectInserter();
		try {
			ObjectId id = w.insert(Constants.OBJ_BLOB, data.getBytes());
			w.flush();
			return id;
		} catch (IOException e) {
			fail(e.toString());
		} finally {
			w.release();
		}
		return null;
	}

	protected void go() throws IllegalStateException, IOException {
		prescanTwoTrees(theHead, theMerge);
	}

	// for these rules, they all have clean yes/no options
	// but it doesn't matter if the entry is clean or not
	// so we can just ignore the state in the filesystem entirely
	@Test
	public void testRules4thru13_IndexEntryNotInHead() throws IOException {
		// rules 4 and 5
		HashMap<String, String> idxMap;

		idxMap = new HashMap<String, String>();
		idxMap.put("foo", "foo");
		setupCase(null, null, idxMap);
		go();

		assertTrue(getUpdated().isEmpty());
		assertTrue(getRemoved().isEmpty());
		assertTrue(getConflicts().isEmpty());

		// rules 6 and 7
		idxMap = new HashMap<String, String>();
		idxMap.put("foo", "foo");
		setupCase(null, idxMap, idxMap);
		go();

		assertAllEmpty();

		// rules 8 and 9
		HashMap<String, String> mergeMap;
		mergeMap = new HashMap<String, String>();

		mergeMap.put("foo", "merge");
		setupCase(null, mergeMap, idxMap);
		go();

		assertTrue(getUpdated().isEmpty());
		assertTrue(getRemoved().isEmpty());
		assertTrue(getConflicts().contains("foo"));

		// rule 10

		HashMap<String, String> headMap = new HashMap<String, String>();
		headMap.put("foo", "foo");
		setupCase(headMap, null, idxMap);
		go();

		assertTrue(getRemoved().contains("foo"));
		assertTrue(getUpdated().isEmpty());
		assertTrue(getConflicts().isEmpty());

		// rule 11
		setupCase(headMap, null, idxMap);
		assertTrue(new File(trash, "foo").delete());
		writeTrashFile("foo", "bar");
		db.getIndex().getMembers()[0].forceRecheck();
		go();

		assertTrue(getRemoved().isEmpty());
		assertTrue(getUpdated().isEmpty());
		assertTrue(getConflicts().contains("foo"));

		// rule 12 & 13
		headMap.put("foo", "head");
		setupCase(headMap, null, idxMap);
		go();

		assertTrue(getRemoved().isEmpty());
		assertTrue(getUpdated().isEmpty());
		assertTrue(getConflicts().contains("foo"));

		// rules 14 & 15
		setupCase(headMap, headMap, idxMap);
		go();

		assertAllEmpty();

		// rules 16 & 17
		setupCase(headMap, mergeMap, idxMap); go();
		assertTrue(getConflicts().contains("foo"));

		// rules 18 & 19
		setupCase(headMap, idxMap, idxMap); go();
		assertAllEmpty();

		// rule 20
		setupCase(idxMap, mergeMap, idxMap); go();
		assertTrue(getUpdated().containsKey("foo"));

		// rules 21
		setupCase(idxMap, mergeMap, idxMap);
		assertTrue(new File(trash, "foo").delete());
		writeTrashFile("foo", "bar");
		db.getIndex().getMembers()[0].forceRecheck();
		go();
		assertTrue(getConflicts().contains("foo"));
	}

	private void assertAllEmpty() {
		assertTrue(getRemoved().isEmpty());
		assertTrue(getUpdated().isEmpty());
		assertTrue(getConflicts().isEmpty());
	}

	@Test
	public void testDirectoryFileSimple() throws IOException {
		Tree treeDF = buildTree(mkmap("DF", "DF"));
		Tree treeDFDF = buildTree(mkmap("DF/DF", "DF/DF"));
		buildIndex(mkmap("DF", "DF"));

		prescanTwoTrees(treeDF, treeDFDF);

		assertTrue(getRemoved().contains("DF"));
		assertTrue(getUpdated().containsKey("DF/DF"));

		recursiveDelete(new File(trash, "DF"));
		buildIndex(mkmap("DF/DF", "DF/DF"));

		prescanTwoTrees(treeDFDF, treeDF);
		assertTrue(getRemoved().contains("DF/DF"));
		assertTrue(getUpdated().containsKey("DF"));
	}

	/*
	 * Directory/File Conflict cases:
	 * It's entirely possible that in practice a number of these may be equivalent
	 * to the cases described in git-read-tree.txt. As long as it does the right thing,
	 * that's all I care about. These are basically reverse-engineered from
	 * what git currently does. If there are tests for these in git, it's kind of
	 * hard to track them all down...
	 *
	 *     H        I       M     Clean     H==M     H==I    I==M         Result
	 *     ------------------------------------------------------------------
	 *1    D        D       F       Y         N       Y       N           Update
	 *2    D        D       F       N         N       Y       N           Conflict
	 *3    D        F       D                 Y       N       N           Update
	 *4    D        F       D                 N       N       N           Update
	 *5    D        F       F       Y         N       N       Y           Keep
	 *6    D        F       F       N         N       N       Y           Keep
	 *7    F        D       F       Y         Y       N       N           Update
	 *8    F        D       F       N         Y       N       N           Conflict
	 *9    F        D       F       Y         N       N       N           Update
	 *10   F        D       D                 N       N       Y           Keep
	 *11   F		D		D				  N		  N		  N			  Conflict
	 *12   F		F		D		Y		  N		  Y		  N			  Update
	 *13   F		F		D		N		  N		  Y		  N			  Conflict
	 *14   F		F		D				  N		  N		  N			  Conflict
	 *15   0		F		D				  N		  N		  N			  Conflict
	 *16   0		D		F		Y		  N		  N		  N			  Update
	 *17   0		D		F		 		  N		  N		  N			  Conflict
	 *18   F        0       D    										  Update
	 *19   D	    0       F											  Update
	 */

	@Test
	public void testDirectoryFileConflicts_1() throws Exception {
		// 1
		doit(mk("DF/DF"), mk("DF"), mk("DF/DF"));
		assertNoConflicts();
		assertUpdated("DF");
		assertRemoved("DF/DF");
	}

	@Test
	public void testDirectoryFileConflicts_2() throws Exception {
		// 2
		setupCase(mk("DF/DF"), mk("DF"), mk("DF/DF"));
		writeTrashFile("DF/DF", "different");
		go();
		assertConflict("DF/DF");

	}

	@Test
	public void testDirectoryFileConflicts_3() throws Exception {
		// 3 - the first to break!
		doit(mk("DF/DF"), mk("DF/DF"), mk("DF"));
		assertUpdated("DF/DF");
		assertRemoved("DF");
	}

	@Test
	public void testDirectoryFileConflicts_4() throws Exception {
		// 4 (basically same as 3, just with H and M different)
		doit(mk("DF/DF"), mkmap("DF/DF", "foo"), mk("DF"));
		assertUpdated("DF/DF");
		assertRemoved("DF");

	}

	@Test
	public void testDirectoryFileConflicts_5() throws Exception {
		// 5
		doit(mk("DF/DF"), mk("DF"), mk("DF"));
		assertRemoved("DF/DF");

	}

	@Test
	public void testDirectoryFileConflicts_6() throws Exception {
		// 6
		setupCase(mk("DF/DF"), mk("DF"), mk("DF"));
		writeTrashFile("DF", "different");
		go();
		assertRemoved("DF/DF");
	}

	@Test
	public void testDirectoryFileConflicts_7() throws Exception {
		// 7
		doit(mk("DF"), mk("DF"), mk("DF/DF"));
		assertUpdated("DF");
		assertRemoved("DF/DF");

		cleanUpDF();
		setupCase(mk("DF/DF"), mk("DF/DF"), mk("DF/DF/DF/DF/DF"));
		go();
		assertRemoved("DF/DF/DF/DF/DF");
		assertUpdated("DF/DF");

		cleanUpDF();
		setupCase(mk("DF/DF"), mk("DF/DF"), mk("DF/DF/DF/DF/DF"));
		writeTrashFile("DF/DF/DF/DF/DF", "diff");
		go();
		assertConflict("DF/DF/DF/DF/DF");

		// assertUpdated("DF/DF");
								// Why do we expect an update on DF/DF. H==M,
								// H&M are files and index contains a dir, index
								// is dirty: that case is not in the table but
								// we cannot update DF/DF to a file, this would
								// require that we delete DF/DF/DF/DF/DF in workdir
								// throwing away unsaved contents.
								// This test would fail in DirCacheCheckoutTests.
	}

	@Test
	public void testDirectoryFileConflicts_8() throws Exception {
		// 8
		setupCase(mk("DF"), mk("DF"), mk("DF/DF"));
		recursiveDelete(new File(db.getWorkTree(), "DF"));
		writeTrashFile("DF", "xy");
		go();
		assertConflict("DF/DF");
	}

	@Test
	public void testDirectoryFileConflicts_9() throws Exception {
		// 9
		doit(mk("DF"), mkmap("DF", "QP"), mk("DF/DF"));
		assertRemoved("DF/DF");
		assertUpdated("DF");
	}

	@Test
	public void testDirectoryFileConflicts_10() throws Exception {
		// 10
		cleanUpDF();
		doit(mk("DF"), mk("DF/DF"), mk("DF/DF"));
		assertNoConflicts();
	}

	@Test
	public void testDirectoryFileConflicts_11() throws Exception {
		// 11
		doit(mk("DF"), mk("DF/DF"), mkmap("DF/DF", "asdf"));
		assertConflict("DF/DF");
	}

	@Test
	public void testDirectoryFileConflicts_12() throws Exception {
		// 12
		cleanUpDF();
		doit(mk("DF"), mk("DF/DF"), mk("DF"));
		assertRemoved("DF");
		assertUpdated("DF/DF");
	}

	@Test
	public void testDirectoryFileConflicts_13() throws Exception {
		// 13
		cleanUpDF();
		setupCase(mk("DF"), mk("DF/DF"), mk("DF"));
		writeTrashFile("DF", "asdfsdf");
		go();
		assertConflict("DF");
		assertUpdated("DF/DF");
	}

	@Test
	public void testDirectoryFileConflicts_14() throws Exception {
		// 14
		cleanUpDF();
		doit(mk("DF"), mk("DF/DF"), mkmap("DF", "Foo"));
		assertConflict("DF");
		assertUpdated("DF/DF");
	}

	@Test
	public void testDirectoryFileConflicts_15() throws Exception {
		// 15
		doit(mkmap(), mk("DF/DF"), mk("DF"));

		// This test would fail in DirCacheCheckoutTests. I think this test is wrong,
		// it should check for conflicts according to rule 15
		// assertRemoved("DF");

		assertUpdated("DF/DF");
	}

	@Test
	public void testDirectoryFileConflicts_15b() throws Exception {
		// 15, take 2, just to check multi-leveled
		doit(mkmap(), mk("DF/DF/DF/DF"), mk("DF"));

		// I think this test is wrong, it should
		// check for conflicts according to rule 15
		// This test would fail in DirCacheCheckouts
		// assertRemoved("DF");

		assertUpdated("DF/DF/DF/DF");
	}

	@Test
	public void testDirectoryFileConflicts_16() throws Exception {
		// 16
		cleanUpDF();
		doit(mkmap(), mk("DF"), mk("DF/DF/DF"));
		assertRemoved("DF/DF/DF");
		assertUpdated("DF");
	}

	@Test
	public void testDirectoryFileConflicts_17() throws Exception {
		// 17
		cleanUpDF();
		setupCase(mkmap(), mk("DF"), mk("DF/DF/DF"));
		writeTrashFile("DF/DF/DF", "asdf");
		go();
		assertConflict("DF/DF/DF");

		// Why do we expect an update on DF. If we really update
		// DF and update also the working tree we would have to
		// overwrite a dirty file in the work-tree DF/DF/DF
		// This test would fail in DirCacheCheckout
		// assertUpdated("DF");
	}

	@Test
	public void testDirectoryFileConflicts_18() throws Exception {
		// 18
		cleanUpDF();
		doit(mk("DF/DF"), mk("DF/DF/DF/DF"), null);
		assertRemoved("DF/DF");
		assertUpdated("DF/DF/DF/DF");
	}

	@Test
	public void testDirectoryFileConflicts_19() throws Exception {
		// 19
		cleanUpDF();
		doit(mk("DF/DF/DF/DF"), mk("DF/DF/DF"), null);
		assertRemoved("DF/DF/DF/DF");
		assertUpdated("DF/DF/DF");
	}

	protected void cleanUpDF() throws Exception {
		tearDown();
		setUp();
		recursiveDelete(new File(trash, "DF"));
	}

	protected void assertConflict(String s) {
		assertTrue(getConflicts().contains(s));
	}

	protected void assertUpdated(String s) {
		assertTrue(getUpdated().containsKey(s));
	}

	protected void assertRemoved(String s) {
		assertTrue(getRemoved().contains(s));
	}

	protected void assertNoConflicts() {
		assertTrue(getConflicts().isEmpty());
	}

	protected void doit(HashMap<String, String> h, HashMap<String, String> m,
			HashMap<String, String> i) throws IOException {
		setupCase(h, m, i);
		go();
	}

	protected static HashMap<String, String> mk(String a) {
		return mkmap(a, a);
	}

	protected static HashMap<String, String> mkmap(String... args) {
		if ((args.length % 2) > 0)
			throw new IllegalArgumentException("needs to be pairs");

		HashMap<String, String> map = new HashMap<String, String>();
		for (int i = 0; i < args.length; i += 2) {
			map.put(args[i], args[i+1]);
		}

		return map;
	}

	@Test
	public void testUntrackedConflicts() throws IOException {
		setupCase(null, mk("foo"), null);
		writeTrashFile("foo", "foo");
		go();

		// test that we don't overwrite untracked files when there is a HEAD
		recursiveDelete(new File(trash, "foo"));
		setupCase(mk("other"), mkmap("other", "other", "foo", "foo"),
				mk("other"));
		writeTrashFile("foo", "bar");
		try {
			checkout();
			fail("didn't get the expected exception");
		} catch (CheckoutConflictException e) {
			assertConflict("foo");
			assertWorkDir(mkmap("foo", "bar", "other", "other"));
			assertIndex(mk("other"));
		}

		// test that we don't overwrite untracked files when there is no HEAD
		recursiveDelete(new File(trash, "other"));
		recursiveDelete(new File(trash, "foo"));
		setupCase(null, mk("foo"), null);
		writeTrashFile("foo", "bar");
		try {
			checkout();
			fail("didn't get the expected exception");
		} catch (CheckoutConflictException e) {
			assertConflict("foo");
			assertWorkDir(mkmap("foo", "bar"));
			assertIndex(mkmap());
		}

		// TODO: Why should we expect conflicts here?
		// H and M are emtpy and according to rule #5 of
		// the carry-over rules a dirty index is no reason
		// for a conflict. (I also feel it should be a
		// conflict because we are going to overwrite
		// unsaved content in the working tree
		// This test would fail in DirCacheCheckoutTest
		// assertConflict("foo");

		recursiveDelete(new File(trash, "foo"));
		recursiveDelete(new File(trash, "other"));
		setupCase(null, mk("foo"), null);
		writeTrashFile("foo/bar/baz", "");
		writeTrashFile("foo/blahblah", "");
		go();

		assertConflict("foo");
		assertConflict("foo/bar/baz");
		assertConflict("foo/blahblah");

		recursiveDelete(new File(trash, "foo"));

		setupCase(mkmap("foo/bar", "", "foo/baz", ""),
				mk("foo"), mkmap("foo/bar", "", "foo/baz", ""));
		assertTrue(new File(trash, "foo/bar").exists());
		go();

		assertNoConflicts();
	}

	@Test
	public void testCloseNameConflictsX0() throws IOException {
		setupCase(mkmap("a/a", "a/a-c"), mkmap("a/a","a/a", "b.b/b.b","b.b/b.bs"), mkmap("a/a", "a/a-c") );
		checkout();
		assertIndex(mkmap("a/a", "a/a", "b.b/b.b", "b.b/b.bs"));
		assertWorkDir(mkmap("a/a", "a/a", "b.b/b.b", "b.b/b.bs"));
		go();
		assertIndex(mkmap("a/a", "a/a", "b.b/b.b", "b.b/b.bs"));
		assertWorkDir(mkmap("a/a", "a/a", "b.b/b.b", "b.b/b.bs"));
		assertNoConflicts();
	}

	@Test
	public void testCloseNameConflicts1() throws IOException {
		setupCase(mkmap("a/a", "a/a-c"), mkmap("a/a","a/a", "a.a/a.a","a.a/a.a"), mkmap("a/a", "a/a-c") );
		checkout();
		assertIndex(mkmap("a/a", "a/a", "a.a/a.a", "a.a/a.a"));
		assertWorkDir(mkmap("a/a", "a/a", "a.a/a.a", "a.a/a.a"));
		go();
		assertIndex(mkmap("a/a", "a/a", "a.a/a.a", "a.a/a.a"));
		assertWorkDir(mkmap("a/a", "a/a", "a.a/a.a", "a.a/a.a"));
		assertNoConflicts();
	}

	@Test
	public void testCheckoutHierarchy() throws IOException {
		setupCase(
				mkmap("a", "a", "b/c", "b/c", "d", "d", "e/f", "e/f", "e/g",
						"e/g"),
				mkmap("a", "a2", "b/c", "b/c", "d", "d", "e/f", "e/f", "e/g",
						"e/g2"),
				mkmap("a", "a", "b/c", "b/c", "d", "d", "e/f", "e/f", "e/g",
						"e/g3"));
		try {
			checkout();
		} catch (CheckoutConflictException e) {
			assertWorkDir(mkmap("a", "a", "b/c", "b/c", "d", "d", "e/f",
					"e/f", "e/g", "e/g3"));
			assertConflict("e/g");
		}
	}

	@Test
	public void testCheckoutOutChanges() throws IOException {
		setupCase(mk("foo"), mk("foo/bar"), mk("foo"));
		checkout();
		assertIndex(mk("foo/bar"));
		assertWorkDir(mk("foo/bar"));

		assertFalse(new File(trash, "foo").isFile());
		assertTrue(new File(trash, "foo/bar").isFile());
		recursiveDelete(new File(trash, "foo"));

		assertWorkDir(mkmap());

		setupCase(mk("foo/bar"), mk("foo"), mk("foo/bar"));
		checkout();

		assertIndex(mk("foo"));
		assertWorkDir(mk("foo"));

		assertFalse(new File(trash, "foo/bar").isFile());
		assertTrue(new File(trash, "foo").isFile());

		setupCase(mk("foo"), mkmap("foo", "qux"), mkmap("foo", "bar"));

		assertIndex(mkmap("foo", "bar"));
		assertWorkDir(mkmap("foo", "bar"));

		try {
			checkout();
			fail("did not throw exception");
		} catch (CheckoutConflictException e) {
			assertIndex(mkmap("foo", "bar"));
			assertWorkDir(mkmap("foo", "bar"));
		}
	}

	@Test
	public void testCheckoutUncachedChanges() throws IOException {
		setupCase(mk("foo"), mk("foo"), mk("foo"));
		writeTrashFile("foo", "otherData");
		checkout();
		assertIndex(mk("foo"));
		assertWorkDir(mkmap("foo", "otherData"));
		assertTrue(new File(trash, "foo").isFile());
	}

	@Test
	public void testDontOverwriteDirtyFile() throws IOException {
		setupCase(mk("foo"), mk("other"), mk("foo"));
		writeTrashFile("foo", "different");
		try {
			checkout();
			fail("Didn't got the expected conflict");
		} catch (CheckoutConflictException e) {
			assertIndex(mk("foo"));
			assertWorkDir(mkmap("foo", "different"));
			assertTrue(getConflicts().equals(Arrays.asList("foo")));
			assertTrue(new File(trash, "foo").isFile());
		}
	}

	public void assertWorkDir(HashMap<String, String> i)
			throws CorruptObjectException, IOException {
		TreeWalk walk = new TreeWalk(db);
		walk.setRecursive(true);
		walk.addTree(new FileTreeIterator(db));
		String expectedValue;
		String path;
		int nrFiles = 0;
		FileTreeIterator ft;
		while (walk.next()) {
			ft = walk.getTree(0, FileTreeIterator.class);
			path = ft.getEntryPathString();
			expectedValue = i.get(path);
			assertNotNull("found unexpected file for path "
					+ path + " in workdir", expectedValue);
			File file = new File(db.getWorkTree(), path);
			assertTrue(file.exists());
			if (file.isFile()) {
				FileInputStream is = new FileInputStream(file);
				byte[] buffer = new byte[(int) file.length()];
				int offset = 0;
				int numRead = 0;
				while (offset < buffer.length
						&& (numRead = is.read(buffer, offset, buffer.length
								- offset)) >= 0) {
					offset += numRead;
				}
				is.close();
				assertTrue("unexpected content for path " + path
						+ " in workDir. Expected: <" + expectedValue + ">",
						Arrays.equals(buffer, i.get(path).getBytes()));
				nrFiles++;
			}
		}
		assertEquals("WorkDir has not the right size.", i.size(), nrFiles);
	}


	public void assertIndex(HashMap<String, String> i)
			throws CorruptObjectException, IOException {
		String expectedValue;
		String path;
		GitIndex theIndex=db.getIndex();
		// Without an explicit refresh we might miss index updates. If the index
		// is updated multiple times inside a FileSystemTimer tick db.getIndex will
		// not reload the index and return a cached (stale) index.
		theIndex.read();
		assertEquals("Index has not the right size.", i.size(),
				theIndex.getMembers().length);
		for (int j = 0; j < theIndex.getMembers().length; j++) {
			path = theIndex.getMembers()[j].getName();
			expectedValue = i.get(path);
			assertNotNull("found unexpected entry for path " + path
					+ " in index", expectedValue);
			assertTrue("unexpected content for path " + path
					+ " in index. Expected: <" + expectedValue + ">",
					Arrays.equals(
							db.open(theIndex.getMembers()[j].getObjectId())
									.getCachedBytes(), i.get(path).getBytes()));
		}
	}

	public abstract void prescanTwoTrees(Tree head, Tree merge) throws IllegalStateException, IOException;
	public abstract void checkout() throws IOException;
	public abstract List<String> getRemoved();
	public abstract Map<String, ObjectId> getUpdated();
	public abstract List<String> getConflicts();
}
