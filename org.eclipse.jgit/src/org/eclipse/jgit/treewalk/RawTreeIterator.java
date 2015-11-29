/*
 * Copyright (C) 2015, Google Inc.
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

import static org.eclipse.jgit.lib.Constants.CHARSET;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;
import static org.eclipse.jgit.lib.Constants.encode;
import static org.eclipse.jgit.lib.FileMode.TYPE_MASK;
import static org.eclipse.jgit.lib.FileMode.TYPE_TREE;
import static org.eclipse.jgit.util.RawParseUtils.decode;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;

/**
 * Parses raw Git trees from the canonical format.
 * <p>
 * This is a simplified and lightweight iterator compared to the standard
 * {@link CanonicalTreeParser}. To improve performance the iterable uses itself
 * as its own iterator, and returns itself from {@link #next()}.
 *
 * @since 4.2
 */
public class RawTreeIterator implements Iterable, Iterator<RawTreeIterator> {
	private final byte[] raw;
	private int mode;
	private int namePtr;
	private int nextPtr;

	/**
	 * Create a new iterator to read the given tree contents.
	 *
	 * @param raw
	 *            contents of the tree.
	 */
	public RawTreeIterator(byte[] raw) {
		this.raw = raw;
	}

	/**
	 * Create a new iterator to read the given tree contents.
	 *
	 * @param reader
	 *            reader to access the tree data.
	 * @param id
	 *            identity of the tree to read.
	 * @throws IncorrectObjectTypeException
	 *             the named object is not a tree.
	 * @throws MissingObjectException
	 *             the named object does not exist.
	 * @throws LargeObjectException
	 *             the named object is too large to read as a tree.
	 * @throws IOException
	 *             the object cannot be read from disk.
	 */
	public RawTreeIterator(ObjectReader reader, AnyObjectId id)
			throws LargeObjectException, MissingObjectException,
			IncorrectObjectTypeException, IOException {
		this(reader.open(id, OBJ_TREE).getCachedBytes());
	}

	/** @return raw byte array storing the tree's contents. */
	public final byte[] buffer() {
		return raw;
	}

	/** @return position of the entry name in {@link #buffer()}. */
	public final int nameOffset() {
		return namePtr;
	}

	private final int nameEnd() {
		return nextPtr - (1 + OBJECT_ID_LENGTH);
	}

	/** @return position of the ObjectId in {@link #buffer()}. */
	public final int idOffset() {
		return nextPtr - OBJECT_ID_LENGTH;
	}

	/** @return the file mode of the current entry. */
	public final FileMode getFileMode() {
		return FileMode.fromBits(mode);
	}

	/** @return mode bits parsed from the current entry. */
	public final int getRawMode() {
		return mode;
	}

	/** @return name of the current entry, as a string. */
	public final String getName() {
		return decode(CHARSET, raw, namePtr, nameEnd());
	}

	/**
	 * Seek the iterator on a file if present.
	 *
	 * @param name
	 *            file name to find (will not find a directory).
	 * @return true if the file exists in this tree; false if it does not exist.
	 */
	public final boolean findFile(String name) {
		return findFile(encode(name));
	}

	/**
	 * Seek the iterator on a file, if present.
	 *
	 * @param name
	 *            file name to find (will not find a directory).
	 * @return true if the file exists in this tree; false if it does not exist.
	 */
	public final boolean findFile(byte[] name) {
		while (hasNext()) {
			next();
			int cmp = nameCompare(name);
			if (cmp == 0) {
				return true;
			} else if (cmp > 0) {
				return false;
			}
		}
		return false;
	}

	/**
	 * Compare a name to the current entry's name.
	 *
	 * @param b file name to compare to.
	 * @return &lt;0 if the current entry sorts first;
	 *         0 if the entries are equal;
	 *         &gt;0 if b's name sorts first.
	 */
	public final int nameCompare(byte[] b) {
		return nameCompare(0, b, 0, b.length);
	}

	/**
	 * Compare a name to the current entry's name.
	 *
	 * @param bMode mode bits for entry b.
	 * @param b name to compare to.
	 * @param bPos first position in b of the name.
	 * @param bEnd one past the last position of b holding the name.
	 * @return &lt;0 if the current entry sorts first;
	 *         0 if the entries are equal;
	 *         &gt;0 if b's name sorts first.
	 */
	public final int nameCompare(int bMode, byte[] b, int bPos, int bEnd) {
		int aPos = nameOffset();
		int aEnd = nameEnd();
		for (; aPos < aEnd && bPos < bEnd; aPos++, bPos++) {
			int cmp = (raw[aPos] & 0xff) - (b[bPos] & 0xff);
			if (cmp != 0) {
				return cmp;
			}
		}
		if (aPos < aEnd) {
			return (raw[aPos] & 0xff) - lastPathChar(bMode);
		}
		if (bPos < bEnd) {
			return lastPathChar(mode) - (b[bPos] & 0xff);
		}
		return lastPathChar(mode) - lastPathChar(bMode);
	}

	private static int lastPathChar(int mode) {
		return (mode & TYPE_MASK) == TYPE_TREE ? '/' : '\0';
	}

	/**
	 * Get the object id of the current entry.
	 *
	 * @return an object id for the current entry.
	 */
	public final ObjectId getObjectId() {
		return ObjectId.fromRaw(raw, idOffset());
	}

	/**
	 * Copy the ObjectId for the current entry.
	 *
	 * @param out
	 *            buffer to copy the object id into.
	 */
	public final void getObjectId(MutableObjectId out) {
		out.fromRaw(raw, idOffset());
	}

	/**
	 * Resets this parser to the beginning and returns {@code this}.
	 *
	 * @returns {@code this}.
	 */
	@Override
	public final Iterator iterator() {
		nextPtr = 0;
		return this;
	}

	@Override
	public final boolean hasNext() {
		return nextPtr != raw.length;
	}

	@Override
	public final RawTreeIterator next() {
		if (nextPtr == raw.length) {
			throw new NoSuchElementException();
		}

		int ptr = nextPtr;
		byte c = raw[ptr++];
		int tmp = c - '0';
		for (;;) {
			c = raw[ptr++];
			if (' ' == c) {
				break;
			}
			tmp <<= 3;
			tmp += c - '0';
		}
		mode = tmp;

		namePtr = ptr;
		while (raw[ptr] != 0) {
			ptr++;
		}
		nextPtr = ptr + (1 + OBJECT_ID_LENGTH);
		return this;
	}

	@Override
	public final void remove() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String toString() {
		return String.format("RawTreeIterator[%o %s %s]", //$NON-NLS-1$
				Integer.valueOf(mode), getName(), getObjectId().name());
	}
}
