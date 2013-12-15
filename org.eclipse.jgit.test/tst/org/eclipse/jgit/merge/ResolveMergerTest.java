/*
 * Copyright (C) 2012, Robin Stocker <robin@nibor.org>
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
package org.eclipse.jgit.merge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.errors.NoMergeBaseException;
import org.eclipse.jgit.errors.NoMergeBaseException.MergeBaseFailureReason;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Assert;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class ResolveMergerTest extends RepositoryTestCase {

	@DataPoint
	public static MergeStrategy resolve = MergeStrategy.RESOLVE;

	@DataPoint
	public static MergeStrategy recursive = MergeStrategy.RECURSIVE;

	@Theory
	public void failingDeleteOfDirectoryWithUntrackedContent(
			MergeStrategy strategy) throws Exception {
		File folder1 = new File(db.getWorkTree(), "folder1");
		FileUtils.mkdir(folder1);
		File file = new File(folder1, "file1.txt");
		write(file, "folder1--file1.txt");
		file = new File(folder1, "file2.txt");
		write(file, "folder1--file2.txt");

		Git git = new Git(db);
		git.add().addFilepattern(folder1.getName()).call();
		RevCommit base = git.commit().setMessage("adding folder").call();

		recursiveDelete(folder1);
		git.rm().addFilepattern("folder1/file1.txt")
				.addFilepattern("folder1/file2.txt").call();
		RevCommit other = git.commit()
				.setMessage("removing folders on 'other'").call();

		git.checkout().setName(base.name()).call();

		file = new File(db.getWorkTree(), "unrelated.txt");
		write(file, "unrelated");

		git.add().addFilepattern("unrelated.txt").call();
		RevCommit head = git.commit().setMessage("Adding another file").call();

		// Untracked file to cause failing path for delete() of folder1
		// but that's ok.
		file = new File(folder1, "file3.txt");
		write(file, "folder1--file3.txt");

		ResolveMerger merger = (ResolveMerger) strategy.newMerger(db, false);
		merger.setCommitNames(new String[] { "BASE", "HEAD", "other" });
		merger.setWorkingTreeIterator(new FileTreeIterator(db));
		boolean ok = merger.merge(head.getId(), other.getId());
		assertTrue(ok);
		assertTrue(file.exists());
	}

	/**
	 * Merging two conflicting subtrees when the index does not contain any file
	 * in that subtree should lead to a conflicting state.
	 *
	 * @param strategy
	 * @throws Exception
	 */
	@Theory
	public void checkMergeConflictingTreesWithoutIndex(MergeStrategy strategy)
			throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("d/1", "orig");
		git.add().addFilepattern("d/1").call();
		RevCommit first = git.commit().setMessage("added d/1").call();

		writeTrashFile("d/1", "master");
		RevCommit masterCommit = git.commit().setAll(true)
				.setMessage("modified d/1 on master").call();

		git.checkout().setCreateBranch(true).setStartPoint(first)
				.setName("side").call();
		writeTrashFile("d/1", "side");
		git.commit().setAll(true).setMessage("modified d/1 on side").call();

		git.rm().addFilepattern("d/1").call();
		git.rm().addFilepattern("d").call();
		MergeResult mergeRes = git.merge().setStrategy(strategy)
				.include(masterCommit).call();
		assertEquals(MergeStatus.CONFLICTING, mergeRes.getMergeStatus());
		assertEquals(
				"[d/1, mode:100644, stage:1, content:orig][d/1, mode:100644, stage:2, content:side][d/1, mode:100644, stage:3, content:master]",
				indexState(CONTENT));
	}

	/**
	 * Merging two different but mergeable subtrees when the index does not
	 * contain any file in that subtree should lead to a merged state.
	 *
	 * @param strategy
	 * @throws Exception
	 */
	@Theory
	public void checkMergeMergeableTreesWithoutIndex(MergeStrategy strategy)
			throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("d/1", "1\n2\n3");
		git.add().addFilepattern("d/1").call();
		RevCommit first = git.commit().setMessage("added d/1").call();

		writeTrashFile("d/1", "1master\n2\n3");
		RevCommit masterCommit = git.commit().setAll(true)
				.setMessage("modified d/1 on master").call();

		git.checkout().setCreateBranch(true).setStartPoint(first)
				.setName("side").call();
		writeTrashFile("d/1", "1\n2\n3side");
		git.commit().setAll(true).setMessage("modified d/1 on side").call();

		git.rm().addFilepattern("d/1").call();
		git.rm().addFilepattern("d").call();
		MergeResult mergeRes = git.merge().setStrategy(strategy)
				.include(masterCommit).call();
		assertEquals(MergeStatus.MERGED, mergeRes.getMergeStatus());
		assertEquals("[d/1, mode:100644, content:1master\n2\n3side\n]",
				indexState(CONTENT));
	}

	/**
	 * An existing directory without tracked content should not prevent merging
	 * a tree where that directory exists.
	 *
	 * @param strategy
	 * @throws Exception
	 */
	@Theory
	public void checkUntrackedFolderIsNotAConflict(
			MergeStrategy strategy) throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("d/1", "1");
		git.add().addFilepattern("d/1").call();
		RevCommit first = git.commit().setMessage("added d/1").call();

		writeTrashFile("e/1", "4");
		git.add().addFilepattern("e/1").call();
		RevCommit masterCommit = git.commit().setMessage("added e/1").call();

		git.checkout().setCreateBranch(true).setStartPoint(first)
				.setName("side").call();
		writeTrashFile("f/1", "5");
		git.add().addFilepattern("f/1").call();
		git.commit().setAll(true).setMessage("added f/1")
				.call();

		// Untracked directory e shall not conflict with merged e/1
		writeTrashFile("e/2", "d two");

		MergeResult mergeRes = git.merge().setStrategy(strategy)
				.include(masterCommit).call();
		assertEquals(MergeStatus.MERGED, mergeRes.getMergeStatus());
		assertEquals(
				"[d/1, mode:100644, content:1][e/1, mode:100644, content:4][f/1, mode:100644, content:5]",
				indexState(CONTENT));
	}

	/**
	 * An existing directory without tracked content should not prevent merging
	 * a file with that name.
	 *
	 * @param strategy
	 * @throws Exception
	 */
	@Theory
	public void checkUntrackedEmpytFolderIsNotAConflictWithFile(
			MergeStrategy strategy)
			throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("d/1", "1");
		git.add().addFilepattern("d/1").call();
		RevCommit first = git.commit().setMessage("added d/1").call();

		writeTrashFile("e", "4");
		git.add().addFilepattern("e").call();
		RevCommit masterCommit = git.commit().setMessage("added e").call();

		git.checkout().setCreateBranch(true).setStartPoint(first)
				.setName("side").call();
		writeTrashFile("f/1", "5");
		git.add().addFilepattern("f/1").call();
		git.commit().setAll(true).setMessage("added f/1").call();

		// Untracked empty directory hierarcy e/1 shall not conflict with merged
		// e/1
		FileUtils.mkdirs(new File(trash, "e/1"), true);

		MergeResult mergeRes = git.merge().setStrategy(strategy)
				.include(masterCommit).call();
		assertEquals(MergeStatus.MERGED, mergeRes.getMergeStatus());
		assertEquals(
				"[d/1, mode:100644, content:1][e, mode:100644, content:4][f/1, mode:100644, content:5]",
				indexState(CONTENT));
	}

	@Theory
	public void mergeWithCrlfInWT(MergeStrategy strategy) throws IOException,
			GitAPIException {
		Git git = Git.wrap(db);
		db.getConfig().setString("core", null, "autocrlf", "false");
		db.getConfig().save();
		writeTrashFile("crlf.txt", "some\r\ndata\r\n");
		git.add().addFilepattern("crlf.txt").call();
		git.commit().setMessage("base").call();

		git.branchCreate().setName("brancha").call();

		writeTrashFile("crlf.txt", "some\r\nmore\r\ndata\r\n");
		git.add().addFilepattern("crlf.txt").call();
		git.commit().setMessage("on master").call();

		git.checkout().setName("brancha").call();
		writeTrashFile("crlf.txt", "some\r\ndata\r\ntoo\r\n");
		git.add().addFilepattern("crlf.txt").call();
		git.commit().setMessage("on brancha").call();

		db.getConfig().setString("core", null, "autocrlf", "input");
		db.getConfig().save();

		MergeResult mergeResult = git.merge().setStrategy(strategy)
				.include(db.resolve("master"))
				.call();
		assertEquals(MergeResult.MergeStatus.MERGED,
				mergeResult.getMergeStatus());
	}

	/**
	 * Merging two equal subtrees when the index does not contain any file in
	 * that subtree should lead to a merged state.
	 *
	 * @param strategy
	 * @throws Exception
	 */
	@Theory
	public void checkMergeEqualTreesWithoutIndex(MergeStrategy strategy)
			throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("d/1", "orig");
		git.add().addFilepattern("d/1").call();
		RevCommit first = git.commit().setMessage("added d/1").call();

		writeTrashFile("d/1", "modified");
		RevCommit masterCommit = git.commit().setAll(true)
				.setMessage("modified d/1 on master").call();

		git.checkout().setCreateBranch(true).setStartPoint(first)
				.setName("side").call();
		writeTrashFile("d/1", "modified");
		git.commit().setAll(true).setMessage("modified d/1 on side").call();

		git.rm().addFilepattern("d/1").call();
		git.rm().addFilepattern("d").call();
		MergeResult mergeRes = git.merge().setStrategy(strategy)
				.include(masterCommit).call();
		assertEquals(MergeStatus.MERGED, mergeRes.getMergeStatus());
		assertEquals("[d/1, mode:100644, content:modified]",
				indexState(CONTENT));
	}

	/**
	 * Merging two equal subtrees with an incore merger should lead to a merged
	 * state (The 'Gerrit' use case).
	 *
	 * @param strategy
	 * @throws Exception
	 */
	@Theory
	public void checkMergeEqualTreesInCore(MergeStrategy strategy)
			throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("d/1", "orig");
		git.add().addFilepattern("d/1").call();
		RevCommit first = git.commit().setMessage("added d/1").call();

		writeTrashFile("d/1", "modified");
		RevCommit masterCommit = git.commit().setAll(true)
				.setMessage("modified d/1 on master").call();

		git.checkout().setCreateBranch(true).setStartPoint(first)
				.setName("side").call();
		writeTrashFile("d/1", "modified");
		RevCommit sideCommit = git.commit().setAll(true)
				.setMessage("modified d/1 on side").call();

		git.rm().addFilepattern("d/1").call();
		git.rm().addFilepattern("d").call();

		ThreeWayMerger resolveMerger = (ThreeWayMerger) strategy.newMerger(db,
				true);
		boolean noProblems = resolveMerger.merge(masterCommit, sideCommit);
		assertTrue(noProblems);
	}

	/**
	 * Merging two equal subtrees when the index and HEAD does not contain any
	 * file in that subtree should lead to a merged state.
	 *
	 * @param strategy
	 * @throws Exception
	 */
	@Theory
	public void checkMergeEqualNewTrees(MergeStrategy strategy)
			throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("2", "orig");
		git.add().addFilepattern("2").call();
		RevCommit first = git.commit().setMessage("added 2").call();

		writeTrashFile("d/1", "orig");
		git.add().addFilepattern("d/1").call();
		RevCommit masterCommit = git.commit().setAll(true)
				.setMessage("added d/1 on master").call();

		git.checkout().setCreateBranch(true).setStartPoint(first)
				.setName("side").call();
		writeTrashFile("d/1", "orig");
		git.add().addFilepattern("d/1").call();
		git.commit().setAll(true).setMessage("added d/1 on side").call();

		git.rm().addFilepattern("d/1").call();
		git.rm().addFilepattern("d").call();
		MergeResult mergeRes = git.merge().setStrategy(strategy)
				.include(masterCommit).call();
		assertEquals(MergeStatus.MERGED, mergeRes.getMergeStatus());
		assertEquals(
				"[2, mode:100644, content:orig][d/1, mode:100644, content:orig]",
				indexState(CONTENT));
	}

	/**
	 * Merging two conflicting subtrees when the index and HEAD does not contain
	 * any file in that subtree should lead to a conflicting state.
	 *
	 * @param strategy
	 * @throws Exception
	 */
	@Theory
	public void checkMergeConflictingNewTrees(MergeStrategy strategy)
			throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("2", "orig");
		git.add().addFilepattern("2").call();
		RevCommit first = git.commit().setMessage("added 2").call();

		writeTrashFile("d/1", "master");
		git.add().addFilepattern("d/1").call();
		RevCommit masterCommit = git.commit().setAll(true)
				.setMessage("added d/1 on master").call();

		git.checkout().setCreateBranch(true).setStartPoint(first)
				.setName("side").call();
		writeTrashFile("d/1", "side");
		git.add().addFilepattern("d/1").call();
		git.commit().setAll(true).setMessage("added d/1 on side").call();

		git.rm().addFilepattern("d/1").call();
		git.rm().addFilepattern("d").call();
		MergeResult mergeRes = git.merge().setStrategy(strategy)
				.include(masterCommit).call();
		assertEquals(MergeStatus.CONFLICTING, mergeRes.getMergeStatus());
		assertEquals(
				"[2, mode:100644, content:orig][d/1, mode:100644, stage:2, content:side][d/1, mode:100644, stage:3, content:master]",
				indexState(CONTENT));
	}

	/**
	 * Merging two conflicting files when the index contains a tree for that
	 * path should lead to a failed state.
	 *
	 * @param strategy
	 * @throws Exception
	 */
	@Theory
	public void checkMergeConflictingFilesWithTreeInIndex(MergeStrategy strategy)
			throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("0", "orig");
		git.add().addFilepattern("0").call();
		RevCommit first = git.commit().setMessage("added 0").call();

		writeTrashFile("0", "master");
		RevCommit masterCommit = git.commit().setAll(true)
				.setMessage("modified 0 on master").call();

		git.checkout().setCreateBranch(true).setStartPoint(first)
				.setName("side").call();
		writeTrashFile("0", "side");
		git.commit().setAll(true).setMessage("modified 0 on side").call();

		git.rm().addFilepattern("0").call();
		writeTrashFile("0/0", "side");
		git.add().addFilepattern("0/0").call();
		MergeResult mergeRes = git.merge().setStrategy(strategy)
				.include(masterCommit).call();
		assertEquals(MergeStatus.FAILED, mergeRes.getMergeStatus());
	}

	/**
	 * Merging two equal files when the index contains a tree for that path
	 * should lead to a failed state.
	 *
	 * @param strategy
	 * @throws Exception
	 */
	@Theory
	public void checkMergeMergeableFilesWithTreeInIndex(MergeStrategy strategy)
			throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("0", "orig");
		writeTrashFile("1", "1\n2\n3");
		git.add().addFilepattern("0").addFilepattern("1").call();
		RevCommit first = git.commit().setMessage("added 0, 1").call();

		writeTrashFile("1", "1master\n2\n3");
		RevCommit masterCommit = git.commit().setAll(true)
				.setMessage("modified 1 on master").call();

		git.checkout().setCreateBranch(true).setStartPoint(first)
				.setName("side").call();
		writeTrashFile("1", "1\n2\n3side");
		git.commit().setAll(true).setMessage("modified 1 on side").call();

		git.rm().addFilepattern("0").call();
		writeTrashFile("0/0", "modified");
		git.add().addFilepattern("0/0").call();
		try {
			git.merge().setStrategy(strategy).include(masterCommit).call();
			Assert.fail("Didn't get the expected exception");
		} catch (CheckoutConflictException e) {
			assertEquals(1, e.getConflictingPaths().size());
			assertEquals("0/0", e.getConflictingPaths().get(0));
		}
	}

	/**
	 * Merging after criss-cross merges. In this case we merge together two
	 * commits which have two equally good common ancestors
	 *
	 * @param strategy
	 * @throws Exception
	 */
	@Theory
	public void checkMergeCrissCross(MergeStrategy strategy) throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("1", "1\n2\n3");
		git.add().addFilepattern("1").call();
		RevCommit first = git.commit().setMessage("added 1").call();

		writeTrashFile("1", "1master\n2\n3");
		RevCommit masterCommit = git.commit().setAll(true)
				.setMessage("modified 1 on master").call();

		writeTrashFile("1", "1master2\n2\n3");
		git.commit().setAll(true)
				.setMessage("modified 1 on master again").call();

		git.checkout().setCreateBranch(true).setStartPoint(first)
				.setName("side").call();
		writeTrashFile("1", "1\n2\na\nb\nc\n3side");
		RevCommit sideCommit = git.commit().setAll(true)
				.setMessage("modified 1 on side").call();

		writeTrashFile("1", "1\n2\n3side2");
		git.commit().setAll(true)
				.setMessage("modified 1 on side again").call();

		MergeResult result = git.merge().setStrategy(strategy)
				.include(masterCommit).call();
		assertEquals(MergeStatus.MERGED, result.getMergeStatus());
		result.getNewHead();
		git.checkout().setName("master").call();
		result = git.merge().setStrategy(strategy).include(sideCommit).call();
		assertEquals(MergeStatus.MERGED, result.getMergeStatus());

		// we have two branches which are criss-cross merged. Try to merge the
		// tips. This should succeed with RecursiveMerge and fail with
		// ResolveMerge
		try {
			MergeResult mergeResult = git.merge().setStrategy(strategy)
					.include(git.getRepository().getRef("refs/heads/side"))
					.call();
			assertEquals(MergeStrategy.RECURSIVE, strategy);
			assertEquals(MergeResult.MergeStatus.MERGED,
					mergeResult.getMergeStatus());
			assertEquals("1master2\n2\n3side2\n", read("1"));
		} catch (JGitInternalException e) {
			assertEquals(MergeStrategy.RESOLVE, strategy);
			assertTrue(e.getCause() instanceof NoMergeBaseException);
			assertEquals(((NoMergeBaseException) e.getCause()).getReason(),
					MergeBaseFailureReason.MULTIPLE_MERGE_BASES_NOT_SUPPORTED);
		}
	}

	@Theory
	public void checkLockedFilesToBeDeleted(MergeStrategy strategy)
			throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("a.txt", "orig");
		writeTrashFile("b.txt", "orig");
		git.add().addFilepattern("a.txt").addFilepattern("b.txt").call();
		RevCommit first = git.commit().setMessage("added a.txt, b.txt").call();

		// modify and delete files on the master branch
		writeTrashFile("a.txt", "master");
		git.rm().addFilepattern("b.txt").call();
		RevCommit masterCommit = git.commit()
				.setMessage("modified a.txt, deleted b.txt").setAll(true)
				.call();

		// switch back to a side branch
		git.checkout().setCreateBranch(true).setStartPoint(first)
				.setName("side").call();
		writeTrashFile("c.txt", "side");
		git.add().addFilepattern("c.txt").call();
		git.commit().setMessage("added c.txt").call();

		// Get a handle to the the file so on windows it can't be deleted.
		FileInputStream fis = new FileInputStream(new File(db.getWorkTree(),
				"b.txt"));
		MergeResult mergeRes = git.merge().setStrategy(strategy)
				.include(masterCommit).call();
		if (mergeRes.getMergeStatus().equals(MergeStatus.FAILED)) {
			// probably windows
			assertEquals(1, mergeRes.getFailingPaths().size());
			assertEquals(MergeFailureReason.COULD_NOT_DELETE, mergeRes
					.getFailingPaths().get("b.txt"));
		}
		assertEquals("[a.txt, mode:100644, content:master]"
				+ "[c.txt, mode:100644, content:side]", indexState(CONTENT));
		fis.close();
	}

	@Theory
	public void checkForCorrectIndex(MergeStrategy strategy) throws Exception {
		File f;
		long lastTs4, lastTsIndex;
		Git git = Git.wrap(db);
		File indexFile = db.getIndexFile();

		// Create initial content and remember when the last file was written.
		f = writeTrashFiles(false, "orig", "orig", "1\n2\n3", "orig", "orig");
		lastTs4 = f.lastModified();

		// add all files, commit and check this doesn't update any working tree
		// files and that the index is in a new file system timer tick. Make
		// sure to wait long enough before adding so the index doesn't contain
		// racily clean entries
		fsTick(f);
		git.add().addFilepattern(".").call();
		RevCommit firstCommit = git.commit().setMessage("initial commit")
				.call();
		checkConsistentLastModified("0", "1", "2", "3", "4");
		checkModificationTimeStampOrder("1", "2", "3", "4", "<.git/index");
		assertEquals("Commit should not touch working tree file 4", lastTs4,
				new File(db.getWorkTree(), "4").lastModified());
		lastTsIndex = indexFile.lastModified();

		// Do modifications on the master branch. Then add and commit. This
		// should touch only "0", "2 and "3"
		fsTick(indexFile);
		f = writeTrashFiles(false, "master", null, "1master\n2\n3", "master",
				null);
		fsTick(f);
		git.add().addFilepattern(".").call();
		RevCommit masterCommit = git.commit().setMessage("master commit")
				.call();
		checkConsistentLastModified("0", "1", "2", "3", "4");
		checkModificationTimeStampOrder("1", "4", "*" + lastTs4, "<*"
				+ lastTsIndex, "<0", "2", "3", "<.git/index");
		lastTsIndex = indexFile.lastModified();

		// Checkout a side branch. This should touch only "0", "2 and "3"
		fsTick(indexFile);
		git.checkout().setCreateBranch(true).setStartPoint(firstCommit)
				.setName("side").call();
		checkConsistentLastModified("0", "1", "2", "3", "4");
		checkModificationTimeStampOrder("1", "4", "*" + lastTs4, "<*"
				+ lastTsIndex, "<0", "2", "3", ".git/index");
		lastTsIndex = indexFile.lastModified();

		// This checkout may have populated worktree and index so fast that we
		// may have smudged entries now. Check that we have the right content
		// and then rewrite the index to get rid of smudged state
		assertEquals("[0, mode:100644, content:orig]" //
				+ "[1, mode:100644, content:orig]" //
				+ "[2, mode:100644, content:1\n2\n3]" //
				+ "[3, mode:100644, content:orig]" //
				+ "[4, mode:100644, content:orig]", //
				indexState(CONTENT));
		fsTick(indexFile);
		f = writeTrashFiles(false, "orig", "orig", "1\n2\n3", "orig", "orig");
		lastTs4 = f.lastModified();
		fsTick(f);
		git.add().addFilepattern(".").call();
		checkConsistentLastModified("0", "1", "2", "3", "4");
		checkModificationTimeStampOrder("*" + lastTsIndex, "<0", "1", "2", "3",
				"4", "<.git/index");
		lastTsIndex = indexFile.lastModified();

		// Do modifications on the side branch. Touch only "1", "2 and "3"
		fsTick(indexFile);
		f = writeTrashFiles(false, null, "side", "1\n2\n3side", "side", null);
		fsTick(f);
		git.add().addFilepattern(".").call();
		git.commit().setMessage("side commit").call();
		checkConsistentLastModified("0", "1", "2", "3", "4");
		checkModificationTimeStampOrder("0", "4", "*" + lastTs4, "<*"
				+ lastTsIndex, "<1", "2", "3", "<.git/index");
		lastTsIndex = indexFile.lastModified();

		// merge master and side. Should only touch "0," "2" and "3"
		fsTick(indexFile);
		git.merge().setStrategy(strategy).include(masterCommit).call();
		checkConsistentLastModified("0", "1", "2", "4");
		checkModificationTimeStampOrder("4", "*" + lastTs4, "<1", "<*"
				+ lastTsIndex, "<0", "2", "3", ".git/index");
		assertEquals(
				"[0, mode:100644, content:master]" //
						+ "[1, mode:100644, content:side]" //
						+ "[2, mode:100644, content:1master\n2\n3side\n]" //
						+ "[3, mode:100644, stage:1, content:orig][3, mode:100644, stage:2, content:side][3, mode:100644, stage:3, content:master]" //
						+ "[4, mode:100644, content:orig]", //
				indexState(CONTENT));
	}

	// Assert that every specified index entry has the same last modification
	// timestamp as the associated file
	private void checkConsistentLastModified(String... pathes)
			throws IOException {
		DirCache dc = db.readDirCache();
		File workTree = db.getWorkTree();
		for (String path : pathes)
			assertEquals(
					"IndexEntry with path "
							+ path
							+ " has lastmodified with is different from the worktree file",
					new File(workTree, path).lastModified(), dc.getEntry(path)
							.getLastModified());
	}

	// Assert that modification timestamps of working tree files are as
	// expected. You may specify n files. It is asserted that every file
	// i+1 is not older than file i. If a path of file i+1 is prefixed with "<"
	// then this file must be younger then file i. A path "*<modtime>"
	// represents a file with a modification time of <modtime>
	// E.g. ("a", "b", "<c", "f/a.txt") means: a<=b<c<=f/a.txt
	private void checkModificationTimeStampOrder(String... pathes) {
		long lastMod = Long.MIN_VALUE;
		for (String p : pathes) {
			boolean strong = p.startsWith("<");
			boolean fixed = p.charAt(strong ? 1 : 0) == '*';
			p = p.substring((strong ? 1 : 0) + (fixed ? 1 : 0));
			long curMod = fixed ? Long.valueOf(p).longValue() : new File(
					db.getWorkTree(), p).lastModified();
			if (strong)
				assertTrue("path " + p + " is not younger than predecesssor",
						curMod > lastMod);
			else
				assertTrue("path " + p + " is older than predecesssor",
						curMod >= lastMod);
		}
	}
}
