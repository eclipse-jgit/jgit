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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.BlockList;

/**
 * Represents the Oid Lookup of commit-graph, the commits inside are arranged in
 * OID order.
 */
class InMemoryOidLookup implements Iterable<RevCommit> {

	public static InMemoryOidLookup load(ProgressMonitor pm,
			@NonNull Set<? extends ObjectId> wants, RevWalk walk)
			throws IOException {
		walk.reset();
		walk.sort(RevSort.COMMIT_TIME_DESC);
		walk.setRetainBody(false);
		for (ObjectId id : wants) {
			RevObject o = walk.parseAny(id);
			if (o instanceof RevCommit) {
				walk.markStart((RevCommit) o);
			}
		}
		List<RevCommit> commits = new BlockList<>();
		RevCommit c;
		pm.beginTask(JGitText.get().findingCommitsForCommitGraph,
				ProgressMonitor.UNKNOWN);
		while ((c = walk.next()) != null) {
			pm.update(1);
			commits.add(c);
		}
		pm.endTask();
		return new InMemoryOidLookup(commits);
	}

	private final List<RevCommit> sortedCommits;

	private final Map<ObjectId, Integer> id2Pos;

	private int extraEdgeCnt;

	private InMemoryOidLookup(List<RevCommit> commits) {
		Collections.sort(commits); // sorted by name
		sortedCommits = commits;
		id2Pos = new HashMap<>();
		extraEdgeCnt = 0;
		for (int i = 0; i < commits.size(); i++) {
			RevCommit c = sortedCommits.get(i);
			if (c.getParentCount() > 2) {
				extraEdgeCnt += c.getParentCount() - 1;
			}
			id2Pos.put(c, Integer.valueOf(i));
		}
	}

	public int getOidPosition(RevCommit c) throws MissingObjectException {
		Integer pos = id2Pos.get(c);
		if (pos == null) {
			throw new MissingObjectException(c, Constants.OBJ_COMMIT);
		}
		return pos.intValue();
	}

	public RevCommit getCommit(int oidPos) {
		return sortedCommits.get(oidPos);
	}

	public int getExtraEdgeCnt() {
		return extraEdgeCnt;
	}

	public int size() {
		return sortedCommits.size();
	}

	@Override
	public Iterator<RevCommit> iterator() {
		return sortedCommits.iterator();
	}
}
