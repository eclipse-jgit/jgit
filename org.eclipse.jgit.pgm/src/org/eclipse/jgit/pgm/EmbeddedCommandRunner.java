/*
 * Copyright (C) 2014, Guillaume Nodet <gnodet@gmail.com>
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.pgm.opt.CmdLineParser;
import org.eclipse.jgit.pgm.opt.SubcommandHandler;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.ExampleMode;
import org.kohsuke.args4j.Option;

/**
 * Helper class to run commands in an embedded mode.
 *
 * @since 3.4
 */
public class EmbeddedCommandRunner {
	@Option(name = "--help", usage = "usage_displayThisHelpText", aliases = { "-h" })
	private boolean help;

	@Option(name = "--show-stack-trace", usage = "usage_displayThejavaStackTraceOnExceptions")
	private boolean showStackTrace;

	@Option(name = "--git-dir", metaVar = "metaVar_gitDir", usage = "usage_setTheGitRepositoryToOperateOn")
	private String gitdir;

	@Argument(index = 0, metaVar = "metaVar_command", required = true, handler = SubcommandHandler.class)
	private TextBuiltin subcommand;

	@Argument(index = 1, metaVar = "metaVar_arg")
	private List<String> arguments = new ArrayList<String>();

    /**
     * Execute a command.
     *
     * @param argv
     *          the command and its arguments
     * @param in
     *          the input stream, may be null in which case the system input stream will be used
     * @param out
     *          the output stream, may be null in which case the system output stream will be used
     * @param err
     *          the error stream, may be null in which case the system error stream will be used
     * @throws Exception
     *          if an error occurs
     */
	public void execute(final String[] argv, InputStream in, OutputStream out, OutputStream err) throws Exception {
		final CmdLineParser clp = new CmdLineParser(this);
		PrintWriter writer = new PrintWriter(err != null ? err : System.err);
		try {
			clp.parseArgument(argv);
		} catch (CmdLineException e) {
			if (argv.length > 0 && !help) {
				writer.println(MessageFormat.format(CLIText.get().fatalError, e.getMessage()));
				writer.flush();
				throw new Die(true);
			}
		}

		if (argv.length == 0 || help) {
			final String ex = clp.printExample(ExampleMode.ALL, CLIText.get().resourceBundle());
			writer.println("jgit" + ex + " command [ARG ...]"); //$NON-NLS-1$
			if (help) {
				writer.println();
				clp.printUsage(writer, CLIText.get().resourceBundle());
				writer.println();
			} else if (subcommand == null) {
				writer.println();
				writer.println(CLIText.get().mostCommonlyUsedCommandsAre);
				final CommandRef[] common = CommandCatalog.common();
				int width = 0;
				for (final CommandRef c : common)
					width = Math.max(width, c.getName().length());
				width += 2;

				for (final CommandRef c : common) {
					writer.print(' ');
					writer.print(c.getName());
					for (int i = c.getName().length(); i < width; i++)
						writer.print(' ');
					writer.print(CLIText.get().resourceBundle().getString(c.getUsage()));
					writer.println();
				}
				writer.println();
			}
			writer.flush();
			throw new Die(true);
		}

		final TextBuiltin cmd = subcommand;
		cmd.ins = in;
		cmd.outs = out;
		cmd.errs = err;
		if (cmd.requiresRepository())
			cmd.init(openGitDir(gitdir), null);
		else
			cmd.init(null, gitdir);
		try {
			cmd.execute(arguments.toArray(new String[arguments.size()]));
		} finally {
			if (cmd.outw != null)
				cmd.outw.flush();
			if (cmd.errw != null)
				cmd.errw.flush();
		}
	}

	/**
	 * Evaluate the {@code --git-dir} option and open the repository.
	 *
	 * @param gitdir
	 *            the {@code --git-dir} option given on the command line. May be
	 *            null if it was not supplied.
	 * @return the repository to operate on.
	 * @throws IOException
	 *             the repository cannot be opened.
	 */
	protected Repository openGitDir(String gitdir) throws IOException {
		RepositoryBuilder rb = new RepositoryBuilder() //
				.setGitDir(gitdir != null ? new File(gitdir) : null) //
				.readEnvironment() //
				.findGitDir();
		if (rb.getGitDir() == null)
			throw new Die(CLIText.get().cantFindGitDirectory);
		return rb.build();
	}
}
