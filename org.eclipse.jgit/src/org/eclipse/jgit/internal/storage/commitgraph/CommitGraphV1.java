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

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Support for the commit-graph v1 format.
 *
 * @see CommitGraph
 */
class CommitGraphV1 implements CommitGraph {

	private final GraphObjectIdIndex idIdx;

	private final GraphCommitData commitData;

	CommitGraphV1(GraphObjectIdIndex lookupIndex, GraphCommitData commitData) {
		this.idIdx = lookupIndex;
		this.commitData = commitData;
	}

	/** {@inheritDoc} */
	@Override
	public int findGraphPosition(AnyObjectId commit) {
		return idIdx.findGraphPosition(commit);
	}

	/** {@inheritDoc} */
	@Override
	public CommitData getCommitData(int graphPos) {
		return commitData.getCommitData(graphPos);
	}

	/** {@inheritDoc} */
	@Override
	public ObjectId getObjectId(int graphPos) {
		return idIdx.getObjectId(graphPos);
	}

	/** {@inheritDoc} */
	@Override
	public long getCommitCnt() {
		return idIdx.getCommitCnt();
	}
}
