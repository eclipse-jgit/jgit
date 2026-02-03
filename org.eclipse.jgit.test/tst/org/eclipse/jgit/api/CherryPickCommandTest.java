/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.eclipse.jgit.api.CherryPickCommitMessageProvider.ORIGINAL;
import static org.eclipse.jgit.api.CherryPickCommitMessageProvider.ORIGINAL_WITH_REFERENCE;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_CONFLICTSTYLE;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_MERGE_SECTION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.eclipse.jgit.api.CherryPickResult.CherryPickStatus;
import org.eclipse.jgit.api.MergeCommand.ConflictStyle;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.MultipleParentsNotAllowedException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.events.ChangeRecorder;
import org.eclipse.jgit.events.ListenerHandle;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.merge.ContentMergeStrategy;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

/**
 * Test cherry-pick command
 */
public class CherryPickCommandTest extends RepositoryTestCase {
	@Test
	public void testCherryPick() throws IOException, JGitInternalException,
			GitAPIException {
		doTestCherryPick(false);
	}

	@Test
	public void testCherryPickNoCommit() throws IOException,
			JGitInternalException, GitAPIException {
		doTestCherryPick(true);
	}

	private void doTestCherryPick(boolean noCommit) throws IOException,
			JGitInternalException,
			GitAPIException {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "first line\nsec. line\nthird line\n");
			git.add().addFilepattern("a").call();
			RevCommit firstCommit = git.commit().setMessage("create a").call();

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

			git.branchCreate().setName("side").setStartPoint(firstCommit).call();
			checkoutBranch("refs/heads/side");

			writeTrashFile("a", "first line\nsec. line\nthird line\nfeature++\n");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("enhanced a").call();

			CherryPickResult pickResult = git.cherryPick().include(fixingA)
					.setNoCommit(noCommit).call();

			assertEquals(CherryPickStatus.OK, pickResult.getStatus());
			assertFalse(new File(db.getWorkTree(), "b").exists());
			checkFile(new File(db.getWorkTree(), "a"),
					"first line\nsecond line\nthird line\nfeature++\n");
			Iterator<RevCommit> history = git.log().call().iterator();
			if (!noCommit)
				assertEquals("fixed a", history.next().getFullMessage());
			assertEquals("enhanced a", history.next().getFullMessage());
			assertEquals("create a", history.next().getFullMessage());
			assertFalse(history.hasNext());
		}
	}

	@Test
	public void testSequentialCherryPick()
			throws IOException, JGitInternalException, GitAPIException {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "first line\nsec. line\nthird line\n");
			git.add().addFilepattern("a").call();
			RevCommit firstCommit = git.commit().setMessage("create a").call();

			writeTrashFile("a",
					"first line\nsec. line\nthird line\nfourth line\n");
			git.add().addFilepattern("a").call();
			RevCommit enlargingA = git.commit().setMessage("enlarged a").call();

			writeTrashFile("a",
					"first line\nsecond line\nthird line\nfourth line\n");
			git.add().addFilepattern("a").call();
			RevCommit fixingA = git.commit().setMessage("fixed a").call();

			git.branchCreate().setName("side").setStartPoint(firstCommit)
					.call();
			checkoutBranch("refs/heads/side");

			writeTrashFile("b", "nothing to do with a");
			git.add().addFilepattern("b").call();
			git.commit().setMessage("create b").call();

			CherryPickResult result = git.cherryPick().include(enlargingA)
					.include(fixingA).call();
			assertEquals(CherryPickResult.CherryPickStatus.OK,
					result.getStatus());

			Iterator<RevCommit> history = git.log().call().iterator();
			assertEquals("fixed a", history.next().getFullMessage());
			assertEquals("enlarged a", history.next().getFullMessage());
			assertEquals("create b", history.next().getFullMessage());
			assertEquals("create a", history.next().getFullMessage());
			assertFalse(history.hasNext());
		}
	}

	@Test
	public void testRootCherryPick()
			throws IOException, JGitInternalException, GitAPIException {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "a");
			writeTrashFile("b", "b");
			git.add().addFilepattern("a").addFilepattern("b").call();
			RevCommit firstCommit = git.commit().setMessage("Create a and b")
					.call();

			git.checkout().setOrphan(true).setName("orphan").call();
			git.rm().addFilepattern("a").addFilepattern("b").call();
			writeTrashFile("a", "a");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("Orphan a").call();

			CherryPickResult result = git.cherryPick().include(firstCommit)
					.call();
			assertEquals(CherryPickResult.CherryPickStatus.OK,
					result.getStatus());

			Iterator<RevCommit> history = git.log().call().iterator();
			assertEquals("Create a and b", history.next().getFullMessage());
			assertEquals("Orphan a", history.next().getFullMessage());
			assertFalse(history.hasNext());
		}
	}

	@Test
	public void testCherryPickDirtyIndex() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit sideCommit = prepareCherryPick(git);

			// modify and add file a
			writeTrashFile("a", "a(modified)");
			git.add().addFilepattern("a").call();
			// do not commit

			doCherryPickAndCheckResult(git, sideCommit,
					MergeFailureReason.DIRTY_INDEX);
		}
	}

	@Test
	public void testCherryPickDirtyWorktree() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit sideCommit = prepareCherryPick(git);

			// modify file a
			writeTrashFile("a", "a(modified)");
			// do not add and commit

			doCherryPickAndCheckResult(git, sideCommit,
					MergeFailureReason.DIRTY_WORKTREE);
		}
	}

	@Test
	public void testCherryPickConflictResolution() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit sideCommit = prepareCherryPick(git);

			CherryPickResult result = git.cherryPick().include(sideCommit.getId())
					.call();

			assertEquals(CherryPickStatus.CONFLICTING, result.getStatus());
			assertTrue(new File(db.getDirectory(), Constants.MERGE_MSG).exists());
			assertEquals("side\n\n# Conflicts:\n#\ta\n",
					db.readMergeCommitMsg());
			assertTrue(new File(db.getDirectory(), Constants.CHERRY_PICK_HEAD)
					.exists());
			assertEquals(sideCommit.getId(), db.readCherryPickHead());
			assertEquals(RepositoryState.CHERRY_PICKING, db.getRepositoryState());

			// Resolve
			writeTrashFile("a", "a");
			git.add().addFilepattern("a").call();

			assertEquals(RepositoryState.CHERRY_PICKING_RESOLVED,
					db.getRepositoryState());

			git.commit().setOnly("a").setMessage("resolve").call();

			assertEquals(RepositoryState.SAFE, db.getRepositoryState());
		}
	}

	@Test
	public void testCherryPickConflictResolutionNoCommit() throws Exception {
		Git git = new Git(db);
		RevCommit sideCommit = prepareCherryPick(git);

		CherryPickResult result = git.cherryPick().include(sideCommit.getId())
				.setNoCommit(true).call();

		assertEquals(CherryPickStatus.CONFLICTING, result.getStatus());
		assertTrue(db.readDirCache().hasUnmergedPaths());
		String expected = "<<<<<<< master\na(master)\n=======\na(side)\n>>>>>>> 527460a side\n";
		assertEquals(expected, read("a"));
		assertTrue(new File(db.getDirectory(), Constants.MERGE_MSG).exists());
		assertEquals("side\n\n# Conflicts:\n#\ta\n", db.readMergeCommitMsg());
		assertFalse(new File(db.getDirectory(), Constants.CHERRY_PICK_HEAD)
				.exists());
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());

		// Resolve
		writeTrashFile("a", "a");
		git.add().addFilepattern("a").call();

		assertEquals(RepositoryState.SAFE, db.getRepositoryState());

		git.commit().setOnly("a").setMessage("resolve").call();

		assertEquals(RepositoryState.SAFE, db.getRepositoryState());
	}

	@Test
	public void testCherryPickConflictReset() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit sideCommit = prepareCherryPick(git);

			CherryPickResult result = git.cherryPick().include(sideCommit.getId())
					.call();

			assertEquals(CherryPickStatus.CONFLICTING, result.getStatus());
			assertEquals(RepositoryState.CHERRY_PICKING, db.getRepositoryState());
			assertTrue(new File(db.getDirectory(), Constants.CHERRY_PICK_HEAD)
					.exists());

			git.reset().setMode(ResetType.MIXED).setRef("HEAD").call();

			assertEquals(RepositoryState.SAFE, db.getRepositoryState());
			assertFalse(new File(db.getDirectory(), Constants.CHERRY_PICK_HEAD)
					.exists());
		}
	}

	@Test
	public void testCherryPickOverExecutableChangeOnNonExectuableFileSystem()
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

			CherryPickResult result = git.cherryPick().include(commit2).call();
			assertNotNull(result);
			assertEquals(CherryPickStatus.OK, result.getStatus());
		}
	}

	@Test
	public void testCherryPickOurs() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit sideCommit = prepareCherryPick(git);

			CherryPickResult result = git.cherryPick()
					.include(sideCommit.getId())
					.setStrategy(MergeStrategy.OURS)
					.call();
			assertEquals(CherryPickStatus.OK, result.getStatus());

			String expected = "a(master)";
			checkFile(new File(db.getWorkTree(), "a"), expected);
		}
	}

	@Test
	public void testCherryPickTheirs() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit sideCommit = prepareCherryPick(git);

			CherryPickResult result = git.cherryPick()
					.include(sideCommit.getId())
					.setStrategy(MergeStrategy.THEIRS)
					.call();
			assertEquals(CherryPickStatus.OK, result.getStatus());

			String expected = "a(side)";
			checkFile(new File(db.getWorkTree(), "a"), expected);
		}
	}

	@Test
	public void testCherryPickXours() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit sideCommit = prepareCherryPickStrategyOption(git);

			CherryPickResult result = git.cherryPick()
					.include(sideCommit.getId())
					.setContentMergeStrategy(ContentMergeStrategy.OURS)
					.call();
			assertEquals(CherryPickStatus.OK, result.getStatus());

			String expected = "a\nmaster\nc\nd\n";
			checkFile(new File(db.getWorkTree(), "a"), expected);
		}
	}

	@Test
	public void testCherryPickXtheirs() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit sideCommit = prepareCherryPickStrategyOption(git);

			CherryPickResult result = git.cherryPick()
					.include(sideCommit.getId())
					.setContentMergeStrategy(ContentMergeStrategy.THEIRS)
					.call();
			assertEquals(CherryPickStatus.OK, result.getStatus());

			String expected = "a\nside\nc\nd\n";
			checkFile(new File(db.getWorkTree(), "a"), expected);
		}
	}

	@Test
	public void testCherryPickConflictMarkers() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit sideCommit = prepareCherryPick(git);

			CherryPickResult result = git.cherryPick().include(sideCommit.getId())
					.call();
			assertEquals(CherryPickStatus.CONFLICTING, result.getStatus());

			String expected = "<<<<<<< master\na(master)\n=======\na(side)\n>>>>>>> 527460a side\n";
			checkFile(new File(db.getWorkTree(), "a"), expected);
		}
	}

	@Test
	public void testCherryPickConflictDiff3Markers() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit sideCommit = prepareCherryPick(git);

			db.getConfig().setEnum(CONFIG_MERGE_SECTION, null,
					CONFIG_KEY_CONFLICTSTYLE, ConflictStyle.DIFF3);

			CherryPickResult result = git.cherryPick()
					.include(sideCommit.getId()).call();
			assertEquals(CherryPickStatus.CONFLICTING, result.getStatus());

			String expected = "<<<<<<< master\na(master)\n||||||| BASE\na\n=======\na(side)\n>>>>>>> 527460a side\n";
			checkFile(new File(db.getWorkTree(), "a"), expected);
		}
	}

	@Test
	public void testCherryPickConflictFiresModifiedEvent() throws Exception {
		ListenerHandle listener = null;
		try (Git git = new Git(db)) {
			RevCommit sideCommit = prepareCherryPick(git);
			ChangeRecorder recorder = new ChangeRecorder();
			listener = db.getListenerList()
					.addWorkingTreeModifiedListener(recorder);
			CherryPickResult result = git.cherryPick()
					.include(sideCommit.getId()).call();
			assertEquals(CherryPickStatus.CONFLICTING, result.getStatus());
			recorder.assertEvent(new String[] { "a" }, ChangeRecorder.EMPTY);
		} finally {
			if (listener != null) {
				listener.remove();
			}
		}
	}

	@Test
	public void testCherryPickNewFileFiresModifiedEvent() throws Exception {
		ListenerHandle listener = null;
		try (Git git = new Git(db)) {
			writeTrashFile("test.txt", "a");
			git.add().addFilepattern("test.txt").call();
			git.commit().setMessage("commit1").call();
			git.checkout().setCreateBranch(true).setName("a").call();

			writeTrashFile("side.txt", "side");
			git.add().addFilepattern("side.txt").call();
			RevCommit side = git.commit().setMessage("side").call();
			assertNotNull(side);

			assertNotNull(git.checkout().setName(Constants.MASTER).call());
			writeTrashFile("test.txt", "b");
			assertNotNull(git.add().addFilepattern("test.txt").call());
			assertNotNull(git.commit().setMessage("commit2").call());

			ChangeRecorder recorder = new ChangeRecorder();
			listener = db.getListenerList()
					.addWorkingTreeModifiedListener(recorder);
			CherryPickResult result = git.cherryPick()
					.include(side.getId()).call();
			assertEquals(CherryPickStatus.OK, result.getStatus());
			recorder.assertEvent(new String[] { "side.txt" },
					ChangeRecorder.EMPTY);
		} finally {
			if (listener != null) {
				listener.remove();
			}
		}
	}

	@Test
	public void testCherryPickOurCommitName() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit sideCommit = prepareCherryPick(git);

			CherryPickResult result = git.cherryPick().include(sideCommit.getId())
					.setOurCommitName("custom name").call();
			assertEquals(CherryPickStatus.CONFLICTING, result.getStatus());

			String expected = "<<<<<<< custom name\na(master)\n=======\na(side)\n>>>>>>> 527460a side\n";
			checkFile(new File(db.getWorkTree(), "a"), expected);
		}
	}

	private RevCommit prepareCherryPick(Git git) throws Exception {
		// create, add and commit file a
		writeTrashFile("a", "a");
		git.add().addFilepattern("a").call();
		RevCommit firstMasterCommit = git.commit().setMessage("first master")
				.call();

		// create and checkout side branch
		createBranch(firstMasterCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");
		// modify, add and commit file a
		writeTrashFile("a", "a(side)");
		git.add().addFilepattern("a").call();
		RevCommit sideCommit = git.commit().setMessage("side").call();

		// checkout master branch
		checkoutBranch("refs/heads/master");
		// modify, add and commit file a
		writeTrashFile("a", "a(master)");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("second master").call();
		return sideCommit;
	}

	private RevCommit prepareCherryPickStrategyOption(Git git)
			throws Exception {
		// create, add and commit file a
		writeTrashFile("a", "a\nb\nc\n");
		git.add().addFilepattern("a").call();
		RevCommit firstMasterCommit = git.commit().setMessage("first master")
				.call();

		// create and checkout side branch
		createBranch(firstMasterCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");
		// modify, add and commit file a
		writeTrashFile("a", "a\nside\nc\nd\n");
		git.add().addFilepattern("a").call();
		RevCommit sideCommit = git.commit().setMessage("side").call();

		// checkout master branch
		checkoutBranch("refs/heads/master");
		// modify, add and commit file a
		writeTrashFile("a", "a\nmaster\nc\n");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("second master").call();
		return sideCommit;
	}

	private void doCherryPickAndCheckResult(final Git git,
			final RevCommit sideCommit, final MergeFailureReason reason)
			throws Exception {
		// get current index state
		String indexState = indexState(CONTENT);

		// cherry-pick
		CherryPickResult result = git.cherryPick().include(sideCommit.getId())
				.call();
		assertEquals(CherryPickStatus.FAILED, result.getStatus());
		// staged file a causes DIRTY_INDEX
		assertEquals(1, result.getFailingPaths().size());
		assertEquals(reason, result.getFailingPaths().get("a"));
		assertEquals("a(modified)", read(new File(db.getWorkTree(), "a")));
		// index shall be unchanged
		assertEquals(indexState, indexState(CONTENT));
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());

		if (reason == null) {
			RefDatabase refDb = db.getRefDatabase();
			ReflogReader reader = refDb.getReflogReader(Constants.HEAD);
			assertTrue(reader.getLastEntry().getComment()
					.startsWith("cherry-pick: "));
			reader = refDb.getReflogReader(db.getFullBranch());
			assertTrue(reader.getLastEntry().getComment()
					.startsWith("cherry-pick: "));
		}
	}

	/**
	 * Cherry-picking merge commit M onto T
	 * <pre>
	 *    M
	 *    |\
	 *    C D
	 *    |/
	 * T  B
	 * | /
	 * A
	 * </pre>
	 * @throws Exception
	 */
	@Test
	public void testCherryPickMerge() throws Exception {
		try (Git git = new Git(db)) {
			commitFile("file", "1\n2\n3\n", "master");
			commitFile("file", "1\n2\n3\n", "side");
			checkoutBranch("refs/heads/side");
			RevCommit commitD = commitFile("file", "1\n2\n3\n4\n5\n", "side2");
			commitFile("file", "a\n2\n3\n", "side");
			MergeResult mergeResult = git.merge().include(commitD).call();
			ObjectId commitM = mergeResult.getNewHead();
			checkoutBranch("refs/heads/master");
			RevCommit commitT = commitFile("another", "t", "master");

			try {
				git.cherryPick().include(commitM).call();
				fail("merges should not be cherry-picked by default");
			} catch (MultipleParentsNotAllowedException e) {
				// expected
			}
			try {
				git.cherryPick().include(commitM).setMainlineParentNumber(3).call();
				fail("specifying a non-existent parent should fail");
			} catch (JGitInternalException e) {
				// expected
				assertTrue(e.getMessage().endsWith(
						"does not have a parent number 3."));
			}

			CherryPickResult result = git.cherryPick().include(commitM)
					.setMainlineParentNumber(1).call();
			assertEquals(CherryPickStatus.OK, result.getStatus());
			checkFile(new File(db.getWorkTree(), "file"), "1\n2\n3\n4\n5\n");

			git.reset().setMode(ResetType.HARD).setRef(commitT.getName()).call();

			CherryPickResult result2 = git.cherryPick().include(commitM)
					.setMainlineParentNumber(2).call();
			assertEquals(CherryPickStatus.OK, result2.getStatus());
			checkFile(new File(db.getWorkTree(), "file"), "a\n2\n3\n");
		}
	}

	private void doCherryPickWithCustomProviderBaseTest(Git git,
			CherryPickCommitMessageProvider commitMessageProvider)
			throws Exception {
		writeTrashFile("fileA", "line 1\nline 2\nline 3\n");
		git.add().addFilepattern("fileA").call();
		RevCommit commitFirst = git.commit().setMessage("create fileA").call();

		writeTrashFile("fileB", "content from file B\n");
		git.add().addFilepattern("fileB").call();
		RevCommit commitCreateFileB = git.commit()
				.setMessage("create fileB\n\nsome commit details").call();

		writeTrashFile("fileA", "line 1\nline 2\nline 3\nline 4\n");
		git.add().addFilepattern("fileA").call();
		RevCommit commitEditFileA1 = git.commit().setMessage("patch fileA 1")
				.call();

		writeTrashFile("fileA", "line 1\nline 2\nline 3\nline 4\nline 5\n");
		git.add().addFilepattern("fileA").call();
		RevCommit commitEditFileA2 = git.commit().setMessage("patch fileA 2")
				.call();

		git.branchCreate().setName("side").setStartPoint(commitFirst).call();
		checkoutBranch("refs/heads/side");

		CherryPickResult pickResult = git.cherryPick()
				.setCherryPickCommitMessageProvider(commitMessageProvider)
				.include(commitCreateFileB).include(commitEditFileA1)
				.include(commitEditFileA2).call();

		assertEquals(CherryPickStatus.OK, pickResult.getStatus());

		assertTrue(new File(db.getWorkTree(), "fileA").exists());
		assertTrue(new File(db.getWorkTree(), "fileB").exists());

		checkFile(new File(db.getWorkTree(), "fileA"),
				"line 1\nline 2\nline 3\nline 4\nline 5\n");
		checkFile(new File(db.getWorkTree(), "fileB"), "content from file B\n");
	}

	@Test
	public void testCherryPickWithCustomCommitMessageProvider()
			throws Exception {
		try (Git git = new Git(db)) {
			@SuppressWarnings("boxing")
			CherryPickCommitMessageProvider messageProvider = srcCommit -> {
				String message = srcCommit.getFullMessage();
				return String.format("%s (message length: %d)", message,
						message.length());
			};
			doCherryPickWithCustomProviderBaseTest(git, messageProvider);

			Iterator<RevCommit> history = git.log().call().iterator();
			assertEquals("patch fileA 2 (message length: 13)",
					history.next().getFullMessage());
			assertEquals("patch fileA 1 (message length: 13)",
					history.next().getFullMessage());
			assertEquals(
					"create fileB\n\nsome commit details (message length: 33)",
					history.next().getFullMessage());
			assertEquals("create fileA", history.next().getFullMessage());
			assertFalse(history.hasNext());
		}
	}

	@Test
	public void testCherryPickWithCustomCommitMessageProvider_ORIGINAL()
			throws Exception {
		try (Git git = new Git(db)) {
			doCherryPickWithCustomProviderBaseTest(git, ORIGINAL);

			Iterator<RevCommit> history = git.log().call().iterator();
			assertEquals("patch fileA 2", history.next().getFullMessage());
			assertEquals("patch fileA 1", history.next().getFullMessage());
			assertEquals("create fileB\n\nsome commit details",
					history.next().getFullMessage());
			assertEquals("create fileA", history.next().getFullMessage());
			assertFalse(history.hasNext());
		}
	}

	@Test
	public void testCherryPickWithCustomCommitMessageProvider_ORIGINAL_WITH_REFERENCE()
			throws Exception {
		try (Git git = new Git(db)) {
			doCherryPickWithCustomProviderBaseTest(git,
					ORIGINAL_WITH_REFERENCE);

			Iterator<RevCommit> history = git.log().call().iterator();
			assertEquals("patch fileA 2\n\n(cherry picked from commit 1ac121e90b0fb6fb18bbb4307e3e9731ceeba9e1)", history.next().getFullMessage());
			assertEquals("patch fileA 1\n\n(cherry picked from commit 71475239df59076e18564fa360e3a74280926c2a)", history.next().getFullMessage());
			assertEquals("create fileB\n\nsome commit details\n\n(cherry picked from commit 29b4501297ccf8de9de9f451e7beb384b51f5378)",
					history.next().getFullMessage());
			assertEquals("create fileA", history.next().getFullMessage());
			assertFalse(history.hasNext());
		}
	}

	@Test
	public void testCherryPickWithCustomCommitMessageProvider_ORIGINAL_WITH_REFERENCE_DonNotAddNewLineAfterFooter()
			throws Exception {
		try (Git git = new Git(db)) {
			CherryPickCommitMessageProvider commitMessageProvider = CherryPickCommitMessageProvider.ORIGINAL_WITH_REFERENCE;

			RevCommit commit1 = addFileAndCommit(git, "file1", "content 1",
					"commit1: no footer line");
			RevCommit commit2 = addFileAndCommit(git, "file2", "content 2",
					"commit2: simple single footer line"
							+ "\n\nSigned-off-by: Alice <alice@example.com>");
			RevCommit commit3 = addFileAndCommit(git, "file3", "content 3",
					"commit3: multiple footer lines\n\n"
							+ "Signed-off-by: Alice <alice@example.com>\n"
							+ "Signed-off-by: Bob <bob@example.com>");
			RevCommit commit4 = addFileAndCommit(git, "file4", "content 4",
					"commit4: extra commit text before footer line\n\n"
							+ "Commit message details\n\n"
							+ "Signed-off-by: Alice <alice@example.com>\n"
							+ "Signed-off-by: Bob <bob@example.com>");
			RevCommit commit5 = addFileAndCommit(git, "file5", "content 5",
					"commit5: extra commit text after footer line\n\n"
							+ "Signed-off-by: Alice <alice@example.com>\n"
							+ "Signed-off-by: Bob <bob@example.com>\n\n"
							+ "some extra description after footer");

			git.branchCreate().setName("side").setStartPoint(commit1).call();
			checkoutBranch("refs/heads/side");

			CherryPickResult pickResult = git.cherryPick()
					.setCherryPickCommitMessageProvider(commitMessageProvider)
					.include(commit2).include(commit3).include(commit4)
					.include(commit5).call();

			assertEquals(CherryPickStatus.OK, pickResult.getStatus());

			assertTrue(new File(db.getWorkTree(), "file1").exists());
			assertTrue(new File(db.getWorkTree(), "file2").exists());
			assertTrue(new File(db.getWorkTree(), "file3").exists());
			assertTrue(new File(db.getWorkTree(), "file4").exists());
			assertTrue(new File(db.getWorkTree(), "file5").exists());

			Iterator<RevCommit> history = git.log().call().iterator();
			RevCommit cpCommit1 = history.next();
			RevCommit cpCommit2 = history.next();
			RevCommit cpCommit3 = history.next();
			RevCommit cpCommit4 = history.next();
			RevCommit cpCommitInit = history.next();
			assertFalse(history.hasNext());

			assertEquals("commit5: extra commit text after footer line\n\n"
					+ "Signed-off-by: Alice <alice@example.com>\n"
					+ "Signed-off-by: Bob <bob@example.com>\n\n"
					+ "some extra description after footer\n\n"
					+ "(cherry picked from commit c3c9959207dc7ae7c83da5d36dc14ef2ca42d572)",
					cpCommit1.getFullMessage());
			assertEquals("commit4: extra commit text before footer line\n\n"
					+ "Commit message details\n\n"
					+ "Signed-off-by: Alice <alice@example.com>\n"
					+ "Signed-off-by: Bob <bob@example.com>\n"
					+ "(cherry picked from commit af3e8106c12cb946a37b403ddb2dd6c11a883698)",
					cpCommit2.getFullMessage());
			assertEquals("commit3: multiple footer lines\n\n"
					+ "Signed-off-by: Alice <alice@example.com>\n"
					+ "Signed-off-by: Bob <bob@example.com>\n"
					+ "(cherry picked from commit 6d60f1a70a11a32dff4402c157c4ac328c32ce6c)",
					cpCommit3.getFullMessage());
			assertEquals("commit2: simple single footer line\n\n"
					+ "Signed-off-by: Alice <alice@example.com>\n"
					+ "(cherry picked from commit 92bf0ec458814ecc73da8e050e60547d2ea6cce5)",
					cpCommit4.getFullMessage());

			assertEquals("commit1: no footer line",
					cpCommitInit.getFullMessage());
		}
	}

	private RevCommit addFileAndCommit(Git git, String fileName,
			String fileText, String commitMessage)
			throws IOException, GitAPIException {
		writeTrashFile(fileName, fileText);
		git.add().addFilepattern(fileName).call();
		return git.commit().setMessage(commitMessage).call();
	}
}
