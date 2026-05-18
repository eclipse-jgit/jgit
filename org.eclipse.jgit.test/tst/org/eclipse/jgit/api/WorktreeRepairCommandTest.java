/*
 * Copyright (C) 2011, GitHub Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Collection;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for WorktreeRepairCommand.
 */
public class WorktreeRepairCommandTest extends RepositoryTestCase {

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();

		try (Git git = new Git(db)) {
			git.commit().setMessage("Initial commit").call(); //$NON-NLS-1$
			git.branchCreate().setName("branch1").call(); //$NON-NLS-1$
		}
	}

	@Test
	public void testWorktreeRepair() throws Exception {
		try (Git git = new Git(db)) {
			File wtDir = new File(trash.getParentFile(), "wt_repair"); //$NON-NLS-1$
			git.worktreeAdd().setPath(wtDir.getAbsolutePath()).setBranch("branch1").call();

			File wtDotGit = new File(wtDir, Constants.DOT_GIT);
			File adminDir = new File(db.getCommonDirectory(), "worktrees/wt_repair");
			File adminGitdir = new File(adminDir, "gitdir");

			File movedWtDir = new File(trash.getParentFile(), "wt_repair_moved");
			FileUtils.rename(wtDir, movedWtDir);

			assertEquals(wtDotGit.getAbsolutePath(), new String(Files.readAllBytes(adminGitdir.toPath())).trim());

			Collection<String> repaired = git.worktreeRepair().addPath(movedWtDir.getAbsolutePath()).call();
			assertEquals(1, repaired.size());
			assertTrue(repaired.contains(movedWtDir.getAbsolutePath()));

			File movedWtDotGit = new File(movedWtDir, Constants.DOT_GIT);
			assertEquals(movedWtDotGit.getAbsolutePath(), new String(Files.readAllBytes(adminGitdir.toPath())).trim());
		}
	}
}
