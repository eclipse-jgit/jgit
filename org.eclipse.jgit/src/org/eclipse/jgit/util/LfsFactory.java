/*
 * Copyright (C) 2018, Markus Duft <markus.duft@ssi-schaefer.com>
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

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.text.MessageFormat;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.attributes.Attribute;
import org.eclipse.jgit.attributes.Attributes;
import org.eclipse.jgit.hooks.PrePushHook;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

/**
 * Represents an optionally present LFS support implementation
 *
 * @since 4.11
 */
public class LfsFactory {

	private static LfsFactory instance = new LfsFactory();

	/**
	 * Constructor
	 */
	protected LfsFactory() {
	}

	/**
	 * @return the current LFS implementation
	 */
	public static LfsFactory getInstance() {
		return instance;
	}

	/**
	 * @param instance
	 *            register a {@link LfsFactory} instance as the
	 *            {@link LfsFactory} implementation to use.
	 */
	public static void setInstance(LfsFactory instance) {
		LfsFactory.instance = instance;
	}

	/**
	 * @return whether LFS support is available
	 */
	public boolean isAvailable() {
		return false;
	}

	/**
	 * Apply clean filtering to the given stream, writing the file content to
	 * the LFS storage if required and returning a stream to the LFS pointer
	 * instead.
	 *
	 * @param db
	 *            the repository
	 * @param input
	 *            the original input
	 * @param length
	 *            the expected input stream length
	 * @param attribute
	 *            the attribute used to check for LFS enablement (i.e. "merge",
	 *            "diff", "filter" from .gitattributes).
	 * @return a stream to the content that should be written to the object
	 *         store along with the expected length of the stream. the original
	 *         stream is not applicable.
	 * @throws IOException
	 *             in case of an error
	 */
	public LfsInputStream applyCleanFilter(Repository db,
			InputStream input, long length, Attribute attribute)
			throws IOException {
		return new LfsInputStream(input, length);
	}

	/**
	 * Apply smudge filtering to a given loader, potentially redirecting it to a
	 * LFS blob which is downloaded on demand.
	 *
	 * @param db
	 *            the repository
	 * @param loader
	 *            the loader for the blob
	 * @param attribute
	 *            the attribute used to check for LFS enablement (i.e. "merge",
	 *            "diff", "filter" from .gitattributes).
	 * @return a loader for the actual data of a blob, or the original loader in
	 *         case LFS is not applicable.
	 * @throws IOException
	 */
	public ObjectLoader applySmudgeFilter(Repository db,
			ObjectLoader loader, Attribute attribute) throws IOException {
		return loader;
	}

	/**
	 * Retrieve a pre-push hook to be applied.
	 *
	 * @param repo
	 *            the {@link Repository} the hook is applied to.
	 * @param outputStream
	 * @return a {@link PrePushHook} implementation or <code>null</code>
	 */
	public @Nullable PrePushHook getPrePushHook(Repository repo,
			PrintStream outputStream) {
		return null;
	}

	/**
	 * @param db
	 *            the repository
	 * @param path
	 *            the path to find attributes for
	 * @return the {@link Attributes} for the given path.
	 * @throws IOException
	 *             in case of an error
	 */
	public static Attributes getAttributesForPath(Repository db, String path)
			throws IOException {
		try (TreeWalk walk = new TreeWalk(db)) {
			walk.addTree(new FileTreeIterator(db));
			PathFilter f = PathFilter.create(path);
			walk.setFilter(f);
			walk.setRecursive(false);
			Attributes attr = null;
			while (walk.next()) {
				if (f.isDone(walk)) {
					attr = walk.getAttributes();
					break;
				} else if (walk.isSubtree()) {
					walk.enterSubtree();
				}
			}
			if (attr == null) {
				throw new IOException(MessageFormat
						.format(JGitText.get().noPathAttributesFound, path));
			}

			return attr;
		}
	}

	/**
	 * Get attributes for given path and commit
	 *
	 * @param db
	 *            the repository
	 * @param path
	 *            the path to find attributes for
	 * @param commit
	 *            the commit to inspect.
	 * @return the {@link Attributes} for the given path.
	 * @throws IOException
	 *             in case of an error
	 */
	public static Attributes getAttributesForPath(Repository db, String path,
			RevCommit commit) throws IOException {
		if (commit == null) {
			return getAttributesForPath(db, path);
		}

		try (TreeWalk walk = TreeWalk.forPath(db, path, commit.getTree())) {
			Attributes attr = walk == null ? null : walk.getAttributes();
			if (attr == null) {
				throw new IOException(MessageFormat
						.format(JGitText.get().noPathAttributesFound, path));
			}

			return attr;
		}
	}

	/**
	 * Encapsulate a potentially exchanged {@link InputStream} along with the
	 * expected stream content length.
	 */
	public static final class LfsInputStream extends InputStream {
		/**
		 * The actual stream.
		 */
		private InputStream stream;

		/**
		 * The expected stream content length.
		 */
		private long length;

		/**
		 * Create a new wrapper around a certain stream
		 *
		 * @param stream
		 *            the stream to wrap. the stream will be closed on
		 *            {@link #close()}.
		 * @param length
		 *            the expected length of the stream
		 */
		public LfsInputStream(InputStream stream, long length) {
			this.stream = stream;
			this.length = length;
		}

		/**
		 * Create a new wrapper around a temporary buffer.
		 *
		 * @param buffer
		 *            the buffer to initialize stream and length from. The
		 *            buffer will be destroyed on {@link #close()}
		 * @throws IOException
		 *             in case of an error opening the stream to the buffer.
		 */
		public LfsInputStream(TemporaryBuffer buffer) throws IOException {
			this.stream = buffer.openInputStreamWithAutoDestroy();
			this.length = buffer.length();
		}

		@Override
		public void close() throws IOException {
			stream.close();
		}

		@Override
		public int read() throws IOException {
			return stream.read();
		}

		/**
		 * @return the length of the stream
		 */
		public long getLength() {
			return length;
		}
	}

}
