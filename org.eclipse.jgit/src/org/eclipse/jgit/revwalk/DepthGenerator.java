/*
 * Copyright (C) 2011, Google Inc.
 * Copyright (C) 2010, Garmin International
 * Copyright (C) 2010, Matt Fischer <matt.fischer@garmin.com>
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

/**
 * Only produce commits which are below a specified depth. Also find boundary
 * points where ancestry becomes uninteresting.
 */
class DepthGenerator extends Generator {
	final static int UNINTERESTING = RevWalk.UNINTERESTING;

	private final FIFORevQueue pending;
	private final int depth;
	// Should be a DepthRevWalk or a DepthObjectWalk
	private final RevWalk walk;
	/**
	 * A flag for commits whose parents are too deep to include
	 */
	final RevFlag SHALLOW;
	/**
	 * A flag for uninteresting commits that are direct parents of
	 * interesting commits
	 */
	final RevFlag BOUNDARY;

	/**
	 * @param w
	 *		The walk that's using this generator
	 * @param d
			The maximum commit depth to generate
	 * @param shallow
			The flag that denotes maximum depth
	 * @param boundary
			The flag that denotes the edge of an uninteresting area
	 * @param s
	 *		A queue of commits to begin with whose depth will be 0
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws IOException
	 */
	DepthGenerator(RevWalk w, int d, final RevFlag shallow, final RevFlag boundary,
			final DateRevQueue s) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		pending = new FIFORevQueue();
		walk = w;
		depth = d;
		SHALLOW = shallow;
		BOUNDARY = boundary;

		s.shareFreeList(pending);

		// Grab all source's commits and make them the 0th layer
		for (;;) {
			final DepthCommit c = (DepthCommit)s.next();
			if (c == null)
				break;
			c.depth = 0;
			pending.add(c);
		}
	}

	@Override
	int outputType() {
		return pending.outputType() | HAS_UNINTERESTING;
	}

	@Override
	void shareFreeList(final BlockRevQueue q) {
		pending.shareFreeList(q);
	}

	@Override
	RevCommit next() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		// Perform a breadth-first descent into the commit graph,
		// marking depths as we go.  This means that if a commit is
		// reachable by more than one route, we are guaranteed to
		// arrive by the shortest route first.
		for (;;) {
			final DepthCommit c = (DepthCommit)pending.next();
			if (c == null)
				return null;

			if ((c.flags & RevWalk.PARSED) == 0)
				c.parseHeaders(walk);

			// These need to be marked so PackWriter doesn't
			// think they're useful parents
			if (c.depth > depth) {
				c.flags |= UNINTERESTING;
				continue;
			}
			if (c.depth == depth)
				c.add(SHALLOW);

			int newDepth = c.depth + 1;

			for (final RevCommit p : c.parents) {
				DepthCommit dp = (DepthCommit)p;
				boolean add = false;

				// If no depth has been assigned to this
				// commit, assign it now.  Since we arrive
				// by the shortest route first, this depth
				// is guaranteed to be the smallest value
				// that any path could produce.
				if (dp.depth == -1) {
					dp.depth = newDepth;
					add = true;
				}

				// Detect boundaries and flag appropriate. Queue
				// them for output, in case a shorter path where
				// they aren't boundaries reached them already.
				if ((c.flags & UNINTERESTING) == 0
						&& ((p.flags & UNINTERESTING) != 0)
						&& (!p.has(BOUNDARY))) {
					p.add(BOUNDARY);
					add = true;
				}

				if (add)
					pending.add(p);
			}

			return c;
		}
	}
}
