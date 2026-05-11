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

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.revwalk.filter.RevFilter;

final class TopoExplorePhase extends TopoPhase {

	private final RevFlag topoPassedFilterFlag;

	private final RevFilter filter;

	private final boolean canDispose;

	TopoExplorePhase(RevWalk walker, RevFilter filter,
			boolean canDispose) {
		super(walker, "EXPLORE"); //$NON-NLS-1$
		this.topoPassedFilterFlag = walker.newFlag("TOPO_PASSED_FILTER"); //$NON-NLS-1$
		this.filter = filter;
		this.canDispose = canDispose;
	}

	void explore(int minGeneration) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		RevCommit c;
		while ((c = tryRemove(minGeneration)) != null) {
			if (propagateUninteresting(c)) {
				continue;
			}

			if (passThroughFilter(c)) {
				c.add(topoPassedFilterFlag);
			}

			for (RevCommit p : c.getParents()) {
				enqueue(p);
			}
		}
	}

	boolean hasPassedFilter(RevCommit c) {
		return c.has(topoPassedFilterFlag);
	}

	private boolean propagateUninteresting(RevCommit c) {
		walker.carryFlagsImpl(c);

		if ((c.flags & RevWalk.UNINTERESTING) != 0) {
			return true;
		}

		return false;
	}

	private boolean passThroughFilter(RevCommit c)
			throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		boolean hadBody = c.getRawBuffer() != null;

		if (filter.requiresCommitBody() && !hadBody) {
			c.parseBody(walker);
		}

		boolean passesFilter = filter.include(walker, c);

		if (!hadBody && canDispose) {
			c.disposeBody();
		}

		return passesFilter;
	}

}
