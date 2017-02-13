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

import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SubmoduleConfig.FetchRecurseSubmodulesMode;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.submodule.SubmoduleStatus;
import org.eclipse.jgit.submodule.SubmoduleStatusType;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Before;
import org.junit.Test;

public class FetchCommandRecurseSubmodulesTest extends RepositoryTestCase {
	private Git git;

	private Git git2;

	private Git sub1Git;

	private Git sub2Git;

	private RevCommit commit1;

	private RevCommit commit2;

	private ObjectId submodule1Head;

	private ObjectId submodule2Head;

	private final RefSpec REFSPEC = new RefSpec("refs/heads/master");

	private final String REMOTE = "origin";

	@Before
	public void setUpSubmodules()
			throws Exception {
		git = new Git(db);

		// Create submodule 1
		File submodule1 = createTempDirectory(
				"testCloneRepositoryWithNestedSubmodules1");
		sub1Git = Git.init().setDirectory(submodule1).call();
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
		sub2Git = Git.init().setDirectory(submodule2).call();
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
		git2 = clone.call();
		addRepoToClose(git2.getRepository());
		assertNotNull(git2);

		// Record current FETCH_HEAD of submodules
		try (SubmoduleWalk walk = SubmoduleWalk
				.forIndex(git2.getRepository())) {
			assertTrue(walk.next());
			Repository r = walk.getRepository();
			submodule1Head = r.resolve(Constants.FETCH_HEAD);

			try (SubmoduleWalk walk2 = SubmoduleWalk.forIndex(r)) {
				assertTrue(walk2.next());
				submodule2Head = walk2.getRepository()
						.resolve(Constants.FETCH_HEAD);
			}
		}

		// Commit in submodule 1
		JGitTestUtil.writeTrashFile(r1, "f1.txt", "test");
		sub1Git.add().addFilepattern("f1.txt").call();
		commit1 = sub1Git.commit().setMessage("new commit").call();

		// Commit in submodule 2
		JGitTestUtil.writeTrashFile(r2, "f2.txt", "test");
		sub2Git.add().addFilepattern("f2.txt").call();
		commit2 = sub2Git.commit().setMessage("new commit").call();
	}

	@Test
	public void shouldNotFetchSubmodulesWhenNo() throws Exception {
		FetchResult result = fetch(FetchRecurseSubmodulesMode.NO);
		assertTrue(result.submoduleResults().isEmpty());
		assertSubmoduleFetchHeads(submodule1Head, submodule2Head);
	}

	@Test
	public void shouldFetchSubmodulesWhenYes() throws Exception {
		FetchResult result = fetch(FetchRecurseSubmodulesMode.YES);
		assertTrue(result.submoduleResults().containsKey("sub"));
		FetchResult subResult = result.submoduleResults().get("sub");
		assertTrue(subResult.submoduleResults().containsKey("sub"));
		assertSubmoduleFetchHeads(commit1, commit2);
	}

	@Test
	public void shouldFetchSubmodulesWhenOnDemandAndRevisionChanged()
			throws Exception {
		// Fetch the submodule in the original git and reset it to
		// the commit that was created
		try (SubmoduleWalk w = SubmoduleWalk.forIndex(git.getRepository())) {
			assertTrue(w.next());
			try (Git g = new Git(w.getRepository())) {
				g.fetch().setRemote(REMOTE).setRefSpecs(REFSPEC).call();
				g.reset().setMode(ResetType.HARD).setRef(commit1.name()).call();
			}
		}

		// Submodule index Id should be same as before, but head Id should be
		// updated to the new commit, and status should be "checked out".
		SubmoduleStatus subStatus = git.submoduleStatus().call().get("sub");
		assertEquals(submodule1Head, subStatus.getIndexId());
		assertEquals(commit1, subStatus.getHeadId());
		assertEquals(SubmoduleStatusType.REV_CHECKED_OUT, subStatus.getType());

		// Add and commit the submodule status
		git.add().addFilepattern("sub").call();
		RevCommit update = git.commit().setMessage("update sub").call();

		// Both submodule index and head should now be at the new commit, and
		// the status should be "initialized".
		subStatus = git.submoduleStatus().call().get("sub");
		assertEquals(commit1, subStatus.getIndexId());
		assertEquals(commit1, subStatus.getHeadId());
		assertEquals(SubmoduleStatusType.INITIALIZED, subStatus.getType());

		FetchResult result = fetch(FetchRecurseSubmodulesMode.ON_DEMAND);

		// The first submodule should have been updated
		assertTrue(result.submoduleResults().containsKey("sub"));
		FetchResult subResult = result.submoduleResults().get("sub");

		// The second submodule should not get updated
		assertTrue(subResult.submoduleResults().isEmpty());
		assertSubmoduleFetchHeads(commit1, submodule2Head);

		// After fetch the parent repo's fetch head should be the commit
		// that updated the submodule.
		assertEquals(update,
				git2.getRepository().resolve(Constants.FETCH_HEAD));
	}

	@Test
	public void shouldNotFetchSubmodulesWhenOnDemandAndRevisionNotChanged()
			throws Exception {
		FetchResult result = fetch(FetchRecurseSubmodulesMode.ON_DEMAND);
		assertTrue(result.submoduleResults().isEmpty());
		assertSubmoduleFetchHeads(submodule1Head, submodule2Head);
	}

	private FetchResult fetch(FetchRecurseSubmodulesMode mode)
			throws Exception {
		FetchResult result = git2.fetch().setRemote(REMOTE).setRefSpecs(REFSPEC)
				.setRecurseSubmodules(mode).call();
		assertNotNull(result);
		return result;
	}

	private void assertSubmoduleFetchHeads(ObjectId expectedHead1,
			ObjectId expectedHead2) throws Exception {
		try (SubmoduleWalk walk = SubmoduleWalk
				.forIndex(git2.getRepository())) {
			assertTrue(walk.next());
			Repository r = walk.getRepository();
			ObjectId newHead1 = r.resolve(Constants.FETCH_HEAD);
			ObjectId newHead2;
			try (SubmoduleWalk walk2 = SubmoduleWalk.forIndex(r)) {
				assertTrue(walk2.next());
				newHead2 = walk2.getRepository().resolve(Constants.FETCH_HEAD);
			}

			assertEquals(expectedHead1, newHead1);
			assertEquals(expectedHead2, newHead2);
		}
	}
}
