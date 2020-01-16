/*
 * Copyright (C) 2012, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
