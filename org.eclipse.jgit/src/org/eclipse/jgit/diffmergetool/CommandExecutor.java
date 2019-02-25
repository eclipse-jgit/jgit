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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FS.ExecutionResult;

/**
 * Runs a command with help of FS
 *
 */
public class CommandExecutor {

	/**
	 *
	 */
	public CommandExecutor() {
	}

	/**
	 * @param fs
	 *            the file system
	 * @param command
	 *            the command string
	 * @param workingDir
	 *            the working directory
	 * @param env
	 *            the environment
	 * @param splitCommands
	 *            should we split the command string to array
	 * @return the execution result
	 * @throws InterruptedException
	 * @throws IOException
	 */
	static public ExecutionResult run(FS fs, String command, File workingDir,
			Map<String, String> env, boolean splitCommands)
			throws IOException, InterruptedException {
		String[] args;
		if (splitCommands) {
			String[] cmdarray = splitSpacesAndQuotes(command, false);
			command = cmdarray[0];
			args = Arrays.copyOfRange(cmdarray, 1, cmdarray.length);
		} else {
			command = "\"" + command + "\""; //$NON-NLS-1$ //$NON-NLS-2$
			args = new String[0];
		}
		ProcessBuilder pb = fs.runInShell(command, args);
		pb.directory(workingDir);
		Map<String, String> envp = pb.environment();
		if (env != null) {
			envp.putAll(env);
		}
		ExecutionResult result = fs.execute(pb, null);
		return result;
	}

	/**
	 *
	 * borrowed the idea from:
	 * https://stackoverflow.com/questions/3366281/tokenizing-a-string-but-
	 * ignoring-delimiters-within-quotes/3366603#3366603
	 *
	 * @param str
	 *            the input string
	 * @param skipOuterQuotes
	 *            should we skip the quotes
	 * @return the command array
	 */
	static public String[] splitSpacesAndQuotes(String str,
			boolean skipOuterQuotes) {
		str += " "; // To detect last token when not quoted... //$NON-NLS-1$
		ArrayList<String> strings = new ArrayList<>();
		boolean inQuote = false;
		char quote = '\"';
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			if (c == '\"' || c == '\'' || c == ' ' && !inQuote) {
				if (c == '\"' || c == '\'') {
					if (!inQuote) {
						quote = c;
						inQuote = true;
					} else {
						if (quote == c) {
							inQuote = false;
						}
					}
					if (skipOuterQuotes || (inQuote && (quote != c))) {
						sb.append(c);
					}
				}
				if (!inQuote && sb.length() > 0) {
					strings.add(sb.toString());
					sb.delete(0, sb.length());
				}
			} else {
				sb.append(c);
			}
		}
		return strings.toArray(new String[strings.size()]);
	}

}
