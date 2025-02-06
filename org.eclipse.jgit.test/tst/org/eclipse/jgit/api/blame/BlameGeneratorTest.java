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
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

/** Unit tests of {@link BlameGenerator}. */
public class BlameGeneratorTest extends RepositoryTestCase {
	@Test
	public void testBoundLineDelete() throws Exception {
		try (Git git = new Git(db)) {
			String[] content1 = new String[] { "first", "second" };
			writeTrashFile("file.txt", join(content1));
			git.add().addFilepattern("file.txt").call();
			RevCommit c1 = git.commit().setMessage("create file").call();

			String[] content2 = new String[] { "third", "first", "second" };
			writeTrashFile("file.txt", join(content2));
			git.add().addFilepattern("file.txt").call();
			RevCommit c2 = git.commit().setMessage("create file").call();

			try (BlameGenerator generator = new BlameGenerator(db, "file.txt")) {
				generator.push(null, db.resolve(Constants.HEAD));
				assertEquals(3, generator.getResultContents().size());

				assertTrue(generator.next());
				assertEquals(c2, generator.getSourceCommit());
				assertEquals(1, generator.getRegionLength());
				assertEquals(0, generator.getResultStart());
				assertEquals(1, generator.getResultEnd());
				assertEquals(0, generator.getSourceStart());
				assertEquals(1, generator.getSourceEnd());
				assertEquals("file.txt", generator.getSourcePath());

				assertTrue(generator.next());
				assertEquals(c1, generator.getSourceCommit());
				assertEquals(2, generator.getRegionLength());
				assertEquals(1, generator.getResultStart());
				assertEquals(3, generator.getResultEnd());
				assertEquals(0, generator.getSourceStart());
				assertEquals(2, generator.getSourceEnd());
				assertEquals("file.txt", generator.getSourcePath());

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

			try (BlameGenerator generator = new BlameGenerator(db, FILENAME_2)) {
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
			try (BlameGenerator generator = new BlameGenerator(db, FILENAME_2)) {
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

			writeTrashFile("file.txt", join(content1));
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("create file").call();

			String[] content2 = new String[] { "" };

			writeTrashFile("file.txt", join(content2));
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("create file").call();

			writeTrashFile("file.txt", join(content1));
			git.add().addFilepattern("file.txt").call();
			RevCommit c3 = git.commit().setMessage("create file").call();

			try (BlameGenerator generator = new BlameGenerator(db, "file.txt")) {
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
	public void cache_tipInCache() throws Exception {
		try (Git git = new Git(db)) {
			String[] content1 = new String[] { "first", "second", "third" };
			writeTrashFile("file.txt", join(content1));
			git.add().addFilepattern("file.txt").call();
			RevCommit c1 = git.commit().setMessage("create file").call();

			String[] content2 = new String[] { "first", "second", "third",
					"fourth" };
			writeTrashFile("file.txt", join(content2));
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("add line").call();

			String[] content3 = new String[] { "first", "other2", "other3",
					"fourth" };
			writeTrashFile("file.txt", join(content3));
			git.add().addFilepattern("file.txt").call();
			RevCommit c3 = git.commit().setMessage("change 2 middle lines")
					.call();

			String[] content4 = new String[] { "first", "other22", "other3",
					"other4" };
			writeTrashFile("file.txt", join(content4));
			git.add().addFilepattern("file.txt").call();
			RevCommit c4 = git.commit().setMessage("change 2nd and 4th lines")
					.call();

			String path = "file.txt";
			PersonIdent  author = new PersonIdent("a", "a@b.com");
			List<CacheRegion> c4Regions = new ArrayList<>(4);
			c4Regions.add(new CacheRegion(path, c1, author, 0, 1));
			c4Regions.add(new CacheRegion(path, c4, author, 1, 2));
			c4Regions.add(new CacheRegion(path, c3, author, 2, 3));
			c4Regions.add(new CacheRegion(path, c4, author, 3, 4));

			InMemoryBlameCache cache = new InMemoryBlameCache();
			cache.put(c4, path, c4Regions);
			try (BlameGenerator generator = new BlameGenerator(db, "file.txt",
					cache)) {
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
			}
		}
	}

	@Test
	public void cache_intermediateInCache() throws Exception {
		try (Git git = new Git(db)) {
			String[] content1 = new String[] { "first", "second", "third" };
			writeTrashFile("file.txt", join(content1));
			git.add().addFilepattern("file.txt").call();
			RevCommit c1 = git.commit().setMessage("create file").call();

			String[] content2 = new String[] { "first", "second", "third",
					"fourth" };
			writeTrashFile("file.txt", join(content2));
			git.add().addFilepattern("file.txt").call();
			RevCommit c2 = git.commit().setMessage("add line").call();

			String[] content3 = new String[] { "first", "other2", "other3",
					"fourth" };
			writeTrashFile("file.txt", join(content3));
			git.add().addFilepattern("file.txt").call();
			RevCommit c3 = git.commit().setMessage("change 2 middle lines")
					.call();

			String[] content4 = new String[] { "first", "other22", "other3",
					"other4" };
			writeTrashFile("file.txt", join(content4));
			git.add().addFilepattern("file.txt").call();
			RevCommit c4 = git.commit().setMessage("change 2nd and 4th lines")
					.call();

			String path = "file.txt";
			PersonIdent  author = new PersonIdent("a", "a@b.com");
			List<CacheRegion> c2Regions = new ArrayList<>(4);
			c2Regions.add(new CacheRegion(path, c1, author, 0, 1));
			c2Regions.add(new CacheRegion(path, c2, author, 1, 3));
			c2Regions.add(new CacheRegion(path, c1, author, 3, 4));

			InMemoryBlameCache cache = new InMemoryBlameCache();
			cache.put(c2, path, c2Regions);
			try (BlameGenerator generator = new BlameGenerator(db, "file.txt",
					cache)) {
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
			}
		}
	}

	private static String join(String... lines) {
		StringBuilder joined = new StringBuilder();
		for (String line : lines)
			joined.append(line).append('\n');
		return joined.toString();
	}

	static class InMemoryBlameCache implements BlameCache {

		record Key(String commitId, String path) {};

		Map<Key, List<CacheRegion>> cache = new HashMap<>();

		@Override
		public List<CacheRegion> get(Repository repo, ObjectId commitId, String path) throws IOException {
			return cache.get(new Key(commitId.name(), path));
		}

		void put(ObjectId commitId, String path, List<CacheRegion> cachedRegions) {
			cache.put(new Key(commitId.name(), path), cachedRegions);
		}

		@Override
		public ObjectId findLastCommit(Repository repo, ObjectId commitId, String path) throws IOException {
			return null;
		}
	}
}
