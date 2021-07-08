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

import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_COMMIT_DATA;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_EXTRA_EDGE_LIST;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_OID_FANOUT;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_OID_LOOKUP;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;

import java.text.MessageFormat;

import org.eclipse.jgit.errors.CommitGraphFormatException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.NB;

/**
 * Builder for {@link CommitGraph}.
 */
class CommitGraphBuilder {

	private final int hashLength;

	private long[] oidFanout;

	private byte[] oidLookup;

	private byte[] commitData;

	private byte[] extraList;

	/** @return A builder of {@link CommitGraph}. */
	static CommitGraphBuilder builder() {
		return new CommitGraphBuilder(OBJECT_ID_LENGTH);
	}

	private CommitGraphBuilder(int hashLength) {
		this.hashLength = hashLength;
	}

	CommitGraphBuilder addOidFanout(byte[] buffer)
			throws CommitGraphFormatException {
		assertChunkNotRepeated(oidFanout, CHUNK_ID_OID_FANOUT);
		oidFanout = new long[GraphObjectIdIndex.FANOUT];
		for (int k = 0; k < oidFanout.length; k++) {
			oidFanout[k] = NB.decodeUInt32(buffer, k * 4);
		}
		return this;
	}

	CommitGraphBuilder addOidLookUp(byte[] buffer)
			throws CommitGraphFormatException {
		assertChunkNotRepeated(oidLookup, CHUNK_ID_OID_LOOKUP);
		oidLookup = buffer;
		return this;
	}

	CommitGraphBuilder addCommitData(byte[] buffer)
			throws CommitGraphFormatException {
		assertChunkNotRepeated(commitData, CHUNK_ID_COMMIT_DATA);
		commitData = buffer;
		return this;
	}

	CommitGraphBuilder addExtraList(byte[] buffer)
			throws CommitGraphFormatException {
		assertChunkNotRepeated(extraList, CHUNK_ID_EXTRA_EDGE_LIST);
		extraList = buffer;
		return this;
	}

	CommitGraph build() throws CommitGraphFormatException {
		assertChunkNotNull(oidFanout, CHUNK_ID_OID_FANOUT);
		assertChunkNotNull(oidLookup, CHUNK_ID_OID_LOOKUP);
		assertChunkNotNull(commitData, CHUNK_ID_COMMIT_DATA);

		GraphObjectIdIndex lookupIndex = new GraphObjectIdIndex(hashLength,
				oidFanout, oidLookup);
		GraphCommitData commitDataChunk = new GraphCommitData(hashLength,
				commitData, extraList);
		return new CommitGraphV1(lookupIndex, commitDataChunk);
	}

	private void assertChunkNotNull(Object object, int chunkId)
			throws CommitGraphFormatException {
		if (object == null) {
			throw new CommitGraphFormatException(
					MessageFormat.format(JGitText.get().commitGraphChunkNeeded,
							Integer.toHexString(chunkId)));
		}
	}

	private void assertChunkNotRepeated(Object object, int chunkId)
			throws CommitGraphFormatException {
		if (object != null) {
			throw new CommitGraphFormatException(MessageFormat.format(
					JGitText.get().commitGraphChunkRepeated,
					Integer.toHexString(chunkId)));
		}
	}
}
