/*
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
	public void testInitRepository()
			throws IOException, JGitInternalException, GitAPIException {
		File directory = createTempDirectory("testInitRepository");
		InitCommand command = new InitCommand();
		command.setDirectory(directory);
		try (Git git = command.call()) {
			assertNotNull(git.getRepository());
		}
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
		try (Git git = command.call()) {
			assertNotNull(git.getRepository());
		}
	}

	@Test
	public void testInitBareRepository() throws IOException,
			JGitInternalException, GitAPIException {
		File directory = createTempDirectory("testInitBareRepository");
		InitCommand command = new InitCommand();
		command.setDirectory(directory);
		command.setBare(true);
		try (Git git = command.call()) {
			Repository repository = git.getRepository();
			assertNotNull(repository);
			assertTrue(repository.isBare());
		}
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
		try (Git git = command.call()) {
			Repository repository = git.getRepository();
			assertNotNull(repository);
			assertEqualsFile(wt, repository.getWorkTree());
			assertEqualsFile(gitDir, repository.getDirectory());
		}
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
		try (Git git = command.call()) {
			Repository repository = git.getRepository();
			assertNotNull(repository);
			assertEqualsFile(gitDir, repository.getDirectory());
			assertEqualsFile(new File(reader.getProperty("user.dir")),
					repository.getWorkTree());
		}
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
		try (Git git = command.call()) {
			Repository repository = git.getRepository();
			assertNotNull(repository);
			assertEqualsFile(new File(reader.getProperty("user.dir"), ".git"),
					repository.getDirectory());
			assertEqualsFile(new File(reader.getProperty("user.dir")),
					repository.getWorkTree());
		}
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
		try (Git git = command.call()) {
			Repository repository = git.getRepository();
			assertNotNull(repository);
			assertEqualsFile(new File(reader.getProperty("user.dir")),
					repository.getDirectory());
			assertNull(repository.getWorkTree());
		}
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
