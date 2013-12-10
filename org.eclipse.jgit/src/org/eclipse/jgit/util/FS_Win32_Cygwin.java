/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2014, Obeo.
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
import java.io.InputStreamReader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;

/**
 * FS implementation for Cygwin on Windows
 *
 * @since 3.0
 */
public class FS_Win32_Cygwin extends FS_Win32 {
	private static String cygpath;

	/**
	 * @return true if cygwin is found
	 */
	public static boolean isCygwin() {
		final String path = AccessController
				.doPrivileged(new PrivilegedAction<String>() {
					public String run() {
						return System.getProperty("java.library.path"); //$NON-NLS-1$
					}
				});
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

	public FS newInstance() {
		return new FS_Win32_Cygwin(this);
	}

	@Override
	public boolean isCaseSensitive() {
		return true;
	}

	@Override
	public File resolve(final File dir, final String pn) {
		String useCygPath = System.getProperty("jgit.usecygpath"); //$NON-NLS-1$
		if (useCygPath != null && useCygPath.equals("true")) { //$NON-NLS-1$
			String w = readPipe(dir, //
					new String[] { cygpath, "--windows", "--absolute", pn }, // //$NON-NLS-1$ //$NON-NLS-2$
					"UTF-8"); //$NON-NLS-1$
			if (w != null)
				return new File(w);
		}
		return super.resolve(dir, pn);
	}

	@Override
	protected File userHomeImpl() {
		final String home = AccessController
				.doPrivileged(new PrivilegedAction<String>() {
					public String run() {
						return System.getenv("HOME"); //$NON-NLS-1$
					}
				});
		if (home == null || home.length() == 0)
			return super.userHomeImpl();
		return resolve(new File("."), home); //$NON-NLS-1$
	}

	@Override
	public ProcessBuilder runInShell(String cmd, String[] args) {
		List<String> argv = new ArrayList<String>(4 + args.length);
		argv.add("sh.exe"); //$NON-NLS-1$
		argv.add("-c"); //$NON-NLS-1$
		argv.add(cmd + " \"$@\""); //$NON-NLS-1$
		argv.add(cmd);
		argv.addAll(Arrays.asList(args));
		ProcessBuilder proc = new ProcessBuilder();
		proc.command(argv);
		return proc;
	}

	@Override
	public boolean supportsSymlinks() {
		return true;
	}

	@Override
	public void createSymLink(File path, String target) throws IOException {
		ProcessBuilder processBuilder = runInShell(
				"ln", new String[] { "-s", target, path.getName() }); //$NON-NLS-1$ //$NON-NLS-2$
		processBuilder.directory(path.getParentFile());
		Process process = processBuilder.start();
		try {
			process.waitFor();
		} catch (InterruptedException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	@Override
	public boolean isSymLink(File path) throws IOException {
		ProcessBuilder processBuilder = runInShell(
				"test", new String[] { "-h", path.toString() }); //$NON-NLS-1$ //$NON-NLS-2$
		Process process = processBuilder.start();
		int exitValue = -1;
		try {
			exitValue = process.waitFor();
		} catch (InterruptedException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
		return exitValue == 0;
	}

	@Override
	public String readSymLink(File path) throws IOException {
		ProcessBuilder processBuilder = runInShell(
				"readlink", new String[] { path.getAbsolutePath() }); //$NON-NLS-1$
		Process process = processBuilder.start();
		String readLine = new BufferedReader(new InputStreamReader(
				process.getInputStream())).readLine();
		try {
			process.waitFor();
		} catch (InterruptedException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
		return readLine;
	}

	@Override
	public long length(File path) throws Exception {
		if (isSymLink(path)) {
			return readSymLink(path).toString().getBytes(
					Constants.CHARACTER_ENCODING).length;
		} else {
			return super.length(path);
		}
	}

}
