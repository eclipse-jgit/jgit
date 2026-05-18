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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.IO;

/**
 * A class used to execute a {@code worktree list} command.
 */
public class WorktreeListCommand extends GitCommand<Collection<Worktree>> {
	private java.time.Instant expireTime;

	/**
	 * Constructor for WorktreeListCommand.
	 *
	 * @param repo
	 *            the repository
	 */
	protected WorktreeListCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Set the expiration time for prunable check.
	 *
	 * @param expireTime
	 *            expiration time
	 * @return {@code this}
	 * @since 7.7
	 */
	public WorktreeListCommand setExpireTime(java.time.Instant expireTime) {
		this.expireTime = expireTime;
		return this;
	}

	@Override
	public Collection<Worktree> call() throws GitAPIException {
		checkCallable();
		List<Worktree> worktrees = new ArrayList<>();

		// Main worktree
		try {
			Worktree mainWt = new Worktree(repo.isBare() ? repo.getDirectory() : repo.getWorkTree(), repo.getBranch(), repo.resolve(Constants.HEAD));
			mainWt.setBare(repo.isBare());
			worktrees.add(mainWt);
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}

		// Linked worktrees
		File worktreesDir = new File(repo.getCommonDirectory(), "worktrees"); //$NON-NLS-1$
		if (worktreesDir.isDirectory()) {
			File[] dirs = worktreesDir.listFiles(File::isDirectory);
			if (dirs != null) {
				for (File dir : dirs) {
					try {
						Worktree wt = readWorktree(dir);
						if (wt != null) {
							worktrees.add(wt);
						}
					} catch (IOException e) {
						// Ignore broken worktrees as native git seems to do or just skip them
					}
				}
			}
		}

		return worktrees;
	}

	private Worktree readWorktree(File worktreeDir) throws IOException {
		File gitdirFile = new File(worktreeDir, "gitdir"); //$NON-NLS-1$
		if (!gitdirFile.isFile()) {
			return null;
		}

		String pathStr = new String(IO.readFully(gitdirFile)).trim();
		File dotGitFile = new File(pathStr);
		File worktreePath = dotGitFile.getParentFile();

		File headFile = new File(worktreeDir, Constants.HEAD);
		String branch = null;
		ObjectId commit = null;

		if (headFile.isFile()) {
			String headStr = new String(IO.readFully(headFile)).trim();
			if (headStr.startsWith("ref: ")) { //$NON-NLS-1$
				branch = headStr.substring(5);
				try {
					commit = repo.resolve(branch);
				} catch (IOException e) {
					// Ignore if cannot resolve
				}
			} else if (ObjectId.isId(headStr)) {
				commit = ObjectId.fromString(headStr);
			}
		}

		File lockedFile = new File(worktreeDir, "locked"); //$NON-NLS-1$
		boolean locked = lockedFile.exists();
		String lockReason = null;
		if (locked) {
			lockReason = new String(IO.readFully(lockedFile)).trim();
		}

		boolean prunable = false;
		String prunableReason = null;
		if (worktreePath != null && !worktreePath.exists()) {
			if (!locked) {
				if (expireTime == null || worktreeDir.lastModified() < expireTime.toEpochMilli()) {
					prunable = true;
					prunableReason = "gitdir file points to non-existent location"; //$NON-NLS-1$
				}
			}
		}

		Worktree wt = new Worktree(worktreePath != null ? worktreePath : dotGitFile, branch, commit);
		wt.setLocked(locked);
		wt.setLockReason(lockReason);
		wt.setPrunable(prunable);
		wt.setPrunableReason(prunableReason);
		return wt;
	}
}
