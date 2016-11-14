/*
 * Copyright (C) 2008, 2014 Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.revplot;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * A commit reference to a commit in the DAG.
 *
 * @param <L>
 *            type of lane being used by the plotter.
 * @see PlotCommitList
 */
public class PlotCommit<L extends PlotLane> extends RevCommit {
	static final PlotCommit[] NO_CHILDREN = {};

	static final PlotLane[] NO_LANES = {};

	static final Ref[] NO_REFS = {};

	PlotLane[] forkingOffLanes;

	PlotLane[] passingLanes;

	PlotLane[] mergingLanes;

	PlotLane lane;

	PlotCommit[] children;

	Ref[] refs;

	/**
	 * Create a new commit.
	 *
	 * @param id
	 *            the identity of this commit.
	 */
	protected PlotCommit(final AnyObjectId id) {
		super(id);
		forkingOffLanes = NO_LANES;
		passingLanes = NO_LANES;
		mergingLanes = NO_LANES;
		children = NO_CHILDREN;
		refs = NO_REFS;
	}

	void addForkingOffLane(final PlotLane f) {
		forkingOffLanes = addLane(f, forkingOffLanes);
	}

	void addPassingLane(final PlotLane c) {
		passingLanes = addLane(c, passingLanes);
	}

	void addMergingLane(final PlotLane m) {
		mergingLanes = addLane(m, mergingLanes);
	}

	private static PlotLane[] addLane(final PlotLane l, PlotLane[] lanes) {
		final int cnt = lanes.length;
		if (cnt == 0)
			lanes = new PlotLane[] { l };
		else if (cnt == 1)
			lanes = new PlotLane[] { lanes[0], l };
		else {
			final PlotLane[] n = new PlotLane[cnt + 1];
			System.arraycopy(lanes, 0, n, 0, cnt);
			n[cnt] = l;
			lanes = n;
		}
		return lanes;
	}

	void addChild(final PlotCommit c) {
		final int cnt = children.length;
		if (cnt == 0)
			children = new PlotCommit[] { c };
		else if (cnt == 1) {
			if (!c.getId().equals(children[0].getId()))
				children = new PlotCommit[] { children[0], c };
		} else {
			for (PlotCommit pc : children)
				if (c.getId().equals(pc.getId()))
					return;
			final PlotCommit[] n = new PlotCommit[cnt + 1];
			System.arraycopy(children, 0, n, 0, cnt);
			n[cnt] = c;
			children = n;
		}
	}

	/**
	 * Get the number of child commits listed in this commit.
	 *
	 * @return number of children; always a positive value but can be 0.
	 */
	public final int getChildCount() {
		return children.length;
	}

	/**
	 * Get the nth child from this commit's child list.
	 *
	 * @param nth
	 *            child index to obtain. Must be in the range 0 through
	 *            {@link #getChildCount()}-1.
	 * @return the specified child.
	 * @throws ArrayIndexOutOfBoundsException
	 *             an invalid child index was specified.
	 */
	public final PlotCommit getChild(final int nth) {
		return children[nth];
	}

	/**
	 * Determine if the given commit is a child (descendant) of this commit.
	 *
	 * @param c
	 *            the commit to test.
	 * @return true if the given commit built on top of this commit.
	 */
	public final boolean isChild(final PlotCommit c) {
		for (final PlotCommit a : children)
			if (a == c)
				return true;
		return false;
	}

	/**
	 * Get the number of refs for this commit.
	 *
	 * @return number of refs; always a positive value but can be 0.
	 */
	public final int getRefCount() {
		return refs.length;
	}

	/**
	 * Get the nth Ref from this commit's ref list.
	 *
	 * @param nth
	 *            ref index to obtain. Must be in the range 0 through
	 *            {@link #getRefCount()}-1.
	 * @return the specified ref.
	 * @throws ArrayIndexOutOfBoundsException
	 *             an invalid ref index was specified.
	 */
	public final Ref getRef(final int nth) {
		return refs[nth];
	}

	/**
	 * Obtain the lane this commit has been plotted into.
	 *
	 * @return the assigned lane for this commit.
	 */
	@SuppressWarnings("unchecked")
	public final L getLane() {
		return (L) lane;
	}

	@Override
	public void reset() {
		forkingOffLanes = NO_LANES;
		passingLanes = NO_LANES;
		mergingLanes = NO_LANES;
		children = NO_CHILDREN;
		lane = null;
		super.reset();
	}
}
