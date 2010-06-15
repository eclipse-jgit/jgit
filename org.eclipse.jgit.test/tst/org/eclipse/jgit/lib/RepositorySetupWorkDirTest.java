/*
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
 *
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

package org.eclipse.jgit.lib;

import java.io.File;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;

/**
 * Tests for setting up the working directory when creating a Repository
 */
public class RepositorySetupWorkDirTest extends LocalDiskRepositoryTestCase {

	public void testIsBare_CreateRepositoryFromArbitraryGitDir()
			throws Exception {
		File gitDir = getFile("workdir");
		assertTrue(new Repository(gitDir).isBare());
	}

	public void testNotBare_CreateRepositoryFromDotGitGitDir() throws Exception {
		File gitDir = getFile("workdir", Constants.DOT_GIT);
		Repository repo = new Repository(gitDir);
		assertFalse(repo.isBare());
		assertWorkdirName(repo, "workdir");
		assertGitdirName(repo, Constants.DOT_GIT);
	}

	private void assertGitdirName(Repository repo, String expected) {
		assertTrue("Wrong Git directory name", repo.getDirectory().getName()
				.equals(expected));
	}

	private void assertWorkdirName(Repository repo, String expected) {
		assertTrue("Wrong working directory name", repo.getWorkDir().getName()
				.equals(expected));
	}

	public void testWorkdirIsParentDir_CreateRepositoryFromDotGitGitDir()
			throws Exception {
		File gitDir = getFile("workdir", Constants.DOT_GIT);
		Repository repo = new Repository(gitDir);
		String workdir = repo.getWorkDir().getName();
		assertEquals(workdir, "workdir");
	}

	public void testNotBare_CreateRepositoryFromWorkDirOnly() throws Exception {
		File workdir = getFile("workdir", "repo");
		Repository repo = new Repository(null, workdir);
		assertFalse(repo.isBare());
		assertWorkdirName(repo, "repo");
		assertGitdirName(repo, Constants.DOT_GIT);

	}

	public void testWorkdirIsDotGit_CreateRepositoryFromWorkDirOnly()
			throws Exception {
		File workdir = getFile("workdir", "repo");
		Repository repo = new Repository(null, workdir);
		assertTrue(repo.getDirectory().getName().equals(Constants.DOT_GIT));
	}

	public void testNotBare_CreateRepositoryFromGitDirOnlyWithWorktreeConfig()
			throws Exception {
		File gitDir = getFile("workdir", "repoWithConfig");
		Repository repo = new Repository(gitDir, null);
		repo.getConfig().setString(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_WORKTREE, "worktreeroot");
		repo.getConfig().save();
		repo = new Repository(gitDir, null);
		assertFalse(repo.isBare());
		assertWorkdirName(repo, "worktreeroot");
		assertGitdirName(repo, "repoWithConfig");
	}

	public void testBare_CreateRepositoryFromGitDirOnlyWithBareConfigTrue()
			throws Exception {
		File gitDir = getFile("workdir", "repoWithConfig");
		Repository repo = new Repository(gitDir, null);
		repo.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_BARE, true);
		repo.getConfig().save();

		repo = new Repository(gitDir, null);
		assertTrue(repo.isBare());
	}

	public void testWorkdirIsParent_CreateRepositoryFromGitDirOnlyWithBareConfigFalse()
			throws Exception {
		File gitDir = getFile("workdir", "repoWithBareConfigTrue", "child");
		Repository repo = new Repository(gitDir, null);
		repo.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_BARE, false);
		repo.getConfig().save();
		repo = new Repository(gitDir, null);
		String workdir = repo.getWorkDir().getName();
		assertEquals("repoWithBareConfigTrue", workdir);
	}

	public void testNotBare_CreateRepositoryFromGitDirOnlyWithBareConfigFalse()
			throws Exception {
		File gitDir = getFile("workdir", "repoWithBareConfigFalse", "child");
		Repository repo = new Repository(gitDir, null);

		repo.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_BARE, false);
		repo.getConfig().save();
		repo = new Repository(gitDir, null);
		assertFalse(repo.isBare());
		assertWorkdirName(repo, "repoWithBareConfigFalse");
		assertGitdirName(repo, "child");
	}

	public void testNotBare_MakeBareUnbareBySetWorkdir() throws Exception {
		File gitDir = getFile("gitDir");
		Repository repo = new Repository(gitDir);
		repo.setWorkDir(getFile("workingDir"));
		assertFalse(repo.isBare());
		assertWorkdirName(repo, "workingDir");
		assertGitdirName(repo, "gitDir");
	}

	public void testExceptionThrown_BareRepoGetWorkDir() throws Exception {
		File gitDir = getFile("workdir");
		try {
			new Repository(gitDir).getWorkDir();
			fail("Expected Exception missing");
		} catch (IllegalStateException e) {
			// expected
		}
	}

	public void testExceptionThrown_BareRepoGetIndex() throws Exception {
		setUp();
		File gitDir = getFile("workdir");
		try {
			new Repository(gitDir).getIndex();
			fail("Expected Exception missing");
		} catch (IllegalStateException e) {
			// expected
		}
		tearDown();
	}

	public void testExceptionThrown_BareRepoGetIndexFile() throws Exception {
		setUp();
		File gitDir = getFile("workdir");
		try {
			new Repository(gitDir).getIndexFile();
			fail("Expected Exception missing");
		} catch (IllegalStateException e) {
			// expected
		}
		tearDown();
	}

	private File getFile(String... pathComponents) {
		String rootPath = new File(new File("target"), "trash").getPath();
		for (String pathComponent : pathComponents)
			rootPath = rootPath + File.separatorChar + pathComponent;
		File result = new File(rootPath);
		result.mkdir();
		return result;
	}

}
