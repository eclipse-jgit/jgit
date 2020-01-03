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
