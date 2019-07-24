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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.pgm.CLIGitCommand;
import org.eclipse.jgit.pgm.CLIGitCommand.Result;
import org.eclipse.jgit.pgm.TextBuiltin.TerminatedByHelpException;
import org.junit.Before;

public class CLIRepositoryTestCase extends LocalDiskRepositoryTestCase {
	/** Test repository, initialized for this test case. */
	protected Repository db;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		db = createWorkRepository();
	}

	/**
	 * Executes specified git commands (with arguments)
	 *
	 * @param cmds
	 *            each string argument must be a valid git command line, e.g.
	 *            "git branch -h"
	 * @return command output
	 * @throws Exception
	 */
	protected String[] executeUnchecked(String... cmds) throws Exception {
		List<String> result = new ArrayList<>(cmds.length);
		for (String cmd : cmds) {
			result.addAll(CLIGitCommand.executeUnchecked(cmd, db));
		}
		return result.toArray(new String[0]);
	}

	/**
	 * Executes specified git commands (with arguments), throws exception and
	 * stops execution on first command which output contains a 'fatal:' error
	 *
	 * @param cmds
	 *            each string argument must be a valid git command line, e.g.
	 *            "git branch -h"
	 * @return command output
	 * @throws Exception
	 */
	protected String[] execute(String... cmds) throws Exception {
		List<String> result = new ArrayList<>(cmds.length);
		for (String cmd : cmds) {
			Result r = CLIGitCommand.executeRaw(cmd, db);
			if (r.ex instanceof TerminatedByHelpException) {
				result.addAll(r.errLines());
			} else if (r.ex != null) {
				throw r.ex;
			}
			result.addAll(r.outLines());
		}
		return result.toArray(new String[0]);
	}

	/**
	 * @param link
	 *            the path of the symbolic link to create
	 * @param target
	 *            the target of the symbolic link
	 * @return the path to the symbolic link
	 * @throws Exception
	 */
	protected Path writeLink(String link, String target) throws Exception {
		return JGitTestUtil.writeLink(db, link, target);
	}

	protected File writeTrashFile(String name, String data)
			throws IOException {
		return JGitTestUtil.writeTrashFile(db, name, data);
	}

	@Override
	protected String read(File file) throws IOException {
		return JGitTestUtil.read(file);
	}

	protected void deleteTrashFile(String name) throws IOException {
		JGitTestUtil.deleteTrashFile(db, name);
	}

	/**
	 * Execute the given commands and print the output to stdout. Use this
	 * function instead of the normal {@link #execute(String...)} when preparing
	 * a test case: the command is executed and then its output is printed on
	 * stdout, thus making it easier to prepare the correct command and expected
	 * output for the test case.
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
	 * Execute the given commands and print test code comparing expected and
	 * actual output. Use this function instead of the normal
	 * {@link #execute(String...)} when preparing a test case: the command is
	 * executed and test code is generated using the command output as a
	 * template of what is expected. The code generated is printed on stdout and
	 * can be pasted in the test case function.
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
			System.out
					.println("\t\tassertArrayOfLinesEquals(new String[] { //");
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
			StringBuilder sb = new StringBuilder(cmdString(cmds[0]));
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

	protected String shellQuote(String s) {
		return "'" + s.replace("'", "'\\''") + "'";
	}

	protected String shellQuote(File f) {
		return "'" + f.getPath().replace("'", "'\\''") + "'";
	}

	protected void assertStringArrayEquals(String expected, String[] actual) {
		// if there is more than one line, ignore last one if empty
		assertEquals(1,
				actual.length > 1 && actual[actual.length - 1].isEmpty()
						? actual.length - 1 : actual.length);
		assertEquals(expected, actual[0]);
	}

	protected void assertArrayOfLinesEquals(String[] expected, String[] actual) {
		assertEquals(toString(expected), toString(actual));
	}

	public static String toString(String... lines) {
		return toString(Arrays.asList(lines));
	}

	public static String toString(List<String> lines) {
		StringBuilder b = new StringBuilder();
		for (String s : lines) {
			// trim indentation, to simplify tests
			s = s.trim();
			if (s != null && !s.isEmpty()) {
				b.append(s);
				b.append('\n');
			}
		}
		// delete last line break to allow simpler tests with one line compare
		if (b.length() > 0 && b.charAt(b.length() - 1) == '\n') {
			b.deleteCharAt(b.length() - 1);
		}
		return b.toString();
	}

	public static boolean contains(List<String> lines, String str) {
		for (String s : lines) {
			if (s.contains(str)) {
				return true;
			}
		}
		return false;
	}
}
