/*
 * Copyright (C) 2026, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.Test;

public class RefStorageFormatTest extends LocalDiskRepositoryTestCase {

	private void setDefault(String format) throws Exception {
		StoredConfig config = mockSystemReader.getUserConfig();
		config.setString("init", null, "defaultRefFormat", format);
		config.save();
	}

	private void setEnvironment(String format) {
		mockSystemReader.setProperty(Constants.GIT_DEFAULT_REF_FORMAT_KEY, format);
	}

	private void assertCreation(String explicit, String expected)
			throws Exception {
		File dir = new File(testRoot.getRoot(), "new.git");
		try (Repository repo = new FileRepositoryBuilder().setGitDir(dir)
				.setRefStorageFormat(explicit).build()) {
			assertEquals("reftable".equals(expected),
					repo.getRefDatabase() instanceof FileReftableDatabase);
			repo.create(true);
		}
		// Defaults must not affect subsequent opens of an existing repository.
		setDefault("reftable".equals(expected) ? "files" : "reftable");
		setEnvironment("reftable".equals(expected) ? "files" : "reftable");
		try (Repository repo = new FileRepository(dir)) {
			assertEquals("reftable".equals(expected),
					repo.getRefDatabase() instanceof FileReftableDatabase);
			assertEquals("reftable".equals(expected) ? 1 : 0,
					repo.getConfig().getInt("core", "repositoryformatversion", -1));
		}
	}

	@Test
	public void configSelectsReftable() throws Exception {
		setDefault("reftable");
		assertCreation(null, "reftable");
	}

	@Test
	public void configSelectsFiles() throws Exception {
		setDefault("files");
		assertCreation(null, "files");
	}

	@Test
	public void environmentSelectsReftable() throws Exception {
		setEnvironment("reftable");
		assertCreation(null, "reftable");
	}

	@Test
	public void environmentSelectsFiles() throws Exception {
		setEnvironment("files");
		assertCreation(null, "files");
	}

	@Test
	public void environmentFilesOverridesConfigReftable() throws Exception {
		setDefault("reftable");
		setEnvironment("files");
		assertCreation(null, "files");
	}

	@Test
	public void environmentReftableOverridesConfigFiles() throws Exception {
		setDefault("files");
		setEnvironment("reftable");
		assertCreation(null, "reftable");
	}

	@Test
	public void explicitFilesOverridesDefaults() throws Exception {
		setDefault("reftable");
		setEnvironment("reftable");
		assertCreation("files", "files");
	}

	@Test
	public void explicitReftableOverridesDefaults() throws Exception {
		setDefault("files");
		setEnvironment("files");
		assertCreation("reftable", "reftable");
	}

	@Test
	public void explicitFormatOverridesInvalidEnvironment() throws Exception {
		setEnvironment("bogus");
		assertCreation("files", "files");
	}

	@Test
	public void invalidConfigFallsBackToFiles() throws Exception {
		setDefault("bogus");
		assertCreation(null, "files");
	}

	@Test
	public void invalidEnvironmentFailsBeforeCreatingRepository() throws Exception {
		setDefault("files");
		for (String format : new String[] { "bogus", "refdir", "REFTABLE", "" }) {
			setEnvironment(format);
			File dir = new File(testRoot.getRoot(), "invalid-" + format);
			assertThrows(IOException.class,
					() -> new FileRepositoryBuilder().setGitDir(dir).build());
			assertFalse(dir.exists());
		}
	}

	@Test
	public void initCommandUsesConfigForNonBareRepository() throws Exception {
		setDefault("reftable");
		File dir = new File(testRoot.getRoot(), "worktree");
		try (Git git = Git.init().setDirectory(dir)
				.setInitialBranch("prototype").call()) {
			assertEquals(FileReftableDatabase.class,
					git.getRepository().getRefDatabase().getClass());
			assertEquals("refs/heads/prototype", git.getRepository()
					.exactRef(Constants.HEAD).getTarget().getName());
		}
	}

	@Test
	public void initCommandUsesEnvironmentForBareRepository() throws Exception {
		setEnvironment("reftable");
		File dir = new File(testRoot.getRoot(), "bare.git");
		try (Git git = Git.init().setDirectory(dir).setBare(true).call()) {
			assertEquals(FileReftableDatabase.class,
					git.getRepository().getRefDatabase().getClass());
		}
	}
}
