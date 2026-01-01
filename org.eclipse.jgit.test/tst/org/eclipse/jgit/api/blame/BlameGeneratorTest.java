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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.blame.BlameGenerator;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

/** Unit tests of {@link BlameGenerator}. */
public class BlameGeneratorTest extends RepositoryTestCase {
	private static final String FILE = "file.txt";

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
	public void testBlameIgnoreSingleRevision() throws Exception {
		try (Git git = new Git(db)) {
			String[] content1 = new String[] { "a" };
			writeTrashFile(FILE, join(content1));
			git.add().addFilepattern(FILE).call();
			RevCommit c1 = git.commit().setMessage("c1").call();

			String[] content2 = new String[] { "a", "b" };
			writeTrashFile(FILE, join(content2));
			git.add().addFilepattern(FILE).call();
			RevCommit c2 = git.commit().setMessage("c2").call();

			String[] content3 = new String[] { "a", "b", "c" };
			writeTrashFile(FILE, join(content3));
			git.add().addFilepattern(FILE).call();
			RevCommit c3 = git.commit().setMessage("c3").call();

			try (BlameGenerator generator = new BlameGenerator(db, FILE)) {
				generator.setIgnoreRevs(Collections.singleton(c2));
				generator.push(null, c3);
				BlameResult result = generator.computeBlameResult();

				assertEquals(3, result.getResultContents().size());
				assertEquals(c1, result.getSourceCommit(0));
				assertEquals(c1, result.getSourceCommit(1));
				assertEquals(c3, result.getSourceCommit(2));
			}
		}
	}

	@Test
	public void testBlameIgnoreMultipleRevisions() throws Exception {
		try (Git git = new Git(db)) {
			String[] content1 = new String[] { "1" };
			writeTrashFile(FILE, join(content1));
			git.add().addFilepattern(FILE).call();
			RevCommit c1 = git.commit().setMessage("c1").call();

			String[] content2 = new String[] { "1", "2" };
			writeTrashFile(FILE, join(content2));
			git.add().addFilepattern(FILE).call();
			RevCommit c2 = git.commit().setMessage("c2").call();

			String[] content3 = new String[] { "1", "2", "3" };
			writeTrashFile(FILE, join(content3));
			git.add().addFilepattern(FILE).call();
			RevCommit c3 = git.commit().setMessage("c3").call();

			String[] content4 = new String[] { "1", "2", "3", "4" };
			writeTrashFile(FILE, join(content4));
			git.add().addFilepattern(FILE).call();
			RevCommit c4 = git.commit().setMessage("c4").call();

			try (BlameGenerator generator = new BlameGenerator(db, FILE)) {
				Set<ObjectId> ignores = new HashSet<>();
				ignores.add(c2);
				ignores.add(c3);
				generator.setIgnoreRevs(ignores);
				generator.push(null, c4);

				BlameResult result = generator.computeBlameResult();

				assertEquals(4, result.getResultContents().size());
				assertEquals(c1, result.getSourceCommit(0));
				assertEquals(c1, result.getSourceCommit(1));
				assertEquals(c1, result.getSourceCommit(2));
				assertEquals(c4, result.getSourceCommit(3));
			}
		}
	}

	@Test
	public void testBlameIgnoreNonModifyingRevision() throws Exception {
		try (Git git = new Git(db)) {
			String[] content1 = new String[] { "A" };
			writeTrashFile(FILE, join(content1));
			git.add().addFilepattern(FILE).call();
			RevCommit c1 = git.commit().setMessage("c1").call();

			String[] content2 = new String[] { "A", "B" };
			writeTrashFile(FILE, join(content2));
			git.add().addFilepattern(FILE).call();
			RevCommit c2 = git.commit().setMessage("c2").call();

			String[] content3 = new String[] { "A prime", "B" };
			writeTrashFile(FILE, join(content3));
			git.add().addFilepattern(FILE).call();
			RevCommit c3 = git.commit().setMessage("c3").call();

			try (BlameGenerator generator = new BlameGenerator(db, FILE)) {
				generator.setIgnoreRevs(Collections.singleton(c2));
				generator.push(null, c3);
				BlameResult result = generator.computeBlameResult();

				assertEquals(c3, result.getSourceCommit(0));
				assertEquals(c1, result.getSourceCommit(1));
			}

			try (BlameGenerator generator = new BlameGenerator(db, FILE)) {
				generator.setIgnoreRevs(Collections.singleton(c3));
				generator.push(null, c3);
				BlameResult result = generator.computeBlameResult();

				assertEquals(c1, result.getSourceCommit(0));
			}
		}
	}

	@Test
	public void testBlameIgnoreMergeCommit() throws Exception {
		try (Git git = new Git(db)) {
			String[] content1 = new String[] { "base" };
			writeTrashFile(FILE, join(content1));
			git.add().addFilepattern(FILE).call();
			RevCommit c1 = git.commit().setMessage("c1").call();

			git.checkout().setCreateBranch(true).setName("a").call();
			String[] content2 = new String[] { "base", "a" };
			writeTrashFile(FILE, join(content2));
			git.add().addFilepattern(FILE).call();
			RevCommit c2 = git.commit().setMessage("c2").call();

			git.checkout().setName("master").call();
			String[] content3 = new String[] { "base", "b" };
			writeTrashFile(FILE, join(content3));
			git.add().addFilepattern(FILE).call();
			RevCommit c3 = git.commit().setMessage("c3").call();

			git.merge().include(c2).call();
			writeTrashFile(FILE, join(content2));
			git.add().addFilepattern(FILE).call();
			RevCommit c4 = git.commit().setMessage("c4").call();

			try (BlameGenerator generator = new BlameGenerator(db, FILE)) {
				generator.setIgnoreRevs(Collections.singleton(c2));
				generator.push(null, c4);
				BlameResult result = generator.computeBlameResult();

				assertEquals(c1, result.getSourceCommit(1));
			}

			try (BlameGenerator generator = new BlameGenerator(db, FILE)) {
				generator.setIgnoreRevs(Collections.singleton(c3));
				generator.push(null, c4);
				BlameResult result = generator.computeBlameResult();

				assertEquals(c2, result.getSourceCommit(1));
			}

			try (BlameGenerator generator = new BlameGenerator(db, FILE)) {
				generator.setIgnoreRevs(Collections.singleton(c4));
				generator.push(null, c4);
				BlameResult result = generator.computeBlameResult();

				assertEquals(c2, result.getSourceCommit(1));
			}
		}
	}

	@Test
	public void testBlameIgnoreWithEmptySet() throws Exception {
		try (Git git = new Git(db)) {
			String[] content1 = new String[] { "a" };
			writeTrashFile(FILE, join(content1));
			git.add().addFilepattern(FILE).call();
			RevCommit c1 = git.commit().setMessage("c1").call();

			try (BlameGenerator generator = new BlameGenerator(db, FILE)) {
				generator.setIgnoreRevs(Collections.emptySet());
				generator.push(null, c1);
				BlameResult result = generator.computeBlameResult();
				assertEquals(c1, result.getSourceCommit(0));
			}
		}
	}

	@Test
	public void testBlameIgnoreWithNull() throws Exception {
		try (Git git = new Git(db)) {
			String[] content1 = new String[] { "a" };
			writeTrashFile(FILE, join(content1));
			git.add().addFilepattern(FILE).call();
			RevCommit c1 = git.commit().setMessage("c1").call();

			try (BlameGenerator generator = new BlameGenerator(db, FILE)) {
				generator.setIgnoreRevs(null);
				generator.push(null, c1);
				BlameResult result = generator.computeBlameResult();
				assertEquals(c1, result.getSourceCommit(0));
			}
		}
	}

	private static String join(String... lines) {
		StringBuilder joined = new StringBuilder();
		for (String line : lines)
			joined.append(line).append('\n');
		return joined.toString();
	}
	@Test
	public void testBlameIgnoreMergeCommitMultipleRegions() throws Exception {
		try (Git git = new Git(db)) {
			String[] content1 = new String[] { "1", "2", "3", "4", "5" };
			writeTrashFile(FILE, join(content1));
			git.add().addFilepattern(FILE).call();
			RevCommit c1 = git.commit().setMessage("c1").call();

			git.checkout().setCreateBranch(true).setName("a").call();
			String[] content2 = new String[] { "1_mod", "2", "3", "4", "5" };
			writeTrashFile(FILE, join(content2));
			git.add().addFilepattern(FILE).call();
			RevCommit c2 = git.commit().setMessage("c2").call();

			git.checkout().setName("master").call();
			String[] content3 = new String[] { "1", "2", "3", "4", "5_mod" };
			writeTrashFile(FILE, join(content3));
			git.add().addFilepattern(FILE).call();
			RevCommit c3 = git.commit().setMessage("c3").call();

			git.merge().include(c2).call();
			String[] content4 = new String[] { "1_mod", "2", "3", "4", "5_mod" };
			writeTrashFile(FILE, join(content4));
			git.add().addFilepattern(FILE).call();
			RevCommit c4 = git.commit().setMessage("c4").call();

			try (BlameGenerator generator = new BlameGenerator(db, FILE)) {
				generator.setIgnoreRevs(Collections.singleton(c4));
				generator.push(null, c4);
				BlameResult result = generator.computeBlameResult();

				assertEquals(c2, result.getSourceCommit(0));
				assertEquals(c1, result.getSourceCommit(1));
				assertEquals(c3, result.getSourceCommit(4));
			}
		}
	}

	@Test
	public void testBlameIgnoreWithRename() throws Exception {
		try (Git git = new Git(db)) {
			String oldFile = "OldFile.txt";
			String newFile = "NewFile.txt";

			String[] content1 = new String[] { "line1", "line2", "line3", "line4", "line5" };
			writeTrashFile(oldFile, join(content1));
			git.add().addFilepattern(oldFile).call();
			RevCommit c1 = git.commit().setMessage("c1: create OldFile").call();

			// Rename OldFile.txt to NewFile.txt and modify line1 in c2 (80% similarity)
			git.rm().addFilepattern(oldFile).call();
			String[] content2 = new String[] { "line1 formatted", "line2", "line3", "line4", "line5" };
			writeTrashFile(newFile, join(content2));
			git.add().addFilepattern(newFile).call();
			RevCommit c2 = git.commit().setMessage("c2: rename and format").call();

			// Modify line2 in c3
			String[] content3 = new String[] { "line1 formatted", "line2 modified", "line3", "line4", "line5" };
			writeTrashFile(newFile, join(content3));
			git.add().addFilepattern(newFile).call();
			RevCommit c3 = git.commit().setMessage("c3: edit line2").call();

			try (BlameGenerator generator = new BlameGenerator(db, newFile)) {
				generator.setFollowFileRenames(true);
				generator.setIgnoreRevs(Collections.singleton(c2));
				generator.push(null, c3);
				BlameResult result = generator.computeBlameResult();

				assertEquals(5, result.getResultContents().size());
				assertEquals(c1, result.getSourceCommit(0));
				assertEquals(oldFile, result.getSourcePath(0));
				assertEquals(c3, result.getSourceCommit(1));
				assertEquals(newFile, result.getSourcePath(1));
				assertEquals(c1, result.getSourceCommit(2));
				assertEquals(oldFile, result.getSourcePath(2));
			}
		}
	}

	@Test
	public void testBlameIgnoreWithLineCountChange() throws Exception {
		try (Git git = new Git(db)) {
			String[] content1 = new String[] { "body1", "body2" };
			writeTrashFile(FILE, join(content1));
			git.add().addFilepattern(FILE).call();
			RevCommit c1 = git.commit().setMessage("c1: initial").call();

			// c2 adds 3 header lines and modifies body1
			String[] content2 = new String[] { "// Header 1", "// Header 2", "// Header 3", "body1 formatted", "body2" };
			writeTrashFile(FILE, join(content2));
			git.add().addFilepattern(FILE).call();
			RevCommit c2 = git.commit().setMessage("c2: add header and format").call();

			// c3 adds a footer line
			String[] content3 = new String[] { "// Header 1", "// Header 2", "// Header 3", "body1 formatted", "body2", "footer" };
			writeTrashFile(FILE, join(content3));
			git.add().addFilepattern(FILE).call();
			RevCommit c3 = git.commit().setMessage("c3: add footer").call();

			try (BlameGenerator generator = new BlameGenerator(db, FILE)) {
				generator.setIgnoreRevs(Collections.singleton(c2));
				generator.push(null, c3);
				BlameResult result = generator.computeBlameResult();

				assertEquals(6, result.getResultContents().size());
				assertEquals(c1, result.getSourceCommit(3));
				assertEquals(c1, result.getSourceCommit(4));
				assertEquals(c3, result.getSourceCommit(5));
			}
		}
	}

	@Test
	public void testBlameCommandWithIgnoreRevs() throws Exception {
		try (Git git = new Git(db)) {
			String[] content1 = new String[] { "a" };
			writeTrashFile(FILE, join(content1));
			git.add().addFilepattern(FILE).call();
			RevCommit c1 = git.commit().setMessage("c1").call();

			String[] content2 = new String[] { "a", "b" };
			writeTrashFile(FILE, join(content2));
			git.add().addFilepattern(FILE).call();
			RevCommit c2 = git.commit().setMessage("c2").call();

			String[] content3 = new String[] { "a", "b", "c" };
			writeTrashFile(FILE, join(content3));
			git.add().addFilepattern(FILE).call();
			RevCommit c3 = git.commit().setMessage("c3").call();

			BlameResult result = git.blame()
					.setFilePath(FILE)
					.setStartCommit(c3)
					.setIgnoreRevs(Collections.singleton(c2))
					.call();

			assertEquals(3, result.getResultContents().size());
			assertEquals(c1, result.getSourceCommit(0));
			assertEquals(c1, result.getSourceCommit(1));
			assertEquals(c3, result.getSourceCommit(2));
		}
	}

	@Test
	public void testBlameIgnoreMergeCommitBothBranches() throws Exception {
		try (Git git = new Git(db)) {
			String[] content1 = new String[] { "header", "body", "footer" };
			writeTrashFile(FILE, join(content1));
			git.add().addFilepattern(FILE).call();
			RevCommit c1 = git.commit().setMessage("c1: base").call();

			// Branch 1: modify header
			git.checkout().setName("branch1").setCreateBranch(true).setStartPoint(c1).call();
			String[] content2 = new String[] { "header branch1", "body", "footer" };
			writeTrashFile(FILE, join(content2));
			git.add().addFilepattern(FILE).call();
			RevCommit c2 = git.commit().setMessage("c2: header branch1").call();

			// Branch 2: modify footer
			git.checkout().setName("branch2").setCreateBranch(true).setStartPoint(c1).call();
			String[] content3 = new String[] { "header", "body", "footer branch2" };
			writeTrashFile(FILE, join(content3));
			git.add().addFilepattern(FILE).call();
			RevCommit c3 = git.commit().setMessage("c3: footer branch2").call();

			// Merge branch1 and branch2 into master
			git.checkout().setName("master").call();
			git.merge().include(c2).call();
			git.merge().include(c3).call();
			String[] content4 = new String[] { "header branch1", "body", "footer branch2" };
			writeTrashFile(FILE, join(content4));
			git.add().addFilepattern(FILE).call();
			RevCommit c4 = git.commit().setMessage("c4: merge branch1 and branch2").call();

			try (BlameGenerator generator = new BlameGenerator(db, FILE)) {
				generator.setIgnoreRevs(Collections.singleton(c4));
				generator.push(null, c4);
				BlameResult result = generator.computeBlameResult();

				assertEquals(3, result.getResultContents().size());
				assertEquals(c2, result.getSourceCommit(0));
				assertEquals(c1, result.getSourceCommit(1));
				assertEquals(c3, result.getSourceCommit(2));
			}
		}
	}

	@Test
	public void testBlameIgnoreLargeInsertion() throws Exception {
		try (Git git = new Git(db)) {
			String[] content1 = new String[] { "line1", "line2" };
			writeTrashFile(FILE, join(content1));
			git.add().addFilepattern(FILE).call();
			RevCommit c1 = git.commit().setMessage("c1: base").call();

			String[] content2 = new String[] {
					"line1",
					"ins1", "ins2", "ins3", "ins4", "ins5",
					"ins6", "ins7", "ins8", "ins9", "ins10",
					"line2"
			};
			writeTrashFile(FILE, join(content2));
			git.add().addFilepattern(FILE).call();
			RevCommit c2 = git.commit().setMessage("c2: insert 10 lines").call();

			String[] content3 = new String[] {
					"line1",
					"ins1", "ins2", "ins3", "ins4", "ins5",
					"ins6", "ins7", "ins8", "ins9", "ins10",
					"line2 modified"
			};
			writeTrashFile(FILE, join(content3));
			git.add().addFilepattern(FILE).call();
			RevCommit c3 = git.commit().setMessage("c3: edit line2").call();

			try (BlameGenerator generator = new BlameGenerator(db, FILE)) {
				generator.setIgnoreRevs(Collections.singleton(c2));
				generator.push(null, c3);
				BlameResult result = generator.computeBlameResult();

				assertEquals(12, result.getResultContents().size());
				assertEquals(c1, result.getSourceCommit(0));
				for (int i = 1; i <= 10; i++) {
					assertEquals(c1, result.getSourceCommit(i));
					assertTrue(result.getSourceLine(i) < 2);
				}
				assertEquals(c3, result.getSourceCommit(11));
			}
		}
	}

	@Test
	public void testBlameIgnoreRootCommit() throws Exception {
		try (Git git = new Git(db)) {
			String[] content1 = new String[] { "root line" };
			writeTrashFile(FILE, join(content1));
			git.add().addFilepattern(FILE).call();
			RevCommit c1 = git.commit().setMessage("c1: root").call();

			try (BlameGenerator generator = new BlameGenerator(db, FILE)) {
				generator.setIgnoreRevs(Collections.singleton(c1));
				generator.push(null, c1);
				BlameResult result = generator.computeBlameResult();

				assertEquals(1, result.getResultContents().size());
				assertEquals(c1, result.getSourceCommit(0));
			}
		}
	}

	@Test
	public void testBlameIgnoreMergeCommitWithConflictResolution() throws Exception {
		try (Git git = new Git(db)) {
			String[] content1 = new String[] { "line1", "line2" };
			writeTrashFile(FILE, join(content1));
			git.add().addFilepattern(FILE).call();
			RevCommit c1 = git.commit().setMessage("c1: base").call();

			// Branch 1: modify line1
			git.checkout().setName("branch1").setCreateBranch(true).setStartPoint(c1).call();
			String[] content2 = new String[] { "line1 branch1", "line2" };
			writeTrashFile(FILE, join(content2));
			git.add().addFilepattern(FILE).call();
			RevCommit c2 = git.commit().setMessage("c2: branch1").call();

			// Branch 2: modify line1 differently
			git.checkout().setName("branch2").setCreateBranch(true).setStartPoint(c1).call();
			String[] content3 = new String[] { "line1 branch2", "line2" };
			writeTrashFile(FILE, join(content3));
			git.add().addFilepattern(FILE).call();
			RevCommit c3 = git.commit().setMessage("c3: branch2").call();

			// Merge branch1 and branch2, resolving conflict with new text
			git.checkout().setName("master").call();
			git.merge().include(c2).call();
			git.merge().include(c3).call();
			String[] content4 = new String[] { "line1 conflict resolved", "line2" };
			writeTrashFile(FILE, join(content4));
			git.add().addFilepattern(FILE).call();
			RevCommit c4 = git.commit().setMessage("c4: merge with resolution").call();

			try (BlameGenerator generator = new BlameGenerator(db, FILE)) {
				generator.setIgnoreRevs(Collections.singleton(c4));
				generator.push(null, c4);
				BlameResult result = generator.computeBlameResult();

				assertEquals(2, result.getResultContents().size());
				assertEquals(c2, result.getSourceCommit(0));
				assertEquals(c1, result.getSourceCommit(1));
			}
		}
	}
}
