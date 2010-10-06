/*
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
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

import java.io.File;
import java.io.FileOutputStream;

import org.eclipse.jgit.api.BranchCommand.ListMode;
import org.eclipse.jgit.api.BranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.errors.NotMergedException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

public class BranchCommandTest extends RepositoryTestCase {
	private Git git;

	RevCommit initialCommit;

	RevCommit secondCommit;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		// commit something
		File sourceFile = new File(db.getWorkTree(), "Test.txt");
		FileOutputStream fos = new FileOutputStream(sourceFile);
		fos.write("Hello world".getBytes("UTF-8"));
		fos.close();
		git.add().addFilepattern("Test.txt").call();
		initialCommit = git.commit().setMessage("Initial commit").call();
		fos = new FileOutputStream(sourceFile);
		fos.write("Some change".getBytes("UTF-8"));
		fos.close();
		git.add().addFilepattern("Test.txt").call();
		secondCommit = git.commit().setMessage("Second commit").call();
		// create a master branch
		RefUpdate rup = db.updateRef("refs/heads/master");
		rup.setNewObjectId(initialCommit.getId());
		rup.setForceUpdate(true);
		rup.update();
	}

	private Git setUpRepoWithRemote() throws Exception {
		Repository remoteRepository = createWorkRepository();
		Git remoteGit = new Git(remoteRepository);
		// commit something
		File sourceFile = new File(remoteRepository.getWorkTree(), "Test.txt");
		FileOutputStream fos = new FileOutputStream(sourceFile);
		fos.write("Hello world".getBytes("UTF-8"));
		fos.close();
		remoteGit.add().addFilepattern("Test.txt").call();
		initialCommit = remoteGit.commit().setMessage("Initial commit").call();
		fos = new FileOutputStream(sourceFile);
		fos.write("Some change".getBytes("UTF-8"));
		fos.close();
		remoteGit.add().addFilepattern("Test.txt").call();
		secondCommit = remoteGit.commit().setMessage("Second commit").call();
		// create a master branch
		RefUpdate rup = remoteRepository.updateRef("refs/heads/master");
		rup.setNewObjectId(initialCommit.getId());
		rup.forceUpdate();

		Repository localRepository = createWorkRepository();
		Git localGit = new Git(localRepository);
		StoredConfig config = localRepository.getConfig();
		RemoteConfig rc = new RemoteConfig(config, "origin");
		rc.addURI(new URIish(remoteRepository.getDirectory().getPath()));
		rc.addFetchRefSpec(new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
		rc.update(config);
		config.save();
		FetchResult res = localGit.fetch().setRemote("origin").call();
		assertFalse(res.getTrackingRefUpdates().isEmpty());
		rup = localRepository.updateRef("refs/heads/master");
		rup.setNewObjectId(initialCommit.getId());
		rup.forceUpdate();
		rup = localRepository.updateRef(Constants.HEAD);
		rup.link("refs/heads/master");
		rup.setNewObjectId(initialCommit.getId());
		rup.update();
		return localGit;
	}

	public void testCreateAndList() throws Exception {
		int localBefore;
		int remoteBefore;
		int allBefore;

		localBefore = git.branch().call().size();
		remoteBefore = git.branch().setList(ListMode.REMOTE).call().size();
		allBefore = git.branch().setList(ListMode.ALL).call().size();

		assertEquals(localBefore + remoteBefore, allBefore);
		Ref newBranch = git.branch().setCreate("NewForTestList", false,
				"master", null).call().get(0);
		assertEquals("refs/heads/NewForTestList", newBranch.getName());

		assertEquals(1, git.branch().call().size() - localBefore);
		assertEquals(0, git.branch().setList(ListMode.REMOTE).call().size()
				- remoteBefore);
		assertEquals(1, git.branch().setList(ListMode.ALL).call().size()
				- allBefore);
		// we can only create local branches
		newBranch = git.branch().setCreate(
				"refs/remotes/origin/NewRemoteForTestList", false, "master",
				null).call().get(0);
		assertEquals("refs/heads/refs/remotes/origin/NewRemoteForTestList",
				newBranch.getName());
		assertEquals(2, git.branch().call().size() - localBefore);
		assertEquals(0, git.branch().setList(ListMode.REMOTE).call().size()
				- remoteBefore);
		assertEquals(2, git.branch().setList(ListMode.ALL).call().size()
				- allBefore);
	}

	public void testCreateForce() throws Exception {
		Ref newBranch = git.branch().setCreate("NewForce", false,
				secondCommit.getId().name(), null).call().get(0);
		assertEquals(newBranch.getTarget().getObjectId(), secondCommit.getId());
		try {
			newBranch = git.branch().setCreate("NewForce", false,
					initialCommit.getId().name(), null).call().get(0);
			fail("Should have failed");
		} catch (Exception e) { // TODO better exception
			// expected
		}
		newBranch = git.branch().setCreate("NewForce", true,
				initialCommit.getId().name(), null).call().get(0);
		assertEquals(newBranch.getTarget().getObjectId(), initialCommit.getId());
	}

	public void testDelete() throws Exception {
		git.branch().setCreate("ForDelete", false, "master", null).call()
				.get(0);
		git.branch().setDelete("ForDelete", false).call();
		// now point the branch to a non-merged commit
		git.branch().setCreate("ForDelete", false, secondCommit.getId().name(),
				null).call().get(0);
		try {
			git.branch().setDelete("ForDelete", false).call();
			fail("Deletion of a non-merged branch without force should have failed");
		} catch (NotMergedException e) {
			// expected
		}
		git.branch().setDelete("ForDelete", true).call();
		git.branch().setCreate("ForDelete", false, "master", null).call()
				.get(0);
		try {
			git.branch().setCreate("ForDelete", false, "master", null).call()
					.get(0);
			fail("Repeated creation of same branch without force should fail");
		} catch (Exception e) { // TODO better exception
			// expected
		}
		// change starting point
		Ref newBranch = git.branch().setCreate("ForDelete", true,
				initialCommit.name(), null).call().get(0);
		assertEquals(newBranch.getTarget().getObjectId(), initialCommit.getId());
		newBranch = git.branch().setCreate("ForDelete", true,
				secondCommit.name(), null).call().get(0);
		assertEquals(newBranch.getTarget().getObjectId(), secondCommit.getId());
		git.branch().setDelete("ForDelete", true).call();
	}

	public void testPullConfigRemoteBranch() throws Exception {
		Git localGit = setUpRepoWithRemote();
		Ref remote = localGit.branch().setList(ListMode.REMOTE).call().get(0);
		assertEquals("refs/remotes/origin/master", remote.getName());
		// by default, we should create pull configuration
		localGit.branch().setCreate("newFromRemote", false, remote.getName(),
				null).call();
		assertEquals("origin", localGit.getRepository().getConfig().getString(
				"branch", "newFromRemote", "remote"));
		localGit.branch().setDelete("newFromRemote", false).call();
		// the pull configuration should be gone after deletion
		assertNull(localGit.getRepository().getConfig().getString("branch",
				"newFromRemote", "remote"));
		// use --no-track
		localGit.branch().setCreate("newFromRemote", false, remote.getName(),
				SetupUpstreamMode.NOTRACK).call();
		assertNull(localGit.getRepository().getConfig().getString("branch",
				"newFromRemote", "remote"));
		localGit.branch().setDelete("newFromRemote", false).call();
	}

	public void testPullConfigLocalBranch() throws Exception {
		Git localGit = setUpRepoWithRemote();
		// by default, we should not create pull configuration
		localGit.branch().setCreate("newFromMaster", false, "master", null)
				.call();
		assertNull(localGit.getRepository().getConfig().getString("branch",
				"newFromMaster", "remote"));
		localGit.branch().setDelete("newFromMaster", false).call();
		// use --track
		localGit.branch().setCreate("newFromMaster", false, "master",
				SetupUpstreamMode.TRACK).call();
		assertEquals(".", localGit.getRepository().getConfig().getString(
				"branch", "newFromMaster", "remote"));
		localGit.branch().setDelete("newFromMaster", false).call();
		// the pull configuration should be gone after deletion
		assertNull(localGit.getRepository().getConfig().getString("branch",
				"newFromRemote", "remote"));
	}
}
