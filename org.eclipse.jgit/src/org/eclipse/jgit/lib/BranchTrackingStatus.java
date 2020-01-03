/*
 * Copyright (C) 2011, Robin Stocker <robin@nibor.org>
 * Copyright (C) 2012, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.io.IOException;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevWalkUtils;
import org.eclipse.jgit.revwalk.filter.RevFilter;

/**
 * Status of a branch's relation to its remote-tracking branch.
 */
public class BranchTrackingStatus {

	/**
	 * Compute the tracking status for the <code>branchName</code> in
	 * <code>repository</code>.
	 *
	 * @param repository
	 *            the git repository to compute the status from
	 * @param branchName
	 *            the local branch
	 * @return the tracking status, or null if it is not known
	 * @throws java.io.IOException
	 */
	public static BranchTrackingStatus of(Repository repository, String branchName)
			throws IOException {

		String shortBranchName = Repository.shortenRefName(branchName);
		String fullBranchName = Constants.R_HEADS + shortBranchName;
		BranchConfig branchConfig = new BranchConfig(repository.getConfig(),
				shortBranchName);

		String trackingBranch = branchConfig.getTrackingBranch();
		if (trackingBranch == null)
			return null;

		Ref tracking = repository.exactRef(trackingBranch);
		if (tracking == null)
			return null;

		Ref local = repository.exactRef(fullBranchName);
		if (local == null)
			return null;

		try (RevWalk walk = new RevWalk(repository)) {

			RevCommit localCommit = walk.parseCommit(local.getObjectId());
			RevCommit trackingCommit = walk.parseCommit(tracking.getObjectId());

			walk.setRevFilter(RevFilter.MERGE_BASE);
			walk.markStart(localCommit);
			walk.markStart(trackingCommit);
			RevCommit mergeBase = walk.next();

			walk.reset();
			walk.setRevFilter(RevFilter.ALL);
			int aheadCount = RevWalkUtils.count(walk, localCommit, mergeBase);
			int behindCount = RevWalkUtils.count(walk, trackingCommit,
					mergeBase);

			return new BranchTrackingStatus(trackingBranch, aheadCount,
					behindCount);
		}
	}

	private final String remoteTrackingBranch;

	private final int aheadCount;

	private final int behindCount;

	private BranchTrackingStatus(String remoteTrackingBranch, int aheadCount,
			int behindCount) {
		this.remoteTrackingBranch = remoteTrackingBranch;
		this.aheadCount = aheadCount;
		this.behindCount = behindCount;
	}

	/**
	 * Get full remote-tracking branch name
	 *
	 * @return full remote-tracking branch name
	 */
	public String getRemoteTrackingBranch() {
		return remoteTrackingBranch;
	}

	/**
	 * Get number of commits that the local branch is ahead of the
	 * remote-tracking branch
	 *
	 * @return number of commits that the local branch is ahead of the
	 *         remote-tracking branch
	 */
	public int getAheadCount() {
		return aheadCount;
	}

	/**
	 * Get number of commits that the local branch is behind of the
	 * remote-tracking branch
	 *
	 * @return number of commits that the local branch is behind of the
	 *         remote-tracking branch
	 */
	public int getBehindCount() {
		return behindCount;
	}
}
