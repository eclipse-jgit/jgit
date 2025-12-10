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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndexLoader.MultiPackIndexFormatException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.NB;

/**
 * Support for the MultiPackIndex v1 format.
 *
 * @see MultiPackIndex
 */
class MultiPackIndexV1 implements MultiPackIndex {

	private final OidLookup idx;

	private final String[] packNames;

	private final OffsetLookup offsets;

	private final PackOffset result = new PackOffset();

	private final ReverseIndex ridx;

	MultiPackIndexV1(int hashLength, @NonNull byte[] oidFanout,
			@NonNull byte[] oidLookup, String[] packNames,
			byte[] bitmappedPackfiles, byte[] objectOffsets,
			byte[] largeObjectOffsets, byte[] bitmapPackOrder) throws MultiPackIndexFormatException {
		this.idx = new OidLookup(hashLength, oidFanout, oidLookup);
		this.offsets = new OffsetLookup(objectOffsets, largeObjectOffsets);
		this.packNames = packNames;
		this.ridx = new ReverseIndex(bitmapPackOrder, idx, offsets,
				bitmappedPackfiles);
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
	public int findPosition(AnyObjectId oid) {
		return idx.findMultiPackIndexPosition(oid);
	}

	@Override
	public int findBitmapPosition(PackOffset po) {
		return ridx.findBitmapPosition(po);
	}

	@Override
	public ObjectId getObjectAtBitmapPosition(int bitmapPosition) {
		return ridx.getObjectId(bitmapPosition);
	}

	@Override
	public ObjectId getObjectAt(int position) {
		return idx.getObjectAt(position);
	}

	@Override
	public int getObjectCount() {
		return idx.getObjectCount();
	}

	@Override
	@Nullable
	public PackOffset find(AnyObjectId objectId) {
		int position = idx.findMultiPackIndexPosition(objectId);
		if (position == -1) {
			return null;
		}
		offsets.getObjectOffset(position, result);
		return result;
	}

	@Override
	public void resolve(Set<ObjectId> matches, AbbreviatedObjectId id,
			int matchLimit) {
		idx.resolve(matches, id, matchLimit);
	}

	@Override
	public long getMemorySize() {
		int packNamesSize = Arrays.stream(packNames)
				.mapToInt(s -> s.getBytes(StandardCharsets.UTF_8).length).sum();
		return packNamesSize + ridx.getMemorySize()
				+ idx.getMemorySize() + offsets.getMemorySize();
	}

	@Override
	public String toString() {
		return "MultiPackIndexV1 {idx=" + idx + ", packfileNames=" //$NON-NLS-1$ //$NON-NLS-2$
				+ Arrays.toString(packNames) + ", ridx=" //$NON-NLS-1$
				+ ridx + ", objectOffsets=" //$NON-NLS-1$
				+ offsets + '}';
	}

	private static int byteArrayLengh(byte[] array) {
		return array == null ? 0 : array.length;
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
		 * @param largeOffsets
		 *            content of largo offsets chunks (can be null).
		 */
		OffsetLookup(@NonNull byte[] offsets, byte[] largeOffsets) {
			this.offsets = offsets;
			this.largeOffsets = largeOffsets;
		}

		/**
		 * Get the metadata of a commit。
		 *
		 * @param position
		 *            the position in the multi-pack-index of the object.
		 * @param result
		 *            an instance of PackOffset to populate with the result.
		 */
		void getObjectOffset(int position, PackOffset result) {
			int offsetInChunk = position * OBJECT_OFFSETS_DATA_WIDTH;
			int packId = NB.decodeInt32(offsets, offsetInChunk);
			int offset = NB.decodeInt32(offsets, offsetInChunk + 4);
			if ((offset & BIT_31_ON) != 0) {
				long bigOffset;
				if (largeOffsets == null) {
					bigOffset = NB.decodeUInt32(offsets, offsetInChunk + 4);
				} else {
					int bigOffsetPos = (offset & TOGGLE_BIT_31);
					bigOffset = NB.decodeInt64(largeOffsets, bigOffsetPos * 8);
				}
				result.setValues(packId, bigOffset);
				return;
			}
			result.setValues(packId, offset);
		}

		long getMemorySize() {
			return (long) byteArrayLengh(offsets)
					+ byteArrayLengh(largeOffsets);
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

		ObjectId getObjectAt(int midxPosition) {
			int rawOffset = hashLength * midxPosition;
			return ObjectId.fromRaw(oidLookup, rawOffset);
		}

		void resolve(Set<ObjectId> matches, AbbreviatedObjectId id,
				int matchLimit) {
			if (matches.size() >= matchLimit) {
				return;
			}

			if (oidLookup.length == 0) {
				return;
			}

			int levelOne = id.getFirstByte();
			int high = fanoutTable[levelOne];
			int low = 0;
			if (levelOne > 0) {
				low = fanoutTable[levelOne - 1];
			}

			int p = -1;
			boolean found = false;
			while (low < high) {
				p = (low + high) >>> 1;
				int cmp = id.prefixCompare(oidLookup, idOffset(p));
				if (cmp < 0) {
					high = p;
				} else if (cmp == 0) {
					found = true;
					break;
				} else {
					low = p + 1;
				}
			}

			if (!found) {
				return;
			}

			// Got a match.
			// We may have landed in the middle of the matches. Move
			// backwards to the start of matches, then walk forwards.
			while (0 < p && id.prefixCompare(oidLookup, idOffset(p - 1)) == 0) {
				p--;
			}

			while (p < high && id.prefixCompare(oidLookup, idOffset(p)) == 0
					&& matches.size() < matchLimit) {
				matches.add(ObjectId.fromRaw(oidLookup, idOffset(p)));
				p++;
			}
		}

		private int idOffset(int position) {
			return position * hashLength;
		}

		long getMemorySize() {
			return 4L + byteArrayLengh(oidLookup) + (FANOUT * 4);
		}

		int getObjectCount() {
			return fanoutTable[FANOUT - 1];
		}
	}

	private static class ReverseIndex {
		private final byte[] bitmappedPackfiles;

		private final byte[] bitmapPackOrder;

		private final OffsetLookup offsets;

		private final OidLookup oids;

		ReverseIndex(byte[] bitmapPackOrder, OidLookup oids,
				OffsetLookup offsets, byte[] bitmappedPackfiles) {
			this.bitmappedPackfiles = bitmappedPackfiles;
			this.bitmapPackOrder = bitmapPackOrder;
			this.oids = oids;
			this.offsets = offsets;
		}

		public int findBitmapPosition(PackOffset po) {
			if (bitmapPackOrder == null || bitmappedPackfiles == null) {
				return -1;
			}

			/*
			 * Bitmapped Packfiles (ID: {'B', 'T', 'M', 'P'}) Stores a table of
			 * two 4-byte unsigned integers in network order. Each table entry
			 * corresponds to a single pack (in the order that they appear above
			 * in the `PNAM` chunk). The values for each table entry are as
			 * follows: - The first bit position (in pseudo-pack order, see
			 * below) to contain an object from that pack. - The number of bits
			 * whose objects are selected from that pack.
			 */
			int packStartBit = NB.decodeInt32(bitmappedPackfiles,
					po.getPackId() * 8);
			int packBitLength = NB.decodeInt32(bitmappedPackfiles,
					(po.getPackId() * 8) + 4);
			return binarySearch(bitmapPackOrder, packStartBit,
					packStartBit + packBitLength, po, offsets);
		}

		private int binarySearch(byte[] bitmapPackOrder, int start, int end,
				PackOffset needle, OffsetLookup offsets) {
			PackOffset result = new PackOffset();
			int low = start;
			int high = end;
			while (low < high) {
				int mid = (low + high) >>> 1;
				int midxPosition = NB.decodeInt32(bitmapPackOrder, mid * 4);
				offsets.getObjectOffset(midxPosition, result);
				int cmp = needle.compareTo(result);
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

		public ObjectId getObjectId(int bitmapPosition) {
			if (bitmapPackOrder == null) {
				return null;
			}
			int midxPosition = NB.decodeInt32(bitmapPackOrder,
					bitmapPosition * 4);
			return oids.getObjectAt(midxPosition);
		}

		public long getMemorySize() {
			return (long) byteArrayLengh(bitmapPackOrder)
					+ byteArrayLengh(bitmappedPackfiles);
		}
	}
}
