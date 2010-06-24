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
import java.io.IOException;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;

/**
 * Tests for setting up the working directory when creating a Repository
 */
public class RepositorySetupWorkDirTest extends LocalDiskRepositoryTestCase {

	public void testIsBare_CreateRepositoryFromArbitraryGitDir()
			throws Exception {
		File gitDir = getFile("workdir");
		assertTrue(new FileRepository(gitDir).isBare());
	}

	public void testNotBare_CreateRepositoryFromDotGitGitDir() throws Exception {
		File gitDir = getFile("workdir", Constants.DOT_GIT);
		Repository repo = new FileRepository(gitDir);
		assertFalse(repo.isBare());
		assertWorkdirPath(repo, "workdir");
		assertGitdirPath(repo, "workdir", Constants.DOT_GIT);
	}

	public void testWorkdirIsParentDir_CreateRepositoryFromDotGitGitDir()
			throws Exception {
		File gitDir = getFile("workdir", Constants.DOT_GIT);
		Repository repo = new FileRepository(gitDir);
		String workdir = repo.getWorkDir().getName();
		assertEquals(workdir, "workdir");
	}

	public void testNotBare_CreateRepositoryFromWorkDirOnly() throws Exception {
		File workdir = getFile("workdir", "repo");
		Repository repo = new FileRepository(null, workdir);
		assertFalse(repo.isBare());
		assertWorkdirPath(repo, "workdir", "repo");
		assertGitdirPath(repo, "workdir", "repo", Constants.DOT_GIT);
	}

	public void testWorkdirIsDotGit_CreateRepositoryFromWorkDirOnly()
			throws Exception {
		File workdir = getFile("workdir", "repo");
		Repository repo = new FileRepository(null, workdir);
		assertGitdirPath(repo, "workdir", "repo", Constants.DOT_GIT);
	}

	public void testNotBare_CreateRepositoryFromGitDirOnlyWithWorktreeConfig()
			throws Exception {
		File gitDir = getFile("workdir", "repoWithConfig");
		File workTree = getFile("workdir", "treeRoot");
		setWorkTree(gitDir, workTree);
		Repository repo = new FileRepository(gitDir, null);
		assertFalse(repo.isBare());
		assertWorkdirPath(repo, "workdir", "treeRoot");
		assertGitdirPath(repo, "workdir", "repoWithConfig");
	}

	public void testBare_CreateRepositoryFromGitDirOnlyWithBareConfigTrue()
			throws Exception {
		File gitDir = getFile("workdir", "repoWithConfig");
		setBare(gitDir, true);
		Repository repo = new FileRepository(gitDir, null);
		assertTrue(repo.isBare());
	}

	public void testWorkdirIsParent_CreateRepositoryFromGitDirOnlyWithBareConfigFalse()
			throws Exception {
		File gitDir = getFile("workdir", "repoWithBareConfigTrue", "child");
		setBare(gitDir, false);
		Repository repo = new FileRepository(gitDir, null);
		assertWorkdirPath(repo, "workdir", "repoWithBareConfigTrue");
	}

	public void testNotBare_CreateRepositoryFromGitDirOnlyWithBareConfigFalse()
			throws Exception {
		File gitDir = getFile("workdir", "repoWithBareConfigFalse", "child");
		setBare(gitDir, false);
		Repository repo = new FileRepository(gitDir, null);
		assertFalse(repo.isBare());
		assertWorkdirPath(repo, "workdir", "repoWithBareConfigFalse");
		assertGitdirPath(repo, "workdir", "repoWithBareConfigFalse", "child");
	}

	public void testNotBare_MakeBareUnbareBySetWorkdir() throws Exception {
		File gitDir = getFile("gitDir");
		Repository repo = new FileRepository(gitDir);
		repo.setWorkDir(getFile("workingDir"));
		assertFalse(repo.isBare());
		assertWorkdirPath(repo, "workingDir");
		assertGitdirPath(repo, "gitDir");
	}

	public void testExceptionThrown_BareRepoGetWorkDir() throws Exception {
		File gitDir = getFile("workdir");
		try {
			new FileRepository(gitDir).getWorkDir();
			fail("Expected IllegalStateException missing");
		} catch (IllegalStateException e) {
			// expected
		}
	}

	public void testExceptionThrown_BareRepoGetIndex() throws Exception {
		File gitDir = getFile("workdir");
		try {
			new FileRepository(gitDir).getIndex();
			fail("Expected IllegalStateException missing");
		} catch (IllegalStateException e) {
			// expected
		}
	}

	public void testExceptionThrown_BareRepoGetIndexFile() throws Exception {
		File gitDir = getFile("workdir");
		try {
			new FileRepository(gitDir).getIndexFile();
			fail("Expected Exception missing");
		} catch (IllegalStateException e) {
			// expected
		}
	}

	private File getFile(String... pathComponents) {
		String rootPath = new File(new File("target"), "trash").getPath();
		for (String pathComponent : pathComponents)
			rootPath = rootPath + File.separatorChar + pathComponent;
		File result = new File(rootPath);
		result.mkdir();
		return result;
	}

	private void setBare(File gitDir, boolean bare) throws IOException {
		Repository repo = new FileRepository(gitDir, null);
		repo.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_BARE, bare);
		repo.getConfig().save();
	}

	private void setWorkTree(File gitDir, File workTree) throws IOException {
		Repository repo = new FileRepository(gitDir, null);
		repo.getConfig()
				.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_WORKTREE,
						workTree.getAbsolutePath());
		repo.getConfig().save();
	}

	private void assertGitdirPath(Repository repo, String... expected)
			throws IOException {
		File exp = getFile(expected).getCanonicalFile();
		File act = repo.getDirectory().getCanonicalFile();
		assertEquals("Wrong Git Directory", exp, act);
	}

	private void assertWorkdirPath(Repository repo, String... expected)
			throws IOException {
		File exp = getFile(expected).getCanonicalFile();
		File act = repo.getWorkDir().getCanonicalFile();
		assertEquals("Wrong working Directory", exp, act);
	}
}
