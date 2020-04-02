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
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;

/**
 * Checks if all objects are reachable from certain starting points doing a
 * walk.
 *
 * This is an expensive check that browses commits, trees, blobs and tags. For
 * reachability just between commits see {@link ReachabilityChecker}
 * implementations.
 *
 * TODO(ifrade): This class won't be public when the interface is introduced.
 * Skipping the @since.
 */
public class PedestrianObjectReachabilityChecker {
	private ObjectWalk walk;

	/**
	 * New instance of the reachability checker using a existing walk.
	 *
	 * @param walk
	 *            ObjectWalk instance to reuse. Caller retains ownership.
	 */
	public PedestrianObjectReachabilityChecker(ObjectWalk walk) {
		this.walk = walk;
	}

	/**
	 * Checks that all targets are reachable from the starters.
	 *
	 * @param targets
	 *            objects we want to reach from the starters
	 * @param starters
	 *            objects known to be reachable to the caller
	 * @return Optional with an unreachable target if there is any (there could
	 *         be more than one). Empty optional means all targets are
	 *         reachable.
	 * @throws MissingObjectException
	 *             An object was missing. This should not happen as the caller
	 *             checked this while doing
	 *             {@link RevWalk#parseAny(AnyObjectId)} to convert ObjectIds to
	 *             RevObjects.
	 * @throws IncorrectObjectTypeException
	 *             Incorrect object type. As with missing objects, this should
	 *             not happen if the caller used
	 *             {@link RevWalk#parseAny(AnyObjectId)}.
	 * @throws IOException
	 *             Cannot access underlying storage
	 */
	public Optional<RevObject> areAllReachable(Collection<RevObject> targets,
			Stream<RevObject> starters) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
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
				// By default, for performance reasons, ObjectWalk does not mark
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
	}
}
