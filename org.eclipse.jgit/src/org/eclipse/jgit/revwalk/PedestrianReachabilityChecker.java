/*
 * Copyright (C) 2019, Google LLC.
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
package org.eclipse.jgit.revwalk;

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Checks the reachability walking the graph from the starters towards the
 * target.
 *
 * @since 5.5
 */
public class PedestrianReachabilityChecker implements ReachabilityChecker {

	private final boolean topoSort;

	private final RevWalk walk;

	/**
	 * New instance of the reachability checker using a existing walk.
	 *
	 * @param topoSort
	 *            walk commits in topological order
	 * @param walk
	 *            RevWalk instance to reuse. Caller retains ownership.
	 */
	public PedestrianReachabilityChecker(boolean topoSort,
			RevWalk walk) {
		this.topoSort = topoSort;
		this.walk = walk;
	}

	@Override
	public boolean isReachable(RevCommit target,
			Iterable<? extends ObjectId> starters) throws IOException {

		if (!starters.iterator().hasNext()) {
			return false;
		}
		walk.reset();
		if (topoSort) {
			walk.sort(RevSort.TOPO);
		}

		walk.markStart(target);
		for (ObjectId id : starters) {
			markUninteresting(walk, id);
		}
		// If the commit is reachable from any given tip, it will appear to be
		// uninteresting to the RevWalk and no output will be produced.
		return walk.next() == null;
	}

	private static void markUninteresting(RevWalk walk, ObjectId id)
			throws IOException {
		if (id == null) {
			return;
		}
		try {
			walk.markUninteresting(walk.parseCommit(id));
		} catch (IncorrectObjectTypeException | MissingObjectException e) {
			// Do nothing, doesn't affect reachability.
		}
	}
}
