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

import static org.eclipse.jgit.internal.ketch.KetchReplica.State.OFFLINE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.ObjectId;

/**
 * A snapshot of a leader and its view of the world.
 */
public class LeaderSnapshot {
	final List<ReplicaSnapshot> replicas = new ArrayList<>();
	KetchLeader.State state;
	long term;
	LogIndex headIndex;
	LogIndex committedIndex;
	boolean idle;

	LeaderSnapshot() {
	}

	/**
	 * Get unmodifiable view of configured replicas.
	 *
	 * @return unmodifiable view of configured replicas.
	 */
	public Collection<ReplicaSnapshot> getReplicas() {
		return Collections.unmodifiableList(replicas);
	}

	/**
	 * Get current state of the leader.
	 *
	 * @return current state of the leader.
	 */
	public KetchLeader.State getState() {
		return state;
	}

	/**
	 * Whether the leader is not running a round to reach consensus, and has no
	 * rounds queued.
	 *
	 * @return {@code true} if the leader is not running a round to reach
	 *         consensus, and has no rounds queued.
	 */
	public boolean isIdle() {
		return idle;
	}

	/**
	 * Get term of this leader
	 *
	 * @return term of this leader. Valid only if {@link #getState()} is
	 *         currently
	 *         {@link org.eclipse.jgit.internal.ketch.KetchLeader.State#LEADER}.
	 */
	public long getTerm() {
		return term;
	}

	/**
	 * Get end of the leader's log
	 *
	 * @return end of the leader's log; null if leader hasn't started up enough
	 *         to begin its own election.
	 */
	@Nullable
	public LogIndex getHead() {
		return headIndex;
	}

	/**
	 * Get state the leader knows is committed on a majority of participant
	 * replicas
	 *
	 * @return state the leader knows is committed on a majority of participant
	 *         replicas. Null until the leader instance has committed a log
	 *         index within its own term.
	 */
	@Nullable
	public LogIndex getCommitted() {
		return committedIndex;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append(isIdle() ? "IDLE" : "RUNNING"); //$NON-NLS-1$ //$NON-NLS-2$
		s.append(" state ").append(getState()); //$NON-NLS-1$
		if (getTerm() > 0) {
			s.append(" term ").append(getTerm()); //$NON-NLS-1$
		}
		s.append('\n');
		s.append(String.format(
				"%-10s %12s %12s\n", //$NON-NLS-1$
				"Replica", "Accepted", "Committed")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		s.append("------------------------------------\n"); //$NON-NLS-1$
		debug(s, "(leader)", getHead(), getCommitted()); //$NON-NLS-1$
		s.append('\n');
		for (ReplicaSnapshot r : getReplicas()) {
			debug(s, r);
			s.append('\n');
		}
		s.append('\n');
		return s.toString();
	}

	private static void debug(StringBuilder b, ReplicaSnapshot s) {
		KetchReplica replica = s.getReplica();
		debug(b, replica.getName(), s.getAccepted(), s.getCommitted());
		b.append(String.format(" %-8s %s", //$NON-NLS-1$
				replica.getParticipation(), s.getState()));
		if (s.getState() == OFFLINE) {
			String err = s.getErrorMessage();
			if (err != null) {
				b.append(" (").append(err).append(')'); //$NON-NLS-1$
			}
		}
	}

	private static void debug(StringBuilder s, String name,
			ObjectId accepted, ObjectId committed) {
		s.append(String.format(
				"%-10s %-12s %-12s", //$NON-NLS-1$
				name, str(accepted), str(committed)));
	}

	static String str(ObjectId c) {
		if (c instanceof LogIndex) {
			return ((LogIndex) c).describeForLog();
		} else if (c != null) {
			return c.abbreviate(8).name();
		}
		return "-"; //$NON-NLS-1$
	}
}
