/*
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
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

package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.UnsupportedPackIndexVersionException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdSet;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.io.SilentFileInputStream;

/**
 * Access path to locate objects by {@link org.eclipse.jgit.lib.ObjectId} in a
 * {@link org.eclipse.jgit.internal.storage.file.PackFile}.
 * <p>
 * Indexes are strictly redundant information in that we can rebuild all of the
 * data held in the index file from the on disk representation of the pack file
 * itself, but it is faster to access for random requests because data is stored
 * by ObjectId.
 * </p>
 */
public abstract class PackIndex
		implements Iterable<PackIndex.MutableEntry>, ObjectIdSet {
	/**
	 * Open an existing pack <code>.idx</code> file for reading.
	 * <p>
	 * The format of the file will be automatically detected and a proper access
	 * implementation for that format will be constructed and returned to the
	 * caller. The file may or may not be held open by the returned instance.
	 * </p>
	 *
	 * @param idxFile
	 *            existing pack .idx to read.
	 * @return access implementation for the requested file.
	 * @throws FileNotFoundException
	 *             the file does not exist.
	 * @throws java.io.IOException
	 *             the file exists but could not be read due to security errors,
	 *             unrecognized data version, or unexpected data corruption.
	 */
	public static PackIndex open(final File idxFile) throws IOException {
		try (SilentFileInputStream fd = new SilentFileInputStream(
				idxFile)) {
				return read(fd);
		} catch (IOException ioe) {
			throw new IOException(
					MessageFormat.format(JGitText.get().unreadablePackIndex,
							idxFile.getAbsolutePath()),
					ioe);
		}
	}

	/**
	 * Read an existing pack index file from a buffered stream.
	 * <p>
	 * The format of the file will be automatically detected and a proper access
	 * implementation for that format will be constructed and returned to the
	 * caller. The file may or may not be held open by the returned instance.
	 *
	 * @param fd
	 *            stream to read the index file from. The stream must be
	 *            buffered as some small IOs are performed against the stream.
	 *            The caller is responsible for closing the stream.
	 * @return a copy of the index in-memory.
	 * @throws java.io.IOException
	 *             the stream cannot be read.
	 * @throws org.eclipse.jgit.errors.CorruptObjectException
	 *             the stream does not contain a valid pack index.
	 */
	public static PackIndex read(InputStream fd) throws IOException,
			CorruptObjectException {
		final byte[] hdr = new byte[8];
		IO.readFully(fd, hdr, 0, hdr.length);
		if (isTOC(hdr)) {
			final int v = NB.decodeInt32(hdr, 4);
			switch (v) {
			case 2:
				return new PackIndexV2(fd);
			default:
				throw new UnsupportedPackIndexVersionException(v);
			}
		}
		return new PackIndexV1(fd, hdr);
	}

	private static boolean isTOC(final byte[] h) {
		final byte[] toc = PackIndexWriter.TOC;
		for (int i = 0; i < toc.length; i++)
			if (h[i] != toc[i])
				return false;
		return true;
	}

	/** Footer checksum applied on the bottom of the pack file. */
	protected byte[] packChecksum;

	/**
	 * Determine if an object is contained within the pack file.
	 *
	 * @param id
	 *            the object to look for. Must not be null.
	 * @return true if the object is listed in this index; false otherwise.
	 */
	public boolean hasObject(final AnyObjectId id) {
		return findOffset(id) != -1;
	}

	/** {@inheritDoc} */
	@Override
	public boolean contains(AnyObjectId id) {
		return findOffset(id) != -1;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Provide iterator that gives access to index entries. Note, that iterator
	 * returns reference to mutable object, the same reference in each call -
	 * for performance reason. If client needs immutable objects, it must copy
	 * returned object on its own.
	 * <p>
	 * Iterator returns objects in SHA-1 lexicographical order.
	 * </p>
	 */
	@Override
	public abstract Iterator<MutableEntry> iterator();

	/**
	 * Obtain the total number of objects described by this index.
	 *
	 * @return number of objects in this index, and likewise in the associated
	 *         pack that this index was generated from.
	 */
	public abstract long getObjectCount();

	/**
	 * Obtain the total number of objects needing 64 bit offsets.
	 *
	 * @return number of objects in this index using a 64 bit offset; that is an
	 *         object positioned after the 2 GB position within the file.
	 */
	public abstract long getOffset64Count();

	/**
	 * Get ObjectId for the n-th object entry returned by {@link #iterator()}.
	 * <p>
	 * This method is a constant-time replacement for the following loop:
	 *
	 * <pre>
	 * Iterator&lt;MutableEntry&gt; eItr = index.iterator();
	 * int curPosition = 0;
	 * while (eItr.hasNext() &amp;&amp; curPosition++ &lt; nthPosition)
	 * 	eItr.next();
	 * ObjectId result = eItr.next().toObjectId();
	 * </pre>
	 *
	 * @param nthPosition
	 *            position within the traversal of {@link #iterator()} that the
	 *            caller needs the object for. The first returned
	 *            {@link org.eclipse.jgit.internal.storage.file.PackIndex.MutableEntry}
	 *            is 0, the second is 1, etc.
	 * @return the ObjectId for the corresponding entry.
	 */
	public abstract ObjectId getObjectId(long nthPosition);

	/**
	 * Get ObjectId for the n-th object entry returned by {@link #iterator()}.
	 * <p>
	 * This method is a constant-time replacement for the following loop:
	 *
	 * <pre>
	 * Iterator&lt;MutableEntry&gt; eItr = index.iterator();
	 * int curPosition = 0;
	 * while (eItr.hasNext() &amp;&amp; curPosition++ &lt; nthPosition)
	 * 	eItr.next();
	 * ObjectId result = eItr.next().toObjectId();
	 * </pre>
	 *
	 * @param nthPosition
	 *            unsigned 32 bit position within the traversal of
	 *            {@link #iterator()} that the caller needs the object for. The
	 *            first returned
	 *            {@link org.eclipse.jgit.internal.storage.file.PackIndex.MutableEntry}
	 *            is 0, the second is 1, etc. Positions past 2**31-1 are
	 *            negative, but still valid.
	 * @return the ObjectId for the corresponding entry.
	 */
	public final ObjectId getObjectId(final int nthPosition) {
		if (nthPosition >= 0)
			return getObjectId((long) nthPosition);
		final int u31 = nthPosition >>> 1;
		final int one = nthPosition & 1;
		return getObjectId(((long) u31) << 1 | one);
	}

	/**
	 * Get offset in a pack for the n-th object entry returned by
	 * {@link #iterator()}.
	 *
	 * @param nthPosition
	 *            unsigned 32 bit position within the traversal of
	 *            {@link #iterator()} for which the caller needs the offset. The
	 *            first returned {@link MutableEntry} is 0, the second is 1,
	 *            etc. Positions past 2**31-1 are negative, but still valid.
	 * @return the offset in a pack for the corresponding entry.
	 */
	abstract long getOffset(long nthPosition);

	/**
	 * Locate the file offset position for the requested object.
	 *
	 * @param objId
	 *            name of the object to locate within the pack.
	 * @return offset of the object's header and compressed content; -1 if the
	 *         object does not exist in this index and is thus not stored in the
	 *         associated pack.
	 */
	public abstract long findOffset(AnyObjectId objId);

	/**
	 * Retrieve stored CRC32 checksum of the requested object raw-data
	 * (including header).
	 *
	 * @param objId
	 *            id of object to look for
	 * @return CRC32 checksum of specified object (at 32 less significant bits)
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             when requested ObjectId was not found in this index
	 * @throws java.lang.UnsupportedOperationException
	 *             when this index doesn't support CRC32 checksum
	 */
	public abstract long findCRC32(AnyObjectId objId)
			throws MissingObjectException, UnsupportedOperationException;

	/**
	 * Check whether this index supports (has) CRC32 checksums for objects.
	 *
	 * @return true if CRC32 is stored, false otherwise
	 */
	public abstract boolean hasCRC32Support();

	/**
	 * Find objects matching the prefix abbreviation.
	 *
	 * @param matches
	 *            set to add any located ObjectIds to. This is an output
	 *            parameter.
	 * @param id
	 *            prefix to search for.
	 * @param matchLimit
	 *            maximum number of results to return. At most this many
	 *            ObjectIds should be added to matches before returning.
	 * @throws java.io.IOException
	 *             the index cannot be read.
	 */
	public abstract void resolve(Set<ObjectId> matches, AbbreviatedObjectId id,
			int matchLimit) throws IOException;

	/**
	 * Represent mutable entry of pack index consisting of object id and offset
	 * in pack (both mutable).
	 *
	 */
	public static class MutableEntry {
		final MutableObjectId idBuffer = new MutableObjectId();

		long offset;

		/**
		 * Returns offset for this index object entry
		 *
		 * @return offset of this object in a pack file
		 */
		public long getOffset() {
			return offset;
		}

		/** @return hex string describing the object id of this entry. */
		public String name() {
			ensureId();
			return idBuffer.name();
		}

		/** @return a copy of the object id. */
		public ObjectId toObjectId() {
			ensureId();
			return idBuffer.toObjectId();
		}

		/** @return a complete copy of this entry, that won't modify */
		public MutableEntry cloneEntry() {
			final MutableEntry r = new MutableEntry();
			ensureId();
			r.idBuffer.fromObjectId(idBuffer);
			r.offset = offset;
			return r;
		}

		void ensureId() {
			// Override in implementations.
		}
	}

	abstract class EntriesIterator implements Iterator<MutableEntry> {
		protected final MutableEntry entry = initEntry();

		protected long returnedNumber = 0;

		protected abstract MutableEntry initEntry();

		@Override
		public boolean hasNext() {
			return returnedNumber < getObjectCount();
		}

		/**
		 * Implementation must update {@link #returnedNumber} before returning
		 * element.
		 */
		@Override
		public abstract MutableEntry next();

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
}
