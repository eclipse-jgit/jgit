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
