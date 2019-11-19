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
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;

/**
 * Check if a commit is reachable from a collection of starting commits.
 * <p>
 * Note that this checks the reachability of commits (and tags). Trees, blobs or
 * any other object will cause IncorrectObjectTypeException exceptions.
 *
 * @since 5.4
 */
public interface ReachabilityChecker {

	/**
	 * Check if all targets are reachable from the {@code starter} commits.
	 * <p>
	 * Caller should parse the objectIds (preferably with
	 * {@code walk.parseCommit()} and handle missing/incorrect type objects
	 * before calling this method.
	 *
	 * @param targets
	 *            commits to reach.
	 * @param starters
	 *            known starting points.
	 * @return An unreachable target if at least one of the targets is
	 *         unreachable. An empty optional if all targets are reachable from
	 *         the starters.
	 *
	 * @throws MissingObjectException
	 *             if any of the incoming objects doesn't exist in the
	 *             repository.
	 * @throws IncorrectObjectTypeException
	 *             if any of the incoming objects is not a commit or a tag.
	 * @throws IOException
	 *             if any of the underlying indexes or readers can not be
	 *             opened.
	 *
	 * @deprecated see {{@link #areAllReachable(Collection, Stream)}
	 */
	@Deprecated
	default Optional<RevCommit> areAllReachable(Collection<RevCommit> targets,
                       Collection<RevCommit> starters) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		return areAllReachable(targets, starters.stream());
	}

	/**
	 * Check if all targets are reachable from the {@code starter} commits.
	 * <p>
	 * Caller should parse the objectIds (preferably with
	 * {@code walk.parseCommit()} and handle missing/incorrect type objects
	 * before calling this method.
	 *
	 * @param targets
	 *            commits to reach.
	 * @param starters
	 *            known starting points.
	 * @return An unreachable target if at least one of the targets is
	 *         unreachable. An empty optional if all targets are reachable from
	 *         the starters.
	 *
	 * @throws MissingObjectException
	 *             if any of the incoming objects doesn't exist in the
	 *             repository.
	 * @throws IncorrectObjectTypeException
	 *             if any of the incoming objects is not a commit or a tag.
	 * @throws IOException
	 *             if any of the underlying indexes or readers can not be
	 *             opened.
	 * @since 5.6
	 */
	Optional<RevCommit> areAllReachable(Collection<RevCommit> targets,
			Stream<RevCommit> starters)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException;
}
