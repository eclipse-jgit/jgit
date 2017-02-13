/*
 * Copyright (C) 2017 David Pursehouse <david.pursehouse@gmail.com>
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
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SubmoduleConfig.FetchRecurseSubmodulesMode;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Theories.class)
public class FetchCommandRecurseSubmodulesTest extends RepositoryTestCase {
	private static final Logger LOG = LoggerFactory
			.getLogger(FetchCommandRecurseSubmodulesTest.class);

	private Git git;

	private final RefSpec REFSPEC = new RefSpec("refs/heads/master");

	private final String REMOTE = "origin";

	@DataPoints
	public static FetchRecurseSubmodulesMode allModes[] = FetchRecurseSubmodulesMode
			.values();

	@Theory
	public void fetchWithRecurseOption(FetchRecurseSubmodulesMode mode)
			throws Exception {
		git = new Git(db);

		LOG.info("Mode: " + mode);
		// Create submodule 1
		File submodule1 = createTempDirectory(
				"testCloneRepositoryWithNestedSubmodules1");
		Git sub1Git = Git.init().setDirectory(submodule1).call();
		assertNotNull(sub1Git);
		Repository sub1 = sub1Git.getRepository();
		assertNotNull(sub1);
		addRepoToClose(sub1);

		String file = "file.txt";
		String path = "sub";

		write(new File(sub1.getWorkTree(), file), "content");
		sub1Git.add().addFilepattern(file).call();
		RevCommit commit = sub1Git.commit().setMessage("create file").call();
		assertNotNull(commit);

		// Create submodule 2
		File submodule2 = createTempDirectory(
				"testCloneRepositoryWithNestedSubmodules2");
		Git sub2Git = Git.init().setDirectory(submodule2).call();
		assertNotNull(sub2Git);
		Repository sub2 = sub2Git.getRepository();
		assertNotNull(sub2);
		addRepoToClose(sub2);

		write(new File(sub2.getWorkTree(), file), "content");
		sub2Git.add().addFilepattern(file).call();
		RevCommit sub2Head = sub2Git.commit().setMessage("create file").call();
		assertNotNull(sub2Head);

		// Add submodule 2 to submodule 1
		Repository r2 = sub1Git.submoduleAdd().setPath(path)
				.setURI(sub2.getDirectory().toURI().toString()).call();
		assertNotNull(r2);
		addRepoToClose(r2);
		RevCommit sub1Head = sub1Git.commit().setAll(true)
				.setMessage("Adding submodule").call();
		assertNotNull(sub1Head);

		// Add submodule 1 to default repository
		Repository r1 = git.submoduleAdd().setPath(path)
				.setURI(sub1.getDirectory().toURI().toString()).call();
		assertNotNull(r1);
		addRepoToClose(r1);
		assertNotNull(git.commit().setAll(true).setMessage("Adding submodule")
				.call());

		// Clone default repository and include submodules
		File directory = createTempDirectory(
				"testCloneRepositoryWithNestedSubmodules");
		CloneCommand clone = Git.cloneRepository();
		clone.setDirectory(directory);
		clone.setCloneSubmodules(true);
		clone.setURI(git.getRepository().getDirectory().toURI().toString());
		Git git2 = clone.call();
		addRepoToClose(git2.getRepository());
		assertNotNull(git2);

		// Record current FETCH_HEAD of submodules
		ObjectId submoduleHead;
		ObjectId submodule2Head;
		try (SubmoduleWalk walk = SubmoduleWalk
				.forIndex(git2.getRepository())) {
			assertTrue(walk.next());
			Repository r = walk.getRepository();
			submoduleHead = r.resolve(Constants.FETCH_HEAD);

			try (SubmoduleWalk walk2 = SubmoduleWalk.forIndex(r)) {
				assertTrue(walk2.next());
				submodule2Head = walk2.getRepository()
						.resolve(Constants.FETCH_HEAD);
			}
		}

		// Commit in subrepository 1
		JGitTestUtil.writeTrashFile(r1, "f1.txt", "test");
		sub1Git.add().addFilepattern("f1.txt").call();
		commit = sub1Git.commit().setMessage("new commit").call();

		// Commit in subrepository 2
		JGitTestUtil.writeTrashFile(r2, "f2.txt", "test");
		sub2Git.add().addFilepattern("f2.txt").call();
		RevCommit commit2 = sub2Git.commit().setMessage("new commit").call();

		// Fetch
		FetchCommand fetchCommand = git2.fetch().setRemote(REMOTE)
				.setRefSpecs(REFSPEC).setRecurseSubmodules(mode);
		fetchCommand.call();

		// Assert about new FETCH_HEAD after executing fetch command
		try (SubmoduleWalk walk = SubmoduleWalk
				.forIndex(git2.getRepository())) {
			assertTrue(walk.next());
			ObjectId newHead;
			ObjectId newHead2;
			Repository r = walk.getRepository();
			newHead = r.resolve(Constants.FETCH_HEAD);
			try (SubmoduleWalk walk2 = SubmoduleWalk.forIndex(r)) {
				assertTrue(walk2.next());
				newHead2 = walk2.getRepository().resolve(Constants.FETCH_HEAD);
			}
			switch (mode) {
			case YES:
				assertEquals(commit, newHead);
				assertEquals(commit2, newHead2);
				break;
			case NO:
				assertEquals(submoduleHead, newHead);
				assertEquals(submodule2Head, newHead2);
				break;
			case ON_DEMAND:
				// TODO
				break;
			}
		}
	}
}
