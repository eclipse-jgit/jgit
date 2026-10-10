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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefStorageFormat;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

/**
 * Tests that converting a RefDirectory repository to reftable preserves its
 * references, including a symbolic HEAD whose target branch is still unborn.
 */
public class ReftableConversionTest extends LocalDiskRepositoryTestCase {

	@Test
	public void roundTripPreservesUnbornHeadAndReflog() throws Exception {
		File dir = new File(testRoot.getRoot(), "roundtrip.git");
		List<ReflogEntry> expected;
		List<ReflogEntry> expectedPopulated;
		try (FileRepository repo = new FileRepository(dir)) {
			repo.create(true);
			RevCommit commit = new TestRepository<>(repo).commit().create();
			// Force a reflog on the populated branch so conversion writes log
			// records for it as well as HEAD. HEAD sorts first and must be
			// written before "refs/".
			RefUpdate populated = repo.updateRef("refs/heads/populated");
			populated.setForceRefLog(true);
			populated.setNewObjectId(commit);
			populated.setRefLogMessage("create populated branch", false);
			assertEquals(RefUpdate.Result.NEW, populated.update());
			expectedPopulated = repo.getReflogReader("refs/heads/populated")
					.getReverseEntries();
			assertEquals(1, expectedPopulated.size());
			RefUpdate head = repo.updateRef(Constants.HEAD);
			head.setForceRefLog(true);
			head.setRefLogMessage("select populated branch", false);
			head.link("refs/heads/populated");
			head = repo.updateRef(Constants.HEAD);
			head.setForceRefLog(true);
			head.setRefLogMessage("select unborn branch", false);
			head.link("refs/heads/unborn");
			expected = repo.getReflogReader(Constants.HEAD).getReverseEntries();
			assertEquals(2, expected.size());
			repo.convertRefStorage("reftable", true, false);
		}
		try (FileRepository repo = new FileRepository(dir)) {
			assertUnbornHeadAndReflog(repo, expected);
			assertReflogEquals(expectedPopulated,
					repo.getReflogReader("refs/heads/populated")
							.getReverseEntries());
			repo.convertRefStorage("files", true, false);
		}
		try (FileRepository repo = new FileRepository(dir)) {
			assertTrue(repo.getRefDatabase() instanceof RefDirectory);
			assertUnbornHeadAndReflog(repo, expected);
			assertReflogEquals(expectedPopulated,
					repo.getReflogReader("refs/heads/populated")
							.getReverseEntries());
		}
	}

	private static void assertUnbornHeadAndReflog(Repository repo,
			List<ReflogEntry> expected) throws Exception {
		assertEquals("refs/heads/unborn",
				repo.exactRef(Constants.HEAD).getTarget().getName());
		assertNull(repo.exactRef(Constants.HEAD).getObjectId());
		assertReflogEquals(expected,
				repo.getReflogReader(Constants.HEAD).getReverseEntries());
	}

	private static void assertReflogEquals(List<ReflogEntry> expected,
			List<ReflogEntry> actual) {
		assertEquals(expected.size(), actual.size());
		for (int i = 0; i < expected.size(); i++) {
			assertEquals(expected.get(i).getOldId(), actual.get(i).getOldId());
			assertEquals(expected.get(i).getNewId(), actual.get(i).getNewId());
			assertEquals(expected.get(i).getWho(), actual.get(i).getWho());
			assertEquals(expected.get(i).getComment(), actual.get(i).getComment());
		}
	}

	@Test
	public void emptyRepositoryRoundTripPreservesUnbornHead() throws Exception {
		File dir = new File(testRoot.getRoot(), "empty-roundtrip.git");
		try (FileRepository repo = new FileRepository(dir)) {
			repo.create(true);
			repo.updateRef(Constants.HEAD).link("refs/heads/prototype");
			repo.convertRefStorage("reftable", false, false);
			repo.convertRefStorage("files", false, false);
		}
		try (FileRepository repo = new FileRepository(dir)) {
			assertEquals("refs/heads/prototype",
					repo.exactRef(Constants.HEAD).getTarget().getName());
			assertNull(repo.exactRef(Constants.HEAD).getObjectId());
		}
	}

	@Test
	public void conversionPreservesUnbornHead() throws Exception {
		File dir = new File(testRoot.getRoot(), "converted.git");
		try (FileRepository repo = new FileRepository(dir)) {
			repo.create(true);
			repo.updateRef(Constants.HEAD).link("refs/heads/prototype");
			assertNotNull(repo.exactRef(Constants.HEAD));
			repo.convertRefStorage("reftable", true, false);
		}
		try (Repository reopened = reopen(dir)) {
			assertTrue(
					reopened.getRefDatabase() instanceof FileReftableDatabase);
			assertNotNull("Conversion must preserve unborn HEAD",
					reopened.exactRef(Constants.HEAD));
			assertEquals("refs/heads/prototype",
					reopened.exactRef(Constants.HEAD).getTarget().getName());
		}
	}

	@Test
	public void populatedConversionReopensWithoutGlobalConfig()
			throws Exception {
		File dir = new File(testRoot.getRoot(), "populated.git");
		try (FileRepository repo = new FileRepository(dir)) {
			repo.create(true);
			new TestRepository<>(repo).branch("refs/heads/prototype").commit()
					.create();
			repo.updateRef(Constants.HEAD).link("refs/heads/prototype");
			repo.convertRefStorage("reftable", true, false);
		}
		try (Repository reopened = reopen(dir)) {
			assertTrue(
					reopened.getRefDatabase() instanceof FileReftableDatabase);
			assertEquals("refs/heads/prototype",
					reopened.exactRef(Constants.HEAD).getTarget().getName());
		}
	}

	@Test
	public void conversionWithBackupKeepsOldStorage() throws Exception {
		File dir = new File(testRoot.getRoot(), "backup.git");
		try (FileRepository repo = new FileRepository(dir)) {
			repo.create(true);
			new TestRepository<>(repo).branch("refs/heads/prototype").commit()
					.create();
			repo.convertRefStorage("reftable", true, true);
		}
		assertTrue("conversion with backup keeps the old refs directory",
				new File(dir, "refs.old").exists());
		try (Repository reopened = reopen(dir)) {
			assertTrue(
					reopened.getRefDatabase() instanceof FileReftableDatabase);
		}
	}

	@Test
	public void convertRefStorageAcceptsLegacyRefdirAlias() throws Exception {
		File dir = new File(testRoot.getRoot(), "legacy.git");
		try (FileRepository repo = new FileRepository(dir)) {
			repo.create(true);
			new TestRepository<>(repo).branch("refs/heads/prototype").commit()
					.create();
			repo.convertRefStorage("reftable", true, false);
		}
		// "refdir" is a pre-existing public API alias for the native "files"
		// name; existing callers still pass it, so it must keep converting to
		// the files backend.
		try (FileRepository repo = (FileRepository) RepositoryCache.FileKey
				.exact(dir, FS.DETECTED).open(true)) {
			repo.convertRefStorage("refdir", true, false);
		}
		try (Repository reopened = reopen(dir)) {
			assertTrue(reopened.getRefDatabase() instanceof RefDirectory);
		}
	}

	@Test
	public void convertRefStorageAcceptsEnum() throws Exception {
		File dir = new File(testRoot.getRoot(), "enum-convert.git");
		try (FileRepository repo = new FileRepository(dir)) {
			repo.create(true);
			new TestRepository<>(repo).branch("refs/heads/prototype").commit()
					.create();
			repo.convertRefStorage(RefStorageFormat.REFTABLE, true, false);
		}
		try (FileRepository repo = (FileRepository) RepositoryCache.FileKey
				.exact(dir, FS.DETECTED).open(true)) {
			assertTrue(repo.getRefDatabase() instanceof FileReftableDatabase);
			repo.convertRefStorage(RefStorageFormat.FILES, true, false);
		}
		try (Repository reopened = reopen(dir)) {
			assertTrue(reopened.getRefDatabase() instanceof RefDirectory);
		}
	}

	@Test
	public void refStorageFormatConfigEnumRoundTrips() {
		assertEquals("reftable", RefStorageFormat.REFTABLE.toConfigValue());
		assertEquals("files", RefStorageFormat.FILES.toConfigValue());
		assertTrue(RefStorageFormat.FILES.matchConfigValue("files"));
		assertEquals(RefStorageFormat.REFTABLE,
				RefStorageFormat.fromConfigValue("reftable"));
		assertNull(RefStorageFormat.fromConfigValue("refdir"));
		assertNull(RefStorageFormat.fromConfigValue(null));
	}

	private static Repository reopen(File dir) throws IOException {
		return RepositoryCache.FileKey.exact(dir, FS.DETECTED).open(true);
	}
}
