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

class TopoSortPendingGenerator extends Generator {

	private final int output;

	private final TopoExplorePhase explorePhase;

	private final TopoInDegreePhase inDegreePhase;

	protected TopoSortPendingGenerator(RevWalk walker, AbstractRevQueue pending,
			RevFilter filter, int outputType, boolean canDispose)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		super(walker.isFirstParent());

		this.output = (outputType | SORT_COMMIT_TIME_DESC | SORT_TOPO)
				& ~NEEDS_REWRITE;
		boolean needsRewrite = (outputType & NEEDS_REWRITE) != 0;
		this.explorePhase = new TopoExplorePhase(walker, filter, canDispose,
				needsRewrite);
		this.inDegreePhase = new TopoInDegreePhase(walker, explorePhase,
				needsRewrite);

		inDegreePhase.initialize(pending);

		int allocatedFlags = explorePhase.getAllocatedFlags();
		allocatedFlags |= inDegreePhase.getAllocatedFlags();
		walker.freeFlagOnReset(allocatedFlags);
	}

	@Override
	int outputType() {
		return output;
	}

	@Override
	RevCommit next() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		while (true) {
			RevCommit c = inDegreePhase.nextReady();

			if (c == null) {
				return null;
			}

			c.add(RevFlag.SEEN);

			if (explorePhase.hasPassedFilter(c)) {
				return c;
			}
		}
	}

}
