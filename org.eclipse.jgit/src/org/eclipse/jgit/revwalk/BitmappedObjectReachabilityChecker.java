/*
 * Copyright (C) 2019, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revwalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;

/**
 * Checks if all objects are reachable from certain starting points using
 * bitmaps.
 */
public class BitmappedObjectReachabilityChecker
		implements ObjectReachabilityChecker {

	private ObjectWalk walk;


	/**
	 * New instance of the reachability checker using a existing walk.
	 *
	 * @param walk
	 *            ObjectWalk instance to reuse. Caller retains ownership.
	 */
	public BitmappedObjectReachabilityChecker(ObjectWalk walk) {
		this.walk = walk;
	}

	/**
	 * Checks all targets are reachable from the starters. This implementation
	 * tries to shortcut the check adding starters incrementally. Ordering the
	 * starters by relevance can improve performance in the average case.
	 *
	 * @param targets
	 *            objects to reach
	 * @param starters
	 *            starting objects, known to the caller
	 * @return Optional with an unreachable object (if any). Empty if all
	 *         targets are reachable from the starters
	 * @throws MissingObjectException
	 *             Missing object. This should not happen, unless the caller got
	 *             the RevObject from a different RevWalk instance.
	 * @throws IncorrectObjectTypeException
	 *             Incorrect object type. This should not happen, unless the
	 *             caller got the RevObject from a different RevWalk instance.
	 * @throws IOException
	 *             Cannot access the underlying storage.
	 */
	@Override
	public Optional<RevObject> areAllReachable(Collection<RevObject> targets,
			Stream<RevObject> starters) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {

		List<RevObject> remainingTargets = new ArrayList<>(targets);
		BitmapWalker bitmapWalker = new BitmapWalker(walk,
				walk.getObjectReader().getBitmapIndex(), null);

		Iterator<RevObject> starterIt = starters.iterator();
		BitmapBuilder seen = null;
		while (starterIt.hasNext()) {
			List<RevObject> asList = Arrays.asList(starterIt.next());
			BitmapBuilder visited = bitmapWalker.findObjects(asList, seen,
					true);
			seen = seen == null ? visited : seen.or(visited);

			remainingTargets.removeIf(seen::contains);
			if (remainingTargets.isEmpty()) {
				return Optional.empty();
			}
		}

		return Optional.of(remainingTargets.get(0));
	}
}
