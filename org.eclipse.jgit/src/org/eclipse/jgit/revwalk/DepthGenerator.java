/*
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

import static org.eclipse.jgit.revwalk.PendingGenerator.OVER_SCAN;
import static org.eclipse.jgit.revwalk.RevWalk.PARSED;
import static org.eclipse.jgit.revwalk.RevWalk.SEEN;
import static org.eclipse.jgit.revwalk.RevWalk.UNINTERESTING;

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;

/**
 * Only produce commits which are below a specified depth.
 *
 * @see DepthWalk
 */
class DepthGenerator extends Generator {
	private final FIFORevQueue pending;

	private final DateRevQueue uninteresting;

	private final int depth;

	private final RevWalk walk;

	/**
	 * Commits which used to be shallow in the client, but which are
	 * being extended as part of this fetch.  These commits should be
	 * returned to the caller as UNINTERESTING so that their blobs/trees
	 * can be marked appropriately in the pack writer.
	 */
	private final RevFlag UNSHALLOW;

	/**
	 * Commits which the normal framework has marked as UNINTERESTING,
	 * but which we now care about again.  This happens if a client is
	 * extending a shallow checkout to become deeper--the new commits at
	 * the bottom of the graph need to be sent, even though they are
	 * below other commits which the client already has.
	 */
	private final RevFlag REINTERESTING;

	/**
	 * @param w
	 * @param s Parent generator
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws IOException
	 */
	DepthGenerator(DepthWalk w, Generator s) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		pending = new FIFORevQueue();
		uninteresting = new DateRevQueue();
		walk = (RevWalk)w;

		this.depth = w.getDepth();
		this.UNSHALLOW = w.getUnshallowFlag();
		this.REINTERESTING = w.getReinterestingFlag();

		s.shareFreeList(pending);

		// Begin by sucking out all of the source's commits, and
		// adding them to the pending queue
		for (;;) {
			RevCommit c = s.next();
			if (c == null) {
				break;
			}
			if (((DepthWalk.Commit) c).getDepth() == 0) {
				pending.add(c);
			}
			if ((c.flags & RevWalk.UNINTERESTING) != 0) {
				uninteresting.add(c);
			}
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

	private void propagateUninteresting(int stoppingPoint)
			throws MissingObjectException, IncorrectObjectTypeException,
				   IOException {
		for (int overScan = OVER_SCAN; overScan > 0; overScan--) {
			DepthWalk.Commit c = (DepthWalk.Commit) uninteresting.next();
			if (c == null) {
				return;
			}
			if ((c.flags & UNINTERESTING) == 0) {
				throw new IllegalStateException("found interesting commit in uninteresting walk");
			}
			for (RevCommit p : c.parents) {
				DepthWalk.Commit dp = (DepthWalk.Commit) p;

				if ((p.flags & SEEN) != 0) {
					continue;
				}
				if ((p.flags & PARSED) == 0) {
					p.parseHeaders(walk);
				}
				p.flags |= SEEN;
				uninteresting.add(p);
			}
			walk.carryFlagsImpl(c);

			if (c.commitTime >= stoppingPoint) {
				overScan = OVER_SCAN + 1;
			}
		}
	}

	@Override
	RevCommit next() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		// Perform a breadth-first descent into the commit graph,
		// marking depths as we go.  This means that if a commit is
		// reachable by more than one route, we are guaranteed to
		// arrive by the shortest route first.
		for (;;) {
			final DepthWalk.Commit c = (DepthWalk.Commit) pending.next();
			if (c == null)
				return null;

			propagateUninteresting(c.commitTime);

			if ((c.flags & RevWalk.PARSED) == 0)
				c.parseHeaders(walk);

			int newDepth = c.depth + 1;

			for (final RevCommit p : c.parents) {
				DepthWalk.Commit dp = (DepthWalk.Commit) p;

				// If no depth has been assigned to this commit, assign
				// it now.  Since we arrive by the shortest route first,
				// this depth is guaranteed to be the smallest value that
				// any path could produce.
				if (dp.depth == -1) {
					dp.depth = newDepth;

					// If the parent is not too deep, add it to the queue
					// so that we can produce it later
					if (newDepth <= depth)
						pending.add(p);
				}

				// If the current commit has become unshallowed, everything
				// below us is new to the client.  Mark its parent as
				// re-interesting, and carry that flag downward to all
				// of its ancestors.
				if(c.has(UNSHALLOW) || c.has(REINTERESTING)) {
					p.add(REINTERESTING);
					p.flags &= ~RevWalk.UNINTERESTING;
				}
			}

			// Produce all commits less than the depth cutoff
			boolean produce = c.depth <= depth;

			// Unshallow commits are uninteresting, but still need to be sent
			// up to the PackWriter so that it will exclude objects correctly.
			// All other uninteresting commits should be omitted.
			if ((c.flags & RevWalk.UNINTERESTING) != 0 && !c.has(UNSHALLOW))
				produce = false;

			if (produce)
				return c;
		}
	}
}
