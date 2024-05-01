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
 * The CommitGraph is a supplemental data structure that accelerates commit
 * graph walks.
 * <p>
 * If a user downgrades or disables the <code>core.commitGraph</code> config
 * setting, then the existing object database is sufficient.
 * </p>
 * <p>
 * It stores the commit graph structure along with some extra metadata to speed
 * up graph walks. By listing commit OIDs in lexicographic order, we can
 * identify an integer position for each commit and refer to the parents of a
 * commit using those integer positions. We use binary search to find initial
 * commits and then use the integer positions for fast lookups during the walk.
 * </p>
 */
public interface CommitGraph {

	/** Empty {@link CommitGraph} with no results. */
	CommitGraph EMPTY = new CommitGraph() {
		@Override
		public int findGraphPosition(AnyObjectId commit) {
			return -1;
		}

		@Override
		public CommitData getCommitData(int graphPos) {
			return null;
		}

		@Override
		public ObjectId getObjectId(int graphPos) {
			return null;
		}

		@Override
		public ChangedPathFilter getChangedPathFilter(int graphPos) {
			return null;
		}

		@Override
		public long getCommitCnt() {
			return 0;
		}
	};

	/**
	 * Find the position in the commit-graph of the commit.
	 * <p>
	 * The position can only be used within the CommitGraph Instance you got it
	 * from. That's because the graph position of the same commit may be
	 * different in CommitGraph obtained at different times (eg., regenerated
	 * new commit-graph).
	 *
	 * @param commit
	 *            the commit for which the commit-graph position will be found.
	 * @return the commit-graph position or -1 if the object was not found.
	 */
	int findGraphPosition(AnyObjectId commit);

	/**
	 * Get the metadata of a commit。
	 * <p>
	 * This function runs in time O(1).
	 * <p>
	 * In the process of commit history traversal,
	 * {@link CommitData#getParents()} makes us get the graphPos of the commit's
	 * parents in advance, so that we can avoid O(logN) lookup and use O(1)
	 * lookup instead.
	 *
	 * @param graphPos
	 *            the position in the commit-graph of the object.
	 * @return the metadata of a commit or null if it's not found.
	 */
	CommitData getCommitData(int graphPos);

	/**
	 * Get the object at the commit-graph position.
	 *
	 * @param graphPos
	 *            the position in the commit-graph of the object.
	 * @return the ObjectId or null if it's not found.
	 */
	ObjectId getObjectId(int graphPos);

	/**
	 * Get the changed path filter of the object at the commit-graph position.
	 *
	 * @param graphPos
	 *            the position in the commit-graph of the object.
	 * @return the bloom filter or null if it's not found.
	 */
	ChangedPathFilter getChangedPathFilter(int graphPos);

	/**
	 * Obtain the total number of commits described by this commit-graph.
	 *
	 * @return number of commits in this commit-graph.
	 */
	long getCommitCnt();

	/**
	 * Metadata of a commit in commit data chunk.
	 */
	interface CommitData {

		/**
		 * Get a reference to this commit's tree.
		 *
		 * @return tree of this commit.
		 */
		ObjectId getTree();

		/**
		 * Obtain an array of all parents.
		 * <p>
		 * The method only provides the graph positions of parents in
		 * commit-graph, call {@link CommitGraph#getObjectId(int)} to get the
		 * real objectId.
		 *
		 * @return the array of parents.
		 */
		int[] getParents();

		/**
		 * Time from the "committer" line.
		 *
		 * @return the commit time in seconds since EPOCH.
		 */
		long getCommitTime();

		/**
		 * Get the generation number (the distance from the root) of the commit.
		 *
		 * @return the generation number or
		 *         {@link org.eclipse.jgit.lib.Constants#COMMIT_GENERATION_NOT_COMPUTED}
		 *         if the writer didn't calculate it.
		 */
		int getGeneration();
	}
}
