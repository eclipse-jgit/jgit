/*
 * Copyright (C) 2010, 2013 Mathias Kinzler <mathias.kinzler@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.RebaseCommand.InteractiveHandler;
import org.eclipse.jgit.api.RebaseCommand.Operation;
import org.eclipse.jgit.api.RebaseResult.Status;
import org.eclipse.jgit.api.errors.InvalidRebaseStepException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IllegalTodoFileModification;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.events.ChangeRecorder;
import org.eclipse.jgit.events.ListenerHandle;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RebaseTodoLine;
import org.eclipse.jgit.lib.RebaseTodoLine.Action;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Before;
import org.junit.Test;

public class RebaseCommandTest extends RepositoryTestCase {
	private static final String GIT_REBASE_TODO = "rebase-merge/git-rebase-todo";

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
		RevCommit head;
		try (RevWalk walk = new RevWalk(db)) {
			head = walk.parseCommit(db.resolve(Constants.HEAD));
			DirCacheCheckout dco = new DirCacheCheckout(db, head.getTree(),
					db.lockDirCache(), commit.getTree());
			dco.setFailOnConflict(true);
			dco.checkout();
		}
		// update the HEAD
		RefUpdate refUpdate = db.updateRef(Constants.HEAD, true);
		refUpdate.setNewObjectId(commit);
		refUpdate.setRefLogMessage("checkout: moving to " + head.getName(),
				false);
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
		RevCommit second = git.commit().setMessage("Add file2").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());

		checkoutBranch("refs/heads/topic");
		assertFalse(new File(db.getWorkTree(), "file2").exists());

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());
		checkFile(file2, "file2");
		assertEquals(Status.FAST_FORWARD, res.getStatus());

		List<ReflogEntry> headLog = db.getReflogReader(Constants.HEAD)
				.getReverseEntries();
		List<ReflogEntry> topicLog = db.getReflogReader("refs/heads/topic")
				.getReverseEntries();
		List<ReflogEntry> masterLog = db.getReflogReader("refs/heads/master")
				.getReverseEntries();
		assertEquals("rebase finished: returning to refs/heads/topic", headLog
				.get(0).getComment());
		assertEquals("checkout: moving from topic to " + second.getName(),
				headLog.get(1).getComment());
		assertEquals(2, masterLog.size());
		assertEquals(2, topicLog.size());
		assertEquals(
				"rebase finished: refs/heads/topic onto " + second.getName(),
				topicLog.get(0).getComment());
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
		RevCommit second = git.commit().setMessage("Change content of file2")
				.call();

		checkoutBranch("refs/heads/topic");
		assertFalse(new File(db.getWorkTree(), "file2").exists());

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());
		checkFile(file2, "file2 new content");
		assertEquals(Status.FAST_FORWARD, res.getStatus());

		List<ReflogEntry> headLog = db.getReflogReader(Constants.HEAD)
				.getReverseEntries();
		List<ReflogEntry> topicLog = db.getReflogReader("refs/heads/topic")
				.getReverseEntries();
		List<ReflogEntry> masterLog = db.getReflogReader("refs/heads/master")
				.getReverseEntries();
		assertEquals("rebase finished: returning to refs/heads/topic", headLog
				.get(0).getComment());
		assertEquals("checkout: moving from topic to " + second.getName(),
				headLog.get(1).getComment());
		assertEquals(3, masterLog.size());
		assertEquals(2, topicLog.size());
		assertEquals(
				"rebase finished: refs/heads/topic onto " + second.getName(),
				topicLog.get(0).getComment());
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

		try (RevWalk rw = new RevWalk(db)) {
			rw.markStart(rw.parseCommit(db.resolve("refs/heads/topic")));
			assertDerivedFrom(rw.next(), e);
			assertDerivedFrom(rw.next(), d);
			assertDerivedFrom(rw.next(), c);
			assertEquals(b, rw.next());
			assertEquals(a, rw.next());
		}

		List<ReflogEntry> headLog = db.getReflogReader(Constants.HEAD)
				.getReverseEntries();
		List<ReflogEntry> sideLog = db.getReflogReader("refs/heads/side")
				.getReverseEntries();
		List<ReflogEntry> topicLog = db.getReflogReader("refs/heads/topic")
				.getReverseEntries();
		List<ReflogEntry> masterLog = db.getReflogReader("refs/heads/master")
				.getReverseEntries();
		assertEquals("rebase finished: returning to refs/heads/topic", headLog
				.get(0).getComment());
		assertEquals("rebase: update file2 on side", headLog.get(1)
				.getComment());
		assertEquals("rebase: Add file2", headLog.get(2).getComment());
		assertEquals("rebase: update file3 on topic", headLog.get(3)
				.getComment());
		assertEquals("checkout: moving from topic to " + b.getName(), headLog
				.get(4).getComment());
		assertEquals(2, masterLog.size());
		assertEquals(2, sideLog.size());
		assertEquals(5, topicLog.size());
		assertEquals("rebase finished: refs/heads/topic onto " + b.getName(),
				topicLog.get(0).getComment());
	}

	static void assertDerivedFrom(RevCommit derived, RevCommit original) {
		assertThat(derived, not(equalTo(original)));
		assertEquals(original.getFullMessage(), derived.getFullMessage());
	}

	@Test
	public void testRebasePreservingMerges1() throws Exception {
		doTestRebasePreservingMerges(true);
	}

	@Test
	public void testRebasePreservingMerges2() throws Exception {
		doTestRebasePreservingMerges(false);
	}

	/**
	 * Transforms the same before-state as in
	 * {@link #testRebaseShouldIgnoreMergeCommits()} to the following.
	 * <p>
	 * This test should always rewrite E.
	 *
	 * <pre>
	 * A - B (master) - - -  C' - D' - F' (topic')
	 *   \                    \       /
	 *    C - D - F (topic)      - E'
	 *     \     /
	 *       - E (side)
	 * </pre>
	 *
	 * @param testConflict
	 * @throws Exception
	 */
	private void doTestRebasePreservingMerges(boolean testConflict)
			throws Exception {
		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		RevCommit a = git.commit().setMessage("commit a").call();

		// create a topic branch
		createBranch(a, "refs/heads/topic");

		// update FILE1 on master
		writeTrashFile(FILE1, "blah");
		writeTrashFile("conflict", "b");
		git.add().addFilepattern(".").call();
		RevCommit b = git.commit().setMessage("commit b").call();

		checkoutBranch("refs/heads/topic");
		writeTrashFile("file3", "more changess");
		git.add().addFilepattern("file3").call();
		RevCommit c = git.commit().setMessage("commit c").call();

		// create a branch from the topic commit
		createBranch(c, "refs/heads/side");

		// second commit on topic
		writeTrashFile("file2", "file2");
		if (testConflict)
			writeTrashFile("conflict", "d");
		git.add().addFilepattern(".").call();
		RevCommit d = git.commit().setMessage("commit d").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());

		// switch to side branch and update file2
		checkoutBranch("refs/heads/side");
		writeTrashFile("file3", "more change");
		if (testConflict)
			writeTrashFile("conflict", "e");
		git.add().addFilepattern(".").call();
		RevCommit e = git.commit().setMessage("commit e").call();

		// switch back to topic and merge in side, creating f
		checkoutBranch("refs/heads/topic");
		MergeResult result = git.merge().include(e.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();
		final RevCommit f;
		if (testConflict) {
			assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());
			assertEquals(Collections.singleton("conflict"), git.status().call()
					.getConflicting());
			// resolve
			writeTrashFile("conflict", "f resolved");
			git.add().addFilepattern("conflict").call();
			f = git.commit().setMessage("commit f").call();
		} else {
			assertEquals(MergeStatus.MERGED, result.getMergeStatus());
			try (RevWalk rw = new RevWalk(db)) {
				f = rw.parseCommit(result.getNewHead());
			}
		}

		RebaseResult res = git.rebase().setUpstream("refs/heads/master")
				.setPreserveMerges(true).call();
		if (testConflict) {
			// first there is a conflict whhen applying d
			assertEquals(Status.STOPPED, res.getStatus());
			assertEquals(Collections.singleton("conflict"), git.status().call()
					.getConflicting());
			assertTrue(read("conflict").contains("\nb\n=======\nd\n"));
			// resolve
			writeTrashFile("conflict", "d new");
			git.add().addFilepattern("conflict").call();
			res = git.rebase().setOperation(Operation.CONTINUE).call();

			// then there is a conflict when applying e
			assertEquals(Status.STOPPED, res.getStatus());
			assertEquals(Collections.singleton("conflict"), git.status().call()
					.getConflicting());
			assertTrue(read("conflict").contains("\nb\n=======\ne\n"));
			// resolve
			writeTrashFile("conflict", "e new");
			git.add().addFilepattern("conflict").call();
			res = git.rebase().setOperation(Operation.CONTINUE).call();

			// finally there is a conflict merging e'
			assertEquals(Status.STOPPED, res.getStatus());
			assertEquals(Collections.singleton("conflict"), git.status().call()
					.getConflicting());
			assertTrue(read("conflict").contains("\nd new\n=======\ne new\n"));
			// resolve
			writeTrashFile("conflict", "f new resolved");
			git.add().addFilepattern("conflict").call();
			res = git.rebase().setOperation(Operation.CONTINUE).call();
		}
		assertEquals(Status.OK, res.getStatus());

		if (testConflict)
			assertEquals("f new resolved", read("conflict"));
		assertEquals("blah", read(FILE1));
		assertEquals("file2", read("file2"));
		assertEquals("more change", read("file3"));

		try (RevWalk rw = new RevWalk(db)) {
			rw.markStart(rw.parseCommit(db.resolve("refs/heads/topic")));
			RevCommit newF = rw.next();
			assertDerivedFrom(newF, f);
			assertEquals(2, newF.getParentCount());
			RevCommit newD = rw.next();
			assertDerivedFrom(newD, d);
			if (testConflict)
				assertEquals("d new", readFile("conflict", newD));
			RevCommit newE = rw.next();
			assertDerivedFrom(newE, e);
			if (testConflict)
				assertEquals("e new", readFile("conflict", newE));
			assertEquals(newD, newF.getParent(0));
			assertEquals(newE, newF.getParent(1));
			assertDerivedFrom(rw.next(), c);
			assertEquals(b, rw.next());
			assertEquals(a, rw.next());
		}
	}

	private String readFile(String path, RevCommit commit) throws IOException {
		try (TreeWalk walk = TreeWalk.forPath(db, path, commit.getTree())) {
			ObjectLoader loader = db.open(walk.getObjectId(0),
					Constants.OBJ_BLOB);
			String result = RawParseUtils.decode(loader.getCachedBytes());
			return result;
		}
	}

	@Test
	public void testRebasePreservingMergesWithUnrelatedSide1() throws Exception {
		doTestRebasePreservingMergesWithUnrelatedSide(true);
	}

	@Test
	public void testRebasePreservingMergesWithUnrelatedSide2() throws Exception {
		doTestRebasePreservingMergesWithUnrelatedSide(false);
	}

	/**
	 * Rebase topic onto master, not rewriting E. The merge resulting in D is
	 * confliicting to show that the manual merge resolution survives the
	 * rebase.
	 *
	 * <pre>
	 * A - B - G (master)
	 *  \   \
	 *   \   C - D - F (topic)
	 *    \     /
	 *      E (side)
	 * </pre>
	 *
	 * <pre>
	 * A - B - G (master)
	 *  \       \
	 *   \       C' - D' - F' (topic')
	 *    \          /
	 *      E (side)
	 * </pre>
	 *
	 * @param testConflict
	 * @throws Exception
	 */
	private void doTestRebasePreservingMergesWithUnrelatedSide(
			boolean testConflict) throws Exception {
		try (RevWalk rw = new RevWalk(db)) {
			rw.sort(RevSort.TOPO);

			writeTrashFile(FILE1, FILE1);
			git.add().addFilepattern(FILE1).call();
			RevCommit a = git.commit().setMessage("commit a").call();

			writeTrashFile("file2", "blah");
			git.add().addFilepattern("file2").call();
			RevCommit b = git.commit().setMessage("commit b").call();

			// create a topic branch
			createBranch(b, "refs/heads/topic");
			checkoutBranch("refs/heads/topic");

			writeTrashFile("file3", "more changess");
			writeTrashFile(FILE1, "preparing conflict");
			git.add().addFilepattern("file3").addFilepattern(FILE1).call();
			RevCommit c = git.commit().setMessage("commit c").call();

			createBranch(a, "refs/heads/side");
			checkoutBranch("refs/heads/side");
			writeTrashFile("conflict", "e");
			writeTrashFile(FILE1, FILE1 + "\n" + "line 2");
			git.add().addFilepattern(".").call();
			RevCommit e = git.commit().setMessage("commit e").call();

			// switch back to topic and merge in side, creating d
			checkoutBranch("refs/heads/topic");
			MergeResult result = git.merge().include(e)
					.setStrategy(MergeStrategy.RESOLVE).call();

			assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());
			assertEquals(result.getConflicts().keySet(),
					Collections.singleton(FILE1));
			writeTrashFile(FILE1, "merge resolution");
			git.add().addFilepattern(FILE1).call();
			RevCommit d = git.commit().setMessage("commit d").call();

			RevCommit f = commitFile("file2", "new content two", "topic");

			checkoutBranch("refs/heads/master");
			writeTrashFile("fileg", "fileg");
			if (testConflict)
				writeTrashFile("conflict", "g");
			git.add().addFilepattern(".").call();
			RevCommit g = git.commit().setMessage("commit g").call();

			checkoutBranch("refs/heads/topic");
			RebaseResult res = git.rebase().setUpstream("refs/heads/master")
					.setPreserveMerges(true).call();
			if (testConflict) {
				assertEquals(Status.STOPPED, res.getStatus());
				assertEquals(Collections.singleton("conflict"), git.status().call()
						.getConflicting());
				// resolve
				writeTrashFile("conflict", "e");
				git.add().addFilepattern("conflict").call();
				res = git.rebase().setOperation(Operation.CONTINUE).call();
			}
			assertEquals(Status.OK, res.getStatus());

			assertEquals("merge resolution", read(FILE1));
			assertEquals("new content two", read("file2"));
			assertEquals("more changess", read("file3"));
			assertEquals("fileg", read("fileg"));

			rw.markStart(rw.parseCommit(db.resolve("refs/heads/topic")));
			RevCommit newF = rw.next();
			assertDerivedFrom(newF, f);
			RevCommit newD = rw.next();
			assertDerivedFrom(newD, d);
			assertEquals(2, newD.getParentCount());
			RevCommit newC = rw.next();
			assertDerivedFrom(newC, c);
			RevCommit newE = rw.next();
			assertEquals(e, newE);
			assertEquals(newC, newD.getParent(0));
			assertEquals(e, newD.getParent(1));
			assertEquals(g, rw.next());
			assertEquals(b, rw.next());
			assertEquals(a, rw.next());
		}
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

		assertEquals(2, db.getReflogReader(Constants.HEAD).getReverseEntries()
				.size());
		assertEquals(2, db.getReflogReader("refs/heads/master")
				.getReverseEntries().size());
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

		assertEquals(1, db.getReflogReader(Constants.HEAD).getReverseEntries()
				.size());
		assertEquals(1, db.getReflogReader("refs/heads/master")
				.getReverseEntries().size());
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
		try (RevWalk rw = new RevWalk(db)) {
			assertEquals(lastMasterChange, rw.parseCommit(
					db.resolve(Constants.HEAD)).getParent(0));
		}
		assertEquals(origHead, db.readOrigHead());
		List<ReflogEntry> headLog = db.getReflogReader(Constants.HEAD)
				.getReverseEntries();
		List<ReflogEntry> topicLog = db.getReflogReader("refs/heads/topic")
				.getReverseEntries();
		List<ReflogEntry> masterLog = db.getReflogReader("refs/heads/master")
				.getReverseEntries();
		assertEquals(2, masterLog.size());
		assertEquals(3, topicLog.size());
		assertEquals("rebase finished: refs/heads/topic onto "
				+ lastMasterChange.getName(), topicLog.get(0).getComment());
		assertEquals("rebase finished: returning to refs/heads/topic", headLog
				.get(0).getComment());
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
		try (RevWalk rw = new RevWalk(db)) {
			assertEquals(lastMasterChange, rw.parseCommit(
					db.resolve(Constants.HEAD)).getParent(0));
		}

		List<ReflogEntry> headLog = db.getReflogReader(Constants.HEAD)
				.getReverseEntries();
		assertEquals(8, headLog.size());
		assertEquals("rebase: change file1 in topic", headLog.get(0)
				.getComment());
		assertEquals("checkout: moving from " + topicCommit.getName() + " to "
				+ lastMasterChange.getName(), headLog.get(1).getComment());
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
		try (RevWalk rw = new RevWalk(db)) {
			assertEquals(addFile2, rw.parseCommit(
					db.resolve(Constants.HEAD)).getParent(0));
		}

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

		assertEquals(RepositoryState.REBASING_MERGE, db
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
		try (RevWalk rw = new RevWalk(db)) {
			assertEquals(lastTopicCommit,
					rw.parseCommit(db.resolve(Constants.HEAD)));
		}
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());

		// rebase- dir in .git must be deleted
		assertFalse(new File(db.getDirectory(), "rebase-merge").exists());
	}

	@Test
	public void testStopOnConflictAndAbortWithDetachedHEAD() throws Exception {
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

		git.checkout().setName(lastTopicCommit.getName()).call();

		RebaseResult res = git.rebase().setUpstream("refs/heads/master").call();
		assertEquals(Status.STOPPED, res.getStatus());
		assertEquals(conflicting, res.getCurrentCommit());
		checkFile(FILE1,
				"<<<<<<< Upstream, based on master\n1master\n=======\n1topic",
				">>>>>>> e0d1dea change file1 in topic\n2\n3\ntopic4");

		assertEquals(RepositoryState.REBASING_MERGE,
				db.getRepositoryState());
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
		assertEquals(lastTopicCommit.getName(), db.getFullBranch());
		checkFile(FILE1, "1topic", "2", "3", "topic4");
		try (RevWalk rw = new RevWalk(db)) {
			assertEquals(lastTopicCommit,
					rw.parseCommit(db.resolve(Constants.HEAD)));
		}
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
		try (RevWalk rw = new RevWalk(db)) {
			RevCommit rc = rw.parseCommit(headId);
			RevCommit parent = rw.parseCommit(rc.getParent(0));
			assertEquals("change file1 in topic\n\nThis is conflicting", parent
					.getFullMessage());
		}
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
		assertEquals(RepositoryState.REBASING_MERGE,
				db.getRepositoryState());

		git.rebase().setOperation(Operation.SKIP).call();

		ObjectId headId = db.resolve(Constants.HEAD);
		try (RevWalk rw = new RevWalk(db)) {
			RevCommit rc = rw.parseCommit(headId);
			assertEquals("change file1 in master", rc.getFullMessage());
		}
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
		assertEquals(RepositoryState.REBASING_MERGE,
				db.getRepositoryState());

		git.rebase().setOperation(Operation.SKIP).call();

		ObjectId headId = db.resolve(Constants.HEAD);
		try (RevWalk rw = new RevWalk(db)) {
			RevCommit rc = rw.parseCommit(headId);
			RevCommit parent = rw.parseCommit(rc.getParent(0));
			assertEquals("A different commit message", parent.getFullMessage());
		}
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

		assertEquals(RepositoryState.REBASING_MERGE, db
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
		try (RevWalk rw = new RevWalk(db)) {
			assertEquals(conflicting, rw.parseCommit(db.resolve(Constants.HEAD)));
			assertEquals(RepositoryState.SAFE, db.getRepositoryState());
		}

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
				convertedAuthor.getBytes(UTF_8));
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
				convertedAuthor.getBytes(UTF_8));
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
		assertEquals(Status.UNCOMMITTED_CHANGES, result.getStatus());
		assertEquals(1, result.getUncommittedChanges().size());
		assertEquals("file2", result.getUncommittedChanges().get(0));
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
		assertEquals(Status.UNCOMMITTED_CHANGES, result.getStatus());
		assertEquals(1, result.getUncommittedChanges().size());
		assertEquals("file2", result.getUncommittedChanges().get(0));

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
		assertEquals(Status.UNCOMMITTED_CHANGES, result.getStatus());
		assertEquals(1, result.getUncommittedChanges().size());
		assertEquals(FILE1, result.getUncommittedChanges().get(0));
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
		assertEquals(Status.UNCOMMITTED_CHANGES, result.getStatus());
		assertEquals(1, result.getUncommittedChanges().size());
		assertEquals(FILE1, result.getUncommittedChanges().get(0));
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
		assertEquals(Status.UNCOMMITTED_CHANGES,
				git.rebase().setUpstream("refs/heads/master")
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
		assertEquals(Status.UNCOMMITTED_CHANGES, result.getStatus());
		assertEquals(1, result.getUncommittedChanges().size());
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
		assertEquals(Status.UNCOMMITTED_CHANGES,
				git.rebase().setUpstream("refs/heads/master")
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
		assertEquals(Status.UNCOMMITTED_CHANGES, result.getStatus());
		// staged file0 causes DIRTY_INDEX
		assertEquals(1, result.getUncommittedChanges().size());
		assertEquals("unstaged modified file0", read(file0));
		// index shall be unchanged
		assertEquals(indexState, indexState(CONTENT));
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());
	}

	@Test
	public void testFastForwardRebaseWithModification() throws Exception {
		// create file0 + file1, add and commit
		writeTrashFile("file0", "file0");
		writeTrashFile(FILE1, "file1");
		git.add().addFilepattern("file0").addFilepattern(FILE1).call();
		RevCommit commit = git.commit().setMessage("commit1").call();

		// create topic branch
		createBranch(commit, "refs/heads/topic");

		// still on master / modify file1, add and commit
		writeTrashFile(FILE1, "modified file1");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("commit2").call();

		// checkout topic branch / modify file0 and add to index
		checkoutBranch("refs/heads/topic");
		writeTrashFile("file0", "modified file0 in index");
		git.add().addFilepattern("file0").addFilepattern(FILE1).call();
		// modify once more
		writeTrashFile("file0", "modified file0");

		// rebase
		RebaseResult result = git.rebase().setUpstream("refs/heads/master")
				.call();
		assertEquals(Status.FAST_FORWARD, result.getStatus());
		checkFile(new File(db.getWorkTree(), "file0"), "modified file0");
		checkFile(new File(db.getWorkTree(), FILE1), "modified file1");
		assertEquals("[file0, mode:100644, content:modified file0 in index]"
				+ "[file1, mode:100644, content:modified file1]",
				indexState(CONTENT));
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());
	}

	@Test
	public void testRebaseWithModificationShouldNotDeleteData()
			throws Exception {
		// create file0 + file1, add and commit
		writeTrashFile("file0", "file0");
		writeTrashFile(FILE1, "file1");
		git.add().addFilepattern("file0").addFilepattern(FILE1).call();
		RevCommit commit = git.commit().setMessage("commit1").call();

		// create topic branch
		createBranch(commit, "refs/heads/topic");

		// still on master / modify file1, add and commit
		writeTrashFile(FILE1, "modified file1");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("commit2").call();

		// checkout topic branch / modify file1, add and commit
		checkoutBranch("refs/heads/topic");
		writeTrashFile(FILE1, "modified file1 on topic");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("commit3").call();

		writeTrashFile("file0", "modified file0");

		RebaseResult result = git.rebase().setUpstream("refs/heads/master")
				.call();
		// the following condition was true before commit 83b6ab233:
		// jgit started the rebase and deleted the change on abort
		// This test should verify that content was deleted
		if (result.getStatus() == Status.STOPPED)
			git.rebase().setOperation(Operation.ABORT).call();

		checkFile(new File(db.getWorkTree(), "file0"), "modified file0");
		checkFile(new File(db.getWorkTree(), FILE1),
				"modified file1 on topic");
		assertEquals("[file0, mode:100644, content:file0]"
				+ "[file1, mode:100644, content:modified file1 on topic]",
				indexState(CONTENT));
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

	@Test
	public void testRebaseWithAutoStash()
			throws Exception {
		// create file0, add and commit
		db.getConfig().setBoolean(ConfigConstants.CONFIG_REBASE_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOSTASH, true);
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
		assertEquals(Status.OK,
				git.rebase().setUpstream("refs/heads/master").call()
						.getStatus());
		checkFile(new File(db.getWorkTree(), "file0"),
				"unstaged modified file0");
		checkFile(new File(db.getWorkTree(), FILE1), "modified file1");
		checkFile(new File(db.getWorkTree(), "file2"), "file2");
		assertEquals("[file0, mode:100644, content:file0]"
				+ "[file1, mode:100644, content:modified file1]"
				+ "[file2, mode:100644, content:file2]",
				indexState(CONTENT));
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());
	}

	@Test
	public void testRebaseWithAutoStashAndSubdirs() throws Exception {
		// create file0, add and commit
		db.getConfig().setBoolean(ConfigConstants.CONFIG_REBASE_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOSTASH, true);
		writeTrashFile("sub/file0", "file0");
		git.add().addFilepattern("sub/file0").call();
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
		writeTrashFile("sub/file0", "unstaged modified file0");

		ChangeRecorder recorder = new ChangeRecorder();
		ListenerHandle handle = db.getListenerList()
				.addWorkingTreeModifiedListener(recorder);
		try {
			// rebase
			assertEquals(Status.OK, git.rebase()
					.setUpstream("refs/heads/master").call().getStatus());
		} finally {
			handle.remove();
		}
		checkFile(new File(new File(db.getWorkTree(), "sub"), "file0"),
				"unstaged modified file0");
		checkFile(new File(db.getWorkTree(), FILE1), "modified file1");
		checkFile(new File(db.getWorkTree(), "file2"), "file2");
		assertEquals(
				"[file1, mode:100644, content:modified file1]"
						+ "[file2, mode:100644, content:file2]"
						+ "[sub/file0, mode:100644, content:file0]",
				indexState(CONTENT));
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());
		recorder.assertEvent(new String[] { "file1", "file2", "sub/file0" },
				new String[0]);
	}

	@Test
	public void testRebaseWithAutoStashConflictOnApply() throws Exception {
		// create file0, add and commit
		db.getConfig().setBoolean(ConfigConstants.CONFIG_REBASE_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOSTASH, true);
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
		writeTrashFile("file1", "unstaged modified file1");

		// rebase
		assertEquals(Status.STASH_APPLY_CONFLICTS,
				git.rebase().setUpstream("refs/heads/master").call()
						.getStatus());
		checkFile(new File(db.getWorkTree(), "file0"), "file0");
		checkFile(
				new File(db.getWorkTree(), FILE1),
				"<<<<<<< HEAD\nmodified file1\n=======\nunstaged modified file1\n>>>>>>> stash\n");
		checkFile(new File(db.getWorkTree(), "file2"), "file2");
		assertEquals(
				"[file0, mode:100644, content:file0]"
						+ "[file1, mode:100644, stage:1, content:file1]"
						+ "[file1, mode:100644, stage:2, content:modified file1]"
						+ "[file1, mode:100644, stage:3, content:unstaged modified file1]"
						+ "[file2, mode:100644, content:file2]",
				indexState(CONTENT));
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());

		List<DiffEntry> diffs = getStashedDiff();
		assertEquals(1, diffs.size());
		assertEquals(DiffEntry.ChangeType.MODIFY, diffs.get(0).getChangeType());
		assertEquals("file1", diffs.get(0).getOldPath());
	}

	@Test
	public void testFastForwardRebaseWithAutoStash() throws Exception {
		// create file0, add and commit
		db.getConfig().setBoolean(ConfigConstants.CONFIG_REBASE_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOSTASH, true);
		writeTrashFile("file0", "file0");
		git.add().addFilepattern("file0").call();
		git.commit().setMessage("commit0").call();
		// create file1, add and commit
		writeTrashFile(FILE1, "file1");
		git.add().addFilepattern(FILE1).call();
		RevCommit commit = git.commit().setMessage("commit1").call();

		// create topic branch
		createBranch(commit, "refs/heads/topic");

		// checkout master branch / modify file1, add and commit
		checkoutBranch("refs/heads/master");
		writeTrashFile(FILE1, "modified file1");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("commit3").call();

		// checkout topic branch / modify file0
		checkoutBranch("refs/heads/topic");
		writeTrashFile("file0", "unstaged modified file0");

		// rebase
		assertEquals(Status.FAST_FORWARD,
				git.rebase().setUpstream("refs/heads/master")
				.call().getStatus());
		checkFile(new File(db.getWorkTree(), "file0"),
				"unstaged modified file0");
		checkFile(new File(db.getWorkTree(), FILE1), "modified file1");
		assertEquals("[file0, mode:100644, content:file0]"
				+ "[file1, mode:100644, content:modified file1]",
				indexState(CONTENT));
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());
	}

	private List<DiffEntry> getStashedDiff() throws AmbiguousObjectException,
			IncorrectObjectTypeException, IOException, MissingObjectException {
		ObjectId stashId = db.resolve("stash@{0}");
		try (RevWalk revWalk = new RevWalk(db)) {
			RevCommit stashCommit = revWalk.parseCommit(stashId);
			List<DiffEntry> diffs = diffWorkingAgainstHead(stashCommit,
					revWalk);
			return diffs;
		}
	}

	private TreeWalk createTreeWalk() {
		TreeWalk walk = new TreeWalk(db);
		walk.setRecursive(true);
		walk.setFilter(TreeFilter.ANY_DIFF);
		return walk;
	}

	private List<DiffEntry> diffWorkingAgainstHead(final RevCommit commit,
			RevWalk revWalk)
			throws IOException {
		RevCommit parentCommit = revWalk.parseCommit(commit.getParent(0));
		try (TreeWalk walk = createTreeWalk()) {
			walk.addTree(parentCommit.getTree());
			walk.addTree(commit.getTree());
			return DiffEntry.scan(walk);
		}
	}

	private int countPicks() throws IOException {
		int count = 0;
		File todoFile = getTodoFile();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(
				new FileInputStream(todoFile), UTF_8))) {
			String line = br.readLine();
			while (line != null) {
				int firstBlank = line.indexOf(' ');
				if (firstBlank != -1) {
					String actionToken = line.substring(0, firstBlank);
					Action action = null;
					try {
						action = Action.parse(actionToken);
					} catch (Exception e) {
						// ignore
					}
					if (Action.PICK.equals(action))
						count++;
				}
				line = br.readLine();
			}
			return count;
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
		assertEquals(Status.UNCOMMITTED_CHANGES, rebaseResult.getStatus());
		assertEquals(1, rebaseResult.getUncommittedChanges().size());
		assertEquals(FILE1, rebaseResult.getUncommittedChanges().get(0));

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

		List<RebaseTodoLine> steps = db.readRebaseTodo(GIT_REBASE_TODO, false);
		assertEquals(2, steps.size());
		assertEquals("1111111", steps.get(0).getCommit().name());
		assertEquals("2222222", steps.get(1).getCommit().name());
	}

	@Test
	public void testRebaseShouldBeAbleToHandleLinesWithoutCommitMessageInRebaseTodoFile()
			throws IOException {
		String todo = "pick 1111111 \n" + "pick 2222222 Commit 2\n"
				+ "# Comment line at end\n";
		write(getTodoFile(), todo);

		List<RebaseTodoLine> steps = db.readRebaseTodo(GIT_REBASE_TODO, false);
		assertEquals(2, steps.size());
		assertEquals("1111111", steps.get(0).getCommit().name());
		assertEquals("2222222", steps.get(1).getCommit().name());
	}

	@Test
	public void testRebaseShouldNotFailIfUserAddCommentLinesInPrepareSteps()
			throws Exception {
		commitFile(FILE1, FILE1, "master");
		RevCommit c2 = commitFile("file2", "file2", "master");

		// update files on master
		commitFile(FILE1, "blah", "master");
		RevCommit c4 = commitFile("file2", "more change", "master");

		RebaseResult res = git.rebase().setUpstream("HEAD~2")
				.runInteractively(new InteractiveHandler() {
					@Override
					public void prepareSteps(List<RebaseTodoLine> steps) {
						steps.add(0, new RebaseTodoLine(
								"# Comment that should not be processed"));
					}

					@Override
					public String modifyCommitMessage(String commit) {
						fail("modifyCommitMessage() was not expected to be called");
						return commit;
					}
				}).call();

		assertEquals(RebaseResult.Status.FAST_FORWARD, res.getStatus());

		RebaseResult res2 = git.rebase().setUpstream("HEAD~2")
				.runInteractively(new InteractiveHandler() {
					@Override
					public void prepareSteps(List<RebaseTodoLine> steps) {
						try {
							// delete RevCommit c4
							steps.get(0).setAction(Action.COMMENT);
						} catch (IllegalTodoFileModification e) {
							fail("unexpected exception: " + e);
						}
					}

					@Override
					public String modifyCommitMessage(String commit) {
						fail("modifyCommitMessage() was not expected to be called");
						return commit;
					}
				}).call();

		assertEquals(RebaseResult.Status.OK, res2.getStatus());

		ObjectId headId = db.resolve(Constants.HEAD);
		try (RevWalk rw = new RevWalk(db)) {
			RevCommit rc = rw.parseCommit(headId);

			ObjectId head1Id = db.resolve(Constants.HEAD + "~1");
			RevCommit rc1 = rw.parseCommit(head1Id);

			assertEquals(rc.getFullMessage(), c4.getFullMessage());
			assertEquals(rc1.getFullMessage(), c2.getFullMessage());
		}
	}

	@Test
	public void testParseRewordCommand() throws Exception {
		String todo = "pick 1111111 Commit 1\n"
				+ "reword 2222222 Commit 2\n";
		write(getTodoFile(), todo);

		List<RebaseTodoLine> steps = db.readRebaseTodo(GIT_REBASE_TODO, false);

		assertEquals(2, steps.size());
		assertEquals("1111111", steps.get(0).getCommit().name());
		assertEquals("2222222", steps.get(1).getCommit().name());
		assertEquals(Action.REWORD, steps.get(1).getAction());
	}

	@Test
	public void testEmptyRebaseTodo() throws Exception {
		write(getTodoFile(), "");
		assertEquals(0, db.readRebaseTodo(GIT_REBASE_TODO, true).size());
		assertEquals(0, db.readRebaseTodo(GIT_REBASE_TODO, false).size());
	}

	@Test
	public void testOnlyCommentRebaseTodo() throws Exception {
		write(getTodoFile(), "# a b c d e\n# e f");
		assertEquals(0, db.readRebaseTodo(GIT_REBASE_TODO, false).size());
		List<RebaseTodoLine> lines = db.readRebaseTodo(GIT_REBASE_TODO, true);
		assertEquals(2, lines.size());
		for (RebaseTodoLine line : lines)
			assertEquals(Action.COMMENT, line.getAction());
		write(getTodoFile(), "# a b c d e\n# e f\n");
		assertEquals(0, db.readRebaseTodo(GIT_REBASE_TODO, false).size());
		lines = db.readRebaseTodo(GIT_REBASE_TODO, true);
		assertEquals(2, lines.size());
		for (RebaseTodoLine line : lines)
			assertEquals(Action.COMMENT, line.getAction());
		write(getTodoFile(), " 	 \r\n# a b c d e\r\n# e f\r\n#");
		assertEquals(0, db.readRebaseTodo(GIT_REBASE_TODO, false).size());
		lines = db.readRebaseTodo(GIT_REBASE_TODO, true);
		assertEquals(4, lines.size());
		for (RebaseTodoLine line : lines)
			assertEquals(Action.COMMENT, line.getAction());
	}

	@Test
	public void testLeadingSpacesRebaseTodo() throws Exception {
		String todo =	"  \t\t pick 1111111 Commit 1\n"
					+ "\t\n"
					+ "\treword 2222222 Commit 2\n";
		write(getTodoFile(), todo);

		List<RebaseTodoLine> steps = db.readRebaseTodo(GIT_REBASE_TODO, false);

		assertEquals(2, steps.size());
		assertEquals("1111111", steps.get(0).getCommit().name());
		assertEquals("2222222", steps.get(1).getCommit().name());
		assertEquals(Action.REWORD, steps.get(1).getAction());
	}

	@Test
	public void testRebaseShouldTryToParseValidLineMarkedAsComment()
			throws IOException {
		String todo = "# pick 1111111 Valid line commented out with space\n"
				+ "#edit 2222222 Valid line commented out without space\n"
				+ "# pick invalidLine Comment line at end\n";
		write(getTodoFile(), todo);

		List<RebaseTodoLine> steps = db.readRebaseTodo(GIT_REBASE_TODO, true);
		assertEquals(3, steps.size());

		RebaseTodoLine firstLine = steps.get(0);

		assertEquals("1111111", firstLine.getCommit().name());
		assertEquals("Valid line commented out with space",
				firstLine.getShortMessage());
		assertEquals("comment", firstLine.getAction().toToken());

		try {
			firstLine.setAction(Action.PICK);
			assertEquals("1111111", firstLine.getCommit().name());
			assertEquals("pick", firstLine.getAction().toToken());
		} catch (Exception e) {
			fail("Valid parsable RebaseTodoLine that has been commented out should allow to change the action, but failed");
		}

		assertEquals("2222222", steps.get(1).getCommit().name());
		assertEquals("comment", steps.get(1).getAction().toToken());

		assertEquals(null, steps.get(2).getCommit());
		assertEquals(null, steps.get(2).getShortMessage());
		assertEquals("comment", steps.get(2).getAction().toToken());
		assertEquals("# pick invalidLine Comment line at end", steps.get(2)
				.getComment());
		try {
			steps.get(2).setAction(Action.PICK);
			fail("A comment RebaseTodoLine that doesn't contain a valid parsable line should fail, but doesn't");
		} catch (Exception e) {
			// expected
		}

	}

	@SuppressWarnings("unused")
	@Test
	public void testRebaseTodoLineSetComment() throws Exception {
		try {
			new RebaseTodoLine("This is a invalid comment");
			fail("Constructing a comment line with invalid comment string should fail, but doesn't");
		} catch (IllegalArgumentException e) {
			// expected
		}
		RebaseTodoLine validCommentLine = new RebaseTodoLine(
				"# This is a comment");
		assertEquals(Action.COMMENT, validCommentLine.getAction());
		assertEquals("# This is a comment", validCommentLine.getComment());

		RebaseTodoLine actionLineToBeChanged = new RebaseTodoLine(Action.EDIT,
				AbbreviatedObjectId.fromString("1111111"), "short Message");
		assertEquals(null, actionLineToBeChanged.getComment());

		try {
			actionLineToBeChanged.setComment("invalid comment");
			fail("Setting a invalid comment string should fail but doesn't");
		} catch (IllegalArgumentException e) {
			assertEquals(null, actionLineToBeChanged.getComment());
		}

		actionLineToBeChanged.setComment("# valid comment");
		assertEquals("# valid comment", actionLineToBeChanged.getComment());
		try {
			actionLineToBeChanged.setComment("invalid comment");
			fail("Setting a invalid comment string should fail but doesn't");
		} catch (IllegalArgumentException e) {
			// expected
			// setting comment failed, but was successfully set before,
			// therefore it may not be altered since then
			assertEquals("# valid comment", actionLineToBeChanged.getComment());
		}
		try {
			actionLineToBeChanged.setComment("# line1 \n line2");
			actionLineToBeChanged.setComment("line1 \n line2");
			actionLineToBeChanged.setComment("\n");
			actionLineToBeChanged.setComment("# line1 \r line2");
			actionLineToBeChanged.setComment("line1 \r line2");
			actionLineToBeChanged.setComment("\r");
			actionLineToBeChanged.setComment("# line1 \n\r line2");
			actionLineToBeChanged.setComment("line1 \n\r line2");
			actionLineToBeChanged.setComment("\n\r");
			fail("Setting a multiline comment string should fail but doesn't");
		} catch (IllegalArgumentException e) {
			// expected
		}
		// Try setting valid comments
		actionLineToBeChanged.setComment("# valid comment");
		assertEquals("# valid comment", actionLineToBeChanged.getComment());

		actionLineToBeChanged.setComment("# \t \t valid comment");
		assertEquals("# \t \t valid comment",
				actionLineToBeChanged.getComment());

		actionLineToBeChanged.setComment("#       ");
		assertEquals("#       ", actionLineToBeChanged.getComment());

		actionLineToBeChanged.setComment("");
		assertEquals("", actionLineToBeChanged.getComment());

		actionLineToBeChanged.setComment("  ");
		assertEquals("  ", actionLineToBeChanged.getComment());

		actionLineToBeChanged.setComment("\t\t");
		assertEquals("\t\t", actionLineToBeChanged.getComment());

		actionLineToBeChanged.setComment(null);
		assertEquals(null, actionLineToBeChanged.getComment());
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

					@Override
					public void prepareSteps(List<RebaseTodoLine> steps) {
						try {
							steps.get(0).setAction(Action.REWORD);
						} catch (IllegalTodoFileModification e) {
							fail("unexpected exception: " + e);
						}
					}

					@Override
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
					@Override
					public void prepareSteps(List<RebaseTodoLine> steps) {
						try {
							steps.get(0).setAction(Action.EDIT);
						} catch (IllegalTodoFileModification e) {
							fail("unexpected exception: " + e);
						}
					}

					@Override
					public String modifyCommitMessage(String commit) {
						return ""; // not used
					}
				}).call();
		assertEquals(Status.EDIT, res.getStatus());
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

	@Test
	public void testParseSquashFixupSequenceCount() {
		int count = RebaseCommand
				.parseSquashFixupSequenceCount("# This is a combination of 3 commits.\n# newline");
		assertEquals(3, count);
	}

	@Test
	public void testRebaseInteractiveSingleSquashAndModifyMessage() throws Exception {
		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Add file1\nnew line").call();
		assertTrue(new File(db.getWorkTree(), FILE1).exists());

		// create file2 on master
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("Add file2\nnew line").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());

		// update FILE1 on master
		writeTrashFile(FILE1, "blah");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("updated file1 on master\nnew line").call();

		writeTrashFile("file2", "more change");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("update file2 on master\nnew line").call();

		git.rebase().setUpstream("HEAD~3")
				.runInteractively(new InteractiveHandler() {

					@Override
					public void prepareSteps(List<RebaseTodoLine> steps) {
						try {
							steps.get(1).setAction(Action.SQUASH);
						} catch (IllegalTodoFileModification e) {
							fail("unexpected exception: " + e);
						}
					}

					@Override
					public String modifyCommitMessage(String commit) {
						final File messageSquashFile = new File(db
								.getDirectory(), "rebase-merge/message-squash");
						final File messageFixupFile = new File(db
								.getDirectory(), "rebase-merge/message-fixup");

						assertFalse(messageFixupFile.exists());
						assertTrue(messageSquashFile.exists());
						assertEquals(
								"# This is a combination of 2 commits.\n# The first commit's message is:\nAdd file2\nnew line\n# This is the 2nd commit message:\nupdated file1 on master\nnew line",
								commit);

						try {
							byte[] messageSquashBytes = IO
									.readFully(messageSquashFile);
							int end = RawParseUtils.prevLF(messageSquashBytes,
									messageSquashBytes.length);
							String messageSquashContent = RawParseUtils.decode(
									messageSquashBytes, 0, end + 1);
							assertEquals(messageSquashContent, commit);
						} catch (Throwable t) {
							fail(t.getMessage());
						}

						return "changed";
					}
				}).call();

		try (RevWalk walk = new RevWalk(db)) {
			ObjectId headId = db.resolve(Constants.HEAD);
			RevCommit headCommit = walk.parseCommit(headId);
			assertEquals(headCommit.getFullMessage(),
					"update file2 on master\nnew line");

			ObjectId head2Id = db.resolve(Constants.HEAD + "^1");
			RevCommit head1Commit = walk.parseCommit(head2Id);
			assertEquals("changed", head1Commit.getFullMessage());
		}
	}

	@Test
	public void testRebaseInteractiveMultipleSquash() throws Exception {
		// create file0 on master
		writeTrashFile("file0", "file0");
		git.add().addFilepattern("file0").call();
		git.commit().setMessage("Add file0\nnew line").call();
		assertTrue(new File(db.getWorkTree(), "file0").exists());

		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Add file1\nnew line").call();
		assertTrue(new File(db.getWorkTree(), FILE1).exists());

		// create file2 on master
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("Add file2\nnew line").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());

		// update FILE1 on master
		writeTrashFile(FILE1, "blah");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("updated file1 on master\nnew line").call();

		writeTrashFile("file2", "more change");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("update file2 on master\nnew line").call();

		git.rebase().setUpstream("HEAD~4")
				.runInteractively(new InteractiveHandler() {

					@Override
					public void prepareSteps(List<RebaseTodoLine> steps) {
						try {
							steps.get(1).setAction(Action.SQUASH);
							steps.get(2).setAction(Action.SQUASH);
						} catch (IllegalTodoFileModification e) {
							fail("unexpected exception: " + e);
						}
					}

					@Override
					public String modifyCommitMessage(String commit) {
						final File messageSquashFile = new File(db.getDirectory(),
								"rebase-merge/message-squash");
						final File messageFixupFile = new File(db.getDirectory(),
								"rebase-merge/message-fixup");
						assertFalse(messageFixupFile.exists());
						assertTrue(messageSquashFile.exists());
						assertEquals(
								"# This is a combination of 3 commits.\n# The first commit's message is:\nAdd file1\nnew line\n# This is the 2nd commit message:\nAdd file2\nnew line\n# This is the 3rd commit message:\nupdated file1 on master\nnew line",
								commit);

						try {
							byte[] messageSquashBytes = IO
									.readFully(messageSquashFile);
							int end = RawParseUtils.prevLF(messageSquashBytes,
									messageSquashBytes.length);
							String messageSquashContend = RawParseUtils.decode(
									messageSquashBytes, 0, end + 1);
							assertEquals(messageSquashContend, commit);
						} catch (Throwable t) {
							fail(t.getMessage());
						}

						return "# This is a combination of 3 commits.\n# The first commit's message is:\nAdd file1\nnew line\n# This is the 2nd commit message:\nAdd file2\nnew line\n# This is the 3rd commit message:\nupdated file1 on master\nnew line";
					}
				}).call();

		try (RevWalk walk = new RevWalk(db)) {
			ObjectId headId = db.resolve(Constants.HEAD);
			RevCommit headCommit = walk.parseCommit(headId);
			assertEquals(headCommit.getFullMessage(),
					"update file2 on master\nnew line");

			ObjectId head2Id = db.resolve(Constants.HEAD + "^1");
			RevCommit head1Commit = walk.parseCommit(head2Id);
			assertEquals(
					"Add file1\nnew line\nAdd file2\nnew line\nupdated file1 on master\nnew line",
					head1Commit.getFullMessage());
		}
	}

	@Test
	public void testRebaseInteractiveMixedSquashAndFixup() throws Exception {
		// create file0 on master
		writeTrashFile("file0", "file0");
		git.add().addFilepattern("file0").call();
		git.commit().setMessage("Add file0\nnew line").call();
		assertTrue(new File(db.getWorkTree(), "file0").exists());

		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Add file1\nnew line").call();
		assertTrue(new File(db.getWorkTree(), FILE1).exists());

		// create file2 on master
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("Add file2\nnew line").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());

		// update FILE1 on master
		writeTrashFile(FILE1, "blah");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("updated file1 on master\nnew line").call();

		writeTrashFile("file2", "more change");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("update file2 on master\nnew line").call();

		git.rebase().setUpstream("HEAD~4")
				.runInteractively(new InteractiveHandler() {

					@Override
					public void prepareSteps(List<RebaseTodoLine> steps) {
						try {
							steps.get(1).setAction(Action.FIXUP);
							steps.get(2).setAction(Action.SQUASH);
						} catch (IllegalTodoFileModification e) {
							fail("unexpected exception: " + e);
						}
					}

					@Override
					public String modifyCommitMessage(String commit) {
						final File messageSquashFile = new File(db
								.getDirectory(), "rebase-merge/message-squash");
						final File messageFixupFile = new File(db
								.getDirectory(), "rebase-merge/message-fixup");

						assertFalse(messageFixupFile.exists());
						assertTrue(messageSquashFile.exists());
						assertEquals(
								"# This is a combination of 3 commits.\n# The first commit's message is:\nAdd file1\nnew line\n# The 2nd commit message will be skipped:\n# Add file2\n# new line\n# This is the 3rd commit message:\nupdated file1 on master\nnew line",
								commit);

						try {
							byte[] messageSquashBytes = IO
									.readFully(messageSquashFile);
							int end = RawParseUtils.prevLF(messageSquashBytes,
									messageSquashBytes.length);
							String messageSquashContend = RawParseUtils.decode(
									messageSquashBytes, 0, end + 1);
							assertEquals(messageSquashContend, commit);
						} catch (Throwable t) {
							fail(t.getMessage());
						}

						return "changed";
					}
				}).call();

		try (RevWalk walk = new RevWalk(db)) {
			ObjectId headId = db.resolve(Constants.HEAD);
			RevCommit headCommit = walk.parseCommit(headId);
			assertEquals(headCommit.getFullMessage(),
					"update file2 on master\nnew line");

			ObjectId head2Id = db.resolve(Constants.HEAD + "^1");
			RevCommit head1Commit = walk.parseCommit(head2Id);
			assertEquals("changed", head1Commit.getFullMessage());
		}
	}

	@Test
	public void testRebaseInteractiveSingleFixup() throws Exception {
		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Add file1\nnew line").call();
		assertTrue(new File(db.getWorkTree(), FILE1).exists());

		// create file2 on master
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("Add file2\nnew line").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());

		// update FILE1 on master
		writeTrashFile(FILE1, "blah");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("updated file1 on master\nnew line").call();

		writeTrashFile("file2", "more change");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("update file2 on master\nnew line").call();

		git.rebase().setUpstream("HEAD~3")
				.runInteractively(new InteractiveHandler() {

					@Override
					public void prepareSteps(List<RebaseTodoLine> steps) {
						try {
							steps.get(1).setAction(Action.FIXUP);
						} catch (IllegalTodoFileModification e) {
							fail("unexpected exception: " + e);
						}
					}

					@Override
					public String modifyCommitMessage(String commit) {
						fail("No callback to modify commit message expected for single fixup");
						return commit;
					}
				}).call();

		try (RevWalk walk = new RevWalk(db)) {
			ObjectId headId = db.resolve(Constants.HEAD);
			RevCommit headCommit = walk.parseCommit(headId);
			assertEquals("update file2 on master\nnew line",
					headCommit.getFullMessage());

			ObjectId head1Id = db.resolve(Constants.HEAD + "^1");
			RevCommit head1Commit = walk.parseCommit(head1Id);
			assertEquals("Add file2\nnew line",
					head1Commit.getFullMessage());
		}
	}

	@Test
	public void testRebaseInteractiveFixupWithBlankLines() throws Exception {
		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Add file1\nnew line").call();
		assertTrue(new File(db.getWorkTree(), FILE1).exists());

		// create file2 on master
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("Add file2").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());

		// update FILE1 on master
		writeTrashFile(FILE1, "blah");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("updated file1 on master\n\nsome text").call();

		git.rebase().setUpstream("HEAD~2")
				.runInteractively(new InteractiveHandler() {

					@Override
					public void prepareSteps(List<RebaseTodoLine> steps) {
						try {
							steps.get(1).setAction(Action.FIXUP);
						} catch (IllegalTodoFileModification e) {
							fail("unexpected exception: " + e);
						}
					}

					@Override
					public String modifyCommitMessage(String commit) {
						fail("No callback to modify commit message expected for single fixup");
						return commit;
					}
				}).call();

		try (RevWalk walk = new RevWalk(db)) {
			ObjectId headId = db.resolve(Constants.HEAD);
			RevCommit headCommit = walk.parseCommit(headId);
			assertEquals("Add file2",
					headCommit.getFullMessage());
		}
	}

	@Test(expected = InvalidRebaseStepException.class)
	public void testRebaseInteractiveFixupFirstCommitShouldFail()
			throws Exception {
		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Add file1\nnew line").call();
		assertTrue(new File(db.getWorkTree(), FILE1).exists());

		// create file2 on master
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("Add file2\nnew line").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());

		git.rebase().setUpstream("HEAD~1")
				.runInteractively(new InteractiveHandler() {

					@Override
					public void prepareSteps(List<RebaseTodoLine> steps) {
						try {
							steps.get(0).setAction(Action.FIXUP);
						} catch (IllegalTodoFileModification e) {
							fail("unexpected exception: " + e);
						}
					}

					@Override
					public String modifyCommitMessage(String commit) {
						return commit;
					}
				}).call();
	}

	@Test(expected = InvalidRebaseStepException.class)
	public void testRebaseInteractiveSquashFirstCommitShouldFail()
			throws Exception {
		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Add file1\nnew line").call();
		assertTrue(new File(db.getWorkTree(), FILE1).exists());

		// create file2 on master
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("Add file2\nnew line").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());

		git.rebase().setUpstream("HEAD~1")
				.runInteractively(new InteractiveHandler() {

					@Override
					public void prepareSteps(List<RebaseTodoLine> steps) {
						try {
							steps.get(0).setAction(Action.SQUASH);
						} catch (IllegalTodoFileModification e) {
							fail("unexpected exception: " + e);
						}
					}

					@Override
					public String modifyCommitMessage(String commit) {
						return commit;
					}
				}).call();
	}

	@Test
	public void testRebaseEndsIfLastStepIsEdit() throws Exception {
		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Add file1\nnew line").call();
		assertTrue(new File(db.getWorkTree(), FILE1).exists());

		// create file2 on master
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("Add file2\nnew line").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());

		git.rebase().setUpstream("HEAD~1")
				.runInteractively(new InteractiveHandler() {

					@Override
					public void prepareSteps(List<RebaseTodoLine> steps) {
						try {
							steps.get(0).setAction(Action.EDIT);
						} catch (IllegalTodoFileModification e) {
							fail("unexpected exception: " + e);
						}
					}

					@Override
					public String modifyCommitMessage(String commit) {
						return commit;
					}
				}).call();
		git.commit().setAmend(true)
				.setMessage("Add file2\nnew line\nanother line").call();
		RebaseResult result = git.rebase().setOperation(Operation.CONTINUE)
				.call();
		assertEquals(Status.OK, result.getStatus());

	}

	@Test
	public void testRebaseShouldStopForEditInCaseOfConflict()
			throws Exception {
		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Add file1\nnew line").call();
		assertTrue(new File(db.getWorkTree(), FILE1).exists());

		//change file1
		writeTrashFile(FILE1, FILE1 + "a");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Change file1").call();

		//change file1
		writeTrashFile(FILE1, FILE1 + "b");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Change file1").call();

		RebaseResult result = git.rebase().setUpstream("HEAD~2")
				.runInteractively(new InteractiveHandler() {

					@Override
					public void prepareSteps(List<RebaseTodoLine> steps) {
						steps.remove(0);
						try {
							steps.get(0).setAction(Action.EDIT);
						} catch (IllegalTodoFileModification e) {
							fail("unexpected exception: " + e);
						}
					}

					@Override
					public String modifyCommitMessage(String commit) {
						return commit;
					}
				}).call();
		assertEquals(Status.STOPPED, result.getStatus());
		git.add().addFilepattern(FILE1).call();
		result = git.rebase().setOperation(Operation.CONTINUE).call();
		assertEquals(Status.EDIT, result.getStatus());

	}

	@Test
	public void testRebaseShouldStopForRewordInCaseOfConflict()
			throws Exception {
		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Add file1\nnew line").call();
		assertTrue(new File(db.getWorkTree(), FILE1).exists());

		// change file1
		writeTrashFile(FILE1, FILE1 + "a");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Change file1").call();

		// change file1
		writeTrashFile(FILE1, FILE1 + "b");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Change file1").call();

		RebaseResult result = git.rebase().setUpstream("HEAD~2")
				.runInteractively(new InteractiveHandler() {

					@Override
					public void prepareSteps(List<RebaseTodoLine> steps) {
						steps.remove(0);
						try {
							steps.get(0).setAction(Action.REWORD);
						} catch (IllegalTodoFileModification e) {
							fail("unexpected exception: " + e);
						}
					}

					@Override
					public String modifyCommitMessage(String commit) {
						return "rewritten commit message";
					}
				}).call();
		assertEquals(Status.STOPPED, result.getStatus());
		git.add().addFilepattern(FILE1).call();
		result = git.rebase().runInteractively(new InteractiveHandler() {

			@Override
			public void prepareSteps(List<RebaseTodoLine> steps) {
				steps.remove(0);
				try {
					steps.get(0).setAction(Action.REWORD);
				} catch (IllegalTodoFileModification e) {
					fail("unexpected exception: " + e);
				}
			}

			@Override
			public String modifyCommitMessage(String commit) {
				return "rewritten commit message";
			}
		}).setOperation(Operation.CONTINUE).call();
		assertEquals(Status.OK, result.getStatus());
		Iterator<RevCommit> logIterator = git.log().all().call().iterator();
		String actualCommitMag = logIterator.next().getShortMessage();
		assertEquals("rewritten commit message", actualCommitMag);

	}

	@Test
	public void testRebaseShouldSquashInCaseOfConflict() throws Exception {
		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Add file1\nnew line").call();
		assertTrue(new File(db.getWorkTree(), FILE1).exists());

		// change file2
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("Change file2").call();

		// change file1
		writeTrashFile(FILE1, FILE1 + "a");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Change file1").call();

		// change file1
		writeTrashFile(FILE1, FILE1 + "b");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Change file1").call();

		RebaseResult result = git.rebase().setUpstream("HEAD~3")
				.runInteractively(new InteractiveHandler() {

					@Override
					public void prepareSteps(List<RebaseTodoLine> steps) {
						try {
							steps.get(0).setAction(Action.PICK);
							steps.remove(1);
							steps.get(1).setAction(Action.SQUASH);
						} catch (IllegalTodoFileModification e) {
							fail("unexpected exception: " + e);
						}
					}

					@Override
					public String modifyCommitMessage(String commit) {
						return "squashed message";
					}
				}).call();
		assertEquals(Status.STOPPED, result.getStatus());
		git.add().addFilepattern(FILE1).call();
		result = git.rebase().runInteractively(new InteractiveHandler() {

			@Override
			public void prepareSteps(List<RebaseTodoLine> steps) {
				try {
					steps.get(0).setAction(Action.PICK);
					steps.remove(1);
					steps.get(1).setAction(Action.SQUASH);
				} catch (IllegalTodoFileModification e) {
					fail("unexpected exception: " + e);
				}
			}

			@Override
			public String modifyCommitMessage(String commit) {
				return "squashed message";
			}
		}).setOperation(Operation.CONTINUE).call();
		assertEquals(Status.OK, result.getStatus());
		Iterator<RevCommit> logIterator = git.log().all().call().iterator();
		String actualCommitMag = logIterator.next().getShortMessage();
		assertEquals("squashed message", actualCommitMag);
	}

	@Test
	public void testRebaseShouldFixupInCaseOfConflict() throws Exception {
		// create file1 on master
		writeTrashFile(FILE1, FILE1);
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Add file1").call();
		assertTrue(new File(db.getWorkTree(), FILE1).exists());

		// change file2
		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("Change file2").call();

		// change file1
		writeTrashFile(FILE1, FILE1 + "a");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("Change file1").call();

		// change file1, add file3
		writeTrashFile(FILE1, FILE1 + "b");
		writeTrashFile("file3", "file3");
		git.add().addFilepattern(FILE1).call();
		git.add().addFilepattern("file3").call();
		git.commit().setMessage("Change file1, add file3").call();

		RebaseResult result = git.rebase().setUpstream("HEAD~3")
				.runInteractively(new InteractiveHandler() {

					@Override
					public void prepareSteps(List<RebaseTodoLine> steps) {
						try {
							steps.get(0).setAction(Action.PICK);
							steps.remove(1);
							steps.get(1).setAction(Action.FIXUP);
						} catch (IllegalTodoFileModification e) {
							fail("unexpected exception: " + e);
						}
					}

					@Override
					public String modifyCommitMessage(String commit) {
						return commit;
					}
				}).call();
		assertEquals(Status.STOPPED, result.getStatus());
		git.add().addFilepattern(FILE1).call();
		result = git.rebase().runInteractively(new InteractiveHandler() {

			@Override
			public void prepareSteps(List<RebaseTodoLine> steps) {
				try {
					steps.get(0).setAction(Action.PICK);
					steps.remove(1);
					steps.get(1).setAction(Action.FIXUP);
				} catch (IllegalTodoFileModification e) {
					fail("unexpected exception: " + e);
				}
			}

			@Override
			public String modifyCommitMessage(String commit) {
				return "commit";
			}
		}).setOperation(Operation.CONTINUE).call();
		assertEquals(Status.OK, result.getStatus());
		Iterator<RevCommit> logIterator = git.log().all().call().iterator();
		String actualCommitMsg = logIterator.next().getShortMessage();
		assertEquals("Change file2", actualCommitMsg);
		actualCommitMsg = logIterator.next().getShortMessage();
		assertEquals("Add file1", actualCommitMsg);
		assertTrue(new File(db.getWorkTree(), "file3").exists());

	}

	@Test
	public void testInteractiveRebaseWithModificationShouldNotDeleteDataOnAbort()
			throws Exception {
		// create file0 + file1, add and commit
		writeTrashFile("file0", "file0");
		writeTrashFile(FILE1, "file1");
		git.add().addFilepattern("file0").addFilepattern(FILE1).call();
		git.commit().setMessage("commit1").call();

		// modify file1, add and commit
		writeTrashFile(FILE1, "modified file1");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("commit2").call();

		// modify file1, add and commit
		writeTrashFile(FILE1, "modified file1 a second time");
		git.add().addFilepattern(FILE1).call();
		git.commit().setMessage("commit3").call();

		// modify file0, but do not commit
		writeTrashFile("file0", "modified file0 in index");
		git.add().addFilepattern("file0").addFilepattern(FILE1).call();
		// do not commit
		writeTrashFile("file0", "modified file0");

		// start rebase
		RebaseResult result = git.rebase().setUpstream("HEAD~2")
				.runInteractively(new InteractiveHandler() {

					@Override
					public void prepareSteps(List<RebaseTodoLine> steps) {
						try {
							steps.get(0).setAction(Action.EDIT);
							steps.get(1).setAction(Action.PICK);
						} catch (IllegalTodoFileModification e) {
							fail("unexpected exception: " + e);
						}
					}

					@Override
					public String modifyCommitMessage(String commit) {
						return commit;
					}
				}).call();
		// the following condition was true before commit 83b6ab233:
		// jgit started the rebase and deleted the change on abort
		// This test should verify that content was deleted
		if (result.getStatus() == Status.EDIT)
			git.rebase().setOperation(Operation.ABORT).call();

		checkFile(new File(db.getWorkTree(), "file0"), "modified file0");
		checkFile(new File(db.getWorkTree(), "file1"),
				"modified file1 a second time");
		assertEquals("[file0, mode:100644, content:modified file0 in index]"
				+ "[file1, mode:100644, content:modified file1 a second time]",
				indexState(CONTENT));

	}

	private File getTodoFile() {
		File todoFile = new File(db.getDirectory(), GIT_REBASE_TODO);
		return todoFile;
	}
}
