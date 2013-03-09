/*
 * Copyright (C) 2010, 2013 Mathias Kinzler <mathias.kinzler@sap.com>
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.RebaseCommand.Action;
import org.eclipse.jgit.api.RebaseCommand.InteractiveHandler;
import org.eclipse.jgit.api.RebaseCommand.Operation;
import org.eclipse.jgit.api.RebaseCommand.Step;
import org.eclipse.jgit.api.RebaseResult.Status;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Before;
import org.junit.Test;

public class RebaseCommandTest extends RepositoryTestCase {
	private static final String FILE1 = "file1";

	protected Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		this.git = new Git(db);
	}

	private void checkoutCommit(RevCommit commit) throws IllegalStateException,
			IOException {
		RevWalk walk = new RevWalk(db);
		RevCommit head = walk.parseCommit(db.resolve(Constants.HEAD));
		DirCacheCheckout dco = new DirCacheCheckout(db, head.getTree(), db
				.lockDirCache(), commit.getTree());
		dco.setFailOnConflict(true);
		dco.checkout();
		walk.release();
		// update the HEAD
		RefUpdate refUpdate = db.updateRef(Constants.HEAD, true);
		refUpdate.setNewObjectId(commit);
		refUpdate.forceUpdate();
	}

	@Test
	public void testFastForwardWithNewFile() throws Exception {
		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		RevCommit first = git.commit().setMessage("Add file1").call();

		assertTrue(new File(db.getWorkTree(), FILE1).exists());
		// create a topic branch
		createBranch(first, "refs/heads/topic");
		// create file2 on master
		File file2 = writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("Add file2").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());

		checkoutBranch("refs/heads/topic");
		assertFalse(new File(db.getWorkTree(), "file2").exists());

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());
		checkFile(file2, "file2");
		assertEquals(Status.FAST_FORWARD, res.getStatus());
	}

	@Test
	public void testFastForwardWithMultipleCommits() throws Exception {
		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		RevCommit first = git.commit().setMessage("Add file1").call();

		assertTrue(new File(db.getWorkTree(), FILE1).exists());
		// create a topic branch
		createBranch(first, "refs/heads/topic");
		// create file2 on master
		File file2 = writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("Add file2").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());
		// write a second commit
		writeTrashFile("file2", "file2 new content");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("Change content of file2").call();

		checkoutBranch("refs/heads/topic");
		assertFalse(new File(db.getWorkTree(), "file2").exists());

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());
		checkFile(file2, "file2 new content");
		assertEquals(Status.FAST_FORWARD, res.getStatus());
	}

	/**
	 * Create the following commits and then attempt to rebase topic onto
	 * master. This will serialize the branches.
	 *
	 * <pre>
	 * A - B (master)
	 *   \
	 *    C - D - F (topic)
	 *     \      /
	 *      E  -  (side)
	 * </pre>
	 *
	 * into
	 *
	 * <pre>
	 * A - B - (master)  C' - D' - E' (topic')
	 *   \
	 *    C - D - F (topic)
	 *     \      /
	 *      E  -  (side)
	 * </pre>
	 *
	 * @throws Exception
	 */
	@Test
	public void testRebaseShouldIgnoreMergeCommits()
			throws Exception {
		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		RevCommit a = git.commit().setMessage("Add file1").call();
		assertTrue(new File(db.getWorkTree(), FILE1).exists());

		// create a topic branch
		createBranch(a, "refs/heads/topic");

		// update FILE1 on master
		writeTrashFile(FILE1, "blah");
		git.add().addFilepattern(FILE1).call();
		RevCommit b = git.commit().setMessage("updated file1 on master").call();

		checkoutBranch("refs/heads/topic");
		writeTrashFile("file3", "more changess");
		git.add().addFilepattern("file3").call();
		RevCommit c = git.commit()
				.setMessage("update file3 on topic").call();

		// create a branch from the topic commit
		createBranch(c, "refs/heads/side");

		// second commit on topic
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		RevCommit d = git.commit().setMessage("Add file2").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());

		// switch to side branch and update file2
		checkoutBranch("refs/heads/side");
		writeTrashFile("file3", "more change");
		git.add().addFilepattern("file3").call();
		RevCommit e = git.commit().setMessage("update file2 on side")
				.call();

		// switch back to topic and merge in side, creating f
		checkoutBranch("refs/heads/topic");
		MergeResult result = git.merge().include(e.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();
		assertEquals(MergeStatus.MERGED, result.getMergeStatus());
		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertEquals(Status.OK, res.getStatus());

		RevWalk rw = new RevWalk(db);
		rw.markStart(rw.parseCommit(db.resolve("refs/heads/topic")));
		assertDerivedFrom(rw.next(), e);
		assertDerivedFrom(rw.next(), d);
		assertDerivedFrom(rw.next(), c);
		assertEquals(b, rw.next());
		assertEquals(a, rw.next());
	}

	static void assertDerivedFrom(RevCommit derived, RevCommit original) {
		assertThat(derived, not(equalTo(original)));
		assertEquals(original.getFullMessage(), derived.getFullMessage());
	}

	@Test
	public void testRebaseParentOntoHeadShouldBeUptoDate() throws Exception {
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		RevCommit parent = git.commit().setMessage("parent comment").call();

		writeTrashFile(FILE1, "another change");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("head commit").call();

		RebaseResult result = git.rebase().setUpstream(parent).call();
		assertEquals(Status.UP_TO_DATE, result.getStatus());
	}

	@Test
	public void testUpToDate() throws Exception {
		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		RevCommit first = git.commit().setMessage("Add file1").call();

		assertTrue(new File(db.getWorkTree(), FILE1).exists());

		RebaseResult res = git.rebase().setUpstream(first).call();
		assertEquals(Status.UP_TO_DATE, res.getStatus());
	}

	@Test
	public void testUnknownUpstream() throws Exception {
		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Add file1").call();

		assertTrue(new File(db.getWorkTree(), FILE1).exists());

		try {
			git.rebase().setUpstream("refs/heads/xyz").call();
			fail("expected exception was not thrown");
		} catch (RefNotFoundException e) {
			// expected exception
		}
	}

	@Test
	public void testConflictFreeWithSingleFile() throws Exception {
		// create file1 on master
		File theFile = writeTrashFile(FILE1, "1\n2\n3\n");
		git.add().addFilepattern(FILE1).call();
		RevCommit second = git.commit().setMessage("Add file1").call();
		assertTrue(new File(db.getWorkTree(), FILE1).exists());
		// change first line in master and commit
		writeTrashFile(FILE1, "1master\n2\n3\n");
		checkFile(theFile, "1master\n2\n3\n");
		git.add().addFilepattern(FILE1).call();
		RevCommit lastMasterChange = git.commit().setMessage(
				"change file1 in master").call();

		// create a topic branch based on second commit
		createBranch(second, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		// we have the old content again
		checkFile(theFile, "1\n2\n3\n");

		assertTrue(new File(db.getWorkTree(), FILE1).exists());
		// change third line in topic branch
		writeTrashFile(FILE1, "1\n2\n3\ntopic\n");
		git.add().addFilepattern(FILE1).call();
		RevCommit origHead = git.commit().setMessage("change file1 in topic")
				.call();

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertEquals(Status.OK, res.getStatus());
		checkFile(theFile, "1master\n2\n3\ntopic\n");
		// our old branch should be checked out again
		assertEquals("refs/heads/topic", db.getFullBranch());
		assertEquals(lastMasterChange, new RevWalk(db).parseCommit(
				db.resolve(Constants.HEAD)).getParent(0));
		assertEquals(origHead, db.readOrigHead());
	}

	@Test
	public void testDetachedHead() throws Exception {
		// create file1 on master
		File theFile = writeTrashFile(FILE1, "1\n2\n3\n");
		git.add().addFilepattern(FILE1).call();
		RevCommit second = git.commit().setMessage("Add file1").call();
		assertTrue(new File(db.getWorkTree(), FILE1).exists());
		// change first line in master and commit
		writeTrashFile(FILE1, "1master\n2\n3\n");
		checkFile(theFile, "1master\n2\n3\n");
		git.add().addFilepattern(FILE1).call();
		RevCommit lastMasterChange = git.commit().setMessage(
				"change file1 in master").call();

		// create a topic branch based on second commit
		createBranch(second, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		// we have the old content again
		checkFile(theFile, "1\n2\n3\n");

		assertTrue(new File(db.getWorkTree(), FILE1).exists());
		// change third line in topic branch
		writeTrashFile(FILE1, "1\n2\n3\ntopic\n");
		git.add().addFilepattern(FILE1).call();
		RevCommit topicCommit = git.commit()
				.setMessage("change file1 in topic").call();
		checkoutBranch("refs/heads/master");
		checkoutCommit(topicCommit);
		assertEquals(topicCommit.getId().getName(), db.getFullBranch());

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertEquals(Status.OK, res.getStatus());
		checkFile(theFile, "1master\n2\n3\ntopic\n");
		assertEquals(lastMasterChange, new RevWalk(db).parseCommit(
				db.resolve(Constants.HEAD)).getParent(0));

	}

	@Test
	public void testFilesAddedFromTwoBranches() throws Exception {
		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		RevCommit masterCommit = git.commit().setMessage("Add file1 to master")
				.call();

		// create a branch named file2 and add file2
		createBranch(masterCommit, "refs/heads/file2");
		checkoutBranch("refs/heads/file2");
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		RevCommit addFile2 = git.commit().setMessage(
				"Add file2 to branch file2").call();

		// create a branch named file3 and add file3
		createBranch(masterCommit, "refs/heads/file3");
		checkoutBranch("refs/heads/file3");
		writeTrashFile("file3", "file3");
		git.add().addFilepattern("file3").call();
		git.commit().setMessage("Add file3 to branch file3").call();

		assertTrue(new File(db.getWorkTree(), FILE1).exists());
		assertFalse(new File(db.getWorkTree(), "file2").exists());
		assertTrue(new File(db.getWorkTree(), "file3").exists());

		RebaseResult res = git.rebase().setUpstream("refs/heads/file2").call();
		assertEquals(Status.OK, res.getStatus());

		assertTrue(new File(db.getWorkTree(), FILE1).exists());
		assertTrue(new File(db.getWorkTree(), "file2").exists());
		assertTrue(new File(db.getWorkTree(), "file3").exists());

		// our old branch should be checked out again
		assertEquals("refs/heads/file3", db.getFullBranch());
		assertEquals(addFile2, new RevWalk(db).parseCommit(
				db.resolve(Constants.HEAD)).getParent(0));

		checkoutBranch("refs/heads/file2");
		assertTrue(new File(db.getWorkTree(), FILE1).exists());
		assertTrue(new File(db.getWorkTree(), "file2").exists());
		assertFalse(new File(db.getWorkTree(), "file3").exists());
	}

	@Test
	public void testStopOnConflict() throws Exception {
		// create file1 on master
		RevCommit firstInMaster = writeFileAndCommit(FILE1, "Add file1", "1",
				"2", "3");
		// change first line in master
		writeFileAndCommit(FILE1, "change file1 in master", "1master", "2", "3");
		checkFile(FILE1, "1master", "2", "3");
		// create a topic branch based on second commit
		createBranch(firstInMaster, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		// we have the old content again
		checkFile(FILE1, "1", "2", "3");

		// add a line (non-conflicting)
		writeFileAndCommit(FILE1, "add a line to file1 in topic", "1", "2",
				"3", "topic4");

		// change first line (conflicting)
		RevCommit conflicting = writeFileAndCommit(FILE1,
				"change file1 in topic", "1topic", "2", "3", "topic4");

		RevCommit lastTopicCommit = writeFileAndCommit(FILE1,
				"change file1 in topic again", "1topic", "2", "3", "topic4");

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertEquals(Status.STOPPED, res.getStatus());
		assertEquals(conflicting, res.getCurrentCommit());
		checkFile(FILE1,
				"<<<<<<< Upstream, based on master\n1master\n=======\n1topic",
				">>>>>>> e0d1dea change file1 in topic\n2\n3\ntopic4");

		assertEquals(RepositoryState.REBASING_INTERACTIVE, db
				.getRepositoryState());
		assertTrue(new File(db.getDirectory(), "rebase-merge").exists());
		// the first one should be included, so we should have left two picks in
		// the file
		assertEquals(1, countPicks());

		// rebase should not succeed in this state
		try {
			git.rebase().setUpstream("refs/heads/master").call();
			fail("Expected exception was not thrown");
		} catch (WrongRepositoryStateException e) {
			// expected
		}

		// abort should reset to topic branch
		res = git.rebase().setOperation(Operation.ABORT).call();
		assertEquals(res.getStatus(), Status.ABORTED);
		assertEquals("refs/heads/topic", db.getFullBranch());
		checkFile(FILE1, "1topic", "2", "3", "topic4");
		RevWalk rw = new RevWalk(db);
		assertEquals(lastTopicCommit, rw
				.parseCommit(db.resolve(Constants.HEAD)));
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());

		// rebase- dir in .git must be deleted
		assertFalse(new File(db.getDirectory(), "rebase-merge").exists());
	}

	@Test
	public void testStopOnConflictAndContinue() throws Exception {
		// create file1 on master
		RevCommit firstInMaster = writeFileAndCommit(FILE1, "Add file1", "1",
				"2", "3");
		// change in master
		writeFileAndCommit(FILE1, "change file1 in master", "1master", "2", "3");

		checkFile(FILE1, "1master", "2", "3");
		// create a topic branch based on the first commit
		createBranch(firstInMaster, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		// we have the old content again
		checkFile(FILE1, "1", "2", "3");

		// add a line (non-conflicting)
		writeFileAndCommit(FILE1, "add a line to file1 in topic", "1", "2",
				"3", "4topic");

		// change first line (conflicting)
		writeFileAndCommit(FILE1,
				"change file1 in topic\n\nThis is conflicting", "1topic", "2",
				"3", "4topic");

		// change second line (not conflicting)
		writeFileAndCommit(FILE1, "change file1 in topic again", "1topic",
				"2topic", "3", "4topic");

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertEquals(Status.STOPPED, res.getStatus());

		// continue should throw a meaningful exception
		try {
			res = git.rebase().setOperation(Operation.CONTINUE).call();
			fail("Expected Exception not thrown");
		} catch (UnmergedPathsException e) {
			// expected
		}

		// merge the file; the second topic commit should go through
		writeFileAndAdd(FILE1, "1topic", "2", "3", "4topic");

		res = git.rebase().setOperation(Operation.CONTINUE).call();
		assertNotNull(res);
		assertEquals(Status.OK, res.getStatus());
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());

		ObjectId headId = db.resolve(Constants.HEAD);
		RevWalk rw = new RevWalk(db);
		RevCommit rc = rw.parseCommit(headId);
		RevCommit parent = rw.parseCommit(rc.getParent(0));
		assertEquals("change file1 in topic\n\nThis is conflicting", parent
				.getFullMessage());
	}

	@Test
	public void testStopOnConflictAndContinueWithNoDeltaToMaster()
			throws Exception {
		// create file1 on master
		RevCommit firstInMaster = writeFileAndCommit(FILE1, "Add file1", "1",
				"2", "3");
		// change in master
		writeFileAndCommit(FILE1, "change file1 in master", "1master", "2", "3");

		checkFile(FILE1, "1master", "2", "3");
		// create a topic branch based on the first commit
		createBranch(firstInMaster, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		// we have the old content again
		checkFile(FILE1, "1", "2", "3");

		// change first line (conflicting)
		writeFileAndCommit(FILE1,
				"change file1 in topic\n\nThis is conflicting", "1topic", "2",
				"3", "4topic");

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertEquals(Status.STOPPED, res.getStatus());

		// continue should throw a meaningful exception
		try {
			res = git.rebase().setOperation(Operation.CONTINUE).call();
			fail("Expected Exception not thrown");
		} catch (UnmergedPathsException e) {
			// expected
		}

		// merge the file; the second topic commit should go through
		writeFileAndAdd(FILE1, "1master", "2", "3");

		res = git.rebase().setOperation(Operation.CONTINUE).call();
		assertNotNull(res);
		assertEquals(Status.NOTHING_TO_COMMIT, res.getStatus());
		assertEquals(RepositoryState.REBASING_INTERACTIVE,
				db.getRepositoryState());

		git.rebase().setOperation(Operation.SKIP).call();

		ObjectId headId = db.resolve(Constants.HEAD);
		RevWalk rw = new RevWalk(db);
		RevCommit rc = rw.parseCommit(headId);
		assertEquals("change file1 in master", rc.getFullMessage());
	}

	@Test
	public void testStopOnConflictAndFailContinueIfFileIsDirty()
			throws Exception {
		// create file1 on master
		RevCommit firstInMaster = writeFileAndCommit(FILE1, "Add file1", "1",
				"2", "3");
		// change in master
		writeFileAndCommit(FILE1, "change file1 in master", "1master", "2", "3");

		checkFile(FILE1, "1master", "2", "3");
		// create a topic branch based on the first commit
		createBranch(firstInMaster, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		// we have the old content again
		checkFile(FILE1, "1", "2", "3");

		// add a line (non-conflicting)
		writeFileAndCommit(FILE1, "add a line to file1 in topic", "1", "2",
				"3", "4topic");

		// change first line (conflicting)
		writeFileAndCommit(FILE1,
				"change file1 in topic\n\nThis is conflicting", "1topic", "2",
				"3", "4topic");

		// change second line (not conflicting)
		writeFileAndCommit(FILE1, "change file1 in topic again", "1topic",
				"2topic", "3", "4topic");

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertEquals(Status.STOPPED, res.getStatus());

		git.add().addFilepattern(FILE1).call();
		File trashFile = writeTrashFile(FILE1, "Some local change");

		res = git.rebase().setOperation(Operation.CONTINUE).call();
		assertNotNull(res);
		assertEquals(Status.STOPPED, res.getStatus());
		checkFile(trashFile, "Some local change");
	}

	@Test
	public void testStopOnLastConflictAndContinue() throws Exception {
		// create file1 on master
		RevCommit firstInMaster = writeFileAndCommit(FILE1, "Add file1", "1",
				"2", "3");
		// change in master
		writeFileAndCommit(FILE1, "change file1 in master", "1master", "2", "3");

		checkFile(FILE1, "1master", "2", "3");
		// create a topic branch based on the first commit
		createBranch(firstInMaster, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		// we have the old content again
		checkFile(FILE1, "1", "2", "3");

		// add a line (non-conflicting)
		writeFileAndCommit(FILE1, "add a line to file1 in topic", "1", "2",
				"3", "4topic");

		// change first line (conflicting)
		writeFileAndCommit(FILE1,
				"change file1 in topic\n\nThis is conflicting", "1topic", "2",
				"3", "4topic");

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertEquals(Status.STOPPED, res.getStatus());

		// merge the file; the second topic commit should go through
		writeFileAndAdd(FILE1, "1topic", "2", "3", "4topic");

		res = git.rebase().setOperation(Operation.CONTINUE).call();
		assertNotNull(res);
		assertEquals(Status.OK, res.getStatus());
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());
	}

	@Test
	public void testStopOnLastConflictAndSkip() throws Exception {
		// create file1 on master
		RevCommit firstInMaster = writeFileAndCommit(FILE1, "Add file1", "1",
				"2", "3");
		// change in master
		writeFileAndCommit(FILE1, "change file1 in master", "1master", "2", "3");

		checkFile(FILE1, "1master", "2", "3");
		// create a topic branch based on the first commit
		createBranch(firstInMaster, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		// we have the old content again
		checkFile(FILE1, "1", "2", "3");

		// add a line (non-conflicting)
		writeFileAndCommit(FILE1, "add a line to file1 in topic", "1", "2",
				"3", "4topic");

		// change first line (conflicting)
		writeFileAndCommit(FILE1,
				"change file1 in topic\n\nThis is conflicting", "1topic", "2",
				"3", "4topic");

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertEquals(Status.STOPPED, res.getStatus());

		// merge the file; the second topic commit should go through
		writeFileAndAdd(FILE1, "1topic", "2", "3", "4topic");

		res = git.rebase().setOperation(Operation.SKIP).call();
		assertNotNull(res);
		assertEquals(Status.OK, res.getStatus());
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());
	}

	@Test
	public void testMergeFirstStopOnLastConflictAndSkip() throws Exception {
		// create file1 on master
		RevCommit firstInMaster = writeFileAndCommit(FILE1, "Add file1", "1",
				"2", "3");
		// change in master
		writeFileAndCommit(FILE1, "change file1 in master", "1master", "2", "3");

		checkFile(FILE1, "1master", "2", "3");
		// create a topic branch based on the first commit
		createBranch(firstInMaster, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		// we have the old content again
		checkFile(FILE1, "1", "2", "3");

		// add a line (conflicting)
		writeFileAndCommit(FILE1, "add a line to file1 in topic", "1topic",
				"2", "3", "4topic");

		// change first line (conflicting again)
		writeFileAndCommit(FILE1,
				"change file1 in topic\n\nThis is conflicting", "1topicagain",
				"2", "3", "4topic");

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertEquals(Status.STOPPED, res.getStatus());

		writeFileAndAdd(FILE1, "merged");

		res = git.rebase().setOperation(Operation.CONTINUE).call();
		assertEquals(Status.STOPPED, res.getStatus());

		res = git.rebase().setOperation(Operation.SKIP).call();
		assertNotNull(res);
		assertEquals(Status.OK, res.getStatus());
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());
		checkFile(FILE1, "merged");
	}

	@Test
	public void testStopOnConflictAndSkipNoConflict() throws Exception {
		// create file1 on master
		RevCommit firstInMaster = writeFileAndCommit(FILE1, "Add file1", "1",
				"2", "3");
		// change in master
		writeFileAndCommit(FILE1, "change file1 in master", "1master", "2", "3");

		checkFile(FILE1, "1master", "2", "3");
		// create a topic branch based on the first commit
		createBranch(firstInMaster, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		// we have the old content again
		checkFile(FILE1, "1", "2", "3");

		// add a line (non-conflicting)
		writeFileAndCommit(FILE1, "add a line to file1 in topic", "1", "2",
				"3", "4topic");

		// change first line (conflicting)
		writeFileAndCommit(FILE1,
				"change file1 in topic\n\nThis is conflicting", "1topic", "2",
				"3", "4topic");

		// change third line (not conflicting)
		writeFileAndCommit(FILE1, "change file1 in topic again", "1topic", "2",
				"3topic", "4topic");

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertEquals(Status.STOPPED, res.getStatus());

		res = git.rebase().setOperation(Operation.SKIP).call();

		checkFile(FILE1, "1master", "2", "3topic", "4topic");
		assertEquals(Status.OK, res.getStatus());
	}

	@Test
	public void testStopOnConflictAndSkipWithConflict() throws Exception {
		// create file1 on master
		RevCommit firstInMaster = writeFileAndCommit(FILE1, "Add file1", "1",
				"2", "3", "4");
		// change in master
		writeFileAndCommit(FILE1, "change file1 in master", "1master", "2",
				"3master", "4");

		checkFile(FILE1, "1master", "2", "3master", "4");
		// create a topic branch based on the first commit
		createBranch(firstInMaster, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		// we have the old content again
		checkFile(FILE1, "1", "2", "3", "4");

		// add a line (non-conflicting)
		writeFileAndCommit(FILE1, "add a line to file1 in topic", "1", "2",
				"3", "4", "5topic");

		// change first line (conflicting)
		writeFileAndCommit(FILE1,
				"change file1 in topic\n\nThis is conflicting", "1topic", "2",
				"3", "4", "5topic");

		// change third line (conflicting)
		writeFileAndCommit(FILE1, "change file1 in topic again", "1topic", "2",
				"3topic", "4", "5topic");

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertEquals(Status.STOPPED, res.getStatus());

		res = git.rebase().setOperation(Operation.SKIP).call();
		// TODO is this correct? It is what the command line returns
		checkFile(
				FILE1,
				"1master\n2\n<<<<<<< Upstream, based on master\n3master\n=======\n3topic",
				">>>>>>> 5afc8df change file1 in topic again\n4\n5topic");
		assertEquals(Status.STOPPED, res.getStatus());
	}

	@Test
	public void testStopOnConflictCommitAndContinue() throws Exception {
		// create file1 on master
		RevCommit firstInMaster = writeFileAndCommit(FILE1, "Add file1", "1",
				"2", "3");
		// change in master
		writeFileAndCommit(FILE1, "change file1 in master", "1master", "2", "3");

		checkFile(FILE1, "1master", "2", "3");
		// create a topic branch based on the first commit
		createBranch(firstInMaster, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		// we have the old content again
		checkFile(FILE1, "1", "2", "3");

		// add a line (non-conflicting)
		writeFileAndCommit(FILE1, "add a line to file1 in topic", "1", "2",
				"3", "4topic");

		// change first line (conflicting)
		writeFileAndCommit(FILE1,
				"change file1 in topic\n\nThis is conflicting", "1topic", "2",
				"3", "4topic");

		// change second line (not conflicting)
		writeFileAndCommit(FILE1, "change file1 in topic again", "1topic", "2",
				"3topic", "4topic");

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertEquals(Status.STOPPED, res.getStatus());

		// continue should throw a meaningful exception
		try {
			res = git.rebase().setOperation(Operation.CONTINUE).call();
			fail("Expected Exception not thrown");
		} catch (UnmergedPathsException e) {
			// expected
		}

		// merge the file; the second topic commit should go through
		writeFileAndCommit(FILE1, "A different commit message", "1topic", "2",
				"3", "4topic");

		res = git.rebase().setOperation(Operation.CONTINUE).call();
		assertNotNull(res);

		// nothing to commit. this leaves the repo state in rebase, so that the
		// user can decide what to do. if he accidentally committed, reset soft,
		// and continue, if he really has nothing to commit, skip.
		assertEquals(Status.NOTHING_TO_COMMIT, res.getStatus());
		assertEquals(RepositoryState.REBASING_INTERACTIVE,
				db.getRepositoryState());

		git.rebase().setOperation(Operation.SKIP).call();

		ObjectId headId = db.resolve(Constants.HEAD);
		RevWalk rw = new RevWalk(db);
		RevCommit rc = rw.parseCommit(headId);
		RevCommit parent = rw.parseCommit(rc.getParent(0));
		assertEquals("A different commit message", parent.getFullMessage());
	}

	private RevCommit writeFileAndCommit(String fileName, String commitMessage,
			String... lines) throws Exception {
		StringBuilder sb = new StringBuilder();
		for (String line : lines) {
			sb.append(line);
			sb.append('\n');
		}
		writeTrashFile(fileName, sb.toString());
		git.add().addFilepattern(fileName).call();
		return git.commit().setMessage(commitMessage).call();
	}

	private void writeFileAndAdd(String fileName, String... lines)
			throws Exception {
		StringBuilder sb = new StringBuilder();
		for (String line : lines) {
			sb.append(line);
			sb.append('\n');
		}
		writeTrashFile(fileName, sb.toString());
		git.add().addFilepattern(fileName).call();
	}

	private void checkFile(String fileName, String... lines) throws Exception {
		File file = new File(db.getWorkTree(), fileName);
		StringBuilder sb = new StringBuilder();
		for (String line : lines) {
			sb.append(line);
			sb.append('\n');
		}
		checkFile(file, sb.toString());
	}

	@Test
	public void testStopOnConflictFileCreationAndDeletion() throws Exception {
		// create file1 on master
		writeTrashFile(FILE1, "Hello World");
		git.add().addFilepattern(FILE1).call();
		// create file2 on master
		File file2 = writeTrashFile("file2", "Hello World 2");
		git.add().addFilepattern("file2").call();
		// create file3 on master
		File file3 = writeTrashFile("file3", "Hello World 3");
		git.add().addFilepattern("file3").call();

		RevCommit firstInMaster = git.commit()
				.setMessage("Add file 1, 2 and 3").call();

		// create file4 on master
		File file4 = writeTrashFile("file4", "Hello World 4");
		git.add().addFilepattern("file4").call();

		deleteTrashFile("file2");
		git.add().setUpdate(true).addFilepattern("file2").call();
		// create folder folder6 on topic (conflicts with file folder6 on topic
		// later on)
		writeTrashFile("folder6/file1", "Hello World folder6");
		git.add().addFilepattern("folder6/file1").call();

		git.commit().setMessage(
				"Add file 4 and folder folder6, delete file2 on master").call();

		// create a topic branch based on second commit
		createBranch(firstInMaster, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");

		deleteTrashFile("file3");
		git.add().setUpdate(true).addFilepattern("file3").call();
		// create file5 on topic
		File file5 = writeTrashFile("file5", "Hello World 5");
		git.add().addFilepattern("file5").call();
		git.commit().setMessage("Delete file3 and add file5 in topic").call();

		// create file folder6 on topic (conflicts with folder6 on master)
		writeTrashFile("folder6", "Hello World 6");
		git.add().addFilepattern("folder6").call();
		// create file7 on topic
		File file7 = writeTrashFile("file7", "Hello World 7");
		git.add().addFilepattern("file7").call();

		deleteTrashFile("file5");
		git.add().setUpdate(true).addFilepattern("file5").call();
		RevCommit conflicting = git.commit().setMessage(
				"Delete file5, add file folder6 and file7 in topic").call();

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertEquals(Status.STOPPED, res.getStatus());
		assertEquals(conflicting, res.getCurrentCommit());

		assertEquals(RepositoryState.REBASING_INTERACTIVE, db
				.getRepositoryState());
		assertTrue(new File(db.getDirectory(), "rebase-merge").exists());
		// the first one should be included, so we should have left two picks in
		// the file
		assertEquals(0, countPicks());

		assertFalse(file2.exists());
		assertFalse(file3.exists());
		assertTrue(file4.exists());
		assertFalse(file5.exists());
		assertTrue(file7.exists());

		// abort should reset to topic branch
		res = git.rebase().setOperation(Operation.ABORT).call();
		assertEquals(res.getStatus(), Status.ABORTED);
		assertEquals("refs/heads/topic", db.getFullBranch());
		RevWalk rw = new RevWalk(db);
		assertEquals(conflicting, rw.parseCommit(db.resolve(Constants.HEAD)));
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());

		// rebase- dir in .git must be deleted
		assertFalse(new File(db.getDirectory(), "rebase-merge").exists());

		assertTrue(file2.exists());
		assertFalse(file3.exists());
		assertFalse(file4.exists());
		assertFalse(file5.exists());
		assertTrue(file7.exists());

	}

	@Test
	public void testAuthorScriptConverter() throws Exception {
		// -1 h timezone offset
		PersonIdent ident = new PersonIdent("Author name", "a.mail@some.com",
				123456789123L, -60);
		String convertedAuthor = git.rebase().toAuthorScript(ident);
		String[] lines = convertedAuthor.split("\n");
		assertEquals("GIT_AUTHOR_NAME='Author name'", lines[0]);
		assertEquals("GIT_AUTHOR_EMAIL='a.mail@some.com'", lines[1]);
		assertEquals("GIT_AUTHOR_DATE='@123456789 -0100'", lines[2]);

		PersonIdent parsedIdent = git.rebase().parseAuthor(
				convertedAuthor.getBytes("UTF-8"));
		assertEquals(ident.getName(), parsedIdent.getName());
		assertEquals(ident.getEmailAddress(), parsedIdent.getEmailAddress());
		// this is rounded to the last second
		assertEquals(123456789000L, parsedIdent.getWhen().getTime());
		assertEquals(ident.getTimeZoneOffset(), parsedIdent.getTimeZoneOffset());

		// + 9.5h timezone offset
		ident = new PersonIdent("Author name", "a.mail@some.com",
				123456789123L, +570);
		convertedAuthor = git.rebase().toAuthorScript(ident);
		lines = convertedAuthor.split("\n");
		assertEquals("GIT_AUTHOR_NAME='Author name'", lines[0]);
		assertEquals("GIT_AUTHOR_EMAIL='a.mail@some.com'", lines[1]);
		assertEquals("GIT_AUTHOR_DATE='@123456789 +0930'", lines[2]);

		parsedIdent = git.rebase().parseAuthor(
				convertedAuthor.getBytes("UTF-8"));
		assertEquals(ident.getName(), parsedIdent.getName());
		assertEquals(ident.getEmailAddress(), parsedIdent.getEmailAddress());
		assertEquals(123456789000L, parsedIdent.getWhen().getTime());
		assertEquals(ident.getTimeZoneOffset(), parsedIdent.getTimeZoneOffset());
	}

	@Test
	public void testRepositoryStateChecks() throws Exception {
		try {
			git.rebase().setOperation(Operation.ABORT).call();
			fail("Expected Exception not thrown");
		} catch (WrongRepositoryStateException e) {
			// expected
		}
		try {
			git.rebase().setOperation(Operation.SKIP).call();
			fail("Expected Exception not thrown");
		} catch (WrongRepositoryStateException e) {
			// expected
		}
		try {
			git.rebase().setOperation(Operation.CONTINUE).call();
			fail("Expected Exception not thrown");
		} catch (WrongRepositoryStateException e) {
			// expected
		}
	}

	@Test
	public void testRebaseWithUntrackedFile() throws Exception {
		// create file1, add and commit
		writeTrashFile(FILE1, "file1");
		git.add().addFilepattern(FILE1).call();
		RevCommit commit = git.commit().setMessage("commit1").call();

		// create topic branch and checkout / create file2, add and commit
		createBranch(commit, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("commit2").call();

		// checkout master branch / modify file1, add and commit
		checkoutBranch("refs/heads/master");
		writeTrashFile(FILE1, "modified file1");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("commit3").call();

		// checkout topic branch / create untracked file3
		checkoutBranch("refs/heads/topic");
		writeTrashFile("file3", "untracked file3");

		// rebase
		assertEquals(Status.OK, git.rebase().setUpstream("refs/heads/master")
				.call().getStatus());
	}

	@Test
	public void testRebaseWithUnstagedTopicChange() throws Exception {
		// create file1, add and commit
		writeTrashFile(FILE1, "file1");
		git.add().addFilepattern(FILE1).call();
		RevCommit commit = git.commit().setMessage("commit1").call();

		// create topic branch and checkout / create file2, add and commit
		createBranch(commit, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("commit2").call();

		// checkout master branch / modify file1, add and commit
		checkoutBranch("refs/heads/master");
		writeTrashFile(FILE1, "modified file1");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("commit3").call();

		// checkout topic branch / modify file2
		checkoutBranch("refs/heads/topic");
		writeTrashFile("file2", "unstaged file2");

		// rebase
		RebaseResult result = git.rebase().setUpstream("refs/heads/master")
				.call();
		assertEquals(Status.CONFLICTS, result.getStatus());
		assertEquals(1, result.getConflicts().size());
		assertEquals("file2", result.getConflicts().get(0));
	}

	@Test
	public void testRebaseWithUncommittedTopicChange() throws Exception {
		// create file1, add and commit
		writeTrashFile(FILE1, "file1");
		git.add().addFilepattern(FILE1).call();
		RevCommit commit = git.commit().setMessage("commit1").call();

		// create topic branch and checkout / create file2, add and commit
		createBranch(commit, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("commit2").call();

		// checkout master branch / modify file1, add and commit
		checkoutBranch("refs/heads/master");
		writeTrashFile(FILE1, "modified file1");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("commit3").call();

		// checkout topic branch / modify file2 and add
		checkoutBranch("refs/heads/topic");
		File uncommittedFile = writeTrashFile("file2", "uncommitted file2");
		git.add().addFilepattern("file2").call();
		// do not commit

		RebaseResult result = git.rebase().setUpstream("refs/heads/master")
				.call();
		assertEquals(Status.CONFLICTS, result.getStatus());
		assertEquals(1, result.getConflicts().size());
		assertEquals("file2", result.getConflicts().get(0));

		checkFile(uncommittedFile, "uncommitted file2");
		assertEquals(RepositoryState.SAFE, git.getRepository().getRepositoryState());
	}

	@Test
	public void testRebaseWithUnstagedMasterChange() throws Exception {
		// create file1, add and commit
		writeTrashFile(FILE1, "file1");
		git.add().addFilepattern(FILE1).call();
		RevCommit commit = git.commit().setMessage("commit1").call();

		// create topic branch and checkout / create file2, add and commit
		createBranch(commit, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("commit2").call();

		// checkout master branch / modify file1, add and commit
		checkoutBranch("refs/heads/master");
		writeTrashFile(FILE1, "modified file1");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("commit3").call();

		// checkout topic branch / modify file1
		checkoutBranch("refs/heads/topic");
		writeTrashFile(FILE1, "unstaged modified file1");

		// rebase
		RebaseResult result = git.rebase().setUpstream("refs/heads/master")
				.call();
		assertEquals(Status.CONFLICTS, result.getStatus());
		assertEquals(1, result.getConflicts().size());
		assertEquals(FILE1, result.getConflicts().get(0));
	}

	@Test
	public void testRebaseWithUncommittedMasterChange() throws Exception {
		// create file1, add and commit
		writeTrashFile(FILE1, "file1");
		git.add().addFilepattern(FILE1).call();
		RevCommit commit = git.commit().setMessage("commit1").call();

		// create topic branch and checkout / create file2, add and commit
		createBranch(commit, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("commit2").call();

		// checkout master branch / modify file1, add and commit
		checkoutBranch("refs/heads/master");
		writeTrashFile(FILE1, "modified file1");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("commit3").call();

		// checkout topic branch / modify file1 and add
		checkoutBranch("refs/heads/topic");
		writeTrashFile(FILE1, "uncommitted modified file1");
		git.add().addFilepattern(FILE1).call();
		// do not commit

		// rebase
		RebaseResult result = git.rebase().setUpstream("refs/heads/master")
				.call();
		assertEquals(Status.CONFLICTS, result.getStatus());
		assertEquals(1, result.getConflicts().size());
		assertEquals(FILE1, result.getConflicts().get(0));
	}

	@Test
	public void testRebaseWithUnstagedMasterChangeBaseCommit() throws Exception {
		// create file0 + file1, add and commit
		writeTrashFile("file0", "file0");
		writeTrashFile(FILE1, "file1");
		git.add().addFilepattern("file0").addFilepattern(FILE1).call();
		RevCommit commit = git.commit().setMessage("commit1").call();

		// create topic branch and checkout / create file2, add and commit
		createBranch(commit, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("commit2").call();

		// checkout master branch / modify file1, add and commit
		checkoutBranch("refs/heads/master");
		writeTrashFile(FILE1, "modified file1");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("commit3").call();

		// checkout topic branch / modify file0
		checkoutBranch("refs/heads/topic");
		writeTrashFile("file0", "unstaged modified file0");

		// rebase
		assertEquals(Status.OK, git.rebase().setUpstream("refs/heads/master")
				.call().getStatus());
	}

	@Test
	public void testRebaseWithUncommittedMasterChangeBaseCommit()
			throws Exception {
		// create file0 + file1, add and commit
		File file0 = writeTrashFile("file0", "file0");
		writeTrashFile(FILE1, "file1");
		git.add().addFilepattern("file0").addFilepattern(FILE1).call();
		RevCommit commit = git.commit().setMessage("commit1").call();

		// create topic branch and checkout / create file2, add and commit
		createBranch(commit, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("commit2").call();

		// checkout master branch / modify file1, add and commit
		checkoutBranch("refs/heads/master");
		writeTrashFile(FILE1, "modified file1");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("commit3").call();

		// checkout topic branch / modify file0 and add
		checkoutBranch("refs/heads/topic");
		write(file0, "unstaged modified file0");
		git.add().addFilepattern("file0").call();
		// do not commit

		// get current index state
		String indexState = indexState(CONTENT);

		// rebase
		RebaseResult result = git.rebase().setUpstream("refs/heads/master")
				.call();
		assertEquals(Status.FAILED, result.getStatus());
		// staged file0 causes DIRTY_INDEX
		assertEquals(1, result.getFailingPaths().size());
		assertEquals(MergeFailureReason.DIRTY_INDEX, result.getFailingPaths()
				.get("file0"));
		assertEquals("unstaged modified file0", read(file0));
		// index shall be unchanged
		assertEquals(indexState, indexState(CONTENT));
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());
	}

	@Test
	public void testRebaseWithUnstagedMasterChangeOtherCommit()
			throws Exception {
		// create file0, add and commit
		writeTrashFile("file0", "file0");
		git.add().addFilepattern("file0").call();
		git.commit().setMessage("commit0").call();
		// create file1, add and commit
		writeTrashFile(FILE1, "file1");
		git.add().addFilepattern(FILE1).call();
		RevCommit commit = git.commit().setMessage("commit1").call();

		// create topic branch and checkout / create file2, add and commit
		createBranch(commit, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("commit2").call();

		// checkout master branch / modify file1, add and commit
		checkoutBranch("refs/heads/master");
		writeTrashFile(FILE1, "modified file1");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("commit3").call();

		// checkout topic branch / modify file0
		checkoutBranch("refs/heads/topic");
		writeTrashFile("file0", "unstaged modified file0");

		// rebase
		assertEquals(Status.OK, git.rebase().setUpstream("refs/heads/master")
				.call().getStatus());
	}

	@Test
	public void testRebaseWithUncommittedMasterChangeOtherCommit()
			throws Exception {
		// create file0, add and commit
		File file0 = writeTrashFile("file0", "file0");
		git.add().addFilepattern("file0").call();
		git.commit().setMessage("commit0").call();
		// create file1, add and commit
		writeTrashFile(FILE1, "file1");
		git.add().addFilepattern(FILE1).call();
		RevCommit commit = git.commit().setMessage("commit1").call();

		// create topic branch and checkout / create file2, add and commit
		createBranch(commit, "refs/heads/topic");
		checkoutBranch("refs/heads/topic");
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("commit2").call();

		// checkout master branch / modify file1, add and commit
		checkoutBranch("refs/heads/master");
		writeTrashFile(FILE1, "modified file1");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("commit3").call();

		// checkout topic branch / modify file0 and add
		checkoutBranch("refs/heads/topic");
		write(file0, "unstaged modified file0");
		git.add().addFilepattern("file0").call();
		// do not commit

		// get current index state
		String indexState = indexState(CONTENT);

		// rebase
		RebaseResult result = git.rebase().setUpstream("refs/heads/master")
				.call();
		assertEquals(Status.FAILED, result.getStatus());
		// staged file0 causes DIRTY_INDEX
		assertEquals(1, result.getFailingPaths().size());
		assertEquals(MergeFailureReason.DIRTY_INDEX, result.getFailingPaths()
				.get("file0"));
		assertEquals("unstaged modified file0", read(file0));
		// index shall be unchanged
		assertEquals(indexState, indexState(CONTENT));
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());
	}

	@Test
	public void testRebaseWithUncommittedDelete() throws Exception {
		// create file0 + file1, add and commit
		File file0 = writeTrashFile("file0", "file0");
		writeTrashFile(FILE1, "file1");
		git.add().addFilepattern("file0").addFilepattern(FILE1).call();
		RevCommit commit = git.commit().setMessage("commit1").call();

		// create topic branch
		createBranch(commit, "refs/heads/topic");

		// still on master / modify file1, add and commit
		writeTrashFile(FILE1, "modified file1");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("commit2").call();

		// checkout topic branch / delete file0 and add to index
		checkoutBranch("refs/heads/topic");
		git.rm().addFilepattern("file0").call();
		// do not commit

		// rebase
		RebaseResult result = git.rebase().setUpstream("refs/heads/master")
				.call();
		assertEquals(Status.FAST_FORWARD, result.getStatus());
		assertFalse("File should still be deleted", file0.exists());
		// index should only have updated file1
		assertEquals("[file1, mode:100644, content:modified file1]",
				indexState(CONTENT));
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());
	}

	private int countPicks() throws IOException {
		int count = 0;
		File todoFile = getTodoFile();
		BufferedReader br = new BufferedReader(new InputStreamReader(
				new FileInputStream(todoFile), "UTF-8"));
		try {
			String line = br.readLine();
			while (line != null) {
				String actionToken = line.substring(0, line.indexOf(' '));
				Action action = null;
				try {
					action = Action.parse(actionToken);
				} catch (Exception e) {
					// ignore
				}
				if (action != null)
					count++;
				line = br.readLine();
			}
			return count;
		} finally {
			br.close();
		}
	}

	@Test
	public void testFastForwardWithMultipleCommitsOnDifferentBranches()
			throws Exception {
		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		RevCommit first = git.commit().setMessage("Add file1").call();
		assertTrue(new File(db.getWorkTree(), FILE1).exists());

		// create a topic branch
		createBranch(first, "refs/heads/topic");

		// create file2 on master
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		RevCommit second = git.commit().setMessage("Add file2").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());

		// create side branch
		createBranch(second, "refs/heads/side");

		// update FILE1 on master
		writeTrashFile(FILE1, "blah");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("updated file1 on master")
				.call();

		// switch to side branch and update file2
		checkoutBranch("refs/heads/side");
		writeTrashFile("file2", "more change");
		git.add().addFilepattern("file2").call();
		RevCommit fourth = git.commit().setMessage("update file2 on side")
				.call();

		// switch back to master and merge in side
		checkoutBranch("refs/heads/master");
		MergeResult result = git.merge().include(fourth.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();
		assertEquals(MergeStatus.MERGED, result.getMergeStatus());

		// switch back to topic branch and rebase it onto master
		checkoutBranch("refs/heads/topic");
		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());
		checkFile(new File(db.getWorkTree(), "file2"), "more change");
		assertEquals(Status.FAST_FORWARD, res.getStatus());
	}

	@Test
	public void testRebaseShouldLeaveWorkspaceUntouchedWithUnstagedChangesConflict()
			throws Exception {
		writeTrashFile(FILE1, "initial file");
		git.add().addFilepattern(FILE1).call();
		RevCommit initial = git.commit().setMessage("initial commit").call();
		createBranch(initial, "refs/heads/side");

		writeTrashFile(FILE1, "updated file");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("updated FILE1 on master").call();

		// switch to side, modify the file
		checkoutBranch("refs/heads/side");
		writeTrashFile(FILE1, "side update");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("updated FILE1 on side").call();

		File theFile = writeTrashFile(FILE1, "dirty the file");

		// and attempt to rebase
		RebaseResult rebaseResult = git.rebase()
					.setUpstream("refs/heads/master").call();
		assertEquals(Status.CONFLICTS, rebaseResult.getStatus());
		assertEquals(1, rebaseResult.getConflicts().size());
		assertEquals(FILE1, rebaseResult.getConflicts().get(0));

		checkFile(theFile, "dirty the file");

		assertEquals(RepositoryState.SAFE, git.getRepository()
				.getRepositoryState());
	}

	@Test
	public void testAbortShouldAlsoAbortNonInteractiveRebaseWithRebaseApplyDir()
			throws Exception {
		writeTrashFile(FILE1, "initial file");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("initial commit").call();

		File applyDir = new File(db.getDirectory(), "rebase-apply");
		File headName = new File(applyDir, "head-name");
		FileUtils.mkdir(applyDir);
		write(headName, "master");
		db.writeOrigHead(db.resolve(Constants.HEAD));

		git.rebase().setOperation(Operation.ABORT).call();

		assertFalse("Abort should clean up .git/rebase-apply",
				applyDir.exists());
		assertEquals(RepositoryState.SAFE, git.getRepository()
				.getRepositoryState());
	}

	@Test
	public void testRebaseShouldBeAbleToHandleEmptyLinesInRebaseTodoFile()
			throws IOException {
		String emptyLine = "\n";
		String todo = "pick 1111111 Commit 1\n" + emptyLine
				+ "pick 2222222 Commit 2\n" + emptyLine
				+ "# Comment line at end\n";
		write(getTodoFile(), todo);

		RebaseCommand rebaseCommand = git.rebase();
		List<Step> steps = rebaseCommand.loadSteps();
		assertEquals(2, steps.size());
		assertEquals("1111111", steps.get(0).commit.name());
		assertEquals("2222222", steps.get(1).commit.name());
	}

	@Test
	public void testParseRewordCommand() throws Exception {
		String todo = "pick 1111111 Commit 1\n"
				+ "reword 2222222 Commit 2\n";
		write(getTodoFile(), todo);

		RebaseCommand rebaseCommand = git.rebase();
		List<Step> steps = rebaseCommand.loadSteps();

		assertEquals(2, steps.size());
		assertEquals("1111111", steps.get(0).commit.name());
		assertEquals("2222222", steps.get(1).commit.name());
		assertEquals(Action.REWORD, steps.get(1).action);
	}

	@Test
	public void testRebaseInteractiveReword() throws Exception {
		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Add file1").call();
		assertTrue(new File(db.getWorkTree(), FILE1).exists());

		// create file2 on master
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("Add file2").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());

		// update FILE1 on master
		writeTrashFile(FILE1, "blah");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("updated file1 on master").call();

		writeTrashFile("file2", "more change");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("update file2 on side").call();

		RebaseResult res = git.rebase().setUpstream("HEAD~2")
				.runInteractively(new InteractiveHandler() {
					public void prepareSteps(List<Step> steps) {
						steps.get(0).action = Action.REWORD;
					}
					public String modifyCommitMessage(String commit) {
						return "rewritten commit message";
					}
				}).call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());
		checkFile(new File(db.getWorkTree(), "file2"), "more change");
		assertEquals(Status.OK, res.getStatus());
		Iterator<RevCommit> logIterator = git.log().all().call().iterator();
		logIterator.next(); // skip first commit;
		String actualCommitMag = logIterator.next().getShortMessage();
		assertEquals("rewritten commit message", actualCommitMag);
	}

	@Test
	public void testRebaseInteractiveEdit() throws Exception {
		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Add file1").call();
		assertTrue(new File(db.getWorkTree(), FILE1).exists());

		// create file2 on master
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("Add file2").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());

		// update FILE1 on master
		writeTrashFile(FILE1, "blah");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("updated file1 on master").call();

		writeTrashFile("file2", "more change");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("update file2 on side").call();

		RebaseResult res = git.rebase().setUpstream("HEAD~2")
				.runInteractively(new InteractiveHandler() {
					public void prepareSteps(List<Step> steps) {
						steps.get(0).action = Action.EDIT;
					}

					public String modifyCommitMessage(String commit) {
						return ""; // not used
					}
				}).call();
		assertEquals(Status.STOPPED, res.getStatus());
		RevCommit toBeEditted = git.log().call().iterator().next();
		assertEquals("updated file1 on master", toBeEditted.getFullMessage());

		// change file and commit with new commit message
		writeTrashFile("file1", "edited");
		git.commit().setAll(true).setAmend(true)
				.setMessage("edited commit message").call();
		// resume rebase
		res = git.rebase().setOperation(Operation.CONTINUE).call();

		checkFile(new File(db.getWorkTree(), "file1"), "edited");
		assertEquals(Status.OK, res.getStatus());
		Iterator<RevCommit> logIterator = git.log().all().call().iterator();
		logIterator.next(); // skip first commit;
		String actualCommitMag = logIterator.next().getShortMessage();
		assertEquals("edited commit message", actualCommitMag);
	}

	private File getTodoFile() {
		File todoFile = new File(db.getDirectory(),
				"rebase-merge/git-rebase-todo");
		return todoFile;
	}
}
