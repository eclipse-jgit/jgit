/*
 * Copyright (C) 2019 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class BlameTest extends CLIRepositoryTestCase {

	@Test
	public void testBlameNoHead() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("inIndex.txt", "index");
			git.add().addFilepattern("inIndex.txt").call();
		}
		assertThrows("no such ref: HEAD", Die.class,
				() -> execute("git blame inIndex.txt"));
	}

	@Test
	public void testBlameCommitted() throws Exception {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();
			writeTrashFile("committed.txt", "committed");
			git.add().addFilepattern("committed.txt").call();
			git.commit().setMessage("commit").call();
		}
		assertStringArrayEquals(
				"1ad3399c (GIT_COMMITTER_NAME 2009-08-15 20:12:58 -0330 1) committed",
				execute("git blame committed.txt"));
	}

	@Test
	public void testBlameStaged() throws Exception {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();
			writeTrashFile("inIndex.txt", "index");
			git.add().addFilepattern("inIndex.txt").call();
		}
		assertStringArrayEquals(
				"00000000 (Not Committed Yet 2009-08-15 20:12:58 -0330 1) index",
				execute("git blame inIndex.txt"));
	}

	@Test
	public void testBlameUnstaged() throws Exception {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();
		}
		writeTrashFile("onlyInWorkingTree.txt", "not in repo");
		assertThrows("no such path 'onlyInWorkingTree.txt' in HEAD", Die.class,
				() -> execute("git blame onlyInWorkingTree.txt"));
	}

	@Test
	public void testBlameNonExisting() throws Exception {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();
		}
		assertThrows("no such path 'does_not_exist.txt' in HEAD", Die.class,
				() -> execute("git blame does_not_exist.txt"));
	}

	@Test
	public void testBlameNonExistingInSubdir() throws Exception {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();
		}
		assertThrows("no such path 'sub/does_not_exist.txt' in HEAD", Die.class,
				() -> execute("git blame sub/does_not_exist.txt"));
	}

	@Test
	public void testBlameMergeConflict() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file", "Origin\n");
			git.add().addFilepattern("file").call();
			git.commit().setMessage("initial commit").call();
			git.checkout().setCreateBranch(true)
					.setName("side").call();
			writeTrashFile("file",
					"Conflicting change from side branch\n");
			git.add().addFilepattern("file").call();
			RevCommit side = git.commit().setMessage("side commit")
					.setCommitter(new PersonIdent("gitter", "")).call();
			git.checkout().setName(Constants.MASTER).call();
			writeTrashFile("file", "Change on master branch\n");
			git.add().addFilepattern("file").call();
			git.commit().setMessage("Commit conflict on master")
					.setCommitter(new PersonIdent("gitter", "")).call();
			MergeResult result = git.merge()
					.include("side", side).call();
			assertTrue("Expected conflict on 'file'",
					result.getConflicts().containsKey("file"));
		}
		String[] expected = {
				"00000000 (Not Committed Yet 2009-08-15 20:12:58 -0330 1) <<<<<<< HEAD",
				"0f5b671c (gitter            2009-08-15 20:12:58 -0330 2) Change on master branch",
				"00000000 (Not Committed Yet 2009-08-15 20:12:58 -0330 3) =======",
				"ae78cff6 (gitter            2009-08-15 20:12:58 -0330 4) Conflicting change from side branch",
				"00000000 (Not Committed Yet 2009-08-15 20:12:58 -0330 5) >>>>>>> side" };
		assertArrayOfLinesEquals(expected, execute("git blame file"));
	}
}
