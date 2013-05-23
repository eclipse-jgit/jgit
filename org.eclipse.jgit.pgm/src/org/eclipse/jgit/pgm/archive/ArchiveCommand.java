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
package org.eclipse.jgit.pgm.archive;

import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.pgm.internal.CLIText;
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
 * cmd = new ArchiveCommand(git.getRepository());
 * try {
 *	cmd.setTree(db.resolve(&quot;HEAD&quot;))
 *		.setOutputStream(out).call();
 * } finally {
 *	cmd.release();
 * }
 * </pre>
 * <p>
 * Create a ZIP file from master:
 *
 * <pre>
 * try {
 *	cmd.setTree(db.resolve(&quot;master&quot;))
 *		.setFormat(ArchiveCommand.Format.ZIP)
 *		.setOutputStream(out).call();
 * } finally {
 *	cmd.release();
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
	 *	ArchiveOutputStream out = format.createArchiveOutputStream(System.out);
	 *	try {
	 *		for (...) {
	 *			format.putEntry(out, path, mode, repo.open(objectId));
	 *		}
	 *	} finally {
	 *		out.close();
	 *	}
	 */
	public static interface Format {
		ArchiveOutputStream createArchiveOutputStream(OutputStream s);
		void putEntry(ArchiveOutputStream out, String path, FileMode mode,
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
			super(MessageFormat.format(CLIText.get().unsupportedArchiveFormat, format));
			this.format = format;
		}

		/**
		 * @return the problematic format name
		 */
		public String getFormat() {
			return format;
		}
	}

	private static final ConcurrentMap<String, Format> formats =
			new ConcurrentHashMap<String, Format>();

	static {
		formats.put("zip", new ZipFormat());
		formats.put("tar", new TarFormat());
	}

	private static Format lookupFormat(String formatName) throws UnsupportedFormatException {
		Format fmt = formats.get(formatName);
		if (fmt == null)
			throw new UnsupportedFormatException(formatName);
		return fmt;
	}

	private OutputStream out;
	private TreeWalk walk;
	private String format = "tar";

	/**
	 * @param repo
	 */
	public ArchiveCommand(Repository repo) {
		super(repo);
		walk = new TreeWalk(repo);
	}

	/**
	 * Release any resources used by the internal ObjectReader.
	 * <p>
	 * This does not close the output stream set with setOutputStream, which
	 * belongs to the caller.
	 */
	public void release() {
		walk.release();
	}

	/**
	 * @return the stream to which the archive has been written
	 */
	@Override
	public OutputStream call() throws GitAPIException {
		final MutableObjectId idBuf = new MutableObjectId();
		final Format fmt = lookupFormat(format);
		final ArchiveOutputStream outa = fmt.createArchiveOutputStream(out);
		final ObjectReader reader = walk.getObjectReader();

		try {
			try {
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
		} catch (IOException e) {
			// TODO(jrn): Throw finer-grained errors.
			throw new JGitInternalException(
					CLIText.get().exceptionCaughtDuringExecutionOfArchiveCommand, e);
		}

		return out;
	}

	/**
	 * @param tree
	 *            the tag, commit, or tree object to produce an archive for
	 * @return this
	 * @throws IOException
	 */
	public ArchiveCommand setTree(ObjectId tree) throws IOException {
		final RevWalk rw = new RevWalk(walk.getObjectReader());
		walk.reset(rw.parseTree(tree));
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
