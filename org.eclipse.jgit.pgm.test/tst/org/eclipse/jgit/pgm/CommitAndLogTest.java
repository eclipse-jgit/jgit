/*
 * Copyright (C) 2012, IBM Corporation and others.
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

import static org.junit.Assert.assertArrayEquals;

public class CommitAndLogTest extends CLIRepositoryTestCase {
	@Test
	public void testCommitAmend() throws Exception {
		assertArrayEquals(new String[] { // commit
						"[master 101cffba0364877df1942891eba7f465f628a3d2] first comit", //
						"", // amend
						"[master d2169869dadf16549be20dcf8c207349d2ed6c62] first commit", //
						"", // log
						"commit d2169869dadf16549be20dcf8c207349d2ed6c62", //
						"Author: GIT_COMMITTER_NAME <GIT_COMMITTER_EMAIL>", //
						"Date:   Sat Aug 15 20:12:58 2009 -0330", //
						"", //
						"    first commit", //
						"", //
						"" //
				}, execute("git commit -m 'first comit'", //
						"git commit --amend -m 'first commit'", //
						"git log"));
	}

	@Test
	public void testLogAll() throws Exception {
		Git git = new Git(db);
		git.commit().setMessage("initial commit").call();
		writeTrashFile("file1", "content1");
		git.add().addFilepattern("file1").call();
		git.commit().setMessage("file1 commit").call();

		assertArrayEquals(
				new String[]{"commit 3d5ad5dc2c2ab06cd5e35109019d834dccf6b3f5",
						"Author: GIT_COMMITTER_NAME <GIT_COMMITTER_EMAIL>",
						"Date:   Sat Aug 15 20:12:58 2009 -0330",
						"",
						"    file1 commit",
						"",
						"commit 6fd41be26b7ee41584dd997f665deb92b6c4c004",
						"Author: GIT_COMMITTER_NAME <GIT_COMMITTER_EMAIL>",
						"Date:   Sat Aug 15 20:12:58 2009 -0330",
						"",
						"    initial commit",
						"",
						""},
				execute("git log --all"));
	}

	@Test
	public void testLogAllNot() throws Exception {
		Git git = new Git(db);
		git.commit().setMessage("initial commit").call();

		assertArrayEquals(
				new String[]{""},
				execute("git log --not --all"));
	}

	@Test
	public void testLogNot() throws Exception {
		Git git = new Git(db);
		git.commit().setMessage("initial commit").call();
		git.branchCreate().setName("side").call();
		writeTrashFile("file1", "content1");
		git.add().addFilepattern("file1").call();
		git.commit().setMessage("file1 commit").call();
		writeTrashFile("file2", "content2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("file2 commit").call();
		git.checkout().setName("side").call();
		writeTrashFile("side", "content");
		git.add().addFilepattern("side").call();
		git.commit().setMessage("side commit").call();

		assertArrayEquals(
				new String[] { "commit 9121e0c521860990acdeb8c64f493f94c609689a",
						"Author: GIT_COMMITTER_NAME <GIT_COMMITTER_EMAIL>",
						"Date:   Sat Aug 15 20:12:58 2009 -0330",
						"",
						"    side commit",
						"",
						""},
				execute("git log --not master --not side"));
	}

}
