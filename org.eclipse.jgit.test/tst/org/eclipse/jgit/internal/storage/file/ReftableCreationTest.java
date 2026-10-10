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

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

/** Tests ref storage format selection when creating a repository. */
public class ReftableCreationTest extends LocalDiskRepositoryTestCase {

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
