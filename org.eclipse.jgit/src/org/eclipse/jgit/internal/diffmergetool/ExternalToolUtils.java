/*
 * Copyright (C) 2018-2021, Andre Bossert <andre.bossert@siemens.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.diffmergetool;

import java.util.TreeMap;
import java.io.IOException;
import java.util.Map;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

/**
 * Utilities for diff- and merge-tools.
 *
 * @since 5.13
 */
public class ExternalToolUtils {

	/**
	 * Prepare command for execution.
	 *
	 * @param command
	 *            the input "command" string
	 * @param localFile
	 *            the local file (ours)
	 * @param remoteFile
	 *            the remote file (theirs)
	 * @param mergedFile
	 *            the merged file (worktree)
	 * @param baseFile
	 *            the base file (can be null)
	 * @return the prepared (with replaced variables) command string
	 * @throws IOException
	 */
	public static String prepareCommand(String command, FileElement localFile,
			FileElement remoteFile, FileElement mergedFile,
			FileElement baseFile) throws IOException {
		if (localFile != null) {
			command = localFile.replaceVariable(command);
		}
		if (remoteFile != null) {
			command = remoteFile.replaceVariable(command);
		}
		if (mergedFile != null) {
			command = mergedFile.replaceVariable(command);
		}
		if (baseFile != null) {
			command = baseFile.replaceVariable(command);
		}
		return command;
	}

	/**
	 * Prepare environment needed for execution.
	 *
	 * @param repo
	 *            the repository
	 * @param localFile
	 *            the local file (ours)
	 * @param remoteFile
	 *            the remote file (theirs)
	 * @param mergedFile
	 *            the merged file (worktree)
	 * @param baseFile
	 *            the base file (can be null)
	 * @return the environment map with variables and values (file paths)
	 * @throws IOException
	 */
	public static Map<String, String> prepareEnvironment(Repository repo,
			FileElement localFile, FileElement remoteFile,
			FileElement mergedFile, FileElement baseFile) throws IOException {
		Map<String, String> env = new TreeMap<>();
		env.put(Constants.GIT_DIR_KEY, repo.getDirectory().getAbsolutePath());
		if (localFile != null) {
			localFile.addToEnv(env);
		}
		if (remoteFile != null) {
			remoteFile.addToEnv(env);
		}
		if (mergedFile != null) {
			mergedFile.addToEnv(env);
		}
		if (baseFile != null) {
			baseFile.addToEnv(env);
		}
		return env;
	}

	/**
	 * @param path
	 *            the path to be quoted
	 * @return quoted path if it contains spaces
	 */
	@SuppressWarnings("nls")
	public static String quotePath(String path) {
		// handling of spaces in path
		if (path.contains(" ")) {
			// add quotes before if needed
			if (!path.startsWith("\"")) {
				path = "\"" + path;
			}
			// add quotes after if needed
			if (!path.endsWith("\"")) {
				path = path + "\"";
			}
		}
		return path;
	}

	/**
	 * @param repo
	 *            the repository
	 * @param path
	 *            the tool path
	 * @return true if tool available and false otherwise
	 */
	public static boolean isToolAvailable(Repository repo, String path) {
		boolean available = true;
		try {
			CommandExecutor cmdExec = new CommandExecutor(repo.getFS(), false);
			available = cmdExec.checkExecutable(path, repo.getWorkTree(),
					prepareEnvironment(repo, null, null, null, null));
		} catch (Exception e) {
			available = false;
		}
		return available;
	}

}
