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

package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Test;

/**
 * Tests for setting up the working directory when creating a Repository
 */
public class RepositorySetupWorkDirTest extends LocalDiskRepositoryTestCase {

	@Test
	public void testIsBare_CreateRepositoryFromArbitraryGitDir()
			throws Exception {
		File gitDir = getFile("workdir");
		assertTrue(new FileRepository(gitDir).isBare());
	}

	@Test
	public void testNotBare_CreateRepositoryFromDotGitGitDir() throws Exception {
		File gitDir = getFile("workdir", Constants.DOT_GIT);
		Repository repo = new FileRepository(gitDir);
		assertFalse(repo.isBare());
		assertWorkdirPath(repo, "workdir");
		assertGitdirPath(repo, "workdir", Constants.DOT_GIT);
	}

	@Test
	public void testWorkdirIsParentDir_CreateRepositoryFromDotGitGitDir()
			throws Exception {
		File gitDir = getFile("workdir", Constants.DOT_GIT);
		Repository repo = new FileRepository(gitDir);
		String workdir = repo.getWorkTree().getName();
		assertEquals(workdir, "workdir");
	}

	@Test
	public void testNotBare_CreateRepositoryFromWorkDirOnly() throws Exception {
		File workdir = getFile("workdir", "repo");
		Repository repo = new FileRepositoryBuilder().setWorkTree(workdir)
				.build();
		assertFalse(repo.isBare());
		assertWorkdirPath(repo, "workdir", "repo");
		assertGitdirPath(repo, "workdir", "repo", Constants.DOT_GIT);
	}

	@Test
	public void testWorkdirIsDotGit_CreateRepositoryFromWorkDirOnly()
			throws Exception {
		File workdir = getFile("workdir", "repo");
		Repository repo = new FileRepositoryBuilder().setWorkTree(workdir)
				.build();
		assertGitdirPath(repo, "workdir", "repo", Constants.DOT_GIT);
	}

	@Test
	public void testNotBare_CreateRepositoryFromGitDirOnlyWithWorktreeConfig()
			throws Exception {
		File gitDir = getFile("workdir", "repoWithConfig");
		File workTree = getFile("workdir", "treeRoot");
		setWorkTree(gitDir, workTree);
		Repository repo = new FileRepositoryBuilder().setGitDir(gitDir).build();
		assertFalse(repo.isBare());
		assertWorkdirPath(repo, "workdir", "treeRoot");
		assertGitdirPath(repo, "workdir", "repoWithConfig");
	}

	@Test
	public void testBare_CreateRepositoryFromGitDirOnlyWithBareConfigTrue()
			throws Exception {
		File gitDir = getFile("workdir", "repoWithConfig");
		setBare(gitDir, true);
		Repository repo = new FileRepositoryBuilder().setGitDir(gitDir).build();
		assertTrue(repo.isBare());
	}

	@Test
	public void testWorkdirIsParent_CreateRepositoryFromGitDirOnlyWithBareConfigFalse()
			throws Exception {
		File gitDir = getFile("workdir", "repoWithBareConfigTrue", "child");
		setBare(gitDir, false);
		Repository repo = new FileRepositoryBuilder().setGitDir(gitDir).build();
		assertWorkdirPath(repo, "workdir", "repoWithBareConfigTrue");
	}

	@Test
	public void testNotBare_CreateRepositoryFromGitDirOnlyWithBareConfigFalse()
			throws Exception {
		File gitDir = getFile("workdir", "repoWithBareConfigFalse", "child");
		setBare(gitDir, false);
		Repository repo = new FileRepositoryBuilder().setGitDir(gitDir).build();
		assertFalse(repo.isBare());
		assertWorkdirPath(repo, "workdir", "repoWithBareConfigFalse");
		assertGitdirPath(repo, "workdir", "repoWithBareConfigFalse", "child");
	}

	@Test
	public void testExceptionThrown_BareRepoGetWorkDir() throws Exception {
		File gitDir = getFile("workdir");
		try {
			new FileRepository(gitDir).getWorkTree();
			fail("Expected NoWorkTreeException missing");
		} catch (NoWorkTreeException e) {
			// expected
		}
	}

	@Test
	public void testExceptionThrown_BareRepoGetIndex() throws Exception {
		File gitDir = getFile("workdir");
		try {
			new FileRepository(gitDir).readDirCache();
			fail("Expected NoWorkTreeException missing");
		} catch (NoWorkTreeException e) {
			// expected
		}
	}

	@Test
	public void testExceptionThrown_BareRepoGetIndexFile() throws Exception {
		File gitDir = getFile("workdir");
		try {
			new FileRepository(gitDir).getIndexFile();
			fail("Expected NoWorkTreeException missing");
		} catch (NoWorkTreeException e) {
			// expected
		}
	}

	private static File getFile(String... pathComponents) throws IOException {
		String rootPath = new File(new File("target"), "trash").getPath();
		for (String pathComponent : pathComponents)
			rootPath = rootPath + File.separatorChar + pathComponent;
		File result = new File(rootPath);
		FileUtils.mkdirs(result, true);
		return result;
	}

	private static void setBare(File gitDir, boolean bare) throws IOException,
			ConfigInvalidException {
		FileBasedConfig cfg = configFor(gitDir);
		cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_BARE, bare);
		cfg.save();
	}

	private static void setWorkTree(File gitDir, File workTree)
			throws IOException,
			ConfigInvalidException {
		String path = workTree.getAbsolutePath();
		FileBasedConfig cfg = configFor(gitDir);
		cfg.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_WORKTREE, path);
		cfg.save();
	}

	private static FileBasedConfig configFor(File gitDir) throws IOException,
			ConfigInvalidException {
		File configPath = new File(gitDir, Constants.CONFIG);
		FileBasedConfig cfg = new FileBasedConfig(configPath, FS.DETECTED);
		cfg.load();
		return cfg;
	}

	private static void assertGitdirPath(Repository repo, String... expected)
			throws IOException {
		File exp = getFile(expected).getCanonicalFile();
		File act = repo.getDirectory().getCanonicalFile();
		assertEquals("Wrong Git Directory", exp, act);
	}

	private static void assertWorkdirPath(Repository repo, String... expected)
			throws IOException {
		File exp = getFile(expected).getCanonicalFile();
		File act = repo.getWorkTree().getCanonicalFile();
		assertEquals("Wrong working Directory", exp, act);
	}
}
