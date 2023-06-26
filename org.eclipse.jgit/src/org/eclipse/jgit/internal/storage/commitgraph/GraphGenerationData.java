/*
 * Copyright (C) 2023, Google LLC
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.commitgraph;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph.GenerationData;
import org.eclipse.jgit.util.NB;

import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GENERATION_DATA_MAX_OFFSET;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GENERATION_DATA_OVERFLOW_BIT;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GENERATION_DATA_OVERFLOW_WIDTH;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GENERATION_DATA_WIDTH;

/**
 * Represent the collection of {@link GenerationData}.
 */
public class GraphGenerationData {
	private final byte[] generationData;

	private final byte[] generationDataOverflow;

	/**
	 * Initialize the GraphGenerationData.
	 *
	 * @param generationData
	 *            content of Generation Data Chunk.
	 * @param generationDataOverflow
	 *            content of Generation Data Overflow Chunk.
	 */
	GraphGenerationData(@NonNull byte[] generationData,
			byte[] generationDataOverflow) {
		this.generationData = generationData;
		this.generationDataOverflow = generationDataOverflow;
	}

	GenerationData getGenerationData(int graphPos) {
		int genDataByteStart = GENERATION_DATA_WIDTH * graphPos;
		long genDataOffset = NB.decodeUInt32(generationData, genDataByteStart);
		if ((genDataOffset & GENERATION_DATA_OVERFLOW_BIT) == 0) {
			return new GenerationDataImpl(genDataOffset);
		}

		int overflowPos = (int) genDataOffset & GENERATION_DATA_MAX_OFFSET;
		int overflowByteStart = overflowPos * GENERATION_DATA_OVERFLOW_WIDTH;
		int overflowByteEnd = overflowByteStart + GENERATION_DATA_OVERFLOW_WIDTH
				- 1;

		if (generationDataOverflow == null) {
			throw new RuntimeException(
					"Generation Data Overflow chunk data missing");
		}
		if (overflowByteEnd >= generationDataOverflow.length) {
			throw new RuntimeException(String.format(
					"Generation Data Overflow data at position %s does not exist",
					graphPos));
		}
		long genDataOverflowedOffset = NB.decodeUInt64(generationDataOverflow,
				overflowByteStart);
		return new GenerationDataImpl(genDataOverflowedOffset);
	}

	private static class GenerationDataImpl implements GenerationData {
		private final long generationData;

		public GenerationDataImpl(long generationData) {
			this.generationData = generationData;
		}

		@Override
		public long getGenerationData() {
			return generationData;
		}
	}
}
