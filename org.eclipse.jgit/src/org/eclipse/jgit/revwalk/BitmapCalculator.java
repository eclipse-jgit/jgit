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

import static java.util.Objects.requireNonNull;

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.revwalk.AddToBitmapFilter;
import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.BitmapIndex.Bitmap;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.eclipse.jgit.lib.ProgressMonitor;

/**
 * Calculate the bitmap indicating what other commits are reachable from certain
 * commit.
 * <p>
 * This bitmap refers only to commits. For a bitmap with ALL objects reachable
 * from certain object, see {@code BitmapWalker}.
 */
class BitmapCalculator {

	private final RevWalk walk;
	private final BitmapIndex bitmapIndex;

	BitmapCalculator(RevWalk walk) throws IOException {
		this.walk = walk;
		this.bitmapIndex = requireNonNull(
				walk.getObjectReader().getBitmapIndex());
	}

	/**
	 * Get the reachability bitmap from certain commit to other commits.
	 * <p>
	 * This will return a precalculated bitmap if available or walk building one
	 * until finding a precalculated bitmap (and returning the union).
	 * <p>
	 * Beware that the returned bitmap it is guaranteed to include ONLY the
	 * commits reachable from the initial commit. It COULD include other objects
	 * (because precalculated bitmaps have them) but caller shouldn't count on
	 * that. See {@link BitmapWalker} for a full reachability bitmap.
	 *
	 * @param start
	 *            the commit. Use {@code walk.parseCommit(objectId)} to get this
	 *            object from the id.
	 * @param pm
	 *            progress monitor. Updated by one per commit browsed in the
	 *            graph
	 * @return the bitmap of reachable commits (and maybe some extra objects)
	 *         for the commit
	 * @throws MissingObjectException
	 *             the supplied id doesn't exist
	 * @throws IncorrectObjectTypeException
	 *             the supplied id doesn't refer to a commit or a tag
	 * @throws IOException
	 *             if the walk cannot open a packfile or loose object
	 */
	BitmapBuilder getBitmap(RevCommit start, ProgressMonitor pm)
			throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		Bitmap precalculatedBitmap = bitmapIndex.getBitmap(start);
		if (precalculatedBitmap != null) {
			return asBitmapBuilder(precalculatedBitmap);
		}

		walk.reset();
		walk.sort(RevSort.TOPO);
		walk.markStart(start);
		// Unbounded walk. If the repo has bitmaps, it should bump into one at
		// some point.

		BitmapBuilder bitmapResult = bitmapIndex.newBitmapBuilder();
		walk.setRevFilter(new AddToBitmapFilter(bitmapResult));
		while (walk.next() != null) {
			// Iterate through all of the commits. The BitmapRevFilter does
			// the work.
			//
			// filter.include returns true for commits that do not have
			// a bitmap in bitmapIndex and are not reachable from a
			// bitmap in bitmapIndex encountered earlier in the walk.
			// Thus the number of commits returned by next() measures how
			// much history was traversed without being able to make use
			// of bitmaps.
			pm.update(1);
		}

		return bitmapResult;
	}

	private BitmapBuilder asBitmapBuilder(Bitmap bitmap) {
		return bitmapIndex.newBitmapBuilder().or(bitmap);
	}
}
