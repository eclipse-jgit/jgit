/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.diff;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.FS;

/**
 * Supplies the content of a file for {@link DiffFormatter}.
 * <p>
 * A content source is not thread-safe. Sources may contain state, including
 * information about the last ObjectLoader they returned. Callers must be
 * careful to ensure there is no more than one ObjectLoader pending on any
 * source, at any time.
 */
public abstract class ContentSource {
	/**
	 * Construct a content source for an ObjectReader.
	 *
	 * @param reader
	 *            the reader to obtain blobs from.
	 * @return a source wrapping the reader.
	 */
	public static ContentSource create(ObjectReader reader) {
		return new ObjectReaderSource(reader);
	}

	/**
	 * Construct a content source for a working directory.
	 *
	 * If the iterator is a {@link FileTreeIterator} an optimized version is
	 * used that doesn't require seeking through a TreeWalk.
	 *
	 * @param iterator
	 *            the iterator to obtain source files through.
	 * @return a content source wrapping the iterator.
	 */
	public static ContentSource create(WorkingTreeIterator iterator) {
		if (iterator instanceof FileTreeIterator) {
			FileTreeIterator i = (FileTreeIterator) iterator;
			return new FileSource(i.getDirectory(), iterator.getRepository()
					.getFS());
		}
		return new WorkingTreeSource(iterator);
	}

	/**
	 * Determine the size of the object.
	 *
	 * @param path
	 *            the path of the file, relative to the root of the repository.
	 * @param id
	 *            blob id of the file, if known.
	 * @return the size in bytes.
	 * @throws IOException
	 *             the file cannot be accessed.
	 */
	public abstract long size(String path, ObjectId id) throws IOException;

	/**
	 * Open the object.
	 *
	 * @param path
	 *            the path of the file, relative to the root of the repository.
	 * @param id
	 *            blob id of the file, if known.
	 * @return a loader that can supply the content of the file. The loader must
	 *         be used before another loader can be obtained from this same
	 *         source.
	 * @throws IOException
	 *             the file cannot be accessed.
	 */
	public abstract ObjectLoader open(String path, ObjectId id)
			throws IOException;

	private static class ObjectReaderSource extends ContentSource {
		private final ObjectReader reader;

		ObjectReaderSource(ObjectReader reader) {
			this.reader = reader;
		}

		@Override
		public long size(String path, ObjectId id) throws IOException {
			return reader.getObjectSize(id, Constants.OBJ_BLOB);
		}

		@Override
		public ObjectLoader open(String path, ObjectId id) throws IOException {
			return reader.open(id, Constants.OBJ_BLOB);
		}
	}

	private static class WorkingTreeSource extends ContentSource {
		private final TreeWalk tw;

		private final WorkingTreeIterator iterator;

		private String current;

		private WorkingTreeIterator ptr;

		WorkingTreeSource(WorkingTreeIterator iterator) {
			this.tw = new TreeWalk((ObjectReader) null);
			this.iterator = iterator;
		}

		@Override
		public long size(String path, ObjectId id) throws IOException {
			seek(path);
			return ptr.getEntryLength();
		}

		@Override
		public ObjectLoader open(String path, ObjectId id) throws IOException {
			seek(path);
			return new ObjectLoader() {
				@Override
				public long getSize() {
					return ptr.getEntryLength();
				}

				@Override
				public int getType() {
					return ptr.getEntryFileMode().getObjectType();
				}

				@Override
				public ObjectStream openStream() throws MissingObjectException,
						IOException {
					long contentLength = ptr.getEntryContentLength();
					InputStream in = ptr.openEntryStream();
					in = new BufferedInputStream(in);
					return new ObjectStream.Filter(getType(), contentLength, in);
				}

				@Override
				public boolean isLarge() {
					return true;
				}

				@Override
				public byte[] getCachedBytes() throws LargeObjectException {
					throw new LargeObjectException();
				}
			};
		}

		private void seek(String path) throws IOException {
			if (!path.equals(current)) {
				iterator.reset();
				tw.reset();
				tw.addTree(iterator);
				tw.setFilter(PathFilter.create(path));
				current = path;
				if (!tw.next())
					throw new FileNotFoundException(path);
				ptr = tw.getTree(0, WorkingTreeIterator.class);
				if (ptr == null)
					throw new FileNotFoundException(path);
			}
		}
	}

	private static class FileSource extends ContentSource {
		private final File root;

		private FS fs;

		FileSource(File root, FS fs) {
			this.root = root;
			this.fs = fs;
		}

		@Override
		public long size(String path, ObjectId id) throws IOException {
			return new File(root, path).length();
		}

		@Override
		public ObjectLoader open(String path, ObjectId id) throws IOException {
			final File p = new File(root, path);
			if (!fs.isFile(p) && !fs.isSymLink(p))
				throw new FileNotFoundException(path);
			return new ObjectLoader() {
				@Override
				public long getSize() {
					try {
						return fs.length(p);
					} catch (IOException e) {
						return 0;
					}
				}

				@Override
				public int getType() {
					return Constants.OBJ_BLOB;
				}

				@Override
				public ObjectStream openStream() throws MissingObjectException,
						IOException {
					if (fs.isSymLink(p))
						return new ObjectStream.SmallStream(Constants.OBJ_BLOB,
								fs.readSymLink(p).getBytes(
										Constants.CHARACTER_ENCODING));
					final FileInputStream in = new FileInputStream(p);
					final long sz = in.getChannel().size();
					final int type = getType();
					final BufferedInputStream b = new BufferedInputStream(in);
					return new ObjectStream.Filter(type, sz, b);
				}

				@Override
				public boolean isLarge() {
					return true;
				}

				@Override
				public byte[] getCachedBytes() throws LargeObjectException {
					throw new LargeObjectException();
				}
			};
		}
	}

	/** A pair of sources to access the old and new sides of a DiffEntry. */
	public static final class Pair {
		private final ContentSource oldSource;

		private final ContentSource newSource;

		/**
		 * Construct a pair of sources.
		 *
		 * @param oldSource
		 *            source to read the old side of a DiffEntry.
		 * @param newSource
		 *            source to read the new side of a DiffEntry.
		 */
		public Pair(ContentSource oldSource, ContentSource newSource) {
			this.oldSource = oldSource;
			this.newSource = newSource;
		}

		/**
		 * Determine the size of the object.
		 *
		 * @param side
		 *            which side of the entry to read (OLD or NEW).
		 * @param ent
		 *            the entry to examine.
		 * @return the size in bytes.
		 * @throws IOException
		 *             the file cannot be accessed.
		 */
		public long size(DiffEntry.Side side, DiffEntry ent) throws IOException {
			switch (side) {
			case OLD:
				return oldSource.size(ent.oldPath, ent.oldId.toObjectId());
			case NEW:
				return newSource.size(ent.newPath, ent.newId.toObjectId());
			default:
				throw new IllegalArgumentException();
			}
		}

		/**
		 * Open the object.
		 *
		 * @param side
		 *            which side of the entry to read (OLD or NEW).
		 * @param ent
		 *            the entry to examine.
		 * @return a loader that can supply the content of the file. The loader
		 *         must be used before another loader can be obtained from this
		 *         same source.
		 * @throws IOException
		 *             the file cannot be accessed.
		 */
		public ObjectLoader open(DiffEntry.Side side, DiffEntry ent)
				throws IOException {
			switch (side) {
			case OLD:
				return oldSource.open(ent.oldPath, ent.oldId.toObjectId());
			case NEW:
				return newSource.open(ent.newPath, ent.newId.toObjectId());
			default:
				throw new IllegalArgumentException();
			}
		}
	}
}
