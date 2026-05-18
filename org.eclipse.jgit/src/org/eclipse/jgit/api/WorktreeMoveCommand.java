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
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FileUtils;

/**
 * A class used to execute a {@code worktree move} command.
 */
public class WorktreeMoveCommand extends GitCommand<Boolean> {
	private String path;
	private String newPath;

	/**
	 * Constructor for WorktreeMoveCommand.
	 *
	 * @param repo
	 *            the repository
	 */
	protected WorktreeMoveCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Set the path to the worktree to move.
	 *
	 * @param path
	 *            path to the worktree
	 * @return {@code this}
	 */
	public WorktreeMoveCommand setPath(String path) {
		this.path = path;
		return this;
	}

	/**
	 * Set the new path for the worktree.
	 *
	 * @param newPath
	 *            new path for the worktree
	 * @return {@code this}
	 */
	public WorktreeMoveCommand setNewPath(String newPath) {
		this.newPath = newPath;
		return this;
	}

	@Override
	public Boolean call() throws GitAPIException {
		checkCallable();
		if (path == null || newPath == null) {
			throw new IllegalArgumentException(
					JGitText.get().worktreePathAndNewPathRequired);
		}

		File worktreeDir = new File(path);
		File newWorktreeDir = new File(newPath);

		if (!worktreeDir.exists()) {
			throw new IllegalStateException(MessageFormat.format(JGitText.get().worktreeDirectoryDoesNotExist, path));
		}
		if (newWorktreeDir.exists()) {
			throw new IllegalStateException(
					MessageFormat.format(JGitText.get().worktreeDestinationExists, newPath));
		}

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

		// Main worktree cannot be moved
		if (target.getPath().equals(repo.getWorkTree())) {
			throw new IllegalStateException(JGitText.get().worktreeCannotMoveMain);
		}

		if (target.isLocked()) {
			throw new IllegalStateException(JGitText.get().worktreeMoveLocked);
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

		try {
			// Move worktree directory
			FileUtils.rename(worktreeDir, newWorktreeDir);

			// Update .git file in new worktree location
			File dotGitFile = new File(newWorktreeDir, Constants.DOT_GIT);
			String gitdirContent = "gitdir: " //$NON-NLS-1$
					+ specificAdminDir.getAbsolutePath();
			Files.write(dotGitFile.toPath(), gitdirContent.getBytes());

			// Update gitdir file in admin dir
			File gitdirAdminFile = new File(specificAdminDir, "gitdir"); //$NON-NLS-1$
			Files.write(gitdirAdminFile.toPath(),
					dotGitFile.getAbsolutePath().getBytes());

			return Boolean.TRUE;
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}
}
