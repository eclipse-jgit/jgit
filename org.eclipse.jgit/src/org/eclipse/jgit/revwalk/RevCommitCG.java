/*
 * Copyright (C) 2023, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.revwalk;

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;

/**
 * RevCommit which parsed from
 * {@link org.eclipse.jgit.internal.storage.commitgraph.CommitGraph}.
 */
public class RevCommitCG extends RevCommit {

	private int graphPosition;

	private int generation = Constants.COMMIT_GENERATION_UNKNOWN;

	/**
	 * Create a new commit reference.
	 *
	 * @param id
	 *            object name for the commit.
	 * @param graphPosition
	 *            the position in the commit-graph of the object.
	 */
	protected RevCommitCG(AnyObjectId id, int graphPosition) {
		super(id);
		this.graphPosition = graphPosition;
	}

	/** {@inheritDoc} */
	@Override
	void parseHeaders(RevWalk walk) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		if (!parseInGraph(walk)) {
			super.parseHeaders(walk);
		}
		if (walk.isRetainBody()) {
			super.parseBody(walk);
		}
	}

	boolean parseInGraph(RevWalk walk) {
		CommitGraph graph = walk.commitGraph().orElse(null);
		if (graph == null) {
			return false;
		}
		CommitGraph.CommitData data = graph.getCommitData(graphPosition);
		if (data == null) {
			return false;
		}

		this.tree = walk.lookupTree(data.getTree());
		this.commitTime = (int) data.getCommitTime();
		this.generation = data.getGeneration();

		int[] pGraphList = data.getParents();
		RevCommit[] pList = new RevCommit[pGraphList.length];
		for (int i = 0; i < pList.length; i++) {
			int graphPos = pGraphList[i];
			ObjectId objId = graph.getObjectId(graphPos);
			pList[i] = walk.lookupCommit(objId, graphPos);
		}
		this.parents = pList;

		flags |= PARSED;
		return true;
	}

	/** {@inheritDoc} */
	@Override
	int getGeneration() {
		return generation;
	}
}
