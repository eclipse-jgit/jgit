/*
 * Copyright (C) 2020, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.revwalk;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.eclipse.jgit.revwalk.BitmapWalker;
import org.eclipse.jgit.revwalk.ObjectReachabilityChecker;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevObject;

/**
 * Checks if all objects are reachable from certain starting points using
 * bitmaps.
 */
public class BitmappedObjectReachabilityChecker
		implements ObjectReachabilityChecker {

	private final ObjectWalk walk;

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
	 * {@inheritDoc}
	 *
	 * This implementation tries to shortcut the check adding starters
	 * incrementally. Ordering the starters by relevance can improve performance
	 * in the average case.
	 */
	@Override
	public Optional<RevObject> areAllReachable(Collection<RevObject> targets,
			Stream<RevObject> starters) throws IOException {
		try {
			BitmapIndex bitmapIndex = walk.getObjectReader().getBitmapIndex();
			BitmapBuilder targetBitmap = bitmapIndex.newBitmapBuilder();
			for (RevObject obj : targets) {
				targetBitmap.addObject(obj, obj.getType());
			}

			BitmapWalker bitmapWalker = new BitmapWalker(walk,
					bitmapIndex, null);

			Iterator<RevObject> starterIt = starters.iterator();
			BitmapBuilder seen = null;
			while (starterIt.hasNext()) {
				List<RevObject> asList = Arrays.asList(starterIt.next());
				BitmapBuilder visited = bitmapWalker.findObjects(asList, seen,
						true);
				seen = seen == null ? visited : seen.or(visited);

				targetBitmap = targetBitmap.andNot(visited);

				if (targetBitmap.cardinality() == 0) {
					return Optional.empty();
				}
			}

			return Optional.of(walk.parseAny(targetBitmap.iterator().next().getObjectId()));
		} catch (MissingObjectException | IncorrectObjectTypeException e) {
			throw new IllegalStateException(e);
		}
	}
}
