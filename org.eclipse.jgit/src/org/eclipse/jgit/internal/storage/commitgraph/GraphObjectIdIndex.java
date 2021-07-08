/*
 * Copyright (C) 2021, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.commitgraph;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

/**
 * The index which are used by the commit-graph to:
 * <ul>
 * <li>Get the SHA1 in commit-graph by using a specific position.</li>
 * <li>Get the position of a specific SHA1 in commit-graph.</li>
 * </ul>
 */
class GraphObjectIdIndex {

	static final int FANOUT = 256;

	private final int hashLength;

	private final long[] oidFanout;

	private final byte[] oidLookup;

	private final long commitCnt;

	/**
	 * Initialize the GraphObjectIdIndex.
	 *
	 * @param hashLength
	 *            length of object hash.
	 * @param oidFanout
	 *            content of OID Fanout Chunk.
	 * @param oidLookup
	 *            content of OID Lookup Chunk.
	 */
	GraphObjectIdIndex(int hashLength, @NonNull long[] oidFanout,
			@NonNull byte[] oidLookup) {
		this.hashLength = hashLength;
		this.oidFanout = oidFanout;
		this.oidLookup = oidLookup;
		this.commitCnt = oidFanout[FANOUT - 1];
	}

	int findGraphPosition(AnyObjectId id) {
		int levelOne = id.getFirstByte();
		int high = (int) oidFanout[levelOne];
		int low = 0;
		if (levelOne > 0) {
			low = (int) oidFanout[levelOne - 1];
		}
		do {
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
		} while (low < high);
		return -1;
	}

	ObjectId getObjectId(int graphPos) {
		return ObjectId.fromRaw(oidLookup, objIdOffset(graphPos));
	}

	long getCommitCnt() {
		return commitCnt;
	}

	int objIdOffset(int pos) {
		return hashLength * pos;
	}
}
