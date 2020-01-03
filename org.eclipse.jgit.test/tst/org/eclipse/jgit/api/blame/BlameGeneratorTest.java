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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.blame.BlameGenerator;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
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

	private static String join(String... lines) {
		StringBuilder joined = new StringBuilder();
		for (String line : lines)
			joined.append(line).append('\n');
		return joined.toString();
	}
}
