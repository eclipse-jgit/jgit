/*
 * Copyright (C) 2011, Christian Halstrick <christian.halstrick@sap.com>
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
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Before;
import org.junit.Test;

public class GitConstructionTest extends RepositoryTestCase {
	private Repository bareRepo;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();
			writeTrashFile("Test.txt", "Hello world");
			git.add().addFilepattern("Test.txt").call();
			git.commit().setMessage("Initial commit").call();
		}

		bareRepo = Git.cloneRepository().setBare(true)
				.setURI(db.getDirectory().toURI().toString())
				.setDirectory(createUniqueTestGitDir(true)).call()
				.getRepository();
		addRepoToClose(bareRepo);
	}

	@Test
	public void testWrap() throws JGitInternalException, GitAPIException {
		Git git = Git.wrap(db);
		assertEquals(1, git.branchList().call().size());

		git = Git.wrap(bareRepo);
		assertEquals(1, git.branchList().setListMode(ListMode.ALL).call()
				.size());

		try {
			Git.wrap(null);
			fail("Expected exception has not been thrown");
		} catch (NullPointerException e) {
			// should not get here
		}
	}

	@Test
	public void testOpen() throws IOException, JGitInternalException,
			GitAPIException {
		Git git = Git.open(db.getDirectory());
		assertEquals(1, git.branchList().call().size());

		git = Git.open(bareRepo.getDirectory());
		assertEquals(1, git.branchList().setListMode(ListMode.ALL).call()
				.size());

		git = Git.open(db.getWorkTree());
		assertEquals(1, git.branchList().setListMode(ListMode.ALL).call()
				.size());

		try {
			Git.open(db.getObjectsDirectory());
			fail("Expected exception has not been thrown");
		} catch (RepositoryNotFoundException e) {
			// should not get here
		}
	}

	@Test
	/**
	 * Tests that a repository with packfiles can be deleted after calling
	 * Git.close(). On Windows the first try to delete the worktree will fail
	 * (because file handles on packfiles are still open) but the second
	 * attempt after a close will succeed.
	 *
	 * @throws IOException
	 * @throws JGitInternalException
	 * @throws GitAPIException
	 */
	public void testClose() throws IOException, JGitInternalException,
			GitAPIException {
		File workTree = db.getWorkTree();
		Git git = Git.open(workTree);
		git.gc().setExpire(null).call();
		git.checkout().setName(git.getRepository().resolve("HEAD^").getName())
				.call();
		try {
			FileUtils.delete(workTree, FileUtils.RECURSIVE);
		} catch (IOException e) {
			git.close();
			FileUtils.delete(workTree, FileUtils.RECURSIVE);
		}
	}
}
