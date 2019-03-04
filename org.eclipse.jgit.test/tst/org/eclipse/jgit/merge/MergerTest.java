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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.NoMergeBaseException;
import org.eclipse.jgit.errors.NoMergeBaseException.MergeBaseFailureReason;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Assert;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class MergerTest extends RepositoryTestCase {

	@DataPoints
	public static MergeStrategy[] strategiesUnderTest = new MergeStrategy[] {
			MergeStrategy.RECURSIVE, MergeStrategy.RESOLVE };

	@Theory
	public void failingDeleteOfDirectoryWithUntrackedContent(
			MergeStrategy strategy) throws Exception {
		File folder1 = new File(db.getWorkTree(), "folder1");
		FileUtils.mkdir(folder1);
		File file = new File(folder1, "file1.txt");
		write(file, "folder1--file1.txt");
		file = new File(folder1, "file2.txt");
		write(file, "folder1--file2.txt");

		try (Git git = new Git(db)) {
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
		assertEquals("[d/1, mode:100644, content:1master\n2\n3side]",
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
	 * A tracked file is replaced by a folder in THEIRS.
	 *
	 * @param strategy
	 * @throws Exception
	 */
	@Theory
	public void checkFileReplacedByFolderInTheirs(MergeStrategy strategy)
			throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("sub", "file");
		git.add().addFilepattern("sub").call();
		RevCommit first = git.commit().setMessage("initial").call();

		git.checkout().setCreateBranch(true).setStartPoint(first)
				.setName("side").call();

		git.rm().addFilepattern("sub").call();
		writeTrashFile("sub/file", "subfile");
		git.add().addFilepattern("sub/file").call();
		RevCommit masterCommit = git.commit().setMessage("file -> folder")
				.call();

		git.checkout().setName("master").call();
		writeTrashFile("noop", "other");
		git.add().addFilepattern("noop").call();
		git.commit().setAll(true).setMessage("noop").call();

		MergeResult mergeRes = git.merge().setStrategy(strategy)
				.include(masterCommit).call();
		assertEquals(MergeStatus.MERGED, mergeRes.getMergeStatus());
		assertEquals(
				"[noop, mode:100644, content:other][sub/file, mode:100644, content:subfile]",
				indexState(CONTENT));
	}

	/**
	 * A tracked file is replaced by a folder in OURS.
	 *
	 * @param strategy
	 * @throws Exception
	 */
	@Theory
	public void checkFileReplacedByFolderInOurs(MergeStrategy strategy)
			throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("sub", "file");
		git.add().addFilepattern("sub").call();
		RevCommit first = git.commit().setMessage("initial").call();

		git.checkout().setCreateBranch(true).setStartPoint(first)
				.setName("side").call();
		writeTrashFile("noop", "other");
		git.add().addFilepattern("noop").call();
		RevCommit sideCommit = git.commit().setAll(true).setMessage("noop")
				.call();

		git.checkout().setName("master").call();
		git.rm().addFilepattern("sub").call();
		writeTrashFile("sub/file", "subfile");
		git.add().addFilepattern("sub/file").call();
		git.commit().setMessage("file -> folder")
				.call();

		MergeResult mergeRes = git.merge().setStrategy(strategy)
				.include(sideCommit).call();
		assertEquals(MergeStatus.MERGED, mergeRes.getMergeStatus());
		assertEquals(
				"[noop, mode:100644, content:other][sub/file, mode:100644, content:subfile]",
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

	@Theory
	public void mergeWithCrlfAutoCrlfTrue(MergeStrategy strategy)
			throws IOException, GitAPIException {
		Git git = Git.wrap(db);
		db.getConfig().setString("core", null, "autocrlf", "true");
		db.getConfig().save();
		writeTrashFile("crlf.txt", "a crlf file\r\n");
		git.add().addFilepattern("crlf.txt").call();
		git.commit().setMessage("base").call();

		git.branchCreate().setName("brancha").call();

		writeTrashFile("crlf.txt", "a crlf file\r\na second line\r\n");
		git.add().addFilepattern("crlf.txt").call();
		git.commit().setMessage("on master").call();

		git.checkout().setName("brancha").call();
		File testFile = writeTrashFile("crlf.txt",
				"a first line\r\na crlf file\r\n");
		git.add().addFilepattern("crlf.txt").call();
		git.commit().setMessage("on brancha").call();

		MergeResult mergeResult = git.merge().setStrategy(strategy)
				.include(db.resolve("master")).call();
		assertEquals(MergeResult.MergeStatus.MERGED,
				mergeResult.getMergeStatus());
		checkFile(testFile, "a first line\r\na crlf file\r\na second line\r\n");
		assertEquals(
				"[crlf.txt, mode:100644, content:a first line\na crlf file\na second line\n]",
				indexState(CONTENT));
	}

	@Theory
	public void rebaseWithCrlfAutoCrlfTrue(MergeStrategy strategy)
			throws IOException, GitAPIException {
		Git git = Git.wrap(db);
		db.getConfig().setString("core", null, "autocrlf", "true");
		db.getConfig().save();
		writeTrashFile("crlf.txt", "line 1\r\nline 2\r\nline 3\r\n");
		git.add().addFilepattern("crlf.txt").call();
		RevCommit first = git.commit().setMessage("base").call();

		git.checkout().setCreateBranch(true).setStartPoint(first)
				.setName("brancha").call();

		File testFile = writeTrashFile("crlf.txt",
				"line 1\r\nmodified line\r\nline 3\r\n");
		git.add().addFilepattern("crlf.txt").call();
		git.commit().setMessage("on brancha").call();

		git.checkout().setName("master").call();
		File otherFile = writeTrashFile("otherfile.txt", "a line\r\n");
		git.add().addFilepattern("otherfile.txt").call();
		git.commit().setMessage("on master").call();

		git.checkout().setName("brancha").call();
		checkFile(testFile, "line 1\r\nmodified line\r\nline 3\r\n");
		assertFalse(otherFile.exists());

		RebaseResult rebaseResult = git.rebase().setStrategy(strategy)
				.setUpstream(db.resolve("master")).call();
		assertEquals(RebaseResult.Status.OK, rebaseResult.getStatus());
		checkFile(testFile, "line 1\r\nmodified line\r\nline 3\r\n");
		checkFile(otherFile, "a line\r\n");
		assertEquals(
				"[crlf.txt, mode:100644, content:line 1\nmodified line\nline 3\n]"
						+ "[otherfile.txt, mode:100644, content:a line\n]",
				indexState(CONTENT));
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
	 * state.
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
	 * Merging two equal subtrees with an incore merger should lead to a merged
	 * state, without using a Repository (the 'Gerrit' use case).
	 *
	 * @param strategy
	 * @throws Exception
	 */
	@Theory
	public void checkMergeEqualTreesInCore_noRepo(MergeStrategy strategy)
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

		try (ObjectInserter ins = db.newObjectInserter()) {
			ThreeWayMerger resolveMerger =
					(ThreeWayMerger) strategy.newMerger(ins, db.getConfig());
			boolean noProblems = resolveMerger.merge(masterCommit, sideCommit);
			assertTrue(noProblems);
		}
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

	@Theory
	public void checkContentMergeNoConflict(MergeStrategy strategy)
			throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("file", "1\n2\n3");
		git.add().addFilepattern("file").call();
		RevCommit first = git.commit().setMessage("added file").call();

		writeTrashFile("file", "1master\n2\n3");
		git.commit().setAll(true).setMessage("modified file on master").call();

		git.checkout().setCreateBranch(true).setStartPoint(first)
				.setName("side").call();
		writeTrashFile("file", "1\n2\n3side");
		RevCommit sideCommit = git.commit().setAll(true)
				.setMessage("modified file on side").call();

		git.checkout().setName("master").call();
		MergeResult result =
				git.merge().setStrategy(strategy).include(sideCommit).call();
		assertEquals(MergeStatus.MERGED, result.getMergeStatus());
		String expected = "1master\n2\n3side";
		assertEquals(expected, read("file"));
	}

	@Theory
	public void checkContentMergeNoConflict_noRepo(MergeStrategy strategy)
			throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("file", "1\n2\n3");
		git.add().addFilepattern("file").call();
		RevCommit first = git.commit().setMessage("added file").call();

		writeTrashFile("file", "1master\n2\n3");
		RevCommit masterCommit = git.commit().setAll(true)
				.setMessage("modified file on master").call();

		git.checkout().setCreateBranch(true).setStartPoint(first)
				.setName("side").call();
		writeTrashFile("file", "1\n2\n3side");
		RevCommit sideCommit = git.commit().setAll(true)
				.setMessage("modified file on side").call();

		try (ObjectInserter ins = db.newObjectInserter()) {
			ResolveMerger merger =
					(ResolveMerger) strategy.newMerger(ins, db.getConfig());
			boolean noProblems = merger.merge(masterCommit, sideCommit);
			assertTrue(noProblems);
			assertEquals("1master\n2\n3side",
					readBlob(merger.getResultTreeId(), "file"));
		}
	}


	/**
	 * Merging a change involving large binary files should short-circuit reads.
	 *
	 * @param strategy
	 * @throws Exception
	 */
	@Theory
	public void checkContentMergeLargeBinaries(MergeStrategy strategy) throws Exception {
		Git git = Git.wrap(db);
		final int LINELEN = 72;

		// setup a merge that would work correctly if we disconsider the stray '\0'
		// that the file contains near the start.
		byte[] binary = new byte[LINELEN * 2000];
		for (int i = 0; i < binary.length; i++) {
			binary[i] = (byte)((i % LINELEN) == 0 ? '\n' : 'x');
		}
		binary[50] = '\0';

		writeTrashFile("file", new String(binary, UTF_8));
		git.add().addFilepattern("file").call();
		RevCommit first = git.commit().setMessage("added file").call();

		// Generate an edit in a single line.
		int idx = LINELEN * 1200 + 1;
		byte save = binary[idx];
		binary[idx] = '@';
		writeTrashFile("file", new String(binary, UTF_8));

		binary[idx] = save;
		git.add().addFilepattern("file").call();
		RevCommit masterCommit = git.commit().setAll(true)
			.setMessage("modified file l 1200").call();

		git.checkout().setCreateBranch(true).setStartPoint(first).setName("side").call();
		binary[LINELEN * 1500 + 1] = '!';
		writeTrashFile("file", new String(binary, UTF_8));
		git.add().addFilepattern("file").call();
		RevCommit sideCommit = git.commit().setAll(true)
			.setMessage("modified file l 1500").call();

		try (ObjectInserter ins = db.newObjectInserter()) {
			// Check that we don't read the large blobs.
			ObjectInserter forbidInserter = new ObjectInserter.Filter() {
				@Override
				protected ObjectInserter delegate() {
					return ins;
				}

				@Override
				public ObjectReader newReader() {
					return new BigReadForbiddenReader(super.newReader(), 8000);
				}
			};

			ResolveMerger merger =
				(ResolveMerger) strategy.newMerger(forbidInserter, db.getConfig());
			boolean noProblems = merger.merge(masterCommit, sideCommit);
			assertFalse(noProblems);
		}
	}

	/**
	 * Throws an exception if reading beyond limit.
	 */
	static class BigReadForbiddenStream extends ObjectStream.Filter {
		int limit;

		BigReadForbiddenStream(ObjectStream orig, int limit) {
			super(orig.getType(), orig.getSize(), orig);
			this.limit = limit;
		}

		@Override
		public long skip(long n) throws IOException {
			limit -= n;
			if (limit < 0) {
				throw new IllegalStateException();
			}

			return super.skip(n);
		}

		@Override
		public int read() throws IOException {
			int r = super.read();
			limit--;
			if (limit < 0) {
				throw new IllegalStateException();
			}
			return r;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int n = super.read(b, off, len);
			limit -= n;
			if (limit < 0) {
				throw new IllegalStateException();
			}
			return n;
		}
	}

	static class BigReadForbiddenReader extends ObjectReader.Filter {
		ObjectReader delegate;
		int limit;

		@Override
		protected ObjectReader delegate() {
			return delegate;
		}

		BigReadForbiddenReader(ObjectReader delegate, int limit) {
			this.delegate = delegate;
			this.limit = limit;
		}

		@Override
		public ObjectLoader open(AnyObjectId objectId, int typeHint) throws IOException {
			ObjectLoader orig = super.open(objectId, typeHint);
			return new ObjectLoader.Filter() {
				@Override
				protected ObjectLoader delegate() {
					return orig;
				}

				@Override
				public ObjectStream openStream() throws IOException {
					ObjectStream os = orig.openStream();
					return new BigReadForbiddenStream(os, limit);
				}
			};
		}
	}

	@Theory
	public void checkContentMergeConflict(MergeStrategy strategy)
			throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("file", "1\n2\n3");
		git.add().addFilepattern("file").call();
		RevCommit first = git.commit().setMessage("added file").call();

		writeTrashFile("file", "1master\n2\n3");
		git.commit().setAll(true).setMessage("modified file on master").call();

		git.checkout().setCreateBranch(true).setStartPoint(first)
				.setName("side").call();
		writeTrashFile("file", "1side\n2\n3");
		RevCommit sideCommit = git.commit().setAll(true)
				.setMessage("modified file on side").call();

		git.checkout().setName("master").call();
		MergeResult result =
				git.merge().setStrategy(strategy).include(sideCommit).call();
		assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());
		String expected = "<<<<<<< HEAD\n"
				+ "1master\n"
				+ "=======\n"
				+ "1side\n"
				+ ">>>>>>> " + sideCommit.name() + "\n"
				+ "2\n"
				+ "3";
		assertEquals(expected, read("file"));
	}

	@Theory
	public void checkContentMergeConflict_noTree(MergeStrategy strategy)
			throws Exception {
		Git git = Git.wrap(db);

		writeTrashFile("file", "1\n2\n3");
		git.add().addFilepattern("file").call();
		RevCommit first = git.commit().setMessage("added file").call();

		writeTrashFile("file", "1master\n2\n3");
		RevCommit masterCommit = git.commit().setAll(true)
				.setMessage("modified file on master").call();

		git.checkout().setCreateBranch(true).setStartPoint(first)
				.setName("side").call();
		writeTrashFile("file", "1side\n2\n3");
		RevCommit sideCommit = git.commit().setAll(true)
				.setMessage("modified file on side").call();

		try (ObjectInserter ins = db.newObjectInserter()) {
			ResolveMerger merger =
					(ResolveMerger) strategy.newMerger(ins, db.getConfig());
			boolean noProblems = merger.merge(masterCommit, sideCommit);
			assertFalse(noProblems);
			assertEquals(Arrays.asList("file"), merger.getUnmergedPaths());

			MergeFormatter fmt = new MergeFormatter();
			merger.getMergeResults().get("file");
			try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				fmt.formatMerge(out, merger.getMergeResults().get("file"),
						"BASE", "OURS", "THEIRS", UTF_8);
				String expected = "<<<<<<< OURS\n"
						+ "1master\n"
						+ "=======\n"
						+ "1side\n"
						+ ">>>>>>> THEIRS\n"
						+ "2\n"
						+ "3";
				assertEquals(expected, new String(out.toByteArray(), UTF_8));
			}
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
					.include(git.getRepository().exactRef("refs/heads/side"))
					.call();
			assertEquals(MergeStrategy.RECURSIVE, strategy);
			assertEquals(MergeResult.MergeStatus.MERGED,
					mergeResult.getMergeStatus());
			assertEquals("1master2\n2\n3side2", read("1"));
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

		// Get a handle to the file so on windows it can't be deleted.
		try (FileInputStream fis = new FileInputStream(
				new File(db.getWorkTree(), "b.txt"))) {
			MergeResult mergeRes = git.merge().setStrategy(strategy)
					.include(masterCommit).call();
			if (mergeRes.getMergeStatus().equals(MergeStatus.FAILED)) {
				// probably windows
				assertEquals(1, mergeRes.getFailingPaths().size());
				assertEquals(MergeFailureReason.COULD_NOT_DELETE,
						mergeRes.getFailingPaths().get("b.txt"));
			}
			assertEquals(
					"[a.txt, mode:100644, content:master]"
							+ "[c.txt, mode:100644, content:side]",
					indexState(CONTENT));
		}
	}

	@Theory
	public void checkForCorrectIndex(MergeStrategy strategy) throws Exception {
		File f;
		long lastTs4, lastTsIndex;
		Git git = Git.wrap(db);
		File indexFile = db.getIndexFile();

		// Create initial content and remember when the last file was written.
		f = writeTrashFiles(false, "orig", "orig", "1\n2\n3", "orig", "orig");
		lastTs4 = FS.DETECTED.lastModified(f);

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
				FS.DETECTED.lastModified(new File(db.getWorkTree(), "4")));
		lastTsIndex = FS.DETECTED.lastModified(indexFile);

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
		lastTsIndex = FS.DETECTED.lastModified(indexFile);

		// Checkout a side branch. This should touch only "0", "2 and "3"
		fsTick(indexFile);
		git.checkout().setCreateBranch(true).setStartPoint(firstCommit)
				.setName("side").call();
		checkConsistentLastModified("0", "1", "2", "3", "4");
		checkModificationTimeStampOrder("1", "4", "*" + lastTs4, "<*"
				+ lastTsIndex, "<0", "2", "3", ".git/index");
		lastTsIndex = FS.DETECTED.lastModified(indexFile);

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
		lastTs4 = FS.DETECTED.lastModified(f);
		fsTick(f);
		git.add().addFilepattern(".").call();
		checkConsistentLastModified("0", "1", "2", "3", "4");
		checkModificationTimeStampOrder("*" + lastTsIndex, "<0", "1", "2", "3",
				"4", "<.git/index");
		lastTsIndex = FS.DETECTED.lastModified(indexFile);

		// Do modifications on the side branch. Touch only "1", "2 and "3"
		fsTick(indexFile);
		f = writeTrashFiles(false, null, "side", "1\n2\n3side", "side", null);
		fsTick(f);
		git.add().addFilepattern(".").call();
		git.commit().setMessage("side commit").call();
		checkConsistentLastModified("0", "1", "2", "3", "4");
		checkModificationTimeStampOrder("0", "4", "*" + lastTs4, "<*"
				+ lastTsIndex, "<1", "2", "3", "<.git/index");
		lastTsIndex = FS.DETECTED.lastModified(indexFile);

		// merge master and side. Should only touch "0," "2" and "3"
		fsTick(indexFile);
		git.merge().setStrategy(strategy).include(masterCommit).call();
		checkConsistentLastModified("0", "1", "2", "4");
		checkModificationTimeStampOrder("4", "*" + lastTs4, "<1", "<*"
				+ lastTsIndex, "<0", "2", "3", ".git/index");
		assertEquals(
				"[0, mode:100644, content:master]" //
						+ "[1, mode:100644, content:side]" //
						+ "[2, mode:100644, content:1master\n2\n3side]" //
						+ "[3, mode:100644, stage:1, content:orig][3, mode:100644, stage:2, content:side][3, mode:100644, stage:3, content:master]" //
						+ "[4, mode:100644, content:orig]", //
				indexState(CONTENT));
	}

	/**
	 * Merging two conflicting submodules when the index does not contain any
	 * entry for that submodule.
	 *
	 * @param strategy
	 * @throws Exception
	 */
	@Theory
	public void checkMergeConflictingSubmodulesWithoutIndex(
			MergeStrategy strategy) throws Exception {
		Git git = Git.wrap(db);
		writeTrashFile("initial", "initial");
		git.add().addFilepattern("initial").call();
		RevCommit initial = git.commit().setMessage("initial").call();

		writeSubmodule("one", ObjectId
				.fromString("1000000000000000000000000000000000000000"));
		git.add().addFilepattern(Constants.DOT_GIT_MODULES).call();
		RevCommit right = git.commit().setMessage("added one").call();

		// a second commit in the submodule

		git.checkout().setStartPoint(initial).setName("left")
				.setCreateBranch(true).call();
		writeSubmodule("one", ObjectId
				.fromString("2000000000000000000000000000000000000000"));

		git.add().addFilepattern(Constants.DOT_GIT_MODULES).call();
		git.commit().setMessage("a different one").call();

		MergeResult result = git.merge().setStrategy(strategy).include(right)
				.call();

		assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());
		Map<String, int[][]> conflicts = result.getConflicts();
		assertEquals(1, conflicts.size());
		assertNotNull(conflicts.get("one"));
	}

	/**
	 * Merging two non-conflicting submodules when the index does not contain
	 * any entry for either submodule.
	 *
	 * @param strategy
	 * @throws Exception
	 */
	@Theory
	public void checkMergeNonConflictingSubmodulesWithoutIndex(
			MergeStrategy strategy) throws Exception {
		Git git = Git.wrap(db);
		writeTrashFile("initial", "initial");
		git.add().addFilepattern("initial").call();

		writeSubmodule("one", ObjectId
				.fromString("1000000000000000000000000000000000000000"));

		// Our initial commit should include a .gitmodules with a bunch of
		// comment lines, so that
		// we don't have a content merge issue when we add a new submodule at
		// the top and a different
		// one at the bottom. This is sort of a hack, but it should allow
		// add/add submodule merges
		String existing = read(Constants.DOT_GIT_MODULES);
		String context = "\n# context\n# more context\n# yet more context\n";
		write(new File(db.getWorkTree(), Constants.DOT_GIT_MODULES),
				existing + context + context + context);

		git.add().addFilepattern(Constants.DOT_GIT_MODULES).call();
		RevCommit initial = git.commit().setMessage("initial").call();

		writeSubmodule("two", ObjectId
				.fromString("1000000000000000000000000000000000000000"));
		git.add().addFilepattern(Constants.DOT_GIT_MODULES).call();

		RevCommit right = git.commit().setMessage("added two").call();

		git.checkout().setStartPoint(initial).setName("left")
				.setCreateBranch(true).call();

		// we need to manually create the submodule for three for the
		// .gitmodules hackery
		addSubmoduleToIndex("three", ObjectId
				.fromString("1000000000000000000000000000000000000000"));
		new File(db.getWorkTree(), "three").mkdir();

		existing = read(Constants.DOT_GIT_MODULES);
		String three = "[submodule \"three\"]\n\tpath = three\n\turl = "
				+ db.getDirectory().toURI() + "\n";
		write(new File(db.getWorkTree(), Constants.DOT_GIT_MODULES),
				three + existing);

		git.add().addFilepattern(Constants.DOT_GIT_MODULES).call();
		git.commit().setMessage("a different one").call();

		MergeResult result = git.merge().setStrategy(strategy).include(right)
				.call();

		assertNull(result.getCheckoutConflicts());
		assertNull(result.getFailingPaths());
		for (String dir : Arrays.asList("one", "two", "three")) {
			assertTrue(new File(db.getWorkTree(), dir).isDirectory());
		}
	}

	private void writeSubmodule(String path, ObjectId commit)
			throws IOException, ConfigInvalidException {
		addSubmoduleToIndex(path, commit);
		new File(db.getWorkTree(), path).mkdir();

		StoredConfig config = db.getConfig();
		config.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_URL,
				db.getDirectory().toURI().toString());
		config.save();

		FileBasedConfig modulesConfig = new FileBasedConfig(
				new File(db.getWorkTree(), Constants.DOT_GIT_MODULES),
				db.getFS());
		modulesConfig.load();
		modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
				ConfigConstants.CONFIG_KEY_PATH, path);
		modulesConfig.save();

	}

	private void addSubmoduleToIndex(String path, ObjectId commit)
			throws IOException {
		DirCache cache = db.lockDirCache();
		DirCacheEditor editor = cache.editor();
		editor.add(new DirCacheEditor.PathEdit(path) {

			@Override
			public void apply(DirCacheEntry ent) {
				ent.setFileMode(FileMode.GITLINK);
				ent.setObjectId(commit);
			}
		});
		editor.commit();
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
					FS.DETECTED.lastModified(new File(workTree, path)), dc.getEntry(path)
							.getLastModified());
	}

	// Assert that modification timestamps of working tree files are as
	// expected. You may specify n files. It is asserted that every file
	// i+1 is not older than file i. If a path of file i+1 is prefixed with "<"
	// then this file must be younger then file i. A path "*<modtime>"
	// represents a file with a modification time of <modtime>
	// E.g. ("a", "b", "<c", "f/a.txt") means: a<=b<c<=f/a.txt
	private void checkModificationTimeStampOrder(String... pathes)
			throws IOException {
		long lastMod = Long.MIN_VALUE;
		for (String p : pathes) {
			boolean strong = p.startsWith("<");
			boolean fixed = p.charAt(strong ? 1 : 0) == '*';
			p = p.substring((strong ? 1 : 0) + (fixed ? 1 : 0));
			long curMod = fixed ? Long.valueOf(p).longValue()
					: FS.DETECTED.lastModified(new File(db.getWorkTree(), p));
			if (strong)
				assertTrue("path " + p + " is not younger than predecesssor",
						curMod > lastMod);
			else
				assertTrue("path " + p + " is older than predecesssor",
						curMod >= lastMod);
		}
	}

	private String readBlob(ObjectId treeish, String path) throws Exception {
		try (TestRepository<?> tr = new TestRepository<>(db)) {
			RevWalk rw = tr.getRevWalk();
			RevTree tree = rw.parseTree(treeish);
			RevObject obj = tr.get(tree, path);
			if (obj == null) {
				return null;
			}
			return new String(
					rw.getObjectReader().open(obj, OBJ_BLOB).getBytes(), UTF_8);
		}
	}
}
