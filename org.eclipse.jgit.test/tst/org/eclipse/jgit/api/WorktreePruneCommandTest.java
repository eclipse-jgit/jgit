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
import java.util.Collection;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for WorktreePruneCommand.
 */
public class WorktreePruneCommandTest extends RepositoryTestCase {

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
	public void testWorktreePrune() throws Exception {
		try (Git git = new Git(db)) {
			File wtDir = new File(trash.getParentFile(), "wt_prune"); //$NON-NLS-1$
			git.worktreeAdd().setPath(wtDir.getAbsolutePath())
					.setBranch("branch1").call(); //$NON-NLS-1$

			assertEquals(2, git.worktreeList().call().size());

			FileUtils.delete(wtDir, FileUtils.RECURSIVE);

			Collection<String> pruned = git.worktreePrune().call();
			assertEquals(1, pruned.size());
			assertTrue(pruned.contains("wt_prune"));

			assertEquals(1, git.worktreeList().call().size());
		}
	}

	@Test
	public void testWorktreePruneExpire() throws Exception {
		try (Git git = new Git(db)) {
			File wtDir1 = new File(trash.getParentFile(), "wt_expire1"); //$NON-NLS-1$
			File wtDir2 = new File(trash.getParentFile(), "wt_expire2"); //$NON-NLS-1$
			git.worktreeAdd().setPath(wtDir1.getAbsolutePath()).setBranch("branch1").call();
			git.worktreeAdd().setPath(wtDir2.getAbsolutePath()).setBranch("branch1").setForce(true).call();

			assertEquals(3, git.worktreeList().call().size());

			FileUtils.delete(wtDir1, FileUtils.RECURSIVE);
			FileUtils.delete(wtDir2, FileUtils.RECURSIVE);

			File adminDir1 = new File(db.getCommonDirectory(), "worktrees/wt_expire1");
			File adminDir2 = new File(db.getCommonDirectory(), "worktrees/wt_expire2");

			long now = System.currentTimeMillis();
			long oneHourAgo = now - 3600 * 1000;
			adminDir1.setLastModified(oneHourAgo - 1000);
			adminDir2.setLastModified(now);

			java.time.Instant expireTime = java.time.Instant.ofEpochMilli(oneHourAgo);

			Collection<String> pruned = git.worktreePrune().setExpireTime(expireTime).call();
			assertEquals(1, pruned.size());
			assertTrue(pruned.contains("wt_expire1"));

			assertEquals(2, git.worktreeList().call().size());
		}
	}

	@Test
	public void testWorktreePruneDryRun() throws Exception {
		try (Git git = new Git(db)) {
			File wtDir = new File(trash.getParentFile(), "wt_prune_dry"); //$NON-NLS-1$
			git.worktreeAdd().setPath(wtDir.getAbsolutePath())
					.setBranch("branch1").call(); //$NON-NLS-1$

			assertEquals(2, git.worktreeList().call().size());

			FileUtils.delete(wtDir, FileUtils.RECURSIVE);

			Collection<String> pruned = git.worktreePrune().setDryRun(true).call();
			assertEquals(1, pruned.size());
			assertTrue(pruned.contains("wt_prune_dry"));

			assertEquals(2, git.worktreeList().call().size());

			git.worktreePrune().call();
			assertEquals(1, git.worktreeList().call().size());
		}
	}
}
