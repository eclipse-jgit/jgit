/*
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.transport;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;

/**
 * Implementation of connectivity checker which try to do check with smaller set
 * of references first and if it fail will fall back to check against all
 * advertised references.
 *
 * This is useful for big repos with enormous number of references.
 *
 * @since 5.6
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
	public void checkConnectivity(BaseReceivePack baseReceivePack,
			Set<ObjectId> advertisedHaves, ProgressMonitor checking)
			throws MissingObjectException, IOException {
		try {
			Set<ObjectId> immediateRefs = new HashSet<>();
			Set<ObjectId> newRefs = new HashSet<>();
			for (ReceiveCommand cmd : baseReceivePack.getAllCommands()) {
				if (cmd.getType() == ReceiveCommand.Type.UPDATE || cmd
						.getType() == ReceiveCommand.Type.UPDATE_NONFASTFORWARD) {
					if (advertisedHaves.contains(cmd.getOldId())) {
						immediateRefs.add(cmd.getOldId());
					}
					if (advertisedHaves.contains(cmd.getNewId())) {
						immediateRefs.add(cmd.getNewId());
					}
				} else if (cmd.getType() == ReceiveCommand.Type.CREATE) {
					if (advertisedHaves.contains(cmd.getNewId())) {
						immediateRefs.add(cmd.getNewId());
					} else {
						newRefs.add(cmd.getNewId());
					}
				}
			}
			if (!newRefs.isEmpty()) {
				immediateRefs.addAll(extractAdvertisedParentCommits(
						baseReceivePack, advertisedHaves, newRefs));
			}

			immediateRefs.addAll(forcedHaves);

			if (!immediateRefs.isEmpty()) {
				connectivityChecker.checkConnectivity(baseReceivePack,
						immediateRefs, checking);
				return;
			}
		} catch (MissingObjectException e) {
			// This is fine, rolling back to all haves.
		}
		connectivityChecker.checkConnectivity(baseReceivePack,
				advertisedHaves, checking);
	}

	/**
	 * @param forcedHaves
	 *            Haves server expect client to depend on.
	 *
	 */
	public void setForcedHaves(Set<ObjectId> forcedHaves) {
		this.forcedHaves.addAll(forcedHaves);
	}

	private Set<ObjectId> extractAdvertisedParentCommits(
			BaseReceivePack baseReceivePack, Set<ObjectId> advertisedHaves,
			Set<ObjectId> newRefs)
			throws MissingObjectException, IOException {
		Set<ObjectId> advertisedParents = new HashSet<>();
		for (ObjectId newRef : newRefs) {
			RevObject object = baseReceivePack.walk.parseAny(newRef);
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
