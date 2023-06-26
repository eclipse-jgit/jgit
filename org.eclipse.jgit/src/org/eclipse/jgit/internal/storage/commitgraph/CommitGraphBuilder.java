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

import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_GENERATION_DATA;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_GENERATION_DATA_OVERFLOW;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_COMMIT_DATA;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_EXTRA_EDGE_LIST;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_OID_FANOUT;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_OID_LOOKUP;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;

import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;

/**
 * Builder for {@link CommitGraph}.
 */
class CommitGraphBuilder {

	private final int hashLength;

	private byte[] oidFanout;

	private byte[] oidLookup;

	private byte[] commitData;

	private byte[] extraList;

	private byte[] generationData;

	private byte[] generationDataOverflow;

	/** @return A builder of {@link CommitGraph}. */
	static CommitGraphBuilder builder() {
		return new CommitGraphBuilder(OBJECT_ID_LENGTH);
	}

	private CommitGraphBuilder(int hashLength) {
		this.hashLength = hashLength;
	}

	CommitGraphBuilder addOidFanout(byte[] buffer)
			throws CommitGraphFormatException {
		assertChunkNotSeenYet(oidFanout, CHUNK_ID_OID_FANOUT);
		oidFanout = buffer;
		return this;
	}

	CommitGraphBuilder addOidLookUp(byte[] buffer)
			throws CommitGraphFormatException {
		assertChunkNotSeenYet(oidLookup, CHUNK_ID_OID_LOOKUP);
		oidLookup = buffer;
		return this;
	}

	CommitGraphBuilder addCommitData(byte[] buffer)
			throws CommitGraphFormatException {
		assertChunkNotSeenYet(commitData, CHUNK_ID_COMMIT_DATA);
		commitData = buffer;
		return this;
	}

	CommitGraphBuilder addExtraList(byte[] buffer)
			throws CommitGraphFormatException {
		assertChunkNotSeenYet(extraList, CHUNK_ID_EXTRA_EDGE_LIST);
		extraList = buffer;
		return this;
	}

	CommitGraphBuilder addGenerationData(byte[] buffer)
			throws CommitGraphFormatException {
		assertChunkNotSeenYet(generationData, CHUNK_GENERATION_DATA);
		generationData = buffer;
		return this;
	}

	CommitGraphBuilder addGenerationDataOverflow(byte[] buffer)
			throws CommitGraphFormatException {
		assertChunkNotSeenYet(generationDataOverflow,
				CHUNK_GENERATION_DATA_OVERFLOW);
		generationDataOverflow = buffer;
		return this;
	}

	CommitGraph build() throws CommitGraphFormatException {
		assertChunkNotNull(oidFanout, CHUNK_ID_OID_FANOUT);
		assertChunkNotNull(oidLookup, CHUNK_ID_OID_LOOKUP);
		assertChunkNotNull(commitData, CHUNK_ID_COMMIT_DATA);

		GraphObjectIndex index = new GraphObjectIndex(hashLength, oidFanout,
				oidLookup);
		GraphCommitData commitDataChunk = new GraphCommitData(hashLength,
				commitData, extraList);

		if (generationData != null) {
			GraphGenerationData generationDataChunk = new GraphGenerationData(
					generationData, generationDataOverflow);
			return new CommitGraphV1(2, index, commitDataChunk,
					generationDataChunk);
		}
		return new CommitGraphV1(1, index, commitDataChunk);
	}

	private void assertChunkNotNull(Object object, int chunkId)
			throws CommitGraphFormatException {
		if (object == null) {
			throw new CommitGraphFormatException(
					MessageFormat.format(JGitText.get().commitGraphChunkNeeded,
							Integer.toHexString(chunkId)));
		}
	}

	private void assertChunkNotSeenYet(Object object, int chunkId)
			throws CommitGraphFormatException {
		if (object != null) {
			throw new CommitGraphFormatException(MessageFormat.format(
					JGitText.get().commitGraphChunkRepeated,
					Integer.toHexString(chunkId)));
		}
	}
}
