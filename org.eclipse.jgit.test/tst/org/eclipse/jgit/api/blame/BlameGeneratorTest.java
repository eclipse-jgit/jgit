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

import java.util.Iterator;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.blame.BlameGenerator;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.FilteredRevCommit;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

/** Unit tests of {@link BlameGenerator}. */
public class BlameGeneratorTest extends RepositoryTestCase {

	public static final String OTHER_FILE = "other_file.txt";

	public static final String INTERESTING_FILE = "interesting_file.txt";

	@Test
	public void testSingleBlame() throws Exception {

		/**
		 * <pre>
		 * (ts) 	OTHER_FILE			INTERESTING_FILE
		 * 1 		a
		 * 2	 	a, b
		 * 3							1, 2				c1 <--
		 * 4	 	a, b, c										 |
		 * 5							1, 2, 3				c2---
		 * </pre>
		 */
		try (Git git = new Git(db);
				RevWalk revWalk = new RevWalk(git.getRepository())) {
			writeTrashFile(OTHER_FILE, join("a"));
			git.add().addFilepattern(OTHER_FILE).call();
			git.commit().setMessage("create file").call();

			writeTrashFile(OTHER_FILE, join("a", "b"));
			git.add().addFilepattern(OTHER_FILE).call();
			git.commit().setMessage("amend file").call();

			writeTrashFile(INTERESTING_FILE, join("1", "2"));
			git.add().addFilepattern(INTERESTING_FILE).call();
			RevCommit c1 = git.commit().setMessage("create file").call();

			writeTrashFile(OTHER_FILE, join("a", "b", "c"));
			git.add().addFilepattern(OTHER_FILE).call();
			git.commit().setMessage("amend file").call();

			writeTrashFile(INTERESTING_FILE, join("1", "2", "3"));
			git.add().addFilepattern(INTERESTING_FILE).call();
			RevCommit c2 = git.commit().setMessage("amend file").call();

			RevCommit filteredC1 = new FilteredRevCommit(c1);
			RevCommit filteredC2 = new FilteredRevCommit(c2, filteredC1);

			revWalk.parseHeaders(filteredC2);

			try (BlameGenerator generator = new BlameGenerator(db,
					INTERESTING_FILE)) {
				generator.push(filteredC2);
				assertEquals(3, generator.getResultContents().size());

				assertTrue(generator.next());
				assertEquals(c2, generator.getSourceCommit());
				assertEquals(1, generator.getRegionLength());
				assertEquals(2, generator.getResultStart());
				assertEquals(3, generator.getResultEnd());
				assertEquals(2, generator.getSourceStart());
				assertEquals(3, generator.getSourceEnd());
				assertEquals(INTERESTING_FILE, generator.getSourcePath());

				assertTrue(generator.next());
				assertEquals(c1, generator.getSourceCommit());
				assertEquals(2, generator.getRegionLength());
				assertEquals(0, generator.getResultStart());
				assertEquals(2, generator.getResultEnd());
				assertEquals(0, generator.getSourceStart());
				assertEquals(2, generator.getSourceEnd());
				assertEquals(INTERESTING_FILE, generator.getSourcePath());

				assertFalse(generator.next());
			}
		}
	}

	@Test
	public void testMergeSingleBlame() throws Exception {
		try (Git git = new Git(db);
				RevWalk revWalk = new RevWalk(git.getRepository())) {

			/**
			 *
			 *
			 * <pre>
			 *  refs/heads/master
			 *      A
			 *     / \       		 refs/heads/side
			 *    /   ---------------->  side
			 *   /                        |
			 *  merge <-------------------
			 * </pre>
			 */

			writeTrashFile(INTERESTING_FILE, join("1", "2"));
			git.add().addFilepattern(INTERESTING_FILE).call();
			RevCommit c1 = git.commit().setMessage("create file").call();

			createBranch(c1, "refs/heads/side");
			checkoutBranch("refs/heads/side");
			writeTrashFile(INTERESTING_FILE, join("1", "2", "3", "4"));
			git.add().addFilepattern(INTERESTING_FILE).call();
			RevCommit sideCommit = git.commit()
					.setMessage("amend file in another branch").call();

			checkoutBranch("refs/heads/master");
			git.merge().setMessage("merge").include(sideCommit)
					.setStrategy(MergeStrategy.RESOLVE).call();

			Iterator<RevCommit> it = git.log().call().iterator();
			RevCommit mergeCommit = it.next();

			RevCommit filteredC1 = new FilteredRevCommit(c1);
			RevCommit filteredSide = new FilteredRevCommit(sideCommit,
					filteredC1);
			RevCommit filteredMerge = new FilteredRevCommit(mergeCommit,
					filteredSide, filteredC1);

			revWalk.parseHeaders(filteredMerge);

			try (BlameGenerator generator = new BlameGenerator(db,
					INTERESTING_FILE)) {
				generator.push(filteredMerge);
				assertEquals(4, generator.getResultContents().size());

				assertTrue(generator.next());
				assertEquals(mergeCommit, generator.getSourceCommit());
				assertEquals(2, generator.getRegionLength());
				assertEquals(2, generator.getResultStart());
				assertEquals(4, generator.getResultEnd());
				assertEquals(2, generator.getSourceStart());
				assertEquals(4, generator.getSourceEnd());
				assertEquals(INTERESTING_FILE, generator.getSourcePath());

				assertTrue(generator.next());
				assertEquals(filteredC1, generator.getSourceCommit());
				assertEquals(2, generator.getRegionLength());
				assertEquals(0, generator.getResultStart());
				assertEquals(2, generator.getResultEnd());
				assertEquals(0, generator.getSourceStart());
				assertEquals(2, generator.getSourceEnd());
				assertEquals(INTERESTING_FILE, generator.getSourcePath());

				assertFalse(generator.next());
			}
		}
	}

	@Test
	public void testMergeBlame() throws Exception {
		try (Git git = new Git(db);
				RevWalk revWalk = new RevWalk(git.getRepository())) {

			/**
			 *
			 *
			 * <pre>
			 *  refs/heads/master
			 *      A
			 *     / \       		 refs/heads/side
			 *    B   ---------------->  side
			 *   /                        |
			 *  merge <-------------------
			 * </pre>
			 */
			writeTrashFile(INTERESTING_FILE, join("1", "2"));
			git.add().addFilepattern(INTERESTING_FILE).call();
			RevCommit c1 = git.commit().setMessage("create file").call();

			createBranch(c1, "refs/heads/side");
			checkoutBranch("refs/heads/side");
			writeTrashFile(INTERESTING_FILE, join("1", "2", "3"));
			git.add().addFilepattern(INTERESTING_FILE).call();
			RevCommit sideCommit = git.commit().setMessage("amend file").call();

			checkoutBranch("refs/heads/master");
			writeTrashFile(INTERESTING_FILE, join("1", "2", "4"));
			git.add().addFilepattern(INTERESTING_FILE).call();
			RevCommit c2 = git.commit().setMessage("delete and amend file")
					.call();

			git.merge().setMessage("merge").include(sideCommit)
					.setStrategy(MergeStrategy.RESOLVE).call();
			writeTrashFile(INTERESTING_FILE, join("1", "2", "3", "4"));
			git.add().addFilepattern(INTERESTING_FILE).call();
			RevCommit mergeCommit = git.commit().setMessage("merge commit")
					.call();

			RevCommit filteredC1 = new FilteredRevCommit(c1);
			RevCommit filteredSide = new FilteredRevCommit(sideCommit,
					filteredC1);
			RevCommit filteredC2 = new FilteredRevCommit(c2, filteredC1);

			RevCommit filteredMerge = new FilteredRevCommit(mergeCommit,
					filteredSide, filteredC2);

			revWalk.parseHeaders(filteredMerge);

			try (BlameGenerator generator = new BlameGenerator(db,
					INTERESTING_FILE)) {
				generator.push(filteredMerge);
				assertEquals(4, generator.getResultContents().size());

				assertTrue(generator.next());
				assertEquals(filteredC2, generator.getSourceCommit());
				assertEquals(1, generator.getRegionLength());
				assertEquals(3, generator.getResultStart());
				assertEquals(4, generator.getResultEnd());
				assertEquals(2, generator.getSourceStart());
				assertEquals(3, generator.getSourceEnd());
				assertEquals(INTERESTING_FILE, generator.getSourcePath());

				assertTrue(generator.next());
				assertEquals(filteredSide, generator.getSourceCommit());
				assertEquals(1, generator.getRegionLength());
				assertEquals(2, generator.getResultStart());
				assertEquals(3, generator.getResultEnd());
				assertEquals(2, generator.getSourceStart());
				assertEquals(3, generator.getSourceEnd());
				assertEquals(INTERESTING_FILE, generator.getSourcePath());

				assertTrue(generator.next());
				assertEquals(filteredC1, generator.getSourceCommit());
				assertEquals(2, generator.getRegionLength());
				assertEquals(0, generator.getResultStart());
				assertEquals(2, generator.getResultEnd());
				assertEquals(0, generator.getSourceStart());
				assertEquals(2, generator.getSourceEnd());
				assertEquals(INTERESTING_FILE, generator.getSourcePath());

				assertFalse(generator.next());
			}
		}
	}

	@Test
	public void testSingleBlame_compareWithWalk() throws Exception {
		/**
		 * <pre>
		 * (ts) 	OTHER_FILE			INTERESTING_FILE
		 * 1 		a
		 * 2	 	a, b
		 * 3							1, 2				c1 <--
		 * 4	 	a, b, c										 |
		 * 6							3, 1, 2				c2---
		 * </pre>
		 */
		try (Git git = new Git(db);
				RevWalk revWalk = new RevWalk(git.getRepository())) {
			writeTrashFile(OTHER_FILE, join("a"));
			git.add().addFilepattern(OTHER_FILE).call();
			git.commit().setMessage("create file").call();

			writeTrashFile(OTHER_FILE, join("a", "b"));
			git.add().addFilepattern(OTHER_FILE).call();
			git.commit().setMessage("amend file").call();

			writeTrashFile(INTERESTING_FILE, join("1", "2"));
			git.add().addFilepattern(INTERESTING_FILE).call();
			RevCommit c1 = git.commit().setMessage("create file").call();

			writeTrashFile(OTHER_FILE, join("a", "b", "c"));
			git.add().addFilepattern(OTHER_FILE).call();
			git.commit().setMessage("amend file").call();

			writeTrashFile(INTERESTING_FILE, join("3", "1", "2"));
			git.add().addFilepattern(INTERESTING_FILE).call();
			RevCommit c2 = git.commit().setMessage("prepend").call();

			RevCommit filteredC1 = new FilteredRevCommit(c1);
			RevCommit filteredC2 = new FilteredRevCommit(c2, filteredC1);

			revWalk.parseHeaders(filteredC2);

			try (BlameGenerator g1 = new BlameGenerator(db, INTERESTING_FILE);
					BlameGenerator g2 = new BlameGenerator(db,
							INTERESTING_FILE)) {
				g1.push(null, c2);
				g2.push(null, filteredC2);

				assertEquals(g1.getResultContents().size(),
						g2.getResultContents().size()); // 3

				assertTrue(g1.next());
				assertTrue(g2.next());

				assertEquals(g1.getSourceCommit(), g2.getSourceCommit()); // c2
				assertEquals(INTERESTING_FILE, g1.getSourcePath());
				assertEquals(g1.getRegionLength(), g2.getRegionLength()); // 1
				assertEquals(g1.getResultStart(), g2.getResultStart()); // 0
				assertEquals(g1.getResultEnd(), g2.getResultEnd()); // 1
				assertEquals(g1.getSourceStart(), g2.getSourceStart()); // 0
				assertEquals(g1.getSourceEnd(), g2.getSourceEnd()); // 1
				assertEquals(g1.getSourcePath(), g2.getSourcePath()); // INTERESTING_FILE

				assertTrue(g1.next());
				assertTrue(g2.next());

				assertEquals(g1.getSourceCommit(), g2.getSourceCommit()); // c1
				assertEquals(g1.getRegionLength(), g2.getRegionLength()); // 2
				assertEquals(g1.getResultStart(), g2.getResultStart()); // 1
				assertEquals(g1.getResultEnd(), g2.getResultEnd()); // 3
				assertEquals(g1.getSourceStart(), g2.getSourceStart()); // 0
				assertEquals(g1.getSourceEnd(), g2.getSourceEnd()); // 2
				assertEquals(g1.getSourcePath(), g2.getSourcePath()); // INTERESTING_FILE

				assertFalse(g1.next());
				assertFalse(g2.next());
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

			writeTrashFile(INTERESTING_FILE, join(content1));
			git.add().addFilepattern(INTERESTING_FILE).call();
			git.commit().setMessage("create file").call();

			String[] content2 = new String[] { "" };

			writeTrashFile(INTERESTING_FILE, join(content2));
			git.add().addFilepattern(INTERESTING_FILE).call();
			git.commit().setMessage("create file").call();

			writeTrashFile(INTERESTING_FILE, join(content1));
			git.add().addFilepattern(INTERESTING_FILE).call();
			RevCommit c3 = git.commit().setMessage("create file").call();

			try (BlameGenerator generator = new BlameGenerator(db,
					INTERESTING_FILE)) {
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

	private static String join(String... lines) {
		StringBuilder joined = new StringBuilder();
		for (String line : lines)
			joined.append(line).append('\n');
		return joined.toString();
	}
}
