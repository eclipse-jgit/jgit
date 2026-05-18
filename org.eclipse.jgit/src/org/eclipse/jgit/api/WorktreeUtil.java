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
import org.eclipse.jgit.lib.Repository;

/**
 * Utility methods for worktree support.
 */
class WorktreeUtil {

	/**
	 * Find the administrative directory for a given worktree.
	 *
	 * @param repo
	 *            the repository
	 * @param worktreeDir
	 *            the worktree directory
	 * @return the administrative directory, or null if not found
	 * @throws IOException
	 *             if an IO error occurred
	 */
	static File findAdminDir(Repository repo, File worktreeDir)
			throws IOException {
		File mainGitDir = repo.getCommonDirectory();
		File worktreesAdminDir = new File(mainGitDir, "worktrees"); //$NON-NLS-1$
		if (worktreesAdminDir.isDirectory()) {
			File[] dirs = worktreesAdminDir.listFiles(File::isDirectory);
			if (dirs != null) {
				for (File dir : dirs) {
					File gitdirFile = new File(dir, "gitdir"); //$NON-NLS-1$
					if (gitdirFile.isFile()) {
						String pathStr = new String(
								Files.readAllBytes(gitdirFile.toPath()))
										.trim();
						File dotGitFile = new File(pathStr);
						if (dotGitFile.getParentFile().getCanonicalFile()
								.equals(worktreeDir.getCanonicalFile())) {
							return dir;
						}
					}
				}
			}
		}
		return null;
	}
}
