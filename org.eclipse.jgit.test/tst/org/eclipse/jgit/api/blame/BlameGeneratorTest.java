/*
 * Copyright (C) 2011, GitHub Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api.blame;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.blame.BlameGenerator;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.blame.cache.BlameCache;
import org.eclipse.jgit.blame.cache.CacheRegion;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Unit tests of {@link BlameGenerator}. */
public class BlameGeneratorTest extends RepositoryTestCase {
	private static final String FILE = "file.txt";

	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
	}

	@Override
	@After
	public void tearDown() throws Exception {
		git.close();
		super.tearDown();
	}

	@Test
	public void testBoundLineDelete() throws Exception {
		try (Git git = new Git(db)) {
			String[] content1 = new String[] { "first", "second" };
			writeTrashFile(FILE, join(content1));
			git.add().addFilepattern(FILE).call();
			RevCommit c1 = git.commit().setMessage("create file").call();

			String[] content2 = new String[] { "third", "first", "second" };
			writeTrashFile(FILE, join(content2));
			git.add().addFilepattern(FILE).call();
			RevCommit c2 = git.commit().setMessage("create file").call();

			try (BlameGenerator generator = new BlameGenerator(db, FILE)) {
				generator.push(null, db.resolve(Constants.HEAD));
				assertEquals(3, generator.getResultContents().size());

				assertTrue(generator.next());
				assertEquals(c2, generator.getSourceCommit());
				assertEquals(1, generator.getRegionLength());
				assertEquals(0, generator.getResultStart());
				assertEquals(1, generator.getResultEnd());
				assertEquals(0, generator.getSourceStart());
				assertEquals(1, generator.getSourceEnd());
				assertEquals(FILE, generator.getSourcePath());

				assertTrue(generator.next());
				assertEquals(c1, generator.getSourceCommit());
				assertEquals(2, generator.getRegionLength());
				assertEquals(1, generator.getResultStart());
				assertEquals(3, generator.getResultEnd());
				assertEquals(0, generator.getSourceStart());
				assertEquals(2, generator.getSourceEnd());
				assertEquals(FILE, generator.getSourcePath());

				assertFalse(generator.next());
			}
		}
	}

	@Test
	public void testRenamedBoundLineDelete() throws Exception {
		try (Git git = new Git(db)) {
			final String FILENAME_1 = "subdir/file1.txt";
			final String FILENAME_2 = "subdir/file2.txt";

			String[] content1 = new String[] { "first", "second" };
			writeTrashFile(FILENAME_1, join(content1));
			git.add().addFilepattern(FILENAME_1).call();
			RevCommit c1 = git.commit().setMessage("create file1").call();

			// rename it
			writeTrashFile(FILENAME_2, join(content1));
			git.add().addFilepattern(FILENAME_2).call();
			deleteTrashFile(FILENAME_1);
			git.rm().addFilepattern(FILENAME_1).call();
			git.commit().setMessage("rename file1.txt to file2.txt").call();

			// and change the new file
			String[] content2 = new String[] { "third", "first", "second" };
			writeTrashFile(FILENAME_2, join(content2));
			git.add().addFilepattern(FILENAME_2).call();
			RevCommit c2 = git.commit().setMessage("change file2").call();

			try (BlameGenerator generator = new BlameGenerator(db,
					FILENAME_2)) {
				generator.push(null, db.resolve(Constants.HEAD));
				assertEquals(3, generator.getResultContents().size());

				assertTrue(generator.next());
				assertEquals(c2, generator.getSourceCommit());
				assertEquals(1, generator.getRegionLength());
				assertEquals(0, generator.getResultStart());
				assertEquals(1, generator.getResultEnd());
				assertEquals(0, generator.getSourceStart());
				assertEquals(1, generator.getSourceEnd());
				assertEquals(FILENAME_2, generator.getSourcePath());

				assertTrue(generator.next());
				assertEquals(c1, generator.getSourceCommit());
				assertEquals(2, generator.getRegionLength());
				assertEquals(1, generator.getResultStart());
				assertEquals(3, generator.getResultEnd());
				assertEquals(0, generator.getSourceStart());
				assertEquals(2, generator.getSourceEnd());
				assertEquals(FILENAME_1, generator.getSourcePath());

				assertFalse(generator.next());
			}

			// and test again with other BlameGenerator API:
			try (BlameGenerator generator = new BlameGenerator(db,
					FILENAME_2)) {
				generator.push(null, db.resolve(Constants.HEAD));
				BlameResult result = generator.computeBlameResult();

				assertEquals(3, result.getResultContents().size());

				assertEquals(c2, result.getSourceCommit(0));
				assertEquals(FILENAME_2, result.getSourcePath(0));

				assertEquals(c1, result.getSourceCommit(1));
				assertEquals(FILENAME_1, result.getSourcePath(1));

				assertEquals(c1, result.getSourceCommit(2));
				assertEquals(FILENAME_1, result.getSourcePath(2));
			}
		}
	}

	@Test
	public void testLinesAllDeletedShortenedWalk() throws Exception {
		try (Git git = new Git(db)) {
			String[] content1 = new String[] { "first", "second", "third" };

			writeTrashFile(FILE, join(content1));
			git.add().addFilepattern(FILE).call();
			git.commit().setMessage("create file").call();

			String[] content2 = new String[] { "" };

			writeTrashFile(FILE, join(content2));
			git.add().addFilepattern(FILE).call();
			git.commit().setMessage("create file").call();

			writeTrashFile(FILE, join(content1));
			git.add().addFilepattern(FILE).call();
			RevCommit c3 = git.commit().setMessage("create file").call();

			try (BlameGenerator generator = new BlameGenerator(db, FILE)) {
				generator.push(null, db.resolve(Constants.HEAD));
				assertEquals(3, generator.getResultContents().size());

				assertTrue(generator.next());
				assertEquals(c3, generator.getSourceCommit());
				assertEquals(0, generator.getResultStart());
				assertEquals(3, generator.getResultEnd());

				assertFalse(generator.next());
			}
		}
	}

	@Test
	public void cache_empty() throws Exception {
		// Baseline, blame without cache
		RevCommit c1 = commit("first", "second", "third");
		/*          */ commit("first", "second", "third", "fourth");
		RevCommit c3 = commit("first", "other2", "other3", "fourth");
		RevCommit c4 = commit("first", "other22", "other3", "other4");

		try (BlameGenerator generator = new BlameGenerator(db, FILE,
				new InMemoryBlameCache())) {
			generator.push(null, db.resolve(Constants.HEAD));
			assertEquals(4, generator.getResultContents().size());

			assertTrue(generator.next());
			assertEquals(c4, generator.getSourceCommit());
			assertEquals(1, generator.getResultStart());
			assertEquals(2, generator.getResultEnd());

			assertTrue(generator.next());
			assertEquals(c4, generator.getSourceCommit());
			assertEquals(3, generator.getResultStart());
			assertEquals(4, generator.getResultEnd());

			assertTrue(generator.next());
			assertEquals(c3, generator.getSourceCommit());
			assertEquals(2, generator.getResultStart());
			assertEquals(3, generator.getResultEnd());

			assertTrue(generator.next());
			assertEquals(c1, generator.getSourceCommit());
			assertEquals(0, generator.getResultStart());
			assertEquals(1, generator.getResultEnd());

			assertFalse(generator.next());

			assertEquals(4, generator.getStats().getCandidatesVisited());
			assertFalse(generator.getStats().isCacheHit());
		}
	}

	@Test
	public void cache_tipInCache() throws Exception {
		RevCommit c1 = commit("first", "second", "third");
		/*          */ commit("first", "second", "third", "fourth");
		RevCommit c3 = commit("first", "other2", "other3", "fourth");
		RevCommit c4 = commit("first", "other22", "other3", "other4");

		List<CacheRegion> c4Regions = new ArrayList<>(4);
		c4Regions.add(new CacheRegion(FILE, c1, 0, 1));
		c4Regions.add(new CacheRegion(FILE, c4, 1, 2));
		c4Regions.add(new CacheRegion(FILE, c3, 2, 3));
		c4Regions.add(new CacheRegion(FILE, c4, 3, 4));

		InMemoryBlameCache cache = new InMemoryBlameCache();
		cache.put(c4, FILE, c4Regions);
		try (BlameGenerator generator = new BlameGenerator(db, FILE, cache)) {
			generator.push(null, db.resolve(Constants.HEAD));
			assertEquals(4, generator.getResultContents().size());

			assertTrue(generator.next());
			assertEquals(c1, generator.getSourceCommit());
			assertEquals(0, generator.getResultStart());
			assertEquals(1, generator.getResultEnd());

			assertTrue(generator.next());
			assertEquals(c4, generator.getSourceCommit());
			assertEquals(1, generator.getResultStart());
			assertEquals(2, generator.getResultEnd());

			assertTrue(generator.next());
			assertEquals(c3, generator.getSourceCommit());
			assertEquals(2, generator.getResultStart());
			assertEquals(3, generator.getResultEnd());

			assertTrue(generator.next());
			assertEquals(c4, generator.getSourceCommit());
			assertEquals(3, generator.getResultStart());
			assertEquals(4, generator.getResultEnd());

			assertFalse(generator.next());

			assertEquals(1, generator.getStats().getCandidatesVisited());
			assertTrue(generator.getStats().isCacheHit());
			assertEquals(1, cache.hits.size());
			assertEquals(c4, cache.hits.get(0));
		}
	}

	@Test
	public void cache_intermediateInCache() throws Exception {
		RevCommit c1 = commit("first", "second", "third");
		RevCommit c2 = commit("first", "second", "third", "fourth");
		RevCommit c3 = commit("first", "other2", "other3", "fourth");
		RevCommit c4 = commit("first", "other22", "other3", "other4");

		List<CacheRegion> c2Regions = new ArrayList<>(3);
		c2Regions.add(new CacheRegion(FILE, c1, 0, 3));
		c2Regions.add(new CacheRegion(FILE, c2, 3, 4));

		InMemoryBlameCache cache = new InMemoryBlameCache();
		cache.put(c2, FILE, c2Regions);
		try (BlameGenerator generator = new BlameGenerator(db, FILE, cache)) {
			generator.push(null, db.resolve(Constants.HEAD));
			assertEquals(4, generator.getResultContents().size());

			assertTrue(generator.next());
			assertEquals(c4, generator.getSourceCommit());
			assertEquals(1, generator.getResultStart());
			assertEquals(2, generator.getResultEnd());
			assertTrue(generator.next());

			assertEquals(c4, generator.getSourceCommit());
			assertEquals(3, generator.getResultStart());
			assertEquals(4, generator.getResultEnd());

			assertTrue(generator.next());
			assertEquals(c3, generator.getSourceCommit());
			assertEquals(2, generator.getResultStart());
			assertEquals(3, generator.getResultEnd());

			assertTrue(generator.next());
			assertEquals(c1, generator.getSourceCommit());
			assertEquals(0, generator.getResultStart());
			assertEquals(1, generator.getResultEnd());

			assertFalse(generator.next());
			assertEquals(3, generator.getStats().getCandidatesVisited());
			assertTrue(generator.getStats().isCacheHit());
			assertEquals(1, cache.hits.size());
			assertEquals(c2, cache.hits.get(0));
		}
	}

	@Test
	public void cache_intermediateInCache_stopInCache() throws Exception {
		RevCommit c1 = commit("first", "second", "third");
		RevCommit c2 = commit("first", "second", "third", "fourth");
		/*          */ commit("first", "other2", "other3", "fourth");
		/*          */ commit("first", "other22", "other3", "other4");

		List<CacheRegion> c1Regions = new ArrayList<>(1);
		c1Regions.add(new CacheRegion(FILE, c1, 0, 3));

		List<CacheRegion> c2Regions = new ArrayList<>(3);
		c2Regions.add(new CacheRegion(FILE, c1, 0, 3));
		c2Regions.add(new CacheRegion(FILE, c2, 3, 4));

		InMemoryBlameCache cache = new InMemoryBlameCache();
		cache.put(c1, FILE, c1Regions);
		cache.put(c2, FILE, c2Regions);
		try (BlameGenerator generator = new BlameGenerator(db, FILE, cache)) {
			generator.push(null, db.resolve(Constants.HEAD));
			while (generator.next()) {
				// We know results are fine from cache_intermediateInCache
			}
			assertEquals(3, generator.getStats().getCandidatesVisited());
			assertTrue(generator.getStats().isCacheHit());
			assertEquals(1, cache.hits.size());
			assertEquals(c2, cache.hits.get(0));
		}
	}

	@Test
	public void cache_genCacheAndUseIt() throws Exception {
		RevCommit c1 = commit("first", "second", "third");
		RevCommit c2 = commit("first", "second", "third", "fourth");
		RevCommit c3 = commit("first", "other2", "other3", "fourth");
		RevCommit c4 = commit("first", "other22", "other3", "other4");

		InMemoryBlameCache cache = blameAndCache(c2);
		try (BlameGenerator generator = new BlameGenerator(db, FILE, cache)) {
			generator.push(null, db.resolve(Constants.HEAD));
			assertEquals(4, generator.getResultContents().size());

			assertTrue(generator.next());
			assertEquals(c4, generator.getSourceCommit());
			assertEquals(1, generator.getResultStart());
			assertEquals(2, generator.getResultEnd());
			assertTrue(generator.next());

			assertEquals(c4, generator.getSourceCommit());
			assertEquals(3, generator.getResultStart());
			assertEquals(4, generator.getResultEnd());

			assertTrue(generator.next());
			assertEquals(c3, generator.getSourceCommit());
			assertEquals(2, generator.getResultStart());
			assertEquals(3, generator.getResultEnd());

			assertTrue(generator.next());
			assertEquals(c1, generator.getSourceCommit());
			assertEquals(0, generator.getResultStart());
			assertEquals(1, generator.getResultEnd());

			assertFalse(generator.next());
			assertEquals(3, generator.getStats().getCandidatesVisited());
			assertTrue(generator.getStats().isCacheHit());

			assertEquals(1, cache.hits.size());
			assertEquals(c2, cache.hits.get(0));
		}
	}

	@Test
	public void cache_compareWithAndWithout() throws Exception {
		RevCommit c1 = commit("first", "second", "third");
		RevCommit c2 = commit("first", "second", "third", "fourth");
		/*          */ commit("first", "other2", "other3", "fourth");
		/*          */ commit("first", "other22", "other3", "other4");

		List<EmittedRegion> withoutCache;
		try (BlameGenerator genNoCache = new BlameGenerator(db, FILE)) {
			genNoCache.push(null, db.resolve(Constants.HEAD));
			withoutCache = consume(genNoCache);
			assertFalse(genNoCache.getStats().isCacheHit());
		}

		List<CacheRegion> c2Regions = new ArrayList<>(3);
		c2Regions.add(new CacheRegion(FILE, c1, 0, 1));
		c2Regions.add(new CacheRegion(FILE, c2, 1, 3));
		c2Regions.add(new CacheRegion(FILE, c1, 3, 4));

		InMemoryBlameCache cache = new InMemoryBlameCache();
		cache.put(c2, FILE, c2Regions);
		List<EmittedRegion> withCache;
		try (BlameGenerator genWithCache = new BlameGenerator(db, FILE,
				cache)) {
			genWithCache.push(null, db.resolve(Constants.HEAD));
			withCache = consume(genWithCache);
			assertTrue(genWithCache.getStats().isCacheHit());
		}

		Collections.sort(withCache);
		Collections.sort(withoutCache);
		assertEquals(withCache.size(), withoutCache.size());
		assertListsEquals(withCache, withoutCache);
	}

	@Test
	public void cache_notNeeded() throws Exception {
		// Last commit overwrites everything, c1 (in cache) is never visited.
		RevCommit c1 = commit("first", "second", "third");
		RevCommit c2 = commit("a", "b", "c", "d");

		List<CacheRegion> c1Regions = new ArrayList<>(1);
		c1Regions.add(new CacheRegion(FILE, c1, 0, 3));
		InMemoryBlameCache cache = new InMemoryBlameCache();
		cache.put(c1, FILE, c1Regions);

		try (BlameGenerator generator = new BlameGenerator(db, FILE, cache)) {
			generator.push(null, db.resolve(Constants.HEAD));

			assertTrue(generator.next());
			assertEquals(c2, generator.getSourceCommit());
			assertEquals(0, generator.getResultStart());
			assertEquals(4, generator.getResultEnd());

			assertFalse(generator.next());
			assertFalse(generator.getStats().isCacheHit());
			assertEquals(1, generator.getStats().getCandidatesVisited());
			assertEquals(0, cache.hits.size());
		}
	}

	private InMemoryBlameCache blameAndCache(RevCommit commit)
			throws IOException {
		List<CacheRegion> regions = new ArrayList<>();
		try (BlameGenerator generator = new BlameGenerator(db, FILE)) {
			generator.push(null, commit);
			while (generator.next()) {
				regions.add(new CacheRegion(FILE,
						generator.getSourceCommit().toObjectId(),
						generator.getResultStart(),
						generator.getResultEnd()));
			}
		}
		InMemoryBlameCache cache = new InMemoryBlameCache();
		cache.put(commit, FILE, regions);
		return cache;
	}

	private List<EmittedRegion> consume(BlameGenerator generator)
			throws IOException {
		List<EmittedRegion> result = new ArrayList<>();
		while (generator.next()) {
			result.add(new EmittedRegion(
					generator.getSourceCommit().toObjectId(),
					generator.getSourceStart(), generator.getSourceStart(),
					generator.getResultStart(), generator.getResultEnd()));
		}
		return result;
	}

	private RevCommit commit(String... lines) throws Exception {
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

	static class InMemoryBlameCache implements BlameCache {

		record Key(String commitId, String path) {
		}

		Map<Key, List<CacheRegion>> cache = new HashMap<>();

		List<ObjectId> hits = new ArrayList<>();

		@Override
		public List<CacheRegion> get(Repository repo, ObjectId commitId,
				String path) throws IOException {
			List<CacheRegion> result = cache.get(new Key(commitId.name(), path));
			if (result != null) {
				hits.add(commitId);
			}
			return result;
		}

		void put(ObjectId commitId, String path,
				List<CacheRegion> cachedRegions) {
			cache.put(new Key(commitId.name(), path), cachedRegions);
		}
	}

	private record EmittedRegion(ObjectId oid, int sourceStart, int sourceEnd,
			int resultStart, int resultEnd)
			implements Comparable<EmittedRegion> {
		@Override
		public int compareTo(EmittedRegion o) {
			return o.resultEnd - resultStart;
		}
	}

	private static void assertListsEquals(List<EmittedRegion> a,
			List<EmittedRegion> b) {
		assertEquals(a.size(), b.size());
		for (int i = 0; i < a.size(); i++) {
			assertEquals(String.format("List differ in element %d", i),
					a.get(i), b.get(i));
		}
	}
}
