/*
 * Copyright (C) 2016, Ned Twigg <ned.twigg@diffplug.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import static org.eclipse.jgit.junit.JGitTestUtil.check;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.junit.Test;

public class CleanTest extends CLIRepositoryTestCase {
	@Test
	public void testCleanRequiresForce() throws Exception {
		try (Git git = new Git(db)) {
			assertArrayOfLinesEquals(
					new String[] { "Removing a", "Removing b" },
					execute("git clean"));
		} catch (Die e) {
			// TODO: should be "fatal: clean.requireForce defaults to true and
			// neither -i, -n, nor -f given; refusing to clean" but we don't
			// support -i yet. Fix this when/if we add support for -i.
			assertEquals(
					"fatal: clean.requireForce defaults to true and neither -n nor -f given; refusing to clean",
					e.getMessage());
		}
	}

	@Test
	public void testCleanRequiresForceConfig() throws Exception {
		try (Git git = new Git(db)) {
			git.getRepository().getConfig().setBoolean("clean", null,
					"requireForce", false);
			assertArrayOfLinesEquals(
					new String[] { "" },
					execute("git clean"));
		}
	}

	@Test
	public void testCleanLeaveDirs() throws Exception {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();

			writeTrashFile("dir/file", "someData");
			writeTrashFile("a", "someData");
			writeTrashFile("b", "someData");

			// all these files should be there
			assertTrue(check(db, "a"));
			assertTrue(check(db, "b"));
			assertTrue(check(db, "dir/file"));

			// dry run should make no change
			assertArrayOfLinesEquals(
					new String[] { "Removing a", "Removing b" },
					execute("git clean -n"));
			assertTrue(check(db, "a"));
			assertTrue(check(db, "b"));
			assertTrue(check(db, "dir/file"));

			// force should make a change
			assertArrayOfLinesEquals(
					new String[] { "Removing a", "Removing b" },
					execute("git clean -f"));
			assertFalse(check(db, "a"));
			assertFalse(check(db, "b"));
			assertTrue(check(db, "dir/file"));
		}
	}

	@Test
	public void testCleanDeleteDirs() throws Exception {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();

			writeTrashFile("dir/file", "someData");
			writeTrashFile("a", "someData");
			writeTrashFile("b", "someData");

			// all these files should be there
			assertTrue(check(db, "a"));
			assertTrue(check(db, "b"));
			assertTrue(check(db, "dir/file"));

			assertArrayOfLinesEquals(
					new String[] { "Removing a", "Removing b",
							"Removing dir/" },
					execute("git clean -d -f"));
			assertFalse(check(db, "a"));
			assertFalse(check(db, "b"));
			assertFalse(check(db, "dir/file"));
		}
	}
}
