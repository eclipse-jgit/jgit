/*
 * Copyright (C) 2020, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revwalk;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.jgit.errors.MissingObjectException;

/**
 * Checks if all objects are reachable from certain starting points doing a
 * walk.
 */
class PedestrianObjectReachabilityChecker implements ObjectReachabilityChecker {
	private final ObjectWalk walk;

	/**
	 * New instance of the reachability checker using a existing walk.
	 *
	 * @param walk
	 *            ObjectWalk instance to reuse. Caller retains ownership.
	 */
	PedestrianObjectReachabilityChecker(ObjectWalk walk) {
		this.walk = walk;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Optional<RevObject> areAllReachable(Collection<RevObject> targets,
			Stream<RevObject> starters) throws IOException {
		try {
			walk.reset();
			walk.sort(RevSort.TOPO);
			for (RevObject target : targets) {
				walk.markStart(target);
			}

			Iterator<RevObject> iterator = starters.iterator();
			while (iterator.hasNext()) {
				RevObject o = iterator.next();
				walk.markUninteresting(o);

				RevObject peeled = walk.peel(o);
				if (peeled instanceof RevCommit) {
					// By default, for performance reasons, ObjectWalk does not
					// mark
					// a tree as uninteresting when we mark a commit. Mark it
					// ourselves so that we can determine reachability exactly.
					walk.markUninteresting(((RevCommit) peeled).getTree());
				}
			}

			RevCommit commit = walk.next();
			if (commit != null) {
				return Optional.of(commit);
			}

			RevObject object = walk.nextObject();
			if (object != null) {
				return Optional.of(object);
			}

			return Optional.empty();
		} catch (MissingObjectException | InvalidObjectException e) {
			throw new IllegalStateException(e);
		}
	}
}
