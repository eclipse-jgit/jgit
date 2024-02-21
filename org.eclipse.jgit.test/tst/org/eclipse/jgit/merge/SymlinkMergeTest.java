/*
 * Copyright (C) 2022 Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.merge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests for merges involving symlinks.
 */
@RunWith(Parameterized.class)
public class SymlinkMergeTest extends RepositoryTestCase {

	@Parameters(name = "target={0}, core.symlinks={1}")
	public static Object[][] parameters() {
		return new Object[][] {
			{ Target.NONE, Boolean.TRUE },
			{ Target.FILE, Boolean.TRUE },
			{ Target.DIRECTORY, Boolean.TRUE },
			{ Target.NONE, Boolean.FALSE },
			{ Target.FILE, Boolean.FALSE },
			{ Target.DIRECTORY, Boolean.FALSE },
		};
	}

	public enum Target {
		NONE, FILE, DIRECTORY
	}

	@Parameter(0)
	public Target target;

	@Parameter(1)
	public boolean useSymLinks;

	private void setTargets() throws IOException {
		switch (target) {
		case DIRECTORY:
			assertTrue(new File(trash, "target").mkdir());
			assertTrue(new File(trash, "target1").mkdir());
			assertTrue(new File(trash, "target2").mkdir());
			break;
		case FILE:
			writeTrashFile("target", "t");
			writeTrashFile("target1", "t1");
			writeTrashFile("target2", "t2");
			break;
		default:
			break;
		}
	}

	private void checkTargets() throws IOException {
		File t = new File(trash, "target");
		File t1 = new File(trash, "target1");
		File t2 = new File(trash, "target2");
		switch (target) {
		case DIRECTORY:
			assertTrue(t.isDirectory());
			assertTrue(t1.isDirectory());
			assertTrue(t2.isDirectory());
			break;
		case FILE:
			checkFile(t, "t");
			checkFile(t1, "t1");
			checkFile(t2, "t2");
			break;
		default:
			assertFalse(Files.exists(t.toPath(), LinkOption.NOFOLLOW_LINKS));
			assertFalse(Files.exists(t1.toPath(), LinkOption.NOFOLLOW_LINKS));
			assertFalse(Files.exists(t2.toPath(), LinkOption.NOFOLLOW_LINKS));
			break;
		}
	}

	private void assertSymLink(File link, String content) throws Exception {
		if (useSymLinks) {
			assertTrue(Files.isSymbolicLink(link.toPath()));
			assertEquals(content, db.getFS().readSymLink(link));
		} else {
			assertFalse(Files.isSymbolicLink(link.toPath()));
			assertTrue(link.isFile());
			checkFile(link, content);
		}
	}

	// Link/link conflict: C git records the conflict but leaves the link in the
	// working tree unchanged.

	@Test
	public void mergeWithSymlinkConflict() throws Exception {
		assumeTrue(db.getFS().supportsSymlinks() || !useSymLinks);
		StoredConfig config = db.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_SYMLINKS, useSymLinks);
		config.save();
		try (TestRepository<Repository> repo = new TestRepository<>(db)) {
			db.incrementOpen();
			// Create the links directly in the git repo, then use a hard reset
			// to get them into the workspace. This enables us to run these
			// tests also with core.symLinks = false.
			RevCommit base = repo
					.commit(repo.tree(repo.link("link", repo.blob("target"))));
			RevCommit side = repo.commit(
					repo.tree(repo.link("link", repo.blob("target1"))), base);
			RevCommit head = repo.commit(
					repo.tree(repo.link("link", repo.blob("target2"))), base);
			try (Git git = new Git(db)) {
				setTargets();
				git.reset().setMode(ResetType.HARD).setRef(head.name()).call();
				File link = new File(trash, "link");
				assertSymLink(link, "target2");
				MergeResult result = git.merge().include(side)
						.setMessage("merged").call();
				assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());
				// Link should be unmodified
				assertSymLink(link, "target2");
				checkTargets();
				assertEquals("[link, mode:120000, stage:1, content:target]"
						+ "[link, mode:120000, stage:2, content:target2]"
						+ "[link, mode:120000, stage:3, content:target1]",
						indexState(CONTENT));
			}
		}
	}

	// In file/link conflicts, C git never does a content merge. It records the
	// stages in the index, and always puts the file into the workspace.

	@Test
	public void mergeWithFileSymlinkConflict() throws Exception {
		assumeTrue(db.getFS().supportsSymlinks() || !useSymLinks);
		StoredConfig config = db.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_SYMLINKS, useSymLinks);
		config.save();
		try (TestRepository<Repository> repo = new TestRepository<>(db)) {
			db.incrementOpen();
			RevCommit base = repo.commit(repo.tree());
			RevCommit side = repo.commit(
					repo.tree(repo.link("link", repo.blob("target1"))), base);
			RevCommit head = repo.commit(
					repo.tree(repo.file("link", repo.blob("not a link"))),
					base);
			try (Git git = new Git(db)) {
				setTargets();
				git.reset().setMode(ResetType.HARD).setRef(head.name()).call();
				File link = new File(trash, "link");
				assertFalse(Files.isSymbolicLink(link.toPath()));
				checkFile(link, "not a link");
				MergeResult result = git.merge().include(side)
						.setMessage("merged").call();
				assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());
				// File should be unmodified
				assertFalse(Files.isSymbolicLink(link.toPath()));
				checkFile(link, "not a link");
				checkTargets();
				assertEquals("[link, mode:100644, stage:2, content:not a link]"
						+ "[link, mode:120000, stage:3, content:target1]",
						indexState(CONTENT));
			}
		}
	}

	@Test
	public void mergeWithSymlinkFileConflict() throws Exception {
		assumeTrue(db.getFS().supportsSymlinks() || !useSymLinks);
		StoredConfig config = db.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_SYMLINKS, useSymLinks);
		config.save();
		try (TestRepository<Repository> repo = new TestRepository<>(db)) {
			db.incrementOpen();
			RevCommit base = repo.commit(repo.tree());
			RevCommit side = repo.commit(
					repo.tree(repo.file("link", repo.blob("not a link"))),
					base);
			RevCommit head = repo.commit(
					repo.tree(repo.link("link", repo.blob("target2"))), base);
			try (Git git = new Git(db)) {
				setTargets();
				git.reset().setMode(ResetType.HARD).setRef(head.name()).call();
				File link = new File(trash, "link");
				assertSymLink(link, "target2");
				MergeResult result = git.merge().include(side)
						.setMessage("merged").call();
				assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());
				// Should now be a file!
				assertFalse(Files.isSymbolicLink(link.toPath()));
				checkFile(link, "not a link");
				checkTargets();
				assertEquals("[link, mode:120000, stage:2, content:target2]"
						+ "[link, mode:100644, stage:3, content:not a link]",
						indexState(CONTENT));
			}
		}
	}

	// In Delete/modify conflicts with the non-deleted side a link, C git puts
	// the link into the working tree.

	@Test
	public void mergeWithSymlinkDeleteModify() throws Exception {
		assumeTrue(db.getFS().supportsSymlinks() || !useSymLinks);
		StoredConfig config = db.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_SYMLINKS, useSymLinks);
		config.save();
		try (TestRepository<Repository> repo = new TestRepository<>(db)) {
			db.incrementOpen();
			RevCommit base = repo
					.commit(repo.tree(repo.link("link", repo.blob("target"))));
			RevCommit side = repo.commit(
					repo.tree(repo.link("link", repo.blob("target1"))), base);
			RevCommit head = repo.commit(repo.tree(), base);
			try (Git git = new Git(db)) {
				setTargets();
				git.reset().setMode(ResetType.HARD).setRef(head.name()).call();
				File link = new File(trash, "link");
				assertFalse(
						Files.exists(link.toPath(), LinkOption.NOFOLLOW_LINKS));
				MergeResult result = git.merge().include(side)
						.setMessage("merged").call();
				assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());
				// Link should have the content from side
				assertSymLink(link, "target1");
				checkTargets();
				assertEquals("[link, mode:120000, stage:1, content:target]"
						+ "[link, mode:120000, stage:3, content:target1]",
						indexState(CONTENT));
			}
		}
	}

	@Test
	public void mergeWithSymlinkModifyDelete() throws Exception {
		assumeTrue(db.getFS().supportsSymlinks() || !useSymLinks);
		StoredConfig config = db.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_SYMLINKS, useSymLinks);
		config.save();
		try (TestRepository<Repository> repo = new TestRepository<>(db)) {
			db.incrementOpen();
			RevCommit base = repo
					.commit(repo.tree(repo.link("link", repo.blob("target"))));
			RevCommit side = repo.commit(repo.tree(), base);
			RevCommit head = repo.commit(
					repo.tree(repo.link("link", repo.blob("target2"))), base);
			try (Git git = new Git(db)) {
				setTargets();
				git.reset().setMode(ResetType.HARD).setRef(head.name()).call();
				File link = new File(trash, "link");
				assertSymLink(link, "target2");
				MergeResult result = git.merge().include(side)
						.setMessage("merged").call();
				assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());
				// Link should be unmodified
				assertSymLink(link, "target2");
				checkTargets();
				assertEquals("[link, mode:120000, stage:1, content:target]"
						+ "[link, mode:120000, stage:2, content:target2]",
						indexState(CONTENT));
			}
		}
	}
}
