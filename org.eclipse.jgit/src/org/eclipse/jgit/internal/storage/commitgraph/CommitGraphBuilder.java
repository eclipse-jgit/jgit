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

import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_BLOOM_FILTER_DATA;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_BLOOM_FILTER_INDEX;
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

	private byte[] bloomFilterIndex;

	private byte[] bloomFilterData;

	/**
	 * Create builder
	 *
	 * @return A builder of {@link CommitGraph}.
	 */
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

	CommitGraphBuilder addBloomFilterIndex(byte[] buffer)
			throws CommitGraphFormatException {
		assertChunkNotSeenYet(bloomFilterIndex, CHUNK_ID_BLOOM_FILTER_INDEX);
		bloomFilterIndex = buffer;
		return this;
	}

	CommitGraphBuilder addBloomFilterData(byte[] buffer)
			throws CommitGraphFormatException {
		assertChunkNotSeenYet(bloomFilterData, CHUNK_ID_BLOOM_FILTER_DATA);
		bloomFilterData = buffer;
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
		GraphChangedPathFilterData cpfData = new GraphChangedPathFilterData(
				bloomFilterIndex, bloomFilterData);
		return new CommitGraphV1(index, commitDataChunk, cpfData);
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
