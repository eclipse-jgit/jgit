/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

import java.util.Collection;
import java.util.HashSet;
import java.util.TreeSet;

import org.eclipse.jgit.revwalk.RevCommitList;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * An ordered list of {@link PlotCommit} subclasses.
 * <p>
 * Commits are allocated into lanes as they enter the list, based upon their
 * connections between descendant (child) commits and ancestor (parent) commits.
 * <p>
 * The source of the list must be a {@link PlotWalk} and {@link #fillTo(int)}
 * must be used to populate the list.
 *
 * @param <L>
 *            type of lane used by the application.
 */
public class PlotCommitList<L extends PlotLane> extends
		RevCommitList<PlotCommit<L>> {
	static final int MAX_LENGTH = 25;

	private int lanesAllocated;

	private final TreeSet<Integer> freeLanes = new TreeSet<Integer>();

	private final HashSet<PlotLane> activeLanes = new HashSet<PlotLane>(32);

	@Override
	public void clear() {
		super.clear();
		lanesAllocated = 0;
		freeLanes.clear();
		activeLanes.clear();
	}

	@Override
	public void source(final RevWalk w) {
		if (!(w instanceof PlotWalk))
			throw new ClassCastException("Not a " + PlotWalk.class.getName());
		super.source(w);
	}

	/**
	 * Find the set of lanes passing through a commit's row.
	 * <p>
	 * Lanes passing through a commit are lanes that the commit is not directly
	 * on, but that need to travel through this commit to connect a descendant
	 * (child) commit to an ancestor (parent) commit. Typically these lanes will
	 * be drawn as lines in the passed commit's box, and the passed commit won't
	 * appear to be connected to those lines.
	 * <p>
	 * This method modifies the passed collection by adding the lanes in any
	 * order.
	 *
	 * @param currCommit
	 *            the commit the caller needs to get the lanes from.
	 * @param result
	 *            collection to add the passing lanes into.
	 */
	public void findPassingThrough(final PlotCommit<L> currCommit,
			final Collection<L> result) {
		for (final PlotLane p : currCommit.passingLanes)
			result.add((L) p);
	}

	@Override
	protected void enter(final int index, final PlotCommit<L> currCommit) {
		setupChildren(currCommit);

		final int nChildren = currCommit.getChildCount();
		if (nChildren == 0)
			return;

		if (nChildren == 1 && currCommit.children[0].getParentCount() < 2) {
			// Only one child, child has only us as their parent.
			// Stay in the same lane as the child.
			//
			final PlotCommit c = currCommit.children[0];
			if (c.lane == null) {
				// Hmmph. This child must be the first along this lane.
				//
				c.lane = nextFreeLane();
				activeLanes.add(c.lane);
			}

			for (int r = index - 1; r >= 0; r--) {
				final PlotCommit rObj = get(r);
				if (rObj == c)
					break;
				rObj.addPassingLane(c.lane);
			}
			currCommit.lane = c.lane;
			currCommit.lane.parent = currCommit;
		} else {
			// More than one child, or our child is a merge.
			// Use a different lane.
			//

			for (int i = 0; i < nChildren; i++) {
				final PlotCommit c = currCommit.children[i];
				if (activeLanes.remove(c.lane)) {
					recycleLane((L) c.lane);
					freeLanes.add(Integer.valueOf(c.lane.getPosition()));
				}
			}

			currCommit.lane = nextFreeLane();
			currCommit.lane.parent = currCommit;
			activeLanes.add(currCommit.lane);

			int remaining = nChildren;
			for (int r = index - 1; r >= 0; r--) {
				final PlotCommit rObj = get(r);
				if (currCommit.isChild(rObj)) {
					if (--remaining == 0)
						break;
				}
				rObj.addPassingLane(currCommit.lane);
			}
		}
	}

	private void setupChildren(final PlotCommit<L> currCommit) {
		final int nParents = currCommit.getParentCount();
		for (int i = 0; i < nParents; i++)
			((PlotCommit) currCommit.getParent(i)).addChild(currCommit);
	}

	private PlotLane nextFreeLane() {
		final PlotLane p = createLane();
		if (freeLanes.isEmpty()) {
			p.position = lanesAllocated++;
		} else {
			final Integer min = freeLanes.first();
			p.position = min.intValue();
			freeLanes.remove(min);
		}
		return p;
	}

	/**
	 * @return a new Lane appropriate for this particular PlotList.
	 */
	protected L createLane() {
		return (L) new PlotLane();
	}

	/**
	 * Return colors and other reusable information to the plotter when a lane
	 * is no longer needed.
	 *
	 * @param lane
	 */
	protected void recycleLane(final L lane) {
		// Nothing.
	}
}
