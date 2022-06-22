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

import java.util.Arrays;
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
	@Test
	public void testBoundLineDelete() throws Exception {
		try (Git git = new Git(db);
				RevWalk revWalk = new RevWalk(git.getRepository())) {
			String[] content1 = new String[] { "abc" };
			writeTrashFile("something_else.txt", join(content1));
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("create file").call();

			String[] content2 = new String[] { "abc", "def" };
			writeTrashFile("something_else.txt", join(content2));
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("update file").call();

			String[] content3 = new String[] { "first", "second" };
			writeTrashFile("file.txt", join(content3));
			git.add().addFilepattern("file.txt").call();
			RevCommit c1 = git.commit().setMessage("create file").call();
			c1 = revWalk.parseCommit(c1.getId());

			String[] content4 = new String[] { "abc", "def", "ghi" };
			writeTrashFile("something_else.txt", join(content4));
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("update file").call();

			String[] content5 = new String[] { "abc" };
			writeTrashFile("something_else.txt", join(content5));
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("create file").call();

			String[] content6 = new String[] { "third", "first", "second" };
			writeTrashFile("file.txt", join(content6));
			git.add().addFilepattern("file.txt").call();
			RevCommit c2 = git.commit().setMessage("create file").call();
			c2 = revWalk.parseCommit(c2.getId());

			RevCommit filteredC1 = new FilteredRevCommit(c1);
			RevCommit filteredC2 = new FilteredRevCommit(c2,
					Arrays.asList(filteredC1));

			filteredC1 = revWalk.parseCommit(filteredC1);
			filteredC2 = revWalk.parseCommit(filteredC2);

			try (BlameGenerator generator = new BlameGenerator(db,
					"file.txt")) {
				generator.push(filteredC2);
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
			String[] contentA = new String[] { "first", "second" };
			writeTrashFile("file.txt", join(contentA));
			git.add().addFilepattern("file.txt").call();
			RevCommit c1 = git.commit().setMessage("create file").call();
			c1 = revWalk.parseCommit(c1.getId());

			createBranch(c1, "refs/heads/side");
			checkoutBranch("refs/heads/side");
			String[] contentSide = new String[] { "first", "second", "third",
					"fourth" };
			writeTrashFile("file.txt", join(contentSide));
			git.add().addFilepattern("file.txt").call();
			RevCommit sideCommit = git.commit().setMessage("create file")
					.call();
			sideCommit = revWalk.parseCommit(sideCommit.getId());
			checkoutBranch("refs/heads/master");

			git.merge().setMessage("merge").include(sideCommit)
					.setStrategy(MergeStrategy.RESOLVE).call();

			Iterator<RevCommit> it = git.log().call().iterator();
			RevCommit mergeCommit = it.next();
			mergeCommit = revWalk.parseCommit(mergeCommit.getId());

			RevCommit filteredC1 = new FilteredRevCommit(c1);
			RevCommit filteredSide = new FilteredRevCommit(sideCommit,
					Arrays.asList(filteredC1));
			RevCommit filteredMerge = new FilteredRevCommit(
					mergeCommit, Arrays.asList(filteredSide, filteredC1));

			filteredC1 = revWalk.parseCommit(filteredC1);
			filteredSide = revWalk.parseCommit(filteredSide);
			filteredMerge = revWalk.parseCommit(filteredMerge);

			try (BlameGenerator generator = new BlameGenerator(db,
					"file.txt")) {
				generator.push(filteredMerge);
				assertEquals(4, generator.getResultContents().size());

				assertTrue(generator.next());
				assertEquals(mergeCommit, generator.getSourceCommit());
				assertEquals(2, generator.getRegionLength());
				assertEquals(2, generator.getResultStart());
				assertEquals(4, generator.getResultEnd());
				assertEquals(2, generator.getSourceStart());
				assertEquals(4, generator.getSourceEnd());
				assertEquals("file.txt", generator.getSourcePath());

				assertTrue(generator.next());
				assertEquals(filteredC1, generator.getSourceCommit());
				assertEquals(2, generator.getRegionLength());
				assertEquals(0, generator.getResultStart());
				assertEquals(2, generator.getResultEnd());
				assertEquals(0, generator.getSourceStart());
				assertEquals(2, generator.getSourceEnd());
				assertEquals("file.txt", generator.getSourcePath());

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
			String[] contentA = new String[] { "first", "second" };
			writeTrashFile("file.txt", join(contentA));
			git.add().addFilepattern("file.txt").call();
			RevCommit c1 = git.commit().setMessage("create file").call();
			c1 = revWalk.parseCommit(c1.getId());

			createBranch(c1, "refs/heads/side");
			checkoutBranch("refs/heads/side");
			String[] contentSide = new String[] { "first", "second", "third" };
			writeTrashFile("file.txt", join(contentSide));
			git.add().addFilepattern("file.txt").call();
			RevCommit sideCommit = git.commit().setMessage("create file")
					.call();
			sideCommit = revWalk.parseCommit(sideCommit.getId());

			checkoutBranch("refs/heads/master");
			String[] contentB = new String[] { "first", "second", "fourth" };
			writeTrashFile("file.txt", join(contentB));
			git.add().addFilepattern("file.txt").call();
			RevCommit c2 = git.commit().setMessage("create file").call();
			c2 = revWalk.parseCommit(c2.getId());

			git.merge().setMessage("merge")
					.include(sideCommit).setStrategy(MergeStrategy.RESOLVE)
					.call();
			String[] contentMerge = new String[] { "first", "second", "third",
					"fourth" };
			writeTrashFile("file.txt", join(contentMerge));
			git.add().addFilepattern("file.txt").call();
			RevCommit mergeCommit = git.commit().setMessage("merge commit")
					.call();
			mergeCommit = revWalk.parseCommit(mergeCommit.getId());

//			Iterator<RevCommit> it = git.log().call().iterator();
//			RevCommit mC = it.next();
//			RevCommit m = revWalk.parseCommit(mC.getId());

			RevCommit filteredC1 = new FilteredRevCommit(c1);
			RevCommit filteredSide = new FilteredRevCommit(sideCommit,
					Arrays.asList(filteredC1));
			RevCommit filteredC2 = new FilteredRevCommit(c2,
					Arrays.asList(filteredC1));

			RevCommit filteredMerge = new FilteredRevCommit(
					mergeCommit, Arrays.asList(filteredSide, filteredC2));

			revWalk.updateCommit(filteredC1);
			revWalk.updateCommit(filteredC2);
			revWalk.updateCommit(filteredSide);
			revWalk.updateCommit(filteredMerge);

			filteredC1 = revWalk.parseCommit(filteredC1);
			filteredSide = revWalk.parseCommit(filteredSide);
			filteredC2 = revWalk.parseCommit(filteredC2);
			filteredMerge = revWalk.parseCommit(filteredMerge);

			try (BlameGenerator generator = new BlameGenerator(db,
					"file.txt")) {
				generator.push(filteredMerge);
				assertEquals(4, generator.getResultContents().size());

				assertTrue(generator.next());
				assertEquals(filteredC2, generator.getSourceCommit());
				assertEquals(1, generator.getRegionLength());
				assertEquals(3, generator.getResultStart());
				assertEquals(4, generator.getResultEnd());
				assertEquals(2, generator.getSourceStart());
				assertEquals(3, generator.getSourceEnd());
				assertEquals("file.txt", generator.getSourcePath());

				assertTrue(generator.next());
				assertEquals(filteredSide, generator.getSourceCommit());
				assertEquals(1, generator.getRegionLength());
				assertEquals(2, generator.getResultStart());
				assertEquals(3, generator.getResultEnd());
				assertEquals(2, generator.getSourceStart());
				assertEquals(3, generator.getSourceEnd());
				assertEquals("file.txt", generator.getSourcePath());

				assertTrue(generator.next());
				assertEquals(filteredC1, generator.getSourceCommit());
				assertEquals(2, generator.getRegionLength());
				assertEquals(0, generator.getResultStart());
				assertEquals(2, generator.getResultEnd());
				assertEquals(0, generator.getSourceStart());
				assertEquals(2, generator.getSourceEnd());
				assertEquals("file.txt", generator.getSourcePath());

				assertFalse(generator.next());
			}
		}
	}

	@Test
	public void testBoundLineDelete_walk() throws Exception {
		try (Git git = new Git(db)) {
			String[] content1 = new String[] { "abc" };
			writeTrashFile("something_else.txt", join(content1));
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("create file").call();

			String[] content2 = new String[] { "abc", "def" };
			writeTrashFile("something_else.txt", join(content2));
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("update file").call();

			String[] content3 = new String[] { "first", "second" };
			writeTrashFile("file.txt", join(content3));
			git.add().addFilepattern("file.txt").call();
			RevCommit c1 = git.commit().setMessage("create file").call();

			String[] content4 = new String[] { "abc", "def", "ghi" };
			writeTrashFile("something_else.txt", join(content4));
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("update file").call();

			String[] content5 = new String[] { "abc" };
			writeTrashFile("something_else.txt", join(content5));
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("create file").call();

			String[] content6 = new String[] { "third", "first", "second" };
			writeTrashFile("file.txt", join(content6));
			git.add().addFilepattern("file.txt").call();
			RevCommit c2 = git.commit().setMessage("create file").call();

			try (BlameGenerator generator = new BlameGenerator(db,
					"file.txt")) {
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

			try (BlameGenerator generator = new BlameGenerator(db,
					"file.txt")) {
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
