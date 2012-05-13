/*
 * Copyright (C) 2012, IBM Corporation and others.
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
package org.eclipse.jgit.lib;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.pgm.CLIGitCommand;
import org.eclipse.jgit.storage.file.FileRepository;
import org.junit.Before;

public class CLIRepositoryTestCase extends LocalDiskRepositoryTestCase {
	/** Test repository, initialized for this test case. */
	protected FileRepository db;

	/** Working directory of {@link #db}. */
	protected File trash;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		db = createWorkRepository();
		trash = db.getWorkTree();
	}

	/**
	 * Execute the given commands and return the standard output.
	 *
	 * @param cmds
	 *            The commands to execute
	 * @return the resulting standard output of the commands
	 * @throws Exception
	 */
	protected String[] execute(String... cmds) throws Exception {
		List<String> result = new ArrayList<String>(cmds.length);
		for (String cmd : cmds)
			result.addAll(CLIGitCommand.execute(cmd, db));
		return result.toArray(new String[0]);
	}

	/**
	 * Execute the given commands and print the output to stdout.
	 *
	 * @param cmds
	 *            The commands to execute
	 * @return the result of the command, see {@link #execute(String...)}
	 * @throws Exception
	 */
	protected String[] executeAndPrint(String... cmds) throws Exception {
		String[] lines = execute(cmds);
		for (String line : lines) {
			System.out.println(line);
		}
		return lines;
	}

	/**
	 * Execute the given commands and print the code to test output.
	 *
	 * @param cmds
	 *            The commands to execute
	 * @return the result of the command, see {@link #execute(String...)}
	 * @throws Exception
	 */
	protected String[] executeAndPrintTestCode(String... cmds) throws Exception {
		String[] lines = execute(cmds);
		String cmdString = cmdString(cmds);
		if (lines.length == 0)
			System.out.println("\t\tassertTrue(execute(" + cmdString
					+ ").length == 0);");
		else {
			System.out.println("\t\tassertArrayEquals(new String[] { //");
			System.out.print("\t\t\t\t\t\t\"" + escapeJava(lines[0]));
			for (int i=1; i<lines.length; i++) {
				System.out.println("\", //");
				System.out.print("\t\t\t\t\t\t\"" + escapeJava(lines[i]));
			}
			System.out.println("\" //");
			System.out.println("\t\t\t\t}, execute(" + cmdString + ")); //");
		}
		return lines;
	}

	protected String cmdString(String... cmds) {
		if (cmds.length == 0)
			return "";
		else if (cmds.length == 1)
			return "\"" + escapeJava(cmds[0]) + "\"";
		else {
			StringBuffer sb = new StringBuffer(cmdString(cmds[0]));
			for (int i=1; i<cmds.length; i++) {
				sb.append(", ");
				sb.append(cmdString(cmds[i]));
			}
			return sb.toString();
		}
	}

	protected String escapeJava(String line) {
		// very crude implementation but ok for generating test code
		return line.replaceAll("\"", "\\\\\"") //
				.replaceAll("\\\\", "\\\\\\")
				.replaceAll("\t", "\\\\t");
	}

	protected File writeTrashFile(final String name, final String data)
			throws IOException {
		return JGitTestUtil.writeTrashFile(db, name, data);
	}
}
