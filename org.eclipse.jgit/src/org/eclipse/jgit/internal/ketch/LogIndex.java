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

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

/**
 * An ObjectId for a commit extended with incrementing log index.
 * <p>
 * For any two LogIndex instances, {@code A} is an ancestor of {@code C}
 * reachable through parent edges in the graph if {@code A.index < C.index}.
 * LogIndex provides a performance optimization for Ketch, the same information
 * can be obtained from {@link org.eclipse.jgit.revwalk.RevWalk}.
 * <p>
 * Index values are only valid within a single
 * {@link org.eclipse.jgit.internal.ketch.KetchLeader} instance after it has won
 * an election. By restricting scope to a single leader new leaders do not need
 * to traverse the entire history to determine the next {@code index} for new
 * proposals. This differs from Raft, where leader election uses the log index
 * and the term number to determine which replica holds a sufficiently
 * up-to-date log. Since Ketch uses Git objects for storage of its replicated
 * log, it keeps the term number as Raft does but uses standard Git operations
 * to imply the log index.
 * <p>
 * {@link org.eclipse.jgit.internal.ketch.Round#runAsync(AnyObjectId)} bumps the
 * index as each new round is constructed.
 */
public class LogIndex extends ObjectId {
	static LogIndex unknown(AnyObjectId id) {
		return new LogIndex(id, 0);
	}

	private final long index;

	private LogIndex(AnyObjectId id, long index) {
		super(id);
		this.index = index;
	}

	LogIndex nextIndex(AnyObjectId id) {
		return new LogIndex(id, index + 1);
	}

	/**
	 * Get index provided by the current leader instance.
	 *
	 * @return index provided by the current leader instance.
	 */
	public long getIndex() {
		return index;
	}

	/**
	 * Check if this log position committed before another log position.
	 * <p>
	 * Only valid for log positions in memory for the current leader.
	 *
	 * @param c
	 *            other (more recent) log position.
	 * @return true if this log position was before {@code c} or equal to c and
	 *         therefore any agreement of {@code c} implies agreement on this
	 *         log position.
	 */
	boolean isBefore(LogIndex c) {
		return index <= c.index;
	}

	/**
	 * Create string suitable for debug logging containing the log index and
	 * abbreviated ObjectId.
	 *
	 * @return string suitable for debug logging containing the log index and
	 *         abbreviated ObjectId.
	 */
	@SuppressWarnings("boxing")
	public String describeForLog() {
		return String.format("%5d/%s", index, abbreviate(6).name()); //$NON-NLS-1$
	}

	/** {@inheritDoc} */
	@SuppressWarnings("boxing")
	@Override
	public String toString() {
		return String.format("LogId[%5d/%s]", index, name()); //$NON-NLS-1$
	}
}
