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

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevWalkException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;

/**
 * Walks a commit graph down a certain number of commits and no further.
 */
public class DepthRevWalk extends RevWalk {
	/**
	 * The number of commits to walk down
	 */
        private int depth;
	/**
	 * A flag for commits whose parents are too deep to include
	 */
	final RevFlag SHALLOW;

	/**
	 * Create a new depth revision walker for a given repository.
	 *
	 * @param repo
	 *            the repository the walker will obtain data from. An
	 *            ObjectReader will be created by the walker, and must be
	 *            released by the caller.
	 * @param depth
	 *            how many commits to walk down
	 * @param shallow
	 *            the shallow flag to use; one will be created if it's null
	 */
	public DepthRevWalk(final Repository repo, int depth, final RevFlag shallow) {
		super(repo);

		this.depth = depth;
		SHALLOW = shallow == null ? newFlag("SHALLOW") : shallow;
	}

	/**
	 * Create a new depth revision walker for a given repository.
	 *
	 * @param or
	 *            the reader the walker will obtain data from. The reader should
	 *            be released by the caller when the walker is no longer
	 *            required.
	 * @param depth
	 *            how many commits to walk down
	 * @param shallow
	 *            the shallow flag to use; one will be created if it's null
	 */
	public DepthRevWalk(ObjectReader or, int depth, final RevFlag shallow) {
		super(or);

		this.depth = depth;
		SHALLOW = shallow == null ? newFlag("SHALLOW") : shallow;
	}

	@Override
	public void markUninteresting(final RevCommit c)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		// This walk is just going down a desired depth and skip nothing
	}

	@Override
	protected RevCommit createCommit(final AnyObjectId id) {
		return new DepthCommit(id);
	}

	/** Get the commit history depth this walk goes to */
	/**
	 * Set the commit history depth this walk goes to
	 * @param depth
	 *            how many commits deep the history should be
	 */
	public int getDepth() {
		return depth;
	}

	// For unit testing
	void setDepth(int depth) {
		this.depth = depth;
	}

	@Override
	public ObjectWalk toObjectWalkWithSameObjects() {
		DepthObjectWalk dow = new DepthObjectWalk(reader, depth, SHALLOW, null);
		RevWalk rw = dow;
		rw.objects = objects;
		rw.freeFlags = freeFlags;
		return dow;
	}
}
