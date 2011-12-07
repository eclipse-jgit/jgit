/*
 * Copyright (C) 2011, GitHub Inc.
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

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
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

	@Test
	public void singleWorkingDirectoryDelete() throws Exception {
		deleteTrashFile("file.txt");
		RevCommit stashed = git.stashCreate().call();
		assertEquals("content", read(committedFile));
		validateStashedCommit(stashed);

		assertEquals(head.getTree(), stashed.getParent(1).getTree());

		List<DiffEntry> diffs = diffWorkingAgainstHead(stashed);
		assertEquals(1, diffs.size());
		assertEquals(DiffEntry.ChangeType.DELETE, diffs.get(0).getChangeType());
		assertEquals("file.txt", diffs.get(0).getOldPath());
	}

	@Test
	public void singleIndexAdd() throws Exception {
		File addedFile = writeTrashFile("file2.txt", "content2");
		git.add().addFilepattern("file2.txt").call();

		RevCommit stashed = Git.wrap(db).stashCreate().call();
		assertFalse(addedFile.exists());
		validateStashedCommit(stashed);

		assertEquals(stashed.getTree(), stashed.getParent(1).getTree());

		List<DiffEntry> diffs = diffWorkingAgainstHead(stashed);
		assertEquals(1, diffs.size());
		assertEquals(DiffEntry.ChangeType.ADD, diffs.get(0).getChangeType());
		assertEquals("file2.txt", diffs.get(0).getNewPath());
	}

	@Test
	public void singleIndexDelete() throws Exception {
		git.rm().addFilepattern("file.txt").call();

		RevCommit stashed = Git.wrap(db).stashCreate().call();
		assertEquals("content", read(committedFile));
		validateStashedCommit(stashed);

		assertEquals(stashed.getTree(), stashed.getParent(1).getTree());

		List<DiffEntry> diffs = diffWorkingAgainstHead(stashed);
		assertEquals(1, diffs.size());
		assertEquals(DiffEntry.ChangeType.DELETE, diffs.get(0).getChangeType());
		assertEquals("file.txt", diffs.get(0).getOldPath());
	}

	@Test
	public void singleWorkingDirectoryModify() throws Exception {
		writeTrashFile("file.txt", "content2");

		RevCommit stashed = Git.wrap(db).stashCreate().call();
		assertEquals("content", read(committedFile));
		validateStashedCommit(stashed);

		assertEquals(head.getTree(), stashed.getParent(1).getTree());

		List<DiffEntry> diffs = diffWorkingAgainstHead(stashed);
		assertEquals(1, diffs.size());
		assertEquals(DiffEntry.ChangeType.MODIFY, diffs.get(0).getChangeType());
		assertEquals("file.txt", diffs.get(0).getNewPath());
	}

	@Test
	public void singleWorkingDirectoryModifyIndexChanged() throws Exception {
		writeTrashFile("file.txt", "content2");
		git.add().addFilepattern("file.txt").call();
		writeTrashFile("file.txt", "content3");

		RevCommit stashed = Git.wrap(db).stashCreate().call();
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
}
