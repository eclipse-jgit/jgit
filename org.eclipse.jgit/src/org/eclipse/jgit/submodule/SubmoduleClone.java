/*
 * Copyright (C) 2024, Simon Eder
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.submodule;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.util.FileUtils;

/**
 * A helper class to ease the cloning of submodules. It provides additional
 * functionality on top of the regular clone command:
 * <ul>
 * <li>a relative path is used for the worktree configuration entry (like
 * cgit)</li>
 * <li>a relative path is used for the GIT_DIR attribute (like cgit)</li>
 * <li>if the submodule is present in the GIT_DIR but has it's .git file
 * deleted, recreate the .git file instead of doing a full clone (like
 * cgit)</li>
 * </ul>
 *
 * @since 7.0
 *
 */
public class SubmoduleClone {
	private static String getRelativeGitDir(File gitDir, File worktreeDir) {
		String workDirPath = FileUtils.relativizeGitPath(
				FileUtils.pathToString(worktreeDir),
				FileUtils.pathToString(gitDir));
		return workDirPath;
	}

	private static String getRelativeWorktreeDir(File gitDir,
			File worktreeDir) {
		String workDirPath = FileUtils.relativizeGitPath(
				FileUtils.pathToString(gitDir),
				FileUtils.pathToString(worktreeDir));
		return workDirPath;
	}

	private static void updateConfigFile(String worktreeDir, Repository repo)
			throws IOException {
		StoredConfig cfg = repo.getConfig();
		cfg.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_WORKTREE, worktreeDir);
		cfg.save();
	}

	private static void updateDotGitFile(String gitDir, File repoDir)
			throws IOException {
		LockFile dotGitLockFile = new LockFile(
				new File(repoDir, Constants.DOT_GIT));
		if (dotGitLockFile.lock()) {
			dotGitLockFile.write(Constants.encode(Constants.GITDIR + gitDir));
			dotGitLockFile.commit();
		}
	}

	private static boolean checkSubmoduleExists(File gitDir) {
		if (gitDir != null && gitDir.exists()) {
			File[] files = gitDir.listFiles();
			return files != null && files.length != 0;
		}
		return false;
	}

	private static void restoreSubmodule(File gitDir, File repoDir)
			throws IOException {
		updateDotGitFile(gitDir.getAbsolutePath(), repoDir);
	}

	/**
	 * Use {@code cloneCmd} to either clone the submodule or if it still exists
	 * in {@code gitDir} only create a .git file in {@code worktreeDir} linking
	 * to {@code gitDir}.<br>
	 * Replace the absolute worktree and gitdir paths with relative ones.
	 * <p>
	 * The Git instance returned by this command needs to be closed by the
	 * caller to free resources held by the underlying {@link Repository}
	 * instance. It is recommended to call this method as soon as you don't need
	 * a reference to this {@link Git} instance and the underlying
	 * {@link Repository} instance anymore.
	 *
	 * @param cloneCmd
	 *            a configured clone command to be called if cloning is required
	 * @param worktreeDir
	 *            working directory of the submodule
	 * @param gitDir
	 *            the git directory of the submodule
	 * @return the Git instance
	 * @throws GitAPIException
	 *             if unable to clone the repository
	 */
	public static Git clone(CloneCommand cloneCmd, File worktreeDir,
			File gitDir)
			throws GitAPIException {
		if (false == checkSubmoduleExists(gitDir)) {
			@SuppressWarnings("resource")
			Git git = cloneCmd.call();
			git.close();
		} else {
			try {
				restoreSubmodule(gitDir, worktreeDir);
			} catch (IOException e) {
				throw new JGitInternalException(e.getMessage(), e);
			}
		}
		Git git;
		try {
			git = Git.open(worktreeDir);
			Repository repo = git.getRepository();
			if (repo != null) {
				String relGitDir = getRelativeGitDir(gitDir, worktreeDir);
				String relWortreekDir = getRelativeWorktreeDir(gitDir,
						worktreeDir);
				updateConfigFile(relWortreekDir, repo);
				updateDotGitFile(relGitDir, worktreeDir);
			}
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
		return git;
	}
}
