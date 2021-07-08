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
public class CommitGraphSingleFile implements CommitGraph {

	private final CommitGraphFileContent content;

	/**
	 * Creates CommitGraph by a single commit-graph file.
	 *
	 * @param content
	 *            the commit-graph file content in memory
	 */
	public CommitGraphSingleFile(CommitGraphFileContent content) {
		this.content = content;
	}

	/** {@inheritDoc} */
	@Override
	public CommitData getCommitData(AnyObjectId commit) {
		int graphPos = content.findGraphPosition(commit);
		return getCommitData(graphPos);
	}

	/** {@inheritDoc} */
	@Override
	public CommitData getCommitData(int graphPos) {
		if (graphPos < 0 || graphPos > content.getCommitCnt()) {
			return null;
		}
		return content.getCommitData(graphPos);
	}

	/** {@inheritDoc} */
	@Override
	public ObjectId getObjectId(int graphPos) {
		return content.getObjectId(graphPos);
	}

	/** {@inheritDoc} */
	@Override
	public long getCommitCnt() {
		return content.getCommitCnt();
	}
}
