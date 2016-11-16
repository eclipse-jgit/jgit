/*
 * Copyright (C) 2016, Google Inc.
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
