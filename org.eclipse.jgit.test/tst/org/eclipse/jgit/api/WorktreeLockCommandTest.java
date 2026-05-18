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

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for WorktreeLockCommand and WorktreeUnlockCommand.
 */
public class WorktreeLockCommandTest extends RepositoryTestCase {

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
	public void testWorktreeLock() throws Exception {
		try (Git git = new Git(db)) {
			File wtDir = new File(trash.getParentFile(), "wt1"); //$NON-NLS-1$
			git.worktreeAdd().setPath(wtDir.getAbsolutePath())
					.setBranch("branch1").call(); //$NON-NLS-1$

			git.worktreeLock().setPath(wtDir.getAbsolutePath()).setReason("manual lock").call();

			File mainGitDir = db.getCommonDirectory();
			File lockFile = new File(mainGitDir, "worktrees/wt1/locked");
			assertTrue("Lock file should exist", lockFile.exists());
			assertEquals("manual lock", new String(Files.readAllBytes(lockFile.toPath())).trim());
		}
	}

	@Test
	public void testWorktreeUnlock() throws Exception {
		try (Git git = new Git(db)) {
			File wtDir = new File(trash.getParentFile(), "wt_unlock"); //$NON-NLS-1$
			git.worktreeAdd().setPath(wtDir.getAbsolutePath())
					.setBranch("branch1").setLock(true).setLockReason("locked").call(); //$NON-NLS-1$

			File mainGitDir = db.getCommonDirectory();
			File lockFile = new File(mainGitDir, "worktrees/wt_unlock/locked");
			assertTrue("Lock file should exist", lockFile.exists());

			git.worktreeUnlock().setPath(wtDir.getAbsolutePath()).call();

			assertTrue("Lock file should be deleted", !lockFile.exists());
		}
	}
}
