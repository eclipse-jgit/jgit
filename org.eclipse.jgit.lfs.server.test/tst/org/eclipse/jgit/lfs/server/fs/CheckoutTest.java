/*
 * Copyright (C) 2016, Christian Halstrick <christian.halstrick@sap.com>
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
package org.eclipse.jgit.lfs.server.fs;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.lfs.CleanFilter;
import org.eclipse.jgit.lfs.SmudgeFilter;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Test;

public class CheckoutTest extends LfsServerTest {

	@Override
	public void setup() throws Exception {
		CleanFilter.register();
		SmudgeFilter.register();
		super.setup();

	}

	@Test
	public void testCheckout() throws Exception {
		Path tempDirectory = getTempDirectory();
		Path repoPath = tempDirectory.resolve("client");
		try (Git git = Git.init().setDirectory(repoPath.toFile()).setBare(false)
				.call()) {
			StoredConfig config = git.getRepository().getConfig();
			config.setString("lfs", null, "url", server.getURI().toString());
			config.setBoolean("filter", "lfs", "useJGitBuiltin", true);
			config.save();
			JGitTestUtil.writeTrashFile(git.getRepository(), ".gitattributes",
					"*.txt filter=lfs");
			git.add().addFilepattern(".gitattributes").call();
			git.commit().setMessage("initial").call();

			JGitTestUtil.writeTrashFile(git.getRepository(), "a.txt", "foo");
			git.add().addFilepattern("a.txt").call();
			RevCommit commit = git.commit().setMessage("add a").call();
			putContent(repoPath.resolve("a.txt"));

			JGitTestUtil.writeTrashFile(git.getRepository(), "a.txt", "bar");
			git.add().addFilepattern("a.txt").call();
			git.commit().setMessage("modify a").call();

			FileUtils.delete(new File(repoPath.toFile(), ".git/lfs/objects"),
					FileUtils.RECURSIVE);

			git.checkout().setName(commit.getName()).call();
			assertEquals("foo",
					JGitTestUtil.read(git.getRepository(), "a.txt"));

		}

	}
}
