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
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.BlockList;

/**
 * Index of commits in SHA1 order, which is used by the commit-graph writer to:
 * <ul>
 * <li>List commits in SHA1 order.</li>
 * <li>Get the position of a specific SHA1 in the list.</li>
 * </ul>
 */
class InMemoryOidLookup implements Iterable<RevCommit> {

	public static InMemoryOidLookup load(ProgressMonitor pm,
			@NonNull Set<? extends ObjectId> wants, RevWalk walk)
			throws IOException {
		walk.reset();
		walk.sort(RevSort.NONE);
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

	private final ObjectIdOwnerMap<CommitWithPosition> commitPosMap;

	private final int extraEdgeCnt;

	/**
	 * Initialize the InMemoryOidLookup.
	 *
	 * @param commits
	 *            the list of commits, and their headers need to be parsed.
	 */
	private InMemoryOidLookup(List<RevCommit> commits) {
		Collections.sort(commits); // sorted by name
		sortedCommits = commits;
		commitPosMap = new ObjectIdOwnerMap<>();
		int cnt = 0;
		for (int i = 0; i < commits.size(); i++) {
			RevCommit c = sortedCommits.get(i);
			if (c.getParentCount() > 2) {
				cnt += c.getParentCount() - 1;
			}
			commitPosMap.add(new CommitWithPosition(c, i));
		}
		this.extraEdgeCnt = cnt;
	}

	public int getOidPosition(RevCommit c) throws MissingObjectException {
		CommitWithPosition commitWithPosition = commitPosMap.get(c);
		if (commitWithPosition == null) {
			throw new MissingObjectException(c, Constants.OBJ_COMMIT);
		}
		return commitWithPosition.position;
	}

	public RevCommit getCommit(int oidPos) {
		if (oidPos < 0 || oidPos >= sortedCommits.size()) {
			return null;
		}
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

	private static class CommitWithPosition extends ObjectIdOwnerMap.Entry {

		final int position;

		CommitWithPosition(AnyObjectId id, int position) {
			super(id);
			this.position = position;
		}
	}
}
