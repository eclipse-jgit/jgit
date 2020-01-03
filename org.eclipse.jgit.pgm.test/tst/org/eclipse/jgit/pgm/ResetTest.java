/*
 * Copyright (C) 2015, Kaloyan Raev <kaloyan.r@zend.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class ResetTest extends CLIRepositoryTestCase {

	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
	}

	@Test
	public void testPathOptionHelp() throws Exception {
		String[] result = execute("git reset -h");
		assertTrue("Unexpected argument: " + result[1],
				result[1].endsWith("[-- path ...]"));
	}

	@Test
	public void testZombieArgument_Bug484951() throws Exception {
		String[] result = execute("git reset -h");
		assertFalse("Unexpected argument: " + result[0],
				result[0].contains("[VAL ...]"));
	}

	@Test
	public void testResetSelf() throws Exception {
		RevCommit commit = git.commit().setMessage("initial commit").call();
		assertStringArrayEquals("",
				execute("git reset --hard " + commit.getId().name()));
		assertEquals(commit.getId(),
				git.getRepository().exactRef("HEAD").getObjectId());
	}

	@Test
	public void testResetPrevious() throws Exception {
		RevCommit commit = git.commit().setMessage("initial commit").call();
		git.commit().setMessage("second commit").call();
		assertStringArrayEquals("",
				execute("git reset --hard " + commit.getId().name()));
		assertEquals(commit.getId(),
				git.getRepository().exactRef("HEAD").getObjectId());
	}

	@Test
	public void testResetEmptyPath() throws Exception {
		RevCommit commit = git.commit().setMessage("initial commit").call();
		assertStringArrayEquals("",
				execute("git reset --hard " + commit.getId().name() + " --"));
		assertEquals(commit.getId(),
				git.getRepository().exactRef("HEAD").getObjectId());
	}

	@Test
	public void testResetPathDoubleDash() throws Exception {
		resetPath(true, true);
	}

	@Test
	public void testResetPathNoDoubleDash() throws Exception {
		resetPath(false, true);
	}

	@Test
	public void testResetPathDoubleDashNoRef() throws Exception {
		resetPath(true, false);
	}

	@Ignore("Currently we cannote recognize if a name is a commit-ish or a path, "
			+ "so 'git reset a' will not work if 'a' is not a branch name but a file path")
	@Test
	public void testResetPathNoDoubleDashNoRef() throws Exception {
		resetPath(false, false);
	}

	private void resetPath(boolean useDoubleDash, boolean supplyCommit)
			throws Exception {
		// create files a and b
		writeTrashFile("a", "Hello world a");
		writeTrashFile("b", "Hello world b");
		// stage the files
		git.add().addFilepattern(".").call();
		// commit the files
		RevCommit commit = git.commit().setMessage("files a & b").call();

		// change both files a and b
		writeTrashFile("a", "New Hello world a");
		writeTrashFile("b", "New Hello world b");
		// stage the files
		git.add().addFilepattern(".").call();

		// reset only file a
		String cmd = String.format("git reset %s%s a",
				supplyCommit ? commit.getId().name() : "",
				useDoubleDash ? " --" : "");
		assertStringArrayEquals("", execute(cmd));
		assertEquals(commit.getId(),
				git.getRepository().exactRef("HEAD").getObjectId());

		org.eclipse.jgit.api.Status status = git.status().call();
		// assert that file a is unstaged
		assertArrayEquals(new String[] { "a" },
				status.getModified().toArray());
		// assert that file b is still staged
		assertArrayEquals(new String[] { "b" },
				status.getChanged().toArray());
	}

}
