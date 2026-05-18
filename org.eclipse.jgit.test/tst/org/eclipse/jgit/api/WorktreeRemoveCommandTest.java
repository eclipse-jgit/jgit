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
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for WorktreeRemoveCommand.
 */
public class WorktreeRemoveCommandTest extends RepositoryTestCase {

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
	public void testWorktreeRemove() throws Exception {
		try (Git git = new Git(db)) {
			File wtDir = new File(trash.getParentFile(), "wt1"); //$NON-NLS-1$
			git.worktreeAdd().setPath(wtDir.getAbsolutePath())
					.setBranch("branch1").call(); //$NON-NLS-1$

			Collection<Worktree> worktrees = git.worktreeList().call();
			assertEquals(2, worktrees.size());

			git.worktreeRemove().setPath(wtDir.getAbsolutePath()).call();

			worktrees = git.worktreeList().call();
			assertEquals(1, worktrees.size());
			assertEquals(db.getWorkTree().getAbsoluteFile(), worktrees
					.iterator().next().getPath().getAbsoluteFile());
		}
	}

	@Test
	public void testWorktreeRemoveUnclean() throws Exception {
		try (Git git = new Git(db)) {
			File wtDir = new File(trash.getParentFile(), "wt_unclean"); //$NON-NLS-1$
			git.worktreeAdd().setPath(wtDir.getAbsolutePath()).setBranch("branch1").call();

			File untracked = new File(wtDir, "untracked.txt");
			Files.write(untracked.toPath(), "data".getBytes());

			boolean failed = false;
			try {
				git.worktreeRemove().setPath(wtDir.getAbsolutePath()).call();
			} catch (IllegalStateException e) {
				failed = true;
				assertTrue(e.getMessage().contains("not clean"));
			}
			assertTrue("Should fail on unclean worktree", failed);

			git.worktreeRemove().setPath(wtDir.getAbsolutePath()).setForce(true).call();
			assertTrue("Worktree should be removed", !wtDir.exists());
		}
	}

	@Test
	public void testWorktreeRemoveLocked() throws Exception {
		try (Git git = new Git(db)) {
			File wtDir = new File(trash.getParentFile(), "wt_rm_locked"); //$NON-NLS-1$
			git.worktreeAdd().setPath(wtDir.getAbsolutePath()).setBranch("branch1").call();
			git.worktreeLock().setPath(wtDir.getAbsolutePath()).setReason("locked").call();

			boolean failed = false;
			try {
				git.worktreeRemove().setPath(wtDir.getAbsolutePath()).call();
			} catch (IllegalStateException e) {
				failed = true;
				assertTrue(e.getMessage().contains("locked"));
			}
			assertTrue("Should fail on locked worktree", failed);

			git.worktreeRemove().setPath(wtDir.getAbsolutePath()).setForce(true).setForceLocked(true).call();
			assertTrue("Worktree should be removed", !wtDir.exists());
		}
	}

	@Test
	public void testWorktreeRemoveMainForbidden() throws Exception {
		try (Git git = new Git(db)) {
			boolean failed = false;
			try {
				git.worktreeRemove().setPath(db.getWorkTree().getAbsolutePath()).call();
			} catch (IllegalStateException e) {
				failed = true;
				assertTrue(e.getMessage().contains("Cannot remove the main worktree"));
			}
			assertTrue("Should fail when removing main worktree", failed);
		}
	}
}
