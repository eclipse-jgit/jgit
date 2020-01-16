/*
 * Copyright (C) 2012, Tomasz Zarna <tomasz.zarna@tasktop.com> and others
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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.junit.Test;

public class ReflogTest extends CLIRepositoryTestCase {
	@Test
	public void testClean() throws Exception {
		assertArrayEquals(new String[] { "" }, execute("git reflog"));
	}

	@Test
	public void testSingleCommit() throws Exception {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();

			assertEquals("6fd41be HEAD@{0}: commit (initial): initial commit",
					execute("git reflog")[0]);
		}
	}

	@Test
	public void testBranch() throws Exception {
		try (Git git = new Git(db)) {
			git.commit().setMessage("first commit").call();
			git.checkout().setCreateBranch(true).setName("side").call();
			writeTrashFile("file", "side content");
			git.add().addFilepattern("file").call();
			git.commit().setMessage("side commit").call();

			assertArrayEquals(new String[] {
					"38890c7 side@{0}: commit: side commit",
					"d216986 side@{1}: branch: Created from commit first commit",
					"" }, execute("git reflog refs/heads/side"));
		}
	}
}
