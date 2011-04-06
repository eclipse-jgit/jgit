/*
 * Copyright (C) 2010, Stefan Lay <stefan.lay@sap.com>
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Iterator;

import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.errors.InvalidMergeHeadsException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class MergeCommandTest extends RepositoryTestCase {
	@Test
	public void testMergeInItself() throws Exception {
		Git git = new Git(db);
		git.commit().setMessage("initial commit").call();

		MergeResult result = git.merge().include(db.getRef(Constants.HEAD)).call();
		assertEquals(MergeResult.MergeStatus.ALREADY_UP_TO_DATE, result.getMergeStatus());
	}

	@Test
	public void testAlreadyUpToDate() throws Exception {
		Git git = new Git(db);
		RevCommit first = git.commit().setMessage("initial commit").call();
		createBranch(first, "refs/heads/branch1");

		RevCommit second = git.commit().setMessage("second commit").call();
		MergeResult result = git.merge().include(db.getRef("refs/heads/branch1")).call();
		assertEquals(MergeResult.MergeStatus.ALREADY_UP_TO_DATE, result.getMergeStatus());
		assertEquals(second, result.getNewHead());

	}

	@Test
	public void testFastForward() throws Exception {
		Git git = new Git(db);
		RevCommit first = git.commit().setMessage("initial commit").call();
		createBranch(first, "refs/heads/branch1");

		RevCommit second = git.commit().setMessage("second commit").call();

		checkoutBranch("refs/heads/branch1");

		MergeResult result = git.merge().include(db.getRef(Constants.MASTER)).call();

		assertEquals(MergeResult.MergeStatus.FAST_FORWARD, result.getMergeStatus());
		assertEquals(second, result.getNewHead());
	}

	@Test
	public void testFastForwardWithFiles() throws Exception {
		Git git = new Git(db);

		writeTrashFile("file1", "file1");
		git.add().addFilepattern("file1").call();
		RevCommit first = git.commit().setMessage("initial commit").call();

		assertTrue(new File(db.getWorkTree(), "file1").exists());
		createBranch(first, "refs/heads/branch1");

		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		RevCommit second = git.commit().setMessage("second commit").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());

		checkoutBranch("refs/heads/branch1");
		assertFalse(new File(db.getWorkTree(), "file2").exists());

		MergeResult result = git.merge().include(db.getRef(Constants.MASTER)).call();

		assertTrue(new File(db.getWorkTree(), "file1").exists());
		assertTrue(new File(db.getWorkTree(), "file2").exists());
		assertEquals(MergeResult.MergeStatus.FAST_FORWARD, result.getMergeStatus());
		assertEquals(second, result.getNewHead());
	}

	@Test
	public void testMultipleHeads() throws Exception {
		Git git = new Git(db);

		writeTrashFile("file1", "file1");
		git.add().addFilepattern("file1").call();
		RevCommit first = git.commit().setMessage("initial commit").call();
		createBranch(first, "refs/heads/branch1");

		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		RevCommit second = git.commit().setMessage("second commit").call();

		writeTrashFile("file3", "file3");
		git.add().addFilepattern("file3").call();
		git.commit().setMessage("third commit").call();

		checkoutBranch("refs/heads/branch1");
		assertFalse(new File(db.getWorkTree(), "file2").exists());
		assertFalse(new File(db.getWorkTree(), "file3").exists());

		MergeCommand merge = git.merge();
		merge.include(second.getId());
		merge.include(db.getRef(Constants.MASTER));
		try {
			merge.call();
			fail("Expected exception not thrown when merging multiple heads");
		} catch (InvalidMergeHeadsException e) {
			// expected this exception
		}
	}

	@Test
	public void testContentMerge() throws Exception {
		Git git = new Git(db);

		writeTrashFile("a", "1\na\n3\n");
		writeTrashFile("b", "1\nb\n3\n");
		writeTrashFile("c/c/c", "1\nc\n3\n");
		git.add().addFilepattern("a").addFilepattern("b")
				.addFilepattern("c/c/c").call();
		RevCommit initialCommit = git.commit().setMessage("initial").call();

		createBranch(initialCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");

		writeTrashFile("a", "1\na(side)\n3\n");
		writeTrashFile("b", "1\nb(side)\n3\n");
		git.add().addFilepattern("a").addFilepattern("b").call();
		RevCommit secondCommit = git.commit().setMessage("side").call();

		assertEquals("1\nb(side)\n3\n", read(new File(db.getWorkTree(), "b")));
		checkoutBranch("refs/heads/master");
		assertEquals("1\nb\n3\n", read(new File(db.getWorkTree(), "b")));

		writeTrashFile("a", "1\na(main)\n3\n");
		writeTrashFile("c/c/c", "1\nc(main)\n3\n");
		git.add().addFilepattern("a").addFilepattern("c/c/c").call();
		git.commit().setMessage("main").call();

		MergeResult result = git.merge().include(secondCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();
		assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());

		assertEquals(
				"1\n<<<<<<< HEAD\na(main)\n=======\na(side)\n>>>>>>> 86503e7e397465588cc267b65d778538bffccb83\n3\n",
				read(new File(db.getWorkTree(), "a")));
		assertEquals("1\nb(side)\n3\n", read(new File(db.getWorkTree(), "b")));
		assertEquals("1\nc(main)\n3\n",
				read(new File(db.getWorkTree(), "c/c/c")));

		assertEquals(1, result.getConflicts().size());
		assertEquals(3, result.getConflicts().get("a")[0].length);

		assertEquals(RepositoryState.MERGING, db.getRepositoryState());
	}

	@Test
	public void testMergeMessage() throws Exception {
		Git git = new Git(db);

		writeTrashFile("a", "1\na\n3\n");
		git.add().addFilepattern("a").call();
		RevCommit initialCommit = git.commit().setMessage("initial").call();

		createBranch(initialCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");

		writeTrashFile("a", "1\na(side)\n3\n");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("side").call();

		checkoutBranch("refs/heads/master");

		writeTrashFile("a", "1\na(main)\n3\n");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("main").call();

		Ref sideBranch = db.getRef("side");

		git.merge().include(sideBranch)
				.setStrategy(MergeStrategy.RESOLVE).call();

		assertEquals("Merge branch 'side'\n\nConflicts:\n\ta\n",
				db.readMergeCommitMsg());

	}

	@Test
	public void testMergeNonVersionedPaths() throws Exception {
		Git git = new Git(db);

		writeTrashFile("a", "1\na\n3\n");
		writeTrashFile("b", "1\nb\n3\n");
		writeTrashFile("c/c/c", "1\nc\n3\n");
		git.add().addFilepattern("a").addFilepattern("b")
				.addFilepattern("c/c/c").call();
		RevCommit initialCommit = git.commit().setMessage("initial").call();

		createBranch(initialCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");

		writeTrashFile("a", "1\na(side)\n3\n");
		writeTrashFile("b", "1\nb(side)\n3\n");
		git.add().addFilepattern("a").addFilepattern("b").call();
		RevCommit secondCommit = git.commit().setMessage("side").call();

		assertEquals("1\nb(side)\n3\n", read(new File(db.getWorkTree(), "b")));
		checkoutBranch("refs/heads/master");
		assertEquals("1\nb\n3\n", read(new File(db.getWorkTree(), "b")));

		writeTrashFile("a", "1\na(main)\n3\n");
		writeTrashFile("c/c/c", "1\nc(main)\n3\n");
		git.add().addFilepattern("a").addFilepattern("c/c/c").call();
		git.commit().setMessage("main").call();

		writeTrashFile("d", "1\nd\n3\n");
		assertTrue(new File(db.getWorkTree(), "e").mkdir());

		MergeResult result = git.merge().include(secondCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();
		assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());

		assertEquals(
				"1\n<<<<<<< HEAD\na(main)\n=======\na(side)\n>>>>>>> 86503e7e397465588cc267b65d778538bffccb83\n3\n",
				read(new File(db.getWorkTree(), "a")));
		assertEquals("1\nb(side)\n3\n", read(new File(db.getWorkTree(), "b")));
		assertEquals("1\nc(main)\n3\n",
				read(new File(db.getWorkTree(), "c/c/c")));
		assertEquals("1\nd\n3\n", read(new File(db.getWorkTree(), "d")));
		File dir = new File(db.getWorkTree(), "e");
		assertTrue(dir.isDirectory());

		assertEquals(1, result.getConflicts().size());
		assertEquals(3, result.getConflicts().get("a")[0].length);

		assertEquals(RepositoryState.MERGING, db.getRepositoryState());
	}

	@Test
	public void testMultipleCreations() throws Exception {
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
	}

	@Test
	public void testMultipleCreationsSameContent() throws Exception {
		Git git = new Git(db);

		writeTrashFile("a", "1\na\n3\n");
		git.add().addFilepattern("a").call();
		RevCommit initialCommit = git.commit().setMessage("initial").call();

		createBranch(initialCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");

		writeTrashFile("b", "1\nb(1)\n3\n");
		git.add().addFilepattern("b").call();
		RevCommit secondCommit = git.commit().setMessage("side").call();

		checkoutBranch("refs/heads/master");

		writeTrashFile("b", "1\nb(1)\n3\n");
		git.add().addFilepattern("b").call();
		git.commit().setMessage("main").call();

		MergeResult result = git.merge().include(secondCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();
		assertEquals(MergeStatus.MERGED, result.getMergeStatus());
		assertEquals("1\nb(1)\n3\n", read(new File(db.getWorkTree(), "b")));
	}

	@Test
	public void testSuccessfulContentMerge() throws Exception {
		Git git = new Git(db);

		writeTrashFile("a", "1\na\n3\n");
		writeTrashFile("b", "1\nb\n3\n");
		writeTrashFile("c/c/c", "1\nc\n3\n");
		git.add().addFilepattern("a").addFilepattern("b")
				.addFilepattern("c/c/c").call();
		RevCommit initialCommit = git.commit().setMessage("initial").call();

		createBranch(initialCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");

		writeTrashFile("a", "1(side)\na\n3\n");
		writeTrashFile("b", "1\nb(side)\n3\n");
		git.add().addFilepattern("a").addFilepattern("b").call();
		RevCommit secondCommit = git.commit().setMessage("side").call();

		assertEquals("1\nb(side)\n3\n", read(new File(db.getWorkTree(), "b")));
		checkoutBranch("refs/heads/master");
		assertEquals("1\nb\n3\n", read(new File(db.getWorkTree(), "b")));

		writeTrashFile("a", "1\na\n3(main)\n");
		writeTrashFile("c/c/c", "1\nc(main)\n3\n");
		git.add().addFilepattern("a").addFilepattern("c/c/c").call();
		RevCommit thirdCommit = git.commit().setMessage("main").call();

		MergeResult result = git.merge().include(secondCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();
		assertEquals(MergeStatus.MERGED, result.getMergeStatus());

		assertEquals("1(side)\na\n3(main)\n", read(new File(db.getWorkTree(),
				"a")));
		assertEquals("1\nb(side)\n3\n", read(new File(db.getWorkTree(), "b")));
		assertEquals("1\nc(main)\n3\n", read(new File(db.getWorkTree(),
				"c/c/c")));

		assertEquals(null, result.getConflicts());

		assertTrue(2 == result.getMergedCommits().length);
		assertEquals(thirdCommit, result.getMergedCommits()[0]);
		assertEquals(secondCommit, result.getMergedCommits()[1]);

		Iterator<RevCommit> it = git.log().call().iterator();
		RevCommit newHead = it.next();
		assertEquals(newHead, result.getNewHead());
		assertEquals(2, newHead.getParentCount());
		assertEquals(thirdCommit, newHead.getParent(0));
		assertEquals(secondCommit, newHead.getParent(1));
		assertEquals(
				"Merge commit '3fa334456d236a92db020289fe0bf481d91777b4'",
				newHead.getFullMessage());
		// @TODO fix me
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());
		// test index state
	}

	@Test
	public void testSuccessfulContentMergeAndDirtyworkingTree()
			throws Exception {
		Git git = new Git(db);

		writeTrashFile("a", "1\na\n3\n");
		writeTrashFile("b", "1\nb\n3\n");
		writeTrashFile("d", "1\nd\n3\n");
		writeTrashFile("c/c/c", "1\nc\n3\n");
		git.add().addFilepattern("a").addFilepattern("b")
				.addFilepattern("c/c/c").addFilepattern("d").call();
		RevCommit initialCommit = git.commit().setMessage("initial").call();

		createBranch(initialCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");

		writeTrashFile("a", "1(side)\na\n3\n");
		writeTrashFile("b", "1\nb(side)\n3\n");
		git.add().addFilepattern("a").addFilepattern("b").call();
		RevCommit secondCommit = git.commit().setMessage("side").call();

		assertEquals("1\nb(side)\n3\n", read(new File(db.getWorkTree(), "b")));
		checkoutBranch("refs/heads/master");
		assertEquals("1\nb\n3\n", read(new File(db.getWorkTree(), "b")));

		writeTrashFile("a", "1\na\n3(main)\n");
		writeTrashFile("c/c/c", "1\nc(main)\n3\n");
		git.add().addFilepattern("a").addFilepattern("c/c/c").call();
		RevCommit thirdCommit = git.commit().setMessage("main").call();

		writeTrashFile("d", "--- dirty ---");
		MergeResult result = git.merge().include(secondCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();
		assertEquals(MergeStatus.MERGED, result.getMergeStatus());

		assertEquals("1(side)\na\n3(main)\n", read(new File(db.getWorkTree(),
				"a")));
		assertEquals("1\nb(side)\n3\n", read(new File(db.getWorkTree(), "b")));
		assertEquals("1\nc(main)\n3\n", read(new File(db.getWorkTree(),
				"c/c/c")));
		assertEquals("--- dirty ---", read(new File(db.getWorkTree(), "d")));

		assertEquals(null, result.getConflicts());

		assertTrue(2 == result.getMergedCommits().length);
		assertEquals(thirdCommit, result.getMergedCommits()[0]);
		assertEquals(secondCommit, result.getMergedCommits()[1]);

		Iterator<RevCommit> it = git.log().call().iterator();
		RevCommit newHead = it.next();
		assertEquals(newHead, result.getNewHead());
		assertEquals(2, newHead.getParentCount());
		assertEquals(thirdCommit, newHead.getParent(0));
		assertEquals(secondCommit, newHead.getParent(1));
		assertEquals(
				"Merge commit '064d54d98a4cdb0fed1802a21c656bfda67fe879'",
				newHead.getFullMessage());

		assertEquals(RepositoryState.SAFE, db.getRepositoryState());
	}

	@Test
	public void testSingleDeletion() throws Exception {
		Git git = new Git(db);

		writeTrashFile("a", "1\na\n3\n");
		writeTrashFile("b", "1\nb\n3\n");
		writeTrashFile("d", "1\nd\n3\n");
		writeTrashFile("c/c/c", "1\nc\n3\n");
		git.add().addFilepattern("a").addFilepattern("b")
				.addFilepattern("c/c/c").addFilepattern("d").call();
		RevCommit initialCommit = git.commit().setMessage("initial").call();

		createBranch(initialCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");

		assertTrue(new File(db.getWorkTree(), "b").delete());
		git.add().addFilepattern("b").setUpdate(true).call();
		RevCommit secondCommit = git.commit().setMessage("side").call();

		assertFalse(new File(db.getWorkTree(), "b").exists());
		checkoutBranch("refs/heads/master");
		assertTrue(new File(db.getWorkTree(), "b").exists());

		writeTrashFile("a", "1\na\n3(main)\n");
		writeTrashFile("c/c/c", "1\nc(main)\n3\n");
		git.add().addFilepattern("a").addFilepattern("c/c/c").call();
		RevCommit thirdCommit = git.commit().setMessage("main").call();

		// We are merging a deletion into our branch
		MergeResult result = git.merge().include(secondCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();
		assertEquals(MergeStatus.MERGED, result.getMergeStatus());

		assertEquals("1\na\n3(main)\n", read(new File(db.getWorkTree(), "a")));
		assertFalse(new File(db.getWorkTree(), "b").exists());
		assertEquals("1\nc(main)\n3\n",
				read(new File(db.getWorkTree(), "c/c/c")));
		assertEquals("1\nd\n3\n", read(new File(db.getWorkTree(), "d")));

		// Do the opposite, be on a branch where we have deleted a file and
		// merge in a old commit where this file was not deleted
		checkoutBranch("refs/heads/side");
		assertFalse(new File(db.getWorkTree(), "b").exists());

		result = git.merge().include(thirdCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();
		assertEquals(MergeStatus.MERGED, result.getMergeStatus());

		assertEquals("1\na\n3(main)\n", read(new File(db.getWorkTree(), "a")));
		assertFalse(new File(db.getWorkTree(), "b").exists());
		assertEquals("1\nc(main)\n3\n",
				read(new File(db.getWorkTree(), "c/c/c")));
		assertEquals("1\nd\n3\n", read(new File(db.getWorkTree(), "d")));
	}

	@Test
	public void testMultipleDeletions() throws Exception {
		Git git = new Git(db);

		writeTrashFile("a", "1\na\n3\n");
		git.add().addFilepattern("a").call();
		RevCommit initialCommit = git.commit().setMessage("initial").call();

		createBranch(initialCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");

		assertTrue(new File(db.getWorkTree(), "a").delete());
		git.add().addFilepattern("a").setUpdate(true).call();
		RevCommit secondCommit = git.commit().setMessage("side").call();

		assertFalse(new File(db.getWorkTree(), "a").exists());
		checkoutBranch("refs/heads/master");
		assertTrue(new File(db.getWorkTree(), "a").exists());

		assertTrue(new File(db.getWorkTree(), "a").delete());
		git.add().addFilepattern("a").setUpdate(true).call();
		git.commit().setMessage("main").call();

		// We are merging a deletion into our branch
		MergeResult result = git.merge().include(secondCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();
		assertEquals(MergeStatus.MERGED, result.getMergeStatus());
	}

	@Test
	public void testDeletionAndConflict() throws Exception {
		Git git = new Git(db);

		writeTrashFile("a", "1\na\n3\n");
		writeTrashFile("b", "1\nb\n3\n");
		writeTrashFile("d", "1\nd\n3\n");
		writeTrashFile("c/c/c", "1\nc\n3\n");
		git.add().addFilepattern("a").addFilepattern("b")
				.addFilepattern("c/c/c").addFilepattern("d").call();
		RevCommit initialCommit = git.commit().setMessage("initial").call();

		createBranch(initialCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");

		assertTrue(new File(db.getWorkTree(), "b").delete());
		writeTrashFile("a", "1\na\n3(side)\n");
		git.add().addFilepattern("b").setUpdate(true).call();
		git.add().addFilepattern("a").setUpdate(true).call();
		RevCommit secondCommit = git.commit().setMessage("side").call();

		assertFalse(new File(db.getWorkTree(), "b").exists());
		checkoutBranch("refs/heads/master");
		assertTrue(new File(db.getWorkTree(), "b").exists());

		writeTrashFile("a", "1\na\n3(main)\n");
		writeTrashFile("c/c/c", "1\nc(main)\n3\n");
		git.add().addFilepattern("a").addFilepattern("c/c/c").call();
		git.commit().setMessage("main").call();

		// We are merging a deletion into our branch
		MergeResult result = git.merge().include(secondCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();
		assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());

		assertEquals(
				"1\na\n<<<<<<< HEAD\n3(main)\n=======\n3(side)\n>>>>>>> 54ffed45d62d252715fc20e41da92d44c48fb0ff\n",
				read(new File(db.getWorkTree(), "a")));
		assertFalse(new File(db.getWorkTree(), "b").exists());
		assertEquals("1\nc(main)\n3\n",
				read(new File(db.getWorkTree(), "c/c/c")));
		assertEquals("1\nd\n3\n", read(new File(db.getWorkTree(), "d")));
	}

	@Test
	public void testMergeFailingWithDirtyWorkingTree() throws Exception {
		Git git = new Git(db);

		writeTrashFile("a", "1\na\n3\n");
		writeTrashFile("b", "1\nb\n3\n");
		git.add().addFilepattern("a").addFilepattern("b").call();
		RevCommit initialCommit = git.commit().setMessage("initial").call();

		createBranch(initialCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");

		writeTrashFile("a", "1(side)\na\n3\n");
		writeTrashFile("b", "1\nb(side)\n3\n");
		git.add().addFilepattern("a").addFilepattern("b").call();
		RevCommit secondCommit = git.commit().setMessage("side").call();

		assertEquals("1\nb(side)\n3\n", read(new File(db.getWorkTree(), "b")));
		checkoutBranch("refs/heads/master");
		assertEquals("1\nb\n3\n", read(new File(db.getWorkTree(), "b")));

		writeTrashFile("a", "1\na\n3(main)\n");
		git.add().addFilepattern("a").call();
		git.commit().setMessage("main").call();

		writeTrashFile("a", "--- dirty ---");
		MergeResult result = git.merge().include(secondCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();

		assertEquals(MergeStatus.FAILED, result.getMergeStatus());

		assertEquals("--- dirty ---", read(new File(db.getWorkTree(), "a")));
		assertEquals("1\nb\n3\n", read(new File(db.getWorkTree(), "b")));

		assertEquals(null, result.getConflicts());

		assertEquals(RepositoryState.SAFE, db.getRepositoryState());
	}

	@Test
	public void testMergeConflictFileFolder() throws Exception {
		Git git = new Git(db);

		writeTrashFile("a", "1\na\n3\n");
		writeTrashFile("b", "1\nb\n3\n");
		git.add().addFilepattern("a").addFilepattern("b").call();
		RevCommit initialCommit = git.commit().setMessage("initial").call();

		createBranch(initialCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");

		writeTrashFile("c/c/c", "1\nc(side)\n3\n");
		writeTrashFile("d", "1\nd(side)\n3\n");
		git.add().addFilepattern("c/c/c").addFilepattern("d").call();
		RevCommit secondCommit = git.commit().setMessage("side").call();

		checkoutBranch("refs/heads/master");

		writeTrashFile("c", "1\nc(main)\n3\n");
		writeTrashFile("d/d/d", "1\nd(main)\n3\n");
		git.add().addFilepattern("c").addFilepattern("d/d/d").call();
		git.commit().setMessage("main").call();

		MergeResult result = git.merge().include(secondCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();

		assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());

		assertEquals("1\na\n3\n", read(new File(db.getWorkTree(), "a")));
		assertEquals("1\nb\n3\n", read(new File(db.getWorkTree(), "b")));
		assertEquals("1\nc(main)\n3\n", read(new File(db.getWorkTree(), "c")));
		assertEquals("1\nd(main)\n3\n", read(new File(db.getWorkTree(), "d/d/d")));

		assertEquals(null, result.getConflicts());

		assertEquals(RepositoryState.MERGING, db.getRepositoryState());
	}

	@Test
	public void testSuccessfulMergeFailsDueToDirtyIndex() throws Exception {
		Git git = new Git(db);

		File fileA = writeTrashFile("a", "a");
		RevCommit initialCommit = addAllAndCommit(git);

		// switch branch
		createBranch(initialCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");
		// modify file a
		write(fileA, "a(side)");
		writeTrashFile("b", "b");
		RevCommit sideCommit = addAllAndCommit(git);

		// switch branch
		checkoutBranch("refs/heads/master");
		writeTrashFile("c", "c");
		addAllAndCommit(git);

		// modify and add file a
		write(fileA, "a(modified)");
		git.add().addFilepattern("a").call();
		// do not commit

		// get current index state
		String indexState = indexState(CONTENT);

		// merge
		MergeResult result = git.merge().include(sideCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();

		checkMergeFailedResult(result, MergeFailureReason.DIRTY_INDEX,
				indexState, fileA);
	}

	@Test
	public void testConflictingMergeFailsDueToDirtyIndex() throws Exception {
		Git git = new Git(db);

		File fileA = writeTrashFile("a", "a");
		RevCommit initialCommit = addAllAndCommit(git);

		// switch branch
		createBranch(initialCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");
		// modify file a
		write(fileA, "a(side)");
		writeTrashFile("b", "b");
		RevCommit sideCommit = addAllAndCommit(git);

		// switch branch
		checkoutBranch("refs/heads/master");
		// modify file a - this will cause a conflict during merge
		write(fileA, "a(master)");
		writeTrashFile("c", "c");
		addAllAndCommit(git);

		// modify and add file a
		write(fileA, "a(modified)");
		git.add().addFilepattern("a").call();
		// do not commit

		// get current index state
		String indexState = indexState(CONTENT);

		// merge
		MergeResult result = git.merge().include(sideCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();

		checkMergeFailedResult(result, MergeFailureReason.DIRTY_INDEX,
				indexState, fileA);
	}

	@Test
	public void testSuccessfulMergeFailsDueToDirtyWorktree() throws Exception {
		Git git = new Git(db);

		File fileA = writeTrashFile("a", "a");
		RevCommit initialCommit = addAllAndCommit(git);

		// switch branch
		createBranch(initialCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");
		// modify file a
		write(fileA, "a(side)");
		writeTrashFile("b", "b");
		RevCommit sideCommit = addAllAndCommit(git);

		// switch branch
		checkoutBranch("refs/heads/master");
		writeTrashFile("c", "c");
		addAllAndCommit(git);

		// modify file a
		write(fileA, "a(modified)");
		// do not add and commit

		// get current index state
		String indexState = indexState(CONTENT);

		// merge
		MergeResult result = git.merge().include(sideCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();

		checkMergeFailedResult(result, MergeFailureReason.DIRTY_WORKTREE,
				indexState, fileA);
	}

	@Test
	public void testConflictingMergeFailsDueToDirtyWorktree() throws Exception {
		Git git = new Git(db);

		File fileA = writeTrashFile("a", "a");
		RevCommit initialCommit = addAllAndCommit(git);

		// switch branch
		createBranch(initialCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");
		// modify file a
		write(fileA, "a(side)");
		writeTrashFile("b", "b");
		RevCommit sideCommit = addAllAndCommit(git);

		// switch branch
		checkoutBranch("refs/heads/master");
		// modify file a - this will cause a conflict during merge
		write(fileA, "a(master)");
		writeTrashFile("c", "c");
		addAllAndCommit(git);

		// modify file a
		write(fileA, "a(modified)");
		// do not add and commit

		// get current index state
		String indexState = indexState(CONTENT);

		// merge
		MergeResult result = git.merge().include(sideCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();

		checkMergeFailedResult(result, MergeFailureReason.DIRTY_WORKTREE,
				indexState, fileA);
	}

	private RevCommit addAllAndCommit(final Git git) throws Exception {
		git.add().addFilepattern(".").call();
		return git.commit().setMessage("message").call();
	}

	private void checkMergeFailedResult(final MergeResult result,
			final MergeFailureReason reason,
			final String indexState, final File fileA) throws Exception {
		assertEquals(MergeStatus.FAILED, result.getMergeStatus());
		assertEquals(reason, result.getFailingPaths().get("a"));
		assertEquals("a(modified)", read(fileA));
		assertFalse(new File(db.getWorkTree(), "b").exists());
		assertEquals("c", read(new File(db.getWorkTree(), "c")));
		assertEquals(indexState, indexState(CONTENT));
		assertEquals(null, result.getConflicts());
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());
	}
}
