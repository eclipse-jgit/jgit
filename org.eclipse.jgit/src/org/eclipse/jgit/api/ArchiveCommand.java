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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;

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
 * @see <a href="http://git-htmldocs.googlecode.com/git/git-archive.html" >Git
 *      documentation about archive</a>
 *
 * @since 3.1
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
		 *            (with trailing '/' for directories)
		 * @param mode
		 *            mode (for example FileMode.REGULAR_FILE or
		 *            FileMode.SYMLINK)
		 * @param loader
		 *            blob object with data for this entry (null for
		 *            directories)
		 * @throws IOException
		 *            thrown by the underlying output stream for I/O errors
		 */
		void putEntry(T out, String path, FileMode mode,
				ObjectLoader loader) throws IOException;

		/**
		 * Filename suffixes representing this format (e.g.,
		 * { ".tar.gz", ".tgz" }).
		 *
		 * The behavior is undefined when suffixes overlap (if
		 * one format claims suffix ".7z", no other format should
		 * take ".tar.7z").
		 *
		 * @return this format's suffixes
		 */
		Iterable<String> suffixes();
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

	private static class FormatEntry {
		final Format<?> format;
		/** Number of times this format has been registered. */
		final int refcnt;

		public FormatEntry(Format<?> format, int refcnt) {
			if (format == null)
				throw new NullPointerException();
			this.format = format;
			this.refcnt = refcnt;
		}
	};

	/**
	 * Available archival formats (corresponding to values for
	 * the --format= option)
	 */
	private static final ConcurrentMap<String, FormatEntry> formats =
			new ConcurrentHashMap<String, FormatEntry>();

	/**
	 * Replaces the entry for a key only if currently mapped to a given
	 * value.
	 *
	 * @param map a map
	 * @param key key with which the specified value is associated
	 * @param oldValue expected value for the key (null if should be absent).
	 * @param newValue value to be associated with the key (null to remove).
	 * @return true if the value was replaced
	 */
	private static <K, V> boolean replace(ConcurrentMap<K, V> map,
			K key, V oldValue, V newValue) {
		if (oldValue == null && newValue == null) // Nothing to do.
			return true;

		if (oldValue == null)
			return map.putIfAbsent(key, newValue) == null;
		else if (newValue == null)
			return map.remove(key, oldValue);
		else
			return map.replace(key, oldValue, newValue);
	}

	/**
	 * Adds support for an additional archival format.  To avoid
	 * unnecessary dependencies, ArchiveCommand does not have support
	 * for any formats built in; use this function to add them.
	 * <p>
	 * OSGi plugins providing formats should call this function at
	 * bundle activation time.
	 * <p>
	 * It is okay to register the same archive format with the same
	 * name multiple times, but don't forget to unregister it that
	 * same number of times, too.
	 * <p>
	 * Registering multiple formats with different names and the
	 * same or overlapping suffixes results in undefined behavior.
	 * TODO: check that suffixes don't overlap.
	 *
	 * @param name name of a format (e.g., "tar" or "zip").
	 * @param fmt archiver for that format
	 * @throws JGitInternalException
	 *              A different archival format with that name was
	 *              already registered.
	 */
	public static void registerFormat(String name, Format<?> fmt) {
		if (fmt == null)
			throw new NullPointerException();

		FormatEntry old, entry;
		do {
			old = formats.get(name);
			if (old == null) {
				entry = new FormatEntry(fmt, 1);
				continue;
			}
			if (!old.format.equals(fmt))
				throw new JGitInternalException(MessageFormat.format(
						JGitText.get().archiveFormatAlreadyRegistered,
						name));
			entry = new FormatEntry(old.format, old.refcnt + 1);
		} while (!replace(formats, name, old, entry));
	}

	/**
	 * Marks support for an archival format as no longer needed so its
	 * Format can be garbage collected if no one else is using it either.
	 * <p>
	 * In other words, this decrements the reference count for an
	 * archival format.  If the reference count becomes zero, removes
	 * support for that format.
	 *
	 * @param name name of format (e.g., "tar" or "zip").
	 * @throws JGitInternalException
	 *              No such archival format was registered.
	 */
	public static void unregisterFormat(String name) {
		FormatEntry old, entry;
		do {
			old = formats.get(name);
			if (old == null)
				throw new JGitInternalException(MessageFormat.format(
						JGitText.get().archiveFormatAlreadyAbsent,
						name));
			if (old.refcnt == 1) {
				entry = null;
				continue;
			}
			entry = new FormatEntry(old.format, old.refcnt - 1);
		} while (!replace(formats, name, old, entry));
	}

	private static Format<?> formatBySuffix(String filenameSuffix)
			throws UnsupportedFormatException {
		if (filenameSuffix != null)
			for (FormatEntry entry : formats.values()) {
				Format<?> fmt = entry.format;
				for (String sfx : fmt.suffixes())
					if (filenameSuffix.endsWith(sfx))
						return fmt;
			}
		return lookupFormat("tar"); //$NON-NLS-1$
	}

	private static Format<?> lookupFormat(String formatName) throws UnsupportedFormatException {
		FormatEntry entry = formats.get(formatName);
		if (entry == null)
			throw new UnsupportedFormatException(formatName);
		return entry.format;
	}

	private OutputStream out;
	private ObjectId tree;
	private String prefix;
	private String format;
	private List<String> paths = new ArrayList<String>();

	/** Filename suffix, for automatically choosing a format. */
	private String suffix;

	/**
	 * @param repo
	 */
	public ArchiveCommand(Repository repo) {
		super(repo);
		setCallable(false);
	}

	private <T extends Closeable> OutputStream writeArchive(Format<T> fmt) {
		final String pfx = prefix == null ? "" : prefix; //$NON-NLS-1$
		final TreeWalk walk = new TreeWalk(repo);
		try {
			final T outa = fmt.createArchiveOutputStream(out);
			try {
				final MutableObjectId idBuf = new MutableObjectId();
				final ObjectReader reader = walk.getObjectReader();
				final RevWalk rw = new RevWalk(walk.getObjectReader());

				walk.reset(rw.parseTree(tree));
				if (paths.size() != 0) {
					walk.setFilter(PathFilterGroup.createFromStrings(paths));
				}
				while (walk.next()) {
					final String name = pfx + walk.getPathString();
					FileMode mode = walk.getFileMode(0);

					if (walk.isSubtree())
						walk.enterSubtree();

					if (mode == FileMode.GITLINK)
						// TODO(jrn): Take a callback to recurse
						// into submodules.
						mode = FileMode.TREE;

					if (mode == FileMode.TREE) {
						fmt.putEntry(outa, name + "/", mode, null);
						continue;
					}
					walk.getObjectId(idBuf, 0);
					fmt.putEntry(outa, name, mode, reader.open(idBuf));
				}
				outa.close();
			} finally {
				out.close();
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

		final Format<?> fmt;
		if (format == null)
			fmt = formatBySuffix(suffix);
		else
			fmt = lookupFormat(format);
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
	 * @param prefix
	 *            string prefixed to filenames in archive (e.g., "master/").
	 *            null means to not use any leading prefix.
	 * @return this
	 * @since 3.3
	 */
	public ArchiveCommand setPrefix(String prefix) {
		this.prefix = prefix;
		return this;
	}

	/**
	 * Set the intended filename for the produced archive. Currently the only
	 * effect is to determine the default archive format when none is specified
	 * with {@link #setFormat(String)}.
	 *
	 * @param filename
	 *            intended filename for the archive
	 * @return this
	 */
	public ArchiveCommand setFilename(String filename) {
		int slash = filename.lastIndexOf('/');
		int dot = filename.indexOf('.', slash + 1);

		if (dot == -1)
			this.suffix = ""; //$NON-NLS-1$
		else
			this.suffix = filename.substring(dot);
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
	 *	      archive format (e.g., "tar" or "zip").
	 *	      null means to choose automatically based on
	 *	      the archive filename.
	 * @return this
	 */
	public ArchiveCommand setFormat(String fmt) {
		this.format = fmt;
		return this;
	}

	/**
	 * 
	 * @param paths
	 *            Set an optional parameter path.
     *            without an optional path parameter, all files and
	 *            subdirectories of the current working directory are included
	 *            in the archive. If one or more paths are specified, only these
	 *            are included.
	 * @return this
	 */
	public ArchiveCommand setPaths(String... paths) {
		this.paths = Arrays.asList(paths);
		return this;
	}
}
