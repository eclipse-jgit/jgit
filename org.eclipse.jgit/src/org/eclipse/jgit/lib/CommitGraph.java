/*
 * Copyright (C) 2021, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

/**
 * The CommitGraph is a supplemental data structure that accelerates commit
 * graph walks.
 * <p>
 * If a user downgrades or disables the <code>core.commitGraph</code> config
 * setting, then the existing ODB is sufficient.
 * </p>
 * <p>
 * It stores the commit graph structure along with some extra metadata to speed
 * up graph walks. By listing commit OIDs in lexicographic order, we can
 * identify an integer position for each commit and refer to the parents of a
 * commit using those integer positions. We use binary search to find initial
 * commits and then use the integer positions for fast lookups during the walk.
 * </p>
 *
 * @since 5.13
 */
public abstract class CommitGraph {

	/**
	 * We use GENERATION_NUMBER_INFINITY(-1) to mark commits not in the
	 * commit-graph file.
	 */
	public static final int GENERATION_NUMBER_INFINITY = -1;

	/**
	 * If a commit-graph file was written by a version of Git that did not
	 * compute generation numbers, then those commits will have generation
	 * number represented by GENERATION_NUMBER_ZERO(0).
	 */
	public static final int GENERATION_NUMBER_ZERO = 0;

	/**
	 * Get the metadata of a commit.
	 *
	 * @param commit
	 *            the commit object id to inspect.
	 * @return the metadata of a commit or null if it's not found.
	 */
	public abstract CommitData getCommitData(AnyObjectId commit);

	/**
	 * Get the metadata of a commit。
	 *
	 * @param graphPos
	 *            the position in the commit-graph of the object.
	 * @return the metadata of a commit or null if it's not found.
	 */
	public abstract CommitData getCommitData(int graphPos);

	/**
	 * Get the object at the commit-graph position.
	 *
	 * @param graphPos
	 *            the position in the commit-graph of the object.
	 * @return the ObjectId or null if it's not found.
	 */
	public abstract ObjectId getObjectId(int graphPos);

	/**
	 * Obtain the total number of commits described by this commit-graph.
	 *
	 * @return number of commits in this commit-graph
	 */
	public abstract long getCommitCnt();

	/**
	 * Metadata of a commit in commit data chunk.
	 */
	public abstract static class CommitData {

		/**
		 * Get a reference to this commit's tree.
		 *
		 * @return tree of this commit.
		 */
		public abstract ObjectId getTree();

		/**
		 * Obtain an array of all parents.
		 * <p>
		 * The method only provides the positions of parents in commit-graph,
		 * call {@link CommitGraph#getObjectId(int)} to get the real objectId.
		 *
		 * @return the array of parents.
		 */
		public abstract int[] getParents();

		/**
		 * Time from the "committer" line.
		 *
		 * @return commit time
		 */
		public abstract long getCommitTime();

		/**
		 * Get the generation number of the commit.
		 * <p>
		 * If A and B are commits with generation numbers N and M, respectively,
		 * and N <= M, then A cannot reach B. That is, we know without searching
		 * that B is not an ancestor of A because it is further from a root
		 * commit than A.
		 * <p>
		 * Conversely, when checking if A is an ancestor of B, then we only need
		 * to walk commits until all commits on the walk boundary have
		 * generation number at most N. If we walk commits using a priority
		 * queue seeded by generation numbers, then we always expand the
		 * boundary commit with highest generation number and can easily detect
		 * the stopping condition.
		 * <p>
		 * We use {@value #GENERATION_NUMBER_INFINITY} to mark commits not in the
		 * commit-graph file. If a commit-graph file was written without
		 * computing generation numbers, then those commits will have generation
		 * number represented by {@value #GENERATION_NUMBER_ZERO}.
		 *
		 * @return the generation number
		 */
		public abstract int getGeneration();
	}
}
