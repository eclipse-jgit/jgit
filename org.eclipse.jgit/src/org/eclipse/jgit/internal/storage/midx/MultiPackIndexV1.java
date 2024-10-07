/*
 * Copyright (C) 2024, GerritForge Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.midx;

import java.util.Arrays;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndexLoader.MultiPackIndexFormatException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.util.NB;

/**
 * Support for the MultiPackIndex v1 format.
 *
 * @see MultiPackIndex
 */
class MultiPackIndexV1 implements MultiPackIndex {

	private final OidLookup idx;

	private final String[] packNames;

	private final byte[] bitmappedPackfiles;

	private final OffsetLookup offsets;

	MultiPackIndexV1(int hashLength, @NonNull byte[] oidFanout,
			@NonNull byte[] oidLookup, String[] packNames,
			byte[] bitmappedPackfiles, byte[] objectOffsets,
			byte[] largeObjectOffsets) throws MultiPackIndexFormatException {
		this.bitmappedPackfiles = bitmappedPackfiles;
		this.idx = new OidLookup(hashLength, oidFanout, oidLookup);
		this.offsets = new OffsetLookup(objectOffsets, largeObjectOffsets);
		this.packNames = packNames;
	}

	@Override
	public String toString() {
		return "MultiPackIndexV1 {" + "idx=" + idx + ", packfileNames="
				+ Arrays.toString(packNames) + ", bitmappedPackfiles="
				+ byteArrayToString(bitmappedPackfiles) + ", objectOffsets="
				+ offsets + '}';
	}

	private String byteArrayToString(byte[] array) {
		return array == null ? "null" : new String(array);
	}

	@Override
	public String[] getPackNames() {
		return packNames;
	}

	@Override
	public boolean hasObject(AnyObjectId oid) {
		return idx.findMultiPackIndexPosition(oid) != -1;
	}

	@Override
	@Nullable
	public PackOffset find(AnyObjectId objectId) {
		int position = idx.findMultiPackIndexPosition(objectId);
		if (position == -1) {
			return null;
		}
		return offsets.getObjectOffset(position);
	}

	/**
	 * Wraps the small and large offset chunks (if exists), to lookup offsets.
	 */
	private static class OffsetLookup {
		private static final int OBJECT_OFFSETS_DATA_WIDTH = 8;

		private static final int BIT_31_ON = 0x80000000;

		private static final int TOGGLE_BIT_31 = 0x7fff_ffff;

		private final byte[] offsets;

		private final byte[] largeOffsets;

		/**
		 * Initialize the ObjectOffsets.
		 *
		 * @param offsets
		 *            content of ObjectOffset Chunk.
		 */
		public OffsetLookup(@NonNull byte[] offsets, byte[] largeOffsets) {
			this.offsets = offsets;
			this.largeOffsets = largeOffsets;
		}

		/**
		 * Get the metadata of a commit。
		 *
		 * @param position
		 *            the position in the multi-pack-index of the object.
		 * @return the ObjectOffset.
		 */
		public PackOffset getObjectOffset(int position) {
			int pos = position * OBJECT_OFFSETS_DATA_WIDTH;
			int packId = NB.decodeInt32(offsets, pos);
			int offset = NB.decodeInt32(offsets, pos + 4);
			if ((offset & BIT_31_ON) != 0) {
				long bigOffset;
				if (largeOffsets == null) {
					bigOffset = NB.decodeUInt32(offsets, pos + 4);
				} else {
					int bigOffsetPos = (offset & TOGGLE_BIT_31);
					bigOffset = NB.decodeInt64(largeOffsets, bigOffsetPos * 8);
				}
				return new PackOffset(packId, bigOffset);
			}
			return new PackOffset(packId, offset);
		}
	}

	/**
	 * Combines the fanout and oid list chunks, to lookup Oids with an efficient
	 * binary search
	 */
	private static class OidLookup {

		private static final int FANOUT = 256;

		private final int hashLength;

		private final int[] fanoutTable;

		private final byte[] oidLookup;

		/**
		 * Initialize the MultiPackIndexIndex.
		 *
		 * @param hashLength
		 *            length of object hash.
		 * @param oidFanout
		 *            content of OID Fanout Chunk.
		 * @param oidLookup
		 *            content of OID Lookup Chunk.
		 * @throws MultiPackIndexFormatException
		 *             MultiPackIndex file's format is different from we
		 *             expected.
		 */
		OidLookup(int hashLength, @NonNull byte[] oidFanout,
				@NonNull byte[] oidLookup)
				throws MultiPackIndexFormatException {
			this.hashLength = hashLength;
			this.oidLookup = oidLookup;

			int[] table = new int[FANOUT];
			long uint32;
			for (int k = 0; k < table.length; k++) {
				uint32 = NB.decodeUInt32(oidFanout, k * 4);
				if (uint32 > Integer.MAX_VALUE) {
					throw new MultiPackIndexFormatException(
							JGitText.get().multiPackIndexFileIsTooLargeForJgit);
				}
				table[k] = (int) uint32;
			}
			this.fanoutTable = table;
		}

		/**
		 * Find the position in the MultiPackIndex file of the specified id.
		 *
		 * @param id
		 *            the id for which the multi-pack-index position will be
		 *            found.
		 * @return the MultiPackIndex position or -1 if the object was not
		 *         found.
		 */
		int findMultiPackIndexPosition(AnyObjectId id) {
			int levelOne = id.getFirstByte();
			int high = fanoutTable[levelOne];
			int low = 0;
			if (levelOne > 0) {
				low = fanoutTable[levelOne - 1];
			}
			while (low < high) {
				int mid = (low + high) >>> 1;
				int cmp = id.compareTo(oidLookup, hashLength * mid);
				if (cmp < 0) {
					high = mid;
				} else if (cmp == 0) {
					return mid;
				} else {
					low = mid + 1;
				}
			}
			return -1;
		}
	}
}
