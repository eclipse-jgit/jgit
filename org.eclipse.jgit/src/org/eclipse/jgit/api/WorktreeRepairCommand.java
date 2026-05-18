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
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

/**
 * A class used to execute a {@code worktree repair} command.
 *
 * @since 7.7
 */
public class WorktreeRepairCommand extends GitCommand<Collection<String>> {
	private List<String> paths = new ArrayList<>();

	/**
	 * Constructor for WorktreeRepairCommand.
	 *
	 * @param repo
	 *            the repository
	 */
	protected WorktreeRepairCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Add a path to repair.
	 *
	 * @param path
	 *            path to repair
	 * @return {@code this}
	 */
	public WorktreeRepairCommand addPath(String path) {
		paths.add(path);
		return this;
	}

	@Override
	public Collection<String> call() throws GitAPIException {
		checkCallable();
		List<String> repaired = new ArrayList<>();

		File mainGitDir = repo.getCommonDirectory();
		File worktreesAdminDir = new File(mainGitDir, "worktrees"); //$NON-NLS-1$

		if (paths.isEmpty()) {
			// 1. If current repo is a linked worktree, repair its link
			File curGitDir = repo.getDirectory();
			if (curGitDir.getParentFile() != null && curGitDir.getParentFile().getName().equals("worktrees")) { //$NON-NLS-1$
				File wtDir = repo.getWorkTree();
				repairPair(wtDir, curGitDir, repaired);
			}

			// 2. Scan all admin dirs in main repo and repair links to their worktrees
			if (worktreesAdminDir.isDirectory()) {
				File[] dirs = worktreesAdminDir.listFiles(File::isDirectory);
				if (dirs != null) {
					for (File adminDir : dirs) {
						try {
							File gitdirFile = new File(adminDir, "gitdir"); //$NON-NLS-1$
							if (gitdirFile.isFile()) {
								String pathStr = new String(Files.readAllBytes(gitdirFile.toPath())).trim();
								File dotGit = new File(pathStr);
								File wtDir = dotGit.getParentFile();
								if (wtDir != null && wtDir.isDirectory()) {
									repairPair(wtDir, adminDir, repaired);
								}
							}
						} catch (IOException e) {
							// Ignore
						}
					}
				}
			}
		} else {
			// Repair specified paths
			for (String pathStr : paths) {
				File wtDir = new File(pathStr);
				if (!wtDir.isDirectory()) {
					continue;
				}
				File dotGit = new File(wtDir, Constants.DOT_GIT);
				if (!dotGit.isFile()) {
					continue;
				}
				try {
					String content = new String(Files.readAllBytes(dotGit.toPath())).trim();
					if (content.startsWith("gitdir: ")) { //$NON-NLS-1$
						String oldAdminPath = content.substring(8).trim();
						File oldAdminDir = new File(oldAdminPath);
						String name = oldAdminDir.getName();
						File adminDir = new File(worktreesAdminDir, name);
						if (adminDir.isDirectory()) {
							repairPair(wtDir, adminDir, repaired);
						}
					}
				} catch (IOException e) {
					// Ignore
				}
			}
		}

		return repaired;
	}

	private void repairPair(File wtDir, File adminDir, List<String> repaired) {
		try {
			File wtDotGit = new File(wtDir, Constants.DOT_GIT);
			File adminGitdir = new File(adminDir, "gitdir"); //$NON-NLS-1$

			String expectedDotGit = "gitdir: " + adminDir.getAbsolutePath(); //$NON-NLS-1$
			String expectedAdmin = wtDotGit.getAbsolutePath();

			boolean changed = false;
			if (!wtDotGit.exists() || !new String(Files.readAllBytes(wtDotGit.toPath())).trim().equals(expectedDotGit)) {
				Files.write(wtDotGit.toPath(), expectedDotGit.getBytes());
				changed = true;
			}

			if (!adminGitdir.exists() || !new String(Files.readAllBytes(adminGitdir.toPath())).trim().equals(expectedAdmin)) {
				Files.write(adminGitdir.toPath(), expectedAdmin.getBytes());
				changed = true;
			}

			if (changed && !repaired.contains(wtDir.getAbsolutePath())) {
				repaired.add(wtDir.getAbsolutePath());
			}
		} catch (IOException e) {
			// Ignore
		}
	}
}
