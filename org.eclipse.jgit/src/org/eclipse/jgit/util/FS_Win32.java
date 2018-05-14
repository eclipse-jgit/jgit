/*
 * Copyright (C) 2009, Google Inc.
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import org.eclipse.jgit.errors.CommandFailedException;
import org.eclipse.jgit.treewalk.FileTreeIterator.FileEntry;
import org.eclipse.jgit.treewalk.FileTreeIterator.FileModeStrategy;
import org.eclipse.jgit.treewalk.WorkingTreeIterator.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * FS implementation for Windows
 *
 * @since 3.0
 */
public class FS_Win32 extends FS {
	private final static Logger LOG = LoggerFactory.getLogger(FS_Win32.class);

	private volatile Boolean supportSymlinks;

	/**
	 * Constructor
	 */
	public FS_Win32() {
		super();
	}

	/**
	 * Constructor
	 *
	 * @param src
	 *            instance whose attributes to copy
	 */
	protected FS_Win32(FS src) {
		super(src);
	}

	/** {@inheritDoc} */
	@Override
	public FS newInstance() {
		return new FS_Win32(this);
	}

	/** {@inheritDoc} */
	@Override
	public boolean supportsExecute() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public boolean canExecute(File f) {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public boolean setExecute(File f, boolean canExec) {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public boolean isCaseSensitive() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public boolean retryFailedLockFileCommit() {
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public Entry[] list(File directory, FileModeStrategy fileModeStrategy) {
		List<Entry> result = new ArrayList<>();
		FS fs = this;
		boolean checkExecutable = fs.supportsExecute();
		try {
			Files.walkFileTree(directory.toPath(),
					EnumSet.noneOf(FileVisitOption.class), 1,
					new SimpleFileVisitor<Path>() {
						@Override
						public FileVisitResult visitFile(Path file,
								BasicFileAttributes attrs) throws IOException {
							File f = file.toFile();
							FS.Attributes attributes = new FS.Attributes(fs, f,
									true, attrs.isDirectory(),
									checkExecutable && f.canExecute(),
									attrs.isSymbolicLink(),
									attrs.isRegularFile(),
									attrs.creationTime().toMillis(),
									attrs.lastModifiedTime().toMillis(),
									attrs.size());
							result.add(new FileEntry(f, fs, attributes,
									fileModeStrategy));
							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult visitFileFailed(Path file,
								IOException exc) throws IOException {
							// Just ignore it
							return FileVisitResult.CONTINUE;
						}
					});
		} catch (IOException e) {
			// Ignore
		}
		if (result.isEmpty()) {
			return NO_ENTRIES;
		}
		return result.toArray(new Entry[result.size()]);
	}

	/** {@inheritDoc} */
	@Override
	protected File discoverGitExe() {
		String path = SystemReader.getInstance().getenv("PATH"); //$NON-NLS-1$
		File gitExe = searchPath(path, "git.exe", "git.cmd"); //$NON-NLS-1$ //$NON-NLS-2$

		if (gitExe == null) {
			if (searchPath(path, "bash.exe") != null) { //$NON-NLS-1$
				// This isn't likely to work, but its worth trying:
				// If bash is in $PATH, git should also be in $PATH.
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
					// The path may be in cygwin/msys notation so resolve it right away
					gitExe = resolve(null, w);
				}
			}
		}

		return gitExe;
	}

	/** {@inheritDoc} */
	@Override
	protected File userHomeImpl() {
		String home = SystemReader.getInstance().getenv("HOME"); //$NON-NLS-1$
		if (home != null)
			return resolve(null, home);
		String homeDrive = SystemReader.getInstance().getenv("HOMEDRIVE"); //$NON-NLS-1$
		if (homeDrive != null) {
			String homePath = SystemReader.getInstance().getenv("HOMEPATH"); //$NON-NLS-1$
			if (homePath != null)
				return new File(homeDrive, homePath);
		}

		String homeShare = SystemReader.getInstance().getenv("HOMESHARE"); //$NON-NLS-1$
		if (homeShare != null)
			return new File(homeShare);

		return super.userHomeImpl();
	}

	/** {@inheritDoc} */
	@Override
	public ProcessBuilder runInShell(String cmd, String[] args) {
		List<String> argv = new ArrayList<>(3 + args.length);
		argv.add("cmd.exe"); //$NON-NLS-1$
		argv.add("/c"); //$NON-NLS-1$
		argv.add(cmd);
		argv.addAll(Arrays.asList(args));
		ProcessBuilder proc = new ProcessBuilder();
		proc.command(argv);
		return proc;
	}

	/** {@inheritDoc} */
	@Override
	public boolean supportsSymlinks() {
		if (supportSymlinks == null)
			detectSymlinkSupport();
		return Boolean.TRUE.equals(supportSymlinks);
	}

	private void detectSymlinkSupport() {
		File tempFile = null;
		try {
			tempFile = File.createTempFile("tempsymlinktarget", ""); //$NON-NLS-1$ //$NON-NLS-2$
			File linkName = new File(tempFile.getParentFile(), "tempsymlink"); //$NON-NLS-1$
			createSymLink(linkName, tempFile.getPath());
			supportSymlinks = Boolean.TRUE;
			linkName.delete();
		} catch (IOException | UnsupportedOperationException
				| InternalError e) {
			supportSymlinks = Boolean.FALSE;
		} finally {
			if (tempFile != null)
				try {
					FileUtils.delete(tempFile);
				} catch (IOException e) {
					throw new RuntimeException(e); // panic
				}
		}
	}

	/** {@inheritDoc} */
	@Override
	public Attributes getAttributes(File path) {
		return FileUtils.getFileAttributesBasic(this, path);
	}
}
