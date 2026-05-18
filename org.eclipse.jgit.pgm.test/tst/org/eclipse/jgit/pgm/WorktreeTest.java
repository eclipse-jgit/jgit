/*
 * Copyright (C) 2011, GitHub Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.util.Arrays;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for worktree command.
 */
public class WorktreeTest extends CLIRepositoryTestCase {

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call(); //$NON-NLS-1$
			git.branchCreate().setName("branch1").call(); //$NON-NLS-1$
		}
	}

	@Test
	public void testList() throws Exception {
		String[] result = execute("git worktree list"); //$NON-NLS-1$
		result = Arrays.stream(result).filter(s -> !s.isEmpty()).toArray(String[]::new);
		assertEquals(1, result.length);
		assertTrue(result[0].contains("master")); //$NON-NLS-1$
	}

	@Test
	public void testAdd() throws Exception {
		File wtDir = new File(db.getWorkTree().getParentFile(), "wt1"); //$NON-NLS-1$
		execute("git worktree add " + wtDir.getAbsolutePath() + " branch1"); //$NON-NLS-1$ //$NON-NLS-2$

		String[] result = execute("git worktree list"); //$NON-NLS-1$
		result = Arrays.stream(result).filter(s -> !s.isEmpty()).toArray(String[]::new);
		assertEquals(2, result.length);

		boolean found = false;
		for (String line : result) {
			if (line.contains(wtDir.getAbsolutePath())) {
				found = true;
			}
		}
		assertTrue("New worktree not found in list", found); //$NON-NLS-1$
	}

	@Test
	public void testListPorcelain() throws Exception {
		File wtDir = new File(db.getWorkTree().getParentFile(), "wt_porcelain"); //$NON-NLS-1$
		execute("git worktree add " + wtDir.getAbsolutePath() + " branch1"); //$NON-NLS-1$ //$NON-NLS-2$

		String[] result = execute("git worktree list --porcelain"); //$NON-NLS-1$
		result = Arrays.stream(result).filter(s -> !s.isEmpty()).toArray(String[]::new);

		assertTrue("Output should contain worktree label", Arrays.stream(result).anyMatch(s -> s.startsWith("worktree " + wtDir.getAbsolutePath())));
		assertTrue("Output should contain branch label", Arrays.stream(result).anyMatch(s -> s.startsWith("branch refs/heads/branch1")));
	}

	@Test
	public void testListLocked() throws Exception {
		File wtDir = new File(db.getWorkTree().getParentFile(), "wt_locked"); //$NON-NLS-1$
		execute("git worktree add --lock --reason testlock " + wtDir.getAbsolutePath() + " branch1"); //$NON-NLS-1$ //$NON-NLS-2$

		String[] result = execute("git worktree list"); //$NON-NLS-1$
		result = Arrays.stream(result).filter(s -> !s.isEmpty()).toArray(String[]::new);
		assertTrue("Default output should contain locked annotation", Arrays.stream(result).anyMatch(s -> s.contains(wtDir.getAbsolutePath()) && s.contains(" locked")));

		String[] verboseResult = execute("git worktree list -v"); //$NON-NLS-1$
		verboseResult = Arrays.stream(verboseResult).filter(s -> !s.isEmpty()).toArray(String[]::new);
		assertTrue("Verbose output should contain lock reason", Arrays.stream(verboseResult).anyMatch(s -> s.contains("\tlocked: testlock")));
	}

	@Test
	public void testLockCli() throws Exception {
		File wtDir = new File(db.getWorkTree().getParentFile(), "wt_lock_cli"); //$NON-NLS-1$
		execute("git worktree add " + wtDir.getAbsolutePath() + " branch1"); //$NON-NLS-1$ //$NON-NLS-2$

		execute("git worktree lock --reason clilock " + wtDir.getAbsolutePath()); //$NON-NLS-1$

		String[] result = execute("git worktree list -v"); //$NON-NLS-1$
		result = Arrays.stream(result).filter(s -> !s.isEmpty()).toArray(String[]::new);
		assertTrue("Should contain lock reason in verbose list", Arrays.stream(result).anyMatch(s -> s.contains("\tlocked: clilock")));
	}

	@Test
	public void testAddCheckoutCli() throws Exception {
		File wtDir = new File(db.getWorkTree().getParentFile(), "wt_checkout_cli"); //$NON-NLS-1$
		try (Git git = new Git(db)) {
			writeTrashFile("file2.txt", "content2");
			git.add().addFilepattern("file2.txt").call();
			git.commit().setMessage("add file2").call();
		}

		execute("git worktree add --no-checkout " + wtDir.getAbsolutePath() + " master"); //$NON-NLS-1$

		File checkFile = new File(wtDir, "file2.txt");
		assertFalse("File should not be checked out with --no-checkout", checkFile.exists());
	}
}
