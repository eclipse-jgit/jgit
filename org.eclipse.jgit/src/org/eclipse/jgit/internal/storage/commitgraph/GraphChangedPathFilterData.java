/*
 * Copyright (C) 2023, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.commitgraph;

import org.eclipse.jgit.util.NB;

/**
 * Represents the BIDX and BDAT data found in a commit graph file.
 */
class GraphChangedPathFilterData {

	private static final int BIDX_BYTES_PER_ENTRY = 4;

	private static final int BDAT_HEADER_BYTES = 12;

	private final byte[] bloomFilterIndex;

	private final byte[] bloomFilterData;

	/**
	 * Initialize the GraphChangedPathFilterData.
	 *
	 * @param bloomFilterIndex
	 *            content of BIDX chunk, if it exists
	 * @param bloomFilterData
	 *            content of BDAT chunk, if it exists
	 */
	GraphChangedPathFilterData(byte[] bloomFilterIndex,
			byte[] bloomFilterData) {

		if ((bloomFilterIndex == null) != (bloomFilterData == null)) {
			bloomFilterIndex = null;
			bloomFilterData = null;
		}
		if (bloomFilterData != null
				&& (NB.decodeUInt32(bloomFilterData,
						4) != ChangedPathFilter.PATH_HASH_COUNT
						|| NB.decodeUInt32(bloomFilterData,
								8) != ChangedPathFilter.BITS_PER_ENTRY)) {
			bloomFilterIndex = null;
			bloomFilterData = null;
		}

		this.bloomFilterIndex = bloomFilterIndex;
		this.bloomFilterData = bloomFilterData;
	}

	ChangedPathFilter getChangedPathFilter(int graphPos) {
		if (bloomFilterIndex == null) {
			return null;
		}
		int priorCumul = graphPos == 0 ? 0
				: NB.decodeInt32(bloomFilterIndex,
						graphPos * BIDX_BYTES_PER_ENTRY - BIDX_BYTES_PER_ENTRY);
		int cumul = NB.decodeInt32(bloomFilterIndex, graphPos * BIDX_BYTES_PER_ENTRY);
		return ChangedPathFilter.fromFile(bloomFilterData,
				priorCumul + BDAT_HEADER_BYTES,
				cumul - priorCumul);
	}
}
