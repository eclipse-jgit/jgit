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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectFormat;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
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
			Repository r = git.getRepository();
			assertNotNull(r);
			assertEquals("refs/heads/master", r.getFullBranch());
		}
	}

	@Test
	public void testInitRepositoryDefaultObjectFormat() throws Exception {
		File directory = createTempDirectory(
				"testInitRepositoryDefaultObjectFormat");
		InitCommand command = new InitCommand();
		command.setDirectory(directory);
		try (Git git = command.call()) {
			Repository r = git.getRepository();
			assertNotNull(r);
			assertEquals(ObjectFormat.SHA_1, r.getObjectFormat());
			assertNull(r.getConfig().getString(
					ConfigConstants.CONFIG_EXTENSIONS_SECTION, null,
					ConfigConstants.CONFIG_KEY_OBJECT_FORMAT));
		}
	}

	@Test
	public void testInitRepositorySha256() throws Exception {
		File directory = createTempDirectory("testInitRepositorySha256");
		InitCommand command = new InitCommand();
		command.setDirectory(directory);
		command.setObjectFormat(ObjectFormat.SHA_256);
		try (Git git = command.call()) {
			Repository r = git.getRepository();
			assertNotNull(r);
			assertEquals(ObjectFormat.SHA_256, r.getObjectFormat());
			StoredConfig config = r.getConfig();
			assertEquals(1,
					config.getInt(ConfigConstants.CONFIG_CORE_SECTION, null,
							ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION, -1));
			assertEquals("sha256",
					config.getString(
							ConfigConstants.CONFIG_EXTENSIONS_SECTION, null,
							ConfigConstants.CONFIG_KEY_OBJECT_FORMAT));
		}
		// Reopen: the object format must be detected from the config.
		try (Repository r = new FileRepository(
				new File(directory, Constants.DOT_GIT))) {
			assertEquals(ObjectFormat.SHA_256, r.getObjectFormat());
		}
	}

	@Test
	public void testAddCommitLogStatusSha256() throws Exception {
		File directory = createTempDirectory("testAddCommitLogSha256");
		InitCommand command = new InitCommand();
		command.setDirectory(directory);
		command.setObjectFormat(ObjectFormat.SHA_256);
		try (Git git = command.call()) {
			File f = new File(directory, "a.txt");
			try (FileOutputStream out = new FileOutputStream(f)) {
				out.write("content".getBytes(UTF_8));
			}
			git.add().addFilepattern("a.txt").call();
			PersonIdent ident = new PersonIdent("Test", "t@example.com");
			RevCommit c = git.commit().setAuthor(ident).setCommitter(ident)
					.setMessage("first").call();
			assertEquals(64, c.name().length());
			assertEquals("Test", c.getAuthorIdent().getName());
			assertEquals("first", c.getFullMessage().trim());
			int n = 0;
			java.util.Iterator<RevCommit> it = git.log().call().iterator();
			while (it.hasNext()) {
				it.next();
				n++;
			}
			assertEquals(1, n);
			assertTrue(git.status().call().isClean());
		}
	}

	@Test
	public void testStatusCleanWithRacilyCleanEntrySha256() throws Exception {
		File directory = createTempDirectory("testStatusSmudgeSha256");
		InitCommand command = new InitCommand();
		command.setDirectory(directory);
		command.setObjectFormat(ObjectFormat.SHA_256);
		try (Git git = command.call()) {
			File f = new File(directory, "a.txt");
			try (FileOutputStream out = new FileOutputStream(f)) {
				out.write("content".getBytes(UTF_8));
			}
			git.add().addFilepattern("a.txt").call();
			PersonIdent ident = new PersonIdent("Test", "t@example.com");
			git.commit().setAuthor(ident).setCommitter(ident)
					.setMessage("first").call();
			// Simulate a racy filesystem: when the index is written in the
			// same time slot as the file was created the entry is smudged,
			// forcing a content check on the next status call.
			DirCache dc = git.getRepository().lockDirCache();
			dc.getEntry(0).smudgeRacilyClean();
			dc.write();
			dc.commit();
			assertTrue(git.status().call().isClean());
		}
	}

	@Test
	public void testInitRepositoryMainInitialBranch()
			throws IOException, JGitInternalException, GitAPIException {
		File directory = createTempDirectory("testInitRepository");
		InitCommand command = new InitCommand();
		command.setDirectory(directory);
		command.setInitialBranch("main");
		try (Git git = command.call()) {
			Repository r = git.getRepository();
			assertNotNull(r);
			assertEquals("refs/heads/main", r.getFullBranch());
		}
	}

	@Test
	public void testInitRepositoryCustomDefaultBranch()
			throws Exception {
		File directory = createTempDirectory("testInitRepository");
		InitCommand command = new InitCommand();
		command.setDirectory(directory);
		MockSystemReader reader = (MockSystemReader) SystemReader.getInstance();
		StoredConfig c = reader.getUserConfig();
		String old = c.getString(ConfigConstants.CONFIG_INIT_SECTION, null,
				ConfigConstants.CONFIG_KEY_DEFAULT_BRANCH);
		c.setString(ConfigConstants.CONFIG_INIT_SECTION, null,
				ConfigConstants.CONFIG_KEY_DEFAULT_BRANCH, "main");
		try (Git git = command.call()) {
			Repository r = git.getRepository();
			assertNotNull(r);
			assertEquals("refs/heads/main", r.getFullBranch());
		} finally {
			c.setString(ConfigConstants.CONFIG_INIT_SECTION, null,
					ConfigConstants.CONFIG_KEY_DEFAULT_BRANCH, old);
		}
	}

	@Test
	public void testInitRepositoryNullInitialBranch() throws Exception {
		File directory = createTempDirectory("testInitRepository");
		InitCommand command = new InitCommand();
		command.setDirectory(directory);
		command.setInitialBranch("main");
		command.setInitialBranch(null);
		try (Git git = command.call()) {
			Repository r = git.getRepository();
			assertNotNull(r);
			assertEquals("refs/heads/master", r.getFullBranch());
		}
	}

	@Test
	public void testInitRepositoryEmptyInitialBranch() throws Exception {
		File directory = createTempDirectory("testInitRepository");
		InitCommand command = new InitCommand();
		command.setDirectory(directory);
		command.setInitialBranch("main");
		command.setInitialBranch("");
		try (Git git = command.call()) {
			Repository r = git.getRepository();
			assertNotNull(r);
			assertEquals("refs/heads/master", r.getFullBranch());
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
			assertEquals("refs/heads/master", repository.getFullBranch());
		}
	}

	@Test
	public void testInitBareRepositoryMainInitialBranch()
			throws IOException, JGitInternalException, GitAPIException {
		File directory = createTempDirectory("testInitBareRepository");
		InitCommand command = new InitCommand();
		command.setDirectory(directory);
		command.setBare(true);
		command.setInitialBranch("main");
		try (Git git = command.call()) {
			Repository repository = git.getRepository();
			assertNotNull(repository);
			assertTrue(repository.isBare());
			assertEquals("refs/heads/main", repository.getFullBranch());
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
