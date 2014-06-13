/*
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2007-2010, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2009, Tor Arne Vestbø <torarnv@gmail.com>
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

package org.eclipse.jgit.treewalk;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.util.FS;

/**
 * Working directory iterator for standard Java IO.
 * <p>
 * This iterator uses the standard <code>java.io</code> package to read the
 * specified working directory as part of a {@link TreeWalk}.
 */
public class FileTreeIterator extends WorkingTreeIterator {
	/**
	 * the starting directory. This directory should correspond to the root of
	 * the repository.
	 */
	protected final File directory;

	/**
	 * the file system abstraction which will be necessary to perform certain
	 * file system operations.
	 */
	protected final FS fs;

	/**
	 * Create a new iterator to traverse the work tree and its children.
	 *
	 * @param repo
	 *            the repository whose working tree will be scanned.
	 */
	public FileTreeIterator(Repository repo) {
		this(repo.getWorkTree(), repo.getFS(),
				repo.getConfig().get(WorkingTreeOptions.KEY));
		initRootIterator(repo);
	}

	/**
	 * Create a new iterator to traverse the given directory and its children.
	 *
	 * @param root
	 *            the starting directory. This directory should correspond to
	 *            the root of the repository.
	 * @param fs
	 *            the file system abstraction which will be necessary to perform
	 *            certain file system operations.
	 * @param options
	 *            working tree options to be used
	 */
	public FileTreeIterator(final File root, FS fs, WorkingTreeOptions options) {
		super(options);
		directory = root;
		this.fs = fs;
		init(entries());
	}

	/**
	 * Create a new iterator to traverse a subdirectory.
	 *
	 * @param p
	 *            the parent iterator we were created from.
	 * @param fs
	 *            the file system abstraction which will be necessary to perform
	 *            certain file system operations.
	 * @param root
	 *            the subdirectory. This should be a directory contained within
	 *            the parent directory.
	 */
	protected FileTreeIterator(final WorkingTreeIterator p, final File root,
			FS fs) {
		super(p);
		directory = root;
		this.fs = fs;
		init(entries());
	}

	@Override
	public AbstractTreeIterator createSubtreeIterator(final ObjectReader reader)
			throws IncorrectObjectTypeException, IOException {
		return new FileTreeIterator(this, ((FileEntry) current()).getFile(), fs);
	}

	private Entry[] entries() {
		final File[] all = directory.listFiles();
		if (all == null)
			return EOF;
		final Entry[] r = new Entry[all.length];
		for (int i = 0; i < r.length; i++)
			r[i] = new FileEntry(all[i], fs);
		return r;
	}

	/**
	 * Wrapper for a standard Java IO file
	 */
	static public class FileEntry extends Entry {
		private final FileMode mode;

		private FS.Attributes attributes;

		private FS fs;

		/**
		 * Create a new file entry.
		 *
		 * @param f
		 *            file
		 * @param fs
		 *            file system
		 */
		public FileEntry(File f, FS fs) {
			this.fs = fs;
			f = fs.normalize(f);
			attributes = fs.getAttributes(f);
			if (attributes.isSymbolicLink())
				mode = FileMode.SYMLINK;
			else if (attributes.isDirectory()) {
				if (directoryIsValidGitRepo(f))
					mode = FileMode.GITLINK;
				else
					mode = FileMode.TREE;
			} else if (attributes.isExecutable())
				mode = FileMode.EXECUTABLE_FILE;
			else
				mode = FileMode.REGULAR_FILE;
		}

		private static boolean directoryIsValidGitRepo(File f) {
			Repository subRepo = null;
			try {
				File dotGit = new File(f, Constants.DOT_GIT);
				subRepo = new RepositoryBuilder().setGitDir(dotGit)
						.setMustExist(true).build();
				subRepo.resolve(Constants.HEAD);
				return true;
			} catch (IOException e) {
				return false;
			} finally {
				if (subRepo != null)
					subRepo.close();
			}
		}

		@Override
		public FileMode getMode() {
			return mode;
		}

		@Override
		public String getName() {
			return attributes.getName();
		}

		@Override
		public long getLength() {
			return attributes.getLength();
		}

		@Override
		public long getLastModified() {
			return attributes.getLastModifiedTime();
		}

		@Override
		public InputStream openInputStream() throws IOException {
			if (fs.isSymLink(getFile()))
				return new ByteArrayInputStream(fs.readSymLink(getFile())
						.getBytes(
						Constants.CHARACTER_ENCODING));
			else
				return new FileInputStream(getFile());
		}

		/**
		 * Get the underlying file of this entry.
		 *
		 * @return the underlying file of this entry
		 */
		public File getFile() {
			return attributes.getFile();
		}
	}

	/**
	 * @return The root directory of this iterator
	 */
	public File getDirectory() {
		return directory;
	}

	/**
	 * @return The location of the working file. This is the same as {@code new
	 *         File(getDirectory(), getEntryPath())} but may be faster by
	 *         reusing an internal File instance.
	 */
	public File getEntryFile() {
		return ((FileEntry) current()).getFile();
	}

	@Override
	protected byte[] idSubmodule(final Entry e) {
		if (repository == null)
			return idSubmodule(getDirectory(), e);
		return super.idSubmodule(e);
	}
}
