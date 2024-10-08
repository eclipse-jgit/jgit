/*
 * Copyright (C) 2024, GerritForge Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file.midx;

import java.util.Set;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.NB;

/**
 * The index which are used by the MultiPackIndex to:
 * <ul>
 * <li>Get the object in MultiPackIndex by using a specific position.</li>
 * <li>Get the position of a specific object in MultiPackIndex.</li>
 * </ul>
 */
public class MultiPackIndexIndex {

	private static final int FANOUT = 256;

	private final int hashLength;

	private final int[] fanoutTable;

	private final byte[] oidLookup;

	private final long objectOffsetCount;

	private final long packFilesCnt;

	/**
	 * Initialize the MultiPackIndexIndex.
	 *
	 * @param hashLength
	 * 		length of object hash.
	 * @param oidFanout
	 * 		content of OID Fanout Chunk.
	 * @param oidLookup
	 * 		content of OID Lookup Chunk.
	 * @param packFilesCnt
	 * 		number of packfiles.
	 * @throws MultiPackIndexFormatException
	 * 		MultiPackIndex file's format is different from we expected.
	 */
	MultiPackIndexIndex(int hashLength, @NonNull byte[] oidFanout,
			@NonNull byte[] oidLookup, long packFilesCnt)
			throws MultiPackIndexFormatException {
		this.hashLength = hashLength;
		this.oidLookup = oidLookup;
		this.packFilesCnt = packFilesCnt;

		int[] table = new int[FANOUT];
		long uint32;
		for (int k = 0; k < table.length; k++) {
			uint32 = NB.decodeUInt32(oidFanout, k * 4);
			if (uint32 > Integer.MAX_VALUE) {
				throw new MultiPackIndexFormatException(
						JGitText.get().multiPackFileIsTooLargeForJgit);
			}
			table[k] = (int) uint32;
		}
		this.fanoutTable = table;
		this.objectOffsetCount = table[FANOUT - 1];
	}

	/**
	 * Find the position in the MultiPackIndex file of the specified id.
	 *
	 * @param id
	 * 		the id for which the multi-pack-index position will be found.
	 * @return the MultiPackIndex position or -1 if the object was not found.
	 */
	public int findMultiPackIndexPosition(AnyObjectId id) {
		int levelOne = id.getFirstByte();
		int high = fanoutTable[levelOne];
		int low = 0;
		if (levelOne > 0) {
			low = fanoutTable[levelOne - 1];
		}
		while (low < high) {
			int mid = (low + high) >>> 1;
			int pos = objIdOffset(mid);
			int cmp = id.compareTo(oidLookup, pos);
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

	/**
	 * Find objects matching the prefix abbreviation.
	 *
	 * @param matches
	 * 		set to add any located ObjectIds to. This is an output parameter.
	 * @param id
	 * 		prefix to search for.
	 * @param matchLimit
	 * 		maximum number of results to return. At most this many ObjectIds should
	 * 		be added to matches before returning.
	 */
	public void resolve(Set<ObjectIdAndPosition> matches,
			AbbreviatedObjectId id, int matchLimit) {
		int levelOne = id.getFirstByte();
		int high = fanoutTable[levelOne];
		int low = 0;
		if (levelOne > 0) {
			low = fanoutTable[levelOne - 1];
		}
		while (low < high) {
			int mid = (low + high) >>> 1;
			int cmp = id.prefixCompare(oidLookup, objIdOffset(mid));
			if (cmp < 0) {
				high = mid;
			} else if (cmp == 0) {
				// We may have landed in the middle of the matches.  Move
				// backwards to the start of matches, then walk forwards.
				//
				while (0 < mid
						&& id.prefixCompare(oidLookup, objIdOffset(mid - 1))
						== 0)
					mid--;
				for (; mid < fanoutTable[levelOne]
						&& id.prefixCompare(oidLookup, objIdOffset(mid))
						== 0; mid++) {
					matches.add(new ObjectIdAndPosition(
							ObjectId.fromRaw(oidLookup, objIdOffset(mid)),
							mid));
					if (matches.size() > matchLimit)
						break;
				}
				return;
			} else {
				low = mid + 1;
			}
		}
	}

	/**
	 * Container for an objectId and it's position within the object offset
	 * chunk
	 */
	public static class ObjectIdAndPosition {
		ObjectId id;

		int position;

		/**
		 * Container for an objectId and it's position within the object offset
		 * chunk
		 *
		 * @param id
		 * 		objectId
		 * @param position
		 * 		position within the object offset chunk
		 */
		public ObjectIdAndPosition(ObjectId id, int position) {
			this.id = id;
			this.position = position;
		}

		/**
		 * @return this objectId
		 */
		public ObjectId getId() {
			return id;
		}

		/**
		 * @return position within the object offset chunk
		 */
		public int getPosition() {
			return position;
		}
	}

	/**
	 * @return count of the packfiles
	 */
	public long getPackFilesCnt() {
		return packFilesCnt;
	}

	long getObjectOffsetCount() {
		return objectOffsetCount;
	}

	private int objIdOffset(int pos) {
		return hashLength * pos;
	}
}
