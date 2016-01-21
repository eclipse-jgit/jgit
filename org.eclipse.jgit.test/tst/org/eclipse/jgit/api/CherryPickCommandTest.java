/*
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
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.eclipse.jgit.api.CherryPickResult.CherryPickStatus;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.MultipleParentsNotAllowedException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.RepositoryState;
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
    public void testSequentialCherryPick() throws IOException, JGitInternalException,
            GitAPIException {
        try (Git git = new Git(db)) {
	        writeTrashFile("a", "first line\nsec. line\nthird line\n");
	        git.add().addFilepattern("a").call();
	        RevCommit firstCommit = git.commit().setMessage("create a").call();

	        writeTrashFile("a", "first line\nsec. line\nthird line\nfourth line\n");
	        git.add().addFilepattern("a").call();
	        RevCommit enlargingA = git.commit().setMessage("enlarged a").call();

	        writeTrashFile("a",
	                "first line\nsecond line\nthird line\nfourth line\n");
	        git.add().addFilepattern("a").call();
	        RevCommit fixingA = git.commit().setMessage("fixed a").call();

	        git.branchCreate().setName("side").setStartPoint(firstCommit).call();
	        checkoutBranch("refs/heads/side");

	        writeTrashFile("b", "nothing to do with a");
	        git.add().addFilepattern("b").call();
	        git.commit().setMessage("create b").call();

	        CherryPickResult result = git.cherryPick().include(enlargingA).include(fixingA).call();
	        assertEquals(CherryPickResult.CherryPickStatus.OK, result.getStatus());

	        Iterator<RevCommit> history = git.log().call().iterator();
	        assertEquals("fixed a", history.next().getFullMessage());
	        assertEquals("enlarged a", history.next().getFullMessage());
	        assertEquals("create b", history.next().getFullMessage());
	        assertEquals("create a", history.next().getFullMessage());
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
			assertEquals("side\n\nConflicts:\n\ta\n", db.readMergeCommitMsg());
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
	public void testCherryPickConflictResolutionNoCOmmit() throws Exception {
		Git git = new Git(db);
		RevCommit sideCommit = prepareCherryPick(git);

		CherryPickResult result = git.cherryPick().include(sideCommit.getId())
				.setNoCommit(true).call();

		assertEquals(CherryPickStatus.CONFLICTING, result.getStatus());
		assertTrue(db.readDirCache().hasUnmergedPaths());
		String expected = "<<<<<<< master\na(master)\n=======\na(side)\n>>>>>>> 527460a side\n";
		assertEquals(expected, read("a"));
		assertTrue(new File(db.getDirectory(), Constants.MERGE_MSG).exists());
		assertEquals("side\n\nConflicts:\n\ta\n", db.readMergeCommitMsg());
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

	private RevCommit prepareCherryPick(final Git git) throws Exception {
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
			ReflogReader reader = db.getReflogReader(Constants.HEAD);
			assertTrue(reader.getLastEntry().getComment()
					.startsWith("cherry-pick: "));
			reader = db.getReflogReader(db.getBranch());
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
}
