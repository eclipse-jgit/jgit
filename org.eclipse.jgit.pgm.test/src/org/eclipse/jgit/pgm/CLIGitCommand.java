/*
 * Copyright (C) 2011-2012, IBM Corporation and others.
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
package org.eclipse.jgit.pgm;

import java.io.ByteArrayOutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.pgm.opt.CmdLineParser;
import org.eclipse.jgit.pgm.opt.SubcommandHandler;
import org.eclipse.jgit.util.IO;
import org.kohsuke.args4j.Argument;

public class CLIGitCommand {
	@Argument(index = 0, metaVar = "metaVar_command", required = true, handler = SubcommandHandler.class)
	private TextBuiltin subcommand;

	@Argument(index = 1, metaVar = "metaVar_arg")
	private List<String> arguments = new ArrayList<String>();

	public TextBuiltin getSubcommand() {
		return subcommand;
	}

	public List<String> getArguments() {
		return arguments;
	}

	public static List<String> execute(String str, Repository db)
			throws Exception {
		try {
			return IO.readLines(new String(rawExecute(str, db)));
		} catch (Die e) {
			return IO.readLines(MessageFormat.format(CLIText.get().fatalError,
					e.getMessage()));
		}
	}

	public static byte[] rawExecute(String str, Repository db)
			throws Exception {
		String[] args = split(str);
		if (!args[0].equalsIgnoreCase("git") || args.length < 2)
			throw new IllegalArgumentException(
					"Expected 'git <command> [<args>]', was:" + str);
		String[] argv = new String[args.length - 1];
		System.arraycopy(args, 1, argv, 0, args.length - 1);

		CLIGitCommand bean = new CLIGitCommand();
		final CmdLineParser clp = new CmdLineParser(bean);
		clp.parseArgument(argv);

		final TextBuiltin cmd = bean.getSubcommand();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		cmd.outs = baos;
		if (cmd.requiresRepository())
			cmd.init(db, null);
		else
			cmd.init(null, null);
		try {
			cmd.execute(bean.getArguments().toArray(
					new String[bean.getArguments().size()]));
		} finally {
			if (cmd.outw != null)
				cmd.outw.flush();
		}
		return baos.toByteArray();
	}

	/**
	 * Split a command line into a string array.
	 *
	 * A copy of Gerrit's
	 * com.google.gerrit.sshd.CommandFactoryProvider#split(String)
	 *
	 * @param commandLine
	 *            a command line
	 * @return the array
	 */
	static String[] split(String commandLine) {
		final List<String> list = new ArrayList<String>();
		boolean inquote = false;
		boolean inDblQuote = false;
		StringBuilder r = new StringBuilder();
		for (int ip = 0; ip < commandLine.length();) {
			final char b = commandLine.charAt(ip++);
			switch (b) {
			case '\t':
			case ' ':
				if (inquote || inDblQuote)
					r.append(b);
				else if (r.length() > 0) {
					list.add(r.toString());
					r = new StringBuilder();
				}
				continue;
			case '\"':
				if (inquote)
					r.append(b);
				else
					inDblQuote = !inDblQuote;
				continue;
			case '\'':
				if (inDblQuote)
					r.append(b);
				else
					inquote = !inquote;
				continue;
			case '\\':
				if (inquote || ip == commandLine.length())
					r.append(b); // literal within a quote
				else
					r.append(commandLine.charAt(ip++));
				continue;
			default:
				r.append(b);
				continue;
			}
		}
		if (r.length() > 0)
			list.add(r.toString());
		return list.toArray(new String[list.size()]);
	}

}
