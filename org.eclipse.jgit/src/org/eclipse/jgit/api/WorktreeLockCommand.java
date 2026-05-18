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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Repository;

/**
 * A class used to execute a {@code worktree lock} command.
 *
 * @since 7.7
 */
public class WorktreeLockCommand extends GitCommand<Boolean> {
	private String path;
	private String reason;

	/**
	 * Constructor for WorktreeLockCommand.
	 *
	 * @param repo
	 *            the repository
	 */
	protected WorktreeLockCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Set the path to the worktree to lock.
	 *
	 * @param path
	 *            path to the worktree
	 * @return {@code this}
	 */
	public WorktreeLockCommand setPath(String path) {
		this.path = path;
		return this;
	}

	/**
	 * Set the reason for locking.
	 *
	 * @param reason
	 *            reason for locking
	 * @return {@code this}
	 */
	public WorktreeLockCommand setReason(String reason) {
		this.reason = reason;
		return this;
	}

	@Override
	public Boolean call() throws GitAPIException {
		checkCallable();
		if (path == null) {
			throw new IllegalArgumentException("Path is required"); //$NON-NLS-1$
		}

		File worktreeDir = new File(path);
		if (!worktreeDir.exists()) {
			throw new IllegalStateException("Directory does not exist: " + path); //$NON-NLS-1$
		}

		// Find the worktree in the list to get its admin dir
		Collection<Worktree> worktrees = new WorktreeListCommand(repo).call();
		Worktree target = null;
		try {
			for (Worktree wt : worktrees) {
				if (wt.getPath().getCanonicalFile()
						.equals(worktreeDir.getCanonicalFile())) {
					target = wt;
					break;
				}
			}
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}

		if (target == null) {
			throw new IllegalStateException("Not a valid worktree: " + path); //$NON-NLS-1$
		}

		// Main worktree cannot be locked
		if (target.getPath().equals(repo.getWorkTree())) {
			throw new IllegalStateException("Cannot lock the main worktree"); //$NON-NLS-1$
		}

		File specificAdminDir;
		try {
			specificAdminDir = WorktreeUtil.findAdminDir(repo, worktreeDir);
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}

		if (specificAdminDir == null) {
			throw new IllegalStateException(
					"Could not find administrative directory for worktree: " //$NON-NLS-1$
							+ path);
		}

		File lockedFile = new File(specificAdminDir, "locked"); //$NON-NLS-1$
		if (lockedFile.exists()) {
			throw new IllegalStateException("Worktree is already locked"); //$NON-NLS-1$
		}

		try {
			String content = reason != null ? reason : ""; //$NON-NLS-1$
			Files.write(lockedFile.toPath(), content.getBytes());
			return Boolean.TRUE;
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}
}
