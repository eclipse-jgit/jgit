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
 * Test reading of git config
 */
public class RepositoryTest extends LocalDiskRepositoryTestCase {

	public void test001_CreateRepositoryFromArbitraryGitDir() throws Exception {
		setUp();
		File gitDir = getFile("workdir");
		assertTrue("Repository on arbitrary Git dir should be bare",
				new Repository(gitDir).isBare());
		tearDown();
	}

	public void test002_CreateRepositoryFromDotGitGitDir() throws Exception {
		setUp();
		File gitDir = getFile("workdir", Constants.DOT_GIT);
		assertFalse("Repository on dotgit Git dir should not be bare",
				new Repository(gitDir).isBare());
		tearDown();
	}

	public void test003_CreateRepositoryFromWorkDirOnly() throws Exception {
		setUp();
		File workdir = getFile("workdir", "repo");
		Repository repo = new Repository(null, workdir);
		assertFalse("Repository on arbitrary workdir should not be bare", repo
				.isBare());
		tearDown();
	}

	public void test004_CreateRepositoryFromWorkDirOnly() throws Exception {
		setUp();
		File workdir = getFile("workdir", "repo");
		Repository repo = new Repository(null, workdir);
		assertTrue("Implicit directory should be dotgit", repo.getDirectory()
				.getPath().endsWith(Constants.DOT_GIT));
		tearDown();
	}

	public void test005_CreateRepositoryFromGitDirOnlyWithWorktreeConfig()
			throws Exception {
		setUp();
		File gitDir = getFile("workdir", "repoWithConfig");
		Repository repo = new Repository(gitDir, null);

		repo.getConfig().setString(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_WORKTREE, "worktreeroot");
		repo.getConfig().save();

		repo = new Repository(gitDir, null);

		assertFalse(
				"Repository on arbitrary Git dir with worktree config should not be bare",
				repo.isBare());
		repo.close();

		tearDown();
	}

	public void test006_CreateRepositoryFromGitDirOnlyWithBareConfigTrue()
			throws Exception {
		setUp();
		File gitDir = getFile("workdir", "repoWithConfig");
		Repository repo = new Repository(gitDir, null);

		repo.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_BARE, true);
		repo.getConfig().save();

		repo = new Repository(gitDir, null);

		assertTrue(
				"Repository on arbitrary Git dir with bare config true should be bare",
				repo.isBare());
		repo.close();

		tearDown();
	}

	public void test007_CreateRepositoryFromGitDirOnlyWithBareConfigFalse()
			throws Exception {
		setUp();
		File gitDir = getFile("workdir", "repoWithBareConfigTrue", "child");
		Repository repo = new Repository(gitDir, null);

		repo.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_BARE, false);
		repo.getConfig().save();

		repo = new Repository(gitDir, null);

		String workdir = repo.getWorkDir().getName();

		assertEquals(
				"Working dir of Repository on arbitrary Git dir with bare config false should not be parent",
				"repoWithBareConfigTrue", workdir);
		repo.close();

		tearDown();
	}

	public void test008_CreateRepositoryFromGitDirOnlyWithBareConfigFalse()
			throws Exception {
		setUp();
		File gitDir = getFile("workdir", "repoWithBareConfigFalse", "child");
		Repository repo = new Repository(gitDir, null);

		repo.getConfig().setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_BARE, false);
		repo.getConfig().save();

		repo = new Repository(gitDir, null);

		assertFalse(
				"Repository on arbitrary Git dir with bare config false should not be bare",
				repo.isBare());

		repo.close();

		tearDown();
	}

	public void test009_MakeBareUnbareBySetWorkdir() throws Exception {
		setUp();
		File gitDir = getFile("gitDir");
		Repository repo = new Repository(gitDir);
		repo.setWorkDir(getFile("workingDir"));
		assertFalse("Repository on arbitrary Git dir should not be bare", repo
				.isBare());

		tearDown();
	}

	public void test010_AssertExceptionGetWorkDir() throws Exception {
		setUp();
		File gitDir = getFile("workdir");
		try {
			new Repository(gitDir).getWorkDir();
			fail("Expected Exception missing");
		} catch (Exception e) {
			// expected
		}
		tearDown();
	}

	public void test011_AssertExceptionGetIndex() throws Exception {
		setUp();
		File gitDir = getFile("workdir");
		try {
			new Repository(gitDir).getIndex();
			fail("Expected Exception missing");
		} catch (Exception e) {
			// expected
		}
		tearDown();
	}

	public void test012_AssertExceptionGetIndexFile() throws Exception {
		setUp();
		File gitDir = getFile("workdir");
		try {
			new Repository(gitDir).getIndexFile();
			fail("Expected Exception missing");
		} catch (Exception e) {
			// expected
		}
		tearDown();
	}

	public File getFile(String... pathComponents) {
		String rootPath = new File(new File("target"), "trash").getPath();
		for (String pathComponent : pathComponents)
			rootPath = rootPath + File.separatorChar + pathComponent;
		File result = new File(rootPath);
		result.mkdir();
		return result;
	}

}
