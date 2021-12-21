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
 * setting, then the existing object database is sufficient.
 * </p>
 * <p>
 * It stores the commit graph structure along with some extra metadata to speed
 * up graph walks. By listing commit OIDs in lexicographic order, we can
 * identify an integer position for each commit and refer to the parents of a
 * commit using those integer positions. We use binary search to find initial
 * commits and then use the integer positions for fast lookups during the walk.
 * </p>
 *
 * @since 6.1
 */
public interface CommitGraph {

	/**
	 * We use GENERATION_UNKNOWN({@value}) to mark commits not in the
	 * commit-graph file.
	 */
	int GENERATION_UNKNOWN = Integer.MAX_VALUE;

	/**
	 * If a commit-graph file was written by a version of Git that did not
	 * compute generation numbers, then those commits will have generation
	 * number represented by GENERATION_NOT_COMPUTED({@value}).
	 */
	int GENERATION_NOT_COMPUTED = 0;

	/**
	 * Get the metadata of a commit.
	 *
	 * @param commit
	 *            the commit object id to inspect.
	 * @return the metadata of a commit or null if it's not found.
	 */
	CommitData getCommitData(AnyObjectId commit);

	/**
	 * Get the metadata of a commit。
	 *
	 * @param graphPos
	 *            the position in the commit-graph of the object.
	 * @return the metadata of a commit or null if it's not found.
	 */
	CommitData getCommitData(int graphPos);

	/**
	 * Finds the bloom filter in the commit-graph of the commit.
	 *
	 * @param commit
	 *            the commit object id to inspect.
	 * @return the bloom filter or null if it was not found.
	 */
	BloomFilter findBloomFilter(AnyObjectId commit);

	/**
	 * Generates a new key by the path.
	 *
	 * @param path
	 *            the input string
	 * @return the key or null if commit-graph does not contain a bloom filter.
	 */
	BloomFilter.Key newBloomKey(String path);

	/**
	 * Get the object at the commit-graph position.
	 *
	 * @param graphPos
	 *            the position in the commit-graph of the object.
	 * @return the ObjectId or null if it's not found.
	 */
	ObjectId getObjectId(int graphPos);

	/**
	 * Obtain the total number of commits described by this commit-graph.
	 *
	 * @return number of commits in this commit-graph
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
		 * @return commit time
		 */
		long getCommitTime();

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
		 * We use {@link #GENERATION_UNKNOWN} to mark commits not in the
		 * commit-graph file. If a commit-graph file was written without
		 * computing generation numbers, then those commits will have generation
		 * number represented by {@link #GENERATION_NOT_COMPUTED}.
		 *
		 * @return the generation number
		 */
		int getGeneration();
	}
}
