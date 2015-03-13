/*
 * Copyright (C) 2013, Christian Halstrick <christian.halstrick@sap.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v1.0 which accompanies this
 * distribution, is reproduced below, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.lib;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Before;
import org.junit.Test;

public class DirCacheCheckoutTestWithSymlinks extends RepositoryTestCase {
	@Before
	public void beforeMethod() {
		// If this assumption fails the tests are skipped. When running on a
		// filesystem not supporting symlinks I don't want this tests
		org.junit.Assume.assumeTrue(FS.DETECTED.supportsSymlinks());
	}

	@Test
	public void testDontDeleteSymlinkOnTopOfRootDir() throws Exception {
		// create a parent folder containing a folder with a test repository
		File repos = createTempDirectory("repos");
		File testRepo = new File(repos, "repo");
		testRepo.mkdirs();
		Git git = Git.init().setDirectory(testRepo).call();
		db = (FileRepository) git.getRepository();

		// Create a situation where a checkout of master whould delete a file in
		// a subfolder of the root of the worktree. No other files/folders exist
		writeTrashFile("d/f", "f");
		git.add().addFilepattern(".").call();
		RevCommit initial = git.commit().setMessage("inital").call();
		git.rm().addFilepattern("d/f").call();
		git.commit().setMessage("modifyOnMaster").call();
		git.checkout().setCreateBranch(true).setName("side")
				.setStartPoint(initial).call();
		writeTrashFile("d/f", "f2");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("modifyOnSide").call();
		git.getRepository().close();

		// Create a symlink pointing to the parent folder of the repo and open
		// the repo with the path containing the symlink
		File reposSymlink = createTempFile();
		FileUtils.createSymLink(reposSymlink, repos.getPath());

		Repository symlinkDB = FileRepositoryBuilder.create(new File(
				reposSymlink, "repo/.git"));
		Git symlinkRepo = Git.wrap(symlinkDB);
		symlinkRepo.checkout().setName("master").call();

		// check that the symlink still exists
		assertTrue("The symlink to the repo should exist after a checkout",
				reposSymlink.exists());
	}
}
