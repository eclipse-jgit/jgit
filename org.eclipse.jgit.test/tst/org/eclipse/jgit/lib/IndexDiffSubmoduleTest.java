/*
 * Copyright (C) 2014, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.submodule.SubmoduleWalk.IgnoreSubmoduleMode;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class IndexDiffSubmoduleTest extends RepositoryTestCase {
	/** a submodule repository inside a root repository */
	protected FileRepository submodule_db;

	/** Working directory of the submodule repository */
	protected File submodule_trash;

	@DataPoints
	public static IgnoreSubmoduleMode allModes[] = IgnoreSubmoduleMode.values();

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		FileRepository submoduleStandalone = createWorkRepository();
		JGitTestUtil.writeTrashFile(submoduleStandalone, "fileInSubmodule",
				"submodule");
		Git submoduleStandaloneGit = Git.wrap(submoduleStandalone);
		submoduleStandaloneGit.add().addFilepattern("fileInSubmodule").call();
		submoduleStandaloneGit.commit().setMessage("add file to submodule")
				.call();

		submodule_db = (FileRepository) Git.wrap(db).submoduleAdd()
				.setPath("modules/submodule")
				.setURI(submoduleStandalone.getDirectory().toURI().toString())
				.call();
		submodule_trash = submodule_db.getWorkTree();
		addRepoToClose(submodule_db);
		writeTrashFile("fileInRoot", "root");
		Git rootGit = Git.wrap(db);
		rootGit.add().addFilepattern("fileInRoot").call();
		rootGit.commit().setMessage("add submodule and root file").call();
	}

	@Theory
	public void testInitiallyClean(IgnoreSubmoduleMode mode)
			throws IOException {
		IndexDiff indexDiff = new IndexDiff(db, Constants.HEAD,
				new FileTreeIterator(db));
		indexDiff.setIgnoreSubmoduleMode(mode);
		assertFalse(indexDiff.diff());
	}

	private Repository cloneWithoutCloningSubmodule() throws Exception {
		File directory = createTempDirectory(
				"testCloneWithoutCloningSubmodules");
		CloneCommand clone = Git.cloneRepository();
		clone.setDirectory(directory);
		clone.setCloneSubmodules(false);
		clone.setURI(db.getDirectory().toURI().toString());
		Git git2 = clone.call();
		addRepoToClose(git2.getRepository());
		return git2.getRepository();
	}

	@Theory
	public void testCleanAfterClone(IgnoreSubmoduleMode mode) throws Exception {
		Repository db2 = cloneWithoutCloningSubmodule();
		IndexDiff indexDiff = new IndexDiff(db2, Constants.HEAD,
				new FileTreeIterator(db2));
		indexDiff.setIgnoreSubmoduleMode(mode);
		boolean changed = indexDiff.diff();
		assertFalse(changed);
	}

	@Theory
	public void testMissingIfDirectoryGone(IgnoreSubmoduleMode mode)
			throws Exception {
		recursiveDelete(submodule_trash);
		IndexDiff indexDiff = new IndexDiff(db, Constants.HEAD,
				new FileTreeIterator(db));
		indexDiff.setIgnoreSubmoduleMode(mode);
		boolean hasChanges = indexDiff.diff();
		if (mode != IgnoreSubmoduleMode.ALL) {
			assertTrue(hasChanges);
			assertEquals("[modules/submodule]",
					indexDiff.getMissing().toString());
		} else {
			assertFalse(hasChanges);
		}
	}

	@Theory
	public void testSubmoduleReplacedByFile(IgnoreSubmoduleMode mode)
			throws Exception {
		recursiveDelete(submodule_trash);
		writeTrashFile("modules/submodule", "nonsense");
		IndexDiff indexDiff = new IndexDiff(db, Constants.HEAD,
				new FileTreeIterator(db));
		indexDiff.setIgnoreSubmoduleMode(mode);
		assertTrue(indexDiff.diff());
		assertEquals("[]", indexDiff.getMissing().toString());
		assertEquals("[]", indexDiff.getUntracked().toString());
		assertEquals("[modules/submodule]", indexDiff.getModified().toString());
	}

	@Theory
	public void testDirtyRootWorktree(IgnoreSubmoduleMode mode)
			throws IOException {
		writeTrashFile("fileInRoot", "2");

		IndexDiff indexDiff = new IndexDiff(db, Constants.HEAD,
				new FileTreeIterator(db));
		indexDiff.setIgnoreSubmoduleMode(mode);
		assertTrue(indexDiff.diff());
	}

	private void assertDiff(IndexDiff indexDiff, IgnoreSubmoduleMode mode,
			IgnoreSubmoduleMode... expectedEmptyModes) throws IOException {
		boolean diffResult = indexDiff.diff();
		Set<String> submodulePaths = indexDiff
				.getPathsWithIndexMode(FileMode.GITLINK);
		boolean emptyExpected = false;
		for (IgnoreSubmoduleMode empty : expectedEmptyModes) {
			if (mode.equals(empty)) {
				emptyExpected = true;
				break;
			}
		}
		if (emptyExpected) {
			assertFalse("diff should be false with mode=" + mode,
					diffResult);
			assertEquals("should have no paths with FileMode.GITLINK", 0,
					submodulePaths.size());
		} else {
			assertTrue("diff should be true with mode=" + mode,
					diffResult);
			assertTrue("submodule path should have FileMode.GITLINK",
					submodulePaths.contains("modules/submodule"));
		}
	}

	@Theory
	public void testDirtySubmoduleWorktree(IgnoreSubmoduleMode mode)
			throws IOException {
		JGitTestUtil.writeTrashFile(submodule_db, "fileInSubmodule", "2");
		IndexDiff indexDiff = new IndexDiff(db, Constants.HEAD,
				new FileTreeIterator(db));
		indexDiff.setIgnoreSubmoduleMode(mode);
		assertDiff(indexDiff, mode, IgnoreSubmoduleMode.ALL,
				IgnoreSubmoduleMode.DIRTY);
	}

	@Theory
	public void testDirtySubmoduleHEAD(IgnoreSubmoduleMode mode)
			throws IOException, GitAPIException {
		JGitTestUtil.writeTrashFile(submodule_db, "fileInSubmodule", "2");
		Git submoduleGit = Git.wrap(submodule_db);
		submoduleGit.add().addFilepattern("fileInSubmodule").call();
		submoduleGit.commit().setMessage("Modified fileInSubmodule").call();

		IndexDiff indexDiff = new IndexDiff(db, Constants.HEAD,
				new FileTreeIterator(db));
		indexDiff.setIgnoreSubmoduleMode(mode);
		assertDiff(indexDiff, mode, IgnoreSubmoduleMode.ALL);
	}

	@Theory
	public void testDirtySubmoduleIndex(IgnoreSubmoduleMode mode)
			throws IOException, GitAPIException {
		JGitTestUtil.writeTrashFile(submodule_db, "fileInSubmodule", "2");
		Git submoduleGit = Git.wrap(submodule_db);
		submoduleGit.add().addFilepattern("fileInSubmodule").call();

		IndexDiff indexDiff = new IndexDiff(db, Constants.HEAD,
				new FileTreeIterator(db));
		indexDiff.setIgnoreSubmoduleMode(mode);
		assertDiff(indexDiff, mode, IgnoreSubmoduleMode.ALL,
				IgnoreSubmoduleMode.DIRTY);
	}

	@Theory
	public void testDirtySubmoduleIndexAndWorktree(IgnoreSubmoduleMode mode)
			throws IOException, GitAPIException, NoWorkTreeException {
		JGitTestUtil.writeTrashFile(submodule_db, "fileInSubmodule", "2");
		Git submoduleGit = Git.wrap(submodule_db);
		submoduleGit.add().addFilepattern("fileInSubmodule").call();
		JGitTestUtil.writeTrashFile(submodule_db, "fileInSubmodule", "3");

		IndexDiff indexDiff = new IndexDiff(db, Constants.HEAD,
				new FileTreeIterator(db));
		indexDiff.setIgnoreSubmoduleMode(mode);
		assertDiff(indexDiff, mode, IgnoreSubmoduleMode.ALL,
				IgnoreSubmoduleMode.DIRTY);
	}

	@Theory
	public void testDirtySubmoduleWorktreeUntracked(IgnoreSubmoduleMode mode)
			throws IOException {
		JGitTestUtil.writeTrashFile(submodule_db, "additionalFileInSubmodule",
				"2");
		IndexDiff indexDiff = new IndexDiff(db, Constants.HEAD,
				new FileTreeIterator(db));
		indexDiff.setIgnoreSubmoduleMode(mode);
		assertDiff(indexDiff, mode, IgnoreSubmoduleMode.ALL,
				IgnoreSubmoduleMode.DIRTY, IgnoreSubmoduleMode.UNTRACKED);
	}

	@Theory
	public void testSubmoduleReplacedByMovedFile(IgnoreSubmoduleMode mode)
			throws Exception {
		Git git = Git.wrap(db);
		git.rm().setCached(true).addFilepattern("modules/submodule").call();
		recursiveDelete(submodule_trash);
		JGitTestUtil.deleteTrashFile(db, "fileInRoot");
		// Move the fileInRoot file
		writeTrashFile("modules/submodule/fileInRoot", "root");
		git.rm().addFilepattern("fileInRoot").addFilepattern("modules/").call();
		git.add().addFilepattern("modules/").call();
		IndexDiff indexDiff = new IndexDiff(db, Constants.HEAD,
				new FileTreeIterator(db));
		indexDiff.setIgnoreSubmoduleMode(mode);
		assertTrue(indexDiff.diff());
		String[] removed = indexDiff.getRemoved().toArray(new String[0]);
		Arrays.sort(removed);
		if (IgnoreSubmoduleMode.ALL.equals(mode)) {
			assertArrayEquals(new String[] { "fileInRoot" }, removed);
		} else {
			assertArrayEquals(
					new String[] { "fileInRoot", "modules/submodule" },
					removed);
		}
		assertEquals("[modules/submodule/fileInRoot]",
				indexDiff.getAdded().toString());
	}

	@Test
	public void testIndexDiffTwoSubmodules() throws Exception {
		// Create a second submodule
		try (Repository submodule2 = createWorkRepository()) {
			JGitTestUtil.writeTrashFile(submodule2, "fileInSubmodule2",
					"submodule2");
			Git subGit = Git.wrap(submodule2);
			subGit.add().addFilepattern("fileInSubmodule2").call();
			subGit.commit().setMessage("add file to submodule2").call();

			try (Repository sub2 = Git.wrap(db)
					.submoduleAdd().setPath("modules/submodule2")
					.setURI(submodule2.getDirectory().toURI().toString())
					.call()) {
				writeTrashFile("fileInRoot", "root+");
				Git rootGit = Git.wrap(db);
				rootGit.add().addFilepattern("fileInRoot").call();
				rootGit.commit().setMessage("add submodule2 and root file")
						.call();
				// Now change files in both submodules
				JGitTestUtil.writeTrashFile(submodule_db, "fileInSubmodule",
						"submodule changed");
				JGitTestUtil.writeTrashFile(sub2, "fileInSubmodule2",
						"submodule2 changed");
				// Set up .gitmodules
				FileBasedConfig gitmodules = new FileBasedConfig(
						new File(db.getWorkTree(), Constants.DOT_GIT_MODULES),
						db.getFS());
				gitmodules.load();
				gitmodules.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION,
						"modules/submodule", ConfigConstants.CONFIG_KEY_IGNORE,
						"all");
				gitmodules.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION,
						"modules/submodule2", ConfigConstants.CONFIG_KEY_IGNORE,
						"none");
				gitmodules.save();
				IndexDiff indexDiff = new IndexDiff(db, Constants.HEAD,
						new FileTreeIterator(db));
				assertTrue(indexDiff.diff());
				String[] modified = indexDiff.getModified()
						.toArray(new String[0]);
				Arrays.sort(modified);
				assertEquals("[.gitmodules, modules/submodule2]",
						Arrays.toString(modified));
				// Try again with "dirty"
				gitmodules.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION,
						"modules/submodule", ConfigConstants.CONFIG_KEY_IGNORE,
						"dirty");
				gitmodules.save();
				indexDiff = new IndexDiff(db, Constants.HEAD,
						new FileTreeIterator(db));
				assertTrue(indexDiff.diff());
				modified = indexDiff.getModified().toArray(new String[0]);
				Arrays.sort(modified);
				assertEquals("[.gitmodules, modules/submodule2]",
						Arrays.toString(modified));
				// Test the config override
				StoredConfig cfg = db.getConfig();
				cfg.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION,
						"modules/submodule", ConfigConstants.CONFIG_KEY_IGNORE,
						"none");
				cfg.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION,
						"modules/submodule2", ConfigConstants.CONFIG_KEY_IGNORE,
						"all");
				cfg.save();
				indexDiff = new IndexDiff(db, Constants.HEAD,
						new FileTreeIterator(db));
				assertTrue(indexDiff.diff());
				modified = indexDiff.getModified().toArray(new String[0]);
				Arrays.sort(modified);
				assertEquals("[.gitmodules, modules/submodule]",
						Arrays.toString(modified));
			}
		}
	}
}
