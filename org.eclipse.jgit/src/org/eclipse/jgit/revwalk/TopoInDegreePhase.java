/*
 * Copyright (c) 2026 Vector Informatik GmbH
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revwalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;

final class TopoInDegreePhase extends TopoPhase {

	private final TopoExplorePhase explorePhase;

	private final AbstractCommitRewriter rewriter;

	private final RevFlag topoSeen;

	private final AbstractRevQueue ready;

	private int minGeneration = Constants.COMMIT_GENERATION_UNKNOWN;

	TopoInDegreePhase(RevWalk walker, TopoExplorePhase explorePhase,
			boolean needsRewrite) {
		super(walker, "INDEGREE"); //$NON-NLS-1$

		this.explorePhase = explorePhase;
		this.rewriter = needsRewrite ? new TopoRewriter() : null;
		this.topoSeen = walker.newFlag("TOPO_SEEN"); //$NON-NLS-1$
		this.ready = RevWalk.newDateRevQueue(walker.isFirstParent());
	}

	void initialize(AbstractRevQueue p) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		List<RevCommit> roots = new ArrayList<>();

		for (RevCommit root = p.next(); root != null; root = p.next()) {
			roots.add(root);
			checkUpdateMinGeneration(root);

			explorePhase.enqueue(root);
			enqueue(root);
		}

		calculateInDegrees(minGeneration);

		for (RevCommit root : roots) {
			checkAddReady(root);
		}
	}

	void calculateInDegrees(int cutoff) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		RevCommit c;

		while ((c = tryRemove(cutoff)) != null) {
			// Only perform inDegree calculation on commits, whose history has
			// been rewritten
			c = rewriteIfNeeded(c);

			visitChild(c);
		}
	}

	private RevCommit rewriteIfNeeded(RevCommit c)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		explorePhase.explore(c.getEffectiveGeneration());

		if (rewriter != null) {
			return rewriter.rewriteParents(c);
		}

		return c;
	}

	private void visitChild(RevCommit c) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		for (RevCommit p : c.getParents()) {
			if ((p.flags & RevWalk.UNINTERESTING) != 0) {
				continue;
			}

			p.inDegree++;
			enqueue(p);

			if (walker.isFirstParent()) {
				break;
			}
		}
	}

	RevCommit nextReady() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		RevCommit c = ready.next();

		if (c == null) {
			return null;
		}

		expand(c);
		return c;
	}

	private void expand(RevCommit c) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		for (RevCommit p : c.getParents()) {
			if ((p.flags & RevWalk.UNINTERESTING) != 0) {
				continue;
			}

			if (checkUpdateMinGeneration(p)) {
				calculateInDegrees(minGeneration);
			}

			p.inDegree--;
			checkAddReady(p);

			if (walker.isFirstParent()) {
				return;
			}
		}
	}

	private void checkAddReady(RevCommit p) {
		if (p.inDegree == 0 && !p.has(topoSeen)) {
			p.add(topoSeen);
			ready.add(p);
		}
	}

	private boolean checkUpdateMinGeneration(RevCommit c)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		RevWalkUtils.ensureHeadersParsed(c, walker);
		int generation = c.getEffectiveGeneration();

		if (generation < minGeneration) {
			minGeneration = generation;
			return true;
		}

		return false;
	}

	private class TopoRewriter extends AbstractCommitRewriter {

		TopoRewriter() {
			super(walker.isFirstParent());
		}

		@Override
		void applyFilterToParents(RevCommit c) throws MissingObjectException,
				IncorrectObjectTypeException, IOException {
			for (RevCommit p : c.getParents()) {
				if ((p.flags & RevWalk.TREE_REV_FILTER_APPLIED) == 0) {
					RevWalkUtils.ensureHeadersParsed(p, walker);
					explorePhase.explore(p.getEffectiveGeneration());
				}
			}

		}

	}

}
