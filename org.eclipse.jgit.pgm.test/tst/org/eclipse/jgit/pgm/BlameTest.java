/*
 * Copyright (C) 2019 Thomas Wolf <thomas.wolf@paranor.ch>
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BlameTest extends CLIRepositoryTestCase {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Test
	public void testBlameNoHead() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("inIndex.txt", "index");
			git.add().addFilepattern("inIndex.txt").call();
		}
		thrown.expect(Die.class);
		thrown.expectMessage("no such ref: HEAD");
		execute("git blame inIndex.txt");
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
				"         (Not Committed Yet          1) index",
				execute("git blame inIndex.txt"));
	}

	@Test
	public void testBlameUnstaged() throws Exception {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();
		}
		writeTrashFile("onlyInWorkingTree.txt", "not in repo");
		thrown.expect(Die.class);
		thrown.expectMessage("no such path 'onlyInWorkingTree.txt' in HEAD");
		execute("git blame onlyInWorkingTree.txt");
	}

	@Test
	public void testBlameNonExisting() throws Exception {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();
		}
		thrown.expect(Die.class);
		thrown.expectMessage("no such path 'does_not_exist.txt' in HEAD");
		execute("git blame does_not_exist.txt");
	}

	@Test
	public void testBlameNonExistingInSubdir() throws Exception {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();
		}
		thrown.expect(Die.class);
		thrown.expectMessage("no such path 'sub/does_not_exist.txt' in HEAD");
		execute("git blame sub/does_not_exist.txt");
	}
}
