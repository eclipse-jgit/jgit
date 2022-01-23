/*
 * Copyright (C) 2010, Stefan Lay <stefan.lay@sap.com>
 * Copyright (C) 2010-2014, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.eclipse.jgit.lib.Constants.MASTER;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.util.Iterator;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.InvalidMergeHeadsException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRepository.BranchBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.lib.Sets;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.merge.ContentMergeStrategy;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.GitDateFormatter;
import org.eclipse.jgit.util.GitDateFormatter.Format;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class MergeCommandTest extends RepositoryTestCase {

	public static @DataPoints
	MergeStrategy[] mergeStrategies = MergeStrategy.get();

	private GitDateFormatter dateFormatter;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		dateFormatter = new GitDateFormatter(Format.DEFAULT);
	}

	@Test
	public void testMergeInItself() throws Exception {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();

			MergeResult result = git.merge().include(db.exactRef(Constants.HEAD)).call();
			assertEquals(MergeResult.MergeStatus.ALREADY_UP_TO_DATE, result.getMergeStatus());
		}
		// no reflog entry written by merge
		assertEquals("commit (initial): initial commit",
				db
				.getReflogReader(Constants.HEAD).getLastEntry().getComment());
		assertEquals("commit (initial): initial commit",
				db
				.getReflogReader(db.getBranch()).getLastEntry().getComment());
	}

	@Test
	public void testAlreadyUpToDate() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit first = git.commit().setMessage("initial commit").call();
			createBranch(first, "refs/heads/branch1");

			RevCommit second = git.commit().setMessage("second commit").call();
			MergeResult result = git.merge().include(db.exactRef("refs/heads/branch1")).call();
			assertEquals(MergeResult.MergeStatus.ALREADY_UP_TO_DATE, result.getMergeStatus());
			assertEquals(second, result.getNewHead());
		}
		// no reflog entry written by merge
		assertEquals("commit: second commit", db
				.getReflogReader(Constants.HEAD).getLastEntry().getComment());
		assertEquals("commit: second commit", db
				.getReflogReader(db.getBranch()).getLastEntry().getComment());
	}

	@Test
	public void testFastForward() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit first = git.commit().setMessage("initial commit").call();
			createBranch(first, "refs/heads/branch1");

			RevCommit second = git.commit().setMessage("second commit").call();

			checkoutBranch("refs/heads/branch1");

			MergeResult result = git.merge().include(db.exactRef(R_HEADS + MASTER)).call();

			assertEquals(MergeResult.MergeStatus.FAST_FORWARD, result.getMergeStatus());
			assertEquals(second, result.getNewHead());
		}
		assertEquals("merge refs/heads/master: Fast-forward",
				db.getReflogReader(Constants.HEAD).getLastEntry().getComment());
		assertEquals("merge refs/heads/master: Fast-forward",
				db.getReflogReader(db.getBranch()).getLastEntry().getComment());
	}

	@Test
	public void testFastForwardNoCommit() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit first = git.commit().setMessage("initial commit").call();
			createBranch(first, "refs/heads/branch1");

			RevCommit second = git.commit().setMessage("second commit").call();

			checkoutBranch("refs/heads/branch1");

			MergeResult result = git.merge().include(db.exactRef(R_HEADS + MASTER))
					.setCommit(false).call();

			assertEquals(MergeResult.MergeStatus.FAST_FORWARD,
					result.getMergeStatus());
			assertEquals(second, result.getNewHead());
		}
		assertEquals("merge refs/heads/master: Fast-forward", db
				.getReflogReader(Constants.HEAD).getLastEntry().getComment());
		assertEquals("merge refs/heads/master: Fast-forward", db
				.getReflogReader(db.getBranch()).getLastEntry().getComment());
	}

	@Test
	public void testFastForwardWithFiles() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file1", "file1");
			git.add().addFilepattern("file1").call();
			RevCommit first = git.commit().setMessage("initial commit").call();

			assertTrue(new File(db.getWorkTree(), "file1").exists());
			createBranch(first, "refs/heads/branch1");

			writeTrashFile("file2", "file2");
			git.add().addFilepattern("file2").call();
			RevCommit second = git.commit().setMessage("second commit").call();
			assertTrue(new File(db.getWorkTree(), "file2").exists());

			checkoutBranch("refs/heads/branch1");
			assertFalse(new File(db.getWorkTree(), "file2").exists());

			MergeResult result = git.merge().include(db.exactRef(R_HEADS + MASTER)).call();

			assertTrue(new File(db.getWorkTree(), "file1").exists());
			assertTrue(new File(db.getWorkTree(), "file2").exists());
			assertEquals(MergeResult.MergeStatus.FAST_FORWARD, result.getMergeStatus());
			assertEquals(second, result.getNewHead());
		}
		assertEquals("merge refs/heads/master: Fast-forward",
				db.getReflogReader(Constants.HEAD).getLastEntry().getComment());
		assertEquals("merge refs/heads/master: Fast-forward",
				db.getReflogReader(db.getBranch()).getLastEntry().getComment());
	}

	@Test
	public void testMultipleHeads() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file1", "file1");
			git.add().addFilepattern("file1").call();
			RevCommit first = git.commit().setMessage("initial commit").call();
			createBranch(first, "refs/heads/branch1");

			writeTrashFile("file2", "file2");
			git.add().addFilepattern("file2").call();
			RevCommit second = git.commit().setMessage("second commit").call();

			writeTrashFile("file3", "file3");
			git.add().addFilepattern("file3").call();
			git.commit().setMessage("third commit").call();

			checkoutBranch("refs/heads/branch1");
			assertFalse(new File(db.getWorkTree(), "file2").exists());
			assertFalse(new File(db.getWorkTree(), "file3").exists());

			MergeCommand merge = git.merge();
			merge.include(second.getId());
			merge.include(db.exactRef(R_HEADS + MASTER));
			try {
				merge.call();
				fail("Expected exception not thrown when merging multiple heads");
			} catch (InvalidMergeHeadsException e) {
				// expected this exception
			}
		}
	}

	@Theory
	public void testMergeSuccessAllStrategies(MergeStrategy mergeStrategy)
			throws Exception {
		try (Git git = new Git(db)) {
			RevCommit first = git.commit().setMessage("first").call();
			createBranch(first, "refs/heads/side");

			writeTrashFile("a", "a");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("second").call();

			checkoutBranch("refs/heads/side");
			writeTrashFile("b", "b");
			git.add().addFilepattern("b").call();
			git.commit().setMessage("third").call();

			MergeResult result = git.merge().setStrategy(mergeStrategy)
					.include(db.exactRef(R_HEADS + MASTER)).call();
			assertEquals(MergeStatus.MERGED, result.getMergeStatus());
		}
		assertEquals(
				"merge refs/heads/master: Merge made by "
						+ mergeStrategy.getName() + ".",
				db.getReflogReader(Constants.HEAD).getLastEntry().getComment());
		assertEquals(
				"merge refs/heads/master: Merge made by "
						+ mergeStrategy.getName() + ".",
				db.getReflogReader(db.getBranch()).getLastEntry().getComment());
	}

	@Theory
	public void testMergeSuccessAllStrategiesNoCommit(
			MergeStrategy mergeStrategy) throws Exception {
		try (Git git = new Git(db)) {
			RevCommit first = git.commit().setMessage("first").call();
			createBranch(first, "refs/heads/side");

			writeTrashFile("a", "a");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("second").call();

			checkoutBranch("refs/heads/side");
			writeTrashFile("b", "b");
			git.add().addFilepattern("b").call();
			RevCommit thirdCommit = git.commit().setMessage("third").call();

			MergeResult result = git.merge().setStrategy(mergeStrategy)
					.setCommit(false)
					.include(db.exactRef(R_HEADS + MASTER)).call();
			assertEquals(MergeStatus.MERGED_NOT_COMMITTED, result.getMergeStatus());
			assertEquals(db.exactRef(Constants.HEAD).getTarget().getObjectId(),
					thirdCommit.getId());
		}
	}

	@Test
	public void testContentMerge() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			writeTrashFile("b", "1\nb\n3\n");
			writeTrashFile("c/c/c", "1\nc\n3\n");
			git.add().addFilepattern("a").addFilepattern("b")
					.addFilepattern("c/c/c").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			writeTrashFile("a", "1\na(side)\n3\n");
			writeTrashFile("b", "1\nb(side)\n3\n");
			git.add().addFilepattern("a").addFilepattern("b").call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			assertEquals("1\nb(side)\n3\n", read(new File(db.getWorkTree(), "b")));
			checkoutBranch("refs/heads/master");
			assertEquals("1\nb\n3\n", read(new File(db.getWorkTree(), "b")));

			writeTrashFile("a", "1\na(main)\n3\n");
			writeTrashFile("c/c/c", "1\nc(main)\n3\n");
			git.add().addFilepattern("a").addFilepattern("c/c/c").call();
			git.commit().setMessage("main").call();

			MergeResult result = git.merge().include(secondCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE).call();
			assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());

			assertEquals(
					"1\n<<<<<<< HEAD\na(main)\n=======\na(side)\n>>>>>>> 86503e7e397465588cc267b65d778538bffccb83\n3\n",
					read(new File(db.getWorkTree(), "a")));
			assertEquals("1\nb(side)\n3\n", read(new File(db.getWorkTree(), "b")));
			assertEquals("1\nc(main)\n3\n",
					read(new File(db.getWorkTree(), "c/c/c")));

			assertEquals(1, result.getConflicts().size());
			assertEquals(3, result.getConflicts().get("a")[0].length);

			assertEquals(RepositoryState.MERGING, db.getRepositoryState());
		}
	}

	@Test
	public void testContentMergeXtheirs() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			writeTrashFile("b", "1\nb\n3\n");
			writeTrashFile("c/c/c", "1\nc\n3\n");
			git.add().addFilepattern("a").addFilepattern("b")
					.addFilepattern("c/c/c").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			writeTrashFile("a", "1\na(side)\n3\n4\n");
			writeTrashFile("b", "1\nb(side)\n3\n4\n");
			git.add().addFilepattern("a").addFilepattern("b").call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			assertEquals("1\nb(side)\n3\n4\n",
					read(new File(db.getWorkTree(), "b")));
			checkoutBranch("refs/heads/master");
			assertEquals("1\nb\n3\n", read(new File(db.getWorkTree(), "b")));

			writeTrashFile("a", "1\na(main)\n3\n");
			writeTrashFile("c/c/c", "1\nc(main)\n3\n");
			git.add().addFilepattern("a").addFilepattern("c/c/c").call();
			git.commit().setMessage("main").call();

			MergeResult result = git.merge().include(secondCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE)
					.setContentMergeStrategy(ContentMergeStrategy.THEIRS)
					.call();
			assertEquals(MergeStatus.MERGED, result.getMergeStatus());

			assertEquals("1\na(side)\n3\n4\n",
					read(new File(db.getWorkTree(), "a")));
			assertEquals("1\nb(side)\n3\n4\n",
					read(new File(db.getWorkTree(), "b")));
			assertEquals("1\nc(main)\n3\n",
					read(new File(db.getWorkTree(), "c/c/c")));

			assertNull(result.getConflicts());

			assertEquals(RepositoryState.SAFE, db.getRepositoryState());
		}
	}

	@Test
	public void testContentMergeXours() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			writeTrashFile("b", "1\nb\n3\n");
			writeTrashFile("c/c/c", "1\nc\n3\n");
			git.add().addFilepattern("a").addFilepattern("b")
					.addFilepattern("c/c/c").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			writeTrashFile("a", "1\na(side)\n3\n4\n");
			writeTrashFile("b", "1\nb(side)\n3\n4\n");
			git.add().addFilepattern("a").addFilepattern("b").call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			assertEquals("1\nb(side)\n3\n4\n",
					read(new File(db.getWorkTree(), "b")));
			checkoutBranch("refs/heads/master");
			assertEquals("1\nb\n3\n", read(new File(db.getWorkTree(), "b")));

			writeTrashFile("a", "1\na(main)\n3\n");
			writeTrashFile("c/c/c", "1\nc(main)\n3\n");
			git.add().addFilepattern("a").addFilepattern("c/c/c").call();
			git.commit().setMessage("main").call();

			MergeResult result = git.merge().include(secondCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE)
					.setContentMergeStrategy(ContentMergeStrategy.OURS).call();
			assertEquals(MergeStatus.MERGED, result.getMergeStatus());

			assertEquals("1\na(main)\n3\n4\n",
					read(new File(db.getWorkTree(), "a")));
			assertEquals("1\nb(side)\n3\n4\n",
					read(new File(db.getWorkTree(), "b")));
			assertEquals("1\nc(main)\n3\n",
					read(new File(db.getWorkTree(), "c/c/c")));

			assertNull(result.getConflicts());

			assertEquals(RepositoryState.SAFE, db.getRepositoryState());
		}
	}

	@Test
	public void testBinaryContentMerge() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile(".gitattributes", "a binary");
			writeTrashFile("a", "initial");
			git.add().addFilepattern(".").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			writeTrashFile("a", "side");
			git.add().addFilepattern("a").call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			checkoutBranch("refs/heads/master");

			writeTrashFile("a", "main");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("main").call();

			MergeResult result = git.merge().include(secondCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE).call();
			assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());

			assertEquals("main", read(new File(db.getWorkTree(), "a")));

			// Hmmm... there doesn't seem to be a way to figure out which files
			// had a binary conflict from a MergeResult...

			assertEquals(RepositoryState.MERGING, db.getRepositoryState());
		}
	}

	@Test
	public void testBinaryContentMergeXtheirs() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile(".gitattributes", "a binary");
			writeTrashFile("a", "initial");
			git.add().addFilepattern(".").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			writeTrashFile("a", "side");
			git.add().addFilepattern("a").call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			checkoutBranch("refs/heads/master");

			writeTrashFile("a", "main");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("main").call();

			MergeResult result = git.merge().include(secondCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE)
					.setContentMergeStrategy(ContentMergeStrategy.THEIRS)
					.call();
			assertEquals(MergeStatus.MERGED, result.getMergeStatus());

			assertEquals("side", read(new File(db.getWorkTree(), "a")));

			assertNull(result.getConflicts());
			assertEquals(RepositoryState.SAFE, db.getRepositoryState());
		}
	}

	@Test
	public void testBinaryContentMergeXours() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile(".gitattributes", "a binary");
			writeTrashFile("a", "initial");
			git.add().addFilepattern(".").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			writeTrashFile("a", "side");
			git.add().addFilepattern("a").call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			checkoutBranch("refs/heads/master");

			writeTrashFile("a", "main");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("main").call();

			MergeResult result = git.merge().include(secondCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE)
					.setContentMergeStrategy(ContentMergeStrategy.OURS).call();
			assertEquals(MergeStatus.MERGED, result.getMergeStatus());

			assertEquals("main", read(new File(db.getWorkTree(), "a")));

			assertNull(result.getConflicts());
			assertEquals(RepositoryState.SAFE, db.getRepositoryState());
		}
	}

	@Test
	public void testMergeTag() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "a");
			git.add().addFilepattern("a").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			writeTrashFile("b", "b");
			git.add().addFilepattern("b").call();
			RevCommit secondCommit = git.commit().setMessage("side").call();
			Ref tag = git.tag().setAnnotated(true).setMessage("my tag 01")
					.setName("tag01").setObjectId(secondCommit).call();

			checkoutBranch("refs/heads/master");

			writeTrashFile("a", "a2");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("main").call();

			MergeResult result = git.merge().include(tag).setStrategy(MergeStrategy.RESOLVE).call();
			assertEquals(MergeStatus.MERGED, result.getMergeStatus());
		}
	}

	@Test
	public void testMergeMessage() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			git.add().addFilepattern("a").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			writeTrashFile("a", "1\na(side)\n3\n");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("side").call();

			checkoutBranch("refs/heads/master");

			writeTrashFile("a", "1\na(main)\n3\n");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("main").call();

			Ref sideBranch = db.exactRef("refs/heads/side");

			git.merge().include(sideBranch)
					.setStrategy(MergeStrategy.RESOLVE).call();

			assertEquals("Merge branch 'side'\n\n# Conflicts:\n#\ta\n",
					db.readMergeCommitMsg());
		}

	}

	@Test
	public void testMergeNonVersionedPaths() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			writeTrashFile("b", "1\nb\n3\n");
			writeTrashFile("c/c/c", "1\nc\n3\n");
			git.add().addFilepattern("a").addFilepattern("b")
					.addFilepattern("c/c/c").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			writeTrashFile("a", "1\na(side)\n3\n");
			writeTrashFile("b", "1\nb(side)\n3\n");
			git.add().addFilepattern("a").addFilepattern("b").call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			assertEquals("1\nb(side)\n3\n", read(new File(db.getWorkTree(), "b")));
			checkoutBranch("refs/heads/master");
			assertEquals("1\nb\n3\n", read(new File(db.getWorkTree(), "b")));

			writeTrashFile("a", "1\na(main)\n3\n");
			writeTrashFile("c/c/c", "1\nc(main)\n3\n");
			git.add().addFilepattern("a").addFilepattern("c/c/c").call();
			git.commit().setMessage("main").call();

			writeTrashFile("d", "1\nd\n3\n");
			assertTrue(new File(db.getWorkTree(), "e").mkdir());

			MergeResult result = git.merge().include(secondCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE).call();
			assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());

			assertEquals(
					"1\n<<<<<<< HEAD\na(main)\n=======\na(side)\n>>>>>>> 86503e7e397465588cc267b65d778538bffccb83\n3\n",
					read(new File(db.getWorkTree(), "a")));
			assertEquals("1\nb(side)\n3\n", read(new File(db.getWorkTree(), "b")));
			assertEquals("1\nc(main)\n3\n",
					read(new File(db.getWorkTree(), "c/c/c")));
			assertEquals("1\nd\n3\n", read(new File(db.getWorkTree(), "d")));
			File dir = new File(db.getWorkTree(), "e");
			assertTrue(dir.isDirectory());

			assertEquals(1, result.getConflicts().size());
			assertEquals(3, result.getConflicts().get("a")[0].length);

			assertEquals(RepositoryState.MERGING, db.getRepositoryState());
		}
	}

	@Test
	public void testMultipleCreations() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			git.add().addFilepattern("a").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			writeTrashFile("b", "1\nb(side)\n3\n");
			git.add().addFilepattern("b").call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			checkoutBranch("refs/heads/master");

			writeTrashFile("b", "1\nb(main)\n3\n");
			git.add().addFilepattern("b").call();
			git.commit().setMessage("main").call();

			MergeResult result = git.merge().include(secondCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE).call();
			assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());
		}
	}

	@Test
	public void testMultipleCreationsSameContent() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			git.add().addFilepattern("a").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			writeTrashFile("b", "1\nb(1)\n3\n");
			git.add().addFilepattern("b").call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			checkoutBranch("refs/heads/master");

			writeTrashFile("b", "1\nb(1)\n3\n");
			git.add().addFilepattern("b").call();
			git.commit().setMessage("main").call();

			MergeResult result = git.merge().include(secondCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE).call();
			assertEquals(MergeStatus.MERGED, result.getMergeStatus());
			assertEquals("1\nb(1)\n3\n", read(new File(db.getWorkTree(), "b")));
			assertEquals("merge " + secondCommit.getId().getName()
					+ ": Merge made by resolve.", db
					.getReflogReader(Constants.HEAD)
					.getLastEntry().getComment());
			assertEquals("merge " + secondCommit.getId().getName()
					+ ": Merge made by resolve.", db
					.getReflogReader(db.getBranch())
					.getLastEntry().getComment());
		}
	}

	@Test
	public void testSuccessfulContentMerge() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			writeTrashFile("b", "1\nb\n3\n");
			writeTrashFile("c/c/c", "1\nc\n3\n");
			git.add().addFilepattern("a").addFilepattern("b")
					.addFilepattern("c/c/c").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			writeTrashFile("a", "1(side)\na\n3\n");
			writeTrashFile("b", "1\nb(side)\n3\n");
			git.add().addFilepattern("a").addFilepattern("b").call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			assertEquals("1\nb(side)\n3\n", read(new File(db.getWorkTree(), "b")));
			checkoutBranch("refs/heads/master");
			assertEquals("1\nb\n3\n", read(new File(db.getWorkTree(), "b")));

			writeTrashFile("a", "1\na\n3(main)\n");
			writeTrashFile("c/c/c", "1\nc(main)\n3\n");
			git.add().addFilepattern("a").addFilepattern("c/c/c").call();
			RevCommit thirdCommit = git.commit().setMessage("main").call();

			MergeResult result = git.merge().include(secondCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE).call();
			assertEquals(MergeStatus.MERGED, result.getMergeStatus());

			assertEquals("1(side)\na\n3(main)\n", read(new File(db.getWorkTree(),
					"a")));
			assertEquals("1\nb(side)\n3\n", read(new File(db.getWorkTree(), "b")));
			assertEquals("1\nc(main)\n3\n", read(new File(db.getWorkTree(),
					"c/c/c")));

			assertEquals(null, result.getConflicts());

			assertEquals(2, result.getMergedCommits().length);
			assertEquals(thirdCommit, result.getMergedCommits()[0]);
			assertEquals(secondCommit, result.getMergedCommits()[1]);

			Iterator<RevCommit> it = git.log().call().iterator();
			RevCommit newHead = it.next();
			assertEquals(newHead, result.getNewHead());
			assertEquals(2, newHead.getParentCount());
			assertEquals(thirdCommit, newHead.getParent(0));
			assertEquals(secondCommit, newHead.getParent(1));
			assertEquals(
					"Merge commit '3fa334456d236a92db020289fe0bf481d91777b4'",
					newHead.getFullMessage());
			// @TODO fix me
			assertEquals(RepositoryState.SAFE, db.getRepositoryState());
			// test index state
		}
	}

	@Test
	public void testSuccessfulContentMergeNoCommit() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			writeTrashFile("b", "1\nb\n3\n");
			writeTrashFile("c/c/c", "1\nc\n3\n");
			git.add().addFilepattern("a").addFilepattern("b")
					.addFilepattern("c/c/c").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			writeTrashFile("a", "1(side)\na\n3\n");
			writeTrashFile("b", "1\nb(side)\n3\n");
			git.add().addFilepattern("a").addFilepattern("b").call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			assertEquals("1\nb(side)\n3\n", read(new File(db.getWorkTree(), "b")));
			checkoutBranch("refs/heads/master");
			assertEquals("1\nb\n3\n", read(new File(db.getWorkTree(), "b")));

			writeTrashFile("a", "1\na\n3(main)\n");
			writeTrashFile("c/c/c", "1\nc(main)\n3\n");
			git.add().addFilepattern("a").addFilepattern("c/c/c").call();
			RevCommit thirdCommit = git.commit().setMessage("main").call();

			MergeResult result = git.merge().include(secondCommit.getId())
					.setCommit(false)
					.setStrategy(MergeStrategy.RESOLVE).call();
			assertEquals(MergeStatus.MERGED_NOT_COMMITTED, result.getMergeStatus());
			assertEquals(db.exactRef(Constants.HEAD).getTarget().getObjectId(),
					thirdCommit.getId());

			assertEquals("1(side)\na\n3(main)\n", read(new File(db.getWorkTree(),
					"a")));
			assertEquals("1\nb(side)\n3\n", read(new File(db.getWorkTree(), "b")));
			assertEquals("1\nc(main)\n3\n",
					read(new File(db.getWorkTree(), "c/c/c")));

			assertEquals(null, result.getConflicts());

			assertEquals(2, result.getMergedCommits().length);
			assertEquals(thirdCommit, result.getMergedCommits()[0]);
			assertEquals(secondCommit, result.getMergedCommits()[1]);
			assertNull(result.getNewHead());
			assertEquals(RepositoryState.MERGING_RESOLVED, db.getRepositoryState());
		}
	}

	@Test
	public void testSuccessfulContentMergeAndDirtyworkingTree()
			throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			writeTrashFile("b", "1\nb\n3\n");
			writeTrashFile("d", "1\nd\n3\n");
			writeTrashFile("c/c/c", "1\nc\n3\n");
			git.add().addFilepattern("a").addFilepattern("b")
					.addFilepattern("c/c/c").addFilepattern("d").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			writeTrashFile("a", "1(side)\na\n3\n");
			writeTrashFile("b", "1\nb(side)\n3\n");
			git.add().addFilepattern("a").addFilepattern("b").call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			assertEquals("1\nb(side)\n3\n", read(new File(db.getWorkTree(), "b")));
			checkoutBranch("refs/heads/master");
			assertEquals("1\nb\n3\n", read(new File(db.getWorkTree(), "b")));

			writeTrashFile("a", "1\na\n3(main)\n");
			writeTrashFile("c/c/c", "1\nc(main)\n3\n");
			git.add().addFilepattern("a").addFilepattern("c/c/c").call();
			RevCommit thirdCommit = git.commit().setMessage("main").call();

			writeTrashFile("d", "--- dirty ---");
			MergeResult result = git.merge().include(secondCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE).call();
			assertEquals(MergeStatus.MERGED, result.getMergeStatus());

			assertEquals("1(side)\na\n3(main)\n", read(new File(db.getWorkTree(),
					"a")));
			assertEquals("1\nb(side)\n3\n", read(new File(db.getWorkTree(), "b")));
			assertEquals("1\nc(main)\n3\n", read(new File(db.getWorkTree(),
					"c/c/c")));
			assertEquals("--- dirty ---", read(new File(db.getWorkTree(), "d")));

			assertEquals(null, result.getConflicts());

			assertEquals(2, result.getMergedCommits().length);
			assertEquals(thirdCommit, result.getMergedCommits()[0]);
			assertEquals(secondCommit, result.getMergedCommits()[1]);

			Iterator<RevCommit> it = git.log().call().iterator();
			RevCommit newHead = it.next();
			assertEquals(newHead, result.getNewHead());
			assertEquals(2, newHead.getParentCount());
			assertEquals(thirdCommit, newHead.getParent(0));
			assertEquals(secondCommit, newHead.getParent(1));
			assertEquals(
					"Merge commit '064d54d98a4cdb0fed1802a21c656bfda67fe879'",
					newHead.getFullMessage());

			assertEquals(RepositoryState.SAFE, db.getRepositoryState());
		}
	}

	@Test
	public void testSingleDeletion() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			writeTrashFile("b", "1\nb\n3\n");
			writeTrashFile("d", "1\nd\n3\n");
			writeTrashFile("c/c/c", "1\nc\n3\n");
			git.add().addFilepattern("a").addFilepattern("b")
					.addFilepattern("c/c/c").addFilepattern("d").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			assertTrue(new File(db.getWorkTree(), "b").delete());
			git.add().addFilepattern("b").setUpdate(true).call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			assertFalse(new File(db.getWorkTree(), "b").exists());
			checkoutBranch("refs/heads/master");
			assertTrue(new File(db.getWorkTree(), "b").exists());

			writeTrashFile("a", "1\na\n3(main)\n");
			writeTrashFile("c/c/c", "1\nc(main)\n3\n");
			git.add().addFilepattern("a").addFilepattern("c/c/c").call();
			RevCommit thirdCommit = git.commit().setMessage("main").call();

			// We are merging a deletion into our branch
			MergeResult result = git.merge().include(secondCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE).call();
			assertEquals(MergeStatus.MERGED, result.getMergeStatus());

			assertEquals("1\na\n3(main)\n", read(new File(db.getWorkTree(), "a")));
			assertFalse(new File(db.getWorkTree(), "b").exists());
			assertEquals("1\nc(main)\n3\n",
					read(new File(db.getWorkTree(), "c/c/c")));
			assertEquals("1\nd\n3\n", read(new File(db.getWorkTree(), "d")));

			// Do the opposite, be on a branch where we have deleted a file and
			// merge in a old commit where this file was not deleted
			checkoutBranch("refs/heads/side");
			assertFalse(new File(db.getWorkTree(), "b").exists());

			result = git.merge().include(thirdCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE).call();
			assertEquals(MergeStatus.MERGED, result.getMergeStatus());

			assertEquals("1\na\n3(main)\n", read(new File(db.getWorkTree(), "a")));
			assertFalse(new File(db.getWorkTree(), "b").exists());
			assertEquals("1\nc(main)\n3\n",
					read(new File(db.getWorkTree(), "c/c/c")));
			assertEquals("1\nd\n3\n", read(new File(db.getWorkTree(), "d")));
		}
	}

	@Test
	public void testMultipleDeletions() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			git.add().addFilepattern("a").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			assertTrue(new File(db.getWorkTree(), "a").delete());
			git.add().addFilepattern("a").setUpdate(true).call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			assertFalse(new File(db.getWorkTree(), "a").exists());
			checkoutBranch("refs/heads/master");
			assertTrue(new File(db.getWorkTree(), "a").exists());

			assertTrue(new File(db.getWorkTree(), "a").delete());
			git.add().addFilepattern("a").setUpdate(true).call();
			git.commit().setMessage("main").call();

			// We are merging a deletion into our branch
			MergeResult result = git.merge().include(secondCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE).call();
			assertEquals(MergeStatus.MERGED, result.getMergeStatus());
		}
	}

	@Test
	public void testDeletionAndConflict() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			writeTrashFile("b", "1\nb\n3\n");
			writeTrashFile("d", "1\nd\n3\n");
			writeTrashFile("c/c/c", "1\nc\n3\n");
			git.add().addFilepattern("a").addFilepattern("b")
					.addFilepattern("c/c/c").addFilepattern("d").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			assertTrue(new File(db.getWorkTree(), "b").delete());
			writeTrashFile("a", "1\na\n3(side)\n");
			git.add().addFilepattern("b").setUpdate(true).call();
			git.add().addFilepattern("a").setUpdate(true).call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			assertFalse(new File(db.getWorkTree(), "b").exists());
			checkoutBranch("refs/heads/master");
			assertTrue(new File(db.getWorkTree(), "b").exists());

			writeTrashFile("a", "1\na\n3(main)\n");
			writeTrashFile("c/c/c", "1\nc(main)\n3\n");
			git.add().addFilepattern("a").addFilepattern("c/c/c").call();
			git.commit().setMessage("main").call();

			// We are merging a deletion into our branch
			MergeResult result = git.merge().include(secondCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE).call();
			assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());

			assertEquals(
					"1\na\n<<<<<<< HEAD\n3(main)\n=======\n3(side)\n>>>>>>> 54ffed45d62d252715fc20e41da92d44c48fb0ff\n",
					read(new File(db.getWorkTree(), "a")));
			assertFalse(new File(db.getWorkTree(), "b").exists());
			assertEquals("1\nc(main)\n3\n",
					read(new File(db.getWorkTree(), "c/c/c")));
			assertEquals("1\nd\n3\n", read(new File(db.getWorkTree(), "d")));
		}
	}

	@Test
	public void testDeletionOnMasterConflict() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			writeTrashFile("b", "1\nb\n3\n");
			git.add().addFilepattern("a").addFilepattern("b").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			// create side branch and modify "a"
			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");
			writeTrashFile("a", "1\na(side)\n3\n");
			git.add().addFilepattern("a").call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			// delete a on master to generate conflict
			checkoutBranch("refs/heads/master");
			git.rm().addFilepattern("a").call();
			RevCommit thirdCommit = git.commit().setMessage("main").call();

			for (ContentMergeStrategy contentStrategy : ContentMergeStrategy
					.values()) {
				// merge side with master
				MergeResult result = git.merge().include(secondCommit.getId())
						.setStrategy(MergeStrategy.RESOLVE)
						.setContentMergeStrategy(contentStrategy)
						.call();
				assertEquals("merge -X " + contentStrategy.name(),
						MergeStatus.CONFLICTING, result.getMergeStatus());

				// result should be 'a' conflicting with workspace content from
				// side
				assertTrue("merge -X " + contentStrategy.name(),
						new File(db.getWorkTree(), "a").exists());
				assertEquals("merge -X " + contentStrategy.name(),
						"1\na(side)\n3\n",
						read(new File(db.getWorkTree(), "a")));
				assertEquals("merge -X " + contentStrategy.name(), "1\nb\n3\n",
						read(new File(db.getWorkTree(), "b")));
				git.reset().setMode(ResetType.HARD).setRef(thirdCommit.name())
						.call();
			}
		}
	}

	@Test
	public void testDeletionOnMasterTheirs() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			writeTrashFile("b", "1\nb\n3\n");
			git.add().addFilepattern("a").addFilepattern("b").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			// create side branch and modify "a"
			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");
			writeTrashFile("a", "1\na(side)\n3\n");
			git.add().addFilepattern("a").call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			// delete a on master to generate conflict
			checkoutBranch("refs/heads/master");
			git.rm().addFilepattern("a").call();
			git.commit().setMessage("main").call();

			// merge side with master
			MergeResult result = git.merge().include(secondCommit.getId())
					.setStrategy(MergeStrategy.THEIRS)
					.call();
			assertEquals(MergeStatus.MERGED, result.getMergeStatus());

			// result should be 'a'
			assertTrue(new File(db.getWorkTree(), "a").exists());
			assertEquals("1\na(side)\n3\n",
					read(new File(db.getWorkTree(), "a")));
			assertEquals("1\nb\n3\n", read(new File(db.getWorkTree(), "b")));
			assertTrue(git.status().call().isClean());
		}
	}

	@Test
	public void testDeletionOnMasterOurs() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			writeTrashFile("b", "1\nb\n3\n");
			git.add().addFilepattern("a").addFilepattern("b").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			// create side branch and modify "a"
			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");
			writeTrashFile("a", "1\na(side)\n3\n");
			git.add().addFilepattern("a").call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			// delete a on master to generate conflict
			checkoutBranch("refs/heads/master");
			git.rm().addFilepattern("a").call();
			git.commit().setMessage("main").call();

			// merge side with master
			MergeResult result = git.merge().include(secondCommit.getId())
					.setStrategy(MergeStrategy.OURS).call();
			assertEquals(MergeStatus.MERGED, result.getMergeStatus());

			assertFalse(new File(db.getWorkTree(), "a").exists());
			assertEquals("1\nb\n3\n", read(new File(db.getWorkTree(), "b")));
			assertTrue(git.status().call().isClean());
		}
	}

	@Test
	public void testDeletionOnSideConflict() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			writeTrashFile("b", "1\nb\n3\n");
			git.add().addFilepattern("a").addFilepattern("b").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			// create side branch and delete "a"
			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");
			git.rm().addFilepattern("a").call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			// update a on master to generate conflict
			checkoutBranch("refs/heads/master");
			writeTrashFile("a", "1\na(main)\n3\n");
			git.add().addFilepattern("a").call();
			RevCommit thirdCommit = git.commit().setMessage("main").call();

			for (ContentMergeStrategy contentStrategy : ContentMergeStrategy
					.values()) {
				// merge side with master
				MergeResult result = git.merge().include(secondCommit.getId())
						.setStrategy(MergeStrategy.RESOLVE)
						.setContentMergeStrategy(contentStrategy)
						.call();
				assertEquals("merge -X " + contentStrategy.name(),
						MergeStatus.CONFLICTING, result.getMergeStatus());

				assertTrue("merge -X " + contentStrategy.name(),
						new File(db.getWorkTree(), "a").exists());
				assertEquals("merge -X " + contentStrategy.name(),
						"1\na(main)\n3\n",
						read(new File(db.getWorkTree(), "a")));
				assertEquals("merge -X " + contentStrategy.name(), "1\nb\n3\n",
						read(new File(db.getWorkTree(), "b")));

				assertNotNull("merge -X " + contentStrategy.name(),
						result.getConflicts());
				assertEquals("merge -X " + contentStrategy.name(), 1,
						result.getConflicts().size());
				assertEquals("merge -X " + contentStrategy.name(), 3,
						result.getConflicts().get("a")[0].length);
				git.reset().setMode(ResetType.HARD).setRef(thirdCommit.name())
						.call();
			}
		}
	}

	@Test
	public void testDeletionOnSideTheirs() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			writeTrashFile("b", "1\nb\n3\n");
			git.add().addFilepattern("a").addFilepattern("b").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			// create side branch and delete "a"
			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");
			git.rm().addFilepattern("a").call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			// update a on master to generate conflict
			checkoutBranch("refs/heads/master");
			writeTrashFile("a", "1\na(main)\n3\n");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("main").call();

			// merge side with master
			MergeResult result = git.merge().include(secondCommit.getId())
					.setStrategy(MergeStrategy.THEIRS).call();
			assertEquals(MergeStatus.MERGED, result.getMergeStatus());

			assertFalse(new File(db.getWorkTree(), "a").exists());
			assertEquals("1\nb\n3\n", read(new File(db.getWorkTree(), "b")));
			assertTrue(git.status().call().isClean());
		}
	}

	@Test
	public void testDeletionOnSideOurs() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			writeTrashFile("b", "1\nb\n3\n");
			git.add().addFilepattern("a").addFilepattern("b").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			// create side branch and delete "a"
			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");
			git.rm().addFilepattern("a").call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			// update a on master to generate conflict
			checkoutBranch("refs/heads/master");
			writeTrashFile("a", "1\na(main)\n3\n");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("main").call();

			// merge side with master
			MergeResult result = git.merge().include(secondCommit.getId())
					.setStrategy(MergeStrategy.OURS).call();
			assertEquals(MergeStatus.MERGED, result.getMergeStatus());

			assertTrue(new File(db.getWorkTree(), "a").exists());
			assertEquals("1\na(main)\n3\n",
					read(new File(db.getWorkTree(), "a")));
			assertEquals("1\nb\n3\n", read(new File(db.getWorkTree(), "b")));
			assertTrue(git.status().call().isClean());
		}
	}

	@Test
	public void testModifiedAndRenamed() throws Exception {
		// this test is essentially the same as testDeletionOnSideConflict,
		// however if once rename support is added this test should result in a
		// successful merge instead of a conflict
		try (Git git = new Git(db)) {
			writeTrashFile("x", "add x");
			git.add().addFilepattern("x").call();
			RevCommit initial = git.commit().setMessage("add x").call();

			createBranch(initial, "refs/heads/d1");
			createBranch(initial, "refs/heads/d2");

			// rename x to y on d1
			checkoutBranch("refs/heads/d1");
			new File(db.getWorkTree(), "x")
					.renameTo(new File(db.getWorkTree(), "y"));
			git.rm().addFilepattern("x").call();
			git.add().addFilepattern("y").call();
			RevCommit d1Commit = git.commit().setMessage("d1 rename x -> y").call();

			checkoutBranch("refs/heads/d2");
			writeTrashFile("x", "d2 change");
			git.add().addFilepattern("x").call();
			RevCommit d2Commit = git.commit().setMessage("d2 change in x").call();

			checkoutBranch("refs/heads/master");
			MergeResult d1Merge = git.merge().include(d1Commit).call();
			assertEquals(MergeResult.MergeStatus.FAST_FORWARD,
					d1Merge.getMergeStatus());

			MergeResult d2Merge = git.merge().include(d2Commit).call();
			assertEquals(MergeResult.MergeStatus.CONFLICTING,
					d2Merge.getMergeStatus());
			assertEquals(1, d2Merge.getConflicts().size());
			assertEquals(3, d2Merge.getConflicts().get("x")[0].length);
		}
	}

	@Test
	public void testMergeFailingWithDirtyWorkingTree() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			writeTrashFile("b", "1\nb\n3\n");
			git.add().addFilepattern("a").addFilepattern("b").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			writeTrashFile("a", "1(side)\na\n3\n");
			writeTrashFile("b", "1\nb(side)\n3\n");
			git.add().addFilepattern("a").addFilepattern("b").call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			assertEquals("1\nb(side)\n3\n", read(new File(db.getWorkTree(), "b")));
			checkoutBranch("refs/heads/master");
			assertEquals("1\nb\n3\n", read(new File(db.getWorkTree(), "b")));

			writeTrashFile("a", "1\na\n3(main)\n");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("main").call();

			writeTrashFile("a", "--- dirty ---");
			MergeResult result = git.merge().include(secondCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE).call();

			assertEquals(MergeStatus.FAILED, result.getMergeStatus());

			assertEquals("--- dirty ---", read(new File(db.getWorkTree(), "a")));
			assertEquals("1\nb\n3\n", read(new File(db.getWorkTree(), "b")));

			assertEquals(null, result.getConflicts());

			assertEquals(RepositoryState.SAFE, db.getRepositoryState());
		}
	}

	@Test
	public void testMergeConflictFileFolder() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			writeTrashFile("b", "1\nb\n3\n");
			git.add().addFilepattern("a").addFilepattern("b").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			writeTrashFile("c/c/c", "1\nc(side)\n3\n");
			writeTrashFile("d", "1\nd(side)\n3\n");
			git.add().addFilepattern("c/c/c").addFilepattern("d").call();
			RevCommit secondCommit = git.commit().setMessage("side").call();

			checkoutBranch("refs/heads/master");

			writeTrashFile("c", "1\nc(main)\n3\n");
			writeTrashFile("d/d/d", "1\nd(main)\n3\n");
			git.add().addFilepattern("c").addFilepattern("d/d/d").call();
			git.commit().setMessage("main").call();

			MergeResult result = git.merge().include(secondCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE).call();

			assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());

			assertEquals("1\na\n3\n", read(new File(db.getWorkTree(), "a")));
			assertEquals("1\nb\n3\n", read(new File(db.getWorkTree(), "b")));
			assertEquals("1\nc(main)\n3\n", read(new File(db.getWorkTree(), "c")));
			assertEquals("1\nd(main)\n3\n", read(new File(db.getWorkTree(), "d/d/d")));

			assertEquals(null, result.getConflicts());

			assertEquals(RepositoryState.MERGING, db.getRepositoryState());
		}
	}

	@Test
	public void testSuccessfulMergeFailsDueToDirtyIndex() throws Exception {
		try (Git git = new Git(db)) {
			File fileA = writeTrashFile("a", "a");
			RevCommit initialCommit = addAllAndCommit(git);

			// switch branch
			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");
			// modify file a
			write(fileA, "a(side)");
			writeTrashFile("b", "b");
			RevCommit sideCommit = addAllAndCommit(git);

			// switch branch
			checkoutBranch("refs/heads/master");
			writeTrashFile("c", "c");
			addAllAndCommit(git);

			// modify and add file a
			write(fileA, "a(modified)");
			git.add().addFilepattern("a").call();
			// do not commit

			// get current index state
			String indexState = indexState(CONTENT);

			// merge
			MergeResult result = git.merge().include(sideCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE).call();

			checkMergeFailedResult(result, MergeFailureReason.DIRTY_INDEX,
					indexState, fileA);
		}
	}

	@Test
	public void testConflictingMergeFailsDueToDirtyIndex() throws Exception {
		try (Git git = new Git(db)) {
			File fileA = writeTrashFile("a", "a");
			RevCommit initialCommit = addAllAndCommit(git);

			// switch branch
			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");
			// modify file a
			write(fileA, "a(side)");
			writeTrashFile("b", "b");
			RevCommit sideCommit = addAllAndCommit(git);

			// switch branch
			checkoutBranch("refs/heads/master");
			// modify file a - this will cause a conflict during merge
			write(fileA, "a(master)");
			writeTrashFile("c", "c");
			addAllAndCommit(git);

			// modify and add file a
			write(fileA, "a(modified)");
			git.add().addFilepattern("a").call();
			// do not commit

			// get current index state
			String indexState = indexState(CONTENT);

			// merge
			MergeResult result = git.merge().include(sideCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE).call();

			checkMergeFailedResult(result, MergeFailureReason.DIRTY_INDEX,
					indexState, fileA);
		}
	}

	@Test
	public void testSuccessfulMergeFailsDueToDirtyWorktree() throws Exception {
		try (Git git = new Git(db)) {
			File fileA = writeTrashFile("a", "a");
			RevCommit initialCommit = addAllAndCommit(git);

			// switch branch
			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");
			// modify file a
			write(fileA, "a(side)");
			writeTrashFile("b", "b");
			RevCommit sideCommit = addAllAndCommit(git);

			// switch branch
			checkoutBranch("refs/heads/master");
			writeTrashFile("c", "c");
			addAllAndCommit(git);

			// modify file a
			write(fileA, "a(modified)");
			// do not add and commit

			// get current index state
			String indexState = indexState(CONTENT);

			// merge
			MergeResult result = git.merge().include(sideCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE).call();

			checkMergeFailedResult(result, MergeFailureReason.DIRTY_WORKTREE,
					indexState, fileA);
		}
	}

	@Test
	public void testConflictingMergeFailsDueToDirtyWorktree() throws Exception {
		try (Git git = new Git(db)) {
			File fileA = writeTrashFile("a", "a");
			RevCommit initialCommit = addAllAndCommit(git);

			// switch branch
			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");
			// modify file a
			write(fileA, "a(side)");
			writeTrashFile("b", "b");
			RevCommit sideCommit = addAllAndCommit(git);

			// switch branch
			checkoutBranch("refs/heads/master");
			// modify file a - this will cause a conflict during merge
			write(fileA, "a(master)");
			writeTrashFile("c", "c");
			addAllAndCommit(git);

			// modify file a
			write(fileA, "a(modified)");
			// do not add and commit

			// get current index state
			String indexState = indexState(CONTENT);

			// merge
			MergeResult result = git.merge().include(sideCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE).call();

			checkMergeFailedResult(result, MergeFailureReason.DIRTY_WORKTREE,
					indexState, fileA);
		}
	}

	@Test
	public void testMergeRemovingFolders() throws Exception {
		File folder1 = new File(db.getWorkTree(), "folder1");
		File folder2 = new File(db.getWorkTree(), "folder2");
		FileUtils.mkdir(folder1);
		FileUtils.mkdir(folder2);
		File file = new File(folder1, "file1.txt");
		write(file, "folder1--file1.txt");
		file = new File(folder1, "file2.txt");
		write(file, "folder1--file2.txt");
		file = new File(folder2, "file1.txt");
		write(file, "folder--file1.txt");
		file = new File(folder2, "file2.txt");
		write(file, "folder2--file2.txt");

		try (Git git = new Git(db)) {
			git.add().addFilepattern(folder1.getName())
					.addFilepattern(folder2.getName()).call();
			RevCommit commit1 = git.commit().setMessage("adding folders").call();

			recursiveDelete(folder1);
			recursiveDelete(folder2);
			git.rm().addFilepattern("folder1/file1.txt")
					.addFilepattern("folder1/file2.txt")
					.addFilepattern("folder2/file1.txt")
					.addFilepattern("folder2/file2.txt").call();
			RevCommit commit2 = git.commit()
					.setMessage("removing folders on 'branch'").call();

			git.checkout().setName(commit1.name()).call();

			MergeResult result = git.merge().include(commit2.getId())
					.setStrategy(MergeStrategy.RESOLVE).call();
			assertEquals(MergeResult.MergeStatus.FAST_FORWARD,
					result.getMergeStatus());
			assertEquals(commit2, result.getNewHead());
			assertFalse(folder1.exists());
			assertFalse(folder2.exists());
		}
	}

	@Test
	public void testMergeRemovingFoldersWithoutFastForward() throws Exception {
		File folder1 = new File(db.getWorkTree(), "folder1");
		File folder2 = new File(db.getWorkTree(), "folder2");
		FileUtils.mkdir(folder1);
		FileUtils.mkdir(folder2);
		File file = new File(folder1, "file1.txt");
		write(file, "folder1--file1.txt");
		file = new File(folder1, "file2.txt");
		write(file, "folder1--file2.txt");
		file = new File(folder2, "file1.txt");
		write(file, "folder--file1.txt");
		file = new File(folder2, "file2.txt");
		write(file, "folder2--file2.txt");

		try (Git git = new Git(db)) {
			git.add().addFilepattern(folder1.getName())
					.addFilepattern(folder2.getName()).call();
			RevCommit base = git.commit().setMessage("adding folders").call();

			recursiveDelete(folder1);
			recursiveDelete(folder2);
			git.rm().addFilepattern("folder1/file1.txt")
					.addFilepattern("folder1/file2.txt")
					.addFilepattern("folder2/file1.txt")
					.addFilepattern("folder2/file2.txt").call();
			RevCommit other = git.commit()
					.setMessage("removing folders on 'branch'").call();

			git.checkout().setName(base.name()).call();

			file = new File(folder2, "file3.txt");
			write(file, "folder2--file3.txt");

			git.add().addFilepattern(folder2.getName()).call();
			git.commit().setMessage("adding another file").call();

			MergeResult result = git.merge().include(other.getId())
					.setStrategy(MergeStrategy.RESOLVE).call();

			assertEquals(MergeResult.MergeStatus.MERGED,
					result.getMergeStatus());
			assertFalse(folder1.exists());
		}
	}

	@Test
	public void testFileModeMerge() throws Exception {
		// Only Java6
		assumeTrue(FS.DETECTED.supportsExecute());
		try (Git git = new Git(db)) {
			writeTrashFile("mergeableMode", "a");
			setExecutable(git, "mergeableMode", false);
			writeTrashFile("conflictingModeWithBase", "a");
			setExecutable(git, "conflictingModeWithBase", false);
			RevCommit initialCommit = addAllAndCommit(git);

			// switch branch
			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");
			setExecutable(git, "mergeableMode", true);
			writeTrashFile("conflictingModeNoBase", "b");
			setExecutable(git, "conflictingModeNoBase", true);
			RevCommit sideCommit = addAllAndCommit(git);

			// switch branch
			createBranch(initialCommit, "refs/heads/side2");
			checkoutBranch("refs/heads/side2");
			setExecutable(git, "mergeableMode", false);
			assertFalse(new File(git.getRepository().getWorkTree(),
					"conflictingModeNoBase").exists());
			writeTrashFile("conflictingModeNoBase", "b");
			setExecutable(git, "conflictingModeNoBase", false);
			addAllAndCommit(git);

			// merge
			MergeResult result = git.merge().include(sideCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE).call();
			assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());
			assertTrue(canExecute(git, "mergeableMode"));
			assertFalse(canExecute(git, "conflictingModeNoBase"));
		}
	}

	@Test
	public void testFileModeMergeWithDirtyWorkTree() throws Exception {
		// Only Java6 (or set x bit in index)
		assumeTrue(FS.DETECTED.supportsExecute());

		try (Git git = new Git(db)) {
			writeTrashFile("mergeableButDirty", "a");
			setExecutable(git, "mergeableButDirty", false);
			RevCommit initialCommit = addAllAndCommit(git);

			// switch branch
			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");
			setExecutable(git, "mergeableButDirty", true);
			RevCommit sideCommit = addAllAndCommit(git);

			// switch branch
			createBranch(initialCommit, "refs/heads/side2");
			checkoutBranch("refs/heads/side2");
			setExecutable(git, "mergeableButDirty", false);
			addAllAndCommit(git);

			writeTrashFile("mergeableButDirty", "b");

			// merge
			MergeResult result = git.merge().include(sideCommit.getId())
					.setStrategy(MergeStrategy.RESOLVE).call();
			assertEquals(MergeStatus.FAILED, result.getMergeStatus());
			assertFalse(canExecute(git, "mergeableButDirty"));
		}
	}

	@Test
	public void testSquashFastForward() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file1", "file1");
			git.add().addFilepattern("file1").call();
			RevCommit first = git.commit().setMessage("initial commit").call();

			assertTrue(new File(db.getWorkTree(), "file1").exists());
			createBranch(first, "refs/heads/branch1");
			checkoutBranch("refs/heads/branch1");

			writeTrashFile("file2", "file2");
			git.add().addFilepattern("file2").call();
			RevCommit second = git.commit().setMessage("second commit").call();
			assertTrue(new File(db.getWorkTree(), "file2").exists());

			writeTrashFile("file3", "file3");
			git.add().addFilepattern("file3").call();
			RevCommit third = git.commit().setMessage("third commit").call();
			assertTrue(new File(db.getWorkTree(), "file3").exists());

			checkoutBranch("refs/heads/master");
			assertTrue(new File(db.getWorkTree(), "file1").exists());
			assertFalse(new File(db.getWorkTree(), "file2").exists());
			assertFalse(new File(db.getWorkTree(), "file3").exists());

			MergeResult result = git.merge()
					.include(db.exactRef("refs/heads/branch1"))
					.setSquash(true)
					.call();

			assertTrue(new File(db.getWorkTree(), "file1").exists());
			assertTrue(new File(db.getWorkTree(), "file2").exists());
			assertTrue(new File(db.getWorkTree(), "file3").exists());
			assertEquals(MergeResult.MergeStatus.FAST_FORWARD_SQUASHED,
					result.getMergeStatus());
			assertEquals(first, result.getNewHead()); // HEAD didn't move
			assertEquals(first, db.resolve(Constants.HEAD + "^{commit}"));

			assertEquals(
					"Squashed commit of the following:\n\ncommit "
							+ third.getName()
							+ "\nAuthor: "
							+ third.getAuthorIdent().getName()
							+ " <"
							+ third.getAuthorIdent().getEmailAddress()
							+ ">\nDate:   "
							+ dateFormatter.formatDate(third
									.getAuthorIdent())
							+ "\n\n\tthird commit\n\ncommit "
							+ second.getName()
							+ "\nAuthor: "
							+ second.getAuthorIdent().getName()
							+ " <"
							+ second.getAuthorIdent().getEmailAddress()
							+ ">\nDate:   "
							+ dateFormatter.formatDate(second
									.getAuthorIdent()) + "\n\n\tsecond commit\n",
					db.readSquashCommitMsg());
			assertNull(db.readMergeCommitMsg());

			Status stat = git.status().call();
			assertEquals(Sets.of("file2", "file3"), stat.getAdded());
		}
	}

	@Test
	public void testSquashMerge() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file1", "file1");
			git.add().addFilepattern("file1").call();
			RevCommit first = git.commit().setMessage("initial commit").call();

			assertTrue(new File(db.getWorkTree(), "file1").exists());
			createBranch(first, "refs/heads/branch1");

			writeTrashFile("file2", "file2");
			git.add().addFilepattern("file2").call();
			RevCommit second = git.commit().setMessage("second commit").call();
			assertTrue(new File(db.getWorkTree(), "file2").exists());

			checkoutBranch("refs/heads/branch1");

			writeTrashFile("file3", "file3");
			git.add().addFilepattern("file3").call();
			RevCommit third = git.commit().setMessage("third commit").call();
			assertTrue(new File(db.getWorkTree(), "file3").exists());

			checkoutBranch("refs/heads/master");
			assertTrue(new File(db.getWorkTree(), "file1").exists());
			assertTrue(new File(db.getWorkTree(), "file2").exists());
			assertFalse(new File(db.getWorkTree(), "file3").exists());

			MergeResult result = git.merge()
					.include(db.exactRef("refs/heads/branch1"))
					.setSquash(true)
					.call();

			assertTrue(new File(db.getWorkTree(), "file1").exists());
			assertTrue(new File(db.getWorkTree(), "file2").exists());
			assertTrue(new File(db.getWorkTree(), "file3").exists());
			assertEquals(MergeResult.MergeStatus.MERGED_SQUASHED,
					result.getMergeStatus());
			assertEquals(second, result.getNewHead()); // HEAD didn't move
			assertEquals(second, db.resolve(Constants.HEAD + "^{commit}"));

			assertEquals(
					"Squashed commit of the following:\n\ncommit "
							+ third.getName()
							+ "\nAuthor: "
							+ third.getAuthorIdent().getName()
							+ " <"
							+ third.getAuthorIdent().getEmailAddress()
							+ ">\nDate:   "
							+ dateFormatter.formatDate(third
									.getAuthorIdent()) + "\n\n\tthird commit\n",
					db.readSquashCommitMsg());
			assertNull(db.readMergeCommitMsg());

			Status stat = git.status().call();
			assertEquals(Sets.of("file3"), stat.getAdded());
		}
	}

	@Test
	public void testSquashMergeConflict() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file1", "file1");
			git.add().addFilepattern("file1").call();
			RevCommit first = git.commit().setMessage("initial commit").call();

			assertTrue(new File(db.getWorkTree(), "file1").exists());
			createBranch(first, "refs/heads/branch1");

			writeTrashFile("file2", "master");
			git.add().addFilepattern("file2").call();
			RevCommit second = git.commit().setMessage("second commit").call();
			assertTrue(new File(db.getWorkTree(), "file2").exists());

			checkoutBranch("refs/heads/branch1");

			writeTrashFile("file2", "branch");
			git.add().addFilepattern("file2").call();
			RevCommit third = git.commit().setMessage("third commit").call();
			assertTrue(new File(db.getWorkTree(), "file2").exists());

			checkoutBranch("refs/heads/master");
			assertTrue(new File(db.getWorkTree(), "file1").exists());
			assertTrue(new File(db.getWorkTree(), "file2").exists());

			MergeResult result = git.merge()
					.include(db.exactRef("refs/heads/branch1"))
					.setSquash(true)
					.call();

			assertTrue(new File(db.getWorkTree(), "file1").exists());
			assertTrue(new File(db.getWorkTree(), "file2").exists());
			assertEquals(MergeResult.MergeStatus.CONFLICTING,
					result.getMergeStatus());
			assertNull(result.getNewHead());
			assertEquals(second, db.resolve(Constants.HEAD + "^{commit}"));

			assertEquals(
					"Squashed commit of the following:\n\ncommit "
							+ third.getName()
							+ "\nAuthor: "
							+ third.getAuthorIdent().getName()
							+ " <"
							+ third.getAuthorIdent().getEmailAddress()
							+ ">\nDate:   "
							+ dateFormatter.formatDate(third
									.getAuthorIdent()) + "\n\n\tthird commit\n",
					db.readSquashCommitMsg());
			assertEquals("\n# Conflicts:\n#\tfile2\n", db.readMergeCommitMsg());

			Status stat = git.status().call();
			assertEquals(Sets.of("file2"), stat.getConflicting());
		}
	}

	@Test
	public void testFastForwardOnly() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit initialCommit = git.commit().setMessage("initial commit")
					.call();
			createBranch(initialCommit, "refs/heads/branch1");
			git.commit().setMessage("second commit").call();
			checkoutBranch("refs/heads/branch1");

			MergeCommand merge = git.merge();
			merge.setFastForward(FastForwardMode.FF_ONLY);
			merge.include(db.exactRef(R_HEADS + MASTER));
			MergeResult result = merge.call();

			assertEquals(MergeStatus.FAST_FORWARD, result.getMergeStatus());
		}
	}

	@Test
	public void testNoFastForward() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit initialCommit = git.commit().setMessage("initial commit")
					.call();
			createBranch(initialCommit, "refs/heads/branch1");
			git.commit().setMessage("second commit").call();
			checkoutBranch("refs/heads/branch1");

			MergeCommand merge = git.merge();
			merge.setFastForward(FastForwardMode.NO_FF);
			merge.include(db.exactRef(R_HEADS + MASTER));
			MergeResult result = merge.call();

			assertEquals(MergeStatus.MERGED, result.getMergeStatus());
		}
	}

	@Test
	public void testNoFastForwardNoCommit() throws Exception {
		// given
		try (Git git = new Git(db)) {
			RevCommit initialCommit = git.commit().setMessage("initial commit")
					.call();
			createBranch(initialCommit, "refs/heads/branch1");
			RevCommit secondCommit = git.commit().setMessage("second commit")
					.call();
			checkoutBranch("refs/heads/branch1");

			// when
			MergeCommand merge = git.merge();
			merge.setFastForward(FastForwardMode.NO_FF);
			merge.include(db.exactRef(R_HEADS + MASTER));
			merge.setCommit(false);
			MergeResult result = merge.call();

			// then
			assertEquals(MergeStatus.MERGED_NOT_COMMITTED, result.getMergeStatus());
			assertEquals(2, result.getMergedCommits().length);
			assertEquals(initialCommit, result.getMergedCommits()[0]);
			assertEquals(secondCommit, result.getMergedCommits()[1]);
			assertNull(result.getNewHead());
			assertEquals(RepositoryState.MERGING_RESOLVED, db.getRepositoryState());
		}
	}

	@Test
	public void testFastForwardOnlyNotPossible() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit initialCommit = git.commit().setMessage("initial commit")
					.call();
			createBranch(initialCommit, "refs/heads/branch1");
			git.commit().setMessage("second commit").call();
			checkoutBranch("refs/heads/branch1");
			writeTrashFile("file1", "branch1");
			git.add().addFilepattern("file").call();
			git.commit().setMessage("second commit on branch1").call();
			MergeCommand merge = git.merge();
			merge.setFastForward(FastForwardMode.FF_ONLY);
			merge.include(db.exactRef(R_HEADS + MASTER));
			MergeResult result = merge.call();

			assertEquals(MergeStatus.ABORTED, result.getMergeStatus());
		}
	}

	@Test
	public void testRecursiveMergeWithConflict() throws Exception {
		try (TestRepository<Repository> db_t = new TestRepository<>(db)) {
			db.incrementOpen();
			BranchBuilder master = db_t.branch("master");
			RevCommit m0 = master.commit()
					.add("f", "1\n2\n3\n4\n5\n6\n7\n8\n9\n").message("m0")
					.create();
			RevCommit m1 = master.commit()
					.add("f", "1-master\n2\n3\n4\n5\n6\n7\n8\n9\n")
					.message("m1").create();
			db_t.getRevWalk().parseCommit(m1);

			BranchBuilder side = db_t.branch("side");
			RevCommit s1 = side.commit().parent(m0)
					.add("f", "1\n2\n3\n4\n5\n6\n7\n8\n9-side\n").message("s1")
					.create();
			RevCommit s2 = side.commit().parent(m1)
					.add("f",
							"1-master\n2\n3\n4\n5\n6\n7-res(side)\n8\n9-side\n")
					.message("s2(merge)").create();
			master.commit().parent(s1)
					.add("f",
							"1-master\n2\n3\n4\n5\n6\n7-conflict\n8\n9-side\n")
					.message("m2(merge)").create();

			Git git = Git.wrap(db);
			git.checkout().setName("master").call();

			MergeResult result = git.merge()
					.setStrategy(MergeStrategy.RECURSIVE).include("side", s2)
					.call();
			assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());
		}
	}

	private Ref prepareSuccessfulMerge(Git git) throws Exception {
		writeTrashFile("a", "1\na\n3\n");
		git.add().addFilepattern("a").call();
		RevCommit initialCommit = git.commit().setMessage("initial").call();

		createBranch(initialCommit, "refs/heads/side");
		checkoutBranch("refs/heads/side");

		writeTrashFile("b", "1\nb\n3\n");
		git.add().addFilepattern("b").call();
		git.commit().setMessage("side").call();

		checkoutBranch("refs/heads/master");

		writeTrashFile("c", "1\nc\n3\n");
		git.add().addFilepattern("c").call();
		git.commit().setMessage("main").call();

		return db.exactRef("refs/heads/side");
	}

	@Test
	public void testMergeWithMessageOption() throws Exception {
		try (Git git = new Git(db)) {
			Ref sideBranch = prepareSuccessfulMerge(git);

			git.merge().include(sideBranch).setStrategy(MergeStrategy.RESOLVE)
					.setMessage("user message").call();

			assertNull(db.readMergeCommitMsg());

			Iterator<RevCommit> it = git.log().call().iterator();
			RevCommit newHead = it.next();
			assertEquals("user message", newHead.getFullMessage());
		}
	}

	@Test
	public void testMergeWithChangeId() throws Exception {
		try (Git git = new Git(db)) {
			Ref sideBranch = prepareSuccessfulMerge(git);

			git.merge().include(sideBranch).setStrategy(MergeStrategy.RESOLVE)
					.setInsertChangeId(true).call();

			assertNull(db.readMergeCommitMsg());

			Iterator<RevCommit> it = git.log().call().iterator();
			RevCommit newHead = it.next();
			String commitMessage = newHead.getFullMessage();
			assertTrue(Pattern.compile("\nChange-Id: I[0-9a-fA-F]{40}\n")
					.matcher(commitMessage).find());
		}
	}

	@Test
	public void testMergeWithMessageAndChangeId() throws Exception {
		try (Git git = new Git(db)) {
			Ref sideBranch = prepareSuccessfulMerge(git);

			git.merge().include(sideBranch).setStrategy(MergeStrategy.RESOLVE)
					.setMessage("user message").setInsertChangeId(true).call();

			assertNull(db.readMergeCommitMsg());

			Iterator<RevCommit> it = git.log().call().iterator();
			RevCommit newHead = it.next();
			String commitMessage = newHead.getFullMessage();
			assertTrue(commitMessage.startsWith("user message\n\n"));
			assertTrue(Pattern.compile("\nChange-Id: I[0-9a-fA-F]{40}\n")
					.matcher(commitMessage).find());
		}
	}

	@Test
	public void testMergeConflictWithMessageOption() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			git.add().addFilepattern("a").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			writeTrashFile("a", "1\na(side)\n3\n");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("side").call();

			checkoutBranch("refs/heads/master");

			writeTrashFile("a", "1\na(main)\n3\n");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("main").call();

			Ref sideBranch = db.exactRef("refs/heads/side");

			git.merge().include(sideBranch).setStrategy(MergeStrategy.RESOLVE)
					.setMessage("user message").call();

			assertEquals("user message\n\n# Conflicts:\n#\ta\n",
					db.readMergeCommitMsg());
		}
	}

	@Test
	public void testMergeConflictWithMessageAndCommentChar() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			git.add().addFilepattern("a").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			writeTrashFile("a", "1\na(side)\n3\n");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("side").call();

			checkoutBranch("refs/heads/master");

			writeTrashFile("a", "1\na(main)\n3\n");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("main").call();

			StoredConfig config = db.getConfig();
			config.setString("core", null, "commentChar", "^");

			Ref sideBranch = db.exactRef("refs/heads/side");

			git.merge().include(sideBranch).setStrategy(MergeStrategy.RESOLVE)
					.setMessage("user message").call();

			assertEquals("user message\n\n^ Conflicts:\n^\ta\n",
					db.readMergeCommitMsg());
		}
	}

	@Test
	public void testMergeConflictWithMessageAndCommentCharAuto()
			throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\na\n3\n");
			git.add().addFilepattern("a").call();
			RevCommit initialCommit = git.commit().setMessage("initial").call();

			createBranch(initialCommit, "refs/heads/side");
			checkoutBranch("refs/heads/side");

			writeTrashFile("a", "1\na(side)\n3\n");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("side").call();

			checkoutBranch("refs/heads/master");

			writeTrashFile("a", "1\na(main)\n3\n");
			git.add().addFilepattern("a").call();
			git.commit().setMessage("main").call();

			StoredConfig config = db.getConfig();
			config.setString("core", null, "commentChar", "auto");

			Ref sideBranch = db.exactRef("refs/heads/side");

			git.merge().include(sideBranch).setStrategy(MergeStrategy.RESOLVE)
					.setMessage("#user message").call();

			assertEquals("#user message\n\n; Conflicts:\n;\ta\n",
					db.readMergeCommitMsg());
		}
	}

	private static void setExecutable(Git git, String path, boolean executable) {
		FS.DETECTED.setExecute(
				new File(git.getRepository().getWorkTree(), path), executable);
	}

	private static boolean canExecute(Git git, String path) {
		return FS.DETECTED.canExecute(new File(git.getRepository()
				.getWorkTree(), path));
	}

	private static RevCommit addAllAndCommit(Git git) throws Exception {
		git.add().addFilepattern(".").call();
		return git.commit().setMessage("message").call();
	}

	private void checkMergeFailedResult(final MergeResult result,
			final MergeFailureReason reason,
			final String indexState, final File fileA) throws Exception {
		assertEquals(MergeStatus.FAILED, result.getMergeStatus());
		assertEquals(reason, result.getFailingPaths().get("a"));
		assertEquals("a(modified)", read(fileA));
		assertFalse(new File(db.getWorkTree(), "b").exists());
		assertEquals("c", read(new File(db.getWorkTree(), "c")));
		assertEquals(indexState, indexState(CONTENT));
		assertEquals(null, result.getConflicts());
		assertEquals(RepositoryState.SAFE, db.getRepositoryState());
	}
}
