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

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Support for the commit-graph v1 format.
 *
 * @see CommitGraph
 */
class CommitGraphV1 implements CommitGraph {

	private final GraphObjectIndex idx;

	private final GraphCommitData commitData;

	private final GraphGenerationData generationData;

	private final int generationVersion;

	CommitGraphV1(int generationVersion, GraphObjectIndex index,
			GraphCommitData commitData, GraphGenerationData generationData) {
		this.generationVersion = generationVersion;
		this.idx = index;
		this.commitData = commitData;
		this.generationData = generationData;
	}

	CommitGraphV1(int generationVersion, GraphObjectIndex index,
			GraphCommitData commitData) {
		this.generationVersion = generationVersion;
		this.idx = index;
		this.commitData = commitData;
		this.generationData = null;
	}

	@Override
	public int findGraphPosition(AnyObjectId commit) {
		return idx.findGraphPosition(commit);
	}

	@Override
	public CommitData getCommitData(int graphPos) {
		if (!isPosValid(graphPos)) {
			return null;
		}
		return commitData.getCommitData(graphPos);
	}

	@Override
	public ObjectId getObjectId(int graphPos) {
		return idx.getObjectId(graphPos);
	}

	@Override
	public long getCommitCnt() {
		return idx.getCommitCnt();
	}

	@Override
	public GenerationData getGenerationData(int graphPos) {
		if (generationVersion <= 1){
			throw new UnsupportedOperationException();
		}
		if (!isPosValid(graphPos)) {
			return null;
		}
		return generationData.getGenerationData(graphPos);
	}

	@Override
	public int getGenerationVersion() {
		return generationVersion;
	}

	private boolean isPosValid(int graphPos) {
		return graphPos >= 0 && graphPos < getCommitCnt();
	}
}
