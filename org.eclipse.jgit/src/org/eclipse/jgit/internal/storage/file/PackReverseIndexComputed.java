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
	private long bucketSize;

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
	private int[] nextBucketStart;

	/**
	 * Mapping from indices in offset order to indices in SHA-1 order.
	 */
	private int[] indexPosInOffsetOrder;

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

		initializeIndexPosInOffsetOrder(cnt);
	}

	/**
	 * Sort the index positions according to the corresponding pack offsets. Use
	 * bucket sort since the offsets are somewhat uniformly distributed over the
	 * range (0, pack size).
	 *
	 * Bucket sort will partition the index position values into separate
	 * buckets according to their corresponding pack offsets. If the assumption
	 * that the offsets are uniformly distributed holds, then each bucket will
	 * contain 1 value on average.
	 *
	 * The buckets are stored in two arrays. The first array holds the first
	 * value in each bucket. The value itself is used to index into the second
	 * array, which holds any further values for the bucket. Each further value
	 * in the second array will itself serve as an index for the next value in
	 * the bucket.
	 *
	 * In this way, the representation is like a linked list per bucket. This
	 * representation may be compacted into two arrays because each index
	 * position value is unique in the range [0, cnt), allowing them to be used
	 * as unique array indexes.
	 *
	 * The final sorted result is written into the single array
	 * indexPosInOffsetOrder so that it can be more easily read by the other
	 * methods of this class.
	 * 
	 * @param cnt
	 *            The count of objects in the index.
	 */
	private void initializeIndexPosInOffsetOrder(int cnt) {
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
		int[] headValues = new int[cnt];
		int[] furtherValues = new int[cnt + 1];
		partitionIndexPositionsIntoBuckets(cnt, headValues, furtherValues,
				offsetsInIndexOrder);
		writeSortedBucketsIntoFinalOrder(cnt, headValues, furtherValues,
				offsetsInIndexOrder);
	}

	/**
	 * Partition the index positions into buckets based on their corresponding
	 * offset order.
	 *
	 * Store the index positions as 1-indexed so that default initialized 0
	 * values can be interpreted as the end of a bucket within the array.
	 *
	 * When a value is added to a bucket and there is an existing value in the
	 * bucket, move the existing value into the further values array before
	 * replacing it.
	 *
	 * @param cnt
	 *            The count of objects in the index.
	 * @param headValues
	 *            The first index position value in each bucket, also used as
	 *            the index of any further bucket values.
	 * @param furtherValues
	 *            If there is more than one value in a bucket, they are stored
	 *            here, with each value being the index for the next value.
	 *            There won't be any collisions because every index position is
	 *            unique.
	 * @param offsetsInIndexOrder
	 *            The pack offsets of each object in index position order.
	 */
	private void partitionIndexPositionsIntoBuckets(int cnt, int[] headValues,
			int[] furtherValues, long[] offsetsInIndexOrder) {
		for (int indexPos = 0; indexPos < cnt; indexPos++) {
			long offset = offsetsInIndexOrder[indexPos];
			int bucketIdx = (int) (offset / bucketSize);
			int asBucketValue = indexPos + 1;
			int current = headValues[bucketIdx];
			headValues[bucketIdx] = asBucketValue;
			furtherValues[asBucketValue] = current;
		}
	}

	/**
	 * Sort the values in each bucket using insertion sort, which should be
	 * performant given the assumption that the offsets are somewhat uniformly
	 * distributed. As the buckets are sorted, write the sorted results into
	 * indexPosInOffsetOrder, so that the results can be read simply from a
	 * single array.
	 *
	 * For each bucket, the index position values are sorted one-by-one using
	 * insertion sort, shifting each left one spot until all index position
	 * values to its left correspond to smaller pack offsets. Each next value in
	 * the bucket is found by interpreting the current value as an index into
	 * the further values array.
	 *
	 * Just before writing the values into the final result, decrement the
	 * stored value, which were previously stored as 1-indexed so that default
	 * initialized 0 values could be interpreted as the end of a bucket within
	 * the array.
	 *
	 * @param cnt
	 *            The count of objects in the index.
	 * @param headValues
	 *            The first index position value in each bucket, also used as
	 *            the index of any further bucket values.
	 * @param furtherValues
	 *            If there is more than one value in a bucket, they are stored
	 *            here, with each value being the index for the next value.
	 *            There won't be any collisions because every index position is
	 *            unique.
	 * @param offsetsInIndexOrder
	 *            The pack offsets of each object in index position order.
	 */
	private void writeSortedBucketsIntoFinalOrder(int cnt, int[] headValues,
			int[] furtherValues, long[] offsetsInIndexOrder) {
		int nextEmptyIdx = 0;
		indexPosInOffsetOrder = new int[cnt];
		nextBucketStart = headValues; // Reuse the allocation
		for (int bucketIdx = 0; bucketIdx < headValues.length; bucketIdx++) {
			int startIdx = nextEmptyIdx;
			for (int bucketValue = headValues[bucketIdx]; bucketValue > 0; bucketValue = furtherValues[bucketValue]) {
				int indexPos = bucketValue - 1;
				long offset = offsetsInIndexOrder[indexPos];
				int writeIdx;
				for (writeIdx = nextEmptyIdx++; startIdx < writeIdx; writeIdx--) {
					int prevIndexPos = indexPosInOffsetOrder[writeIdx - 1];
					if (offset > offsetsInIndexOrder[prevIndexPos]) {
						break;
					}
					indexPosInOffsetOrder[writeIdx] = indexPosInOffsetOrder[writeIdx
							- 1];
				}
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
