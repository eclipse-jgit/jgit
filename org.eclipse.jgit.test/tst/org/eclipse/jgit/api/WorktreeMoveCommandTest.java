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
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for WorktreeMoveCommand.
 */
public class WorktreeMoveCommandTest extends RepositoryTestCase {

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
	public void testWorktreeMove() throws Exception {
		try (Git git = new Git(db)) {
			File wtDir = new File(trash.getParentFile(), "wt1"); //$NON-NLS-1$
			File newWtDir = new File(trash.getParentFile(), "wt2"); //$NON-NLS-1$
			git.worktreeAdd().setPath(wtDir.getAbsolutePath())
					.setBranch("branch1").call(); //$NON-NLS-1$

			git.worktreeMove().setPath(wtDir.getAbsolutePath())
					.setNewPath(newWtDir.getAbsolutePath()).call();

			Collection<Worktree> worktrees = git.worktreeList().call();
			assertEquals(2, worktrees.size());

			boolean found = false;
			for (Worktree wt : worktrees) {
				if (wt.getPath().getAbsoluteFile()
						.equals(newWtDir.getAbsoluteFile())) {
					found = true;
				}
			}
			assertTrue("Moved worktree not found in list", found); //$NON-NLS-1$
		}
	}

	@Test
	public void testWorktreeMoveLocked() throws Exception {
		try (Git git = new Git(db)) {
			File wtDir = new File(trash.getParentFile(), "wt_move_locked"); //$NON-NLS-1$
			File newWtDir = new File(trash.getParentFile(), "wt_move_locked_new"); //$NON-NLS-1$
			git.worktreeAdd().setPath(wtDir.getAbsolutePath()).setBranch("branch1").setLock(true).call();

			boolean failed = false;
			try {
				git.worktreeMove().setPath(wtDir.getAbsolutePath()).setNewPath(newWtDir.getAbsolutePath()).call();
			} catch (IllegalStateException e) {
				failed = true;
				assertTrue(e.getMessage().contains("locked"));
			}
			assertTrue("Should fail when moving locked worktree", failed);
		}
	}
}
