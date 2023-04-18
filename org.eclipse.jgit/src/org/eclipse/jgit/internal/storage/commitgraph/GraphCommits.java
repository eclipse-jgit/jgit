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
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.BlockList;

/**
 * The commits which are used by the commit-graph writer to:
 * <ul>
 * <li>List commits in SHA1 order.</li>
 * <li>Get the position of a specific SHA1 in the list.</li>
 * </ul>
 *
 * @since 6.5
 */
public class GraphCommits implements Iterable<RevCommit> {

	/**
	 * Prepare and create the commits for
	 * {@link org.eclipse.jgit.internal.storage.commitgraph.CommitGraphWriter}
	 * from the RevWalk.
	 *
	 * @param pm
	 *            progress monitor.
	 * @param wants
	 *            the list of wanted objects, writer walks commits starting at
	 *            these. Must not be {@code null}.
	 * @param walk
	 *            the RevWalk to use. Must not be {@code null}.
	 * @return the commits' collection which are used by the commit-graph
	 *         writer. Never null.
	 * @throws IOException
	 *             if an error occurred
	 */
	public static GraphCommits fromWalk(ProgressMonitor pm,
			@NonNull Set<? extends ObjectId> wants, @NonNull RevWalk walk)
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
		return new GraphCommits(commits, walk.getObjectReader());
	}

	private final List<RevCommit> sortedCommits;

	private final ObjectIdOwnerMap<CommitWithPosition> commitPosMap;

	private final int extraEdgeCnt;

	private final ObjectReader objectReader;

	/**
	 * Initialize the GraphCommits.
	 *
	 * @param commits
	 *            list of commits with their headers already parsed.
	 * @param objectReader
	 *            object reader
	 */
	private GraphCommits(List<RevCommit> commits, ObjectReader objectReader) {
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
		this.objectReader = objectReader;
	}

	int getOidPosition(RevCommit c) throws MissingObjectException {
		CommitWithPosition commitWithPosition = commitPosMap.get(c);
		if (commitWithPosition == null) {
			throw new MissingObjectException(c, Constants.OBJ_COMMIT);
		}
		return commitWithPosition.position;
	}

	int getExtraEdgeCnt() {
		return extraEdgeCnt;
	}

	int size() {
		return sortedCommits.size();
	}

	ObjectReader getObjectReader() {
		return objectReader;
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
