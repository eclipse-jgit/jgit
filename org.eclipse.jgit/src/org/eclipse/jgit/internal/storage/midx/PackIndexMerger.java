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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.lib.MutableObjectId;

/**
 * Collect the stats and offers an iterator over the union of n-pack indexes.
 * <p>
 * The multipack index is a list of (sha1, packid, offset) ordered by sha1. We
 * can build it from the individual pack indexes (sha1, offset) ordered by sha1,
 * with a simple merge ignoring duplicates.
 * <p>
 * This class encapsulates the merging logic and precalculates the stats that
 * the index needs (like total count of objects). To limit memory consumption,
 * it does the merge as it goes during the iteration and iterators use mutable
 * entries. The stats of the combined index are calculated in an iteration at
 * construction time.
 */
class PackIndexMerger {

	private static final int LIMIT_31_BITS = (1 << 31) - 1;

	private static final long LIMIT_32_BITS = (1L << 32) - 1;

	/**
	 * Object returned by the iterator.
	 * <p>
	 * The iterator returns (on each next()) the same instance with different
	 * values, to avoid allocating many short-lived objects. Callers should not
	 * keep a reference to that returned value.
	 */
	static class MidxMutableEntry {
		// The object id
		final MutableObjectId oid = new MutableObjectId();

		// Position of the pack in the ordered list of pack in this merger
		int packId;

		// Offset in its pack
		long offset;

		/**
		 * Copy values from another mutable entry
		 *
		 * @param other
		 *            another mutable entry
		 */
		void fill(int packId, PackIndex.MutableEntry other) {
			other.copyOidTo(oid);
			this.packId = packId;
			this.offset = other.getOffset();
		}
	}

	private final List<String> packNames;

	private final List<PackIndex> indexes;

	private final boolean needsLargeOffsetsChunk;

	private final int offsetsOver31BitsCount;

	private final int uniqueObjectCount;

	PackIndexMerger(Map<String, PackIndex> packs) {
		this.packNames = packs.keySet().stream().sorted()
				.collect(Collectors.toUnmodifiableList());

		this.indexes = packNames.stream().map(name -> packs.get(name))
				.collect(Collectors.toUnmodifiableList());

		// Iterate for duplicates
		int objectCount = 0;
		boolean hasLargeOffsets = false;
		int over31bits = 0;
		MutableObjectId lastSeen = new MutableObjectId();
		MultiIndexIterator it = new MultiIndexIterator(indexes);
		while (it.hasNext()) {
			MidxMutableEntry entry = it.next();
			if (lastSeen.equals(entry.oid)) {
				continue;
			}
			// If there is at least one offset value larger than 2^32-1, then
			// the large offset chunk must exist, and offsets larger than
			// 2^31-1 must be stored in it instead
			if (entry.offset > LIMIT_32_BITS) {
				hasLargeOffsets = true;
			}
			if (entry.offset > LIMIT_31_BITS) {
				over31bits++;
			}

			lastSeen.fromObjectId(entry.oid);
			objectCount++;
		}
		uniqueObjectCount = objectCount;
		offsetsOver31BitsCount = over31bits;
		needsLargeOffsetsChunk = hasLargeOffsets;
	}

	/**
	 * Object count of the merged index (i.e. without duplicates)
	 *
	 * @return object count of the merged index
	 */
	int getUniqueObjectCount() {
		return uniqueObjectCount;
	}

	/**
	 * If any object in any of the indexes has an offset over 2^32-1
	 *
	 * @return true if there is any object with offset > 2^32 -1
	 */
	boolean needsLargeOffsetsChunk() {
		return needsLargeOffsetsChunk;
	}

	/**
	 * How many object have offsets over 2^31-1
	 * <p>
	 * Per multipack index spec, IF there is large offset chunk, all this
	 * offsets should be there.
	 *
	 * @return number of objects with offsets over 2^31-1
	 */
	int getOffsetsOver31BitsCount() {
		return offsetsOver31BitsCount;
	}

	/**
	 * List of pack names in alphabetical order.
	 * <p>
	 * Order matters: In case of duplicates, the multipack index prefers the
	 * first package with it. This is in the same order we are using to
	 * prioritize duplicates.
	 *
	 * @return List of pack names, in the order used by the merge.
	 */
	List<String> getPackNames() {
		return packNames;
	}

	/**
	 * How many packs are being merged
	 *
	 * @return count of packs merged
	 */
	int getPackCount() {
		return packNames.size();
	}

	/**
	 * Iterator over the merged indexes in sha1 order without duplicates
	 * <p>
	 * The returned entry in the iterator is mutable, callers should NOT keep a
	 * reference to it.
	 *
	 * @return an iterator in sha1 order without duplicates.
	 */
	Iterator<MidxMutableEntry> bySha1Iterator() {
		return new DedupMultiIndexIterator(new MultiIndexIterator(indexes),
				getUniqueObjectCount());
	}

	/**
	 * For testing. Iterate all entries, not skipping duplicates (stable order)
	 *
	 * @return an iterator of all objects in sha1 order, including duplicates.
	 */
	Iterator<MidxMutableEntry> rawIterator() {
		return new MultiIndexIterator(indexes);
	}

	/**
	 * Iterator over n-indexes in ObjectId order.
	 * <p>
	 * It returns duplicates if the same object id is in different indexes. Wrap
	 * it with {@link DedupMultiIndexIterator (Iterator, int)} to avoid
	 * duplicates.
	 */
	private static final class MultiIndexIterator
			implements Iterator<MidxMutableEntry> {

		private final List<PackIndexPeekIterator> indexIterators;

		private final MidxMutableEntry mutableEntry = new MidxMutableEntry();

		private int nextIndexToRead;

		private boolean hasEmptyIndexes;

		MultiIndexIterator(List<PackIndex> indexes) {
			this.indexIterators = new ArrayList<>(indexes.size());
			for (int i = 0; i < indexes.size(); i++) {
				PackIndexPeekIterator it = new PackIndexPeekIterator(i,
						indexes.get(i));
				it.next(); // Position in the first element
				indexIterators.add(it);
			}
			nextIndexToRead = findNextIndex();
		}

		@Override
		public boolean hasNext() {
			return nextIndexToRead != -1;
		}

		@Override
		public MidxMutableEntry next() {
			if (nextIndexToRead == -1) {
				throw new NoSuchElementException();
			}
			consume(nextIndexToRead, mutableEntry);
			nextIndexToRead = findNextIndex();
			return mutableEntry;
		}

		private void consume(int index, MidxMutableEntry entry) {
			PackIndexPeekIterator winner = indexIterators.get(index);
			entry.fill(winner.getPackId(), winner.peek());
			winner.next();
			if (hasEmptyIndexes) {
				for (int i = indexIterators.size() - 1; i >= 0; i--) {
					if (indexIterators.get(i).peek() == null) {
						indexIterators.remove(i);
					}
				}
			}
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
			int nextIndex = -1; // In the list of indexes
			PackIndex.MutableEntry bestSoFar = null;
			for (int index = 0; index < indexIterators.size(); index++) {
				PackIndexPeekIterator current = indexIterators.get(index);
				if (current.peek() == null) {
					// No more entries in this index, but we cannot delete
					// it now, because index could point to the wrong position.
					// Clean it up after consuming the element, so findNextIndex
					// do not see it in the next invocation.
					hasEmptyIndexes = true;
					continue;
				}

				if (nextIndex == -1
						|| current.peek().compareBySha1To(bestSoFar) < 0) {
					nextIndex = index;
					bestSoFar = current.peek();
				}
			}
			return nextIndex;
		}
	}

	private static class DedupMultiIndexIterator
			implements Iterator<MidxMutableEntry> {
		private final MultiIndexIterator src;

		private int remaining;

		private final MutableObjectId lastOid = new MutableObjectId();

		DedupMultiIndexIterator(MultiIndexIterator src, int totalCount) {
			this.src = src;
			this.remaining = totalCount;
		}

		@Override
		public boolean hasNext() {
			return remaining > 0;
		}

		@Override
		public MidxMutableEntry next() {
			MidxMutableEntry next = src.next();
			while (next != null && lastOid.equals(next.oid)) {
				next = src.next();
			}

			if (next == null) {
				throw new NoSuchElementException();
			}

			lastOid.fromObjectId(next.oid);
			remaining--;
			return next;
		}
	}

	/**
	 * Convenience around the PackIndex iterator to read the current value
	 * multiple times without consuming it.
	 * <p>
	 * This is used to merge indexes in the multipack index, where we need to
	 * compare the current value between indexes multiple times to find the
	 * next.
	 * <p>
	 * We could also implement this keeping the position (int) and
	 * {@link PackIndex.MutableEntry#getObjectId(int)}, but that would create an
	 * ObjectId per entry. This implementation reuses the MutableEntry and avoid
	 * instantiations.
	 */
	// Visible for testing
	static class PackIndexPeekIterator {
		private final Iterator<PackIndex.MutableEntry> it;

		private final int packId;

		PackIndex.MutableEntry current;

		PackIndexPeekIterator(int packId, PackIndex index) {
			it = index.iterator();
			this.packId = packId;
		}

		PackIndex.MutableEntry next() {
			if (it.hasNext()) {
				current = it.next();
			} else {
				current = null;
			}
			return current;
		}

		PackIndex.MutableEntry peek() {
			return current;
		}

		int getPackId() {
			return packId;
		}
	}
}
