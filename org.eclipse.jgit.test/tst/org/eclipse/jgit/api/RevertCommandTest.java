/*
 * Copyright (C) 2011, Robin Rosenberg
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
package org.eclipse.jgit.api;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.ReflogReader;
import org.junit.Test;

/**
 * Test revert command
 */
public class RevertCommandTest extends RepositoryTestCase {
	@Test
	public void testRevert() throws IOException, JGitInternalException,
			GitAPIException {
		Git git = new Git(db);

		writeTrashFile("a", "first line\nsec. line\nthird line\n");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("create a").call();

		writeTrashFile("b", "content\n");
		git.add().addFilepattern("b").call();
		git.commit().setMessage("create b").call();

		writeTrashFile("a", "first line\nsec. line\nthird line\nfourth line\n");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("enlarged a").call();

		writeTrashFile("a",
				"first line\nsecond line\nthird line\nfourth line\n");
		git.add().addFilepattern("a").call();
		RevCommit fixingA = git.commit().setMessage("fixed a").call();

		writeTrashFile("b", "first line\n");
		git.add().addFilepattern("b").call();
		git.commit().setMessage("fixed b").call();

		git.revert().include(fixingA).call();

		assertTrue(new File(db.getWorkTree(), "b").exists());
		checkFile(new File(db.getWorkTree(), "a"),
				"first line\nsec. line\nthird line\nfourth line\n");
		Iterator<RevCommit> history = git.log().call().iterator();
		RevCommit revertCommit = history.next();
		String expectedMessage = "Revert \"fixed a\"\n\n"
				+ "This reverts commit " + fixingA.getId().getName() + ".\n";
		assertEquals(expectedMessage, revertCommit.getFullMessage());
		assertEquals("fixed b", history.next().getFullMessage());
		assertEquals("fixed a", history.next().getFullMessage());
		assertEquals("enlarged a", history.next().getFullMessage());
		assertEquals("create b", history.next().getFullMessage());
		assertEquals("create a", history.next().getFullMessage());
		assertFalse(history.hasNext());

		ReflogReader reader = db.getReflogReader(Constants.HEAD);
		assertTrue(reader.getLastEntry().getComment()
				.startsWith("revert: Revert \""));
		reader = db.getReflogReader(db.getBranch());
		assertTrue(reader.getLastEntry().getComment()
				.startsWith("revert: Revert \""));

	}

	@Test
	public void testRevertDirtyIndex() throws Exception {
		Git git = new Git(db);
		RevCommit sideCommit = prepareRevert(git);

		// modify and add file a
		writeTrashFile("a", "a(modified)");
		git.add().addFilepattern("a").call();
		// do not commit

		doRevertAndCheckResult(git, sideCommit,
				MergeFailureReason.DIRTY_INDEX);
	}

	@Test
	public void testRevertDirtyWorktree() throws Exception {
		Git git = new Git(db);
		RevCommit sideCommit = prepareRevert(git);

		// modify file a
		writeTrashFile("a", "a(modified)");
		// do not add and commit

		doRevertAndCheckResult(git, sideCommit,
				MergeFailureReason.DIRTY_WORKTREE);
	}

	@Test
	public void testRevertConflictResolution() throws Exception {
		Git git = new Git(db);
		RevCommit sideCommit = prepareRevert(git);

		RevertCommand revert = git.revert();
		RevCommit newHead = revert.include(sideCommit.getId()).call();
		assertNull(newHead);
		MergeResult result = revert.getFailingResult();
		assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());
		assertTrue(new File(db.getDirectory(), Constants.MERGE_MSG).exists());
		assertEquals("Revert \"" + sideCommit.getShortMessage()
				+ "\"\n\nThis reverts commit " + sideCommit.getId().getName()
				+ ".\n\nConflicts:\n\ta\n",
				db.readMergeCommitMsg());
		assertTrue(new File(db.getDirectory(), Constants.REVERT_HEAD)
				.exists());
		assertEquals(sideCommit.getId(), db.readRevertHead());
		assertEquals(RepositoryState.REVERTING, db.getRepositoryState());

		// Resolve
		writeTrashFile("a", "a");
		git.add().addFilepattern("a").call();

		assertEquals(RepositoryState.REVERTING_RESOLVED,
				db.getRepositoryState());

		git.commit().setOnly("a").setMessage("resolve").call();

		assertEquals(RepositoryState.SAFE, db.getRepositoryState());
	}

	@Test
	public void testRevertkConflictReset() throws Exception {
		Git git = new Git(db);

		RevCommit sideCommit = prepareRevert(git);

		RevertCommand revert = git.revert();
		RevCommit newHead = revert.include(sideCommit.getId()).call();
		assertNull(newHead);
		MergeResult result = revert.getFailingResult();

		assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());
		assertEquals(RepositoryState.REVERTING, db.getRepositoryState());
		assertTrue(new File(db.getDirectory(), Constants.REVERT_HEAD)
				.exists());

		git.reset().setMode(ResetType.MIXED).setRef("HEAD").call();

		assertEquals(RepositoryState.SAFE, db.getRepositoryState());
		assertFalse(new File(db.getDirectory(), Constants.REVERT_HEAD)
				.exists());
	}

	@Test
	public void testRevertOverExecutableChangeOnNonExectuableFileSystem()
			throws Exception {
		Git git = new Git(db);
		File file = writeTrashFile("test.txt", "a");
		assertNotNull(git.add().addFilepattern("test.txt").call());
		assertNotNull(git.commit().setMessage("commit1").call());

		assertNotNull(git.checkout().setCreateBranch(true).setName("a").call());

		writeTrashFile("test.txt", "b");
		assertNotNull(git.add().addFilepattern("test.txt").call());
		RevCommit commit2 = git.commit().setMessage("commit2").call();
		assertNotNull(commit2);

		assertNotNull(git.checkout().setName(Constants.MASTER).call());

		DirCache cache = db.lockDirCache();
		cache.getEntry("test.txt").setFileMode(FileMode.EXECUTABLE_FILE);
		cache.write();
		assertTrue(cache.commit());
		cache.unlock();

		assertNotNull(git.commit().setMessage("commit3").call());

		db.getFS().setExecute(file, false);
		git.getRepository()
				.getConfig()
				.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_FILEMODE, false);

		RevertCommand revert = git.revert();
		RevCommit newHead = revert.include(commit2).call();
		assertNotNull(newHead);
	}

	@Test
	public void testRevertConflictMarkers() throws Exception {
		Git git = new Git(db);
		RevCommit sideCommit = prepareRevert(git);

		RevertCommand revert = git.revert();
		RevCommit newHead = revert.include(sideCommit.getId())
				.call();
		assertNull(newHead);
		MergeResult result = revert.getFailingResult();
		assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());

		String expected = "<<<<<<< master\na(latest)\n=======\na\n>>>>>>> ca96c31 second master\n";
		checkFile(new File(db.getWorkTree(), "a"), expected);
	}

	@Test
	public void testRevertOurCommitName() throws Exception {
		Git git = new Git(db);
		RevCommit sideCommit = prepareRevert(git);

		RevertCommand revert = git.revert();
		RevCommit newHead = revert.include(sideCommit.getId())
				.setOurCommitName("custom name").call();
		assertNull(newHead);
		MergeResult result = revert.getFailingResult();
		assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());

		String expected = "<<<<<<< custom name\na(latest)\n=======\na\n>>>>>>> ca96c31 second master\n";
		checkFile(new File(db.getWorkTree(), "a"), expected);
	}

	private RevCommit prepareRevert(final Git git) throws Exception {
		// create, add and commit file a
		writeTrashFile("a", "a");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("first master").call();

		// First commit
		checkoutBranch("refs/heads/master");
		// modify, add and commit file a
		writeTrashFile("a", "a(previous)");
		git.add().addFilepattern("a").call();
		RevCommit oldCommit = git.commit().setMessage("second master").call();

		// modify, add and commit file a
		writeTrashFile("a", "a(latest)");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("side").call();

		return oldCommit;
	}

	private void doRevertAndCheckResult(final Git git,
			final RevCommit sideCommit, final MergeFailureReason reason)
			throws Exception {
		// get current index state
		String indexState = indexState(CONTENT);

		// revert
		RevertCommand revert = git.revert();
		RevCommit resultCommit = revert.include(sideCommit.getId()).call();
		assertNull(resultCommit);
		MergeResult result = revert.getFailingResult();
		assertEquals(MergeStatus.FAILED, result.getMergeStatus());
		// staged file a causes DIRTY_INDEX
		assertEquals(1, result.getFailingPaths().size());
		assertEquals(reason, result.getFailingPaths().get("a"));
		assertEquals("a(modified)", read(new File(db.getWorkTree(), "a")));
		// index shall be unchanged
		assertEquals(indexState, indexState(CONTENT));
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());

		if (reason == null) {
			ReflogReader reader = db.getReflogReader(Constants.HEAD);
			assertTrue(reader.getLastEntry().getComment()
					.startsWith("revert: "));
			reader = db.getReflogReader(db.getBranch());
			assertTrue(reader.getLastEntry().getComment()
					.startsWith("revert: "));
		}
	}
}
