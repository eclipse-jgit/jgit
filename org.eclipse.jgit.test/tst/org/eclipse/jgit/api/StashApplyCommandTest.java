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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.text.MessageFormat;

import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests of {@link StashApplyCommand}
 */
public class StashApplyCommandTest extends RepositoryTestCase {

	private static final String PATH = "file.txt";

	private RevCommit head;

	private Git git;

	private File committedFile;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = Git.wrap(db);
		committedFile = writeTrashFile(PATH, "content");
		git.add().addFilepattern(PATH).call();
		head = git.commit().setMessage("add file").call();
		assertNotNull(head);
	}

	@Test
	public void workingDirectoryDelete() throws Exception {
		deleteTrashFile(PATH);
		assertFalse(committedFile.exists());
		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);
		assertFalse(committedFile.exists());

		Status status = git.status().call();
		assertTrue(status.getAdded().isEmpty());
		assertTrue(status.getChanged().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getModified().isEmpty());
		assertTrue(status.getUntracked().isEmpty());
		assertTrue(status.getRemoved().isEmpty());

		assertEquals(1, status.getMissing().size());
		assertTrue(status.getMissing().contains(PATH));
	}

	@Test
	public void indexAdd() throws Exception {
		String addedPath = "file2.txt";
		File addedFile = writeTrashFile(addedPath, "content2");
		git.add().addFilepattern(addedPath).call();

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertFalse(addedFile.exists());

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);
		assertTrue(addedFile.exists());
		assertEquals("content2", read(addedFile));

		Status status = git.status().call();
		assertTrue(status.getChanged().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getModified().isEmpty());
		assertTrue(status.getRemoved().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertEquals(1, status.getAdded().size());
		assertTrue(status.getAdded().contains(addedPath));
	}

	@Test
	public void indexDelete() throws Exception {
		git.rm().addFilepattern("file.txt").call();

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);
		assertFalse(committedFile.exists());

		Status status = git.status().call();
		assertTrue(status.getAdded().isEmpty());
		assertTrue(status.getChanged().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getModified().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertEquals(1, status.getRemoved().size());
		assertTrue(status.getRemoved().contains(PATH));
	}

	@Test
	public void workingDirectoryModify() throws Exception {
		writeTrashFile("file.txt", "content2");

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);
		assertEquals("content2", read(committedFile));

		Status status = git.status().call();
		assertTrue(status.getAdded().isEmpty());
		assertTrue(status.getChanged().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getRemoved().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertEquals(1, status.getModified().size());
		assertTrue(status.getModified().contains(PATH));
	}

	@Test
	public void workingDirectoryModifyInSubfolder() throws Exception {
		String path = "d1/d2/f.txt";
		File subfolderFile = writeTrashFile(path, "content");
		git.add().addFilepattern(path).call();
		head = git.commit().setMessage("add file").call();

		writeTrashFile(path, "content2");

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(subfolderFile));

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);
		assertEquals("content2", read(subfolderFile));

		Status status = git.status().call();
		assertTrue(status.getAdded().isEmpty());
		assertTrue(status.getChanged().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getRemoved().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertEquals(1, status.getModified().size());
		assertTrue(status.getModified().contains(path));
	}

	@Test
	public void workingDirectoryModifyIndexChanged() throws Exception {
		writeTrashFile("file.txt", "content2");
		git.add().addFilepattern("file.txt").call();
		writeTrashFile("file.txt", "content3");

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);
		assertEquals("content3", read(committedFile));

		Status status = git.status().call();
		assertTrue(status.getAdded().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getRemoved().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertEquals(1, status.getChanged().size());
		assertTrue(status.getChanged().contains(PATH));
		assertEquals(1, status.getModified().size());
		assertTrue(status.getModified().contains(PATH));
	}

	@Test
	public void workingDirectoryCleanIndexModify() throws Exception {
		writeTrashFile("file.txt", "content2");
		git.add().addFilepattern("file.txt").call();
		writeTrashFile("file.txt", "content");

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);
		assertEquals("content2", read(committedFile));

		Status status = git.status().call();
		assertTrue(status.getAdded().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getModified().isEmpty());
		assertTrue(status.getRemoved().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertEquals(1, status.getChanged().size());
		assertTrue(status.getChanged().contains(PATH));
	}

	@Test
	public void workingDirectoryDeleteIndexAdd() throws Exception {
		String path = "file2.txt";
		File added = writeTrashFile(path, "content2");
		assertTrue(added.exists());
		git.add().addFilepattern(path).call();
		FileUtils.delete(added);
		assertFalse(added.exists());

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertFalse(added.exists());

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);
		assertEquals("content2", read(added));

		Status status = git.status().call();
		assertTrue(status.getChanged().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getModified().isEmpty());
		assertTrue(status.getRemoved().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertEquals(1, status.getAdded().size());
		assertTrue(status.getAdded().contains(path));
	}

	@Test
	public void workingDirectoryDeleteIndexEdit() throws Exception {
		writeTrashFile(PATH, "content2");
		git.add().addFilepattern(PATH).call();
		FileUtils.delete(committedFile);
		assertFalse(committedFile.exists());

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);
		assertFalse(committedFile.exists());

		Status status = git.status().call();
		assertTrue(status.getAdded().isEmpty());
		assertTrue(status.getChanged().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getModified().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertEquals(1, status.getRemoved().size());
		assertTrue(status.getRemoved().contains(PATH));
	}

	@Test
	public void multipleEdits() throws Exception {
		String addedPath = "file2.txt";
		git.rm().addFilepattern(PATH).call();
		File addedFile = writeTrashFile(addedPath, "content2");
		git.add().addFilepattern(addedPath).call();

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertTrue(committedFile.exists());
		assertFalse(addedFile.exists());

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);

		Status status = git.status().call();
		assertTrue(status.getChanged().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getModified().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertEquals(1, status.getRemoved().size());
		assertTrue(status.getRemoved().contains(PATH));
		assertEquals(1, status.getAdded().size());
		assertTrue(status.getAdded().contains(addedPath));
	}

	@Test
	public void workingDirectoryContentConflict() throws Exception {
		writeTrashFile(PATH, "content2");

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));
		assertTrue(git.status().call().isClean());

		writeTrashFile(PATH, "content3");

		try {
			git.stashApply().call();
			fail("Exception not thrown");
		} catch (CheckoutConflictException e) {
			assertEquals("[" + new File(trash, PATH) + "]", e
					.getConflictingPaths().toString());
		}
	}

	@Test
	public void indexContentConflict() throws Exception {
		writeTrashFile(PATH, "content2");

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));
		assertTrue(git.status().call().isClean());

		writeTrashFile(PATH, "content3");
		git.add().addFilepattern(PATH).call();
		writeTrashFile(PATH, "content2");

		try {
			git.stashApply().call();
			fail("Exception not thrown");
		} catch (CheckoutConflictException e) {
			assertEquals("[" + new File(trash, PATH) + "]", e
					.getConflictingPaths().toString());
		}
	}

	@Test
	public void workingDirectoryEditPreCommit() throws Exception {
		writeTrashFile(PATH, "content2");

		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertEquals("content", read(committedFile));
		assertTrue(git.status().call().isClean());

		String path2 = "file2.txt";
		writeTrashFile(path2, "content3");
		git.add().addFilepattern(path2).call();
		assertNotNull(git.commit().setMessage("adding file").call());

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);

		Status status = git.status().call();
		assertTrue(status.getAdded().isEmpty());
		assertTrue(status.getChanged().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getRemoved().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertEquals(1, status.getModified().size());
		assertTrue(status.getModified().contains(PATH));
	}

	@Test
	public void stashChangeInANewSubdirectory() throws Exception {
		String subdir = "subdir";
		String fname = "file2.txt";
		String path = subdir + "/" + fname;
		String otherBranch = "otherbranch";

		writeTrashFile(subdir, fname, "content2");

		git.add().addFilepattern(path).call();
		RevCommit stashed = git.stashCreate().call();
		assertNotNull(stashed);
		assertTrue(git.status().call().isClean());

		git.branchCreate().setName(otherBranch).call();
		git.checkout().setName(otherBranch).call();

		ObjectId unstashed = git.stashApply().call();
		assertEquals(stashed, unstashed);

		Status status = git.status().call();
		assertTrue(status.getChanged().isEmpty());
		assertTrue(status.getConflicting().isEmpty());
		assertTrue(status.getMissing().isEmpty());
		assertTrue(status.getRemoved().isEmpty());
		assertTrue(status.getModified().isEmpty());
		assertTrue(status.getUntracked().isEmpty());

		assertEquals(1, status.getAdded().size());
		assertTrue(status.getAdded().contains(path));
	}

	@Test
	public void unstashNonStashCommit() throws Exception {
		try {
			git.stashApply().setStashRef(head.name()).call();
			fail("Exception not thrown");
		} catch (JGitInternalException e) {
			assertEquals(MessageFormat.format(
					JGitText.get().stashCommitMissingTwoParents, head.name()),
					e.getMessage());
		}
	}

	@Test
	public void unstashNoHead() throws Exception {
		Repository repo = createWorkRepository();
		try {
			Git.wrap(repo).stashApply().call();
			fail("Exception not thrown");
		} catch (NoHeadException e) {
			assertNotNull(e.getMessage());
		}
	}

	@Test
	public void noStashedCommits() throws Exception {
		try {
			git.stashApply().call();
			fail("Exception not thrown");
		} catch (InvalidRefNameException e) {
			assertNotNull(e.getMessage());
		}
	}
}
