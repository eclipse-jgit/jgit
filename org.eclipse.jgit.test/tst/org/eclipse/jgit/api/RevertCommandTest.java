/*
 * Copyright (C) 2011, Robin Rosenberg and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.eclipse.jgit.lib.Constants.OBJECT_ID_ABBREV_STRING_LENGTH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

/**
 * Test revert command
 */
public class RevertCommandTest extends RepositoryTestCase {
	@Test
	public void testRevert() throws IOException, JGitInternalException,
			GitAPIException {
		try (Git git = new Git(db)) {
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

			assertEquals(RepositoryState.SAFE, db.getRepositoryState());

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

	}

	@Test
	public void testRevertMultiple() throws IOException, JGitInternalException,
			GitAPIException {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "first\n");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("add first").call();

			writeTrashFile("a", "first\nsecond\n");
			git.add().addFilepattern("a").call();
			RevCommit secondCommit = git.commit().setMessage("add second").call();

			writeTrashFile("a", "first\nsecond\nthird\n");
			git.add().addFilepattern("a").call();
			RevCommit thirdCommit = git.commit().setMessage("add third").call();

			git.revert().include(thirdCommit).include(secondCommit).call();

			assertEquals(RepositoryState.SAFE, db.getRepositoryState());

			checkFile(new File(db.getWorkTree(), "a"), "first\n");
			Iterator<RevCommit> history = git.log().call().iterator();
			RevCommit revertCommit = history.next();
			String expectedMessage = "Revert \"add second\"\n\n"
					+ "This reverts commit "
					+ secondCommit.getId().getName() + ".\n";
			assertEquals(expectedMessage, revertCommit.getFullMessage());
			revertCommit = history.next();
			expectedMessage = "Revert \"add third\"\n\n"
					+ "This reverts commit " + thirdCommit.getId().getName()
					+ ".\n";
			assertEquals(expectedMessage, revertCommit.getFullMessage());
			assertEquals("add third", history.next().getFullMessage());
			assertEquals("add second", history.next().getFullMessage());
			assertEquals("add first", history.next().getFullMessage());
			assertFalse(history.hasNext());

			ReflogReader reader = db.getReflogReader(Constants.HEAD);
			assertTrue(reader.getLastEntry().getComment()
					.startsWith("revert: Revert \""));
			reader = db.getReflogReader(db.getBranch());
			assertTrue(reader.getLastEntry().getComment()
					.startsWith("revert: Revert \""));
		}

	}

	@Test
	public void testRevertMultipleWithFail() throws IOException,
			JGitInternalException, GitAPIException {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "first\n");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("add first").call();

			writeTrashFile("a", "first\nsecond\n");
			git.add().addFilepattern("a").call();
			RevCommit secondCommit = git.commit().setMessage("add second").call();

			writeTrashFile("a", "first\nsecond\nthird\n");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("add third").call();

			writeTrashFile("a", "first\nsecond\nthird\nfourth\n");
			git.add().addFilepattern("a").call();
			RevCommit fourthCommit = git.commit().setMessage("add fourth").call();

			git.revert().include(fourthCommit).include(secondCommit).call();

			// not SAFE because it failed
			assertEquals(RepositoryState.REVERTING, db.getRepositoryState());

			checkFile(new File(db.getWorkTree(), "a"), "first\n"
					+ "<<<<<<< master\n" + "second\n" + "third\n" + "=======\n"
					+ ">>>>>>> " + secondCommit.getId().abbreviate(OBJECT_ID_ABBREV_STRING_LENGTH).name()
					+ " add second\n");
			Iterator<RevCommit> history = git.log().call().iterator();
			RevCommit revertCommit = history.next();
			String expectedMessage = "Revert \"add fourth\"\n\n"
					+ "This reverts commit " + fourthCommit.getId().getName()
					+ ".\n";
			assertEquals(expectedMessage, revertCommit.getFullMessage());
			assertEquals("add fourth", history.next().getFullMessage());
			assertEquals("add third", history.next().getFullMessage());
			assertEquals("add second", history.next().getFullMessage());
			assertEquals("add first", history.next().getFullMessage());
			assertFalse(history.hasNext());

			ReflogReader reader = db.getReflogReader(Constants.HEAD);
			assertTrue(reader.getLastEntry().getComment()
					.startsWith("revert: Revert \""));
			reader = db.getReflogReader(db.getBranch());
			assertTrue(reader.getLastEntry().getComment()
					.startsWith("revert: Revert \""));
		}

	}

	@Test
	public void testRevertDirtyIndex() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit sideCommit = prepareRevert(git);

			// modify and add file a
			writeTrashFile("a", "a(modified)");
			git.add().addFilepattern("a").call();
			// do not commit

			doRevertAndCheckResult(git, sideCommit,
					MergeFailureReason.DIRTY_INDEX);
		}
}

	@Test
	public void testRevertDirtyWorktree() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit sideCommit = prepareRevert(git);

			// modify file a
			writeTrashFile("a", "a(modified)");
			// do not add and commit

			doRevertAndCheckResult(git, sideCommit,
					MergeFailureReason.DIRTY_WORKTREE);
		}
	}

	@Test
	public void testRevertConflictResolution() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit sideCommit = prepareRevert(git);

			RevertCommand revert = git.revert();
			RevCommit newHead = revert.include(sideCommit.getId()).call();
			assertNull(newHead);
			MergeResult result = revert.getFailingResult();
			assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());
			assertTrue(new File(db.getDirectory(), Constants.MERGE_MSG).exists());
			assertEquals("Revert \"" + sideCommit.getShortMessage()
					+ "\"\n\nThis reverts commit " + sideCommit.getId().getName()
					+ ".\n\n# Conflicts:\n#\ta\n",
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
	}

	@Test
	public void testRevertConflictReset() throws Exception {
		try (Git git = new Git(db)) {
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
	}

	@Test
	public void testRevertOverExecutableChangeOnNonExectuableFileSystem()
			throws Exception {
		try (Git git = new Git(db)) {
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
	}

	@Test
	public void testRevertConflictMarkers() throws Exception {
		try (Git git = new Git(db)) {
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
	}

	@Test
	public void testRevertOurCommitName() throws Exception {
		try (Git git = new Git(db)) {
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
	}

	private RevCommit prepareRevert(Git git) throws Exception {
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
