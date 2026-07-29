/*
 * Copyright (c) 2026 Vector Informatik GmbH
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revwalk;

import java.io.IOException;
import java.util.PriorityQueue;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;

class TopoPhase {

	private final RevFlag testFlag;

	private final PriorityQueue<RevCommit> queue;

	protected final RevWalk walker;

	TopoPhase(RevWalk walker, String name) {
		this.walker = walker;
		this.testFlag = walker.newFlag(name + "_SEEN"); //$NON-NLS-1$
		this.queue = new PriorityQueue<>(TopoPhase::compareGenThenDate);
	}

	private static int compareGenThenDate(RevCommit a, RevCommit b) {
		int genCompare = Integer.compare(b.getEffectiveGeneration(),
				a.getEffectiveGeneration());

		if (genCompare != 0) {
			return genCompare;
		}

		return Integer.compare(b.commitTime, a.commitTime);
	}

	final void enqueue(RevCommit c) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		if (!c.has(testFlag)) {
			RevWalkUtils.ensureHeadersParsed(c, walker);
			c.add(testFlag);
			queue.offer(c);
		}
	}

	/**
	 * If the queue is not empty and the head of the queue has an effective
	 * generation equal to or higher than {@code minGeneration}, it is removed
	 * from the queue and returned.
	 * <p>
	 * Otherwise, {@code null} is returned
	 *
	 * @param minGeneration
	 *            The minimum desired generation number
	 * @return The head of the queue or {@code null}
	 */
	final RevCommit tryRemove(int minGeneration) {
		RevCommit candidate = queue.peek();

		if (candidate == null) {
			return null;
		}

		// No need to parse the commit before querying generation, since only
		// parsed commits can be added to the queue
		if (candidate.getEffectiveGeneration() >= minGeneration) {
			queue.remove();
			return candidate;
		}

		return null;
	}

}
