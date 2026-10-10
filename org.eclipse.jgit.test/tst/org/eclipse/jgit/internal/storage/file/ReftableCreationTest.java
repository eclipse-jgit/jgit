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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefStorageFormat;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

/** Tests ref storage format selection and reftable repository creation. */
public class ReftableCreationTest extends LocalDiskRepositoryTestCase {

	@Test
	public void directCreationWritesNativeCompatibilityFiles() throws Exception {
		File dir = new File(testRoot.getRoot(), "compat.git");
		try (FileRepository repo = (FileRepository) new FileRepositoryBuilder()
				.setGitDir(dir).setRefStorageFormat("reftable").build()) {
			repo.create(true);
			assertEquals("refs/heads/master",
					repo.exactRef(Constants.HEAD).getTarget().getName());
		}
		assertEquals("ref: refs/heads/.invalid\n",
				Files.readString(new File(dir, Constants.HEAD).toPath()));
		assertEquals("this repository uses the reftable format\n",
				Files.readString(new File(dir, "refs/heads").toPath()));
	}

	@Test
	public void directCreationPersistsReftableFormat() throws Exception {
		File dir = new File(testRoot.getRoot(), "direct.git");
		try (FileRepository repo = (FileRepository) new FileRepositoryBuilder()
				.setGitDir(dir).setRefStorageFormat("reftable").build()) {
			assertTrue(repo.getRefDatabase() instanceof FileReftableDatabase);
			repo.create(true);
			FileBasedConfig disk = new FileBasedConfig(new File(dir, "config"),
					FS.DETECTED);
			disk.load();
			assertEquals(1,
					disk.getInt("core", null, "repositoryformatversion", 0));
			assertEquals("reftable",
					disk.getString("extensions", null, "refStorage"));
		}
	}

	@Test
	public void directCreationIsDiscoverable() throws Exception {
		File dir = new File(testRoot.getRoot(), "discover.git");
		try (FileRepository repo = (FileRepository) new FileRepositoryBuilder()
				.setGitDir(dir).setRefStorageFormat("reftable").build()) {
			repo.create(true);
		}
		assertTrue(RepositoryCache.FileKey.isGitRepository(dir, FS.DETECTED));
		assertEquals(dir, RepositoryCache.FileKey.resolve(
				new File(testRoot.getRoot(), "discover"), FS.DETECTED));
	}

	@Test
	public void directCreationReopensWithSameBackendAndHead() throws Exception {
		File dir = new File(testRoot.getRoot(), "reopen.git");
		try (FileRepository repo = (FileRepository) new FileRepositoryBuilder()
				.setGitDir(dir).setRefStorageFormat("reftable").build()) {
			repo.create(true);
			repo.updateRef(Constants.HEAD).link("refs/heads/prototype");
		}
		try (FileRepository reopened = new FileRepository(dir)) {
			assertTrue(reopened.getRefDatabase() instanceof FileReftableDatabase);
			assertEquals("refs/heads/prototype",
					reopened.exactRef(Constants.HEAD).getTarget().getName());
		}
	}

	@Test
	public void defaultCreationReopensAsRefDirectory() throws Exception {
		File dir = new File(testRoot.getRoot(), "default.git");
		try (FileRepository repo = new FileRepository(dir)) {
			repo.create(true);
		}
		FileBasedConfig disk = new FileBasedConfig(new File(dir, "config"),
				FS.DETECTED);
		disk.load();
		assertEquals(0,
				disk.getInt("core", null, "repositoryformatversion", -1));
		assertNull(disk.getString("extensions", null, "refStorage"));
		try (Repository reopened = RepositoryCache.FileKey.exact(dir,
				FS.DETECTED).open(true)) {
			assertTrue(reopened.getRefDatabase() instanceof RefDirectory);
		}
	}

	@Test
	public void globalExtensionsRefStorageIgnoredAtCreation() throws Exception {
		FileBasedConfig global = mockSystemReader.getJGitConfig();
		global.setInt("core", null, "repositoryformatversion", 1);
		global.setString("extensions", null, "refStorage", "reftable");
		global.save();
		File dir = new File(testRoot.getRoot(), "global-ignored.git");
		try (FileRepository repo = new FileRepository(dir)) {
			repo.create(true);
			assertTrue(repo.getRefDatabase() instanceof RefDirectory);
		}
		FileBasedConfig disk = new FileBasedConfig(new File(dir, "config"),
				FS.DETECTED);
		disk.load();
		assertEquals(0,
				disk.getInt("core", null, "repositoryformatversion", -1));
		assertNull(disk.getString("extensions", null, "refStorage"));
	}

	@Test
	public void explicitApiCreatesReftableWithoutInheritedConfig()
			throws Exception {
		File dir = new File(testRoot.getRoot(), "explicit.git");
		try (FileRepository repo = (FileRepository) new FileRepositoryBuilder()
				.setGitDir(dir).setRefStorageFormat("reftable").build()) {
			assertTrue(repo.getRefDatabase() instanceof FileReftableDatabase);
			repo.create(true);
		}
		try (Repository reopened = RepositoryCache.FileKey.exact(dir,
				FS.DETECTED).open(true)) {
			assertTrue(
					reopened.getRefDatabase() instanceof FileReftableDatabase);
		}
	}

	@Test
	public void existingRepoRejectsConflictingRefStorageFormat() throws Exception {
		File dir = new File(testRoot.getRoot(), "existing.git");
		try (FileRepository repo = new FileRepository(dir)) {
			repo.create(true);
			assertTrue(repo.getRefDatabase() instanceof RefDirectory);
		}
		assertThrows(IOException.class,
				() -> new FileRepositoryBuilder().setGitDir(dir)
						.setRefStorageFormat("reftable").build());
		try (FileRepository reopened = (FileRepository) new FileRepositoryBuilder()
				.setGitDir(dir).setRefStorageFormat("files").build()) {
			assertTrue(reopened.getRefDatabase() instanceof RefDirectory);
		}
	}

	@Test
	public void existingReftableRejectsConflictingRefStorageFormat()
			throws Exception {
		File dir = new File(testRoot.getRoot(), "existing-reftable.git");
		try (Repository repo = new FileRepositoryBuilder().setGitDir(dir)
				.setRefStorageFormat("reftable").build()) {
			repo.create(true);
		}
		assertThrows(IOException.class,
				() -> new FileRepositoryBuilder().setGitDir(dir)
						.setRefStorageFormat("files").build());
		try (Repository repo = new FileRepositoryBuilder().setGitDir(dir)
				.setRefStorageFormat("reftable").build()) {
			assertTrue(repo.getRefDatabase() instanceof FileReftableDatabase);
		}
	}

	@Test
	public void explicitReftableCreatesNonBareRepository() throws Exception {
		File workTree = new File(testRoot.getRoot(), "worktree");
		File dir = new File(workTree, Constants.DOT_GIT);
		try (Repository repo = new FileRepositoryBuilder().setGitDir(dir)
				.setWorkTree(workTree).setInitialBranch("prototype")
				.setRefStorageFormat("reftable").build()) {
			repo.create(false);
		}
		try (Repository repo = new FileRepository(dir)) {
			assertTrue(repo.getRefDatabase() instanceof FileReftableDatabase);
			assertEquals(workTree, repo.getWorkTree());
			assertEquals("refs/heads/prototype",
					repo.exactRef(Constants.HEAD).getTarget().getName());
		}
	}

	@Test
	public void explicitEnumFormatSelectsBackend() throws Exception {
		File reftableDir = new File(testRoot.getRoot(), "enum-reftable.git");
		try (FileRepository repo = (FileRepository) new FileRepositoryBuilder()
				.setGitDir(reftableDir)
				.setRefStorageFormat(RefStorageFormat.REFTABLE).build()) {
			assertTrue(repo.getRefDatabase() instanceof FileReftableDatabase);
		}
		File filesDir = new File(testRoot.getRoot(), "enum-files.git");
		try (FileRepository repo = (FileRepository) new FileRepositoryBuilder()
				.setGitDir(filesDir)
				.setRefStorageFormat(RefStorageFormat.FILES).build()) {
			assertTrue(repo.getRefDatabase() instanceof RefDirectory);
		}
	}

	@Test
	public void builderRejectsLegacyAndUppercaseFormatNames() throws Exception {
		for (String format : new String[] { "refdir", "FILES", "REFTABLE", "" }) {
			File dir = new File(testRoot.getRoot(), "invalid-" + format);
			assertThrows(IOException.class,
					() -> new FileRepositoryBuilder().setGitDir(dir)
							.setRefStorageFormat(format).build());
		}
	}

	@Test
	public void unknownRefStorageFormatIsRejected() throws Exception {
		File dir = new File(testRoot.getRoot(), "bogus.git");
		assertThrows(IOException.class,
				() -> new FileRepositoryBuilder().setGitDir(dir)
						.setRefStorageFormat("bogus").build());
	}
}
