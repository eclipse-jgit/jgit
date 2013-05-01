/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
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

package org.eclipse.jgit.lib;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Set;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.transport.PackParser;

/**
 * Inserts objects into an existing {@code ObjectDatabase}.
 * <p>
 * An inserter is not thread-safe. Individual threads should each obtain their
 * own unique inserter instance, or must arrange for locking at a higher level
 * to ensure the inserter is in use by no more than one thread at a time.
 * <p>
 * Objects written by an inserter may not be immediately visible for reading
 * after the insert method completes. Callers must invoke either
 * {@link #release()} or {@link #flush()} prior to updating references or
 * otherwise making the returned ObjectIds visible to other code.
 */
public abstract class ObjectInserter {
	/** An inserter that can be used for formatting and id generation only. */
	public static class Formatter extends ObjectInserter {
		@Override
		public ObjectId insert(int objectType, long length, InputStream in)
				throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public PackParser newPackParser(InputStream in) throws IOException {
			throw new UnsupportedOperationException();
		}

		@Override
		public ObjectReader newReader(Repository db) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void flush() throws IOException {
			// Do nothing.
		}

		@Override
		public void release() {
			// Do nothing.
		}
	}

	/** Wraps a delegate ObjectInserter. */
	public static abstract class Filter extends ObjectInserter {
		/** @return delegate ObjectInserter to handle all processing. */
		protected abstract ObjectInserter delegate();

		@Override
		protected byte[] buffer() {
			return delegate().buffer();
		}

		public ObjectId idFor(int type, byte[] data) {
			return delegate().idFor(type, data);
		}

		public ObjectId idFor(int type, byte[] data, int off, int len) {
			return delegate().idFor(type, data, off, len);
		}

		public ObjectId idFor(int objectType, long length, InputStream in)
				throws IOException {
			return delegate().idFor(objectType, length, in);
		}

		public ObjectId idFor(TreeFormatter formatter) {
			return delegate().idFor(formatter);
		}

		public ObjectId insert(int type, byte[] data) throws IOException {
			return delegate().insert(type, data);
		}

		public ObjectId insert(int type, byte[] data, int off, int len)
				throws IOException {
			return delegate().insert(type, data, off, len);
		}

		public ObjectId insert(int objectType, long length, InputStream in)
				throws IOException {
			return delegate().insert(objectType, length, in);
		}

		public PackParser newPackParser(InputStream in) throws IOException {
			return delegate().newPackParser(in);
		}

		public ObjectReader newReader(Repository db) {
			return delegate().newReader(db);
		}

		public void flush() throws IOException {
			delegate().flush();
		}

		public void release() {
			delegate().release();
		}
	}

	/** Digest to compute the name of an object. */
	private final MessageDigest digest;

	/** Temporary working buffer for streaming data through. */
	private byte[] tempBuffer;

	/** Create a new inserter for a database. */
	protected ObjectInserter() {
		digest = Constants.newMessageDigest();
	}

	/**
	 * Obtain a temporary buffer for use by the ObjectInserter or its subclass.
	 * <p>
	 * This buffer is supplied by the ObjectInserter base class to itself and
	 * its subclasses for the purposes of pulling data from a supplied
	 * InputStream, passing it through a Deflater, or formatting the canonical
	 * format of a small object like a small tree or commit.
	 * <p>
	 * <strong>This buffer IS NOT for translation such as auto-CRLF or content
	 * filtering and must not be used for such purposes.</strong>
	 * <p>
	 * The returned buffer is small, around a few KiBs, and the size may change
	 * between versions of JGit. Callers using this buffer must always check the
	 * length of the returned array to ascertain how much space was provided.
	 * <p>
	 * There is a single buffer for each ObjectInserter, repeated calls to this
	 * method will (usually) always return the same buffer. If the caller needs
	 * more than one buffer, or needs a buffer of a larger size, it must manage
	 * that buffer on its own.
	 * <p>
	 * The buffer is usually on first demand for a buffer.
	 *
	 * @return a temporary byte array for use by the caller.
	 */
	protected byte[] buffer() {
		byte[] b = tempBuffer;
		if (b == null)
			tempBuffer = b = new byte[8192];
		return b;
	}

	/** @return digest to help compute an ObjectId */
	protected MessageDigest digest() {
		digest.reset();
		return digest;
	}

	/**
	 * Compute the name of an object, without inserting it.
	 *
	 * @param type
	 *            type code of the object to store.
	 * @param data
	 *            complete content of the object.
	 * @return the name of the object.
	 */
	public ObjectId idFor(int type, byte[] data) {
		return idFor(type, data, 0, data.length);
	}

	/**
	 * Compute the name of an object, without inserting it.
	 *
	 * @param type
	 *            type code of the object to store.
	 * @param data
	 *            complete content of the object.
	 * @param off
	 *            first position within {@code data}.
	 * @param len
	 *            number of bytes to copy from {@code data}.
	 * @return the name of the object.
	 */
	public ObjectId idFor(int type, byte[] data, int off, int len) {
		MessageDigest md = digest();
		md.update(Constants.encodedTypeString(type));
		md.update((byte) ' ');
		md.update(Constants.encodeASCII(len));
		md.update((byte) 0);
		md.update(data, off, len);
		return ObjectId.fromRaw(md.digest());
	}

	/**
	 * Compute the name of an object, without inserting it.
	 *
	 * @param objectType
	 *            type code of the object to store.
	 * @param length
	 *            number of bytes to scan from {@code in}.
	 * @param in
	 *            stream providing the object content. The caller is responsible
	 *            for closing the stream.
	 * @return the name of the object.
	 * @throws IOException
	 *             the source stream could not be read.
	 */
	public ObjectId idFor(int objectType, long length, InputStream in)
			throws IOException {
		MessageDigest md = digest();
		md.update(Constants.encodedTypeString(objectType));
		md.update((byte) ' ');
		md.update(Constants.encodeASCII(length));
		md.update((byte) 0);
		byte[] buf = buffer();
		while (length > 0) {
			int n = in.read(buf, 0, (int) Math.min(length, buf.length));
			if (n < 0)
				throw new EOFException("Unexpected end of input");
			md.update(buf, 0, n);
			length -= n;
		}
		return ObjectId.fromRaw(md.digest());
	}

	/**
	 * Compute the ObjectId for the given tree without inserting it.
	 *
	 * @param formatter
	 * @return the computed ObjectId
	 */
	public ObjectId idFor(TreeFormatter formatter) {
		return formatter.computeId(this);
	}

	/**
	 * Insert a single tree into the store, returning its unique name.
	 *
	 * @param formatter
	 *            the formatter containing the proposed tree's data.
	 * @return the name of the tree object.
	 * @throws IOException
	 *             the object could not be stored.
	 */
	public final ObjectId insert(TreeFormatter formatter) throws IOException {
		// Delegate to the formatter, as then it can pass the raw internal
		// buffer back to this inserter, avoiding unnecessary data copying.
		//
		return formatter.insertTo(this);
	}

	/**
	 * Insert a single commit into the store, returning its unique name.
	 *
	 * @param builder
	 *            the builder containing the proposed commit's data.
	 * @return the name of the commit object.
	 * @throws IOException
	 *             the object could not be stored.
	 */
	public final ObjectId insert(CommitBuilder builder) throws IOException {
		return insert(Constants.OBJ_COMMIT, builder.build());
	}

	/**
	 * Insert a single annotated tag into the store, returning its unique name.
	 *
	 * @param builder
	 *            the builder containing the proposed tag's data.
	 * @return the name of the tag object.
	 * @throws IOException
	 *             the object could not be stored.
	 */
	public final ObjectId insert(TagBuilder builder) throws IOException {
		return insert(Constants.OBJ_TAG, builder.build());
	}

	/**
	 * Insert a single object into the store, returning its unique name.
	 *
	 * @param type
	 *            type code of the object to store.
	 * @param data
	 *            complete content of the object.
	 * @return the name of the object.
	 * @throws IOException
	 *             the object could not be stored.
	 */
	public ObjectId insert(final int type, final byte[] data)
			throws IOException {
		return insert(type, data, 0, data.length);
	}

	/**
	 * Insert a single object into the store, returning its unique name.
	 *
	 * @param type
	 *            type code of the object to store.
	 * @param data
	 *            complete content of the object.
	 * @param off
	 *            first position within {@code data}.
	 * @param len
	 *            number of bytes to copy from {@code data}.
	 * @return the name of the object.
	 * @throws IOException
	 *             the object could not be stored.
	 */
	public ObjectId insert(int type, byte[] data, int off, int len)
			throws IOException {
		return insert(type, len, new ByteArrayInputStream(data, off, len));
	}

	/**
	 * Insert a single object into the store, returning its unique name.
	 *
	 * @param objectType
	 *            type code of the object to store.
	 * @param length
	 *            number of bytes to copy from {@code in}.
	 * @param in
	 *            stream providing the object content. The caller is responsible
	 *            for closing the stream.
	 * @return the name of the object.
	 * @throws IOException
	 *             the object could not be stored, or the source stream could
	 *             not be read.
	 */
	public abstract ObjectId insert(int objectType, long length, InputStream in)
			throws IOException;

	/**
	 * Initialize a parser to read from a pack formatted stream.
	 *
	 * @param in
	 *            the input stream. The stream is not closed by the parser, and
	 *            must instead be closed by the caller once parsing is complete.
	 * @return the pack parser.
	 * @throws IOException
	 *             the parser instance, which can be configured and then used to
	 *             parse objects into the ObjectDatabase.
	 */
	public abstract PackParser newPackParser(InputStream in) throws IOException;

	/**
	 * Open a reader for objects that may have been written by this inserter.
	 * <p>
	 * Prevents callers from having to flush before objects become available.
	 * Should only be used from the same thread as the inserter; objects written
	 * by this inserter might not be visible to
	 * {@code this.newReader().newReader()}.
	 * <p>
	 * The default implementation calls {@link #flush()} on this inserter before
	 * each read; subclasses should provide more efficient implementations.
	 *
	 * @param db
	 *            fallback repository.
	 * @return reader for previously-written objects.
	 */
	public ObjectReader newReader(Repository db) {
		final ObjectReader reader = db.newObjectReader();
		return new ObjectReader() {
			@Override
			public ObjectReader newReader() {
				return reader.newReader();
			}

			@Override
			public Collection<ObjectId> resolve(AbbreviatedObjectId id)
					throws IOException {
				flush();
				return reader.resolve(id);
			}

			@Override
			public ObjectLoader open(AnyObjectId objectId, int typeHint)
					throws MissingObjectException, IncorrectObjectTypeException,
					IOException {
				flush();
				return reader.open(objectId, typeHint);
			}

			@Override
			public Set<ObjectId> getShallowCommits() throws IOException {
				flush();
				return reader.getShallowCommits();
			}
		};
	}

	/**
	 * Make all inserted objects visible.
	 * <p>
	 * The flush may take some period of time to make the objects available to
	 * other threads.
	 *
	 * @throws IOException
	 *             the flush could not be completed; objects inserted thus far
	 *             are in an indeterminate state.
	 */
	public abstract void flush() throws IOException;

	/**
	 * Release any resources used by this inserter.
	 * <p>
	 * An inserter that has been released can be used again, but may need to be
	 * released after the subsequent usage.
	 */
	public abstract void release();
}
