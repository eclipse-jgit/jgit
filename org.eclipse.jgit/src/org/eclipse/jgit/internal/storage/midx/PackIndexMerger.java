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
import java.util.List;

import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex.MidxIterator;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex.MutableEntry;
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
public class PackIndexMerger {

	private static final int LIMIT_31_BITS = (1 << 31) - 1;

	private static final long LIMIT_32_BITS = (1L << 32) - 1;

	private final MidxIterator source;

	private final boolean needsLargeOffsetsChunk;

	private final int offsetsOver31BitsCount;

	private final int uniqueObjectCount;

	private final int[] objectsPerPack;

	private final List<String> packnames;

	public static class Builder {

		private final List<MidxIterator> packIndexes = new ArrayList<>();

		public Builder addPack(String name, PackIndex idx) {
			packIndexes.add(MidxIterators.fromPackIndexIterator(name, idx));
			return this;
		}

		public PackIndexMerger build() {
			return new PackIndexMerger(
					MidxIterators.dedup(MidxIterators.join(packIndexes)));
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * A common view of the input pack indexes
	 *
	 * @param source
	 *            MidxIterator built by deduping union of all pack indexes
	 */
	private PackIndexMerger(MidxIterator source) {
		this.source = source;
		this.packnames = source.getPackNames();

		objectsPerPack = new int[packnames.size()];
		// Iterate for duplicates
		int objectCount = 0;
		boolean hasLargeOffsets = false;
		int over31bits = 0;
		MutableObjectId lastSeen = new MutableObjectId();
		while (source.hasNext()) {
			MutableEntry entry = source.next();
			// If there is at least one offset value larger than 2^32-1, then
			// the large offset chunk must exist, and offsets larger than
			// 2^31-1 must be stored in it instead
			if (entry.getOffset() > LIMIT_32_BITS) {
				hasLargeOffsets = true;
			}
			if (entry.getOffset() > LIMIT_31_BITS) {
				over31bits++;
			}

			lastSeen.fromObjectId(entry.oid);
			objectCount++;
			objectsPerPack[entry.getPackId()]++;
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
	 * Number of objects selected for the midx per pack id
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
		return packnames;
	}

	/**
	 * How many packs are being merged
	 *
	 * @return count of packs merged
	 */
	int getPackCount() {
		return packnames.size();
	}

	/**
	 * Iterator over the merged indexes in sha1 order without duplicates
	 * <p>
	 * The returned entry in the iterator is mutable, callers should NOT keep a
	 * reference to it.
	 *
	 * @return an iterator in sha1 order without duplicates.
	 */
	MidxIterator bySha1Iterator() {
		source.reset();
		return source;
	}
}
