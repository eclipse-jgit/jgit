/*
 * Copyright (C) 2012, GitHub Inc.
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests of {@link StashCreateCommand}
 */
public class StashCreateCommandTest extends RepositoryTestCase {

	private RevCommit head;

	private Git git;

	private File committedFile;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = Git.wrap(db);
		committedFile = writeTrashFile("file.txt", "content");
		git.add().addFilepattern("file.txt").call();
		head = git.commit().setMessage("add file").call();
		assertNotNull(head);
		writeTrashFile("untracked.txt", "content");
	}

	/**
	 * Core validation to be performed on all stashed commits
	 *
	 * @param commit
	 * @throws IOException
	 */
	private void validateStashedCommit(final RevCommit commit)
			throws IOException {
		assertNotNull(commit);
		Ref stashRef = db.getRef(Constants.R_STASH);
		assertNotNull(stashRef);
		assertEquals(commit, stashRef.getObjectId());
		assertNotNull(commit.getAuthorIdent());
		assertEquals(commit.getAuthorIdent(), commit.getCommitterIdent());
		assertEquals(2, commit.getParentCount());

		// Load parents
		RevWalk walk = new RevWalk(db);
		try {
			for (RevCommit parent : commit.getParents())
				walk.parseBody(parent);
		} finally {
			walk.release();
		}

		assertEquals(1, commit.getParent(1).getParentCount());
		assertEquals(head, commit.getParent(1).getParent(0));
		assertFalse("Head tree matches stashed commit tree", commit.getTree()
				.equals(head.getTree()));
		assertEquals(head, commit.getParent(0));
		assertFalse(commit.getFullMessage().equals(
				commit.getParent(1).getFullMessage()));
	}

	private TreeWalk createTreeWalk() {
		TreeWalk walk = new TreeWalk(db);
		walk.setRecursive(true);
		walk.setFilter(TreeFilter.ANY_DIFF);
		return walk;
	}

	private List<DiffEntry> diffWorkingAgainstHead(final RevCommit commit)
			throws IOException {
		TreeWalk walk = createTreeWalk();
		try {
			walk.addTree(commit.getParent(0).getTree());
			walk.addTree(commit.getTree());
			return DiffEntry.scan(walk);
		} finally {
			walk.release();
		}
	}

	private List<DiffEntry> diffIndexAgainstHead(final RevCommit commit)
			throws IOException {
		TreeWalk walk = createTreeWalk();
		try {
			walk.addTree(commit.getParent(0).getTree());
			walk.addTree(commit.getParent(1).getTree());
			return DiffEntry.scan(walk);
		} finally {
			walk.release();
		}
	}

	private List<DiffEntry> diffIndexAgainstWorking(final RevCommit commit)
			throws IOException {
		TreeWalk walk = createTreeWalk();
		try {
			walk.addTree(commit.getParent(1).getTree());
			walk.addTree(commit.getTree());
			return DiffEntry.scan(walk);
		} finally {
			walk.release();
		}
	}

	@Test
	public void noLocalChanges() throws Exception {
		assertNull(git.stashCreate().call());
	}

	@Test
	public void workingDirectoryDelete() throws Exception {
		deleteTrashFile("file.txt");
		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));
		validateStashedCommit(stashed);

		assertEquals(head.getTree(), stashed.getParent(1).getTree());

		List<DiffEntry> diffs = diffWorkingAgainstHead(stashed);
		assertEquals(1, diffs.size());
		assertEquals(DiffEntry.ChangeType.DELETE, diffs.get(0).getChangeType());
		assertEquals("file.txt", diffs.get(0).getOldPath());
	}

	@Test
	public void indexAdd() throws Exception {
		File addedFile = writeTrashFile("file2.txt", "content2");
		git.add().addFilepattern("file2.txt").call();

		RevCommit stashed = Git.wrap(db).stashCreate().call();
		assertNotNull(stashed);
		assertFalse(addedFile.exists());
		validateStashedCommit(stashed);

		assertEquals(stashed.getTree(), stashed.getParent(1).getTree());

		List<DiffEntry> diffs = diffWorkingAgainstHead(stashed);
		assertEquals(1, diffs.size());
		assertEquals(DiffEntry.ChangeType.ADD, diffs.get(0).getChangeType());
		assertEquals("file2.txt", diffs.get(0).getNewPath());
	}

	@Test
	public void newFileInIndexThenModifiedInWorkTree() throws Exception {
		writeTrashFile("file", "content");
		git.add().addFilepattern("file").call();
		writeTrashFile("file", "content2");
		RevCommit stashedWorkTree = Git.wrap(db).stashCreate().call();
		validateStashedCommit(stashedWorkTree);
		RevWalk walk = new RevWalk(db);
		RevCommit stashedIndex = stashedWorkTree.getParent(1);
		walk.parseBody(stashedIndex);
		walk.parseBody(stashedIndex.getTree());
		walk.parseBody(stashedIndex.getParent(0));
		List<DiffEntry> workTreeStashAgainstWorkTree = diffWorkingAgainstHead(stashedWorkTree);
		assertEquals(1, workTreeStashAgainstWorkTree.size());
		List<DiffEntry> workIndexAgainstWorkTree = diffIndexAgainstHead(stashedWorkTree);
		assertEquals(1, workIndexAgainstWorkTree.size());
		List<DiffEntry> indexStashAgainstWorkTree = diffIndexAgainstWorking(stashedWorkTree);
		assertEquals(1, indexStashAgainstWorkTree.size());
	}

	@Test
	public void indexDelete() throws Exception {
		git.rm().addFilepattern("file.txt").call();

		RevCommit stashed = Git.wrap(db).stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));
		validateStashedCommit(stashed);

		assertEquals(stashed.getTree(), stashed.getParent(1).getTree());

		List<DiffEntry> diffs = diffWorkingAgainstHead(stashed);
		assertEquals(1, diffs.size());
		assertEquals(DiffEntry.ChangeType.DELETE, diffs.get(0).getChangeType());
		assertEquals("file.txt", diffs.get(0).getOldPath());
	}

	@Test
	public void workingDirectoryModify() throws Exception {
		writeTrashFile("file.txt", "content2");

		RevCommit stashed = Git.wrap(db).stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));
		validateStashedCommit(stashed);

		assertEquals(head.getTree(), stashed.getParent(1).getTree());

		List<DiffEntry> diffs = diffWorkingAgainstHead(stashed);
		assertEquals(1, diffs.size());
		assertEquals(DiffEntry.ChangeType.MODIFY, diffs.get(0).getChangeType());
		assertEquals("file.txt", diffs.get(0).getNewPath());
	}

	@Test
	public void workingDirectoryModifyInSubfolder() throws Exception {
		String path = "d1/d2/f.txt";
		File subfolderFile = writeTrashFile(path, "content");
		git.add().addFilepattern(path).call();
		head = git.commit().setMessage("add file").call();

		writeTrashFile(path, "content2");

		RevCommit stashed = Git.wrap(db).stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(subfolderFile));
		validateStashedCommit(stashed);

		assertEquals(head.getTree(), stashed.getParent(1).getTree());

		List<DiffEntry> diffs = diffWorkingAgainstHead(stashed);
		assertEquals(1, diffs.size());
		assertEquals(DiffEntry.ChangeType.MODIFY, diffs.get(0).getChangeType());
		assertEquals(path, diffs.get(0).getNewPath());
	}

	@Test
	public void workingDirectoryModifyIndexChanged() throws Exception {
		writeTrashFile("file.txt", "content2");
		git.add().addFilepattern("file.txt").call();
		writeTrashFile("file.txt", "content3");

		RevCommit stashed = Git.wrap(db).stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));
		validateStashedCommit(stashed);

		assertFalse(stashed.getTree().equals(stashed.getParent(1).getTree()));

		List<DiffEntry> workingDiffs = diffWorkingAgainstHead(stashed);
		assertEquals(1, workingDiffs.size());
		assertEquals(DiffEntry.ChangeType.MODIFY, workingDiffs.get(0)
				.getChangeType());
		assertEquals("file.txt", workingDiffs.get(0).getNewPath());

		List<DiffEntry> indexDiffs = diffIndexAgainstHead(stashed);
		assertEquals(1, indexDiffs.size());
		assertEquals(DiffEntry.ChangeType.MODIFY, indexDiffs.get(0)
				.getChangeType());
		assertEquals("file.txt", indexDiffs.get(0).getNewPath());

		assertEquals(workingDiffs.get(0).getOldId(), indexDiffs.get(0)
				.getOldId());
		assertFalse(workingDiffs.get(0).getNewId()
				.equals(indexDiffs.get(0).getNewId()));
	}

	@Test
	public void workingDirectoryCleanIndexModify() throws Exception {
		writeTrashFile("file.txt", "content2");
		git.add().addFilepattern("file.txt").call();
		writeTrashFile("file.txt", "content");

		RevCommit stashed = Git.wrap(db).stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));
		validateStashedCommit(stashed);

		assertEquals(stashed.getParent(1).getTree(), stashed.getTree());

		List<DiffEntry> workingDiffs = diffWorkingAgainstHead(stashed);
		assertEquals(1, workingDiffs.size());
		assertEquals(DiffEntry.ChangeType.MODIFY, workingDiffs.get(0)
				.getChangeType());
		assertEquals("file.txt", workingDiffs.get(0).getNewPath());

		List<DiffEntry> indexDiffs = diffIndexAgainstHead(stashed);
		assertEquals(1, indexDiffs.size());
		assertEquals(DiffEntry.ChangeType.MODIFY, indexDiffs.get(0)
				.getChangeType());
		assertEquals("file.txt", indexDiffs.get(0).getNewPath());

		assertEquals(workingDiffs.get(0).getOldId(), indexDiffs.get(0)
				.getOldId());
		assertTrue(workingDiffs.get(0).getNewId()
				.equals(indexDiffs.get(0).getNewId()));
	}

	@Test
	public void workingDirectoryDeleteIndexAdd() throws Exception {
		String path = "file2.txt";
		File added = writeTrashFile(path, "content2");
		assertTrue(added.exists());
		git.add().addFilepattern(path).call();
		FileUtils.delete(added);
		assertFalse(added.exists());

		RevCommit stashed = Git.wrap(db).stashCreate().call();
		assertNotNull(stashed);
		assertFalse(added.exists());

		validateStashedCommit(stashed);

		assertEquals(stashed.getParent(1).getTree(), stashed.getTree());

		List<DiffEntry> workingDiffs = diffWorkingAgainstHead(stashed);
		assertEquals(1, workingDiffs.size());
		assertEquals(DiffEntry.ChangeType.ADD, workingDiffs.get(0)
				.getChangeType());
		assertEquals(path, workingDiffs.get(0).getNewPath());

		List<DiffEntry> indexDiffs = diffIndexAgainstHead(stashed);
		assertEquals(1, indexDiffs.size());
		assertEquals(DiffEntry.ChangeType.ADD, indexDiffs.get(0)
				.getChangeType());
		assertEquals(path, indexDiffs.get(0).getNewPath());

		assertEquals(workingDiffs.get(0).getOldId(), indexDiffs.get(0)
				.getOldId());
		assertTrue(workingDiffs.get(0).getNewId()
				.equals(indexDiffs.get(0).getNewId()));
	}

	@Test
	public void workingDirectoryDeleteIndexEdit() throws Exception {
		File edited = writeTrashFile("file.txt", "content2");
		git.add().addFilepattern("file.txt").call();
		FileUtils.delete(edited);
		assertFalse(edited.exists());

		RevCommit stashed = Git.wrap(db).stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));
		validateStashedCommit(stashed);

		assertFalse(stashed.getTree().equals(stashed.getParent(1).getTree()));

		List<DiffEntry> workingDiffs = diffWorkingAgainstHead(stashed);
		assertEquals(1, workingDiffs.size());
		assertEquals(DiffEntry.ChangeType.DELETE, workingDiffs.get(0)
				.getChangeType());
		assertEquals("file.txt", workingDiffs.get(0).getOldPath());

		List<DiffEntry> indexDiffs = diffIndexAgainstHead(stashed);
		assertEquals(1, indexDiffs.size());
		assertEquals(DiffEntry.ChangeType.MODIFY, indexDiffs.get(0)
				.getChangeType());
		assertEquals("file.txt", indexDiffs.get(0).getNewPath());

		assertEquals(workingDiffs.get(0).getOldId(), indexDiffs.get(0)
				.getOldId());
		assertFalse(workingDiffs.get(0).getNewId()
				.equals(indexDiffs.get(0).getNewId()));
	}

	@Test
	public void multipleEdits() throws Exception {
		git.rm().addFilepattern("file.txt").call();
		File addedFile = writeTrashFile("file2.txt", "content2");
		git.add().addFilepattern("file2.txt").call();

		RevCommit stashed = Git.wrap(db).stashCreate().call();
		assertNotNull(stashed);
		assertFalse(addedFile.exists());
		validateStashedCommit(stashed);

		assertEquals(stashed.getTree(), stashed.getParent(1).getTree());

		List<DiffEntry> diffs = diffWorkingAgainstHead(stashed);
		assertEquals(2, diffs.size());
		assertEquals(DiffEntry.ChangeType.DELETE, diffs.get(0).getChangeType());
		assertEquals("file.txt", diffs.get(0).getOldPath());
		assertEquals(DiffEntry.ChangeType.ADD, diffs.get(1).getChangeType());
		assertEquals("file2.txt", diffs.get(1).getNewPath());
	}

	@Test
	public void refLogIncludesCommitMessage() throws Exception {
		PersonIdent who = new PersonIdent("user", "user@email.com");
		deleteTrashFile("file.txt");
		RevCommit stashed = git.stashCreate().setPerson(who).call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));
		validateStashedCommit(stashed);

		ReflogReader reader = git.getRepository().getReflogReader(
				Constants.R_STASH);
		ReflogEntry entry = reader.getLastEntry();
		assertNotNull(entry);
		assertEquals(ObjectId.zeroId(), entry.getOldId());
		assertEquals(stashed, entry.getNewId());
		assertEquals(who, entry.getWho());
		assertEquals(stashed.getFullMessage(), entry.getComment());
	}

	@Test(expected = UnmergedPathsException.class)
	public void unmergedPathsShouldCauseException() throws Exception {
		commitFile("file.txt", "master", "base");
		RevCommit side = commitFile("file.txt", "side", "side");
		commitFile("file.txt", "master", "master");
		git.merge().include(side).call();

		git.stashCreate().call();
	}
}
