/*
 * Copyright (C) 2006, Robin Rosenberg <robin.rosenberg@dewire.com>
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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.awtui.AwtAuthenticator;
import org.eclipse.jgit.awtui.AwtCredentialsProvider;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.pgm.opt.CmdLineParser;
import org.eclipse.jgit.pgm.opt.SubcommandHandler;
import org.eclipse.jgit.util.CachedAuthenticator;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.ExampleMode;
import org.kohsuke.args4j.Option;

/** Command line entry point. */
public class Main {
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
	 * Execute the command line.
	 *
	 * @param argv
	 *            arguments.
	 */
	public static void main(final String[] argv) {
		new Main().run(argv);
	}

	/**
	 * Parse the command line and execute the requested action.
	 *
	 * Subclasses should allocate themselves and then invoke this method:
	 *
	 * <pre>
	 * class ExtMain {
	 * 	public static void main(String[] argv) {
	 * 		new ExtMain().run(argv);
	 * 	}
	 * }
	 * </pre>
	 *
	 * @param argv
	 *            arguments.
	 */
	protected void run(final String[] argv) {
		try {
			if (!installConsole()) {
				AwtAuthenticator.install();
				AwtCredentialsProvider.install();
			}
			configureHttpProxy();
			execute(argv);
		} catch (Die err) {
			System.err.println(MessageFormat.format(CLIText.get().fatalError, err.getMessage()));
			if (showStackTrace)
				err.printStackTrace();
			System.exit(128);
		} catch (Exception err) {
			// Try to detect errno == EPIPE and exit normally if that happens
			// There may be issues with operating system versions and locale,
			// but we can probably assume that these messages will not be thrown
			// under other circumstances.
			if (err.getClass() == IOException.class) {
				// Linux, OS X
				if (err.getMessage().equals("Broken pipe")) //$NON-NLS-1$
					System.exit(0);
				// Windows
				if (err.getMessage().equals("The pipe is being closed")) //$NON-NLS-1$
					System.exit(0);
			}
			if (!showStackTrace && err.getCause() != null
					&& err instanceof TransportException)
				System.err.println(MessageFormat.format(CLIText.get().fatalError, err.getCause().getMessage()));

			if (err.getClass().getName().startsWith("org.eclipse.jgit.errors.")) { //$NON-NLS-1$
				System.err.println(MessageFormat.format(CLIText.get().fatalError, err.getMessage()));
				if (showStackTrace)
					err.printStackTrace();
				System.exit(128);
			}
			err.printStackTrace();
			System.exit(1);
		}
		if (System.out.checkError()) {
			System.err.println(CLIText.get().unknownIoErrorStdout);
			System.exit(1);
		}
		if (System.err.checkError()) {
			// No idea how to present an error here, most likely disk full or
			// broken pipe
			System.exit(1);
		}
	}

	private void execute(final String[] argv) throws Exception {
		final CmdLineParser clp = new CmdLineParser(this);
		PrintWriter writer = new PrintWriter(System.err);
		try {
			clp.parseArgument(argv);
		} catch (CmdLineException err) {
			if (argv.length > 0 && !help) {
				writer.println(MessageFormat.format(CLIText.get().fatalError, err.getMessage()));
				writer.flush();
				System.exit(1);
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
			System.exit(1);
		}

		final TextBuiltin cmd = subcommand;
		if (cmd.requiresRepository())
			cmd.init(openGitDir(gitdir), null);
		else
			cmd.init(null, gitdir);
		try {
			cmd.execute(arguments.toArray(new String[arguments.size()]));
		} finally {
			if (cmd.outw != null)
				cmd.outw.flush();
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

	private static boolean installConsole() {
		try {
			install("org.eclipse.jgit.console.ConsoleAuthenticator"); //$NON-NLS-1$
			install("org.eclipse.jgit.console.ConsoleCredentialsProvider"); //$NON-NLS-1$
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		} catch (NoClassDefFoundError e) {
			return false;
		} catch (UnsupportedClassVersionError e) {
			return false;

		} catch (IllegalArgumentException e) {
			throw new RuntimeException(CLIText.get().cannotSetupConsole, e);
		} catch (SecurityException e) {
			throw new RuntimeException(CLIText.get().cannotSetupConsole, e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(CLIText.get().cannotSetupConsole, e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException(CLIText.get().cannotSetupConsole, e);
		} catch (NoSuchMethodException e) {
			throw new RuntimeException(CLIText.get().cannotSetupConsole, e);
		}
	}

	private static void install(final String name)
			throws IllegalAccessException, InvocationTargetException,
			NoSuchMethodException, ClassNotFoundException {
		try {
		Class.forName(name).getMethod("install").invoke(null); //$NON-NLS-1$
		} catch (InvocationTargetException e) {
			if (e.getCause() instanceof RuntimeException)
				throw (RuntimeException) e.getCause();
			if (e.getCause() instanceof Error)
				throw (Error) e.getCause();
			throw e;
		}
	}

	/**
	 * Configure the JRE's standard HTTP based on <code>http_proxy</code>.
	 * <p>
	 * The popular libcurl library honors the <code>http_proxy</code>
	 * environment variable as a means of specifying an HTTP proxy for requests
	 * made behind a firewall. This is not natively recognized by the JRE, so
	 * this method can be used by command line utilities to configure the JRE
	 * before the first request is sent.
	 *
	 * @throws MalformedURLException
	 *             the value in <code>http_proxy</code> is unsupportable.
	 */
	private static void configureHttpProxy() throws MalformedURLException {
		final String s = System.getenv("http_proxy"); //$NON-NLS-1$
		if (s == null || s.equals("")) //$NON-NLS-1$
			return;

		final URL u = new URL((s.indexOf("://") == -1) ? "http://" + s : s); //$NON-NLS-1$ //$NON-NLS-2$
		if (!"http".equals(u.getProtocol())) //$NON-NLS-1$
			throw new MalformedURLException(MessageFormat.format(CLIText.get().invalidHttpProxyOnlyHttpSupported, s));

		final String proxyHost = u.getHost();
		final int proxyPort = u.getPort();

		System.setProperty("http.proxyHost", proxyHost); //$NON-NLS-1$
		if (proxyPort > 0)
			System.setProperty("http.proxyPort", String.valueOf(proxyPort)); //$NON-NLS-1$

		final String userpass = u.getUserInfo();
		if (userpass != null && userpass.contains(":")) { //$NON-NLS-1$
			final int c = userpass.indexOf(':');
			final String user = userpass.substring(0, c);
			final String pass = userpass.substring(c + 1);
			CachedAuthenticator
					.add(new CachedAuthenticator.CachedAuthentication(
							proxyHost, proxyPort, user, pass));
		}
	}
}
