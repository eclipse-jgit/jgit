/*
 * Copyright (c) 2019, Google LLC  and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport.internal;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Implementation of connectivity checker which try to do check with smaller set
 * of references first and if it fail will fall back to check against all
 * advertised references.
 *
 * This is useful for big repos with enormous number of references.
 */
public class IterativeConnectivityChecker implements ConnectivityChecker {

	private final ConnectivityChecker connectivityChecker;

	private final Set<ObjectId> forcedHaves = new HashSet<>();

	/**
	 * @param connectivityChecker
	 *            Delegate checker which will be called for actual checks.
	 */
	public IterativeConnectivityChecker(
			ConnectivityChecker connectivityChecker) {
		this.connectivityChecker = connectivityChecker;
	}

	@Override
	public void checkConnectivity(ConnectivityCheckInfo connectivityCheckInfo,
			Set<ObjectId> advertisedHaves, ProgressMonitor pm)
			throws MissingObjectException, IOException {
		try {
			Set<ObjectId> expectedParents = new HashSet<>();
			Set<ObjectId> newRefs = new HashSet<>();
			for (ReceiveCommand cmd : connectivityCheckInfo.getCommands()) {
				if (cmd.getType() == ReceiveCommand.Type.UPDATE || cmd
						.getType() == ReceiveCommand.Type.UPDATE_NONFASTFORWARD) {
					if (advertisedHaves.contains(cmd.getOldId())) {
						expectedParents.add(cmd.getOldId());
					}
					if (advertisedHaves.contains(cmd.getNewId())) {
						expectedParents.add(cmd.getNewId());
					}
				} else if (cmd.getType() == ReceiveCommand.Type.CREATE) {
					if (advertisedHaves.contains(cmd.getNewId())) {
						expectedParents.add(cmd.getNewId());
					} else {
						newRefs.add(cmd.getNewId());
					}
				}
			}
			if (!newRefs.isEmpty()) {
				expectedParents.addAll(extractAdvertisedParentCommits(
						connectivityCheckInfo, advertisedHaves, newRefs));
			}

			expectedParents.addAll(forcedHaves);

			if (!expectedParents.isEmpty()) {
				connectivityChecker.checkConnectivity(connectivityCheckInfo,
						expectedParents, pm);
				return;
			}
		} catch (MissingObjectException e) {
			// This is fine, retry with all haves.
		}
		connectivityChecker.checkConnectivity(connectivityCheckInfo,
				advertisedHaves, pm);
	}

	/**
	 * @param forcedHaves
	 *            Haves server expects client to depend on.
	 *
	 */
	public void setForcedHaves(Set<ObjectId> forcedHaves) {
		this.forcedHaves.addAll(forcedHaves);
	}

	private Set<ObjectId> extractAdvertisedParentCommits(
			ConnectivityCheckInfo connectivityCheckInfo,
			Set<ObjectId> advertisedHaves,
			Set<ObjectId> newRefs)
			throws MissingObjectException, IOException {
		Set<ObjectId> advertisedParents = new HashSet<>();
		for (ObjectId newRef : newRefs) {
			RevObject object = connectivityCheckInfo.getWalk().parseAny(newRef);
			if (object instanceof RevCommit) {
				for (RevCommit parentCommit : ((RevCommit) object)
						.getParents()) {
					if (advertisedHaves.contains(parentCommit.getId())) {
						advertisedParents.add(parentCommit.getId());
					}
				}
			}
		}
		return advertisedParents;
	}

}
