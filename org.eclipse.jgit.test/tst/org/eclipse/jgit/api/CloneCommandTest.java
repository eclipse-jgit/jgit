/*
 * Copyright (C) 2011, Chris Aniszczyk <caniszczyk@gmail.com>
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.submodule.SubmoduleStatus;
import org.eclipse.jgit.submodule.SubmoduleStatusType;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Test;

public class CloneCommandTest extends RepositoryTestCase {

	private Git git;

	private TestRepository<Repository> tr;

	public void setUp() throws Exception {
		super.setUp();
		tr = new TestRepository<Repository>(db);

		git = new Git(db);
		// commit something
		writeTrashFile("Test.txt", "Hello world");
		git.add().addFilepattern("Test.txt").call();
		git.commit().setMessage("Initial commit").call();

		// create a master branch and switch to it
		git.branchCreate().setName("test").call();
		RefUpdate rup = db.updateRef(Constants.HEAD);
		rup.link("refs/heads/test");

		// commit something on the test branch
		writeTrashFile("Test.txt", "Some change");
		git.add().addFilepattern("Test.txt").call();
		git.commit().setMessage("Second commit").call();
		RevBlob blob = tr.blob("blob-not-in-master-branch");
		git.tag().setName("tag-for-blob").setObjectId(blob).call();
	}

	@Test
	public void testCloneRepository() throws IOException,
			JGitInternalException, GitAPIException {
		File directory = createTempDirectory("testCloneRepository");
		CloneCommand command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setURI("file://" + git.getRepository().getWorkTree().getPath());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertNotNull(git2);
		ObjectId id = git2.getRepository().resolve("tag-for-blob");
		assertNotNull(id);
		assertEquals(git2.getRepository().getFullBranch(), "refs/heads/test");
		assertEquals(
				"origin",
				git2.getRepository()
						.getConfig()
						.getString(ConfigConstants.CONFIG_BRANCH_SECTION,
								"test", ConfigConstants.CONFIG_KEY_REMOTE));
		assertEquals(
				"refs/heads/test",
				git2.getRepository()
						.getConfig()
						.getString(ConfigConstants.CONFIG_BRANCH_SECTION,
								"test", ConfigConstants.CONFIG_KEY_MERGE));
		assertEquals(2, git2.branchList().setListMode(ListMode.REMOTE).call()
				.size());
	}

	@Test
	public void testCloneRepositoryWithBranch() throws IOException,
			JGitInternalException, GitAPIException {
		File directory = createTempDirectory("testCloneRepositoryWithBranch");
		CloneCommand command = Git.cloneRepository();
		command.setBranch("refs/heads/master");
		command.setDirectory(directory);
		command.setURI("file://" + git.getRepository().getWorkTree().getPath());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());

		assertNotNull(git2);
		assertEquals(git2.getRepository().getFullBranch(), "refs/heads/master");
		assertEquals(
				"refs/heads/master, refs/remotes/origin/master, refs/remotes/origin/test",
				allRefNames(git2.branchList().setListMode(ListMode.ALL).call()));

		// Same thing, but now without checkout
		directory = createTempDirectory("testCloneRepositoryWithBranch_bare");
		command = Git.cloneRepository();
		command.setBranch("refs/heads/master");
		command.setDirectory(directory);
		command.setURI("file://" + git.getRepository().getWorkTree().getPath());
		command.setNoCheckout(true);
		git2 = command.call();
		addRepoToClose(git2.getRepository());

		assertNotNull(git2);
		assertEquals(git2.getRepository().getFullBranch(), "refs/heads/master");
		assertEquals("refs/remotes/origin/master, refs/remotes/origin/test",
				allRefNames(git2.branchList().setListMode(ListMode.ALL).call()));

		// Same thing, but now test with bare repo
		directory = createTempDirectory("testCloneRepositoryWithBranch_bare");
		command = Git.cloneRepository();
		command.setBranch("refs/heads/master");
		command.setDirectory(directory);
		command.setURI("file://" + git.getRepository().getWorkTree().getPath());
		command.setBare(true);
		git2 = command.call();
		addRepoToClose(git2.getRepository());

		assertNotNull(git2);
		assertEquals(git2.getRepository().getFullBranch(), "refs/heads/master");
		assertEquals("refs/heads/master, refs/heads/test", allRefNames(git2
				.branchList().setListMode(ListMode.ALL).call()));
	}

	@Test
	public void testCloneRepositoryOnlyOneBranch() throws IOException,
			JGitInternalException, GitAPIException {
		File directory = createTempDirectory("testCloneRepositoryWithBranch");
		CloneCommand command = Git.cloneRepository();
		command.setBranch("refs/heads/master");
		command.setBranchesToClone(Collections
				.singletonList("refs/heads/master"));
		command.setDirectory(directory);
		command.setURI("file://" + git.getRepository().getWorkTree().getPath());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertNotNull(git2);
		assertEquals(git2.getRepository().getFullBranch(), "refs/heads/master");
		assertEquals("refs/remotes/origin/master", allRefNames(git2
				.branchList().setListMode(ListMode.REMOTE).call()));

		// Same thing, but now test with bare repo
		directory = createTempDirectory("testCloneRepositoryWithBranch_bare");
		command = Git.cloneRepository();
		command.setBranch("refs/heads/master");
		command.setBranchesToClone(Collections
				.singletonList("refs/heads/master"));
		command.setDirectory(directory);
		command.setURI("file://" + git.getRepository().getWorkTree().getPath());
		command.setBare(true);
		git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertNotNull(git2);
		assertEquals(git2.getRepository().getFullBranch(), "refs/heads/master");
		assertEquals("refs/heads/master", allRefNames(git2.branchList()
				.setListMode(ListMode.ALL).call()));
	}

	public static String allRefNames(List<Ref> refs) {
		StringBuilder sb = new StringBuilder();
		for (Ref f : refs) {
			if (sb.length() > 0)
				sb.append(", ");
			sb.append(f.getName());
		}
		return sb.toString();
	}

	@Test
	public void testCloneRepositoryWhenDestinationDirectoryExistsAndIsNotEmpty()
			throws IOException, JGitInternalException, GitAPIException {
		String dirName = "testCloneTargetDirectoryNotEmpty";
		File directory = createTempDirectory(dirName);
		CloneCommand command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setURI("file://" + git.getRepository().getWorkTree().getPath());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertNotNull(git2);
		// clone again
		command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setURI("file://" + git.getRepository().getWorkTree().getPath());
		try {
			git2 = command.call();
			// we shouldn't get here
			fail("destination directory already exists and is not an empty folder, cloning should fail");
		} catch (JGitInternalException e) {
			assertTrue(e.getMessage().contains("not an empty directory"));
			assertTrue(e.getMessage().contains(dirName));
		}
	}

	@Test
	public void testCloneRepositoryWithMultipleHeadBranches() throws Exception {
		git.checkout().setName(Constants.MASTER).call();
		git.branchCreate().setName("a").call();

		File directory = createTempDirectory("testCloneRepositoryWithMultipleHeadBranches");
		CloneCommand clone = Git.cloneRepository();
		clone.setDirectory(directory);
		clone.setURI("file://" + git.getRepository().getWorkTree().getPath());
		Git git2 = clone.call();
		addRepoToClose(git2.getRepository());
		assertNotNull(git2);

		assertEquals(Constants.MASTER, git2.getRepository().getBranch());
	}

	@Test
	public void testCloneRepositoryWithSubmodules() throws Exception {
		git.checkout().setName(Constants.MASTER).call();

		String file = "file.txt";
		writeTrashFile(file, "content");
		git.add().addFilepattern(file).call();
		RevCommit commit = git.commit().setMessage("create file").call();

		SubmoduleAddCommand command = new SubmoduleAddCommand(db);
		String path = "sub";
		command.setPath(path);
		String uri = db.getDirectory().toURI().toString();
		command.setURI(uri);
		Repository repo = command.call();
		assertNotNull(repo);
		addRepoToClose(repo);
		git.add().addFilepattern(path)
				.addFilepattern(Constants.DOT_GIT_MODULES).call();
		git.commit().setMessage("adding submodule").call();

		File directory = createTempDirectory("testCloneRepositoryWithSubmodules");
		CloneCommand clone = Git.cloneRepository();
		clone.setDirectory(directory);
		clone.setCloneSubmodules(true);
		clone.setURI("file://" + git.getRepository().getWorkTree().getPath());
		Git git2 = clone.call();
		addRepoToClose(git2.getRepository());
		assertNotNull(git2);

		assertEquals(Constants.MASTER, git2.getRepository().getBranch());
		assertTrue(new File(git2.getRepository().getWorkTree(), path
				+ File.separatorChar + file).exists());

		SubmoduleStatusCommand status = new SubmoduleStatusCommand(
				git2.getRepository());
		Map<String, SubmoduleStatus> statuses = status.call();
		SubmoduleStatus pathStatus = statuses.get(path);
		assertNotNull(pathStatus);
		assertEquals(SubmoduleStatusType.INITIALIZED, pathStatus.getType());
		assertEquals(commit, pathStatus.getHeadId());
		assertEquals(commit, pathStatus.getIndexId());
	}

	@Test
	public void testCloneRepositoryWithNestedSubmodules() throws Exception {
		git.checkout().setName(Constants.MASTER).call();

		// Create submodule 1
		File submodule1 = createTempDirectory("testCloneRepositoryWithNestedSubmodules1");
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
		File submodule2 = createTempDirectory("testCloneRepositoryWithNestedSubmodules2");
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
		Repository r = sub1Git.submoduleAdd().setPath(path)
				.setURI(sub2.getDirectory().toURI().toString()).call();
		assertNotNull(r);
		addRepoToClose(r);
		RevCommit sub1Head = sub1Git.commit().setAll(true)
				.setMessage("Adding submodule").call();
		assertNotNull(sub1Head);

		// Add submodule 1 to default repository
		r = git.submoduleAdd().setPath(path)
				.setURI(sub1.getDirectory().toURI().toString()).call();
		assertNotNull(r);
		addRepoToClose(r);
		assertNotNull(git.commit().setAll(true).setMessage("Adding submodule")
				.call());

		// Clone default repository and include submodules
		File directory = createTempDirectory("testCloneRepositoryWithNestedSubmodules");
		CloneCommand clone = Git.cloneRepository();
		clone.setDirectory(directory);
		clone.setCloneSubmodules(true);
		clone.setURI(git.getRepository().getDirectory().toURI().toString());
		Git git2 = clone.call();
		addRepoToClose(git2.getRepository());
		assertNotNull(git2);

		assertEquals(Constants.MASTER, git2.getRepository().getBranch());
		assertTrue(new File(git2.getRepository().getWorkTree(), path
				+ File.separatorChar + file).exists());
		assertTrue(new File(git2.getRepository().getWorkTree(), path
				+ File.separatorChar + path + File.separatorChar + file)
				.exists());

		SubmoduleStatusCommand status = new SubmoduleStatusCommand(
				git2.getRepository());
		Map<String, SubmoduleStatus> statuses = status.call();
		SubmoduleStatus pathStatus = statuses.get(path);
		assertNotNull(pathStatus);
		assertEquals(SubmoduleStatusType.INITIALIZED, pathStatus.getType());
		assertEquals(sub1Head, pathStatus.getHeadId());
		assertEquals(sub1Head, pathStatus.getIndexId());

		SubmoduleWalk walk = SubmoduleWalk.forIndex(git2.getRepository());
		assertTrue(walk.next());
		Repository clonedSub1 = walk.getRepository();
		addRepoToClose(clonedSub1);
		assertNotNull(clonedSub1);
		status = new SubmoduleStatusCommand(clonedSub1);
		statuses = status.call();
		pathStatus = statuses.get(path);
		assertNotNull(pathStatus);
		assertEquals(SubmoduleStatusType.INITIALIZED, pathStatus.getType());
		assertEquals(sub2Head, pathStatus.getHeadId());
		assertEquals(sub2Head, pathStatus.getIndexId());
		assertFalse(walk.next());
	}

	@Test
	public void testCloneWithAutoSetupRebase() throws Exception {
		File directory = createTempDirectory("testCloneRepository1");
		CloneCommand command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setURI("file://" + git.getRepository().getWorkTree().getPath());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertFalse(git2
				.getRepository()
				.getConfig()
				.getBoolean(ConfigConstants.CONFIG_BRANCH_SECTION, "test",
						ConfigConstants.CONFIG_KEY_REBASE, false));

		FileBasedConfig userConfig = SystemReader.getInstance().openUserConfig(
				null, git.getRepository().getFS());
		userConfig.setString(ConfigConstants.CONFIG_BRANCH_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOSETUPREBASE,
				ConfigConstants.CONFIG_KEY_ALWAYS);
		userConfig.save();
		directory = createTempDirectory("testCloneRepository2");
		command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setURI("file://" + git.getRepository().getWorkTree().getPath());
		git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertTrue(git2
				.getRepository()
				.getConfig()
				.getBoolean(ConfigConstants.CONFIG_BRANCH_SECTION, "test",
						ConfigConstants.CONFIG_KEY_REBASE, false));

		userConfig.setString(ConfigConstants.CONFIG_BRANCH_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOSETUPREBASE,
				ConfigConstants.CONFIG_KEY_REMOTE);
		userConfig.save();
		directory = createTempDirectory("testCloneRepository2");
		command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setURI("file://" + git.getRepository().getWorkTree().getPath());
		git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertTrue(git2
				.getRepository()
				.getConfig()
				.getBoolean(ConfigConstants.CONFIG_BRANCH_SECTION, "test",
						ConfigConstants.CONFIG_KEY_REBASE, false));

	}
}
