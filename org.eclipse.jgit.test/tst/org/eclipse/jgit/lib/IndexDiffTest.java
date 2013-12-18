/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2013, Robin Stocker <robin@nibor.org>
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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.TreeSet;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.IndexDiff.StageState;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.util.IO;
import org.junit.Test;

@SuppressWarnings("deprecation")
public class IndexDiffTest extends RepositoryTestCase {

	static PathEdit add(final Repository db, final File workdir,
			final String path) throws FileNotFoundException, IOException {
		ObjectInserter inserter = db.newObjectInserter();
		final File f = new File(workdir, path);
		final ObjectId id = inserter.insert(Constants.OBJ_BLOB,
				IO.readFully(f));
		return new PathEdit(path) {
			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.REGULAR_FILE);
				ent.setLength(f.length());
				ent.setObjectId(id);
			}
		};
	}

	@Test
	public void testAdded() throws IOException {
		writeTrashFile("file1", "file1");
		writeTrashFile("dir/subfile", "dir/subfile");
		Tree tree = new Tree(db);
		tree.setId(insertTree(tree));

		DirCache index = db.lockDirCache();
		DirCacheEditor editor = index.editor();
		editor.add(add(db, trash, "file1"));
		editor.add(add(db, trash, "dir/subfile"));
		editor.commit();
		FileTreeIterator iterator = new FileTreeIterator(db);
		IndexDiff diff = new IndexDiff(db, tree.getId(), iterator);
		diff.diff();
		assertEquals(2, diff.getAdded().size());
		assertTrue(diff.getAdded().contains("file1"));
		assertTrue(diff.getAdded().contains("dir/subfile"));
		assertEquals(0, diff.getChanged().size());
		assertEquals(0, diff.getModified().size());
		assertEquals(0, diff.getRemoved().size());
		assertEquals(Collections.EMPTY_SET, diff.getUntrackedFolders());
	}

	@Test
	public void testRemoved() throws IOException {
		writeTrashFile("file2", "file2");
		writeTrashFile("dir/file3", "dir/file3");

		Tree tree = new Tree(db);
		tree.addFile("file2");
		tree.addFile("dir/file3");
		assertEquals(2, tree.memberCount());
		tree.findBlobMember("file2").setId(ObjectId.fromString("30d67d4672d5c05833b7192cc77a79eaafb5c7ad"));
		Tree tree2 = (Tree) tree.findTreeMember("dir");
		tree2.findBlobMember("file3").setId(ObjectId.fromString("873fb8d667d05436d728c52b1d7a09528e6eb59b"));
		tree2.setId(insertTree(tree2));
		tree.setId(insertTree(tree));

		FileTreeIterator iterator = new FileTreeIterator(db);
		IndexDiff diff = new IndexDiff(db, tree.getId(), iterator);
		diff.diff();
		assertEquals(2, diff.getRemoved().size());
		assertTrue(diff.getRemoved().contains("file2"));
		assertTrue(diff.getRemoved().contains("dir/file3"));
		assertEquals(0, diff.getChanged().size());
		assertEquals(0, diff.getModified().size());
		assertEquals(0, diff.getAdded().size());
		assertEquals(Collections.EMPTY_SET, diff.getUntrackedFolders());
	}

	@Test
	public void testModified() throws IOException, GitAPIException {

		writeTrashFile("file2", "file2");
		writeTrashFile("dir/file3", "dir/file3");

		Git git = new Git(db);
		git.add().addFilepattern("file2").addFilepattern("dir/file3").call();

		writeTrashFile("dir/file3", "changed");

		Tree tree = new Tree(db);
		tree.addFile("file2").setId(ObjectId.fromString("0123456789012345678901234567890123456789"));
		tree.addFile("dir/file3").setId(ObjectId.fromString("0123456789012345678901234567890123456789"));
		assertEquals(2, tree.memberCount());

		Tree tree2 = (Tree) tree.findTreeMember("dir");
		tree2.setId(insertTree(tree2));
		tree.setId(insertTree(tree));
		FileTreeIterator iterator = new FileTreeIterator(db);
		IndexDiff diff = new IndexDiff(db, tree.getId(), iterator);
		diff.diff();
		assertEquals(2, diff.getChanged().size());
		assertTrue(diff.getChanged().contains("file2"));
		assertTrue(diff.getChanged().contains("dir/file3"));
		assertEquals(1, diff.getModified().size());
		assertTrue(diff.getModified().contains("dir/file3"));
		assertEquals(0, diff.getAdded().size());
		assertEquals(0, diff.getRemoved().size());
		assertEquals(0, diff.getMissing().size());
		assertEquals(Collections.EMPTY_SET, diff.getUntrackedFolders());
	}

	@Test
	public void testConflicting() throws Exception {
		Git git = new Git(db);

		writeTrashFile("a", "1\na\n3\n");
		writeTrashFile("b", "1\nb\n3\n");
		git.add().addFilepattern("a").addFilepattern("b").call();
		RevCommit initialCommit = git.commit().setMessage("initial").call();

		// create side branch with two modifications
		createBranch(initialCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");
		writeTrashFile("a", "1\na(side)\n3\n");
		writeTrashFile("b", "1\nb\n3\n(side)");
		git.add().addFilepattern("a").addFilepattern("b").call();
		RevCommit secondCommit = git.commit().setMessage("side").call();

		// update a on master to generate conflict
		checkoutBranch("refs/heads/master");
		writeTrashFile("a", "1\na(main)\n3\n");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("main").call();

		// merge side with master
		MergeResult result = git.merge().include(secondCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();
		assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());

		FileTreeIterator iterator = new FileTreeIterator(db);
		IndexDiff diff = new IndexDiff(db, Constants.HEAD, iterator);
		diff.diff();

		assertEquals("[b]",
				new TreeSet<String>(diff.getChanged()).toString());
		assertEquals("[]", diff.getAdded().toString());
		assertEquals("[]", diff.getRemoved().toString());
		assertEquals("[]", diff.getMissing().toString());
		assertEquals("[]", diff.getModified().toString());
		assertEquals("[a]", diff.getConflicting().toString());
		assertEquals(StageState.BOTH_MODIFIED,
				diff.getConflictingStageStates().get("a"));
		assertEquals(Collections.EMPTY_SET, diff.getUntrackedFolders());
	}

	@Test
	public void testConflictingDeletedAndModified() throws Exception {
		Git git = new Git(db);

		writeTrashFile("a", "1\na\n3\n");
		writeTrashFile("b", "1\nb\n3\n");
		git.add().addFilepattern("a").addFilepattern("b").call();
		RevCommit initialCommit = git.commit().setMessage("initial").call();

		// create side branch and delete "a"
		createBranch(initialCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");
		git.rm().addFilepattern("a").call();
		RevCommit secondCommit = git.commit().setMessage("side").call();

		// update a on master to generate conflict
		checkoutBranch("refs/heads/master");
		writeTrashFile("a", "1\na(main)\n3\n");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("main").call();

		// merge side with master
		MergeResult result = git.merge().include(secondCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();
		assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());

		FileTreeIterator iterator = new FileTreeIterator(db);
		IndexDiff diff = new IndexDiff(db, Constants.HEAD, iterator);
		diff.diff();

		assertEquals("[]", new TreeSet<String>(diff.getChanged()).toString());
		assertEquals("[]", diff.getAdded().toString());
		assertEquals("[]", diff.getRemoved().toString());
		assertEquals("[]", diff.getMissing().toString());
		assertEquals("[]", diff.getModified().toString());
		assertEquals("[a]", diff.getConflicting().toString());
		assertEquals(StageState.DELETED_BY_THEM,
				diff.getConflictingStageStates().get("a"));
		assertEquals(Collections.EMPTY_SET, diff.getUntrackedFolders());
	}

	@Test
	public void testConflictingFromMultipleCreations() throws Exception {
		Git git = new Git(db);

		writeTrashFile("a", "1\na\n3\n");
		git.add().addFilepattern("a").call();
		RevCommit initialCommit = git.commit().setMessage("initial").call();

		createBranch(initialCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");

		writeTrashFile("b", "1\nb(side)\n3\n");
		git.add().addFilepattern("b").call();
		RevCommit secondCommit = git.commit().setMessage("side").call();

		checkoutBranch("refs/heads/master");

		writeTrashFile("b", "1\nb(main)\n3\n");
		git.add().addFilepattern("b").call();
		git.commit().setMessage("main").call();

		MergeResult result = git.merge().include(secondCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();
		assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());

		FileTreeIterator iterator = new FileTreeIterator(db);
		IndexDiff diff = new IndexDiff(db, Constants.HEAD, iterator);
		diff.diff();

		assertEquals("[]", new TreeSet<String>(diff.getChanged()).toString());
		assertEquals("[]", diff.getAdded().toString());
		assertEquals("[]", diff.getRemoved().toString());
		assertEquals("[]", diff.getMissing().toString());
		assertEquals("[]", diff.getModified().toString());
		assertEquals("[b]", diff.getConflicting().toString());
		assertEquals(Collections.EMPTY_SET, diff.getUntrackedFolders());
	}

	@Test
	public void testUnchangedSimple() throws IOException, GitAPIException {
		writeTrashFile("a.b", "a.b");
		writeTrashFile("a.c", "a.c");
		writeTrashFile("a=c", "a=c");
		writeTrashFile("a=d", "a=d");
		Git git = new Git(db);
		git.add().addFilepattern("a.b").call();
		git.add().addFilepattern("a.c").call();
		git.add().addFilepattern("a=c").call();
		git.add().addFilepattern("a=d").call();

		Tree tree = new Tree(db);
		// got the hash id'd from the data using echo -n a.b|git hash-object -t blob --stdin
		tree.addFile("a.b").setId(ObjectId.fromString("f6f28df96c2b40c951164286e08be7c38ec74851"));
		tree.addFile("a.c").setId(ObjectId.fromString("6bc0e647512d2a0bef4f26111e484dc87df7f5ca"));
		tree.addFile("a=c").setId(ObjectId.fromString("06022365ddbd7fb126761319633bf73517770714"));
		tree.addFile("a=d").setId(ObjectId.fromString("fa6414df3da87840700e9eeb7fc261dd77ccd5c2"));

		tree.setId(insertTree(tree));

		FileTreeIterator iterator = new FileTreeIterator(db);
		IndexDiff diff = new IndexDiff(db, tree.getId(), iterator);
		diff.diff();
		assertEquals(0, diff.getChanged().size());
		assertEquals(0, diff.getAdded().size());
		assertEquals(0, diff.getRemoved().size());
		assertEquals(0, diff.getMissing().size());
		assertEquals(0, diff.getModified().size());
		assertEquals(Collections.EMPTY_SET, diff.getUntrackedFolders());
	}

	/**
	 * This test has both files and directories that involve the tricky ordering
	 * used by Git.
	 *
	 * @throws IOException
	 * @throws GitAPIException
	 */
	@Test
	public void testUnchangedComplex() throws IOException, GitAPIException {
		Git git = new Git(db);
		writeTrashFile("a.b", "a.b");
		writeTrashFile("a.c", "a.c");
		writeTrashFile("a/b.b/b", "a/b.b/b");
		writeTrashFile("a/b", "a/b");
		writeTrashFile("a/c", "a/c");
		writeTrashFile("a=c", "a=c");
		writeTrashFile("a=d", "a=d");
		git.add().addFilepattern("a.b").addFilepattern("a.c")
				.addFilepattern("a/b.b/b").addFilepattern("a/b")
				.addFilepattern("a/c").addFilepattern("a=c")
				.addFilepattern("a=d").call();

		Tree tree = new Tree(db);
		// got the hash id'd from the data using echo -n a.b|git hash-object -t blob --stdin
		tree.addFile("a.b").setId(ObjectId.fromString("f6f28df96c2b40c951164286e08be7c38ec74851"));
		tree.addFile("a.c").setId(ObjectId.fromString("6bc0e647512d2a0bef4f26111e484dc87df7f5ca"));
		tree.addFile("a/b.b/b").setId(ObjectId.fromString("8d840bd4e2f3a48ff417c8e927d94996849933fd"));
		tree.addFile("a/b").setId(ObjectId.fromString("db89c972fc57862eae378f45b74aca228037d415"));
		tree.addFile("a/c").setId(ObjectId.fromString("52ad142a008aeb39694bafff8e8f1be75ed7f007"));
		tree.addFile("a=c").setId(ObjectId.fromString("06022365ddbd7fb126761319633bf73517770714"));
		tree.addFile("a=d").setId(ObjectId.fromString("fa6414df3da87840700e9eeb7fc261dd77ccd5c2"));

		Tree tree3 = (Tree) tree.findTreeMember("a/b.b");
		tree3.setId(insertTree(tree3));
		Tree tree2 = (Tree) tree.findTreeMember("a");
		tree2.setId(insertTree(tree2));
		tree.setId(insertTree(tree));

		FileTreeIterator iterator = new FileTreeIterator(db);
		IndexDiff diff = new IndexDiff(db, tree.getId(), iterator);
		diff.diff();
		assertEquals(0, diff.getChanged().size());
		assertEquals(0, diff.getAdded().size());
		assertEquals(0, diff.getRemoved().size());
		assertEquals(0, diff.getMissing().size());
		assertEquals(0, diff.getModified().size());
		assertEquals(Collections.EMPTY_SET, diff.getUntrackedFolders());
	}

	private ObjectId insertTree(Tree tree) throws IOException {
		ObjectInserter oi = db.newObjectInserter();
		try {
			ObjectId id = oi.insert(Constants.OBJ_TREE, tree.format());
			oi.flush();
			return id;
		} finally {
			oi.release();
		}
	}

	/**
	 * A file is removed from the index but stays in the working directory. It
	 * is checked if IndexDiff detects this file as removed and untracked.
	 *
	 * @throws Exception
	 */
	@Test
	public void testRemovedUntracked() throws Exception{
		Git git = new Git(db);
		String path = "file";
		writeTrashFile(path, "content");
		git.add().addFilepattern(path).call();
		git.commit().setMessage("commit").call();
		removeFromIndex(path);
		FileTreeIterator iterator = new FileTreeIterator(db);
		IndexDiff diff = new IndexDiff(db, Constants.HEAD, iterator);
		diff.diff();
		assertTrue(diff.getRemoved().contains(path));
		assertTrue(diff.getUntracked().contains(path));
		assertEquals(Collections.EMPTY_SET, diff.getUntrackedFolders());
	}

	/**
	 *
	 * @throws Exception
	 */
	@Test
	public void testUntrackedFolders() throws Exception {
		Git git = new Git(db);

		IndexDiff diff = new IndexDiff(db, Constants.HEAD,
				new FileTreeIterator(db));
		diff.diff();
		assertEquals(Collections.EMPTY_SET, diff.getUntrackedFolders());

		writeTrashFile("readme", "");
		writeTrashFile("src/com/A.java", "");
		writeTrashFile("src/com/B.java", "");
		writeTrashFile("src/org/A.java", "");
		writeTrashFile("src/org/B.java", "");
		writeTrashFile("target/com/A.java", "");
		writeTrashFile("target/com/B.java", "");
		writeTrashFile("target/org/A.java", "");
		writeTrashFile("target/org/B.java", "");

		git.add().addFilepattern("src").addFilepattern("readme").call();
		git.commit().setMessage("initial").call();

		diff = new IndexDiff(db, Constants.HEAD,
				new FileTreeIterator(db));
		diff.diff();
		assertEquals(new HashSet<String>(Arrays.asList("target")),
				diff.getUntrackedFolders());

		writeTrashFile("src/tst/A.java", "");
		writeTrashFile("src/tst/B.java", "");

		diff = new IndexDiff(db, Constants.HEAD, new FileTreeIterator(db));
		diff.diff();
		assertEquals(new HashSet<String>(Arrays.asList("target", "src/tst")),
				diff.getUntrackedFolders());

		git.rm().addFilepattern("src/com/B.java").addFilepattern("src/org")
				.call();
		git.commit().setMessage("second").call();
		writeTrashFile("src/org/C.java", "");

		diff = new IndexDiff(db, Constants.HEAD, new FileTreeIterator(db));
		diff.diff();
		assertEquals(
				new HashSet<String>(Arrays.asList("src/org", "src/tst",
						"target")),
				diff.getUntrackedFolders());
	}

	@Test
	public void testAssumeUnchanged() throws Exception {
		Git git = new Git(db);
		String path = "file";
		writeTrashFile(path, "content");
		git.add().addFilepattern(path).call();
		String path2 = "file2";
		writeTrashFile(path2, "content");
		String path3 = "file3";
		writeTrashFile(path3, "some content");
		git.add().addFilepattern(path2).addFilepattern(path3).call();
		git.commit().setMessage("commit").call();
		assumeUnchanged(path2);
		assumeUnchanged(path3);
		writeTrashFile(path, "more content");
		deleteTrashFile(path3);

		FileTreeIterator iterator = new FileTreeIterator(db);
		IndexDiff diff = new IndexDiff(db, Constants.HEAD, iterator);
		diff.diff();
		assertEquals(2, diff.getAssumeUnchanged().size());
		assertEquals(1, diff.getModified().size());
		assertEquals(0, diff.getChanged().size());
		assertTrue(diff.getAssumeUnchanged().contains("file2"));
		assertTrue(diff.getAssumeUnchanged().contains("file3"));
		assertTrue(diff.getModified().contains("file"));

		git.add().addFilepattern(".").call();

		iterator = new FileTreeIterator(db);
		diff = new IndexDiff(db, Constants.HEAD, iterator);
		diff.diff();
		assertEquals(2, diff.getAssumeUnchanged().size());
		assertEquals(0, diff.getModified().size());
		assertEquals(1, diff.getChanged().size());
		assertTrue(diff.getAssumeUnchanged().contains("file2"));
		assertTrue(diff.getAssumeUnchanged().contains("file3"));
		assertTrue(diff.getChanged().contains("file"));
		assertEquals(Collections.EMPTY_SET, diff.getUntrackedFolders());
	}

	@Test
	public void testStageState() throws IOException {
		final int base = DirCacheEntry.STAGE_1;
		final int ours = DirCacheEntry.STAGE_2;
		final int theirs = DirCacheEntry.STAGE_3;
		verifyStageState(StageState.BOTH_DELETED, base);
		verifyStageState(StageState.DELETED_BY_THEM, ours, base);
		verifyStageState(StageState.DELETED_BY_US, base, theirs);
		verifyStageState(StageState.BOTH_MODIFIED, base, ours, theirs);
		verifyStageState(StageState.ADDED_BY_US, ours);
		verifyStageState(StageState.BOTH_ADDED, ours, theirs);
		verifyStageState(StageState.ADDED_BY_THEM, theirs);

		assertTrue(StageState.BOTH_DELETED.hasBase());
		assertFalse(StageState.BOTH_DELETED.hasOurs());
		assertFalse(StageState.BOTH_DELETED.hasTheirs());
		assertFalse(StageState.BOTH_ADDED.hasBase());
		assertTrue(StageState.BOTH_ADDED.hasOurs());
		assertTrue(StageState.BOTH_ADDED.hasTheirs());
	}

	private void verifyStageState(StageState expected, int... stages)
			throws IOException {
		DirCacheBuilder builder = db.lockDirCache().builder();
		for (int stage : stages) {
			DirCacheEntry entry = createEntry("a", FileMode.REGULAR_FILE,
					stage, "content");
			builder.add(entry);
		}
		builder.commit();

		IndexDiff diff = new IndexDiff(db, Constants.HEAD,
				new FileTreeIterator(db));
		diff.diff();

		assertEquals(
				"Conflict for entries in stages " + Arrays.toString(stages),
				expected, diff.getConflictingStageStates().get("a"));
	}

	private void removeFromIndex(String path) throws IOException {
		final DirCache dirc = db.lockDirCache();
		final DirCacheEditor edit = dirc.editor();
		edit.add(new DirCacheEditor.DeletePath(path));
		if (!edit.commit())
			throw new IOException("could not commit");
	}

	private void assumeUnchanged(String path) throws IOException {
		final DirCache dirc = db.lockDirCache();
		final DirCacheEntry ent = dirc.getEntry(path);
		if (ent != null)
			ent.setAssumeValid(true);
		dirc.write();
		if (!dirc.commit())
			throw new IOException("could not commit");
	}

}
