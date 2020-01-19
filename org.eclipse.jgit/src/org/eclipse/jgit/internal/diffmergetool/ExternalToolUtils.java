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
		command = localFile.replaceVariable(command);
		command = remoteFile.replaceVariable(command);
		command = mergedFile.replaceVariable(command);
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
		localFile.addToEnv(env);
		remoteFile.addToEnv(env);
		mergedFile.addToEnv(env);
		if (baseFile != null) {
			baseFile.addToEnv(env);
		}
		return env;
	}

}
