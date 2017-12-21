/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008-2011, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2008-2011, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2010-2011, Christian Halstrick <christian.halstrick@sap.com>
 * and other copyright owners as documented in the project's IP log.
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

import static org.junit.Assert.assertArrayEquals;
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

import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CheckoutResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheCheckout.CheckoutMetadata;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.CheckoutConflictException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.events.ChangeRecorder;
import org.eclipse.jgit.events.ListenerHandle;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRepository.BranchBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Assume;
import org.junit.Test;

public class DirCacheCheckoutTest extends RepositoryTestCase {
	private DirCacheCheckout dco;
	protected ObjectId theHead;
	protected ObjectId theMerge;
	private DirCache dirCache;

	private void prescanTwoTrees(ObjectId head, ObjectId merge)
			throws IllegalStateException, IOException {
		DirCache dc = db.lockDirCache();
		try {
			dco = new DirCacheCheckout(db, head, dc, merge);
			dco.preScanTwoTrees();
		} finally {
			dc.unlock();
		}
	}

	private void checkout() throws IOException {
		DirCache dc = db.lockDirCache();
		try {
			dco = new DirCacheCheckout(db, theHead, dc, theMerge);
			dco.checkout();
		} finally {
			dc.unlock();
		}
	}

	private List<String> getRemoved() {
		return dco.getRemoved();
	}

	private Map<String, CheckoutMetadata> getUpdated() {
		return dco.getUpdated();
	}

	private List<String> getConflicts() {
		return dco.getConflicts();
	}

	private static HashMap<String, String> mk(String a) {
		return mkmap(a, a);
	}

	private static HashMap<String, String> mkmap(String... args) {
		if ((args.length % 2) > 0)
			throw new IllegalArgumentException("needs to be pairs");

		HashMap<String, String> map = new HashMap<>();
		for (int i = 0; i < args.length; i += 2) {
			map.put(args[i], args[i + 1]);
		}

		return map;
	}

	@Test
	public void testResetHard() throws IOException, NoFilepatternException,
			GitAPIException {
		ChangeRecorder recorder = new ChangeRecorder();
		ListenerHandle handle = null;
		try (Git git = new Git(db)) {
			handle = db.getListenerList()
					.addWorkingTreeModifiedListener(recorder);
			writeTrashFile("f", "f()");
			writeTrashFile("D/g", "g()");
			git.add().addFilepattern(".").call();
			git.commit().setMessage("inital").call();
			assertIndex(mkmap("f", "f()", "D/g", "g()"));
			recorder.assertNoEvent();
			git.branchCreate().setName("topic").call();
			recorder.assertNoEvent();

			writeTrashFile("f", "f()\nmaster");
			writeTrashFile("D/g", "g()\ng2()");
			writeTrashFile("E/h", "h()");
			git.add().addFilepattern(".").call();
			RevCommit master = git.commit().setMessage("master-1").call();
			assertIndex(mkmap("f", "f()\nmaster", "D/g", "g()\ng2()", "E/h", "h()"));
			recorder.assertNoEvent();

			checkoutBranch("refs/heads/topic");
			assertIndex(mkmap("f", "f()", "D/g", "g()"));
			recorder.assertEvent(new String[] { "f", "D/g" },
					new String[] { "E/h" });

			writeTrashFile("f", "f()\nside");
			assertTrue(new File(db.getWorkTree(), "D/g").delete());
			writeTrashFile("G/i", "i()");
			git.add().addFilepattern(".").call();
			git.add().addFilepattern(".").setUpdate(true).call();
			RevCommit topic = git.commit().setMessage("topic-1").call();
			assertIndex(mkmap("f", "f()\nside", "G/i", "i()"));
			recorder.assertNoEvent();

			writeTrashFile("untracked", "untracked");

			resetHard(master);
			assertIndex(mkmap("f", "f()\nmaster", "D/g", "g()\ng2()", "E/h", "h()"));
			recorder.assertEvent(new String[] { "f", "D/g", "E/h" },
					new String[] { "G", "G/i" });

			resetHard(topic);
			assertIndex(mkmap("f", "f()\nside", "G/i", "i()"));
			assertWorkDir(mkmap("f", "f()\nside", "G/i", "i()", "untracked",
					"untracked"));
			recorder.assertEvent(new String[] { "f", "G/i" },
					new String[] { "D", "D/g", "E", "E/h" });

			assertEquals(MergeStatus.CONFLICTING, git.merge().include(master)
					.call().getMergeStatus());
			assertEquals(
					"[D/g, mode:100644, stage:1][D/g, mode:100644, stage:3][E/h, mode:100644][G/i, mode:100644][f, mode:100644, stage:1][f, mode:100644, stage:2][f, mode:100644, stage:3]",
					indexState(0));
			recorder.assertEvent(new String[] { "f", "D/g", "E/h" },
					ChangeRecorder.EMPTY);

			resetHard(master);
			assertIndex(mkmap("f", "f()\nmaster", "D/g", "g()\ng2()", "E/h", "h()"));
			assertWorkDir(mkmap("f", "f()\nmaster", "D/g", "g()\ng2()", "E/h",
					"h()", "untracked", "untracked"));
			recorder.assertEvent(new String[] { "f", "D/g" },
					new String[] { "G", "G/i" });

		} finally {
			if (handle != null) {
				handle.remove();
			}
		}
	}

	/**
	 * Reset hard from unclean condition.
	 * <p>
	 * WorkDir: Empty <br>
	 * Index: f/g <br>
	 * Merge: x
	 *
	 * @throws Exception
	 */
	@Test
	public void testResetHardFromIndexEntryWithoutFileToTreeWithoutFile()
			throws Exception {
		ChangeRecorder recorder = new ChangeRecorder();
		ListenerHandle handle = null;
		try (Git git = new Git(db)) {
			handle = db.getListenerList()
					.addWorkingTreeModifiedListener(recorder);
			writeTrashFile("x", "x");
			git.add().addFilepattern("x").call();
			RevCommit id1 = git.commit().setMessage("c1").call();

			writeTrashFile("f/g", "f/g");
			git.rm().addFilepattern("x").call();
			recorder.assertEvent(ChangeRecorder.EMPTY, new String[] { "x" });
			git.add().addFilepattern("f/g").call();
			git.commit().setMessage("c2").call();
			deleteTrashFile("f/g");
			deleteTrashFile("f");

			// The actual test
			git.reset().setMode(ResetType.HARD).setRef(id1.getName()).call();
			assertIndex(mkmap("x", "x"));
			recorder.assertEvent(new String[] { "x" }, ChangeRecorder.EMPTY);
		} finally {
			if (handle != null) {
				handle.remove();
			}
		}
	}

	/**
	 * Test first checkout in a repo
	 *
	 * @throws Exception
	 */
	@Test
	public void testInitialCheckout() throws Exception {
		ChangeRecorder recorder = new ChangeRecorder();
		ListenerHandle handle = null;
		try (Git git = new Git(db)) {
			handle = db.getListenerList()
					.addWorkingTreeModifiedListener(recorder);
			TestRepository<Repository> db_t = new TestRepository<>(db);
			BranchBuilder master = db_t.branch("master");
			master.commit().add("f", "1").message("m0").create();
			assertFalse(new File(db.getWorkTree(), "f").exists());
			git.checkout().setName("master").call();
			assertTrue(new File(db.getWorkTree(), "f").exists());
			recorder.assertEvent(new String[] { "f" }, ChangeRecorder.EMPTY);
		} finally {
			if (handle != null) {
				handle.remove();
			}
		}
	}

	private DirCacheCheckout resetHard(RevCommit commit)
			throws NoWorkTreeException,
			CorruptObjectException, IOException {
		DirCacheCheckout dc;
		dc = new DirCacheCheckout(db, null, db.lockDirCache(),
				commit.getTree());
		dc.setFailOnConflict(true);
		assertTrue(dc.checkout());
		return dc;
	}

	private void assertIndex(HashMap<String, String> i)
			throws CorruptObjectException, IOException {
		String expectedValue;
		String path;
		DirCache read = DirCache.read(db.getIndexFile(), db.getFS());

		assertEquals("Index has not the right size.", i.size(),
				read.getEntryCount());
		for (int j = 0; j < read.getEntryCount(); j++) {
			path = read.getEntry(j).getPathString();
			expectedValue = i.get(path);
			assertNotNull("found unexpected entry for path " + path
					+ " in index", expectedValue);
			assertTrue("unexpected content for path " + path
					+ " in index. Expected: <" + expectedValue + ">",
					Arrays.equals(db.open(read.getEntry(j).getObjectId())
							.getCachedBytes(), i.get(path).getBytes()));
		}
	}

	@Test
	public void testRules1thru3_NoIndexEntry() throws IOException {
		ObjectId head = buildTree(mk("foo"));
		ObjectId merge = db.newObjectInserter().insert(Constants.OBJ_TREE,
				new byte[0]);

		prescanTwoTrees(head, merge);

		assertTrue(getRemoved().contains("foo"));

		prescanTwoTrees(merge, head);

		assertTrue(getUpdated().containsKey("foo"));

		merge = buildTree(mkmap("foo", "a"));

		prescanTwoTrees(head, merge);

		assertConflict("foo");
	}

	void setupCase(HashMap<String, String> headEntries, HashMap<String, String> mergeEntries, HashMap<String, String> indexEntries) throws IOException {
		theHead = buildTree(headEntries);
		theMerge = buildTree(mergeEntries);
		buildIndex(indexEntries);
	}

	private void buildIndex(HashMap<String, String> indexEntries) throws IOException {
		dirCache = new DirCache(db.getIndexFile(), db.getFS());
		if (indexEntries != null) {
			assertTrue(dirCache.lock());
			DirCacheEditor editor = dirCache.editor();
			for (java.util.Map.Entry<String,String> e : indexEntries.entrySet()) {
				writeTrashFile(e.getKey(), e.getValue());
				ObjectInserter inserter = db.newObjectInserter();
				final ObjectId id = inserter.insert(Constants.OBJ_BLOB,
						Constants.encode(e.getValue()));
				editor.add(new DirCacheEditor.DeletePath(e.getKey()));
				editor.add(new DirCacheEditor.PathEdit(e.getKey()) {
					@Override
					public void apply(DirCacheEntry ent) {
						ent.setFileMode(FileMode.REGULAR_FILE);
						ent.setObjectId(id);
						ent.setUpdateNeeded(false);
					}
				});
			}
			assertTrue(editor.commit());
		}

	}

	static final class AddEdit extends PathEdit {

		private final ObjectId data;

		private final long length;

		public AddEdit(String entryPath, ObjectId data, long length) {
			super(entryPath);
			this.data = data;
			this.length = length;
		}

		@Override
		public void apply(DirCacheEntry ent) {
			ent.setFileMode(FileMode.REGULAR_FILE);
			ent.setLength(length);
			ent.setObjectId(data);
		}

	}

	private ObjectId buildTree(HashMap<String, String> headEntries)
			throws IOException {
		DirCache lockDirCache = DirCache.newInCore();
		// assertTrue(lockDirCache.lock());
		DirCacheEditor editor = lockDirCache.editor();
		if (headEntries != null) {
			for (java.util.Map.Entry<String, String> e : headEntries.entrySet()) {
				AddEdit addEdit = new AddEdit(e.getKey(),
						genSha1(e.getValue()), e.getValue().length());
				editor.add(addEdit);
			}
		}
		editor.finish();
		return lockDirCache.writeTree(db.newObjectInserter());
	}

	ObjectId genSha1(String data) {
		try (ObjectInserter w = db.newObjectInserter()) {
			ObjectId id = w.insert(Constants.OBJ_BLOB, data.getBytes());
			w.flush();
			return id;
		} catch (IOException e) {
			fail(e.toString());
		}
		return null;
	}

	protected void go() throws IllegalStateException, IOException {
		prescanTwoTrees(theHead, theMerge);
	}

	@Test
	public void testRules4thru13_IndexEntryNotInHead() throws IOException {
		// rules 4 and 5
		HashMap<String, String> idxMap;

		idxMap = new HashMap<>();
		idxMap.put("foo", "foo");
		setupCase(null, null, idxMap);
		go();

		assertTrue(getUpdated().isEmpty());
		assertTrue(getRemoved().isEmpty());
		assertTrue(getConflicts().isEmpty());

		// rules 6 and 7
		idxMap = new HashMap<>();
		idxMap.put("foo", "foo");
		setupCase(null, idxMap, idxMap);
		go();

		assertAllEmpty();

		// rules 8 and 9
		HashMap<String, String> mergeMap;
		mergeMap = new HashMap<>();

		mergeMap.put("foo", "merge");
		setupCase(null, mergeMap, idxMap);
		go();

		assertTrue(getUpdated().isEmpty());
		assertTrue(getRemoved().isEmpty());
		assertTrue(getConflicts().contains("foo"));

		// rule 10

		HashMap<String, String> headMap = new HashMap<>();
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
		db.readDirCache().getEntry(0).setUpdateNeeded(true);
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
		db.readDirCache().getEntry(0).setUpdateNeeded(true);
		go();
		assertTrue(getConflicts().contains("foo"));
	}

	private void assertAllEmpty() {
		assertTrue(getRemoved().isEmpty());
		assertTrue(getUpdated().isEmpty());
		assertTrue(getConflicts().isEmpty());
	}

	/*-
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
	 *3    D        F       D                 Y       N       N           Keep
	 *4    D        F       D                 N       N       N           Conflict
	 *5    D        F       F       Y         N       N       Y           Keep
	 *5b   D        F       F       Y         N       N       N           Conflict
	 *6    D        F       F       N         N       N       Y           Keep
	 *6b   D        F       F       N         N       N       N           Conflict
	 *7    F        D       F       Y         Y       N       N           Update
	 *8    F        D       F       N         Y       N       N           Conflict
	 *9    F        D       F       Y         N       N       N           Update
	 *10   F        D       D                 N       N       Y           Keep
	 *11   F        D       D                 N       N       N           Conflict
	 *12   F        F       D       Y         N       Y       N           Update
	 *13   F        F       D       N         N       Y       N           Conflict
	 *14   F        F       D                 N       N       N           Conflict
	 *15   0        F       D                 N       N       N           Conflict
	 *16   0        D       F       Y         N       N       N           Update
	 *17   0        D       F                 N       N       N           Conflict
	 *18   F        0       D                                             Update
	 *19   D        0       F                                             Update
	 */
	@Test
	public void testDirectoryFileSimple() throws IOException {
		ObjectId treeDF = buildTree(mkmap("DF", "DF"));
		ObjectId treeDFDF = buildTree(mkmap("DF/DF", "DF/DF"));
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
		// 3
		doit(mk("DF/DF"), mk("DF/DF"), mk("DF"));
		assertNoConflicts();
	}

	@Test
	public void testDirectoryFileConflicts_4() throws Exception {
		// 4 (basically same as 3, just with H and M different)
		doit(mk("DF/DF"), mkmap("DF/DF", "foo"), mk("DF"));
		assertConflict("DF/DF");

	}

	@Test
	public void testDirectoryFileConflicts_5() throws Exception {
		// 5
		doit(mk("DF/DF"), mk("DF"), mk("DF"));
		assertRemoved("DF/DF");
		assertEquals(0, dco.getConflicts().size());
		assertEquals(0, dco.getUpdated().size());
	}

	@Test
	public void testDirectoryFileConflicts_5b() throws Exception {
		// 5
		doit(mk("DF/DF"), mkmap("DF", "different"), mk("DF"));
		assertRemoved("DF/DF");
		assertConflict("DF");
		assertEquals(0, dco.getUpdated().size());
	}

	@Test
	public void testDirectoryFileConflicts_6() throws Exception {
		// 6
		setupCase(mk("DF/DF"), mk("DF"), mk("DF"));
		writeTrashFile("DF", "different");
		go();
		assertRemoved("DF/DF");
		assertEquals(0, dco.getConflicts().size());
		assertEquals(0, dco.getUpdated().size());
	}

	@Test
	public void testDirectoryFileConflicts_6b() throws Exception {
		// 6
		setupCase(mk("DF/DF"), mk("DF"), mkmap("DF", "different"));
		writeTrashFile("DF", "again different");
		go();
		assertRemoved("DF/DF");
		assertConflict("DF");
		assertEquals(0, dco.getUpdated().size());
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
		doit(mkmap("DF", "QP"), mkmap("DF", "QP"), mkmap("DF/DF", "DF/DF"));
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

	protected void doit(HashMap<String, String> h, HashMap<String, String> m, HashMap<String, String> i)
			throws IOException {
				setupCase(h, m, i);
				go();
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
			assertEquals("foo", e.getConflictingFiles()[0]);
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
			assertIndex(mkmap("other", "other"));
		}

		// TODO: Why should we expect conflicts here?
		// H and M are empty and according to rule #5 of
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
			assertEquals("e/g", e.getConflictingFiles()[0]);
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
	public void testCheckoutChangeLinkToEmptyDir() throws Exception {
		Assume.assumeTrue(FS.DETECTED.supportsSymlinks());
		String fname = "was_file";
		ChangeRecorder recorder = new ChangeRecorder();
		ListenerHandle handle = null;
		try (Git git = new Git(db)) {
			handle = db.getListenerList()
					.addWorkingTreeModifiedListener(recorder);
			// Add a file
			writeTrashFile(fname, "a");
			git.add().addFilepattern(fname).call();

			// Add a link to file
			String linkName = "link";
			File link = writeLink(linkName, fname).toFile();
			git.add().addFilepattern(linkName).call();
			git.commit().setMessage("Added file and link").call();

			assertWorkDir(mkmap(linkName, "a", fname, "a"));

			// replace link with empty directory
			FileUtils.delete(link);
			FileUtils.mkdir(link);
			assertTrue("Link must be a directory now", link.isDirectory());

			// modify file
			writeTrashFile(fname, "b");
			assertWorkDir(mkmap(fname, "b", linkName, "/"));
			recorder.assertNoEvent();

			// revert both paths to HEAD state
			git.checkout().setStartPoint(Constants.HEAD).addPath(fname)
					.addPath(linkName).call();

			assertWorkDir(mkmap(fname, "a", linkName, "a"));
			recorder.assertEvent(new String[] { fname, linkName },
					ChangeRecorder.EMPTY);

			Status st = git.status().call();
			assertTrue(st.isClean());
		} finally {
			if (handle != null) {
				handle.remove();
			}
		}
	}

	@Test
	public void testCheckoutChangeLinkToEmptyDirs() throws Exception {
		Assume.assumeTrue(FS.DETECTED.supportsSymlinks());
		String fname = "was_file";
		ChangeRecorder recorder = new ChangeRecorder();
		ListenerHandle handle = null;
		try (Git git = new Git(db)) {
			handle = db.getListenerList()
					.addWorkingTreeModifiedListener(recorder);
			// Add a file
			writeTrashFile(fname, "a");
			git.add().addFilepattern(fname).call();

			// Add a link to file
			String linkName = "link";
			File link = writeLink(linkName, fname).toFile();
			git.add().addFilepattern(linkName).call();
			git.commit().setMessage("Added file and link").call();

			assertWorkDir(mkmap(linkName, "a", fname, "a"));

			// replace link with directory containing only directories, no files
			FileUtils.delete(link);
			FileUtils.mkdirs(new File(link, "dummyDir"));
			assertTrue("Link must be a directory now", link.isDirectory());

			assertFalse("Must not delete non empty directory", link.delete());

			// modify file
			writeTrashFile(fname, "b");
			assertWorkDir(mkmap(fname, "b", linkName + "/dummyDir", "/"));
			recorder.assertNoEvent();

			// revert both paths to HEAD state
			git.checkout().setStartPoint(Constants.HEAD).addPath(fname)
					.addPath(linkName).call();

			assertWorkDir(mkmap(fname, "a", linkName, "a"));
			recorder.assertEvent(new String[] { fname, linkName },
					ChangeRecorder.EMPTY);

			Status st = git.status().call();
			assertTrue(st.isClean());
		} finally {
			if (handle != null) {
				handle.remove();
			}
		}
	}

	@Test
	public void testCheckoutChangeLinkToNonEmptyDirs() throws Exception {
		Assume.assumeTrue(FS.DETECTED.supportsSymlinks());
		String fname = "file";
		ChangeRecorder recorder = new ChangeRecorder();
		ListenerHandle handle = null;
		try (Git git = new Git(db)) {
			handle = db.getListenerList()
					.addWorkingTreeModifiedListener(recorder);
			// Add a file
			writeTrashFile(fname, "a");
			git.add().addFilepattern(fname).call();

			// Add a link to file
			String linkName = "link";
			File link = writeLink(linkName, fname).toFile();
			git.add().addFilepattern(linkName).call();
			git.commit().setMessage("Added file and link").call();

			assertWorkDir(mkmap(linkName, "a", fname, "a"));

			// replace link with directory containing only directories, no files
			FileUtils.delete(link);

			// create but do not add a file in the new directory to the index
			writeTrashFile(linkName + "/dir1", "file1", "c");

			// create but do not add a file in the new directory to the index
			writeTrashFile(linkName + "/dir2", "file2", "d");

			assertTrue("File must be a directory now", link.isDirectory());
			assertFalse("Must not delete non empty directory", link.delete());

			// 2 extra files are created
			assertWorkDir(mkmap(fname, "a", linkName + "/dir1/file1", "c",
					linkName + "/dir2/file2", "d"));
			recorder.assertNoEvent();

			// revert path to HEAD state
			git.checkout().setStartPoint(Constants.HEAD).addPath(linkName)
					.call();

			// expect only the one added to the index
			assertWorkDir(mkmap(linkName, "a", fname, "a"));
			recorder.assertEvent(new String[] { linkName },
					ChangeRecorder.EMPTY);

			Status st = git.status().call();
			assertTrue(st.isClean());
		} finally {
			if (handle != null) {
				handle.remove();
			}
		}
	}

	@Test
	public void testCheckoutChangeLinkToNonEmptyDirsAndNewIndexEntry()
			throws Exception {
		Assume.assumeTrue(FS.DETECTED.supportsSymlinks());
		String fname = "file";
		ChangeRecorder recorder = new ChangeRecorder();
		ListenerHandle handle = null;
		try (Git git = new Git(db)) {
			handle = db.getListenerList()
					.addWorkingTreeModifiedListener(recorder);
			// Add a file
			writeTrashFile(fname, "a");
			git.add().addFilepattern(fname).call();

			// Add a link to file
			String linkName = "link";
			File link = writeLink(linkName, fname).toFile();
			git.add().addFilepattern(linkName).call();
			git.commit().setMessage("Added file and link").call();

			assertWorkDir(mkmap(linkName, "a", fname, "a"));

			// replace link with directory containing only directories, no files
			FileUtils.delete(link);

			// create and add a file in the new directory to the index
			writeTrashFile(linkName + "/dir1", "file1", "c");
			git.add().addFilepattern(linkName + "/dir1/file1").call();

			// create but do not add a file in the new directory to the index
			writeTrashFile(linkName + "/dir2", "file2", "d");

			assertTrue("File must be a directory now", link.isDirectory());
			assertFalse("Must not delete non empty directory", link.delete());

			// 2 extra files are created
			assertWorkDir(mkmap(fname, "a", linkName + "/dir1/file1", "c",
					linkName + "/dir2/file2", "d"));
			recorder.assertNoEvent();

			// revert path to HEAD state
			git.checkout().setStartPoint(Constants.HEAD).addPath(linkName)
					.call();

			// original file and link
			assertWorkDir(mkmap(linkName, "a", fname, "a"));
			recorder.assertEvent(new String[] { linkName },
					ChangeRecorder.EMPTY);

			Status st = git.status().call();
			assertTrue(st.isClean());
		} finally {
			if (handle != null) {
				handle.remove();
			}
		}
	}

	@Test
	public void testCheckoutChangeFileToEmptyDir() throws Exception {
		String fname = "was_file";
		ChangeRecorder recorder = new ChangeRecorder();
		ListenerHandle handle = null;
		try (Git git = new Git(db)) {
			handle = db.getListenerList()
					.addWorkingTreeModifiedListener(recorder);
			// Add a file
			File file = writeTrashFile(fname, "a");
			git.add().addFilepattern(fname).call();
			git.commit().setMessage("Added file").call();

			// replace file with empty directory
			FileUtils.delete(file);
			FileUtils.mkdir(file);
			assertTrue("File must be a directory now", file.isDirectory());
			assertWorkDir(mkmap(fname, "/"));
			recorder.assertNoEvent();

			// revert path to HEAD state
			git.checkout().setStartPoint(Constants.HEAD).addPath(fname).call();
			assertWorkDir(mkmap(fname, "a"));
			recorder.assertEvent(new String[] { fname }, ChangeRecorder.EMPTY);

			Status st = git.status().call();
			assertTrue(st.isClean());
		} finally {
			if (handle != null) {
				handle.remove();
			}
		}
	}

	@Test
	public void testCheckoutChangeFileToEmptyDirs() throws Exception {
		String fname = "was_file";
		ChangeRecorder recorder = new ChangeRecorder();
		ListenerHandle handle = null;
		try (Git git = new Git(db)) {
			handle = db.getListenerList()
					.addWorkingTreeModifiedListener(recorder);
			// Add a file
			File file = writeTrashFile(fname, "a");
			git.add().addFilepattern(fname).call();
			git.commit().setMessage("Added file").call();

			// replace file with directory containing only directories, no files
			FileUtils.delete(file);
			FileUtils.mkdirs(new File(file, "dummyDir"));
			assertTrue("File must be a directory now", file.isDirectory());
			assertFalse("Must not delete non empty directory", file.delete());

			assertWorkDir(mkmap(fname + "/dummyDir", "/"));
			recorder.assertNoEvent();

			// revert path to HEAD state
			git.checkout().setStartPoint(Constants.HEAD).addPath(fname).call();
			assertWorkDir(mkmap(fname, "a"));
			recorder.assertEvent(new String[] { fname }, ChangeRecorder.EMPTY);

			Status st = git.status().call();
			assertTrue(st.isClean());
		} finally {
			if (handle != null) {
				handle.remove();
			}
		}
	}

	@Test
	public void testCheckoutChangeFileToNonEmptyDirs() throws Exception {
		String fname = "was_file";
		ChangeRecorder recorder = new ChangeRecorder();
		ListenerHandle handle = null;
		try (Git git = new Git(db)) {
			handle = db.getListenerList()
					.addWorkingTreeModifiedListener(recorder);
			// Add a file
			File file = writeTrashFile(fname, "a");
			git.add().addFilepattern(fname).call();
			git.commit().setMessage("Added file").call();

			assertWorkDir(mkmap(fname, "a"));

			// replace file with directory containing only directories, no files
			FileUtils.delete(file);

			// create but do not add a file in the new directory to the index
			writeTrashFile(fname + "/dir1", "file1", "c");

			// create but do not add a file in the new directory to the index
			writeTrashFile(fname + "/dir2", "file2", "d");

			assertTrue("File must be a directory now", file.isDirectory());
			assertFalse("Must not delete non empty directory", file.delete());

			// 2 extra files are created
			assertWorkDir(mkmap(fname + "/dir1/file1", "c",
					fname + "/dir2/file2", "d"));
			recorder.assertNoEvent();

			// revert path to HEAD state
			git.checkout().setStartPoint(Constants.HEAD).addPath(fname).call();

			// expect only the one added to the index
			assertWorkDir(mkmap(fname, "a"));
			recorder.assertEvent(new String[] { fname }, ChangeRecorder.EMPTY);

			Status st = git.status().call();
			assertTrue(st.isClean());
		} finally {
			if (handle != null) {
				handle.remove();
			}
		}
	}

	@Test
	public void testCheckoutChangeFileToNonEmptyDirsAndNewIndexEntry()
			throws Exception {
		String fname = "was_file";
		ChangeRecorder recorder = new ChangeRecorder();
		ListenerHandle handle = null;
		try (Git git = new Git(db)) {
			handle = db.getListenerList()
					.addWorkingTreeModifiedListener(recorder);
			// Add a file
			File file = writeTrashFile(fname, "a");
			git.add().addFilepattern(fname).call();
			git.commit().setMessage("Added file").call();

			assertWorkDir(mkmap(fname, "a"));

			// replace file with directory containing only directories, no files
			FileUtils.delete(file);

			// create and add a file in the new directory to the index
			writeTrashFile(fname + "/dir", "file1", "c");
			git.add().addFilepattern(fname + "/dir/file1").call();

			// create but do not add a file in the new directory to the index
			writeTrashFile(fname + "/dir", "file2", "d");

			assertTrue("File must be a directory now", file.isDirectory());
			assertFalse("Must not delete non empty directory", file.delete());

			// 2 extra files are created
			assertWorkDir(mkmap(fname + "/dir/file1", "c", fname + "/dir/file2",
					"d"));
			recorder.assertNoEvent();

			// revert path to HEAD state
			git.checkout().setStartPoint(Constants.HEAD).addPath(fname).call();
			assertWorkDir(mkmap(fname, "a"));
			recorder.assertEvent(new String[] { fname }, ChangeRecorder.EMPTY);
			Status st = git.status().call();
			assertTrue(st.isClean());
		} finally {
			if (handle != null) {
				handle.remove();
			}
		}
	}

	@Test
	public void testCheckoutOutChangesAutoCRLFfalse() throws IOException {
		setupCase(mk("foo"), mkmap("foo/bar", "foo\nbar"), mk("foo"));
		checkout();
		assertIndex(mkmap("foo/bar", "foo\nbar"));
		assertWorkDir(mkmap("foo/bar", "foo\nbar"));
	}

	@Test
	public void testCheckoutOutChangesAutoCRLFInput() throws IOException {
		setupCase(mk("foo"), mkmap("foo/bar", "foo\nbar"), mk("foo"));
		db.getConfig().setString("core", null, "autocrlf", "input");
		checkout();
		assertIndex(mkmap("foo/bar", "foo\nbar"));
		assertWorkDir(mkmap("foo/bar", "foo\nbar"));
	}

	@Test
	public void testCheckoutOutChangesAutoCRLFtrue() throws IOException {
		setupCase(mk("foo"), mkmap("foo/bar", "foo\nbar"), mk("foo"));
		db.getConfig().setString("core", null, "autocrlf", "true");
		checkout();
		assertIndex(mkmap("foo/bar", "foo\nbar"));
		assertWorkDir(mkmap("foo/bar", "foo\r\nbar"));
	}

	@Test
	public void testCheckoutOutChangesAutoCRLFtrueBinary() throws IOException {
		setupCase(mk("foo"), mkmap("foo/bar", "foo\nb\u0000ar"), mk("foo"));
		db.getConfig().setString("core", null, "autocrlf", "true");
		checkout();
		assertIndex(mkmap("foo/bar", "foo\nb\u0000ar"));
		assertWorkDir(mkmap("foo/bar", "foo\nb\u0000ar"));
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
			assertEquals(Arrays.asList("foo"), getConflicts());
			assertTrue(new File(trash, "foo").isFile());
		}
	}

	@Test
	public void testDontOverwriteEmptyFolder() throws IOException {
		setupCase(mk("foo"), mk("foo"), mk("foo"));
		FileUtils.mkdir(new File(db.getWorkTree(), "d"));
		checkout();
		assertWorkDir(mkmap("foo", "foo", "d", "/"));
	}

	@Test
	public void testOverwriteUntrackedIgnoredFile() throws IOException,
			GitAPIException {
		String fname="file.txt";
		ChangeRecorder recorder = new ChangeRecorder();
		ListenerHandle handle = null;
		try (Git git = new Git(db)) {
			handle = db.getListenerList()
					.addWorkingTreeModifiedListener(recorder);
			// Add a file
			writeTrashFile(fname, "a");
			git.add().addFilepattern(fname).call();
			git.commit().setMessage("create file").call();

			// Create branch
			git.branchCreate().setName("side").call();

			// Modify file
			writeTrashFile(fname, "b");
			git.add().addFilepattern(fname).call();
			git.commit().setMessage("modify file").call();
			recorder.assertNoEvent();

			// Switch branches
			git.checkout().setName("side").call();
			recorder.assertEvent(new String[] { fname }, ChangeRecorder.EMPTY);
			git.rm().addFilepattern(fname).call();
			recorder.assertEvent(ChangeRecorder.EMPTY, new String[] { fname });
			writeTrashFile(".gitignore", fname);
			git.add().addFilepattern(".gitignore").call();
			git.commit().setMessage("delete and ignore file").call();

			writeTrashFile(fname, "Something different");
			recorder.assertNoEvent();
			git.checkout().setName("master").call();
			assertWorkDir(mkmap(fname, "b"));
			recorder.assertEvent(new String[] { fname },
					new String[] { ".gitignore" });
			assertTrue(git.status().call().isClean());
		} finally {
			if (handle != null) {
				handle.remove();
			}
		}
	}

	@Test
	public void testOverwriteUntrackedFileModeChange()
			throws IOException, GitAPIException {
		String fname = "file.txt";
		ChangeRecorder recorder = new ChangeRecorder();
		ListenerHandle handle = null;
		try (Git git = new Git(db)) {
			handle = db.getListenerList()
					.addWorkingTreeModifiedListener(recorder);
			// Add a file
			File file = writeTrashFile(fname, "a");
			git.add().addFilepattern(fname).call();
			git.commit().setMessage("create file").call();
			assertWorkDir(mkmap(fname, "a"));

			// Create branch
			git.branchCreate().setName("side").call();

			// Switch branches
			git.checkout().setName("side").call();
			recorder.assertNoEvent();

			// replace file with directory containing files
			FileUtils.delete(file);

			// create and add a file in the new directory to the index
			writeTrashFile(fname + "/dir1", "file1", "c");
			git.add().addFilepattern(fname + "/dir1/file1").call();

			// create but do not add a file in the new directory to the index
			writeTrashFile(fname + "/dir2", "file2", "d");

			assertTrue("File must be a directory now", file.isDirectory());
			assertFalse("Must not delete non empty directory", file.delete());

			// 2 extra files are created
			assertWorkDir(mkmap(fname + "/dir1/file1", "c",
					fname + "/dir2/file2", "d"));

			try {
				git.checkout().setName("master").call();
				fail("did not throw exception");
			} catch (Exception e) {
				// 2 extra files are still there
				assertWorkDir(mkmap(fname + "/dir1/file1", "c",
						fname + "/dir2/file2", "d"));
			}
			recorder.assertNoEvent();
		} finally {
			if (handle != null) {
				handle.remove();
			}
		}
	}

	@Test
	public void testOverwriteUntrackedLinkModeChange()
			throws Exception {
		Assume.assumeTrue(FS.DETECTED.supportsSymlinks());
		String fname = "file.txt";
		ChangeRecorder recorder = new ChangeRecorder();
		ListenerHandle handle = null;
		try (Git git = new Git(db)) {
			handle = db.getListenerList()
					.addWorkingTreeModifiedListener(recorder);
			// Add a file
			writeTrashFile(fname, "a");
			git.add().addFilepattern(fname).call();

			// Add a link to file
			String linkName = "link";
			File link = writeLink(linkName, fname).toFile();
			git.add().addFilepattern(linkName).call();
			git.commit().setMessage("Added file and link").call();

			assertWorkDir(mkmap(linkName, "a", fname, "a"));

			// Create branch
			git.branchCreate().setName("side").call();

			// Switch branches
			git.checkout().setName("side").call();
			recorder.assertNoEvent();

			// replace link with directory containing files
			FileUtils.delete(link);

			// create and add a file in the new directory to the index
			writeTrashFile(linkName + "/dir1", "file1", "c");
			git.add().addFilepattern(linkName + "/dir1/file1").call();

			// create but do not add a file in the new directory to the index
			writeTrashFile(linkName + "/dir2", "file2", "d");

			assertTrue("Link must be a directory now", link.isDirectory());
			assertFalse("Must not delete non empty directory", link.delete());

			// 2 extra files are created
			assertWorkDir(mkmap(fname, "a", linkName + "/dir1/file1", "c",
					linkName + "/dir2/file2", "d"));

			try {
				git.checkout().setName("master").call();
				fail("did not throw exception");
			} catch (Exception e) {
				// 2 extra files are still there
				assertWorkDir(mkmap(fname, "a", linkName + "/dir1/file1", "c",
						linkName + "/dir2/file2", "d"));
			}
			recorder.assertNoEvent();
		} finally {
			if (handle != null) {
				handle.remove();
			}
		}
	}

	@Test
	public void testFileModeChangeWithNoContentChangeUpdate() throws Exception {
		if (!FS.DETECTED.supportsExecute())
			return;

		ChangeRecorder recorder = new ChangeRecorder();
		ListenerHandle handle = null;
		try (Git git = new Git(db)) {
			handle = db.getListenerList()
					.addWorkingTreeModifiedListener(recorder);
			// Add non-executable file
			File file = writeTrashFile("file.txt", "a");
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("commit1").call();
			assertFalse(db.getFS().canExecute(file));

			// Create branch
			git.branchCreate().setName("b1").call();

			// Make file executable
			db.getFS().setExecute(file, true);
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("commit2").call();
			recorder.assertNoEvent();

			// Verify executable and working directory is clean
			Status status = git.status().call();
			assertTrue(status.getModified().isEmpty());
			assertTrue(status.getChanged().isEmpty());
			assertTrue(db.getFS().canExecute(file));

			// Switch branches
			git.checkout().setName("b1").call();

			// Verify not executable and working directory is clean
			status = git.status().call();
			assertTrue(status.getModified().isEmpty());
			assertTrue(status.getChanged().isEmpty());
			assertFalse(db.getFS().canExecute(file));
			recorder.assertEvent(new String[] { "file.txt" },
					ChangeRecorder.EMPTY);
		} finally {
			if (handle != null) {
				handle.remove();
			}
		}
	}

	@Test
	public void testFileModeChangeAndContentChangeConflict() throws Exception {
		if (!FS.DETECTED.supportsExecute())
			return;

		ChangeRecorder recorder = new ChangeRecorder();
		ListenerHandle handle = null;
		try (Git git = new Git(db)) {
			handle = db.getListenerList()
					.addWorkingTreeModifiedListener(recorder);
			// Add non-executable file
			File file = writeTrashFile("file.txt", "a");
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("commit1").call();
			assertFalse(db.getFS().canExecute(file));

			// Create branch
			git.branchCreate().setName("b1").call();

			// Make file executable
			db.getFS().setExecute(file, true);
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("commit2").call();

			// Verify executable and working directory is clean
			Status status = git.status().call();
			assertTrue(status.getModified().isEmpty());
			assertTrue(status.getChanged().isEmpty());
			assertTrue(db.getFS().canExecute(file));

			writeTrashFile("file.txt", "b");

			// Switch branches
			CheckoutCommand checkout = git.checkout().setName("b1");
			try {
				checkout.call();
				fail("Checkout exception not thrown");
			} catch (org.eclipse.jgit.api.errors.CheckoutConflictException e) {
				CheckoutResult result = checkout.getResult();
				assertNotNull(result);
				assertNotNull(result.getConflictList());
				assertEquals(1, result.getConflictList().size());
				assertTrue(result.getConflictList().contains("file.txt"));
			}
			recorder.assertNoEvent();
		} finally {
			if (handle != null) {
				handle.remove();
			}
		}
	}

	@Test
	public void testDirtyFileModeEqualHeadMerge()
			throws Exception {
		if (!FS.DETECTED.supportsExecute())
			return;

		ChangeRecorder recorder = new ChangeRecorder();
		ListenerHandle handle = null;
		try (Git git = new Git(db)) {
			handle = db.getListenerList()
					.addWorkingTreeModifiedListener(recorder);
			// Add non-executable file
			File file = writeTrashFile("file.txt", "a");
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("commit1").call();
			assertFalse(db.getFS().canExecute(file));

			// Create branch
			git.branchCreate().setName("b1").call();

			// Create second commit and don't touch file
			writeTrashFile("file2.txt", "");
			git.add().addFilepattern("file2.txt").call();
			git.commit().setMessage("commit2").call();

			// stage a mode change
			writeTrashFile("file.txt", "a");
			db.getFS().setExecute(file, true);
			git.add().addFilepattern("file.txt").call();

			// dirty the file
			writeTrashFile("file.txt", "b");

			assertEquals(
					"[file.txt, mode:100755, content:a][file2.txt, mode:100644, content:]",
					indexState(CONTENT));
			assertWorkDir(mkmap("file.txt", "b", "file2.txt", ""));
			recorder.assertNoEvent();

			// Switch branches and check that the dirty file survived in
			// worktree and index
			git.checkout().setName("b1").call();
			assertEquals("[file.txt, mode:100755, content:a]",
					indexState(CONTENT));
			assertWorkDir(mkmap("file.txt", "b"));
			recorder.assertEvent(ChangeRecorder.EMPTY,
					new String[] { "file2.txt" });
		} finally {
			if (handle != null) {
				handle.remove();
			}
		}
	}

	@Test
	public void testDirtyFileModeEqualIndexMerge()
			throws Exception {
		if (!FS.DETECTED.supportsExecute())
			return;

		ChangeRecorder recorder = new ChangeRecorder();
		ListenerHandle handle = null;
		try (Git git = new Git(db)) {
			handle = db.getListenerList()
					.addWorkingTreeModifiedListener(recorder);
			// Add non-executable file
			File file = writeTrashFile("file.txt", "a");
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("commit1").call();
			assertFalse(db.getFS().canExecute(file));

			// Create branch
			git.branchCreate().setName("b1").call();

			// Create second commit with executable file
			file = writeTrashFile("file.txt", "b");
			db.getFS().setExecute(file, true);
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("commit2").call();

			// stage the same content as in the branch we want to switch to
			writeTrashFile("file.txt", "a");
			db.getFS().setExecute(file, false);
			git.add().addFilepattern("file.txt").call();

			// dirty the file
			writeTrashFile("file.txt", "c");
			db.getFS().setExecute(file, true);

			assertEquals("[file.txt, mode:100644, content:a]",
					indexState(CONTENT));
			assertWorkDir(mkmap("file.txt", "c"));
			recorder.assertNoEvent();

			// Switch branches and check that the dirty file survived in
			// worktree
			// and index
			git.checkout().setName("b1").call();
			assertEquals("[file.txt, mode:100644, content:a]",
					indexState(CONTENT));
			assertWorkDir(mkmap("file.txt", "c"));
			recorder.assertNoEvent();
		} finally {
			if (handle != null) {
				handle.remove();
			}
		}
	}

	@Test
	public void testFileModeChangeAndContentChangeNoConflict() throws Exception {
		if (!FS.DETECTED.supportsExecute())
			return;

		ChangeRecorder recorder = new ChangeRecorder();
		ListenerHandle handle = null;
		try (Git git = new Git(db)) {
			handle = db.getListenerList()
					.addWorkingTreeModifiedListener(recorder);
			// Add first file
			File file1 = writeTrashFile("file1.txt", "a");
			git.add().addFilepattern("file1.txt").call();
			git.commit().setMessage("commit1").call();
			assertFalse(db.getFS().canExecute(file1));

			// Add second file
			File file2 = writeTrashFile("file2.txt", "b");
			git.add().addFilepattern("file2.txt").call();
			git.commit().setMessage("commit2").call();
			assertFalse(db.getFS().canExecute(file2));
			recorder.assertNoEvent();

			// Create branch from first commit
			assertNotNull(git.checkout().setCreateBranch(true).setName("b1")
					.setStartPoint(Constants.HEAD + "~1").call());
			recorder.assertEvent(ChangeRecorder.EMPTY,
					new String[] { "file2.txt" });

			// Change content and file mode in working directory and index
			file1 = writeTrashFile("file1.txt", "c");
			db.getFS().setExecute(file1, true);
			git.add().addFilepattern("file1.txt").call();

			// Switch back to 'master'
			assertNotNull(git.checkout().setName(Constants.MASTER).call());
			recorder.assertEvent(new String[] { "file2.txt" },
					ChangeRecorder.EMPTY);
		} finally {
			if (handle != null) {
				handle.remove();
			}
		}
	}

	@Test(expected = CheckoutConflictException.class)
	public void testFolderFileConflict() throws Exception {
		RevCommit headCommit = commitFile("f/a", "initial content", "master");
		RevCommit checkoutCommit = commitFile("f/a", "side content", "side");
		FileUtils.delete(new File(db.getWorkTree(), "f"), FileUtils.RECURSIVE);
		writeTrashFile("f", "file instead of folder");
		new DirCacheCheckout(db, headCommit.getTree(), db.lockDirCache(),
				checkoutCommit.getTree()).checkout();
	}

	@Test
	public void testMultipleContentConflicts() throws Exception {
		commitFile("a", "initial content", "master");
		RevCommit headCommit = commitFile("b", "initial content", "master");
		commitFile("a", "side content", "side");
		RevCommit checkoutCommit = commitFile("b", "side content", "side");
		writeTrashFile("a", "changed content");
		writeTrashFile("b", "changed content");

		try {
			new DirCacheCheckout(db, headCommit.getTree(), db.lockDirCache(),
					checkoutCommit.getTree()).checkout();
			fail();
		} catch (CheckoutConflictException expected) {
			assertEquals(2, expected.getConflictingFiles().length);
			assertTrue(Arrays.asList(expected.getConflictingFiles())
					.contains("a"));
			assertTrue(Arrays.asList(expected.getConflictingFiles())
					.contains("b"));
			assertEquals("changed content", read("a"));
			assertEquals("changed content", read("b"));
		}
	}

	@Test
	public void testFolderFileAndContentConflicts() throws Exception {
		RevCommit headCommit = commitFile("f/a", "initial content", "master");
		commitFile("b", "side content", "side");
		RevCommit checkoutCommit = commitFile("f/a", "side content", "side");
		FileUtils.delete(new File(db.getWorkTree(), "f"), FileUtils.RECURSIVE);
		writeTrashFile("f", "file instead of a folder");
		writeTrashFile("b", "changed content");

		try {
			new DirCacheCheckout(db, headCommit.getTree(), db.lockDirCache(),
					checkoutCommit.getTree()).checkout();
			fail();
		} catch (CheckoutConflictException expected) {
			assertEquals(2, expected.getConflictingFiles().length);
			assertTrue(Arrays.asList(expected.getConflictingFiles())
					.contains("b"));
			assertTrue(Arrays.asList(expected.getConflictingFiles())
					.contains("f"));
			assertEquals("file instead of a folder", read("f"));
			assertEquals("changed content", read("b"));
		}
	}

	@Test
	public void testLongFilename() throws Exception {
		char[] bytes = new char[253];
		Arrays.fill(bytes, 'f');
		String longFileName = new String(bytes);
		// 1
		doit(mkmap(longFileName, "a"), mkmap(longFileName, "b"),
				mkmap(longFileName, "a"));
		writeTrashFile(longFileName, "a");
		checkout();
		assertNoConflicts();
		assertUpdated(longFileName);
	}

	public void assertWorkDir(Map<String, String> i)
			throws CorruptObjectException,
			IOException {
		try (TreeWalk walk = new TreeWalk(db)) {
			walk.setRecursive(false);
			walk.addTree(new FileTreeIterator(db));
			String expectedValue;
			String path;
			int nrFiles = 0;
			FileTreeIterator ft;
			while (walk.next()) {
				ft = walk.getTree(0, FileTreeIterator.class);
				path = ft.getEntryPathString();
				expectedValue = i.get(path);
				File file = new File(db.getWorkTree(), path);
				assertTrue(file.exists());
				if (file.isFile()) {
					assertNotNull("found unexpected file for path " + path
							+ " in workdir", expectedValue);
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
					assertArrayEquals("unexpected content for path " + path
							+ " in workDir. ", buffer, i.get(path).getBytes());
					nrFiles++;
				} else if (file.isDirectory()) {
					String[] files = file.list();
					if (files != null && files.length == 0) {
						assertEquals("found unexpected empty folder for path "
								+ path + " in workDir. ", "/", i.get(path));
						nrFiles++;
					}
				}
				if (walk.isSubtree()) {
					walk.enterSubtree();
				}
			}
			assertEquals("WorkDir has not the right size.", i.size(), nrFiles);
		}
	}
}
