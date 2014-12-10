/*
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Before;
import org.junit.Test;

public class InitCommandTest extends RepositoryTestCase {

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
	}

	@Test
	public void testInitRepository() throws IOException, JGitInternalException,
			GitAPIException {
		File directory = createTempDirectory("testInitRepository");
		InitCommand command = new InitCommand();
		command.setDirectory(directory);
		Repository repository = command.call().getRepository();
		addRepoToClose(repository);
		assertNotNull(repository);
	}

	@Test
	public void testInitNonEmptyRepository() throws IOException,
			JGitInternalException, GitAPIException {
		File directory = createTempDirectory("testInitRepository2");
		File someFile = new File(directory, "someFile");
		someFile.createNewFile();
		assertTrue(someFile.exists());
		assertTrue(directory.listFiles().length > 0);
		InitCommand command = new InitCommand();
		command.setDirectory(directory);
		Repository repository = command.call().getRepository();
		addRepoToClose(repository);
		assertNotNull(repository);
	}

	@Test
	public void testInitBareRepository() throws IOException,
			JGitInternalException, GitAPIException {
		File directory = createTempDirectory("testInitBareRepository");
		InitCommand command = new InitCommand();
		command.setDirectory(directory);
		command.setBare(true);
		Repository repository = command.call().getRepository();
		addRepoToClose(repository);
		assertNotNull(repository);
		assertTrue(repository.isBare());
	}

	// non-bare repos where gitDir and directory is set. Same as
	// "git init --separate-git-dir /tmp/a /tmp/b"
	@Test
	public void testInitWithExplicitGitDir() throws IOException,
			JGitInternalException, GitAPIException {
		File wt = createTempDirectory("testInitRepositoryWT");
		File gitDir = createTempDirectory("testInitRepositoryGIT");
		InitCommand command = new InitCommand();
		command.setDirectory(wt);
		command.setGitDir(gitDir);
		Repository repository = command.call().getRepository();
		addRepoToClose(repository);
		assertNotNull(repository);
		assertEqualsFile(wt, repository.getWorkTree());
		assertEqualsFile(gitDir, repository.getDirectory());
	}

	// non-bare repos where only gitDir is set. Same as
	// "git init --separate-git-dir /tmp/a"
	@Test
	public void testInitWithOnlyExplicitGitDir() throws IOException,
			JGitInternalException, GitAPIException {
		MockSystemReader reader = (MockSystemReader) SystemReader.getInstance();
		reader.setProperty(Constants.OS_USER_DIR, getTemporaryDirectory()
				.getAbsolutePath());
		File gitDir = createTempDirectory("testInitRepository/.git");
		InitCommand command = new InitCommand();
		command.setGitDir(gitDir);
		Repository repository = command.call().getRepository();
		addRepoToClose(repository);
		assertNotNull(repository);
		assertEqualsFile(gitDir, repository.getDirectory());
		assertEqualsFile(new File(reader.getProperty("user.dir")),
				repository.getWorkTree());
	}

	// Bare repos where gitDir and directory is set will only work if gitDir and
	// directory is pointing to same dir. Same as
	// "git init --bare --separate-git-dir /tmp/a /tmp/b"
	// (works in native git but I guess that's more a bug)
	@Test(expected = IllegalStateException.class)
	public void testInitBare_DirAndGitDirMustBeEqual() throws IOException,
			JGitInternalException, GitAPIException {
		File gitDir = createTempDirectory("testInitRepository.git");
		InitCommand command = new InitCommand();
		command.setBare(true);
		command.setDirectory(gitDir);
		command.setGitDir(new File(gitDir, ".."));
		command.call();
	}

	// If neither directory nor gitDir is set in a non-bare repo make sure
	// worktree and gitDir are set correctly. Standard case. Same as
	// "git init"
	@Test
	public void testInitWithDefaultsNonBare() throws JGitInternalException,
			GitAPIException, IOException {
		MockSystemReader reader = (MockSystemReader) SystemReader.getInstance();
		reader.setProperty(Constants.OS_USER_DIR, getTemporaryDirectory()
				.getAbsolutePath());
		InitCommand command = new InitCommand();
		command.setBare(false);
		Repository repository = command.call().getRepository();
		addRepoToClose(repository);
		assertNotNull(repository);
		assertEqualsFile(new File(reader.getProperty("user.dir"), ".git"),
				repository.getDirectory());
		assertEqualsFile(new File(reader.getProperty("user.dir")),
				repository.getWorkTree());
	}

	// If neither directory nor gitDir is set in a bare repo make sure
	// worktree and gitDir are set correctly. Standard case. Same as
	// "git init --bare"
	@Test(expected = NoWorkTreeException.class)
	public void testInitWithDefaultsBare() throws JGitInternalException,
			GitAPIException, IOException {
		MockSystemReader reader = (MockSystemReader) SystemReader.getInstance();
		reader.setProperty(Constants.OS_USER_DIR, getTemporaryDirectory()
				.getAbsolutePath());
		InitCommand command = new InitCommand();
		command.setBare(true);
		Repository repository = command.call().getRepository();
		addRepoToClose(repository);
		assertNotNull(repository);
		assertEqualsFile(new File(reader.getProperty("user.dir")),
				repository.getDirectory());
		assertNull(repository.getWorkTree());
	}

	// In a non-bare repo when directory and gitDir is set then they shouldn't
	// point to the same dir. Same as
	// "git init --separate-git-dir /tmp/a /tmp/a"
	// (works in native git but I guess that's more a bug)
	@Test(expected = IllegalStateException.class)
	public void testInitNonBare_GitdirAndDirShouldntBeSame()
			throws JGitInternalException, GitAPIException, IOException {
		File gitDir = createTempDirectory("testInitRepository.git");
		InitCommand command = new InitCommand();
		command.setBare(false);
		command.setGitDir(gitDir);
		command.setDirectory(gitDir);
		command.call().getRepository();
	}
}
