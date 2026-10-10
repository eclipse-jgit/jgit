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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

/**
 * Tests that directly creating a reftable repository produces a repository
 * that reopens as reftable and whose persisted format matches its storage
 * backend.
 */
public class ReftableCreationTest extends LocalDiskRepositoryTestCase {

	private void enableGlobalReftable() throws Exception {
		FileBasedConfig config = mockSystemReader.getJGitConfig();
		config.setInt("core", null, "repositoryformatversion", 1);
		config.setString("extensions", null, "refStorage", "reftable");
		config.save();
	}

	@Test
	public void directCreationWritesNativeCompatibilityFiles() throws Exception {
		enableGlobalReftable();
		File dir = new File(testRoot.getRoot(), "compat.git");
		try (FileRepository repo = new FileRepository(dir)) {
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
		enableGlobalReftable();
		File dir = new File(testRoot.getRoot(), "direct.git");
		try (FileRepository repo = new FileRepository(dir)) {
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
		enableGlobalReftable();
		File dir = new File(testRoot.getRoot(), "discover.git");
		try (FileRepository repo = new FileRepository(dir)) {
			repo.create(true);
		}
		assertTrue(RepositoryCache.FileKey.isGitRepository(dir, FS.DETECTED));
		assertEquals(dir, RepositoryCache.FileKey.resolve(
				new File(testRoot.getRoot(), "discover"), FS.DETECTED));
	}

	@Test
	public void directCreationReopensWithSameBackendAndHead() throws Exception {
		enableGlobalReftable();
		File dir = new File(testRoot.getRoot(), "reopen.git");
		try (FileRepository repo = new FileRepository(dir)) {
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
	public void directCreationSelfDescribesAsReftable() throws Exception {
		enableGlobalReftable();
		File dir = new File(testRoot.getRoot(), "selfdescribe.git");
		try (FileRepository repo = new FileRepository(dir)) {
			repo.create(true);
		}
		// Drop the inherited configuration: the repository must select
		// reftable from its own persisted format alone.
		FileBasedConfig global = mockSystemReader.getJGitConfig();
		global.unsetSection("extensions", null);
		global.unset("core", null, "repositoryformatversion");
		global.save();
		try (Repository reopened = RepositoryCache.FileKey.exact(dir,
				FS.DETECTED).open(true)) {
			assertTrue(
					reopened.getRefDatabase() instanceof FileReftableDatabase);
		}
	}
}
