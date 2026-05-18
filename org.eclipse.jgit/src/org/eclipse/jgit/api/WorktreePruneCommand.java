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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FileUtils;

/**
 * A class used to execute a {@code worktree prune} command.
 *
 * @since 7.7
 */
public class WorktreePruneCommand extends GitCommand<Collection<String>> {
	private boolean dryRun;
	private java.time.Instant expireTime;

	/**
	 * Constructor for WorktreePruneCommand.
	 *
	 * @param repo
	 *            the repository
	 */
	protected WorktreePruneCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Set whether to perform a dry run.
	 *
	 * @param dryRun
	 *            true to perform a dry run
	 * @return {@code this}
	 */
	public WorktreePruneCommand setDryRun(boolean dryRun) {
		this.dryRun = dryRun;
		return this;
	}



	/**
	 * Set the expiration time.
	 *
	 * @param expireTime
	 *            expiration time
	 * @return {@code this}
	 */
	public WorktreePruneCommand setExpireTime(java.time.Instant expireTime) {
		this.expireTime = expireTime;
		return this;
	}

	@Override
	public Collection<String> call() throws GitAPIException {
		checkCallable();
		List<String> pruned = new ArrayList<>();

		File mainGitDir = repo.getCommonDirectory();
		File worktreesAdminDir = new File(mainGitDir, "worktrees"); //$NON-NLS-1$

		if (worktreesAdminDir.isDirectory()) {
			File[] dirs = worktreesAdminDir.listFiles(File::isDirectory);
			if (dirs != null) {
				for (File dir : dirs) {
					if (expireTime != null && dir.lastModified() > expireTime.toEpochMilli()) {
						continue;
					}

					try {
						File gitdirFile = new File(dir, "gitdir"); //$NON-NLS-1$
						if (!gitdirFile.isFile()) {
							// Broken admin dir, prune it
							pruneDir(dir, pruned);
							continue;
						}

						String pathStr = new String(
								Files.readAllBytes(gitdirFile.toPath()))
										.trim();
						File dotGitFile = new File(pathStr);
						File worktreeDir = dotGitFile.getParentFile();

						if (!worktreeDir.exists()) {
							// Check if locked
							File lockedFile = new File(dir, "locked"); //$NON-NLS-1$
							if (!lockedFile.exists()) {
								pruneDir(dir, pruned);
							}
						}
					} catch (IOException e) {
						// Ignore errors on individual directories
					}
				}
			}
		}

		return pruned;
	}

	private void pruneDir(File dir, List<String> pruned) throws IOException {
		pruned.add(dir.getName());
		if (!dryRun) {
			FileUtils.delete(dir, FileUtils.RECURSIVE);
		}
	}
}
