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

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;

/**
 * Only produce commits which are below a specified depth.
 */
public class DepthGenerator extends Generator {
	private final FIFORevQueue pending;

	private final int outputType;
	private final int depth;
	private final DepthWalk.CompareMode compareMode;
	private final RevWalk walk;

	/**
	 * @param w
	 * @param s Parent generator
	 * @param depth Maximum depth of commits.
	 * @param compareMode Comparison mode
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws IOException
	 */
	public DepthGenerator(RevWalk w, Generator s, int depth, DepthWalk.CompareMode compareMode) throws MissingObjectException,
	IncorrectObjectTypeException, IOException {
		pending = new FIFORevQueue();
		outputType = s.outputType();
		walk = w;
		this.depth = depth;
		this.compareMode = compareMode;

		s.shareFreeList(pending);

		// Begin by sucking out all of the source's commits, and
		// adding them to the pending queue
		for (;;) {
			final RevCommit c = s.next();
			if (c == null)
				break;
			pending.add(c);
		}
	}

	@Override
	int outputType() {
		return outputType;
	}

	@Override
	void shareFreeList(final BlockRevQueue q) {
		q.shareFreeList(pending);
	}

	@Override
	RevCommit next() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		for (;;) {
			final RevCommit c = pending.next();
			if (c == null)
				return null;

			c.parseHeaders(walk);

			DepthWalk.Commit dc = (DepthWalk.Commit)c;
			int newDepth = dc.getDepth() + 1;

			for (final RevCommit p : c.parents) {
				// Carry this child's depth up to the parent if it is
				// less than the parent's current depth
				DepthWalk.Commit dp = (DepthWalk.Commit)p;

				if (dp.getDepth() > newDepth) {
					dp.setDepth(newDepth);

					// If the parent is not too deep, add it to the queue
					// so that we can produce it later
					if (newDepth <= depth)
						pending.add(p);
				}
			}

			// Determine whether or not we will produce this commit
			boolean produce = true;
			switch (compareMode) {
			case EQUAL:
				produce = (dc.getDepth() == depth);
				break;

			case LESS_THAN_EQUAL:
				produce = (dc.getDepth() <= depth);
				break;
			}

			if (produce)
				return c;
			else
				continue;
		}
	}
}
