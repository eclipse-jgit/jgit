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
 * RevCommit parsed from
 * {@link org.eclipse.jgit.internal.storage.commitgraph.CommitGraph}.
 *
 * @since 6.5
 */
class RevCommitCG extends RevCommit {

	private final int graphPosition;

	private int generationV1 = Constants.COMMIT_GENERATION_UNKNOWN_V1;

	private long generationV2 = Constants.COMMIT_GENERATION_UNKNOWN_V2;

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

	@Override
	void parseCanonical(RevWalk walk, byte[] raw) throws IOException {
		if (walk.isRetainBody()) {
			buffer = raw;
		}
		parseInGraph(walk);
	}

	@Override
	void parseHeaders(RevWalk walk) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		if (walk.isRetainBody()) {
			super.parseBody(walk); // This parses header and body
			return;
		}
		parseInGraph(walk);
	}

	private void parseInGraph(RevWalk walk) throws IOException {
		CommitGraph graph = walk.commitGraph();
		CommitGraph.CommitData commitData = graph.getCommitData(graphPosition);
		if (commitData == null) {
			// RevCommitCG was created because we got its graphPosition from
			// commit-graph. If now the commit-graph doesn't know about it,
			// something went wrong.
			throw new IllegalStateException();
		}
		if (!walk.shallowCommitsInitialized) {
			walk.initializeShallowCommits(this);
		}

		this.tree = walk.lookupTree(commitData.getTree());
		this.commitTime = (int) commitData.getCommitTime();
		this.generationV1 = commitData.getGeneration();

		if (graph.getGenerationVersion() > 1) {
			CommitGraph.GenerationData generationData = graph
					.getGenerationData(graphPosition);
			this.generationV2 = commitData.getCommitTime()
					+ generationData.getGenerationData();
		}

		if (getParents() == null) {
			int[] pGraphList = commitData.getParents();
			if (pGraphList.length == 0) {
				this.parents = RevCommit.NO_PARENTS;
			} else {
				RevCommit[] pList = new RevCommit[pGraphList.length];
				for (int i = 0; i < pList.length; i++) {
					int graphPos = pGraphList[i];
					ObjectId objId = graph.getObjectId(graphPos);
					pList[i] = walk.lookupCommit(objId, graphPos);
				}
				this.parents = pList;
			}
		}
		flags |= PARSED;
	}

	@Override
	int getGenerationV1() {
		return generationV1;
	}

	/** {@inheritDoc} */
	@Override
	long getGenerationV2() {
		return generationV2;
	}
}
