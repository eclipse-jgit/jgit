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
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.awtui.AwtAuthenticator;
import org.eclipse.jgit.awtui.AwtSshSessionFactory;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.pgm.opt.CmdLineParser;
import org.eclipse.jgit.pgm.opt.SubcommandHandler;
import org.eclipse.jgit.util.CachedAuthenticator;
import org.eclipse.jgit.util.SystemReader;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.ExampleMode;
import org.kohsuke.args4j.Option;

/** Command line entry point. */
public class Main {
	@Option(name = "--help", usage = "display this help text", aliases = { "-h" })
	private boolean help;

	@Option(name = "--show-stack-trace", usage = "display the Java stack trace on exceptions")
	private boolean showStackTrace;

	@Option(name = "--git-dir", metaVar = "GIT_DIR", usage = "set the git repository to operate on")
	private File gitdir;

	@Argument(index = 0, metaVar = "command", required = true, handler = SubcommandHandler.class)
	private TextBuiltin subcommand;

	@Argument(index = 1, metaVar = "ARG")
	private List<String> arguments = new ArrayList<String>();

	/**
	 * Execute the command line.
	 *
	 * @param argv
	 *            arguments.
	 */
	public static void main(final String[] argv) {
		final Main me = new Main();
		try {
			if (!installConsole()) {
				AwtAuthenticator.install();
				AwtSshSessionFactory.install();
			}
			configureHttpProxy();
			me.execute(argv);
		} catch (Die err) {
			System.err.println("fatal: " + err.getMessage());
			if (me.showStackTrace)
				err.printStackTrace();
			System.exit(128);
		} catch (Exception err) {
			if (!me.showStackTrace && err.getCause() != null
					&& err instanceof TransportException)
				System.err.println("fatal: " + err.getCause().getMessage());

			if (err.getClass().getName().startsWith("org.eclipse.jgit.errors.")) {
				System.err.println("fatal: " + err.getMessage());
				if (me.showStackTrace)
					err.printStackTrace();
				System.exit(128);
			}
			err.printStackTrace();
			System.exit(1);
		}
	}

	private void execute(final String[] argv) throws Exception {
		final CmdLineParser clp = new CmdLineParser(this);
		try {
			clp.parseArgument(argv);
		} catch (CmdLineException err) {
			if (argv.length > 0 && !help) {
				System.err.println("fatal: " + err.getMessage());
				System.exit(1);
			}
		}

		if (argv.length == 0 || help) {
			final String ex = clp.printExample(ExampleMode.ALL);
			System.err.println("jgit" + ex + " command [ARG ...]");
			if (help) {
				System.err.println();
				clp.printUsage(System.err);
				System.err.println();
			} else if (subcommand == null) {
				System.err.println();
				System.err.println("The most commonly used commands are:");
				final CommandRef[] common = CommandCatalog.common();
				int width = 0;
				for (final CommandRef c : common)
					width = Math.max(width, c.getName().length());
				width += 2;

				for (final CommandRef c : common) {
					System.err.print(' ');
					System.err.print(c.getName());
					for (int i = c.getName().length(); i < width; i++)
						System.err.print(' ');
					System.err.print(c.getUsage());
					System.err.println();
				}
				System.err.println();
			}
			System.exit(1);
		}

		final TextBuiltin cmd = subcommand;
		if (cmd.requiresRepository()) {
			if (gitdir == null) {
				String gitDirEnv = SystemReader.getInstance().getenv(Constants.GIT_DIR_KEY);
				if (gitDirEnv != null)
					gitdir = new File(gitDirEnv);
			}
			if (gitdir == null)
				gitdir = findGitDir();

			File gitworktree;
			String gitWorkTreeEnv = SystemReader.getInstance().getenv(Constants.GIT_WORK_TREE_KEY);
			if (gitWorkTreeEnv != null)
				gitworktree = new File(gitWorkTreeEnv);
			else
				gitworktree = null;

			File indexfile;
			String indexFileEnv = SystemReader.getInstance().getenv(Constants.GIT_INDEX_KEY);
			if (indexFileEnv != null)
				indexfile = new File(indexFileEnv);
			else
				indexfile = null;

			File objectdir;
			String objectDirEnv = SystemReader.getInstance().getenv(Constants.GIT_OBJECT_DIRECTORY_KEY);
			if (objectDirEnv != null)
				objectdir = new File(objectDirEnv);
			else
				objectdir = null;

			File[] altobjectdirs;
			String altObjectDirEnv = SystemReader.getInstance().getenv(Constants.GIT_ALTERNATE_OBJECT_DIRECTORIES_KEY);
			if (altObjectDirEnv != null) {
				String[] parserdAltObjectDirEnv = altObjectDirEnv.split(File.pathSeparator);
				altobjectdirs = new File[parserdAltObjectDirEnv.length];
				for (int i = 0; i < parserdAltObjectDirEnv.length; i++)
					altobjectdirs[i] = new File(parserdAltObjectDirEnv[i]);
			} else
				altobjectdirs = null;

			if (gitdir == null || !gitdir.isDirectory()) {
				System.err.println("error: can't find git directory");
				System.exit(1);
			}
			cmd.init(new Repository(gitdir, gitworktree, objectdir, altobjectdirs, indexfile), gitdir);
		} else {
			cmd.init(null, gitdir);
		}
		try {
			cmd.execute(arguments.toArray(new String[arguments.size()]));
		} finally {
			if (cmd.out != null)
				cmd.out.flush();
		}
	}

	private static File findGitDir() {
		Set<String> ceilingDirectories = new HashSet<String>();
		String ceilingDirectoriesVar = SystemReader.getInstance().getenv(
				Constants.GIT_CEILING_DIRECTORIES_KEY);
		if (ceilingDirectoriesVar != null) {
			ceilingDirectories.addAll(Arrays.asList(ceilingDirectoriesVar
					.split(File.pathSeparator)));
		}
		File current = new File("").getAbsoluteFile();
		while (current != null) {
			final File gitDir = new File(current, Constants.DOT_GIT);
			if (gitDir.isDirectory())
				return gitDir;
			current = current.getParentFile();
			if (current != null
					&& ceilingDirectories.contains(current.getPath()))
				break;
		}
		return null;
	}

	private static boolean installConsole() {
		try {
			install("org.eclipse.jgit.console.ConsoleAuthenticator");
			install("org.eclipse.jgit.console.ConsoleSshSessionFactory");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		} catch (NoClassDefFoundError e) {
			return false;
		} catch (UnsupportedClassVersionError e) {
			return false;

		} catch (IllegalArgumentException e) {
			throw new RuntimeException("Cannot setup console", e);
		} catch (SecurityException e) {
			throw new RuntimeException("Cannot setup console", e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Cannot setup console", e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException("Cannot setup console", e);
		} catch (NoSuchMethodException e) {
			throw new RuntimeException("Cannot setup console", e);
		}
	}

	private static void install(final String name)
			throws IllegalAccessException, InvocationTargetException,
			NoSuchMethodException, ClassNotFoundException {
		try {
		Class.forName(name).getMethod("install").invoke(null);
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
		final String s = System.getenv("http_proxy");
		if (s == null || s.equals(""))
			return;

		final URL u = new URL((s.indexOf("://") == -1) ? "http://" + s : s);
		if (!"http".equals(u.getProtocol()))
			throw new MalformedURLException("Invalid http_proxy: " + s
					+ ": Only http supported.");

		final String proxyHost = u.getHost();
		final int proxyPort = u.getPort();

		System.setProperty("http.proxyHost", proxyHost);
		if (proxyPort > 0)
			System.setProperty("http.proxyPort", String.valueOf(proxyPort));

		final String userpass = u.getUserInfo();
		if (userpass != null && userpass.contains(":")) {
			final int c = userpass.indexOf(':');
			final String user = userpass.substring(0, c);
			final String pass = userpass.substring(c + 1);
			CachedAuthenticator
					.add(new CachedAuthenticator.CachedAuthentication(
							proxyHost, proxyPort, user, pass));
		}
	}
}
