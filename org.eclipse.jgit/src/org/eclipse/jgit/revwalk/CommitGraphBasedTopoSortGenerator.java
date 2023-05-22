/*
 * Copyright (C) 2023, HIS eG
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revwalk;

import static org.eclipse.jgit.lib.Constants.COMMIT_GENERATION_UNKNOWN;

import java.io.IOException;
import java.util.Comparator;
import java.util.PriorityQueue;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Sorts commits in topological order using the commit-graph.
 *
 * @see <a href=
 *      "https://devblogs.microsoft.com/devops/supercharging-the-git-commit-graph-iii-generations/#using-generation-number-for-topological-sorting">
 *      Conceptual idea </a>
 * @see <a href=
 *      "https://github.com/git/git/commit/b45424181e9e8b2284a48c6db7b8db635bbfccc8">
 *      First implementation in cgit </a>
 *
 * @since 6.6
 */
public class CommitGraphBasedTopoSortGenerator extends Generator {

	private static final int TOPO_WALK_EXPLORED = RevWalk.TOPO_WALK_EXPLORED;

	private boolean isRewriteEnabled = false;

	private final int outputType;

	private final Generator sourceGenerator;

	private final CommitGraph commitGraph;

	private Comparator<RevCommit> GenerationNumberComparator = (c1, c2) -> {
		if (c1 == c2) {
			return 0;
		}
		int g1 = getGenerationNumber(c1);
		int g2 = getGenerationNumber(c2);
		if (g1 < g2) {
			return 1;
		} else if (g1 > g2) {
			return -1;
		}
		// Generation numbers are equal. Compare using commit date.
		if (c1.commitTime < c2.commitTime) {
			return 1;
		} else if (c1.commitTime > c2.commitTime) {
			return -1;
		}
		return 0;
	};

	/**
	 * Contains commits up to which we have explored. Ordered by maximizing the
	 * generation number.
	 */
	PriorityQueue<RevCommit> exploreQueue;

	/**
	 * Contains commits for which an indegree value has been computed. Ordered
	 * by maximizing the generation number.
	 */
	PriorityQueue<RevCommit> indegreeQueue;

	/**
	 * Contains commits than can be emitted by this generator. Ordered by
	 * maximizing the commit time.
	 */
	DateRevQueue topoQueue;

	int minGeneration;

	CommitGraphBasedTopoSortGenerator(Generator s, RevWalk w)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		super(s.firstParent);
		if (w.getRewriteParents()) {
			isRewriteEnabled = true;
		}
		commitGraph = w.commitGraph();
		sourceGenerator = s;
		outputType = s.outputType() | SORT_TOPO;
		exploreQueue = new PriorityQueue<>(GenerationNumberComparator);
		indegreeQueue = new PriorityQueue<>(GenerationNumberComparator);
		topoQueue = new DateRevQueue();

		RevCommit c = s.next();
		if (c == null) {
			return;
		}
		minGeneration = getGenerationNumber(c);
		exploreQueue.add(c);
		c.inDegree = 1;
		indegreeQueue.add(c);
		topoQueue.add(c);

		computeIndegreeToDepth();
	}

	@Override
	int outputType() {
		return outputType;
	}

	@Override
	void shareFreeList(BlockRevQueue q) {
		sourceGenerator.shareFreeList(q);
	}

	private void exploreWalkStep() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		RevCommit c = exploreQueue.poll();
		if (c == null) {
			return;
		}

		if (isRewriteEnabled) {
			rewriteParents(c);
		}

		if (c.getParents() == null) {
			return;
		}

		for (RevCommit p : c.getParents()) {
			if ((p.flags & TOPO_WALK_EXPLORED) != TOPO_WALK_EXPLORED) {
				p.flags |= TOPO_WALK_EXPLORED;
				exploreQueue.add(p);
			}
		}

	}

	/**
	 * Makes sure that the previous {@link PendingGenerator} has filtered
	 * parents and that the previous {@link RewriteGenerator} has rewritten
	 * parents.
	 *
	 * @param c
	 * @throws IOException
	 * @throws IncorrectObjectTypeException
	 * @throws MissingObjectException
	 */
	private void rewriteParents(RevCommit c) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		if (c.getParents() == null) {
			return;
		}
		loop: while (true) {
			for (RevCommit parent : c.getParents()) {
				if (((parent.flags & RevWalk.TREE_REV_FILTER_APPLIED) == 0)
						|| ((parent.flags
								& RevWalk.REWRITE) == RevWalk.REWRITE)) {
					RevCommit n = sourceGenerator.next();
					if (n == null) {
						// Source generator is exhausted; all parent commits
						// have been rewritten by RewriteGenerator
						return;
					}

					// Parent might have been rewritten. We must call
					// c.getParents() again to obtain the new parents
					continue loop;
				}
			}
			break loop;
		}
	}

	/**
	 * Advances the explore walk until the generation numbers of all commits in
	 * the {@link #exploreQueue} are smaller than the generationCutOff
	 *
	 * @param generationCutOff
	 * @throws IOException
	 * @throws IncorrectObjectTypeException
	 * @throws MissingObjectException
	 */
	private void exploreToDepth(int generationCutOff)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		while (!exploreQueue.isEmpty() && getGenerationNumber(
				exploreQueue.peek()) >= generationCutOff) {
			exploreWalkStep();
		}
	}

	private void indegreeWalkStep() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		RevCommit c = indegreeQueue.poll();
		if (c == null) {
			return;
		}

		exploreToDepth(getGenerationNumber(c));

		if (c.getParents() == null) {
			return;
		}

		for (RevCommit p : c.getParents()) {
			/*
			 * Note:
			 *
			 * indegree == 0 -> commit was not yet seen by the indegree walk
			 *
			 * indegree == 1 -> all children of this commit have been emitted
			 *
			 * indegree >= 2 -> at least one child of this commit has not yet
			 * been emitted
			 */
			if (p.inDegree == 0) {
				p.inDegree = 2;
				indegreeQueue.add(p);
			} else {
				p.inDegree++;
			}

		}


	}

	/**
	 * Compute the indegree values until the generation numbers of all commits
	 * in the indegree queue are less than the {@link #minGeneration} number.
	 *
	 * @throws IOException
	 * @throws IncorrectObjectTypeException
	 * @throws MissingObjectException
	 */
	private void computeIndegreeToDepth() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		while (!indegreeQueue.isEmpty()
				&& getGenerationNumber(indegreeQueue.peek()) >= minGeneration) {
			indegreeWalkStep();
		}
	}


	private void expandTopoWalk(RevCommit c) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		if (c.getParents() == null) {
			return;
		}
		for (RevCommit p : c.getParents()) {
			int parentGenerationNumber = getGenerationNumber(p);
			if (parentGenerationNumber < minGeneration) {
				minGeneration = parentGenerationNumber;
				computeIndegreeToDepth();
			}

			// Decrement because we are about to emit commit c
			p.inDegree--;
			// Parent becomes ready to be emitted
			if (p.inDegree == 1) {
				topoQueue.add(p);
			}
		}
	}

	/**
	 * Return the next topological sorted commit and continue/expand the topo
	 * walk.
	 */
	@Override
	RevCommit next() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		RevCommit c = topoQueue.next();
		if (c == null) {
			return null;
		}

		expandTopoWalk(c);

		return c;
	}


	int getGenerationNumber(ObjectId id) {
		int graphPos = commitGraph.findGraphPosition(id);
		CommitGraph.CommitData commitData = commitGraph.getCommitData(graphPos);
		if (commitData != null) {
			return commitData.getGeneration();
		}
		return COMMIT_GENERATION_UNKNOWN;
	}


}