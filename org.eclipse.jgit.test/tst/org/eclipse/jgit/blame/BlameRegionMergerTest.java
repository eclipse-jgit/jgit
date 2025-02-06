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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jgit.blame.cache.CacheRegion;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class BlameRegionMergerTest extends RepositoryTestCase {

	private static final ObjectId O1 = ObjectId
			.fromString("ff6dd8db6edc9aa0ac58fea1d14a55be46c3eb14");

	private static final ObjectId O2 = ObjectId
			.fromString("c3c7f680c6bee238617f25f6aa85d0b565fc8ecb");

	private static final ObjectId O3 = ObjectId
			.fromString("29e014aad0399fe8ede7c101d01b6e440ac9966b");

	List<RevCommit> fakeCommits = List.of(new FakeRevCommit(O1),
			new FakeRevCommit(O2), new FakeRevCommit(O3));

	// In reverse order, so the code doesn't assume a sorted list
	List<CacheRegion> cachedRegions = List.of(
			new CacheRegion("README", O3, 20, 30),
			new CacheRegion("README", O2, 10, 20),
			new CacheRegion("README", O1, 0, 10));

	BlameRegionMerger blamer = new BlameRegionMergerFakeCommits(fakeCommits,
			cachedRegions);

	@Test
	public void intersectRegions_allInside() {
		Region unblamed = new Region(15, 18, 10);
		CacheRegion blamed = new CacheRegion("README", O1, 10, 90);

		Region result = BlameRegionMerger.intersectRegions(unblamed, blamed);
		// Same lines in result and source
		assertEquals(15, result.resultStart);
		assertEquals(18, result.sourceStart);
		assertEquals(10, result.length);
		assertNull(result.next);
	}

	@Test
	public void intersectRegions_startsBefore() {
		// Intesecting [4, 14) with [10, 90)
		Region unblamed = new Region(30, 4, 10);
		CacheRegion blamed = new CacheRegion("README", O1, 10, 90);

		Region result = BlameRegionMerger.intersectRegions(unblamed, blamed);

		// The unblamed region starting at 4 (sourceStart), starts at 30 in the
		// original file (resultStart). e.g. some commit introduced
		// lines. If we take the second portion of the region, we need to move
		// the result start accordingly.
		assertEquals(36, result.resultStart);
		assertEquals(10, result.sourceStart);
		assertEquals(4, result.length);
		assertNull(result.next);
	}

	@Test
	public void intersectRegions_endsAfter() {
		// Intesecting [85, 95) with [10, 90)
		Region unblamed = new Region(30, 85, 10);
		CacheRegion blamed = new CacheRegion("README", O1, 10, 90);

		Region result = BlameRegionMerger.intersectRegions(unblamed, blamed);

		assertEquals(30, result.resultStart);
		assertEquals(85, result.sourceStart);
		assertEquals(5, result.length);
		assertNull(result.next);
	}

	@Test
	public void intersectRegions_spillOverBothSides() {
		// Intesecting [5, 100) with [10, 90)
		Region unblamed = new Region(30, 5, 95);
		CacheRegion blamed = new CacheRegion("README", O1, 10, 90);

		Region result = BlameRegionMerger.intersectRegions(unblamed, blamed);

		assertEquals(35, result.resultStart);
		assertEquals(10, result.sourceStart);
		assertEquals(80, result.length);
		assertNull(result.next);
	}

	@Test
	public void intersectRegions_exactMatch() {
		// Intesecting [5, 100) with [10, 90)
		Region unblamed = new Region(30, 10, 80);
		CacheRegion blamed = new CacheRegion("README", O1, 10, 90);

		Region result = BlameRegionMerger.intersectRegions(unblamed, blamed);

		assertEquals(30, result.resultStart);
		assertEquals(10, result.sourceStart);
		assertEquals(80, result.length);
		assertNull(result.next);
	}

	@Test
	public void blame_exactOverlap() throws IOException {
		Region unblamed = new Region(0, 10, 10);
		List<Candidate> blamed = blamer.mergeOneRegion(unblamed);

		assertEquals(1, blamed.size());
		Candidate c = blamed.get(0);
		assertEquals(c.sourceCommit.name(), O2.name());
		assertEquals(c.regionList.resultStart, unblamed.resultStart);
		assertEquals(c.regionList.sourceStart, unblamed.sourceStart);
		assertEquals(10, c.regionList.length);
		assertNull(c.regionList.next);
	}

	@Test
	public void blame_corruptedIndex() {
		Region outOfRange = new Region(0, 43, 4);
		// This region is out of the blamed area
		assertThrows(IllegalStateException.class,
				() -> blamer.mergeOneRegion(outOfRange));
	}

	@Test
	public void blame_allInsideOneBlamedRegion() throws IOException {
		Region unblamed = new Region(0, 5, 3);
		// This region if fully blamed to O1
		List<Candidate> blamed = blamer.mergeOneRegion(unblamed);
		assertEquals(1, blamed.size());
		Candidate c = blamed.get(0);
		assertEquals(c.sourceCommit.name(), O1.name());
		assertEquals(c.regionList.resultStart, unblamed.resultStart);
		assertEquals(c.regionList.sourceStart, unblamed.sourceStart);
		assertEquals(3, c.regionList.length);
		assertNull(c.regionList.next);
	}

	@Test
	public void blame_overTwoBlamedRegions() throws IOException {
		Region unblamed = new Region(0, 8, 5);
		// (8, 10) belongs go C1, (10, 13) to C2
		List<Candidate> blamed = blamer.mergeOneRegion(unblamed);
		assertEquals(2, blamed.size());
		Candidate c = blamed.get(0);
		assertEquals(c.sourceCommit.name(), O1.name());
		assertEquals(unblamed.resultStart, c.regionList.resultStart);
		assertEquals(unblamed.sourceStart, c.regionList.sourceStart);
		assertEquals(2, c.regionList.length);
		assertNull(c.regionList.next);

		c = blamed.get(1);
		assertEquals(c.sourceCommit.name(), O2.name());
		assertEquals(2, c.regionList.resultStart);
		assertEquals(10, c.regionList.sourceStart);
		assertEquals(3, c.regionList.length);
		assertNull(c.regionList.next);
	}

	@Test
	public void blame_all() throws IOException {
		Region unblamed = new Region(0, 0, 30);
		List<Candidate> blamed = blamer.mergeOneRegion(unblamed);
		assertEquals(3, blamed.size());
		Candidate c = blamed.get(0);
		assertEquals(c.sourceCommit.name(), O1.name());
		assertEquals(unblamed.resultStart, c.regionList.resultStart);
		assertEquals(unblamed.sourceStart, c.regionList.sourceStart);
		assertEquals(10, c.regionList.length);
		assertNull(c.regionList.next);

		c = blamed.get(1);
		assertEquals(c.sourceCommit.name(), O2.name());
		assertEquals(10, c.regionList.resultStart);
		assertEquals(10, c.regionList.sourceStart);
		assertEquals(10, c.regionList.length);
		assertNull(c.regionList.next);

		c = blamed.get(2);
		assertEquals(c.sourceCommit.name(), O3.name());
		assertEquals(20, c.regionList.resultStart);
		assertEquals(20, c.regionList.sourceStart);
		assertEquals(10, c.regionList.length);
		assertNull(c.regionList.next);
	}

	@Test
	public void blame_fromCandidate() {
		// We don't use anything from the candidate besides the
		// regionList
		Candidate c = new Candidate(null, null, null);
		c.regionList = new Region(0, 8, 5);
		c.regionList.next = new Region(22, 22, 4);

		Candidate blamed = blamer.mergeCandidate(c);
		// Three candidates
		assertNotNull(blamed);
		assertNotNull(blamed.queueNext);
		assertNotNull(blamed.queueNext.queueNext);
		assertNull(blamed.queueNext.queueNext.queueNext);

		assertEquals(O1.name(), blamed.sourceCommit.name());

		Candidate second = blamed.queueNext;
		assertEquals(O2.name(), second.sourceCommit.name());

		Candidate third = blamed.queueNext.queueNext;
		assertEquals(O3.name(), third.sourceCommit.name());
	}

	@Test
	public void blame_fromCandidate_twiceCandidateInOutput() {
		Candidate c = new Candidate(null, null, null);
		// This produces O1 and O2
		c.regionList = new Region(0, 8, 5);
		// This produces O2 and O3
		c.regionList.next = new Region(20, 15, 7);

		Candidate blamed = blamer.mergeCandidate(c);
		assertCandidateSingleRegion(O1, 2, blamed);
		blamed = blamed.queueNext;
		assertCandidateSingleRegion(O2, 3, blamed);
		// We do not merge candidates afterwards, so these are
		// two different candidates to the same source
		blamed = blamed.queueNext;
		assertCandidateSingleRegion(O2, 5, blamed);
		blamed = blamed.queueNext;
		assertCandidateSingleRegion(O3, 2, blamed);
		assertNull(blamed.queueNext);
	}

	private static void assertCandidateSingleRegion(ObjectId expectedOid,
			int expectedLength, Candidate actual) {
		assertNotNull("candidate", actual);
		assertNotNull("region list not empty", actual.regionList);
		assertNull("region list has only one element", actual.regionList.next);
		assertEquals(expectedOid, actual.sourceCommit);
		assertEquals(expectedLength, actual.regionList.length);
	}

	private static final class BlameRegionMergerFakeCommits
			extends BlameRegionMerger {

		private final Map<ObjectId, RevCommit> cache;

		BlameRegionMergerFakeCommits(List<RevCommit> commits,
				List<CacheRegion> blamedRegions) {
			super(null, null, blamedRegions);
			cache = commits.stream().collect(Collectors
					.toMap(RevCommit::toObjectId, Function.identity()));
		}

		@Override
		protected RevCommit parse(ObjectId oid) {
			return cache.get(oid);
		}
	}

	private static final class FakeRevCommit extends RevCommit {
		FakeRevCommit(AnyObjectId id) {
			super(id);
		}
	}
}
