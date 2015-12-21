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

/** One round-trip to all replicas proposing a log entry. */
abstract class Round {
	final KetchLeader leader;
	final LogId acceptedOld;
	LogId acceptedNew;
	List<ReceiveCommand> stageCommands;

	Round(KetchLeader leader, LogId head) {
		this.leader = leader;
		this.acceptedOld = head;
	}

	/**
	 * Invoked without {@link KetchLeader#lock} to build objects.
	 * <p>
	 * Creates new accepted commit and calls {@link #acceptAsync(AnyObjectId)}
	 * to begin execution of the round across the system.
	 *
	 * @throws IOException
	 *             the round cannot build new objects within the leader's
	 *             repository. The leader may be unable to execute.
	 */
	abstract void start() throws IOException;

	void acceptAsync(AnyObjectId id) {
		acceptedNew = acceptedOld.nextId(id);
		leader.acceptAsync(this);
	}

	/**
	 * Notify the round it was accepted by a majority of the system.
	 * <p>
	 * Invoked with {@link KetchLeader#lock} held by the caller.
	 */
	abstract void success();
}
