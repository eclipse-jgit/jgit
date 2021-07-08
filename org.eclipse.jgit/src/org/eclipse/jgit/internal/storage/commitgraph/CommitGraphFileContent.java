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
 * Representation of the commit-graph file content.
 * <p>
 * The commit-graph stores a list of commit OIDs and some associated metadata,
 * including:
 * <ol>
 * <li>The generation number of the commit. Commits with no parents have
 * generation number 1; commits with parents have generation number one more
 * than the maximum generation number of its parents. We reserve zero as
 * special, and can be used to mark a generation number invalid or as "not
 * computed".</li>
 * <li>The root tree OID.</li>
 * <li>The commit date.</li>
 * <li>The parents of the commit, stored using positional references within the
 * graph file.</li>
 * </ol>
 * </p>
 */
interface CommitGraphFileContent {

	/**
	 * Finds the position in the commit-graph of the object.
	 *
	 * @param objId
	 *            the id for which the commit-graph position will be found.
	 * @return the commit-graph id or -1 if the object was not found.
	 */
	int findGraphPosition(AnyObjectId objId);

	/**
	 * Get the object at the commit-graph position.
	 *
	 * @param graphPos
	 *            the position in the commit-graph of the object.
	 * @return the ObjectId or null if the object was not found.
	 */
	ObjectId getObjectId(int graphPos);

	/**
	 * Get the metadata of a commit。
	 *
	 * @param graphPos
	 *            the position in the commit-graph of the object.
	 * @return the metadata of a commit or null if it's not found.
	 */
	CommitGraph.CommitData getCommitData(int graphPos);

	/**
	 * Obtain the total number of commits described by this commit-graph.
	 *
	 * @return number of commits in this commit-graph
	 */
	long getCommitCnt();

	/**
	 * Get the hash length of this commit-graph
	 *
	 * @return object hash length
	 */
	int getHashLength();

	class CommitDataImpl implements CommitGraph.CommitData {

		ObjectId tree;

		int[] parents;

		long commitTime;

		int generation;

		@Override
		public ObjectId getTree() {
			return tree;
		}

		@Override
		public int[] getParents() {
			return parents;
		}

		@Override
		public long getCommitTime() {
			return commitTime;
		}

		@Override
		public int getGeneration() {
			return generation;
		}
	}
}
