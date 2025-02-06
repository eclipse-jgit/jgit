/*
 * Copyright (C) 2025, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.blame;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.blame.cache.CacheRegion;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

/**
 * Translates an unblamed region into one or more blamed regions, using the
 * fully blamed data from cache.
 * <p>
 * Blamed and unblamed regions are not symmetrical: An unblamed region is just a
 * range of lines over the file. A blamed region is a Candidate (with the commit
 * info) with a region inside (the range blamed).
 */
class RegionBlamer {
	private final List<CacheRegion> blamedRegions;

	private final RevWalk rw;

	RegionBlamer(RevWalk rw, List<CacheRegion> blamedRegions) {
		this.blamedRegions = blamedRegions;
		this.rw = rw;
	}

	Candidate blame(Candidate candidate) throws IOException {
		List<Candidate> newCandidates = new ArrayList<>();
		Region r = candidate.regionList;
		while (r != null) {
			newCandidates.addAll(blameRegion(r));
			r = r.next;
		}
		return asLinkedCandidate(newCandidates);
	}

	// Visible for testing
	List<Candidate> blameRegion(Region region) throws IOException {
		List<CacheRegion> overlaps = findOverlaps(region);
		if (overlaps.isEmpty()) {
			throw new IllegalStateException(
					"Cached blame should cover all lines");
		}
		List<Candidate> candidates = new ArrayList<>();
		for (CacheRegion overlap : overlaps) {
			candidates.add(asCandidate(region, overlap));
		}
		return candidates;
	}

	private List<CacheRegion> findOverlaps(Region unblamed) {
		int unblamedStart = unblamed.sourceStart;
		int unblamedEnd = unblamedStart + unblamed.length;
		List<CacheRegion> overlapping = new ArrayList<>();
		for (CacheRegion blamed : blamedRegions) {
			// End is not included
			if (blamed.getEnd() <= unblamedStart
					|| blamed.getStart() >= unblamedEnd) {
				// Completely before or later
				continue;
			}
			overlapping.add(blamed);
		}
		return overlapping;
	}

	/**
	 * Create candidate with the commit of the blamed and the portion of the
	 * unblamed region that overlaps
	 *
	 * @param unblamed
	 *            an unblamed region
	 * @param blamed
	 *            a blamed region that overlaps (total or partially) with the
	 *            unblamed region
	 * @return a candidate
	 * @throws IOException
	 *             cannot parse the objectId of the blamed region as a commit
	 */
	private Candidate asCandidate(Region unblamed, CacheRegion blamed)
			throws IOException {
		// Repository is not needed
		Candidate c = new Candidate(null, parse(blamed.getSourceCommit()),
				PathFilter.create(blamed.getSourcePath()));
		c.regionList = getBlamedPortion(unblamed, blamed);
		return c;
	}

	private Region getBlamedPortion(Region unblamed, CacheRegion blamed) {
		int blamedStart = Math.max(blamed.getStart(), unblamed.sourceStart);
		int blamedEnd = Math.min(blamed.getEnd(),
				unblamed.sourceStart + unblamed.length);

		// result start and source start should move together
		int blameStartDelta = blamedStart - unblamed.sourceStart;
		return new Region(unblamed.resultStart + blameStartDelta, blamedStart,
				blamedEnd - blamedStart);
	}

	// Tests can override this, so they don't need a real repo, commit and walk
	protected RevCommit parse(ObjectId oid) throws IOException {
		return rw.parseCommit(oid);
	}

	private Candidate asLinkedCandidate(List<Candidate> c) {
		Candidate head = c.get(0);
		Candidate tail = head;
		for (int i = 1; i < c.size(); i++) {
			tail.queueNext = c.get(i);
			tail = tail.queueNext;
		}
		return head;
	}
}
