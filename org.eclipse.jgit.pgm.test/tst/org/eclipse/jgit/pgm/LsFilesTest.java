/*
 * Copyright (C) 2017, Matthias Sohn <matthias.sohn@sap.com>
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Before;
import org.junit.Test;

public class LsFilesTest extends CLIRepositoryTestCase {

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		try (Git git = new Git(db)) {
			JGitTestUtil.writeTrashFile(db, "hello", "hello");
			JGitTestUtil.writeTrashFile(db, "dir", "world", "world");
			git.add().addFilepattern("dir").call();
			git.commit().setMessage("Initial commit").call();

			JGitTestUtil.writeTrashFile(db, "hello2", "hello");
			git.add().addFilepattern("hello2").call();
			FileUtils.createSymLink(new File(db.getWorkTree(), "link"),
					"target");
			FileUtils.mkdir(new File(db.getWorkTree(), "target"));
			writeTrashFile("target/file", "someData");
			git.add().addFilepattern("target").addFilepattern("link").call();
			git.commit().setMessage("hello2").call();

			JGitTestUtil.writeTrashFile(db, "staged", "x");
			JGitTestUtil.deleteTrashFile(db, "hello2");
			git.add().addFilepattern("staged").call();
			JGitTestUtil.writeTrashFile(db, "untracked", "untracked");
		}
	}

	@Test
	public void testHelp() throws Exception {
		String[] result = execute("git ls-files -h");
		assertTrue(result[1].startsWith("jgit ls-files"));
	}

	@Test
	public void testLsFiles() throws Exception {
		assertArrayEquals(new String[] { "dir/world", "hello2", "link",
				"staged", "target/file", "" }, execute("git ls-files"));
	}
}
