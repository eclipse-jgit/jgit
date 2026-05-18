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
import java.text.MessageFormat;
import java.util.Collection;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FileUtils;

/**
 * A class used to execute a {@code worktree remove} command.
 */
public class WorktreeRemoveCommand extends GitCommand<Boolean> {
	private String path;
	private boolean force;
	private boolean forceLocked;

	/**
	 * Constructor for WorktreeRemoveCommand.
	 *
	 * @param repo
	 *            the repository
	 */
	protected WorktreeRemoveCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Set the path to the worktree to remove.
	 *
	 * @param path
	 *            path to the worktree
	 * @return {@code this}
	 */
	public WorktreeRemoveCommand setPath(String path) {
		this.path = path;
		return this;
	}

	/**
	 * Set whether to force removal even if unclean.
	 *
	 * @param force
	 *            true to force removal
	 * @return {@code this}
	 * @since 7.7
	 */
	public WorktreeRemoveCommand setForce(boolean force) {
		this.force = force;
		return this;
	}

	/**
	 * Set whether to force removal of locked worktree.
	 *
	 * @param forceLocked
	 *            true to force removal of locked
	 * @return {@code this}
	 * @since 7.7
	 */
	public WorktreeRemoveCommand setForceLocked(boolean forceLocked) {
		this.forceLocked = forceLocked;
		return this;
	}

	@Override
	public Boolean call() throws GitAPIException {
		checkCallable();
		if (path == null) {
			throw new IllegalArgumentException(JGitText.get().worktreePathRequired);
		}

		File worktreeDir = new File(path);

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

		if (target.isBare() || target.getPath().equals(repo.getWorkTree())) {
			throw new IllegalStateException(JGitText.get().worktreeCannotRemoveMain);
		}

		if (target.isLocked() && !forceLocked) {
			throw new IllegalStateException(JGitText.get().worktreeRemoveLocked);
		}

		File specificAdminDir;
		try {
			specificAdminDir = WorktreeUtil.findAdminDir(repo, worktreeDir);
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}

		if (specificAdminDir == null) {
			throw new IllegalStateException(MessageFormat.format(JGitText.get().worktreeAdminDirNotFound, path));
		}

		if (!worktreeDir.exists() && !force) {
			throw new IllegalStateException(JGitText.get().worktreeRemoveWorkingDirDoesNotExist);
		}

		if (worktreeDir.exists() && !force) {
			try {
				Repository wtRepo = new BaseRepositoryBuilder().setFS(repo.getFS()).setGitDir(specificAdminDir).setGitCommonDir(repo.getCommonDirectory()).setWorkTree(worktreeDir).build();
				try (Git git = new Git(wtRepo)) {
					Status status = git.status().call();
					if (!status.isClean()) {
						throw new IllegalStateException(JGitText.get().worktreeRemoveUnclean);
					}
				}
			} catch (IOException e) {
				throw new JGitInternalException(e.getMessage(), e);
			}
		}

		try {
			if (worktreeDir.exists()) {
				FileUtils.delete(worktreeDir, FileUtils.RECURSIVE);
			}
			if (specificAdminDir.exists()) {
				FileUtils.delete(specificAdminDir, FileUtils.RECURSIVE);
			}
			return Boolean.TRUE;
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}
}
