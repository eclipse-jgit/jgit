/*
 * Copyright (C) 2016, Ned Twigg <ned.twigg@diffplug.com>
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
package org.eclipse.jgit.pgm;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.junit.Test;
import static org.eclipse.jgit.junit.JGitTestUtil.check;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class CleanTest extends CLIRepositoryTestCase {
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

			assertArrayOfLinesEquals(
					new String[] { "Removing a", "Removing b" },
					execute("git clean"));
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
					execute("git clean -d"));
			assertFalse(check(db, "a"));
			assertFalse(check(db, "b"));
			assertFalse(check(db, "dir/file"));
		}
	}
}
