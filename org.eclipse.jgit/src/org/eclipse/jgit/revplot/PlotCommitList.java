/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>,
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
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

import java.text.MessageFormat;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;

import org.eclipse.jgit.internal.JGitText;
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

	private int positionsAllocated;

	private final TreeSet<Integer> freePositions = new TreeSet<Integer>();

	private final HashSet<PlotLane> activeLanes = new HashSet<PlotLane>(32);

	/** number of (child) commits on a lane */
	private final HashMap<PlotLane, Integer> laneLength = new HashMap<PlotLane, Integer>(
			32);

	@Override
	public void clear() {
		super.clear();
		positionsAllocated = 0;
		freePositions.clear();
		activeLanes.clear();
		laneLength.clear();
	}

	@Override
	public void source(final RevWalk w) {
		if (!(w instanceof PlotWalk))
			throw new ClassCastException(MessageFormat.format(JGitText.get().classCastNotA, PlotWalk.class.getName()));
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
		if (nChildren == 0) {
			currCommit.lane = nextFreeLane();
			return;
		}

		if (nChildren == 1 && currCommit.children[0].getParentCount() < 2) {
			// Only one child, child has only us as their parent.
			// Stay in the same lane as the child.

			@SuppressWarnings("unchecked")
			final PlotCommit<L> c = currCommit.children[0];
			if (c.lane == null) {
				// Hmmph. This child must be the first along this lane.
				//
				c.lane = nextFreeLane();
			}

			currCommit.lane = c.lane;
			Integer len = laneLength.get(currCommit.lane);
			len = Integer.valueOf(len.intValue() + 1);
			laneLength.put(currCommit.lane, len);
			handleBlockedLanes(index, currCommit, c);
		} else {
			// More than one child, or our child is a merge.

			// Assign each child a new lane in the (uncommon) case, that it was
			// not previously assigned a lane.

			// At the same time we look for the child lane the current commit
			// should continue. Condidate lanes for this are those with
			// children, that have the current commit as their first parent.
			// There can be multiple canidate lanes. In that case the longest
			// lane is chosen, as this is usually the lane representing the
			// branch the commit actually was made on.

			// When there are no canidate lanes (i.e. the current commit has
			// only children whose non-first parent it is) we place the current
			// commit on a new lane.

			// The lane the current commit will be placed on:
			PlotLane reservedLane = null;
			PlotCommit childOnReservedLane = null;
			int lengthOfReservedLane = -1;

			for (int i = 0; i < nChildren; i++) {
				@SuppressWarnings("unchecked")
				final PlotCommit<L> c = currCommit.children[i];
				// don't forget to position all of your children if they are
				// not already positioned.
				if (c.lane == null)
					c.lane = nextFreeLane();

				if (c.getParent(0) == currCommit) {
					Integer len = laneLength.get(c.lane);
					// we may be the first parent for multiple lines of
					// development, try to continue the longest one
					if (len.intValue() > lengthOfReservedLane) {
						reservedLane = c.lane;
						childOnReservedLane = c;
						lengthOfReservedLane = len.intValue();
					}
				}
			}

			if (reservedLane != null) {
				currCommit.lane = reservedLane;
				laneLength.put(reservedLane,
						Integer.valueOf(lengthOfReservedLane + 1));
				handleBlockedLanes(index, currCommit, childOnReservedLane);
			} else {
				currCommit.lane = nextFreeLane();
				handleBlockedLanes(index, currCommit, null);
			}

			// close lanes of children, if there are no first parents that might
			// want to continue the child lanes
			for (int i = 0; i < nChildren; i++) {
				final PlotCommit c = currCommit.children[i];
				PlotCommit firstParent = (PlotCommit) c.getParent(0);
				if (firstParent.lane != null && firstParent.lane != c.lane)
					closeLane(c.lane);
			}
		}
	}

	/**
	 * Connects the commit to all its children. "Draws" the lanes and
	 * repositions lanes (of the commit or a child) if the lane between commit
	 * and the respective child is blocked.
	 *
	 * @param index
	 *            the index of <code>currCommit</code> in the list
	 * @param currCommit
	 * @param childOnLane
	 *            the direct child on the same lane as <code>currCommit</code>,
	 *            may be null
	 */
	private void handleBlockedLanes(final int index, final PlotCommit currCommit,
			final PlotCommit childOnLane) {

		for (PlotCommit child : currCommit.children) {
			if (child == childOnLane)
				continue; // this is handled after the for-loop

			// Is the child a merge or is it forking off?
			boolean childIsMerge = child.getParent(0) != currCommit;
			PlotLane laneToUse = childIsMerge ? currCommit.lane : child.lane;
			if (childIsMerge) {
				laneToUse = handleMerge(index, currCommit, childOnLane, child,
						laneToUse);
				child.addMergingLane(laneToUse);
			} else {
				// We want to draw a forking arc in the child's lane.

				// This lane cannot be blocked, because the child lane is
				// still active. This keeps other unrelated lanes and
				// non-first parents of this child from using this lane.

				currCommit.addForkingOffLane(laneToUse);
			}

			// Actually connect currCommit to the child
			drawLaneToChild(index, child, laneToUse);
		}

		// Connect currCommit to childOnLane
		if (childOnLane != null)
			drawLaneToChild(index, childOnLane, childOnLane.lane);
	}

	// Handles the case where currCommit is a non-first parent of the child
	private PlotLane handleMerge(final int index, final PlotCommit currCommit,
			final PlotCommit childOnLane, PlotCommit child, PlotLane laneToUse) {

		// find all blocked positions between currCommit and this child

		int childIndex = index; // useless initialization, should
								// always be set in the loop below
		BitSet blockedPositions = new BitSet();
		for (int r = index - 1; r >= 0; r--) {
			final PlotCommit rObj = get(r);
			if (rObj == child) {
				childIndex = r;
				break;
			}
			addBlockedPosition(blockedPositions, rObj);
		}

		// handle blockades

		if (blockedPositions.get(laneToUse.getPosition())) {
			// We want to draw a merging arc in our lane to the child,
			// which is on another lane, but our lane is blocked.

			// Check if childOnLane is beetween commit and the child we
			// are currently processing
			boolean needDetour = false;
			if (childOnLane != null) {
				for (int r = index - 1; r > childIndex; r--) {
					final PlotCommit rObj = get(r);
					if (rObj == childOnLane) {
						needDetour = true;
						break;
					}
				}
			}

			if (needDetour) {
				// It is childOnLane which is blocking us. Repositioning
				// our lane would not help, because this repositions the
				// child too, keeping the blockade.
				// Instead, we create a "detour lane" which gets us
				// around the blockade. That lane has no commits on it.
				laneToUse = nextFreeLane(blockedPositions);
				currCommit.addForkingOffLane(laneToUse);
				closeLane(laneToUse);
			} else {
				// The blockade is (only) due to other (already closed)
				// lanes at the current lane's position. In this case we
				// reposition the current lane.
				// Ws are the first commit on this lane, because
				// otherwise the child commit on this lane would have
				// kept other lanes from blocking us. Since we are the
				// first commit, we can freely reposition.
				int newPos = getFreePosition(blockedPositions);
				freePositions.add(Integer.valueOf(laneToUse
						.getPosition()));
				laneToUse.position = newPos;
			}
		}
		return laneToUse;
	}

	/**
	 * Connects the commit at commitIndex to the child, using the given lane.
	 * All blockades on the lane must be resolved before calling this method.
	 *
	 * @param commitIndex
	 * @param child
	 * @param laneToContinue
	 */
	private void drawLaneToChild(final int commitIndex, PlotCommit child,
			PlotLane laneToContinue) {
		for (int r = commitIndex - 1; r >= 0; r--) {
			final PlotCommit rObj = get(r);
			if (rObj == child)
				break;
			if (rObj != null)
				rObj.addPassingLane(laneToContinue);
		}
	}

	private static void addBlockedPosition(BitSet blockedPositions,
			final PlotCommit rObj) {
		if (rObj != null) {
			PlotLane lane = rObj.getLane();
			// positions may be blocked by a commit on a lane
			if (lane != null)
				blockedPositions.set(lane.getPosition());
			// positions may also be blocked by passing, forking off
			// and merging lanes
			for (PlotLane l : rObj.passingLanes)
				blockedPositions.set(l.getPosition());
			for (PlotLane l : rObj.forkingOffLanes)
				blockedPositions.set(l.getPosition());
			for (PlotLane l : rObj.mergingLanes)
				blockedPositions.set(l.getPosition());
		}
	}

	private void closeLane(PlotLane lane) {
		if (activeLanes.remove(lane)) {
			recycleLane((L) lane);
			laneLength.remove(lane);
			freePositions.add(Integer.valueOf(lane.getPosition()));
		}
	}

	private void setupChildren(final PlotCommit<L> currCommit) {
		final int nParents = currCommit.getParentCount();
		for (int i = 0; i < nParents; i++)
			((PlotCommit) currCommit.getParent(i)).addChild(currCommit);
	}

	private PlotLane nextFreeLane() {
		return nextFreeLane(null);
	}

	private PlotLane nextFreeLane(BitSet blockedPositions) {
		final PlotLane p = createLane();
		p.position = getFreePosition(blockedPositions);
		activeLanes.add(p);
		laneLength.put(p, Integer.valueOf(1));
		return p;
	}

	/**
	 * @param blockedPositions
	 *            may be null
	 * @return a free lane position
	 */
	private int getFreePosition(BitSet blockedPositions) {
		if (freePositions.isEmpty())
			return positionsAllocated++;

		if (blockedPositions != null) {
			for (Integer pos : freePositions)
				if (!blockedPositions.get(pos.intValue())) {
					freePositions.remove(pos);
					return pos.intValue();
				}
			return positionsAllocated++;
		} else {
			final Integer min = freePositions.first();
			freePositions.remove(min);
			return min.intValue();
		}
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
