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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.blame.cache.CacheRegion;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Blame a file on a repo at certain revision with different caches (e.g.
 * empty, tip in cache, parent in cache...) and verify same result.
 */
@RunWith(Parameterized.class)
public class BlameGeneratorSimpleTest extends RepositoryTestCase {

	@Parameterized.Parameter
	public BlameAtC4TestData testData;

	// Expected results when blaming C4 with certain cache
	private record BlameAtC4TestData(InMemoryBlameCache cache,
			boolean expectedCacheHit, int expectedCandidatesVisited) {
		@Override
		public String toString() {
			return cache.toString();
		}
	}

	/**
	 * File contents:
	 *
	 * <pre>
	 *    c1       c2       c3       c4         c4 blame
	 *   -----    -----    -----    -----       --------
	 *   first    first    first    first          c1
	 *   second   second   other2   other22        c4
	 *   third    third    other3   other3         c3
	 *            fourth   fourth   other4         c4
	 * </pre>
	 */
	@Parameterized.Parameters(name = "{0}")
	public static Collection<BlameAtC4TestData> data() {
		InMemoryBlameCache empty = new InMemoryBlameCache("<empty>");

		// Cache with c2
		// In c2, first 1 lines are for c1 and last one for c2
		List<CacheRegion> c2Regions = new ArrayList<>(2);
		c2Regions.add(new CacheRegion(FILE, C1, 0, 3));
		c2Regions.add(new CacheRegion(FILE, C2, 3, 4));

		InMemoryBlameCache c2Cache = new InMemoryBlameCache("<c2>");
		c2Cache.put(C2, FILE, c2Regions);

		InMemoryBlameCache c4Cache = new InMemoryBlameCache("<c4>");
		List<CacheRegion> c4Regions = new ArrayList<>(4);
		c4Regions.add(new CacheRegion(FILE, C1, 0, 1));
		c4Regions.add(new CacheRegion(FILE, C4, 1, 2));
		c4Regions.add(new CacheRegion(FILE, C3, 2, 3));
		c4Regions.add(new CacheRegion(FILE, C4, 3, 4));
		c4Cache.put(C4, FILE, c4Regions);

		return Arrays.asList(new BlameAtC4TestData(empty, false, 4),
				new BlameAtC4TestData(c2Cache, true, 3),
				new BlameAtC4TestData(c4Cache, true, 1));
	}

	private static final ObjectId C1 = ObjectId
			.fromString("7af6eba13d51b635098c0fec00311b6e798797d5");

	private static final ObjectId C2 = ObjectId
			.fromString("b878c6c87e3db5f3ef384190244e42941e062b0f");

	private static final ObjectId C3 = ObjectId
			.fromString("6ab0c905c5490340a4539492b399c4cf435899f9");

	private static final ObjectId C4 = ObjectId
			.fromString("a8fc99fbbbdc87a4aec2df45af6e048bdbc36faf");

	private void setupSimpleRepo() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit c1 = commit(git, "first", "second", "third");
			RevCommit c2 = commit(git, "first", "second", "third", "fourth");
			RevCommit c3 = commit(git, "first", "other2", "other3", "fourth");
			RevCommit c4 = commit(git,"first", "other22", "other3", "other4");

			// We need the hardcoded IDs for the static test data.
			// Verify everything matches
			assertEquals(c1.toObjectId(), C1);
			assertEquals(c2.toObjectId(), C2);
			assertEquals(c3.toObjectId(), C3);
			assertEquals(c4.toObjectId(), C4);
		}
	}

	private static final String FILE = "file.txt";

	@Test
	public void correctness_sameBlame() throws Exception {
		setupSimpleRepo();
		try (BlameGenerator generator = new BlameGenerator(db, FILE,
				testData.cache)) {
			generator.push(null, db.resolve(Constants.HEAD));
			List<EmittedRegion> regions = consume(generator);

			assertAllLinesCovered(4, regions);
			List<EmittedRegion> expected = Arrays.asList(
					new EmittedRegion(C1, 0, 1, 0, 1),
					new EmittedRegion(C4, 1, 2, 1, 2),
					new EmittedRegion(C3, 2, 3, 2, 3),
					new EmittedRegion(C4, 3, 4, 3, 4));
			assertRegionsStrictEquals(expected, regions);
		}
	}

	@Test
	public void cache_hitIfAvailable() throws Exception {
		setupSimpleRepo();
		try (BlameGenerator generator = new BlameGenerator(db, FILE,
				testData.cache)) {
			generator.push(null, db.resolve(Constants.HEAD));
			assertEquals(4, generator.getResultContents().size());
			consume(generator);

			assertEquals("hits cache", testData.expectedCacheHit,
					generator.getStats().isCacheHit());
		}
	}

	@Test
	public void cache_walkStops() throws Exception {
		setupSimpleRepo();
		try (BlameGenerator generator = new BlameGenerator(db, FILE,
				testData.cache)) {
			generator.push(null, db.resolve(Constants.HEAD));
			assertEquals(4, generator.getResultContents().size());
			consume(generator);

			assertEquals("candidates visited",
					testData.expectedCandidatesVisited,
					generator.getStats().getCandidatesVisited());
		}
	}

	@Test
	public void cache_notNeeded() throws Exception {
		setupSimpleRepo();
		RevCommit c5;
		// Commit overwriting everything
		try (Git git = new Git(db)) {
			c5 = commit(git, "a", "b", "c", "d");
		}
		try (BlameGenerator generator = new BlameGenerator(db, FILE,
				testData.cache)) {
			generator.push(null, db.resolve(Constants.HEAD));

			// Cache doesn't matter, c5 has all the blame
			assertTrue(generator.next());
			assertEquals(c5, generator.getSourceCommit());
			assertEquals(0, generator.getResultStart());
			assertEquals(4, generator.getResultEnd());

			assertFalse(generator.next());
			assertFalse(generator.getStats().isCacheHit());
			assertEquals(1, generator.getStats().getCandidatesVisited());
		}
	}

	@Test
	public void cache_generateAndUse() throws Exception {
		setupSimpleRepo();

		InMemoryBlameCache cache = blameAndCache(C2);
		try (BlameGenerator generator = new BlameGenerator(db, FILE, cache)) {
			generator.push(null, db.resolve(Constants.HEAD));
			assertEquals(4, generator.getResultContents().size());

			List<EmittedRegion> regions = consume(generator);
			assertAllLinesCovered(4, regions);
			List<EmittedRegion> expected = Arrays.asList(
					new EmittedRegion(C1, 0, 1, 0, 1),
					new EmittedRegion(C4, 1, 2, 1, 2),
					new EmittedRegion(C3, 2, 3, 2, 3),
					new EmittedRegion(C4, 3, 4, 3, 4));
			assertRegionsStrictEquals(expected, regions);

			assertEquals(1, cache.getHits().size());
			assertEquals(C2, cache.getHits().get(0));
		}
	}

	private List<EmittedRegion> consume(BlameGenerator generator)
			throws IOException {
		List<EmittedRegion> result = new ArrayList<>();
		while (generator.next()) {
			EmittedRegion genRegion = new EmittedRegion(
					generator.getSourceCommit().toObjectId(),
					generator.getSourceStart(), generator.getSourceEnd(),
					generator.getResultStart(), generator.getResultEnd());
			result.add(genRegion);
		}
		return result;
	}

	private InMemoryBlameCache blameAndCache(ObjectId commit)
			throws IOException {
		InMemoryBlameCache cache = new InMemoryBlameCache("<x>");
		try (BlameGenerator generator = new BlameGenerator(db, FILE)) {
			generator.push(null, commit);
			List<CacheRegion> regions = consume(generator).stream()
					.map(EmittedRegion::asCacheRegion)
					.collect(Collectors.toUnmodifiableList());
			cache.put(commit, FILE, regions);
		}
		return cache;
	}

	private RevCommit commit(Git git, String... lines) throws Exception {
		writeTrashFile(FILE, join(lines));
		git.add().addFilepattern(FILE).call();
		return git.commit().setMessage("a commit").call();
	}

	private static String join(String... lines) {
		StringBuilder joined = new StringBuilder();
		for (String line : lines)
			joined.append(line).append('\n');
		return joined.toString();
	}

	private static void assertRegionsStrictEquals(List<EmittedRegion> expected,
			List<EmittedRegion> actual) {
		assertEquals(expected.size(), actual.size());
		Collections.sort(actual);
		for (int i = 0; i < expected.size(); i++) {
			assertEquals(String.format("List differ in element %d", i),
					expected.get(i), actual.get(i));
		}
	}

	private static void assertAllLinesCovered(int lines,
			List<EmittedRegion> regions) {
		Collections.sort(regions);
		assertEquals("Starts in first line", 0, regions.get(0).resultStart());
		for (int i = 1; i < regions.size(); i++) {
			assertEquals("No gaps", regions.get(i).resultStart(),
					regions.get(i - 1).resultEnd());
		}
		assertEquals("Ends in last line", lines,
				regions.get(regions.size() - 1).resultEnd());
	}

	private record EmittedRegion(ObjectId oid, int sourceStart, int sourceEnd,
			int resultStart, int resultEnd)
			implements Comparable<EmittedRegion> {
		@Override
		public int compareTo(EmittedRegion o) {
			return resultStart - o.resultStart;
		}

		CacheRegion asCacheRegion() {
			return new CacheRegion(FILE, oid, resultStart, resultEnd);
		}
	}
}
