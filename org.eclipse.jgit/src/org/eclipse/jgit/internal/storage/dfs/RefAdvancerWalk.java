/*
 * Copyright (C) 2026, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.dfs;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

/**
 * Walk from some commits and find where to they enter a pack
 */
class RefAdvancerWalk {

	private final DfsRepository db;

	private final InPackPredicate includeP;

	/**
	 * True when the commit is in the pack
	 */
	@FunctionalInterface
	interface InPackPredicate {
		boolean test(RevCommit c) throws IOException;
	}

	RefAdvancerWalk(DfsRepository db, InPackPredicate include) {
		this.db = db;
		this.includeP = include;
	}

	private RevWalk createRevWalk() {
		RevWalk rw = new RevWalk(db);
		rw.sort(RevSort.COMMIT_TIME_DESC);
		rw.setRevFilter(new FirstInPack(includeP));
		return rw;
	}

	/**
	 * Advance the tips to their first commit inside the pack
	 *
	 * @param allTips
	 *            tips of interesting refs
	 * @return first commit(s) where the tips enter the pack. A tips may
	 *         translate into 0 commits (it doesn't enter the pack in its
	 *         history), 1 commit (a linear history) or n commits (merges lead
	 *         to multiple histories into the pack). A tip already inside the
	 *         pack is returned as it is.
	 * @throws IOException
	 *             error browsing history
	 */
	Set<RevCommit> advance(List<ObjectId> allTips) throws IOException {
		Set<RevCommit> tipsInMidx = new HashSet<>(allTips.size());
		try (RevWalk rw = createRevWalk()) {
			for (ObjectId tip : allTips) {
				RevObject tipObject = rw.parseAny(tip);
				if (!(tipObject instanceof RevCommit tipCommit)) {
					continue;
				}

				rw.markStart(tipCommit);
				RevCommit inPack;
				while ((inPack = rw.next()) != null) {
					tipsInMidx.add(inPack);
				}
			}
		}
		return tipsInMidx;
	}

	private static class FirstInPack extends RevFilter {

		private final InPackPredicate isInPack;

		FirstInPack(InPackPredicate isInPack) {
			this.isInPack = isInPack;
		}

		@Override
		public boolean include(RevWalk walker, RevCommit cmit)
				throws StopWalkException, IOException {
			if (!isInPack.test(cmit)) {
				return false;
			}

			for (RevCommit p : cmit.getParents()) {
				walker.markUninteresting(p);
			}
			return true;
		}

		@Override
		public RevFilter clone() {
			return this;
		}
	}
}
