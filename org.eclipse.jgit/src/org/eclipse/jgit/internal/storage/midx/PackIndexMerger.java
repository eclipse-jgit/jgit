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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex.MidxIterator;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex.MutableEntry;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.transport.PackedObjectInfo;

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

	private final MidxIterator midxIterator;

	private final boolean needsLargeOffsetsChunk;

	private final int offsetsOver31BitsCount;

	private final int uniqueObjectCount;

	private final int[] objectsPerPack;

	private final long[] maxOffsetPerPack;

	private final List<String> packnames;

	private final List<ObjectTypeLookup> objectTypeLookups;

	/**
	 * Lookup for the git object type of an entry at a given offset in a pack.
	 */
	@FunctionalInterface
	public interface ObjectTypeLookup {
		/**
		 * Return the git object type (e.g. {@code OBJ_COMMIT}) for the object
		 * at {@code offset}.
		 *
		 * @param offset
		 *            offset within the pack
		 * @return object type constant
		 * @throws IOException
		 *             an error reading the object type
		 */
		int getObjectType(long offset) throws IOException;
	}

	/**
	 * Builder collecting the inputs for the merger.
	 * <p>
	 * Order matters. In case of duplicates, the merger chooses the object in
	 * the first pack it appears.
	 */
	public static class Builder {

		private final List<MidxIterator> packIndexes = new ArrayList<>();

		private final List<ObjectTypeLookup> objectTypeLookups = new ArrayList<>();

		private ProgressMonitor pm = NullProgressMonitor.INSTANCE;

		/**
		 * Add a regular pack to the midx
		 *
		 * @param name
		 *            name of the pack
		 * @param idx
		 *            primary index of the pack
		 * @return this builder
		 */
		public Builder addPack(String name, PackIndex idx) {
			return addPack(name, idx, null);
		}

		/**
		 * Add a regular pack to the midx with object type lookup
		 *
		 * @param name
		 *            name of the pack
		 * @param idx
		 *            primary index of the pack
		 * @param objectTypeLookup
		 *            lookup function for object types by offset (needed for
		 *            {@link #toObjectsToPack})
		 * @return this builder
		 */
		public Builder addPack(String name, PackIndex idx,
				@Nullable ObjectTypeLookup objectTypeLookup) {
			packIndexes.add(MidxIterators.fromPackIndexIterator(name, idx));
			objectTypeLookups.add(objectTypeLookup);
			return this;
		}

		/**
		 * Add an in-memory pack (represented by its SHA-1 sorted objects) to
		 * the midx.
		 *
		 * @param name
		 *            name of the pack
		 * @param objects
		 *            objects in the pack, sorted in SHA-1 order, with offsets
		 *            and types populated
		 * @return this builder
		 */
		public Builder addPack(String name,
				List<? extends PackedObjectInfo> objects) {
			packIndexes.add(MidxIterators.fromObjectsToPack(name, objects));
			objectTypeLookups.add(new PackedObjectTypeLookup(objects));
			return this;
		}

		/**
		 * Add data from this midx iterator to the merge
		 * <p>
		 * Packs are kept in the order of the iterator.
		 *
		 * @param midx
		 *            a midx iterator
		 * @return this builder
		 */
		public Builder addMidx(MidxIterator midx) {
			packIndexes.add(midx);
			for (int i = 0; i < midx.getPackNames().size(); i++) {
				objectTypeLookups.add(null);
			}
			return this;
		}

		/**
		 * Add a progress monitor to the build process
		 * <p>
		 * Give visibility over the first iteration of the packs calculating the
		 * data needed for midx headers (unique object count, if large offsets
		 * are needed...)
		 *
		 * @param pm
		 *            a progress monitor
		 * @return this builder
		 */
		public Builder setProgressMonitor(ProgressMonitor pm) {
			this.pm = pm;
			return this;
		}

		/**
		 * Build the merger instance
		 *
		 * @return a merger instance
		 */
		public PackIndexMerger build() {
			return new PackIndexMerger(
					MidxIterators.dedup(MidxIterators.join(packIndexes)),
					objectTypeLookups, pm);
		}
	}

	/**
	 * Create a builder
	 *
	 * @return an empty builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * A common view of the input pack indexes
	 *
	 * @param midxIterator
	 *            MidxIterator built by deduping union of all pack indexes
	 * @param objectTypeLookups
	 *            lookup functions for object types per pack
	 * @param pm
	 *            a progress monitor
	 */
	private PackIndexMerger(MidxIterator midxIterator,
			List<ObjectTypeLookup> objectTypeLookups, ProgressMonitor pm) {
		this.midxIterator = midxIterator;
		this.packnames = midxIterator.getPackNames();
		this.objectTypeLookups = objectTypeLookups;

		objectsPerPack = new int[packnames.size()];
		maxOffsetPerPack = new long[packnames.size()];
		pm.beginTask("Iterating objects for midx headers",
				ProgressMonitor.UNKNOWN);
		// Iterate for duplicates and counts that we need to build the chunk
		// headers.
		int objectCount = 0;
		boolean hasLargeOffsets = false;
		int over31bits = 0;
		MutableObjectId lastSeen = new MutableObjectId();
		while (midxIterator.hasNext()) {
			MutableEntry entry = midxIterator.next();
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
			// TODO(ifrade): we can calculate the fanout table already here.
			// It saves an iteration over all objects for only 1Kb of memory
			int packId = entry.getPackId();
			objectsPerPack[packId]++;
			if (entry.getOffset() > maxOffsetPerPack[packId]) {
				maxOffsetPerPack[packId] = entry.getOffset();
			}
			pm.update(1);
		}
		uniqueObjectCount = objectCount;
		offsetsOver31BitsCount = over31bits;
		needsLargeOffsetsChunk = hasLargeOffsets;
		pm.endTask();
	}

	/**
	 * Object count of the merged index (i.e. without duplicates)
	 *
	 * @return object count of the merged index
	 */
	public int getUniqueObjectCount() {
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
	 * This always returns the same iterator resetted. We don't support two
	 * iterators over this merged data.
	 * <p>
	 * The returned entry in the iterator is mutable, callers should NOT keep a
	 * reference to it.
	 *
	 * @return an iterator in sha1 order without duplicates.
	 */
	public MidxIterator bySha1Iterator() {
		midxIterator.reset();
		return midxIterator;
	}

	/**
	 * Convert the merged index entries into a list of {@link ObjectToPack} in
	 * SHA-1 order.
	 * <p>
	 * Each returned {@link ObjectToPack} has a synthetic offset assigned so
	 * that sorting by offset orders objects by {@code (packId, offset)}.
	 *
	 * @param pm
	 *            progress monitor
	 * @param byId
	 *            map populated with the created {@link ObjectToPack} instances
	 * @return list of {@link ObjectToPack} in SHA-1 order
	 * @throws IOException
	 *             an error reading object types
	 */
	public List<ObjectToPack> toObjectsToPack(ProgressMonitor pm,
			ObjectIdOwnerMap<ObjectToPack> byId) throws IOException {
		long[] accPackSize = new long[packnames.size()];
		for (int i = 1; i < packnames.size(); i++) {
			accPackSize[i] = accPackSize[i - 1] + maxOffsetPerPack[i - 1] + 1;
		}

		pm.beginTask("Converting midx to ObjectsToPack", //$NON-NLS-1$
				getUniqueObjectCount());
		List<ObjectToPack> result = new ArrayList<>(getUniqueObjectCount());
		MidxIterator it = bySha1Iterator();
		while (it.hasNext()) {
			MutableEntry entry = it.next();
			ObjectTypeLookup lookup = objectTypeLookups.get(entry.getPackId());
			if (lookup == null) {
				throw new IllegalStateException(
						"No ObjectTypeLookup provided for pack " //$NON-NLS-1$
								+ packnames.get(entry.getPackId()));
			}
			int objectType = lookup.getObjectType(entry.getOffset());
			ObjectToPack o = new ObjectToPack(entry.getObjectId().toObjectId(),
					objectType);
			o.setOffset(accPackSize[entry.getPackId()] + entry.getOffset());
			result.add(o);
			byId.add(o);
			pm.update(1);
		}
		pm.endTask();
		return result;
	}

	private static class PackedObjectTypeLookup implements ObjectTypeLookup {
		private final List<? extends PackedObjectInfo> objects;

		private final int size;

		private int cursor;

		PackedObjectTypeLookup(List<? extends PackedObjectInfo> objects) {
			this.objects = objects;
			this.size = objects.size();
		}

		@Override
		public int getObjectType(long offset) {
			// Offsets are looked up in SHA-1 order (the same order as objects),
			// so start searching right after the last matched position and wrap
			// around just in case.
			for (int i = 0; i < size; i++) {
				int idx = (cursor + i) % size;
				PackedObjectInfo obj = objects.get(idx);
				if (obj.getOffset() == offset) {
					cursor = (idx + 1) % size;
					return obj.getType();
				}
			}
			throw new IllegalArgumentException(
					"No object found at offset " + offset); //$NON-NLS-1$
		}
	}
}
