/*
 * Copyright (C) 2011, Kevin Sawicki <kevin@github.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

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
	@Before
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
	public void testUpdateSymLink() throws Exception {
		Assume.assumeTrue(FS.DETECTED.supportsSymlinks());

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
	public void testUpdateBrokenSymLinkToDirectory() throws Exception {
		Assume.assumeTrue(FS.DETECTED.supportsSymlinks());

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
	public void testUpdateBrokenSymLink() throws Exception {
		Assume.assumeTrue(FS.DETECTED.supportsSymlinks());

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
	public void testUpdateWorkingDirectory() throws Exception {
		CheckoutCommand co = git.checkout();
		File written = writeTrashFile(FILE1, "");
		assertEquals("", read(written));
		co.addPath(FILE1).call();
		assertEquals("3", read(written));
		assertEquals("c", read(new File(db.getWorkTree(), FILE2)));
	}

	@Test
	public void testCheckoutFirst() throws Exception {
		CheckoutCommand co = git.checkout();
		File written = writeTrashFile(FILE1, "");
		co.setStartPoint(initialCommit).addPath(FILE1).call();
		assertEquals("1", read(written));
		assertEquals("c", read(new File(db.getWorkTree(), FILE2)));
	}

	@Test
	public void testCheckoutSecond() throws Exception {
		CheckoutCommand co = git.checkout();
		File written = writeTrashFile(FILE1, "");
		co.setStartPoint("HEAD~1").addPath(FILE1).call();
		assertEquals("2", read(written));
		assertEquals("c", read(new File(db.getWorkTree(), FILE2)));
	}

	@Test
	public void testCheckoutMultiple() throws Exception {
		CheckoutCommand co = git.checkout();
		File test = writeTrashFile(FILE1, "");
		File test2 = writeTrashFile(FILE2, "");
		co.setStartPoint("HEAD~2").addPath(FILE1).addPath(FILE2).call();
		assertEquals("1", read(test));
		assertEquals("a", read(test2));
	}

	@Test
	public void testUpdateWorkingDirectoryFromIndex() throws Exception {
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
	public void testUpdateWorkingDirectoryFromHeadWithIndexChange()
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
	public void testUpdateWorkingDirectoryFromIndex2() throws Exception {
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
	public void testCheckoutMixedNewlines() throws Exception {
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
	public void testCheckoutRepository() throws Exception {
		CheckoutCommand co = git.checkout();
		File test = writeTrashFile(FILE1, "");
		File test2 = writeTrashFile(FILE2, "");
		co.setStartPoint("HEAD~2").setAllPaths(true).call();
		assertEquals("1", read(test));
		assertEquals("a", read(test2));
	}


	@Test(expected = JGitInternalException.class)
	public void testCheckoutOfConflictingFileShouldThrow()
			throws Exception {
		setupConflictingState();

		git.checkout().addPath(FILE1).call();
	}

	@Test
	public void testCheckoutOurs() throws Exception {
		setupConflictingState();

		git.checkout().setStage(Stage.OURS).addPath(FILE1).call();

		assertEquals("3", read(FILE1));
		assertStageOneToThree(FILE1);
	}

	@Test
	public void testCheckoutTheirs() throws Exception {
		setupConflictingState();

		git.checkout().setStage(Stage.THEIRS).addPath(FILE1).call();

		assertEquals("Conflicting", read(FILE1));
		assertStageOneToThree(FILE1);
	}

	@Test
	public void testCheckoutOursWhenNoBase() throws Exception {
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
		assertEquals("Expected add/add file to not have base stage",
				DirCacheEntry.STAGE_2, cache.getEntry(file).getStage());

		assertTrue(read(file).startsWith("<<<<<<< HEAD"));

		git.checkout().setStage(Stage.OURS).addPath(file).call();

		assertEquals("Added on master", read(file));

		cache = DirCache.read(db.getIndexFile(), db.getFS());
		assertEquals("Expected conflict stages to still exist after checkout",
				DirCacheEntry.STAGE_2, cache.getEntry(file).getStage());
	}

	@Test(expected = IllegalStateException.class)
	public void testStageNotPossibleWithBranch() throws Exception {
		git.checkout().setStage(Stage.OURS).setStartPoint("master").call();
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
