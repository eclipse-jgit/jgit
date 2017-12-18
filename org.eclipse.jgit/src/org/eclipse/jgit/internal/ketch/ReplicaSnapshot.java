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

import java.util.Date;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.ObjectId;

/**
 * A snapshot of a replica.
 *
 * @see LeaderSnapshot
 */
public class ReplicaSnapshot {
	final KetchReplica replica;
	ObjectId accepted;
	ObjectId committed;
	KetchReplica.State state;
	String error;
	long retryAtMillis;

	ReplicaSnapshot(KetchReplica replica) {
		this.replica = replica;
	}

	/**
	 * Get the replica this snapshot describes the state of
	 *
	 * @return the replica this snapshot describes the state of
	 */
	public KetchReplica getReplica() {
		return replica;
	}

	/**
	 * Get current state of the replica
	 *
	 * @return current state of the replica
	 */
	public KetchReplica.State getState() {
		return state;
	}

	/**
	 * Get last known Git commit at {@code refs/txn/accepted}
	 *
	 * @return last known Git commit at {@code refs/txn/accepted}
	 */
	@Nullable
	public ObjectId getAccepted() {
		return accepted;
	}

	/**
	 * Get last known Git commit at {@code refs/txn/committed}
	 *
	 * @return last known Git commit at {@code refs/txn/committed}
	 */
	@Nullable
	public ObjectId getCommitted() {
		return committed;
	}

	/**
	 * Get error message
	 *
	 * @return if {@link #getState()} ==
	 *         {@link org.eclipse.jgit.internal.ketch.KetchReplica.State#OFFLINE}
	 *         an optional human-readable message from the transport system
	 *         explaining the failure.
	 */
	@Nullable
	public String getErrorMessage() {
		return error;
	}

	/**
	 * Get when the leader will retry communication with the offline or lagging
	 * replica
	 *
	 * @return time (usually in the future) when the leader will retry
	 *         communication with the offline or lagging replica; null if no
	 *         retry is scheduled or necessary.
	 */
	@Nullable
	public Date getRetryAt() {
		return retryAtMillis > 0 ? new Date(retryAtMillis) : null;
	}
}
