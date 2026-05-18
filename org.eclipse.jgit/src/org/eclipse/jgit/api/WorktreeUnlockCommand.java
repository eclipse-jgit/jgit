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
import java.text.MessageFormat;
import java.util.Collection;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;

/**
 * A class used to execute a {@code worktree unlock} command.
 *
 * @since 7.7
 */
public class WorktreeUnlockCommand extends GitCommand<Boolean> {
	private String path;

	/**
	 * Constructor for WorktreeUnlockCommand.
	 *
	 * @param repo
	 *            the repository
	 */
	protected WorktreeUnlockCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Set the path to the worktree to unlock.
	 *
	 * @param path
	 *            path to the worktree
	 * @return {@code this}
	 */
	public WorktreeUnlockCommand setPath(String path) {
		this.path = path;
		return this;
	}

	@Override
	public Boolean call() throws GitAPIException {
		checkCallable();
		if (path == null) {
			throw new IllegalArgumentException(JGitText.get().worktreePathRequired);
		}

		File worktreeDir = new File(path);
		if (!worktreeDir.exists()) {
			throw new IllegalStateException(MessageFormat.format(JGitText.get().worktreeDirectoryDoesNotExist, path));
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
			throw new IllegalStateException(MessageFormat.format(JGitText.get().worktreeNotValid, path));
		}

		// Main worktree cannot be unlocked
		if (target.getPath().equals(repo.getWorkTree())) {
			throw new IllegalStateException(JGitText.get().worktreeCannotUnlockMain);
		}

		File specificAdminDir;
		try {
			specificAdminDir = WorktreeUtil.findAdminDir(repo, worktreeDir);
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}

		if (specificAdminDir == null) {
			throw new IllegalStateException(
					MessageFormat.format(JGitText.get().worktreeAdminDirNotFound, path));
		}

		File lockedFile = new File(specificAdminDir, "locked"); //$NON-NLS-1$
		if (!lockedFile.exists()) {
			throw new IllegalStateException(JGitText.get().worktreeNotLocked);
		}

		try {
			Files.delete(lockedFile.toPath());
			return Boolean.TRUE;
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}
}
