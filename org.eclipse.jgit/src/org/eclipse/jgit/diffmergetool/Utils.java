/*
 * Copyright (C) 2018-2019, Andre Bossert <andre.bossert@siemens.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.diffmergetool;

import java.util.TreeMap;
import java.io.IOException;
import java.util.Map;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

/**
 * Utilities for diff- and merge-tools.
 *
 * @since 5.4
 */
public class Utils {

	/**
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
	 * @param db
	 *            the repository database
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
	public static Map<String, String> prepareEnvironment(Repository db,
			FileElement localFile, FileElement remoteFile,
			FileElement mergedFile, FileElement baseFile) throws IOException {
		Map<String, String> env = new TreeMap<>();
		env.put(Constants.GIT_DIR_KEY, db.getDirectory().getAbsolutePath());
		localFile.addToEnv(env);
		remoteFile.addToEnv(env);
		mergedFile.addToEnv(env);
		if (baseFile != null) {
			baseFile.addToEnv(env);
		}
		return env;
	}

}
