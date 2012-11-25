/*
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

package org.eclipse.jgit.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** Abstraction to support various file system operations not in Java. */
public abstract class FS {
	/** The auto-detected implementation selected for this operating system and JRE. */
	public static final FS DETECTED = detect();

	/**
	 * Auto-detect the appropriate file system abstraction.
	 *
	 * @return detected file system abstraction
	 */
	public static FS detect() {
		return detect(null);
	}

	/**
	 * Auto-detect the appropriate file system abstraction, taking into account
	 * the presence of a Cygwin installation on the system. Using jgit in
	 * combination with Cygwin requires a more elaborate (and possibly slower)
	 * resolution of file system paths.
	 *
	 * @param cygwinUsed
	 *            <ul>
	 *            <li><code>Boolean.TRUE</code> to assume that Cygwin is used in
	 *            combination with jgit</li>
	 *            <li><code>Boolean.FALSE</code> to assume that Cygwin is
	 *            <b>not</b> used with jgit</li>
	 *            <li><code>null</code> to auto-detect whether a Cygwin
	 *            installation is present on the system and in this case assume
	 *            that Cygwin is used</li>
	 *            </ul>
	 *
	 *            Note: this parameter is only relevant on Windows.
	 *
	 * @return detected file system abstraction
	 */
	public static FS detect(Boolean cygwinUsed) {
		if (SystemReader.getInstance().isWindows()) {
			if (cygwinUsed == null)
				cygwinUsed = Boolean.valueOf(FS_Win32_Cygwin.isCygwin());
			if (cygwinUsed.booleanValue())
				return new FS_Win32_Cygwin();
			else
				return new FS_Win32();
		} else if (FS_POSIX_Java6.hasExecute())
			return new FS_POSIX_Java6();
		else
			return new FS_POSIX_Java5();
	}

	private volatile Holder<File> userHome;

	private volatile Holder<File> gitPrefix;

	/**
	 * Constructs a file system abstraction.
	 */
	protected FS() {
		// Do nothing by default.
	}

	/**
	 * Initialize this FS using another's current settings.
	 *
	 * @param src
	 *            the source FS to copy from.
	 */
	protected FS(FS src) {
		userHome = src.userHome;
		gitPrefix = src.gitPrefix;
	}

	/** @return a new instance of the same type of FS. */
	public abstract FS newInstance();

	/**
	 * Does this operating system and JRE support the execute flag on files?
	 *
	 * @return true if this implementation can provide reasonably accurate
	 *         executable bit information; false otherwise.
	 */
	public abstract boolean supportsExecute();

	/**
	 * Is this file system case sensitive
	 *
	 * @return true if this implementation is case sensitive
	 */
	public abstract boolean isCaseSensitive();

	/**
	 * Determine if the file is executable (or not).
	 * <p>
	 * Not all platforms and JREs support executable flags on files. If the
	 * feature is unsupported this method will always return false.
	 *
	 * @param f
	 *            abstract path to test.
	 * @return true if the file is believed to be executable by the user.
	 */
	public abstract boolean canExecute(File f);

	/**
	 * Set a file to be executable by the user.
	 * <p>
	 * Not all platforms and JREs support executable flags on files. If the
	 * feature is unsupported this method will always return false and no
	 * changes will be made to the file specified.
	 *
	 * @param f
	 *            path to modify the executable status of.
	 * @param canExec
	 *            true to enable execution; false to disable it.
	 * @return true if the change succeeded; false otherwise.
	 */
	public abstract boolean setExecute(File f, boolean canExec);

	/**
	 * Resolve this file to its actual path name that the JRE can use.
	 * <p>
	 * This method can be relatively expensive. Computing a translation may
	 * require forking an external process per path name translated. Callers
	 * should try to minimize the number of translations necessary by caching
	 * the results.
	 * <p>
	 * Not all platforms and JREs require path name translation. Currently only
	 * Cygwin on Win32 require translation for Cygwin based paths.
	 *
	 * @param dir
	 *            directory relative to which the path name is.
	 * @param name
	 *            path name to translate.
	 * @return the translated path. <code>new File(dir,name)</code> if this
	 *         platform does not require path name translation.
	 */
	public File resolve(final File dir, final String name) {
		final File abspn = new File(name);
		if (abspn.isAbsolute())
			return abspn;
		return new File(dir, name);
	}

	/**
	 * Determine the user's home directory (location where preferences are).
	 * <p>
	 * This method can be expensive on the first invocation if path name
	 * translation is required. Subsequent invocations return a cached result.
	 * <p>
	 * Not all platforms and JREs require path name translation. Currently only
	 * Cygwin on Win32 requires translation of the Cygwin HOME directory.
	 *
	 * @return the user's home directory; null if the user does not have one.
	 */
	public File userHome() {
		Holder<File> p = userHome;
		if (p == null) {
			p = new Holder<File>(userHomeImpl());
			userHome = p;
		}
		return p.value;
	}

	/**
	 * Set the user's home directory location.
	 *
	 * @param path
	 *            the location of the user's preferences; null if there is no
	 *            home directory for the current user.
	 * @return {@code this}.
	 */
	public FS setUserHome(File path) {
		userHome = new Holder<File>(path);
		return this;
	}

	/**
	 * Does this file system have problems with atomic renames?
	 *
	 * @return true if the caller should retry a failed rename of a lock file.
	 */
	public abstract boolean retryFailedLockFileCommit();

	/**
	 * Determine the user's home directory (location where preferences are).
	 *
	 * @return the user's home directory; null if the user does not have one.
	 */
	protected File userHomeImpl() {
		final String home = AccessController
				.doPrivileged(new PrivilegedAction<String>() {
					public String run() {
						return System.getProperty("user.home"); //$NON-NLS-1$
					}
				});
		if (home == null || home.length() == 0)
			return null;
		return new File(home).getAbsoluteFile();
	}

	/**
	 * Searches the given path to see if it contains one of the given files.
	 * Returns the first it finds. Returns null if not found or if path is null.
	 *
	 * @param path
	 *            List of paths to search separated by File.pathSeparator
	 * @param lookFor
	 *            Files to search for in the given path
	 * @return the first match found, or null
	 **/
	static File searchPath(final String path, final String... lookFor) {
		if (path == null)
			return null;

		for (final String p : path.split(File.pathSeparator)) {
			for (String command : lookFor) {
				final File e = new File(p, command);
				if (e.isFile())
					return e.getAbsoluteFile();
			}
		}
		return null;
	}

	/**
	 * Execute a command and return a single line of output as a String
	 *
	 * @param dir
	 *            Working directory for the command
	 * @param command
	 *            as component array
	 * @param encoding
	 * @return the one-line output of the command
	 */
	protected static String readPipe(File dir, String[] command, String encoding) {
		final boolean debug = Boolean.parseBoolean(SystemReader.getInstance()
				.getProperty("jgit.fs.debug")); //$NON-NLS-1$
		try {
			if (debug)
				System.err.println("readpipe " + Arrays.asList(command) + "," //$NON-NLS-1$
						+ dir);
			final Process p = Runtime.getRuntime().exec(command, null, dir);
			final BufferedReader lineRead = new BufferedReader(
					new InputStreamReader(p.getInputStream(), encoding));
			p.getOutputStream().close();
			final AtomicBoolean gooblerFail = new AtomicBoolean(false);
			Thread gobbler = new Thread() {
				public void run() {
					InputStream is = p.getErrorStream();
					try {
						int ch;
						if (debug)
							while ((ch = is.read()) != -1)
								System.err.print((char) ch);
						else
							while (is.read() != -1) {
								// ignore
							}
					} catch (IOException e) {
						// Just print on stderr for debugging
						if (debug)
							e.printStackTrace(System.err);
						gooblerFail.set(true);
					}
					try {
						is.close();
					} catch (IOException e) {
						// Just print on stderr for debugging
						if (debug)
							e.printStackTrace(System.err);
						gooblerFail.set(true);
					}
				}
			};
			gobbler.start();
			String r = null;
			try {
				r = lineRead.readLine();
				if (debug) {
					System.err.println("readpipe may return '" + r + "'"); //$NON-NLS-1$
					System.err.println("(ignoring remaing output:"); //$NON-NLS-1$
				}
				String l;
				while ((l = lineRead.readLine()) != null) {
					if (debug)
						System.err.println(l);
				}
			} finally {
				p.getErrorStream().close();
				lineRead.close();
			}

			for (;;) {
				try {
					int rc = p.waitFor();
					gobbler.join();
					if (rc == 0 && r != null && r.length() > 0
							&& !gooblerFail.get())
						return r;
					if (debug)
						System.err.println("readpipe rc=" + rc); //$NON-NLS-1$
					break;
				} catch (InterruptedException ie) {
					// Stop bothering me, I have a zombie to reap.
				}
			}
		} catch (IOException e) {
			if (debug)
				System.err.println(e);
			// Ignore error (but report)
		}
		if (debug)
			System.err.println("readpipe returns null"); //$NON-NLS-1$
		return null;
	}

	/** @return the $prefix directory C Git would use. */
	public File gitPrefix() {
		Holder<File> p = gitPrefix;
		if (p == null) {
			String overrideGitPrefix = SystemReader.getInstance().getProperty(
					"jgit.gitprefix"); //$NON-NLS-1$
			if (overrideGitPrefix != null)
				p = new Holder<File>(new File(overrideGitPrefix));
			else
				p = new Holder<File>(discoverGitPrefix());
			gitPrefix = p;
		}
		return p.value;
	}

	/** @return the $prefix directory C Git would use. */
	protected abstract File discoverGitPrefix();

	/**
	 * Set the $prefix directory C Git uses.
	 *
	 * @param path
	 *            the directory. Null if C Git is not installed.
	 * @return {@code this}
	 */
	public FS setGitPrefix(File path) {
		gitPrefix = new Holder<File>(path);
		return this;
	}

	/**
	 * Initialize a ProcesssBuilder to run a command using the system shell.
	 *
	 * @param cmd
	 *            command to execute. This string should originate from the
	 *            end-user, and thus is platform specific.
	 * @param args
	 *            arguments to pass to command. These should be protected from
	 *            shell evaluation.
	 * @return a partially completed process builder. Caller should finish
	 *         populating directory, environment, and then start the process.
	 */
	public abstract ProcessBuilder runInShell(String cmd, String[] args);

	private static class Holder<V> {
		final V value;

		Holder(V value) {
			this.value = value;
		}
	}
}
