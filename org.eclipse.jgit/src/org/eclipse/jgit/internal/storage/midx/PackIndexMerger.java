/*
 * Copyright (C) 2025, Google LLC
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

import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.lib.AnyObjectId;
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
		private final MutableObjectId oid = new MutableObjectId();

		// Position of the pack in the ordered list of pack in this merger
		private int packId;

		// Offset in its pack
		private long offset;

		public AnyObjectId getObjectId() {
			return oid;
		}

		public int getPackId() {
			return packId;
		}

		public long getOffset() {
			return offset;
		}

		/**
		 * Copy values from another mutable entry
		 *
		 * @param packId
		 *            packId
		 * @param other
		 *            another mutable entry
		 */
		private void fill(int packId, PackIndex.MutableEntry other) {
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

	private final int[] objectsPerPack;

	/**
	 * Build a common view of these pack indexes
	 * <p>
	 * Order matters: in case of duplicates, the first pack with the object wins
	 *
	 * @param packs
	 *            map of pack names to indexes, ordered.
	 */
	PackIndexMerger(Map<String, PackIndex> packs) {
		this.packNames = packs.keySet().stream().toList();
		this.indexes = packs.values().stream().toList();

		objectsPerPack = new int[packNames.size()];
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
			objectsPerPack[entry.packId]++;
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
	 * Number of objects selected for the midx per packid
	 *
	 * @return array where position n contains the amount of objects selected
	 *         for pack id n
	 */
	int[] getObjectsPerPack() {
		return objectsPerPack;
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

		MultiIndexIterator(List<PackIndex> indexes) {
			this.indexIterators = new ArrayList<>(indexes.size());
			for (int i = 0; i < indexes.size(); i++) {
				PackIndexPeekIterator it = new PackIndexPeekIterator(i,
						indexes.get(i));
				// Position in the first element
				if (it.next() != null) {
					indexIterators.add(it);
				}
			}
		}

		@Override
		public boolean hasNext() {
			return !indexIterators.isEmpty();
		}

		@Override
		public MidxMutableEntry next() {
			PackIndexPeekIterator winner = null;
			for (int index = 0; index < indexIterators.size(); index++) {
				PackIndexPeekIterator current = indexIterators.get(index);
				if (winner == null
						|| current.peek().compareBySha1To(winner.peek()) < 0) {
					winner = current;
				}
			}

			if (winner == null) {
				throw new NoSuchElementException();
			}

			mutableEntry.fill(winner.getPackId(), winner.peek());
			if (winner.next() == null) {
				indexIterators.remove(winner);
			}
			return mutableEntry;
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
	 * MutableEntry#getObjectId, but that would create an ObjectId per entry.
	 * This implementation reuses the MutableEntry and avoid instantiations.
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
