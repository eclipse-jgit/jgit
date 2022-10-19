/*
 * Copyright (C) 2011, 2020 Kevin Sawicki <kevin@github.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.eclipse.jgit.api.CheckoutCommand.Stage;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests of path-based uses of {@link CheckoutCommand}
 */
public class PathCheckoutCommandTest extends RepositoryTestCase {

	private static final String FILE1 = "f/Test.txt";

	private static final String FILE2 = "Test2.txt";

	private static final String FILE3 = "Test3.txt";

	private static final String LINK = "link";

	Git git;

	RevCommit initialCommit;

	RevCommit secondCommit;

	@Override
	@BeforeEach
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		writeTrashFile(FILE1, "1");
		writeTrashFile(FILE2, "a");
		git.add().addFilepattern(FILE1).addFilepattern(FILE2).call();
		initialCommit = git.commit().setMessage("Initial commit").call();
		writeTrashFile(FILE1, "2");
		writeTrashFile(FILE2, "b");
		git.add().addFilepattern(FILE1).addFilepattern(FILE2).call();
		secondCommit = git.commit().setMessage("Second commit").call();
		writeTrashFile(FILE1, "3");
		writeTrashFile(FILE2, "c");
		git.add().addFilepattern(FILE1).addFilepattern(FILE2).call();
		git.commit().setMessage("Third commit").call();
	}

	@Test
	void testUpdateSymLink() throws Exception {
		Assumptions.assumeTrue(FS.DETECTED.supportsSymlinks());

		Path path = writeLink(LINK, FILE1);
		git.add().addFilepattern(LINK).call();
		git.commit().setMessage("Added link").call();
		assertEquals("3", read(path.toFile()));

		writeLink(LINK, FILE2);
		assertEquals("c", read(path.toFile()));

		CheckoutCommand co = git.checkout();
		co.addPath(LINK).call();

		assertEquals("3", read(path.toFile()));
	}

	@Test
	void testUpdateBrokenSymLinkToDirectory() throws Exception {
		Assumptions.assumeTrue(FS.DETECTED.supportsSymlinks());

		Path path = writeLink(LINK, "f");
		git.add().addFilepattern(LINK).call();
		git.commit().setMessage("Added link").call();
		assertEquals("f", FileUtils.readSymLink(path.toFile()));
		assertTrue(path.toFile().exists());

		writeLink(LINK, "link_to_nowhere");
		assertFalse(path.toFile().exists());
		assertEquals("link_to_nowhere", FileUtils.readSymLink(path.toFile()));

		CheckoutCommand co = git.checkout();
		co.addPath(LINK).call();

		assertEquals("f", FileUtils.readSymLink(path.toFile()));
	}

	@Test
	void testUpdateBrokenSymLink() throws Exception {
		Assumptions.assumeTrue(FS.DETECTED.supportsSymlinks());

		Path path = writeLink(LINK, FILE1);
		git.add().addFilepattern(LINK).call();
		git.commit().setMessage("Added link").call();
		assertEquals("3", read(path.toFile()));
		assertEquals(FILE1, FileUtils.readSymLink(path.toFile()));

		writeLink(LINK, "link_to_nowhere");
		assertFalse(path.toFile().exists());
		assertEquals("link_to_nowhere", FileUtils.readSymLink(path.toFile()));

		CheckoutCommand co = git.checkout();
		co.addPath(LINK).call();

		assertEquals("3", read(path.toFile()));
	}

	@Test
	void testUpdateWorkingDirectory() throws Exception {
		CheckoutCommand co = git.checkout();
		File written = writeTrashFile(FILE1, "");
		assertEquals("", read(written));
		co.addPath(FILE1).call();
		assertEquals("3", read(written));
		assertEquals("c", read(new File(db.getWorkTree(), FILE2)));
	}

	@Test
	void testCheckoutFirst() throws Exception {
		CheckoutCommand co = git.checkout();
		File written = writeTrashFile(FILE1, "");
		co.setStartPoint(initialCommit).addPath(FILE1).call();
		assertEquals("1", read(written));
		assertEquals("c", read(new File(db.getWorkTree(), FILE2)));
	}

	@Test
	void testCheckoutSecond() throws Exception {
		CheckoutCommand co = git.checkout();
		File written = writeTrashFile(FILE1, "");
		co.setStartPoint("HEAD~1").addPath(FILE1).call();
		assertEquals("2", read(written));
		assertEquals("c", read(new File(db.getWorkTree(), FILE2)));
	}

	@Test
	void testCheckoutMultiple() throws Exception {
		CheckoutCommand co = git.checkout();
		File test = writeTrashFile(FILE1, "");
		File test2 = writeTrashFile(FILE2, "");
		co.setStartPoint("HEAD~2").addPath(FILE1).addPath(FILE2).call();
		assertEquals("1", read(test));
		assertEquals("a", read(test2));
	}

	@Test
	void testUpdateWorkingDirectoryFromIndex() throws Exception {
		CheckoutCommand co = git.checkout();
		File written = writeTrashFile(FILE1, "3a");
		git.add().addFilepattern(FILE1).call();
		written = writeTrashFile(FILE1, "");
		assertEquals("", read(written));
		co.addPath(FILE1).call();
		assertEquals("3a", read(written));
		assertEquals("c", read(new File(db.getWorkTree(), FILE2)));
	}

	@Test
	void testUpdateWorkingDirectoryFromHeadWithIndexChange()
			throws Exception {
		CheckoutCommand co = git.checkout();
		File written = writeTrashFile(FILE1, "3a");
		git.add().addFilepattern(FILE1).call();
		written = writeTrashFile(FILE1, "");
		assertEquals("", read(written));
		co.addPath(FILE1).setStartPoint("HEAD").call();
		assertEquals("3", read(written));
		assertEquals("c", read(new File(db.getWorkTree(), FILE2)));
	}

	@Test
	void testUpdateWorkingDirectoryFromIndex2() throws Exception {
		CheckoutCommand co = git.checkout();
		fsTick(git.getRepository().getIndexFile());

		File written1 = writeTrashFile(FILE1, "3(modified)");
		File written2 = writeTrashFile(FILE2, "a(modified)");
		fsTick(written2);

		// make sure that we get unsmudged entries for FILE1 and FILE2
		writeTrashFile(FILE3, "foo");
		git.add().addFilepattern(FILE3).call();
		fsTick(git.getRepository().getIndexFile());

		git.add().addFilepattern(FILE1).addFilepattern(FILE2).call();
		fsTick(git.getRepository().getIndexFile());

		writeTrashFile(FILE1, "3(modified again)");
		writeTrashFile(FILE2, "a(modified again)");
		fsTick(written2);

		co.addPath(FILE1).setStartPoint(secondCommit).call();

		assertEquals("2", read(written1));
		assertEquals("a(modified again)", read(written2));

		validateIndex(git);
	}

	public static void validateIndex(Git git) throws NoWorkTreeException,
			IOException {
		DirCache dc = git.getRepository().lockDirCache();
		try (ObjectReader r = git.getRepository().getObjectDatabase()
				.newReader()) {
			for (int i = 0; i < dc.getEntryCount(); ++i) {
				DirCacheEntry entry = dc.getEntry(i);
				if (entry.getLength() > 0)
					assertEquals(entry.getLength(), r.getObjectSize(
							entry.getObjectId(), ObjectReader.OBJ_ANY));
			}
		} finally {
			dc.unlock();
		}
	}

	@Test
	void testCheckoutMixedNewlines() throws Exception {
		// "git config core.autocrlf true"
		StoredConfig config = git.getRepository().getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOCRLF, true);
		config.save();
		// edit <FILE1>
		File written = writeTrashFile(FILE1, "4\r\n4");
		assertEquals("4\r\n4", read(written));
		// "git add <FILE1>"
		git.add().addFilepattern(FILE1).call();
		// "git commit -m 'CRLF'"
		git.commit().setMessage("CRLF").call();
		// edit <FILE1>
		written = writeTrashFile(FILE1, "4\n4");
		assertEquals("4\n4", read(written));
		// "git add <FILE1>"
		git.add().addFilepattern(FILE1).call();
		// "git checkout -- <FILE1>
		git.checkout().addPath(FILE1).call();
		// "git status" => clean
		Status status = git.status().call();
		assertEquals(0, status.getAdded().size());
		assertEquals(0, status.getChanged().size());
		assertEquals(0, status.getConflicting().size());
		assertEquals(0, status.getMissing().size());
		assertEquals(0, status.getModified().size());
		assertEquals(0, status.getRemoved().size());
		assertEquals(0, status.getUntracked().size());
	}

	@Test
	void testCheckoutRepository() throws Exception {
		CheckoutCommand co = git.checkout();
		File test = writeTrashFile(FILE1, "");
		File test2 = writeTrashFile(FILE2, "");
		co.setStartPoint("HEAD~2").setAllPaths(true).call();
		assertEquals("1", read(test));
		assertEquals("a", read(test2));
	}


	@Test
	void testCheckoutOfConflictingFileShouldThrow()
			throws Exception {
		assertThrows(JGitInternalException.class, () -> {
			setupConflictingState();

			git.checkout().addPath(FILE1).call();
		});
	}

	@Test
	void testCheckoutOurs() throws Exception {
		setupConflictingState();

		git.checkout().setStage(Stage.OURS).addPath(FILE1).call();

		assertEquals("3", read(FILE1));
		assertStageOneToThree(FILE1);
	}

	@Test
	void testCheckoutTheirs() throws Exception {
		setupConflictingState();

		git.checkout().setStage(Stage.THEIRS).addPath(FILE1).call();

		assertEquals("Conflicting", read(FILE1));
		assertStageOneToThree(FILE1);
	}

	@Test
	void testCheckoutFileWithConflict() throws Exception {
		setupConflictingState();
		assertEquals('[' + FILE1 + ']',
				git.status().call().getConflicting().toString());
		git.checkout().setStartPoint(Constants.HEAD).addPath(FILE1).call();
		assertEquals("3", read(FILE1));
		assertTrue(git.status().call().isClean());
	}

	@Test
	void testCheckoutOursWhenNoBase() throws Exception {
		String file = "added.txt";

		git.checkout().setCreateBranch(true).setName("side")
				.setStartPoint(initialCommit).call();
		writeTrashFile(file, "Added on side");
		git.add().addFilepattern(file).call();
		RevCommit side = git.commit().setMessage("Commit on side").call();

		git.checkout().setName("master").call();
		writeTrashFile(file, "Added on master");
		git.add().addFilepattern(file).call();
		git.commit().setMessage("Commit on master").call();

		git.merge().include(side).call();
		assertEquals(RepositoryState.MERGING, db.getRepositoryState());

		DirCache cache = DirCache.read(db.getIndexFile(), db.getFS());
		assertEquals(DirCacheEntry.STAGE_2, cache.getEntry(file).getStage(), "Expected add/add file to not have base stage");

		assertTrue(read(file).startsWith("<<<<<<< HEAD"));

		git.checkout().setStage(Stage.OURS).addPath(file).call();

		assertEquals("Added on master", read(file));

		cache = DirCache.read(db.getIndexFile(), db.getFS());
		assertEquals(DirCacheEntry.STAGE_2, cache.getEntry(file).getStage(), "Expected conflict stages to still exist after checkout");
	}

	@Test
	void testStageNotPossibleWithBranch() throws Exception {
		assertThrows(IllegalStateException.class, () -> {
			git.checkout().setStage(Stage.OURS).setStartPoint("master").call();
		});
	}

	private void setupConflictingState() throws Exception {
		git.checkout().setCreateBranch(true).setName("conflict")
				.setStartPoint(initialCommit).call();
		writeTrashFile(FILE1, "Conflicting");
		RevCommit conflict = git.commit().setAll(true)
				.setMessage("Conflicting change").call();

		git.checkout().setName("master").call();

		git.merge().include(conflict).call();
		assertEquals(RepositoryState.MERGING, db.getRepositoryState());
		assertStageOneToThree(FILE1);
	}

	private void assertStageOneToThree(String name) throws Exception {
		DirCache cache = DirCache.read(db.getIndexFile(), db.getFS());
		int i = cache.findEntry(name);
		DirCacheEntry stage1 = cache.getEntry(i);
		DirCacheEntry stage2 = cache.getEntry(i + 1);
		DirCacheEntry stage3 = cache.getEntry(i + 2);

		assertEquals(DirCacheEntry.STAGE_1, stage1.getStage());
		assertEquals(DirCacheEntry.STAGE_2, stage2.getStage());
		assertEquals(DirCacheEntry.STAGE_3, stage3.getStage());
	}
}
