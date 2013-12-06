/*
 * Copyright (C) 2012, Google Inc.
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

import com.googlecode.javaewah.EWAHCompressedBitmap;
import com.googlecode.javaewah.IntIterator;

/**
 * A wrapper around the EWAHCompressedBitmap optimized for the contains
 * operation.
 */
final class InflatingBitSet {
	private static final long[] EMPTY = new long[0];

	private final EWAHCompressedBitmap bitmap;

	private IntIterator iterator;

	private long[] inflated;

	private int nextPosition = -1;

	private final int sizeInBits;

	InflatingBitSet(EWAHCompressedBitmap bitmap) {
		this(bitmap, EMPTY);
	}

	private InflatingBitSet(EWAHCompressedBitmap orBitmap, long[] inflated) {
		this.bitmap = orBitmap;
		this.inflated = inflated;
		this.sizeInBits = bitmap.sizeInBits();
	}

	final boolean maybeContains(int position) {
		if (get(position))
			return true;
		return nextPosition <= position && position < sizeInBits;
	}

	final boolean contains(int position) {
		if (get(position))
			return true;
		if (position <= nextPosition || position >= sizeInBits)
			return position == nextPosition;

		if (iterator == null) {
			iterator = bitmap.intIterator();
			if (iterator.hasNext())
				nextPosition = iterator.next();
			else
				return false;
		} else if (!iterator.hasNext())
			return false;

		int positionBlock = block(position);
		if (positionBlock >= inflated.length) {
			long[] tmp = new long[block(sizeInBits) + 1];
			System.arraycopy(inflated, 0, tmp, 0, inflated.length);
			inflated = tmp;
		}

		int block = block(nextPosition);
		long word = mask(nextPosition);
		int end = Math.max(nextPosition, position) | 63;
		while (iterator.hasNext()) {
			nextPosition = iterator.next();
			if (end < nextPosition)
				break;

			int b = block(nextPosition);
			long m = mask(nextPosition);
			if (block == b) {
				word |= m;
			} else {
				inflated[block] = word;
				block = b;
				word = m;
			}
		}
		inflated[block] = word;
		return block == positionBlock && (word & mask(position)) != 0;
	}

	private final boolean get(int position) {
		int b = block(position);
		return b < inflated.length && (inflated[b] & mask(position)) != 0;
	}

	private static final int block(int position) {
		return position >> 6;
	}

	private static final long mask(int position) {
		return 1L << position;
	}

	private final boolean isEmpty() {
		return sizeInBits == 0;
	}

	final InflatingBitSet or(EWAHCompressedBitmap other) {
		if (other.sizeInBits() == 0)
			return this;
		return new InflatingBitSet(bitmap.or(other), inflated);
	}

	final InflatingBitSet andNot(EWAHCompressedBitmap other) {
		if (isEmpty())
			return this;
		return new InflatingBitSet(bitmap.andNot(other));
	}

	final InflatingBitSet xor(EWAHCompressedBitmap other) {
		if (isEmpty()) {
			if (other.sizeInBits() == 0)
				return this;
			return new InflatingBitSet(other);
		}
		return new InflatingBitSet(bitmap.xor(other));
	}

	final EWAHCompressedBitmap getBitmap() {
		return bitmap;
	}
}
