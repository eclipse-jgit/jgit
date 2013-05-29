/*
 * Copyright (C) 2012 Google Inc.
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
package org.eclipse.jgit.api;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Create an archive of files from a named tree.
 * <p>
 * Examples (<code>git</code> is a {@link Git} instance):
 * <p>
 * Create a tarball from HEAD:
 *
 * <pre>
 * ArchiveCommand.registerFormat("tar", new TarFormat());
 * try {
 *	git.archive()
 *		.setTree(db.resolve(&quot;HEAD&quot;))
 *		.setOutputStream(out)
 *		.call();
 * } finally {
 *	ArchiveCommand.unregisterFormat("tar");
 * }
 * </pre>
 * <p>
 * Create a ZIP file from master:
 *
 * <pre>
 * ArchiveCommand.registerFormat("zip", new ZipFormat());
 * try {
 *	git.archive().
 *		.setTree(db.resolve(&quot;master&quot;))
 *		.setFormat("zip")
 *		.setOutputStream(out)
 *		.call();
 * } finally {
 *	ArchiveCommand.unregisterFormat("zip");
 * }
 * </pre>
 *
 * @see <a href="http://git-htmldocs.googlecode.com/git/git-archive.html"
 *      >Git documentation about archive</a>
 *
 * @since 3.0
 */
public class ArchiveCommand extends GitCommand<OutputStream> {
	/**
	 * Archival format.
	 *
	 * Usage:
	 *	Repository repo = git.getRepository();
	 *	T out = format.createArchiveOutputStream(System.out);
	 *	try {
	 *		for (...) {
	 *			format.putEntry(out, path, mode, repo.open(objectId));
	 *		}
	 *	} finally {
	 *		out.close();
	 *	}
	 *
	 * @param <T>
	 *            type representing an archive being created.
	 */
	public static interface Format<T extends Closeable> {
		/**
		 * Start a new archive. Entries can be included in the archive using the
		 * putEntry method, and then the archive should be closed using its
		 * close method.
		 *
		 * @param s
		 *            underlying output stream to which to write the archive.
		 * @return new archive object for use in putEntry
		 * @throws IOException
		 *             thrown by the underlying output stream for I/O errors
		 */
		T createArchiveOutputStream(OutputStream s) throws IOException;

		/**
		 * Write an entry to an archive.
		 *
		 * @param out
		 *            archive object from createArchiveOutputStream
		 * @param path
		 *            full filename relative to the root of the archive
		 * @param mode
		 *            mode (for example FileMode.REGULAR_FILE or
		 *            FileMode.SYMLINK)
		 * @param loader
		 *            blob object with data for this entry
		 * @throws IOException
		 *            thrown by the underlying output stream for I/O errors
		 */
		void putEntry(T out, String path, FileMode mode,
				ObjectLoader loader) throws IOException;
	}

	/**
	 * Signals an attempt to use an archival format that ArchiveCommand
	 * doesn't know about (for example due to a typo).
	 */
	public static class UnsupportedFormatException extends GitAPIException {
		private static final long serialVersionUID = 1L;

		private final String format;

		/**
		 * @param format the problematic format name
		 */
		public UnsupportedFormatException(String format) {
			super(MessageFormat.format(JGitText.get().unsupportedArchiveFormat, format));
			this.format = format;
		}

		/**
		 * @return the problematic format name
		 */
		public String getFormat() {
			return format;
		}
	}

	/**
	 * Available archival formats (corresponding to values for
	 * the --format= option)
	 */
	private static final ConcurrentMap<String, Format<?>> formats =
			new ConcurrentHashMap<String, Format<?>>();

	/**
	 * Adds support for an additional archival format.  To avoid
	 * unnecessary dependencies, ArchiveCommand does not have support
	 * for any formats built in; use this function to add them.
	 *
	 * OSGi plugins providing formats should call this function at
	 * bundle activation time.
	 *
	 * @param name name of a format (e.g., "tar" or "zip").
	 * @param fmt archiver for that format
	 * @throws JGitInternalException
	 *              An archival format with that name was already registered.
	 */
	public static void registerFormat(String name, Format<?> fmt) {
		if (formats.putIfAbsent(name, fmt) != null)
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().archiveFormatAlreadyRegistered,
					name));
	}

	/**
	 * Removes support for an archival format so its Format can be
	 * garbage collected.
	 *
	 * @param name name of format (e.g., "tar" or "zip").
	 * @throws JGitInternalException
	 *              No such archival format was registered.
	 */
	public static void unregisterFormat(String name) {
		if (formats.remove(name) == null)
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().archiveFormatAlreadyAbsent,
					name));
	}

	private static Format<?> lookupFormat(String formatName) throws UnsupportedFormatException {
		Format<?> fmt = formats.get(formatName);
		if (fmt == null)
			throw new UnsupportedFormatException(formatName);
		return fmt;
	}

	private OutputStream out;
	private ObjectId tree;
	private String format = "tar";

	/**
	 * @param repo
	 */
	public ArchiveCommand(Repository repo) {
		super(repo);
		setCallable(false);
	}

	private <T extends Closeable> OutputStream writeArchive(Format<T> fmt) {
		final TreeWalk walk = new TreeWalk(repo);
		try {
			final T outa = fmt.createArchiveOutputStream(out);
			try {
				final MutableObjectId idBuf = new MutableObjectId();
				final ObjectReader reader = walk.getObjectReader();
				final RevWalk rw = new RevWalk(walk.getObjectReader());

				walk.reset(rw.parseTree(tree));
				walk.setRecursive(true);
				while (walk.next()) {
					final String name = walk.getPathString();
					final FileMode mode = walk.getFileMode(0);

					if (mode == FileMode.TREE)
						// ZIP entries for directories are optional.
						// Leave them out, mimicking "git archive".
						continue;

					walk.getObjectId(idBuf, 0);
					fmt.putEntry(outa, name, mode, reader.open(idBuf));
				}
			} finally {
				outa.close();
			}
			return out;
		} catch (IOException e) {
			// TODO(jrn): Throw finer-grained errors.
			throw new JGitInternalException(
					JGitText.get().exceptionCaughtDuringExecutionOfArchiveCommand, e);
		} finally {
			walk.release();
		}
	}

	/**
	 * @return the stream to which the archive has been written
	 */
	@Override
	public OutputStream call() throws GitAPIException {
		checkCallable();

		final Format<?> fmt = lookupFormat(format);
		return writeArchive(fmt);
	}

	/**
	 * @param tree
	 *            the tag, commit, or tree object to produce an archive for
	 * @return this
	 */
	public ArchiveCommand setTree(ObjectId tree) {
		if (tree == null)
			throw new IllegalArgumentException();

		this.tree = tree;
		setCallable(true);
		return this;
	}

	/**
	 * @param out
	 *	      the stream to which to write the archive
	 * @return this
	 */
	public ArchiveCommand setOutputStream(OutputStream out) {
		this.out = out;
		return this;
	}

	/**
	 * @param fmt
	 *	      archive format (e.g., "tar" or "zip")
	 * @return this
	 */
	public ArchiveCommand setFormat(String fmt) {
		this.format = fmt;
		return this;
	}
}
