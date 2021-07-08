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
import org.eclipse.jgit.lib.CommitGraph;
import org.eclipse.jgit.lib.ObjectId;

/**
 * CommitGraph implementation by a single commit-graph file.
 *
 * @see CommitGraph
 */
public class CommitGraphSingleImpl implements CommitGraph {

	private final CommitGraphData graphData;

	/**
	 * Creates CommitGraph by a single commit-graph file.
	 *
	 * @param graphData
	 *            the commit-graph file in memory
	 */
	public CommitGraphSingleImpl(CommitGraphData graphData) {
		this.graphData = graphData;
	}

	/** {@inheritDoc} */
	@Override
	public CommitData getCommitData(AnyObjectId commit) {
		int graphPos = graphData.findGraphPosition(commit);
		return getCommitData(graphPos);
	}

	/** {@inheritDoc} */
	@Override
	public CommitData getCommitData(int graphPos) {
		if (graphPos < 0 || graphPos > graphData.getCommitCnt()) {
			return null;
		}
		return graphData.getCommitData(graphPos);
	}

	/** {@inheritDoc} */
	@Override
	public ObjectId getObjectId(int graphPos) {
		return graphData.getObjectId(graphPos);
	}

	/** {@inheritDoc} */
	@Override
	public long getCommitCnt() {
		return graphData.getCommitCnt();
	}
}
