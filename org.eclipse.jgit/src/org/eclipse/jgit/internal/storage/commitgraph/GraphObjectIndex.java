/*
 * Copyright (C) 2022, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.commitgraph;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.NB;

/**
 * The index which are used by the commit-graph to:
 * <ul>
 * <li>Get the object in commit-graph by using a specific position.</li>
 * <li>Get the position of a specific object in commit-graph.</li>
 * </ul>
 */
class GraphObjectIndex {

	private static final int FANOUT = 256;

	private final int hashLength;

	private final int[] fanoutTable;

	private final byte[] oidLookup;

	private final long commitCnt;

	/**
	 * Initialize the GraphObjectIndex.
	 *
	 * @param hashLength
	 *            length of object hash.
	 * @param oidFanout
	 *            content of OID Fanout Chunk.
	 * @param oidLookup
	 *            content of OID Lookup Chunk.
	 * @throws CommitGraphFormatException
	 *             commit-graph file's format is different from we expected.
	 */
	GraphObjectIndex(int hashLength, @NonNull byte[] oidFanout,
			@NonNull byte[] oidLookup) throws CommitGraphFormatException {
		this.hashLength = hashLength;
		this.oidLookup = oidLookup;

		int[] table = new int[FANOUT];
		long uint32;
		for (int k = 0; k < table.length; k++) {
			uint32 = NB.decodeUInt32(oidFanout, k * 4);
			if (uint32 > Integer.MAX_VALUE) {
				throw new CommitGraphFormatException(
						JGitText.get().commitGraphFileIsTooLargeForJgit);
			}
			table[k] = (int) uint32;
		}
		this.fanoutTable = table;
		this.commitCnt = table[FANOUT - 1];
	}

	/**
	 * Find the position in the commit-graph of the specified id.
	 *
	 * @param id
	 *            the id for which the commit-graph position will be found.
	 * @return the commit-graph position or -1 if the object was not found.
	 */
	int findGraphPosition(AnyObjectId id) {
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
	 * Get the object at the commit-graph position.
	 *
	 * @param graphPos
	 *            the position in the commit-graph of the object.
	 * @return the ObjectId or null if it's not found.
	 */
	ObjectId getObjectId(int graphPos) {
		if (graphPos < 0 || graphPos >= commitCnt) {
			return null;
		}
		return ObjectId.fromRaw(oidLookup, objIdOffset(graphPos));
	}

	long getCommitCnt() {
		return commitCnt;
	}

	private int objIdOffset(int pos) {
		return hashLength * pos;
	}
}
