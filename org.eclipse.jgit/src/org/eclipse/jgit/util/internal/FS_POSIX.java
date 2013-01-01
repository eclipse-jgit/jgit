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
package org.eclipse.jgit.util.internal;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;

/**
 * Base FS for POSIX based systems
 */
public abstract class FS_POSIX extends FS {
	@Override
	protected File discoverGitPrefix() {
		String path = SystemReader.getInstance().getenv("PATH"); //$NON-NLS-1$
		File gitExe = searchPath(path, "git"); //$NON-NLS-1$
		if (gitExe != null)
			return gitExe.getParentFile().getParentFile();

		if (SystemReader.getInstance().isMacOS()) {
			// On MacOSX, PATH is shorter when Eclipse is launched from the
			// Finder than from a terminal. Therefore try to launch bash as a
			// login shell and search using that.
			//
			String w = readPipe(userHome(), //
					new String[] { "bash", "--login", "-c", "which git" }, // //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					Charset.defaultCharset().name());
			if (w == null || w.length() == 0)
				return null;
			File parentFile = new File(w).getParentFile();
			if (parentFile == null)
				return null;
			return parentFile.getParentFile();
		}

		return null;
	}

	/**
	 * Default constructor
	 */
	protected FS_POSIX() {
		super();
	}

	/**
	 * Constructore
	 *
	 * @param src
	 *            FS to copy some settings from
	 */
	protected FS_POSIX(FS src) {
		super(src);
	}

	@Override
	public boolean isCaseSensitive() {
		return !SystemReader.getInstance().isMacOS();
	}

	@Override
	public void setHidden(File path, boolean hidden) throws IOException {
		// Do nothing
	}

	@Override
	public ProcessBuilder runInShell(String cmd, String[] args) {
		List<String> argv = new ArrayList<String>(4 + args.length);
		argv.add("sh"); //$NON-NLS-1$
		argv.add("-c"); //$NON-NLS-1$
		argv.add(cmd + " \"$@\""); //$NON-NLS-1$
		argv.add(cmd);
		argv.addAll(Arrays.asList(args));
		ProcessBuilder proc = new ProcessBuilder();
		proc.command(argv);
		return proc;
	}
}
