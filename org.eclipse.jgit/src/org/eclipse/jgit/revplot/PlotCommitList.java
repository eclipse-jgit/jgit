/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>,
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2014, Konrad Kügler and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
 * An ordered list of {@link org.eclipse.jgit.revplot.PlotCommit} subclasses.
 * <p>
 * Commits are allocated into lanes as they enter the list, based upon their
 * connections between descendant (child) commits and ancestor (parent) commits.
 * <p>
 * The source of the list must be a {@link org.eclipse.jgit.revplot.PlotWalk}
 * and {@link #fillTo(int)} must be used to populate the list.
 *
 * @param <L>
 *            type of lane used by the application.
 */
public class PlotCommitList<L extends PlotLane> extends
		RevCommitList<PlotCommit<L>> {
	static final int MAX_LENGTH = 25;

	private int positionsAllocated;

	private final TreeSet<Integer> freePositions = new TreeSet<>();

	private final HashSet<PlotLane> activeLanes = new HashSet<>(32);

	/** number of (child) commits on a lane */
	private final HashMap<PlotLane, Integer> laneLength = new HashMap<>(
			32);

	/** {@inheritDoc} */
	@Override
	public void clear() {
		super.clear();
		positionsAllocated = 0;
		freePositions.clear();
		activeLanes.clear();
		laneLength.clear();
	}

	/** {@inheritDoc} */
	@Override
	public void source(RevWalk w) {
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
	@SuppressWarnings("unchecked")
	public void findPassingThrough(final PlotCommit<L> currCommit,
			final Collection<L> result) {
		for (PlotLane p : currCommit.passingLanes)
			result.add((L) p);
	}

	/** {@inheritDoc} */
	@SuppressWarnings("ReferenceEquality")
	@Override
	protected void enter(int index, PlotCommit<L> currCommit) {
		setupChildren(currCommit);

		final int nChildren = currCommit.getChildCount();
		if (nChildren == 0) {
			currCommit.lane = nextFreeLane();
		} else if (nChildren == 1
				&& currCommit.children[0].getParentCount() < 2) {
			// Only one child, child has only us as their parent.
			// Stay in the same lane as the child.

			@SuppressWarnings("unchecked")
			final PlotCommit<L> c = currCommit.children[0];
			currCommit.lane = c.lane;
			Integer len = laneLength.get(currCommit.lane);
			len = len != null ? Integer.valueOf(len.intValue() + 1)
					: Integer.valueOf(0);
			laneLength.put(currCommit.lane, len);
		} else {
			// More than one child, or our child is a merge.

			// We look for the child lane the current commit should continue.
			// Candidate lanes for this are those with children, that have the
			// current commit as their first parent.
			// There can be multiple candidate lanes. In that case the longest
			// lane is chosen, as this is usually the lane representing the
			// branch the commit actually was made on.

			// When there are no candidate lanes (i.e. the current commit has
			// only children whose non-first parent it is) we place the current
			// commit on a new lane.

			// The lane the current commit will be placed on:
			PlotLane reservedLane = null;
			PlotCommit childOnReservedLane = null;
			int lengthOfReservedLane = -1;

			for (int i = 0; i < nChildren; i++) {
				@SuppressWarnings("unchecked")
				final PlotCommit<L> c = currCommit.children[i];
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

		continueActiveLanes(currCommit);
		if (currCommit.getParentCount() == 0)
			closeLane(currCommit.lane);
	}

	private void continueActiveLanes(PlotCommit currCommit) {
		for (PlotLane lane : activeLanes)
			if (lane != currCommit.lane)
				currCommit.addPassingLane(lane);
	}

	/**
	 * Sets up fork and merge information in the involved PlotCommits.
	 * Recognizes and handles blockades that involve forking or merging arcs.
	 *
	 * @param index
	 *            the index of <code>currCommit</code> in the list
	 * @param currCommit
	 * @param childOnLane
	 *            the direct child on the same lane as <code>currCommit</code>,
	 *            may be null if <code>currCommit</code> is the first commit on
	 *            the lane
	 */
	@SuppressWarnings("ReferenceEquality")
	private void handleBlockedLanes(final int index, final PlotCommit currCommit,
			final PlotCommit childOnLane) {
		for (PlotCommit child : currCommit.children) {
			if (child == childOnLane)
				continue; // simple continuations of lanes are handled by
							// continueActiveLanes() calls in enter()

			// Is the child a merge or is it forking off?
			boolean childIsMerge = child.getParent(0) != currCommit;
			if (childIsMerge) {
				PlotLane laneToUse = currCommit.lane;
				laneToUse = handleMerge(index, currCommit, childOnLane, child,
						laneToUse);
				child.addMergingLane(laneToUse);
			} else {
				// We want to draw a forking arc in the child's lane.
				// As an active lane, the child lane already continues
				// (unblocked) up to this commit, we only need to mark it as
				// forking off from the current commit.
				PlotLane laneToUse = child.lane;
				currCommit.addForkingOffLane(laneToUse);
			}
		}
	}

	// Handles the case where currCommit is a non-first parent of the child
	@SuppressWarnings("ReferenceEquality")
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
				// We are the first commit on this lane, because
				// otherwise the child commit on this lane would have
				// kept other lanes from blocking us. Since we are the
				// first commit, we can freely reposition.
				int newPos = getFreePosition(blockedPositions);
				freePositions.add(Integer.valueOf(laneToUse
						.getPosition()));
				laneToUse.position = newPos;
			}
		}

		// Actually connect currCommit to the merge child
		drawLaneToChild(index, child, laneToUse);
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
	@SuppressWarnings("ReferenceEquality")
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
			// Positions may be blocked by a commit on a lane.
			if (lane != null)
				blockedPositions.set(lane.getPosition());
			// Positions may also be blocked by forking off and merging lanes.
			// We don't consider passing lanes, because every passing lane forks
			// off and merges at it ends.
			for (PlotLane l : rObj.forkingOffLanes)
				blockedPositions.set(l.getPosition());
			for (PlotLane l : rObj.mergingLanes)
				blockedPositions.set(l.getPosition());
		}
	}

	@SuppressWarnings("unchecked")
	private void closeLane(PlotLane lane) {
		if (activeLanes.remove(lane)) {
			recycleLane((L) lane);
			laneLength.remove(lane);
			freePositions.add(Integer.valueOf(lane.getPosition()));
		}
	}

	private void setupChildren(PlotCommit<L> currCommit) {
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
		}
		final Integer min = freePositions.first();
		freePositions.remove(min);
		return min.intValue();
	}

	/**
	 * Create a new {@link PlotLane} appropriate for this particular
	 * {@link PlotCommitList}.
	 *
	 * @return a new {@link PlotLane} appropriate for this particular
	 *         {@link PlotCommitList}.
	 */
	@SuppressWarnings("unchecked")
	protected L createLane() {
		return (L) new PlotLane();
	}

	/**
	 * Return colors and other reusable information to the plotter when a lane
	 * is no longer needed.
	 *
	 * @param lane
	 *            a lane
	 */
	protected void recycleLane(L lane) {
		// Nothing.
	}
}
