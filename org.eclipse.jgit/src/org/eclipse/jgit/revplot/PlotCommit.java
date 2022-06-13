/*
 * Copyright (C) 2008, 2014 Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
	protected PlotCommit(AnyObjectId id) {
		super(id);
		forkingOffLanes = NO_LANES;
		passingLanes = NO_LANES;
		mergingLanes = NO_LANES;
		children = NO_CHILDREN;
		refs = NO_REFS;
	}

	void addForkingOffLane(PlotLane f) {
		forkingOffLanes = addLane(f, forkingOffLanes);
	}

	void addPassingLane(PlotLane c) {
		passingLanes = addLane(c, passingLanes);
	}

	void addMergingLane(PlotLane m) {
		mergingLanes = addLane(m, mergingLanes);
	}

	private static PlotLane[] addLane(PlotLane l, PlotLane[] lanes) {
		final int cnt = lanes.length;
		switch (cnt) {
		case 0:
			lanes = new PlotLane[] { l };
			break;
		case 1:
			lanes = new PlotLane[] { lanes[0], l };
			break;
		default:
			final PlotLane[] n = new PlotLane[cnt + 1];
			System.arraycopy(lanes, 0, n, 0, cnt);
			n[cnt] = l;
			lanes = n;
			break;
		}
		return lanes;
	}

	void addChild(PlotCommit c) {
		final int cnt = children.length;
		switch (cnt) {
		case 0:
			children = new PlotCommit[] { c };
			break;
		case 1:
			if (!c.getId().equals(children[0].getId()))
				children = new PlotCommit[] { children[0], c };
			break;
		default:
			for (PlotCommit pc : children)
				if (c.getId().equals(pc.getId()))
					return;
			final PlotCommit[] n = new PlotCommit[cnt + 1];
			System.arraycopy(children, 0, n, 0, cnt);
			n[cnt] = c;
			children = n;
			break;
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
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *             an invalid child index was specified.
	 */
	public final PlotCommit getChild(int nth) {
		return children[nth];
	}

	/**
	 * Determine if the given commit is a child (descendant) of this commit.
	 *
	 * @param c
	 *            the commit to test.
	 * @return true if the given commit built on top of this commit.
	 */
	@SuppressWarnings("ReferenceEquality")
	public final boolean isChild(PlotCommit c) {
		for (PlotCommit a : children)
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
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *             an invalid ref index was specified.
	 */
	public final Ref getRef(int nth) {
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

	/** {@inheritDoc} */
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
