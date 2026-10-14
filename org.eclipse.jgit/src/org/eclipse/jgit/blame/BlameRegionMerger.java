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
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.blame.cache.CacheRegion;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
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
class BlameRegionMerger {
	private final Repository repo;

	private final List<CacheRegion> cachedRegions;

	private final RevWalk rw;

	private final Set<ObjectId> ignoreIds;

	BlameRegionMerger(Repository repo, RevWalk rw,
			List<CacheRegion> cachedRegions) {
		this(repo, rw, cachedRegions, Collections.emptySet());
	}

	BlameRegionMerger(Repository repo, RevWalk rw,
			List<CacheRegion> cachedRegions, Set<ObjectId> ignoreIds) {
		this.repo = repo;
		List<CacheRegion> sorted = new ArrayList<>(cachedRegions);
		Collections.sort(sorted);
		this.cachedRegions = sorted;
		this.rw = rw;
		this.ignoreIds = ignoreIds != null ? ignoreIds
				: Collections.emptySet();
	}

	/**
	 * Return one or more candidates blaming the regions of the "unblamed"
	 * incoming candidate that are not in {@code ignoreIds}, updating
	 * {@code candidate.regionList} to hold only any remaining regions whose
	 * cached source commits are in {@code ignoreIds}.
	 *
	 * @param candidate
	 *            a candidate with a list of unblamed regions
	 * @return A linked list of Candidates with their blamed regions, null if
	 *         there was any error or no regions could be resolved from cache.
	 */
	Candidate mergeCandidate(Candidate candidate) {
		List<Candidate> newCandidates = new ArrayList<>();
		Region remainingHead = null;
		Region remainingTail = null;
		Region r = candidate.regionList;
		while (r != null) {
			List<Region> unblamed = new ArrayList<>();
			try {
				newCandidates.addAll(mergeOneRegion(r, unblamed));
			} catch (IOException e) {
				return null;
			}
			for (Region u : unblamed) {
				if (remainingTail == null) {
					remainingHead = u;
				} else {
					remainingTail.next = u;
				}
				remainingTail = u;
			}
			r = r.next;
		}
		if (newCandidates.isEmpty()) {
			return null;
		}
		candidate.regionList = remainingHead;
		return asLinkedCandidate(newCandidates);
	}

	// Visible for testing
	List<Candidate> mergeOneRegion(Region region) throws IOException {
		return mergeOneRegion(region, new ArrayList<>());
	}

	private List<Candidate> mergeOneRegion(Region region,
			List<Region> unblamedOut) throws IOException {
		List<CacheRegion> overlaps = findOverlaps(region);
		if (overlaps.isEmpty()) {
			throw new IOException(JGitText.get().blameRegionCoverAllLines);
		}
		/*
		 * Cached regions cover the whole file. We find first which ones overlap
		 * with our unblamed region. Then we take the overlapping portions with
		 * the corresponding blame.
		 */
		List<Candidate> candidates = new ArrayList<>();
		for (CacheRegion overlap : overlaps) {
			Region blamedRegions = intersectRegions(region, overlap);
			if (ignoreIds.contains(overlap.getSourceCommit())) {
				unblamedOut.add(blamedRegions);
				continue;
			}
			Candidate c = new Candidate(repo, parse(overlap.getSourceCommit()),
					PathFilter.create(overlap.getSourcePath()));
			c.regionList = blamedRegions;
			candidates.add(c);
		}
		return candidates;
	}

	// Visible for testing
	List<CacheRegion> findOverlaps(Region unblamed) {
		int unblamedStart = unblamed.sourceStart;
		int unblamedEnd = unblamedStart + unblamed.length;
		List<CacheRegion> overlapping = new ArrayList<>();
		for (CacheRegion blamed : cachedRegions) {
			// End is not included
			if (blamed.getEnd() <= unblamedStart) {
				// Blamed region is completely before
				continue;
			}

			if (blamed.getStart() >= unblamedEnd) {
				// Blamed region is completely after
				// Blamed regions are sorted by start position, nothing will
				// match anymore
				break;
			}
			overlapping.add(blamed);
		}
		return overlapping;
	}

	// Visible for testing
	/**
	 * Calculate the intersection between a Region and a CacheRegion, adjusting
	 * the start if needed.
	 * <p>
	 * This should be called only if there is an overlap (filtering the cached
	 * regions with {@link #findOverlaps(Region)}), otherwise the result is
	 * meaningless.
	 *
	 * @param unblamed
	 *            a region from the blame generator
	 * @param cached
	 *            a cached region
	 * @return a new region with the intersection.
	 */
	static Region intersectRegions(Region unblamed, CacheRegion cached) {
		int blamedStart = Math.max(cached.getStart(), unblamed.sourceStart);
		int blamedEnd = Math.min(cached.getEnd(),
				unblamed.sourceStart + unblamed.length);
		int length = blamedEnd - blamedStart;

		// result start and source start should move together
		int blameStartDelta = blamedStart - unblamed.sourceStart;
		return new Region(unblamed.resultStart + blameStartDelta, blamedStart,
				length);
	}

	// Tests can override this, so they don't need a real repo, commit and walk
	protected RevCommit parse(ObjectId oid) throws IOException {
		return rw.parseCommit(oid);
	}

	private static Candidate asLinkedCandidate(List<Candidate> c) {
		Candidate head = c.get(0);
		Candidate tail = head;
		for (int i = 1; i < c.size(); i++) {
			tail.queueNext = c.get(i);
			tail = tail.queueNext;
		}
		return head;
	}
}
