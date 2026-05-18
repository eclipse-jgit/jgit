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
import static org.junit.Assert.assertFalse;
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
 * Tests for WorktreeAddCommand.
 */
public class WorktreeAddCommandTest extends RepositoryTestCase {

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
	public void testWorktreeAdd() throws Exception {
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

			File adminDir = new File(db.getCommonDirectory(), "worktrees/" + wtDir.getName());
			File commondirFile = new File(adminDir, "commondir");
			assertTrue("commondir file should exist", commondirFile.exists());
			assertEquals("../..", new String(Files.readAllBytes(commondirFile.toPath())).trim());
		}
	}

	@Test
	public void testWorktreeAddDetach() throws Exception {
		try (Git git = new Git(db)) {
			File wtDir = new File(trash.getParentFile(), "wt_detach"); //$NON-NLS-1$
			git.worktreeAdd().setPath(wtDir.getAbsolutePath())
					.setBranch("branch1").setDetach(true).call(); //$NON-NLS-1$

			Collection<Worktree> worktrees = git.worktreeList().call();
			assertEquals(2, worktrees.size());

			Worktree target = null;
			for (Worktree wt : worktrees) {
				if (wt.getPath().getAbsoluteFile().equals(wtDir.getAbsoluteFile())) {
					target = wt;
					break;
				}
			}
			assertTrue("New worktree not found", target != null);
			assertEquals(null, target.getBranch());
		}
	}

	@Test
	public void testWorktreeAddLock() throws Exception {
		try (Git git = new Git(db)) {
			File wtDir = new File(trash.getParentFile(), "wt_lock"); //$NON-NLS-1$
			git.worktreeAdd().setPath(wtDir.getAbsolutePath())
					.setBranch("branch1").setLock(true).setLockReason("testing lock").call(); //$NON-NLS-1$

			File mainGitDir = db.getCommonDirectory();
			File lockFile = new File(mainGitDir, "worktrees/wt_lock/locked");
			assertTrue("Lock file should exist", lockFile.exists());
			assertEquals("testing lock", new String(Files.readAllBytes(lockFile.toPath())).trim());
		}
	}

	@Test
	public void testWorktreeAddNewBranch() throws Exception {
		try (Git git = new Git(db)) {
			File wtDir = new File(trash.getParentFile(), "wt_new_branch"); //$NON-NLS-1$
			git.worktreeAdd().setPath(wtDir.getAbsolutePath())
					.setNewBranch("newbranch").call(); //$NON-NLS-1$

			Collection<Worktree> worktrees = git.worktreeList().call();
			assertEquals(2, worktrees.size());

			Worktree target = null;
			for (Worktree wt : worktrees) {
				if (wt.getPath().getAbsoluteFile().equals(wtDir.getAbsoluteFile())) {
					target = wt;
					break;
				}
			}
			assertTrue("New worktree not found", target != null);
			assertEquals("refs/heads/newbranch", target.getBranch()); //$NON-NLS-1$
		}
	}

	@Test
	public void testWorktreeAddOmittedBranch() throws Exception {
		try (Git git = new Git(db)) {
			File wtDir = new File(trash.getParentFile(), "wt_derived"); //$NON-NLS-1$
			git.worktreeAdd().setPath(wtDir.getAbsolutePath()).call();

			Collection<Worktree> worktrees = git.worktreeList().call();
			assertEquals(2, worktrees.size());

			Worktree target = null;
			for (Worktree wt : worktrees) {
				if (wt.getPath().getAbsoluteFile().equals(wtDir.getAbsoluteFile())) {
					target = wt;
					break;
				}
			}
			assertTrue(target != null);
			assertEquals("refs/heads/wt_derived", target.getBranch());
		}
	}

	@Test
	public void testWorktreeAddGuessRemote() throws Exception {
		try (Git git = new Git(db)) {
			org.eclipse.jgit.lib.RefUpdate ru = db.updateRef("refs/remotes/origin/wt_remote");
			ru.setNewObjectId(db.resolve(Constants.HEAD));
			ru.update();

			File wtDir = new File(trash.getParentFile(), "wt_remote");
			git.worktreeAdd().setPath(wtDir.getAbsolutePath()).call();

			Collection<Worktree> worktrees = git.worktreeList().call();
			assertEquals(2, worktrees.size());

			Worktree target = null;
			for (Worktree wt : worktrees) {
				if (wt.getPath().getAbsoluteFile().equals(wtDir.getAbsoluteFile())) {
					target = wt;
					break;
				}
			}
			assertTrue(target != null);
			assertEquals("refs/heads/wt_remote", target.getBranch());
		}
	}

	@Test
	public void testWorktreeAddForce() throws Exception {
		try (Git git = new Git(db)) {
			File wtDir = new File(trash.getParentFile(), "wt_force"); //$NON-NLS-1$
			FileUtils.mkdirs(wtDir, true);

			boolean failed = false;
			try {
				git.worktreeAdd().setPath(wtDir.getAbsolutePath()).setBranch("branch1").call();
			} catch (IllegalStateException e) {
				failed = true;
				assertTrue(e.getMessage().contains("Directory already exists"));
			}
			assertTrue("Should fail when directory exists", failed);

			// Should succeed with force
			git.worktreeAdd().setPath(wtDir.getAbsolutePath()).setBranch("branch1").setForce(true).call();
			assertEquals(2, git.worktreeList().call().size());
		}
	}

	@Test
	public void testWorktreeAddNoCheckout() throws Exception {
		try (Git git = new Git(db)) {
			File wtDir = new File(trash.getParentFile(), "wt_no_checkout"); //$NON-NLS-1$
			writeTrashFile("file.txt", "content");
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("add file").call();

			git.worktreeAdd().setPath(wtDir.getAbsolutePath()).setBranch("master").setCheckout(false).call();

			File checkFile = new File(wtDir, "file.txt");
			assertFalse("File should not be checked out", checkFile.exists());
		}
	}

	@Test
	public void testWorktreeAddOrphan() throws Exception {
		try (Git git = new Git(db)) {
			File wtDir = new File(trash.getParentFile(), "wt_orphan"); //$NON-NLS-1$
			git.worktreeAdd().setPath(wtDir.getAbsolutePath()).setOrphan(true).call();

			Collection<Worktree> worktrees = git.worktreeList().call();
			Worktree target = null;
			for (Worktree wt : worktrees) {
				if (wt.getPath().getAbsoluteFile().equals(wtDir.getAbsoluteFile())) {
					target = wt;
					break;
				}
			}
			assertTrue(target != null);
			assertEquals("refs/heads/wt_orphan", target.getBranch());
		}
	}

	@Test
	public void testWorktreeAddPreviousBranch() throws Exception {
		try (Git git = new Git(db)) {
			git.branchCreate().setName("branch2").call();
			git.checkout().setName("branch1").call();
			git.checkout().setName("branch2").call();

			File wtDir = new File(trash.getParentFile(), "wt_previous"); //$NON-NLS-1$
			git.worktreeAdd().setPath(wtDir.getAbsolutePath()).setBranch("-").call();

			Collection<Worktree> worktrees = git.worktreeList().call();
			Worktree target = null;
			for (Worktree wt : worktrees) {
				if (wt.getPath().getAbsoluteFile().equals(wtDir.getAbsoluteFile())) {
					target = wt;
					break;
				}
			}
			assertTrue(target != null);
			assertEquals("refs/heads/branch1", target.getBranch());
		}
	}
}
