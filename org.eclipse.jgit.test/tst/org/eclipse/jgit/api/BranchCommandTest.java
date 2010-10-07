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

import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.CannotDeleteCurrentBranchException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.NotMergedException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
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
		// checkout master
		writeTrashFile("SomeFile.txt", "Hi there");
		git.commit().setMessage("initial commit").call();
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

	// private void checkoutBranch(String branchName)
	// throws IllegalStateException, IOException {
	// git.getRepository().updateRef(Constants.HEAD).link("refs/heads/master");
	// RevWalk walk = new RevWalk(db);
	// RevCommit head = walk.parseCommit(db.resolve(Constants.HEAD));
	// RevCommit branch = walk.parseCommit(db.resolve(branchName));
	// DirCacheCheckout dco = new DirCacheCheckout(db, head.getTree().getId(),
	// db.lockDirCache(), branch.getTree().getId());
	// dco.setFailOnConflict(true);
	// dco.checkout();
	// walk.release();
	// // update the HEAD
	// RefUpdate refUpdate = db.updateRef(Constants.HEAD);
	// refUpdate.link(branchName);
	// }

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

		localBefore = git.branchList().call().size();
		remoteBefore = git.branchList().setListMode(ListMode.REMOTE).call()
				.size();
		allBefore = git.branchList().setListMode(ListMode.ALL).call().size();

		assertEquals(localBefore + remoteBefore, allBefore);
		Ref newBranch = git.branchCreate().setParameters("NewForTestList",
				false, "master", null).call();
		assertEquals("refs/heads/NewForTestList", newBranch.getName());

		assertEquals(1, git.branchList().call().size() - localBefore);
		assertEquals(0, git.branchList().setListMode(ListMode.REMOTE).call()
				.size()
				- remoteBefore);
		assertEquals(1, git.branchList().setListMode(ListMode.ALL).call()
				.size()
				- allBefore);
		// we can only create local branches
		newBranch = git.branchCreate().setParameters(
				"refs/remotes/origin/NewRemoteForTestList", false, "master",
				null).call();
		assertEquals("refs/heads/refs/remotes/origin/NewRemoteForTestList",
				newBranch.getName());
		assertEquals(2, git.branchList().call().size() - localBefore);
		assertEquals(0, git.branchList().setListMode(ListMode.REMOTE).call()
				.size()
				- remoteBefore);
		assertEquals(2, git.branchList().setListMode(ListMode.ALL).call()
				.size()
				- allBefore);
		try {
			git.branchCreate().setName("In va lid").call();
			fail("Create branch with invalid ref name should fail");
		} catch (InvalidRefNameException e) {
			// expected
		}
	}

	public void testCreateForce() throws Exception {
		Ref newBranch = git.branchCreate().setParameters("NewForce", false,
				secondCommit.getId().name(), null).call();
		assertEquals(newBranch.getTarget().getObjectId(), secondCommit.getId());
		try {
			newBranch = git.branchCreate().setParameters("NewForce", false,
					initialCommit.getId().name(), null).call();
			fail("Should have failed");
		} catch (RefAlreadyExistsException e) {
			// expected
		}
		newBranch = git.branchCreate().setParameters("NewForce", true,
				initialCommit.getId().name(), null).call();
		assertEquals(newBranch.getTarget().getObjectId(), initialCommit.getId());
	}

	public void testDelete() throws Exception {
		git.branchCreate().setParameters("ForDelete", false, "master", null)
				.call();
		git.branchDelete().setBranchNames("ForDelete").call();
		// now point the branch to a non-merged commit
		git.branchCreate().setParameters("ForDelete", false,
				secondCommit.getId().name(), null).call();
		try {
			git.branchDelete().setBranchNames("ForDelete").call();
			fail("Deletion of a non-merged branch without force should have failed");
		} catch (NotMergedException e) {
			// expected
		}
		git.branchDelete().setBranchNames("ForDelete").setForce(true).call();
		git.branchCreate().setParameters("ForDelete", false, "master", null)
				.call();
		try {
			git.branchCreate()
					.setParameters("ForDelete", false, "master", null).call();
			fail("Repeated creation of same branch without force should fail");
		} catch (RefAlreadyExistsException e) {
			// expected
		}
		// change starting point
		Ref newBranch = git.branchCreate().setParameters("ForDelete", true,
				initialCommit.name(), null).call();
		assertEquals(newBranch.getTarget().getObjectId(), initialCommit.getId());
		newBranch = git.branchCreate().setParameters("ForDelete", true,
				secondCommit.name(), null).call();
		assertEquals(newBranch.getTarget().getObjectId(), secondCommit.getId());
		git.branchDelete().setBranchNames("ForDelete").setForce(true).call();
		try {
			git.branchDelete().setBranchNames("master").call();
			fail("Deletion of checked out branch without force should have failed");
		} catch (CannotDeleteCurrentBranchException e) {
			// expected
		}
		try {
			git.branchDelete().setBranchNames("master").setForce(true).call();
			fail("Deletion of checked out branch with force should have failed");
		} catch (CannotDeleteCurrentBranchException e) {
			// expected
		}
	}

	public void testPullConfigRemoteBranch() throws Exception {
		Git localGit = setUpRepoWithRemote();
		Ref remote = localGit.branchList().setListMode(ListMode.REMOTE).call()
				.get(0);
		assertEquals("refs/remotes/origin/master", remote.getName());
		// by default, we should create pull configuration
		localGit.branchCreate().setParameters("newFromRemote", false,
				remote.getName(), null).call();
		assertEquals("origin", localGit.getRepository().getConfig().getString(
				"branch", "newFromRemote", "remote"));
		localGit.branchDelete().setBranchNames("newFromRemote").call();
		// the pull configuration should be gone after deletion
		assertNull(localGit.getRepository().getConfig().getString("branch",
				"newFromRemote", "remote"));
		// use --no-track
		localGit.branchCreate().setParameters("newFromRemote", false,
				remote.getName(), SetupUpstreamMode.NOTRACK).call();
		assertNull(localGit.getRepository().getConfig().getString("branch",
				"newFromRemote", "remote"));
		localGit.branchDelete().setBranchNames("newFromRemote").call();
	}

	public void testPullConfigLocalBranch() throws Exception {
		Git localGit = setUpRepoWithRemote();
		// by default, we should not create pull configuration
		localGit.branchCreate().setParameters("newFromMaster", false, "master",
				null).call();
		assertNull(localGit.getRepository().getConfig().getString("branch",
				"newFromMaster", "remote"));
		localGit.branchDelete().setBranchNames("newFromMaster").call();
		// use --track
		localGit.branchCreate().setParameters("newFromMaster", false, "master",
				SetupUpstreamMode.TRACK).call();
		assertEquals(".", localGit.getRepository().getConfig().getString(
				"branch", "newFromMaster", "remote"));
		localGit.branchDelete().setBranchNames("newFromMaster").call();
		// the pull configuration should be gone after deletion
		assertNull(localGit.getRepository().getConfig().getString("branch",
				"newFromRemote", "remote"));
	}

	public void testPullConfigRenameLocalBranch() throws Exception {
		Git localGit = setUpRepoWithRemote();
		// by default, we should not create pull configuration
		localGit.branchCreate().setParameters("newFromMaster", false, "master",
				null).call();
		assertNull(localGit.getRepository().getConfig().getString("branch",
				"newFromMaster", "remote"));
		localGit.branchDelete().setBranchNames("newFromMaster").call();
		// use --track
		localGit.branchCreate().setParameters("newFromMaster", false, "master",
				SetupUpstreamMode.TRACK).call();
		assertEquals(".", localGit.getRepository().getConfig().getString(
				"branch", "newFromMaster", "remote"));
		localGit.branchRename().setOldName("newFromMaster").setNewName(
				"renamed").call();
		assertNull(".", localGit.getRepository().getConfig().getString(
				"branch", "newFromMaster", "remote"));
		assertEquals(".", localGit.getRepository().getConfig().getString(
				"branch", "renamed", "remote"));
		localGit.branchDelete().setBranchNames("renamed").call();
		// the pull configuration should be gone after deletion
		assertNull(localGit.getRepository().getConfig().getString("branch",
				"newFromRemote", "remote"));
	}

	public void testRenameLocalBranch() throws Exception {
		// create some branch
		git.branchCreate().setParameters("existing", false, "master", null)
				.call();
		// a local branch
		Ref branch = git.branchCreate().setParameters("fromMasterForRename",
				false, "master", null).call();
		assertEquals(Constants.R_HEADS + "fromMasterForRename", branch
				.getName());
		Ref renamed = git.branchRename().setOldName("fromMasterForRename")
				.setNewName("newName").call();
		assertEquals(Constants.R_HEADS + "newName", renamed.getName());
		try {
			git.branchRename().setOldName(renamed.getName()).setNewName(
					"existing").call();
			fail("Should have failed");
		} catch (RefAlreadyExistsException e) {
			// expected
		}
		try {
			git.branchRename().setNewName("In va lid").call();
			fail("Rename with invalid ref name should fail");
		} catch (InvalidRefNameException e) {
			// expected
		}
	}

	public void testRenameRemoteTrackingBranch() throws Exception {
		Git localGit = setUpRepoWithRemote();
		Ref remoteBranch = localGit.branchList().setListMode(ListMode.REMOTE)
				.call().get(0);
		Ref renamed = localGit.branchRename()
				.setOldName(remoteBranch.getName()).setNewName("newRemote")
				.call();
		assertEquals(Constants.R_REMOTES + "newRemote", renamed.getName());
	}
}
