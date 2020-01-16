/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.ketch;

import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * One round-trip to all replicas proposing a log entry.
 * <p>
 * In Raft a log entry represents a state transition at a specific index in the
 * replicated log. The leader can only append log entries to the log.
 * <p>
 * In Ketch a log entry is recorded under the {@code refs/txn} namespace. This
 * occurs when:
 * <ul>
 * <li>a replica wants to establish itself as a new leader by proposing a new
 * term (see {@link ElectionRound})
 * <li>an established leader wants to gain consensus on new {@link Proposal}s
 * (see {@link ProposalRound})
 * </ul>
 */
abstract class Round {
	final KetchLeader leader;
	final LogIndex acceptedOldIndex;
	LogIndex acceptedNewIndex;
	List<ReceiveCommand> stageCommands;

	Round(KetchLeader leader, LogIndex head) {
		this.leader = leader;
		this.acceptedOldIndex = head;
	}

	KetchSystem getSystem() {
		return leader.getSystem();
	}

	/**
	 * Creates a commit for {@code refs/txn/accepted} and calls
	 * {@link #runAsync(AnyObjectId)} to begin execution of the round across
	 * the system.
	 * <p>
	 * If references are being updated (such as in a {@link ProposalRound}) the
	 * RefTree may be modified.
	 * <p>
	 * Invoked without {@link KetchLeader#lock} to build objects.
	 *
	 * @throws IOException
	 *             the round cannot build new objects within the leader's
	 *             repository. The leader may be unable to execute.
	 */
	abstract void start() throws IOException;

	/**
	 * Asynchronously distribute the round's new value for
	 * {@code refs/txn/accepted} to all replicas.
	 * <p>
	 * Invoked by {@link #start()} after new commits have been created for the
	 * log. The method passes {@code newId} to {@link KetchLeader} to be
	 * distributed to all known replicas.
	 *
	 * @param newId
	 *            new value for {@code refs/txn/accepted}.
	 */
	void runAsync(AnyObjectId newId) {
		acceptedNewIndex = acceptedOldIndex.nextIndex(newId);
		leader.runAsync(this);
	}

	/**
	 * Notify the round it was accepted by a majority of the system.
	 * <p>
	 * Invoked by the leader with {@link KetchLeader#lock} held by the caller.
	 */
	abstract void success();
}
