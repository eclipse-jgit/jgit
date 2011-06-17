/*
 * Copyright (C) 2011, Google Inc.
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

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import static org.eclipse.jgit.revwalk.RevWalk.UNINTERESTING;
/**
 * Specialized subclass of ObjectWalk to be aware of depth and shallowness.
 * This walk only goes to a given commit depth and understands that commits
 * beneath shallow ones may be interesting.
 */
public class DepthObjectWalk extends ObjectWalk {
	/** The number of commits to walk down */
	private int depth;

	/**
	 * A flag for uninteresting commits that are direct parents of
	 * interesting commits
	 */
	final RevFlag BOUNDARY;

	/** A flag for commits whose parents are too deep to include */
	final RevFlag SHALLOW;

	/**
	 * All commits which the have been reported as shallow, and
	 * whose ancestors are therefore unavailable
	 */
	private final Collection<? extends ObjectId> clientShallows;

	/**
	 * Create a new depth revision and object walker for a given repository.
	 *
	 * @param repo
	 *            the repository the walker will obtain data from. An
	 *            ObjectReader will be created by the walker, and must be
	 *            released by the caller.
	 * @param depth
	 *            how many commits to walk down
	 * @param shallow
	 *            the shallow flag to use; one will be created if it's null
	 * @param clientShallows
	 *            commits whose ancestors aren't available
	 */
	public DepthObjectWalk(final Repository repo, int depth,
			final Collection<? extends ObjectId> clientShallows) {
		super(repo);

		this.depth = depth;
		this.clientShallows = clientShallows;
		this.SHALLOW = newFlag("SHALLOW");
		BOUNDARY = newFlag("BOUNDARY");
	}

	/**
	 * Create a new depth revision and object walker for a given repository.
	 *
	 * @param or
	 *            the reader the walker will obtain data from. The reader
	 *            should be released by the caller when the walker is no
	 *            longer required.
	 * @param depth
	 *            how many commits to walk down
	 * @param clientShallows
	 *            commits whose ancestors aren't available
	 */
	public DepthObjectWalk(ObjectReader or, int depth,
			final Collection<? extends ObjectId> clientShallows) {
		super(or);

		this.depth = depth;
		this.clientShallows = clientShallows;
		SHALLOW = newFlag("SHALLOW");
		BOUNDARY = newFlag("BOUNDARY");
	}

	DepthObjectWalk(ObjectReader or, int depth, final RevFlag shallow,
			final Collection<? extends ObjectId> clientShallows) {
		super(or);

		this.depth = depth;
		SHALLOW = shallow == null ? newFlag("SHALLOW") : shallow;
		this.clientShallows = clientShallows;
		BOUNDARY = newFlag("BOUNDARY");
	}

	@Override
	protected RevCommit createCommit(final AnyObjectId id) {
		return new DepthCommit(id);
	}

	/** Get the commit history depth this walk goes to */
	public int getDepth() {
		return depth;
	}

	/**
	 * Mark an object to not produce in the output. The flag will apply to
	 * the entire ancestry but will stop at shallow commits.
	 *
	 * @param o
	 *            the object to start traversing from
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws IOException
	 */
	@Override
	public void markUninteresting(RevObject o) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		while (o instanceof RevTag) {
			o.flags |= UNINTERESTING;
			if (hasRevSort(RevSort.BOUNDARY))
				addObject(o);
			o = ((RevTag) o).getObject();
			parseHeaders(o);
		}

		if (o instanceof RevCommit)
			markUninteresting((RevCommit) o);
		else if (o instanceof RevTree)
			markTreeUninteresting((RevTree) o);
		else
			o.flags |= UNINTERESTING;

		if (o.getType() != Constants.OBJ_COMMIT
				&& hasRevSort(RevSort.BOUNDARY)) {
			addObject(o);
		}
	}

	/**
	 * Mark a commit to not produce in the output. The flag will apply to
	 * the entire ancestry but will stop at shallow commits.
	 *
	 * @param c
	 *            the commit to start traversing from
	 */
	@Override
	public void markUninteresting(final RevCommit c)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		c.flags |= UNINTERESTING;
		// Propagate uninterestingness until a shallow commit is found;
		// commits below a shallow one are interesting again
		if (clientShallows == null || !clientShallows.contains(c)) {
			if ((c.flags & PARSED) == 0)
				c.parseHeaders(this);
			for (RevCommit p : c.parents)
				markUninteresting(p);
		}
	}

	@Override
	public RevCommit next() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		for (;;) {
			final RevCommit r = super.next();
			if ((r != null)
					&& (r.flags & UNINTERESTING) != 0
					&& !r.has(BOUNDARY))
				continue;
			return r;
		}
	}

	// For unit testing
	void setDepth(int depth) {
		this.depth = depth;
	}

	@Override
	protected boolean shouldSkipObject(final RevObject o) {
		if (o.has(BOUNDARY))
			return !hasRevSort(RevSort.BOUNDARY);
		return (o.flags & UNINTERESTING) != 0;
	}
}
