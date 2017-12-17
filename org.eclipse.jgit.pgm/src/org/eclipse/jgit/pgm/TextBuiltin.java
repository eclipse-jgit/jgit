/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_REMOTES;
import static org.eclipse.jgit.lib.Constants.R_TAGS;

import java.io.BufferedWriter;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.MessageFormat;
import java.util.ResourceBundle;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.pgm.opt.CmdLineParser;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.ThrowingPrintWriter;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;

/**
 * Abstract command which can be invoked from the command line.
 * <p>
 * Commands are configured with a single "current" repository and then the
 * {@link #execute(String[])} method is invoked with the arguments that appear
 * on the command line after the command name.
 * <p>
 * Command constructors should perform as little work as possible as they may be
 * invoked very early during process loading, and the command may not execute
 * even though it was constructed.
 */
public abstract class TextBuiltin {
	private String commandName;

	@Option(name = "--help", usage = "usage_displayThisHelpText", aliases = { "-h" })
	private boolean help;

	/**
	 * Input stream, typically this is standard input.
	 *
	 * @since 3.4
	 */
	protected InputStream ins;

	/**
	 * Writer to output to, typically this is standard output.
	 *
	 * @since 2.2
	 */
	protected ThrowingPrintWriter outw;

	/**
	 * Stream to output to, typically this is standard output.
	 *
	 * @since 2.2
	 */
	protected OutputStream outs;

	/**
	 * Error writer, typically this is standard error.
	 *
	 * @since 3.4
	 */
	protected ThrowingPrintWriter errw;

	/**
	 * Error output stream, typically this is standard error.
	 *
	 * @since 3.4
	 */
	protected OutputStream errs;

	/** Git repository the command was invoked within. */
	protected Repository db;

	/** Directory supplied via --git-dir command line option. */
	protected String gitdir;

	/** RevWalk used during command line parsing, if it was required. */
	protected RevWalk argWalk;

	final void setCommandName(final String name) {
		commandName = name;
	}

	/**
	 * If this command requires a repository.
	 *
	 * @return true if {@link #db}/{@link #getRepository()} is required
	 */
	protected boolean requiresRepository() {
		return true;
	}

	/**
	 * Initializes the command to work with a repository, including setting the
	 * output and error streams.
	 *
	 * @param repository
	 *            the opened repository that the command should work on.
	 * @param gitDir
	 *            value of the {@code --git-dir} command line option, if
	 *            {@code repository} is null.
	 * @param input
	 *            input stream from which input will be read
	 * @param output
	 *            output stream to which output will be written
	 * @param error
	 *            error stream to which errors will be written
	 * @since 4.9
	 */
	public void initRaw(final Repository repository, final String gitDir,
			InputStream input, OutputStream output, OutputStream error) {
		this.ins = input;
		this.outs = output;
		this.errs = error;
		init(repository, gitDir);
	}

	/**
	 * Initialize the command to work with a repository.
	 *
	 * @param repository
	 *            the opened repository that the command should work on.
	 * @param gitDir
	 *            value of the {@code --git-dir} command line option, if
	 *            {@code repository} is null.
	 */
	protected void init(final Repository repository, final String gitDir) {
		try {
			final String outputEncoding = repository != null ? repository
					.getConfig().getString("i18n", null, "logOutputEncoding") : null; //$NON-NLS-1$ //$NON-NLS-2$
			if (ins == null)
				ins = new FileInputStream(FileDescriptor.in);
			if (outs == null)
				outs = new FileOutputStream(FileDescriptor.out);
			if (errs == null)
				errs = new FileOutputStream(FileDescriptor.err);
			BufferedWriter outbufw;
			if (outputEncoding != null)
				outbufw = new BufferedWriter(new OutputStreamWriter(outs,
						outputEncoding));
			else
				outbufw = new BufferedWriter(new OutputStreamWriter(outs));
			outw = new ThrowingPrintWriter(outbufw);
			BufferedWriter errbufw;
			if (outputEncoding != null)
				errbufw = new BufferedWriter(new OutputStreamWriter(errs,
						outputEncoding));
			else
				errbufw = new BufferedWriter(new OutputStreamWriter(errs));
			errw = new ThrowingPrintWriter(errbufw);
		} catch (IOException e) {
			throw die(CLIText.get().cannotCreateOutputStream);
		}

		if (repository != null && repository.getDirectory() != null) {
			db = repository;
			gitdir = repository.getDirectory().getAbsolutePath();
		} else {
			db = repository;
			gitdir = gitDir;
		}
	}

	/**
	 * Parse arguments and run this command.
	 *
	 * @param args
	 *            command line arguments passed after the command name.
	 * @throws java.lang.Exception
	 *             an error occurred while processing the command. The main
	 *             framework will catch the exception and print a message on
	 *             standard error.
	 */
	public final void execute(String[] args) throws Exception {
		parseArguments(args);
		run();
	}

	/**
	 * Parses the command line arguments prior to running.
	 * <p>
	 * This method should only be invoked by {@link #execute(String[])}, prior
	 * to calling {@link #run()}. The default implementation parses all
	 * arguments into this object's instance fields.
	 *
	 * @param args
	 *            the arguments supplied on the command line, if any.
	 * @throws java.io.IOException
	 */
	protected void parseArguments(final String[] args) throws IOException {
		final CmdLineParser clp = new CmdLineParser(this);
		help = containsHelp(args);
		try {
			clp.parseArgument(args);
		} catch (CmdLineException err) {
			this.errw.println(CLIText.fatalError(err.getMessage()));
			if (help) {
				printUsage("", clp); //$NON-NLS-1$
			}
			throw die(true, err);
		}

		if (help) {
			printUsage("", clp); //$NON-NLS-1$
			throw new TerminatedByHelpException();
		}

		argWalk = clp.getRevWalkGently();
	}

	/**
	 * Print the usage line
	 *
	 * @param clp
	 *            a {@link org.eclipse.jgit.pgm.opt.CmdLineParser} object.
	 * @throws java.io.IOException
	 */
	public void printUsageAndExit(final CmdLineParser clp) throws IOException {
		printUsageAndExit("", clp); //$NON-NLS-1$
	}

	/**
	 * Print an error message and the usage line
	 *
	 * @param message
	 *            a {@link java.lang.String} object.
	 * @param clp
	 *            a {@link org.eclipse.jgit.pgm.opt.CmdLineParser} object.
	 * @throws java.io.IOException
	 */
	public void printUsageAndExit(final String message, final CmdLineParser clp) throws IOException {
		printUsage(message, clp);
		throw die(true);
	}

	/**
	 * Print usage help text.
	 *
	 * @param message
	 *            non null
	 * @param clp
	 *            parser used to print options
	 * @throws java.io.IOException
	 * @since 4.2
	 */
	protected void printUsage(final String message, final CmdLineParser clp)
			throws IOException {
		errw.println(message);
		errw.print("jgit "); //$NON-NLS-1$
		errw.print(commandName);
		clp.printSingleLineUsage(errw, getResourceBundle());
		errw.println();

		errw.println();
		clp.printUsage(errw, getResourceBundle());
		errw.println();

		errw.flush();
	}

	/**
	 * Get error writer
	 *
	 * @return error writer, typically this is standard error.
	 * @since 4.2
	 */
	public ThrowingPrintWriter getErrorWriter() {
		return errw;
	}

	/**
	 * Get output writer
	 *
	 * @return output writer, typically this is standard output.
	 * @since 4.9
	 */
	public ThrowingPrintWriter getOutputWriter() {
		return outw;
	}

	/**
	 * Get resource bundle with localized texts
	 *
	 * @return the resource bundle that will be passed to args4j for purpose of
	 *         string localization
	 */
	protected ResourceBundle getResourceBundle() {
		return CLIText.get().resourceBundle();
	}

	/**
	 * Perform the actions of this command.
	 * <p>
	 * This method should only be invoked by {@link #execute(String[])}.
	 *
	 * @throws java.lang.Exception
	 *             an error occurred while processing the command. The main
	 *             framework will catch the exception and print a message on
	 *             standard error.
	 */
	protected abstract void run() throws Exception;

	/**
	 * Get the repository
	 *
	 * @return the repository this command accesses.
	 */
	public Repository getRepository() {
		return db;
	}

	ObjectId resolve(final String s) throws IOException {
		final ObjectId r = db.resolve(s);
		if (r == null)
			throw die(MessageFormat.format(CLIText.get().notARevision, s));
		return r;
	}

	/**
	 * Exit the command with an error message
	 *
	 * @param why
	 *            textual explanation
	 * @return a runtime exception the caller is expected to throw
	 */
	protected static Die die(final String why) {
		return new Die(why);
	}

	/**
	 * Exit the command with an error message and an exception
	 *
	 * @param why
	 *            textual explanation
	 * @param cause
	 *            why the command has failed.
	 * @return a runtime exception the caller is expected to throw
	 */
	protected static Die die(final String why, final Throwable cause) {
		return new Die(why, cause);
	}

	/**
	 * Exit the command
	 *
	 * @param aborted
	 *            boolean indicating that the execution has been aborted before
	 *            running
	 * @return a runtime exception the caller is expected to throw
	 * @since 3.4
	 */
	protected static Die die(boolean aborted) {
		return new Die(aborted);
	}

	/**
	 * Exit the command
	 *
	 * @param aborted
	 *            boolean indicating that the execution has been aborted before
	 *            running
	 * @param cause
	 *            why the command has failed.
	 * @return a runtime exception the caller is expected to throw
	 * @since 4.2
	 */
	protected static Die die(boolean aborted, final Throwable cause) {
		return new Die(aborted, cause);
	}

	String abbreviateRef(String dst, boolean abbreviateRemote) {
		if (dst.startsWith(R_HEADS))
			dst = dst.substring(R_HEADS.length());
		else if (dst.startsWith(R_TAGS))
			dst = dst.substring(R_TAGS.length());
		else if (abbreviateRemote && dst.startsWith(R_REMOTES))
			dst = dst.substring(R_REMOTES.length());
		return dst;
	}

	/**
	 * Check if the arguments contain a help option
	 *
	 * @param args
	 *            non null
	 * @return true if the given array contains help option
	 * @since 4.2
	 */
	public static boolean containsHelp(String[] args) {
		for (String str : args) {
			if (str.equals("-h") || str.equals("--help")) { //$NON-NLS-1$ //$NON-NLS-2$
				return true;
			}
		}
		return false;
	}

	/**
	 * Exception thrown by {@link TextBuiltin} if it proceeds 'help' option
	 *
	 * @since 4.2
	 */
	public static class TerminatedByHelpException extends Die {
		private static final long serialVersionUID = 1L;

		/**
		 * Default constructor
		 */
		public TerminatedByHelpException() {
			super(true);
		}

	}
}
