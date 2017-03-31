/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2012, Research In Motion Limited
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

package org.eclipse.jgit.merge;

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;

/** A merge of 2 trees, using a common base ancestor tree. */
public abstract class ThreeWayMerger extends Merger {
	private RevTree baseTree;

	private ObjectId baseCommitId;

	/**
	 * Create a new merge instance for a repository.
	 *
	 * @param local
	 *            the repository this merger will read and write data on.
	 */
	protected ThreeWayMerger(final Repository local) {
		super(local);
	}

	/**
	 * Create a new merge instance for a repository.
	 *
	 * @param local
	 *            the repository this merger will read and write data on.
	 * @param inCore
	 *            perform the merge in core with no working folder involved
	 */
	protected ThreeWayMerger(final Repository local, boolean inCore) {
		this(local);
	}

	/**
	 * Create a new in-core merge instance from an inserter.
	 *
	 * @param inserter
	 *            the inserter to write objects to.
	 * @since 4.8
	 */
	protected ThreeWayMerger(ObjectInserter inserter) {
		super(inserter);
	}

	/**
	 * Set the common ancestor tree.
	 *
	 * @param id
	 *            common base treeish; null to automatically compute the common
	 *            base from the input commits during
	 *            {@link #merge(AnyObjectId...)}.
	 * @throws IncorrectObjectTypeException
	 *             the object is not a treeish.
	 * @throws MissingObjectException
	 *             the object does not exist.
	 * @throws IOException
	 *             the object could not be read.
	 */
	public void setBase(final AnyObjectId id) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		if (id != null) {
			baseTree = walk.parseTree(id);
		} else {
			baseTree = null;
		}
	}

	@Override
	public boolean merge(final AnyObjectId... tips) throws IOException {
		if (tips.length != 2)
			return false;
		return super.merge(tips);
	}

	@Override
	public ObjectId getBaseCommitId() {
		return baseCommitId;
	}

	/**
	 * Create an iterator to walk the merge base.
	 *
	 * @return an iterator over the caller-specified merge base, or the natural
	 *         merge base of the two input commits.
	 * @throws IOException
	 */
	protected AbstractTreeIterator mergeBase() throws IOException {
		if (baseTree != null)
			return openTree(baseTree);
		RevCommit baseCommit = (baseCommitId != null) ? walk
				.parseCommit(baseCommitId) : getBaseCommit(sourceCommits[0],
				sourceCommits[1]);
		if (baseCommit == null) {
			baseCommitId = null;
			return new EmptyTreeIterator();
		} else {
			baseCommitId = baseCommit.toObjectId();
			return openTree(baseCommit.getTree());
		}
	}
}
