/*
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
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

package org.eclipse.jgit.storage.file;

import java.text.MessageFormat;
import java.util.Arrays;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.storage.file.PackIndex.MutableEntry;

/**
 * <p>
 * Reverse index for forward pack index. Provides operations based on offset
 * instead of object id. Such offset-based reverse lookups are performed in
 * O(log n) time.
 * </p>
 *
 * @see PackIndex
 * @see PackFile
 */
public class PackReverseIndex {
	/** Index we were created from, and that has our ObjectId data. */
	private final PackIndex index;

	/**
	 * (offset31, truly) Offsets accommodating in 31 bits.
	 */
	private final int offsets32[];

	/**
	 * Offsets not accommodating in 31 bits.
	 */
	private final long offsets64[];

	/** Position of the corresponding {@link #offsets32} in {@link #index}. */
	private final int nth32[];

	/** Position of the corresponding {@link #offsets64} in {@link #index}. */
	private final int nth64[];

	/**
	 * Create reverse index from straight/forward pack index, by indexing all
	 * its entries.
	 *
	 * @param packIndex
	 *            forward index - entries to (reverse) index.
	 */
	public PackReverseIndex(final PackIndex packIndex) {
		index = packIndex;

		final long cnt = index.getObjectCount();
		final long n64 = index.getOffset64Count();
		final long n32 = cnt - n64;
		if (n32 > Integer.MAX_VALUE || n64 > Integer.MAX_VALUE
				|| cnt > 0xffffffffL)
			throw new IllegalArgumentException(
					JGitText.get().hugeIndexesAreNotSupportedByJgitYet);

		offsets32 = new int[(int) n32];
		offsets64 = new long[(int) n64];
		nth32 = new int[offsets32.length];
		nth64 = new int[offsets64.length];

		int i32 = 0;
		int i64 = 0;
		for (final MutableEntry me : index) {
			final long o = me.getOffset();
			if (o < Integer.MAX_VALUE)
				offsets32[i32++] = (int) o;
			else
				offsets64[i64++] = o;
		}

		Arrays.sort(offsets32);
		Arrays.sort(offsets64);

		int nth = 0;
		for (final MutableEntry me : index) {
			final long o = me.getOffset();
			if (o < Integer.MAX_VALUE)
				nth32[Arrays.binarySearch(offsets32, (int) o)] = nth++;
			else
				nth64[Arrays.binarySearch(offsets64, o)] = nth++;
		}
	}

	/**
	 * Search for object id with the specified start offset in this pack
	 * (reverse) index.
	 *
	 * @param offset
	 *            start offset of object to find.
	 * @return object id for this offset, or null if no object was found.
	 */
	public ObjectId findObject(final long offset) {
		if (offset <= Integer.MAX_VALUE) {
			final int i32 = Arrays.binarySearch(offsets32, (int) offset);
			if (i32 < 0)
				return null;
			return index.getObjectId(nth32[i32]);
		} else {
			final int i64 = Arrays.binarySearch(offsets64, offset);
			if (i64 < 0)
				return null;
			return index.getObjectId(nth64[i64]);
		}
	}

	/**
	 * Search for the next offset to the specified offset in this pack (reverse)
	 * index.
	 *
	 * @param offset
	 *            start offset of previous object (must be valid-existing
	 *            offset).
	 * @param maxOffset
	 *            maximum offset in a pack (returned when there is no next
	 *            offset).
	 * @return offset of the next object in a pack or maxOffset if provided
	 *         offset was the last one.
	 * @throws CorruptObjectException
	 *             when there is no object with the provided offset.
	 */
	public long findNextOffset(final long offset, final long maxOffset)
			throws CorruptObjectException {
		if (offset <= Integer.MAX_VALUE) {
			final int i32 = Arrays.binarySearch(offsets32, (int) offset);
			if (i32 < 0)
				throw new CorruptObjectException(
						MessageFormat.format(
								JGitText.get().cantFindObjectInReversePackIndexForTheSpecifiedOffset,
								Long.valueOf(offset)));

			if (i32 + 1 == offsets32.length) {
				if (offsets64.length > 0)
					return offsets64[0];
				return maxOffset;
			}
			return offsets32[i32 + 1];
		} else {
			final int i64 = Arrays.binarySearch(offsets64, offset);
			if (i64 < 0)
				throw new CorruptObjectException(
						MessageFormat.format(
								JGitText.get().cantFindObjectInReversePackIndexForTheSpecifiedOffset,
								Long.valueOf(offset)));

			if (i64 + 1 == offsets64.length)
				return maxOffset;
			return offsets64[i64 + 1];
		}
	}

	int findPostion(long offset) {
		if (offset <= Integer.MAX_VALUE) {
			final int i32 = Arrays.binarySearch(offsets32, (int) offset);
			if (i32 < 0)
				return -1;
			return i32;
		} else {
			final int i64 = Arrays.binarySearch(offsets64, offset);
			if (i64 < 0)
				return -1;
			return nth32.length + i64;
		}
	}

	ObjectId findObjectByPosition(int nthPosition) {
		if (nthPosition < nth32.length)
			return index.getObjectId(nth32[nthPosition]);
		final int i64 = nthPosition - nth32.length;
		return index.getObjectId(nth64[i64]);
	}
}
