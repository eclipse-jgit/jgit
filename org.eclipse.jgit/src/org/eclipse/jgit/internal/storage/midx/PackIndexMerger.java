/*
 * Copyright (C) 2025, Google Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.midx;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Collects n pack indexes and makes them look like one for iteration and
 * counting objects.
 */
class PackIndexMerger {

	static class MutableEntry {
		ObjectId oid;

		int packId;

		long offset;

		void fill(MutableEntry other) {
			this.oid = other.oid;
			this.packId = other.packId;
			this.offset = other.offset;
		}
	}

	private final List<String> packNames;

	private final PackIndex[] indexes;

	private final boolean needsLargeOffsetsChunk;

	private final int offsetsOver31BitsCount;

	private final int uniqueObjectCount;

	private static final int LIMIT_31_BITS = (1 << 31) - 1;

	private static final long LIMIT_32_BITS = (1L << 32) - 1;

	PackIndexMerger(Map<String, PackIndex> packs) {
		this.packNames = packs.keySet().stream().sorted()
				.collect(Collectors.toUnmodifiableList());
		PackIndex[] tmpIndexes = new PackIndex[packNames.size()];
		for (int i = 0; i < packNames.size(); i++) {
			tmpIndexes[i] = packs.get(packNames.get(i));
		}
		this.indexes = tmpIndexes;

		// Iterate for duplicates
		int objectCount = 0;
		boolean hasLargeOffsets = false;
		int over31bits = 0;
		DedupMultiIndexIterator di = (DedupMultiIndexIterator) bySha1Iterator();
		while (di.hasNext()) {
			MutableEntry entry = di.next();
			// If there is at least one offset value larger than 2^32-1, then
			//	the large offset chunk must exist, and offsets larger than
			//	2^31-1 must be stored in it instead
			if (entry.offset > LIMIT_32_BITS) {
				hasLargeOffsets = true;
			}
			if (entry.offset > LIMIT_31_BITS) {
				over31bits++;
			}

			objectCount++;
		}
		uniqueObjectCount = objectCount;
		offsetsOver31BitsCount = over31bits;
		needsLargeOffsetsChunk = hasLargeOffsets;
	}

	int getUniqueObjectCount() {
		return uniqueObjectCount;
	}

	boolean needsLargeOffsetsChunk() {
		return needsLargeOffsetsChunk;
	}

	int getOffsetsOver31BitsCount() {
		return offsetsOver31BitsCount;
	}

	List<String> getPackNames() {
		return packNames;
	}

	int getPackCount() {
		return packNames.size();
	}

	Iterator<MutableEntry> bySha1Iterator() {
		return new DedupMultiIndexIterator(new MultiIndexIterator(this));
	}

	private PackIndex getIndex(int i) {
		return indexes[i];
	}

	private int getIndexCount() {
		return indexes.length;
	}

	/**
	 * Iterator over n-indexes in ObjectId order.
	 * <p>
	 * It returns duplicates if the same object id is in different indexes. Wrap
	 * it with {@link DedupMultiIndexIterator (Iterator)} to avoid duplicates.
	 */
	private static final class MultiIndexIterator implements Iterator<MutableEntry> {

		private final PackIndexMerger im;

		private final int[] currentPosition;

		private final MutableEntry mutableEntry = new MutableEntry();

		int nextIndexToRead;

		MultiIndexIterator(PackIndexMerger im) {
			this.im = im;
			this.currentPosition = new int[im.getIndexCount()];
			for (int i = 0; i < im.getIndexCount(); i++) {
				currentPosition[i] = 0;
			}
			nextIndexToRead = findNextIndex();
		}

		@Override
		public boolean hasNext() {
			return nextIndexToRead != -1;
		}

		@Override
		public MutableEntry next() {
			if (nextIndexToRead == -1) {
				throw new NoSuchElementException();
			}
			consume(nextIndexToRead, mutableEntry);
			nextIndexToRead = findNextIndex();
			return mutableEntry;
		}

		private void consume(int index, MutableEntry entry) {
			entry.oid = im.getIndex(index)
					.getObjectId(currentPosition[index]);
			entry.packId = index;
			entry.offset = im.getIndex(index).getOffset(currentPosition[index]);
			currentPosition[index]++;
		}

		/**
		 * What index has the next entry to read.
		 * <p>
		 * If the same entry is available in multiple indexes, it is returned
		 * multiple times, in the order of the indexes.
		 *
		 * @return index to read, -1 if no entries left.
		 */
		private int findNextIndex() {
			int winner = -1;
			for (int i = 0; i < im.getIndexCount(); i++) {
				if (currentPosition[i] >= im.getIndex(i).getObjectCount()) {
					// We exhausted index i
					continue;
				}

				if (winner == -1) {
					winner = i;
					continue;
				}
				ObjectId tip = im.getIndex(i).getObjectId(currentPosition[i]);
				ObjectId bestSoFar = im.getIndex(winner)
						.getObjectId(currentPosition[winner]);
				if (tip.compareTo(bestSoFar) < 0) {
					winner = i;
				}
			}
			return winner;
		}
	}

	private static class DedupMultiIndexIterator implements Iterator<MutableEntry> {
		private final MultiIndexIterator src;

		private MutableEntry srcNext;

		private final MutableEntry lastReturned = new MutableEntry();

		DedupMultiIndexIterator(MultiIndexIterator src) {
			this.src = src;
			srcNext = getSrcNext();
		}

		@Override
		public boolean hasNext() {
			return srcNext != null;
		}

		@Override
		public MutableEntry next() {
			if (srcNext == null) {
				throw new NoSuchElementException();
			}
			// Return a copy, because srcNext is mutable, and it will
			// point to next element after findNext()
			lastReturned.fill(srcNext);
			srcNext = getSrcNext();
			return lastReturned;
		}

		private MutableEntry getSrcNext() {
			while (src.hasNext()) {
				MutableEntry element = src.next();
				if (lastReturned.oid == null || !lastReturned.oid.equals(element.oid)) {
					return element;
				}
			}
			return null;
		}
	}
}
