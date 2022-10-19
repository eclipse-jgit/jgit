/*
 * Copyright (C) 2020 Thomas Wolf <thomas.wolf@paranor.ch> and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.dircache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.EnumSet;
import java.util.Set;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.dircache.DirCache.DirCacheVersion;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.SystemReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for initial DirCache version after a clone or after a mixed or hard
 * reset.
 */
public class DirCacheAfterCloneTest extends RepositoryTestCase {

	@Override
	@BeforeEach
	public void setUp() throws Exception {
		super.setUp();
		try (Git git = new Git(db)) {
			writeTrashFile("Test.txt", "Hello world");
			git.add().addFilepattern("Test.txt").call();
			git.commit().setMessage("Initial commit").call();
		}
	}

	private DirCacheVersion cloneAndCheck(Set<DirCacheVersion> expected)
			throws Exception {
		File directory = createTempDirectory("testCloneRepository");
		CloneCommand command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setURI("file://" + db.getWorkTree().getAbsolutePath());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertNotNull(git2);
		DirCache dc = DirCache.read(git2.getRepository());
		DirCacheVersion version = dc.getVersion();
		assertTrue(expected.contains(version));
		return version;
	}

	@Test
	void testCloneV3OrV2() throws Exception {
		cloneAndCheck(EnumSet.of(DirCacheVersion.DIRC_VERSION_MINIMUM,
				DirCacheVersion.DIRC_VERSION_EXTENDED));
	}

	@Test
	void testCloneV4() throws Exception {
		StoredConfig cfg = SystemReader.getInstance().getUserConfig();
		cfg.load();
		cfg.setInt("index", null, "version", 4);
		cfg.save();
		cloneAndCheck(EnumSet.of(DirCacheVersion.DIRC_VERSION_PATHCOMPRESS));
	}

	@Test
	void testCloneV4manyFiles() throws Exception {
		StoredConfig cfg = SystemReader.getInstance().getUserConfig();
		cfg.load();
		cfg.setBoolean("feature", null, "manyFiles", true);
		cfg.save();
		cloneAndCheck(EnumSet.of(DirCacheVersion.DIRC_VERSION_PATHCOMPRESS));
	}

	@Test
	void testCloneV3CommitNoVersionChange() throws Exception {
		DirCacheVersion initial = cloneAndCheck(
				EnumSet.of(DirCacheVersion.DIRC_VERSION_MINIMUM,
						DirCacheVersion.DIRC_VERSION_EXTENDED));
		StoredConfig cfg = db.getConfig();
		cfg.setInt("index", null, "version", 4);
		cfg.save();
		try (Git git = new Git(db)) {
			writeTrashFile("Test.txt2", "Hello again");
			git.add().addFilepattern("Test.txt2").call();
			git.commit().setMessage("Second commit").call();
		}
		assertEquals(initial,
				DirCache.read(db).getVersion(),
				"DirCache version should be unchanged");
	}

	@Test
	void testCloneV3ResetHardVersionChange() throws Exception {
		cloneAndCheck(EnumSet.of(DirCacheVersion.DIRC_VERSION_MINIMUM,
				DirCacheVersion.DIRC_VERSION_EXTENDED));
		StoredConfig cfg = db.getConfig();
		cfg.setInt("index", null, "version", 4);
		cfg.save();
		FileUtils.delete(new File(db.getDirectory(), "index"));
		try (Git git = new Git(db)) {
			git.reset().setMode(ResetType.HARD).call();
		}
		assertEquals(DirCacheVersion.DIRC_VERSION_PATHCOMPRESS,
				DirCache.read(db).getVersion(),
				"DirCache version should have changed");
	}

	@Test
	void testCloneV3ResetMixedVersionChange() throws Exception {
		cloneAndCheck(EnumSet.of(DirCacheVersion.DIRC_VERSION_MINIMUM,
				DirCacheVersion.DIRC_VERSION_EXTENDED));
		StoredConfig cfg = db.getConfig();
		cfg.setInt("index", null, "version", 4);
		cfg.save();
		FileUtils.delete(new File(db.getDirectory(), "index"));
		try (Git git = new Git(db)) {
			git.reset().setMode(ResetType.MIXED).call();
		}
		assertEquals(DirCacheVersion.DIRC_VERSION_PATHCOMPRESS,
				DirCache.read(db).getVersion(),
				"DirCache version should have changed");
	}
}
