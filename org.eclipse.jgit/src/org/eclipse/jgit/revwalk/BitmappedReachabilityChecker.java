/*
 * Copyright (C) 2019, Google LLC
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.eclipse.jgit.lib.NullProgressMonitor;

/**
 * Checks the reachability using bitmaps.
 *
 * @since 5.4
 */
public class BitmappedReachabilityChecker implements ReachabilityChecker {

	private final RevWalk walk;

	/**
	 * @param walk
	 *            walk on the repository to get or create the bitmaps for the
	 *            commits. It must have bitmaps.
	 * @throws AssertionError
	 *             runtime exception if walk is over a repository without
	 *             bitmaps
	 * @throws IOException
	 *             if the index or the object reader cannot be opened.
	 */
	public BitmappedReachabilityChecker(RevWalk walk)
			throws IOException {
		this.walk = walk;
		if (walk.getObjectReader().getBitmapIndex() == null) {
			throw new AssertionError(
					"Trying to use bitmapped reachability check " //$NON-NLS-1$
							+ "on a repository without bitmaps"); //$NON-NLS-1$
		}
	}

	/**
	 * Check all targets are reachable from the starters.
	 * <p>
	 * In this implementation, it is recommended to put the most popular
	 * starters (e.g. refs/heads tips) at the beginning of the collection
	 */
	@Override
	public Optional<RevCommit> areAllReachable(Collection<RevCommit> targets,
			Collection<RevCommit> starters) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		BitmapCalculator calculator = new BitmapCalculator(walk);

		/**
		 * Iterate over starters bitmaps and remove targets as they become
		 * reachable.
		 *
		 * Building the total starters bitmap has the same cost (iterating over
		 * all starters adding the bitmaps) and this gives us the chance to
		 * shorcut the loop.
		 *
		 * This is based on the assuption that most of the starters will have
		 * the reachability bitmap precalculated. If many require a walk, the
		 * walk.reset() could start to take too much time.
		 */
		List<RevCommit> remainingTargets = new ArrayList<>(targets);
		for (RevCommit starter : starters) {
			BitmapBuilder starterBitmap = calculator.getBitmap(starter,
					NullProgressMonitor.INSTANCE);
			remainingTargets.removeIf(starterBitmap::contains);
			if (remainingTargets.isEmpty()) {
				return Optional.empty();
			}
		}

		return Optional.of(remainingTargets.get(0));
	}

}
