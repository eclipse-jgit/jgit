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
 * @since 5.8
 */
public interface ObjectReachabilityChecker {

	/**
	 * Checks that all targets are reachable from the starters.
	 *
	 * @param targets
	 *            objects we want to know if they are reachable from the
	 *            starters
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
	Optional<RevObject> areAllReachable(Collection<RevObject> targets,
			Stream<RevObject> starters) throws MissingObjectException,
			IncorrectObjectTypeException, IOException;

}