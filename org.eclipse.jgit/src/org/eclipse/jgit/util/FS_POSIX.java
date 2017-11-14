/*
 * Copyright (C) 2010, Robin Rosenberg
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
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.CommandFailedException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base FS for POSIX based systems
 *
 * @since 3.0
 */
public class FS_POSIX extends FS {
	private final static Logger LOG = LoggerFactory.getLogger(FS_POSIX.class);

	private static final int DEFAULT_UMASK = 0022;
	private volatile int umask = -1;

	/** Default constructor. */
	protected FS_POSIX() {
	}

	/**
	 * Constructor
	 *
	 * @param src
	 *            FS to copy some settings from
	 */
	protected FS_POSIX(FS src) {
		super(src);
		if (src instanceof FS_POSIX) {
			umask = ((FS_POSIX) src).umask;
		}
	}

	@Override
	public FS newInstance() {
		return new FS_POSIX(this);
	}

	/**
	 * Set the umask, overriding any value observed from the shell.
	 *
	 * @param umask
	 *            mask to apply when creating files.
	 * @since 4.0
	 */
	public void setUmask(int umask) {
		this.umask = umask;
	}

	private int umask() {
		int u = umask;
		if (u == -1) {
			u = readUmask();
			umask = u;
		}
		return u;
	}

	/** @return mask returned from running {@code umask} command in shell. */
	private static int readUmask() {
		try {
			Process p = Runtime.getRuntime().exec(
					new String[] { "sh", "-c", "umask" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					null, null);
			try (BufferedReader lineRead = new BufferedReader(
					new InputStreamReader(p.getInputStream(), Charset
							.defaultCharset().name()))) {
				if (p.waitFor() == 0) {
					String s = lineRead.readLine();
					if (s != null && s.matches("0?\\d{3}")) { //$NON-NLS-1$
						return Integer.parseInt(s, 8);
					}
				}
				return DEFAULT_UMASK;
			}
		} catch (Exception e) {
			return DEFAULT_UMASK;
		}
	}

	@Override
	protected File discoverGitExe() {
		String path = SystemReader.getInstance().getenv("PATH"); //$NON-NLS-1$
		File gitExe = searchPath(path, "git"); //$NON-NLS-1$

		if (gitExe == null) {
			if (SystemReader.getInstance().isMacOS()) {
				if (searchPath(path, "bash") != null) { //$NON-NLS-1$
					// On MacOSX, PATH is shorter when Eclipse is launched from the
					// Finder than from a terminal. Therefore try to launch bash as a
					// login shell and search using that.
					String w;
					try {
						w = readPipe(userHome(),
							new String[]{"bash", "--login", "-c", "which git"}, // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
							Charset.defaultCharset().name());
					} catch (CommandFailedException e) {
						LOG.warn(e.getMessage());
						return null;
					}
					if (!StringUtils.isEmptyOrNull(w)) {
						gitExe = new File(w);
					}
				}
			}
		}

		return gitExe;
	}

	@Override
	public boolean isCaseSensitive() {
		return !SystemReader.getInstance().isMacOS();
	}

	@Override
	public boolean supportsExecute() {
		return true;
	}

	@Override
	public boolean canExecute(File f) {
		return FileUtils.canExecute(f);
	}

	@Override
	public boolean setExecute(File f, boolean canExecute) {
		if (!isFile(f))
			return false;
		if (!canExecute)
			return f.setExecutable(false, false);

		try {
			Path path = FileUtils.toPath(f);
			Set<PosixFilePermission> pset = Files.getPosixFilePermissions(path);

			// owner (user) is always allowed to execute.
			pset.add(PosixFilePermission.OWNER_EXECUTE);

			int mask = umask();
			apply(pset, mask, PosixFilePermission.GROUP_EXECUTE, 1 << 3);
			apply(pset, mask, PosixFilePermission.OTHERS_EXECUTE, 1);
			Files.setPosixFilePermissions(path, pset);
			return true;
		} catch (IOException e) {
			// The interface doesn't allow to throw IOException
			final boolean debug = Boolean.parseBoolean(SystemReader
					.getInstance().getProperty("jgit.fs.debug")); //$NON-NLS-1$
			if (debug)
				System.err.println(e);
			return false;
		}
	}

	private static void apply(Set<PosixFilePermission> set,
			int umask, PosixFilePermission perm, int test) {
		if ((umask & test) == 0) {
			// If bit is clear in umask, permission is allowed.
			set.add(perm);
		} else {
			// If bit is set in umask, permission is denied.
			set.remove(perm);
		}
	}

	@Override
	public ProcessBuilder runInShell(String cmd, String[] args) {
		List<String> argv = new ArrayList<>(4 + args.length);
		argv.add("sh"); //$NON-NLS-1$
		argv.add("-c"); //$NON-NLS-1$
		argv.add(cmd + " \"$@\""); //$NON-NLS-1$
		argv.add(cmd);
		argv.addAll(Arrays.asList(args));
		ProcessBuilder proc = new ProcessBuilder();
		proc.command(argv);
		return proc;
	}

	/**
	 * @since 4.0
	 */
	@Override
	public ProcessResult runHookIfPresent(Repository repository, String hookName,
			String[] args, PrintStream outRedirect, PrintStream errRedirect,
			String stdinArgs) throws JGitInternalException {
		return internalRunHookIfPresent(repository, hookName, args, outRedirect,
				errRedirect, stdinArgs);
	}

	@Override
	public boolean retryFailedLockFileCommit() {
		return false;
	}

	@Override
	public boolean supportsSymlinks() {
		return true;
	}

	@Override
	public void setHidden(File path, boolean hidden) throws IOException {
		// no action on POSIX
	}

	/**
	 * @since 3.3
	 */
	@Override
	public Attributes getAttributes(File path) {
		return FileUtils.getFileAttributesPosix(this, path);
	}

	/**
	 * @since 3.3
	 */
	@Override
	public File normalize(File file) {
		return FileUtils.normalize(file);
	}

	/**
	 * @since 3.3
	 */
	@Override
	public String normalize(String name) {
		return FileUtils.normalize(name);
	}

	/**
	 * @since 3.7
	 */
	@Override
	public File findHook(Repository repository, String hookName) {
		final File gitdir = repository.getDirectory();
		if (gitdir == null) {
			return null;
		}
		final Path hookPath = gitdir.toPath().resolve(Constants.HOOKS)
				.resolve(hookName);
		if (Files.isExecutable(hookPath))
			return hookPath.toFile();
		return null;
	}
}
