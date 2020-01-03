/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.PrintStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.CommandFailedException;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FS implementation for Cygwin on Windows
 *
 * @since 3.0
 */
public class FS_Win32_Cygwin extends FS_Win32 {
	private final static Logger LOG = LoggerFactory
			.getLogger(FS_Win32_Cygwin.class);

	private static String cygpath;

	/**
	 * Whether cygwin is found
	 *
	 * @return true if cygwin is found
	 */
	public static boolean isCygwin() {
		final String path = AccessController
				.doPrivileged((PrivilegedAction<String>) () -> System
						.getProperty("java.library.path") //$NON-NLS-1$
				);
		if (path == null)
			return false;
		File found = FS.searchPath(path, "cygpath.exe"); //$NON-NLS-1$
		if (found != null)
			cygpath = found.getPath();
		return cygpath != null;
	}

	/**
	 * Constructor
	 */
	public FS_Win32_Cygwin() {
		super();
	}

	/**
	 * Constructor
	 *
	 * @param src
	 *            instance whose attributes to copy
	 */
	protected FS_Win32_Cygwin(FS src) {
		super(src);
	}

	/** {@inheritDoc} */
	@Override
	public FS newInstance() {
		return new FS_Win32_Cygwin(this);
	}

	/** {@inheritDoc} */
	@Override
	public File resolve(File dir, String pn) {
		String useCygPath = System.getProperty("jgit.usecygpath"); //$NON-NLS-1$
		if (useCygPath != null && useCygPath.equals("true")) { //$NON-NLS-1$
			String w;
			try {
				w = readPipe(dir, //
					new String[] { cygpath, "--windows", "--absolute", pn }, // //$NON-NLS-1$ //$NON-NLS-2$
					UTF_8.name());
			} catch (CommandFailedException e) {
				LOG.warn(e.getMessage());
				return null;
			}
			if (!StringUtils.isEmptyOrNull(w)) {
				return new File(w);
			}
		}
		return super.resolve(dir, pn);
	}

	/** {@inheritDoc} */
	@Override
	protected File userHomeImpl() {
		final String home = AccessController.doPrivileged(
				(PrivilegedAction<String>) () -> System.getenv("HOME") //$NON-NLS-1$
		);
		if (home == null || home.length() == 0)
			return super.userHomeImpl();
		return resolve(new File("."), home); //$NON-NLS-1$
	}

	/** {@inheritDoc} */
	@Override
	public ProcessBuilder runInShell(String cmd, String[] args) {
		List<String> argv = new ArrayList<>(4 + args.length);
		argv.add("sh.exe"); //$NON-NLS-1$
		argv.add("-c"); //$NON-NLS-1$
		argv.add("$0 \"$@\""); //$NON-NLS-1$
		argv.add(cmd);
		argv.addAll(Arrays.asList(args));
		ProcessBuilder proc = new ProcessBuilder();
		proc.command(argv);
		return proc;
	}

	/** {@inheritDoc} */
	@Override
	public String relativize(String base, String other) {
		final String relativized = super.relativize(base, other);
		return relativized.replace(File.separatorChar, '/');
	}

	/** {@inheritDoc} */
	@Override
	public ProcessResult runHookIfPresent(Repository repository, String hookName,
			String[] args, PrintStream outRedirect, PrintStream errRedirect,
			String stdinArgs) throws JGitInternalException {
		return internalRunHookIfPresent(repository, hookName, args, outRedirect,
				errRedirect, stdinArgs);
	}
}
