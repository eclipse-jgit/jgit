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
 * Tests for WorktreeListCommand.
 */
public class WorktreeListCommandTest extends RepositoryTestCase {

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
	public void testWorktreeList() throws Exception {
		try (Git git = new Git(db)) {
			File wtDir = new File(trash.getParentFile(), "wt1"); //$NON-NLS-1$
			git.worktreeAdd().setPath(wtDir.getAbsolutePath())
					.setBranch("branch1").call(); //$NON-NLS-1$

			Collection<Worktree> worktrees = git.worktreeList().call();
			assertEquals(2, worktrees.size()); // Main + wt1

			boolean found = false;
			for (Worktree wt : worktrees) {
				if (wt.getPath().getAbsoluteFile()
						.equals(wtDir.getAbsoluteFile())) {
					found = true;
					assertEquals("refs/heads/branch1", wt.getBranch()); //$NON-NLS-1$
				}
			}
			assertTrue("New worktree not found in list", found); //$NON-NLS-1$
		}
	}

	@Test
	public void testWorktreeListPrunable() throws Exception {
		try (Git git = new Git(db)) {
			File wtDir = new File(trash.getParentFile(), "wt_list_prunable"); //$NON-NLS-1$
			git.worktreeAdd().setPath(wtDir.getAbsolutePath())
					.setBranch("branch1").call(); //$NON-NLS-1$

			FileUtils.delete(wtDir, FileUtils.RECURSIVE);

			Collection<Worktree> worktrees = git.worktreeList().call();
			Worktree target = null;
			for (Worktree wt : worktrees) {
				if (wt.getPath().getAbsoluteFile().equals(wtDir.getAbsoluteFile())) {
					target = wt;
					break;
				}
			}
			assertTrue(target != null);
			assertTrue("Should be prunable", target.isPrunable());
			assertEquals("gitdir file points to non-existent location", target.getPrunableReason());
		}
	}
}
