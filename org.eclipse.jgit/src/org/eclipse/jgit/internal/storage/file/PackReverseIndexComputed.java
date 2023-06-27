/*
 * Copyright (C) 2023, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import java.text.MessageFormat;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.PackIndex.MutableEntry;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Reverse index for forward pack index which is computed from the forward pack
 * index.
 * <p>
 * Creating an instance uses an insertion sort of the entries in the forward
 * index, so it runs in quadratic time on average.
 */
final class PackReverseIndexComputed implements PackReverseIndex {
	/**
	 * Index we were created from, and that has our ObjectId data.
	 */
	private final PackIndex index;

	/**
	 * The number of offsets each bucket in indexPosInOffsetOrder could contain.
	 */
	private final long bucketSize;

	/**
	 * The indexes into indexPosInOffsetOrder at which the next bucket starts.
	 *
	 * For example, given offset o (and therefore bucket = o / bucketSize), the
	 * indexPos corresponding to o will be contained in the range
	 * indexPosInOffsetOrder[nextBucketStart[bucket - 1]] inclusive to
	 * indexPosInOffsetOrder[nextBucketStart[bucket]] exclusive.
	 *
	 * This range information can speed up #binarySearch by identifying the
	 * relevant bucket and only searching within its range.
	 * <p>
	 * See {@link #binarySearch}
	 */
	private final int[] nextBucketStart;

	/**
	 * Mapping from indices in offset order to indices in SHA-1 order.
	 */
	private final int[] indexPosInOffsetOrder;

	/**
	 * Create reverse index from straight/forward pack index, by indexing all
	 * its entries.
	 *
	 * @param packIndex
	 *            forward index - entries to (reverse) index.
	 */
	PackReverseIndexComputed(PackIndex packIndex) {
		index = packIndex;

		long rawCnt = index.getObjectCount();
		if (rawCnt + 1 > Integer.MAX_VALUE) {
			throw new IllegalArgumentException(
					JGitText.get().hugeIndexesAreNotSupportedByJgitYet);
		}
		int cnt = (int) rawCnt;

		if (cnt == 0) {
			bucketSize = Long.MAX_VALUE;
			nextBucketStart = new int[1];
			indexPosInOffsetOrder = new int[0];
			return;
		}

		// Sort the index positions according to the corresponding pack offsets.
		// Use bucket sort since the offsets are somewhat uniformly distributed
		// over the range (0, pack size).
		long[] offsetsInIndexOrder = new long[cnt];
		long maxOffset = 0;
		int i = 0;
		for (MutableEntry entry : index) {
			long offset = entry.getOffset();
			offsetsInIndexOrder[i++] = offset;
			if (offset > maxOffset) {
				maxOffset = offset;
			}
		}

		bucketSize = maxOffset / cnt + 1;
		// The first value in each bucket, also used as the index of any further
		// bucket values.
		int[] headValues = new int[cnt];
		// If there is more than one value in a bucket, they are stored here,
		// with each value being the index for the next value. There won't be
		// any collisions because every index position is unique.
		int[] furtherValues = new int[cnt + 1];
		for (int indexPos = 0; indexPos < cnt; indexPos++) {
			// The offset determines which bucket this index position falls
			// into, since the goal is sort into offset order.
			long offset = offsetsInIndexOrder[indexPos];
			int bucketIdx = (int) (offset / bucketSize);
			// Store the index positions as 1-indexed so that default
			// initialized value 0 can be interpreted as the end of the bucket
			// values.
			int asBucketValue = indexPos + 1;
			// If there is an existing value in this bucket, move it into the
			// further values array before replacing it.
			int current = headValues[bucketIdx];
			headValues[bucketIdx] = asBucketValue;
			furtherValues[asBucketValue] = current;
		}

		int nextEmptyIdx = 0;
		indexPosInOffsetOrder = new int[cnt];
		nextBucketStart = headValues; // Reuse the allocation
		for (int bucketIdx = 0; bucketIdx < headValues.length; bucketIdx++) {
			// Insertion sort of the values in the bucket.
			int startIdx = nextEmptyIdx;
			// Each value is also an index pointing to the next value, starting
			// at the head value.
			for (int bucketValue = headValues[bucketIdx]; bucketValue > 0; bucketValue = furtherValues[bucketValue]) {
				// Reverse the incrementation to get back to 0-indexed.
				int indexPos = bucketValue - 1;
				long offset = offsetsInIndexOrder[indexPos];
				int writeIdx;
				// Move any previous values with a bigger offset over one spot.
				for (writeIdx = nextEmptyIdx++; startIdx < writeIdx; writeIdx--) {
					int prevIndexPos = indexPosInOffsetOrder[writeIdx - 1];
					if (offset > offsetsInIndexOrder[prevIndexPos]) {
						break;
					}
					indexPosInOffsetOrder[writeIdx] = indexPosInOffsetOrder[writeIdx
							- 1];
				}
				// Insert just after any previous values with smaller offsets.
				indexPosInOffsetOrder[writeIdx] = indexPos;
			}
			// The value at the shared allocation can now be overwritten safely.
			nextBucketStart[bucketIdx] = nextEmptyIdx;
		}
	}

	@Override
	public ObjectId findObject(long offset) {
		final int ith = binarySearch(offset);
		if (ith < 0) {
			return null;
		}
		return index.getObjectId(indexPosInOffsetOrder[ith]);
	}

	@Override
	public long findNextOffset(long offset, long maxOffset)
			throws CorruptObjectException {
		final int ith = binarySearch(offset);
		if (ith < 0) {
			throw new CorruptObjectException(MessageFormat.format(JGitText
					.get().cantFindObjectInReversePackIndexForTheSpecifiedOffset,
					Long.valueOf(offset)));
		}

		if (ith + 1 == indexPosInOffsetOrder.length) {
			return maxOffset;
		}
		return index.getOffset(indexPosInOffsetOrder[ith + 1]);
	}

	@Override
	public int findPosition(long offset) {
		return binarySearch(offset);
	}

	private int binarySearch(long offset) {
		int bucket = (int) (offset / bucketSize);
		int low = bucket == 0 ? 0 : nextBucketStart[bucket - 1];
		int high = nextBucketStart[bucket];
		while (low < high) {
			final int mid = (low + high) >>> 1;
			final long o = index.getOffset(indexPosInOffsetOrder[mid]);
			if (offset < o) {
				high = mid;
			} else if (offset == o) {
				return mid;
			} else {
				low = mid + 1;
			}
		}
		return -1;
	}

	@Override
	public ObjectId findObjectByPosition(int nthPosition) {
		return index.getObjectId(indexPosInOffsetOrder[nthPosition]);
	}
}
