/*
 * Copyright (C) 2011, 2013 Chris Aniszczyk <caniszczyk@gmail.com>
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.BranchConfig.BranchRebaseMode;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileBasedShallow;
import org.eclipse.jgit.submodule.SubmoduleStatus;
import org.eclipse.jgit.submodule.SubmoduleStatusType;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Test;

public class CloneCommandTest extends RepositoryTestCase {

	private Git git;

	private TestRepository<Repository> tr;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		tr = new TestRepository<>(db);

		git = new Git(db);
		// commit something
		writeTrashFile("Test.txt", "Hello world");
		git.add().addFilepattern("Test.txt").call();
		git.commit().setMessage("Initial commit").call();
		git.tag().setName("tag-initial").setMessage("Tag initial").call();

		// create a test branch and switch to it
		git.checkout().setCreateBranch(true).setName("test").call();

		// commit something on the test branch
		writeTrashFile("Test.txt", "Some change");
		git.add().addFilepattern("Test.txt").call();
		git.commit().setMessage("Second commit").call();
		RevBlob blob = tr.blob("blob-not-in-master-branch");
		git.tag().setName("tag-for-blob").setObjectId(blob).call();
	}

	@Test
	public void testCloneRepository() throws IOException,
			JGitInternalException, GitAPIException, URISyntaxException {
		File directory = createTempDirectory("testCloneRepository");
		CloneCommand command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setURI(fileUri());
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
		assertEquals(new RefSpec("+refs/heads/*:refs/remotes/origin/*"),
				fetchRefSpec(git2.getRepository()));
	}

	@Test
	public void testCloneRepositoryExplicitGitDir() throws IOException,
			JGitInternalException, GitAPIException {
		File directory = createTempDirectory("testCloneRepository");
		CloneCommand command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setGitDir(new File(directory, ".git"));
		command.setURI(fileUri());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertEquals(directory, git2.getRepository().getWorkTree());
		assertEquals(new File(directory, ".git"), git2.getRepository()
				.getDirectory());
	}

	@Test
	public void testCloneRepositoryExplicitGitDirNonStd() throws IOException,
			JGitInternalException, GitAPIException {
		File directory = createTempDirectory("testCloneRepository");
		File gDir = createTempDirectory("testCloneRepository.git");
		CloneCommand command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setGitDir(gDir);
		command.setURI(fileUri());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertEquals(directory, git2.getRepository().getWorkTree());
		assertEquals(gDir, git2.getRepository()
				.getDirectory());
		assertTrue(new File(directory, ".git").isFile());
		assertFalse(new File(gDir, ".git").exists());
	}

	@Test
	public void testCloneRepositoryExplicitGitDirBare() throws IOException,
			JGitInternalException, GitAPIException {
		File gDir = createTempDirectory("testCloneRepository.git");
		CloneCommand command = Git.cloneRepository();
		command.setBare(true);
		command.setGitDir(gDir);
		command.setURI(fileUri());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		try {
			assertNull(null, git2.getRepository().getWorkTree());
			fail("Expected NoWorkTreeException");
		} catch (NoWorkTreeException e) {
			assertEquals(gDir, git2.getRepository().getDirectory());
		}
	}

	@Test
	public void testBareCloneRepository() throws IOException,
			JGitInternalException, GitAPIException, URISyntaxException {
		File directory = createTempDirectory("testCloneRepository_bare");
		CloneCommand command = Git.cloneRepository();
		command.setBare(true);
		command.setDirectory(directory);
		command.setURI(fileUri());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertEquals(new RefSpec("+refs/heads/*:refs/heads/*"),
				fetchRefSpec(git2.getRepository()));
	}

	@Test
	public void testCloneRepositoryCustomRemote() throws Exception {
		File directory = createTempDirectory("testCloneRemoteUpstream");
		CloneCommand command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setRemote("upstream");
		command.setURI(fileUri());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertEquals("+refs/heads/*:refs/remotes/upstream/*",
				git2.getRepository()
					.getConfig()
					.getStringList("remote", "upstream",
							"fetch")[0]);
		assertEquals("upstream",
				git2.getRepository()
					.getConfig()
					.getString("branch", "test", "remote"));
		assertEquals(db.resolve("test"),
				git2.getRepository().resolve("upstream/test"));
	}

	@Test
	public void testBareCloneRepositoryCustomRemote() throws Exception {
		File directory = createTempDirectory("testCloneRemoteUpstream_bare");
		CloneCommand command = Git.cloneRepository();
		command.setBare(true);
		command.setDirectory(directory);
		command.setRemote("upstream");
		command.setURI(fileUri());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertEquals("+refs/heads/*:refs/heads/*",
				git2.getRepository()
					.getConfig()
					.getStringList("remote", "upstream",
							"fetch")[0]);
		assertEquals("upstream",
				git2.getRepository()
					.getConfig()
					.getString("branch", "test", "remote"));
		assertNull(git2.getRepository().resolve("upstream/test"));
	}

	@Test
	public void testBareCloneRepositoryNullRemote() throws Exception {
		File directory = createTempDirectory("testCloneRemoteNull_bare");
		CloneCommand command = Git.cloneRepository();
		command.setBare(true);
		command.setDirectory(directory);
		command.setRemote(null);
		command.setURI(fileUri());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertEquals("+refs/heads/*:refs/heads/*", git2.getRepository()
				.getConfig().getStringList("remote", "origin", "fetch")[0]);
		assertEquals("origin", git2.getRepository().getConfig()
				.getString("branch", "test", "remote"));
	}

	public static RefSpec fetchRefSpec(Repository r) throws URISyntaxException {
		RemoteConfig remoteConfig =
				new RemoteConfig(r.getConfig(), Constants.DEFAULT_REMOTE_NAME);
		return remoteConfig.getFetchRefSpecs().get(0);
	}

	@Test
	public void testCloneRepositoryWithBranch() throws IOException,
			JGitInternalException, GitAPIException {
		File directory = createTempDirectory("testCloneRepositoryWithBranch");
		CloneCommand command = Git.cloneRepository();
		command.setBranch("refs/heads/master");
		command.setDirectory(directory);
		command.setURI(fileUri());
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
		command.setURI(fileUri());
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
		command.setURI(fileUri());
		command.setBare(true);
		git2 = command.call();
		addRepoToClose(git2.getRepository());

		assertNotNull(git2);
		assertEquals(git2.getRepository().getFullBranch(), "refs/heads/master");
		assertEquals("refs/heads/master, refs/heads/test", allRefNames(git2
				.branchList().setListMode(ListMode.ALL).call()));
	}

	@Test
	public void testCloneRepositoryWithBranchShortName() throws Exception {
		File directory = createTempDirectory("testCloneRepositoryWithBranch");
		CloneCommand command = Git.cloneRepository();
		command.setBranch("test");
		command.setDirectory(directory);
		command.setURI(fileUri());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());

		assertNotNull(git2);
		assertEquals("refs/heads/test", git2.getRepository().getFullBranch());
	}

	@Test
	public void testCloneRepositoryWithTagName() throws Exception {
		File directory = createTempDirectory("testCloneRepositoryWithBranch");
		CloneCommand command = Git.cloneRepository();
		command.setBranch("tag-initial");
		command.setDirectory(directory);
		command.setURI(fileUri());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());

		assertNotNull(git2);
		ObjectId taggedCommit = db.resolve("tag-initial^{commit}");
		assertEquals(taggedCommit.name(), git2
				.getRepository().getFullBranch());
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
		command.setURI(fileUri());
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
		command.setURI(fileUri());
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
		command.setURI(fileUri());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertNotNull(git2);
		// clone again
		command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setURI(fileUri());
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
		clone.setURI(fileUri());
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
		try (SubmoduleWalk walk = SubmoduleWalk.forIndex(git.getRepository())) {
			assertTrue(walk.next());
			Repository subRepo = walk.getRepository();
			addRepoToClose(subRepo);
			assertNotNull(subRepo);
			assertEquals(
					new File(git.getRepository().getWorkTree(), walk.getPath()),
					subRepo.getWorkTree());
			assertEquals(new File(new File(git.getRepository().getDirectory(),
					"modules"), walk.getPath()), subRepo.getDirectory());
		}

		File directory = createTempDirectory("testCloneRepositoryWithSubmodules");
		CloneCommand clone = Git.cloneRepository();
		clone.setDirectory(directory);
		clone.setCloneSubmodules(true);
		clone.setURI(fileUri());
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

		try (SubmoduleWalk walk = SubmoduleWalk
				.forIndex(git2.getRepository())) {
			assertTrue(walk.next());
			Repository clonedSub1 = walk.getRepository();
			addRepoToClose(clonedSub1);
			assertNotNull(clonedSub1);
			assertEquals(new File(git2.getRepository().getWorkTree(),
					walk.getPath()), clonedSub1.getWorkTree());
			assertEquals(
					new File(new File(git2.getRepository().getDirectory(),
							"modules"), walk.getPath()),
					clonedSub1.getDirectory());
		}
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
		assertNotNull(clonedSub1);
		assertEquals(
				new File(git2.getRepository().getWorkTree(), walk.getPath()),
				clonedSub1.getWorkTree());
		assertEquals(new File(new File(git2.getRepository().getDirectory(),
				"modules"), walk.getPath()),
				clonedSub1.getDirectory());
		status = new SubmoduleStatusCommand(clonedSub1);
		statuses = status.call();
		clonedSub1.close();
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
		command.setURI(fileUri());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertNull(git2.getRepository().getConfig().getEnum(
				BranchRebaseMode.values(),
				ConfigConstants.CONFIG_BRANCH_SECTION, "test",
				ConfigConstants.CONFIG_KEY_REBASE, null));

		FileBasedConfig userConfig = SystemReader.getInstance().openUserConfig(
				null, git.getRepository().getFS());
		userConfig.setString(ConfigConstants.CONFIG_BRANCH_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOSETUPREBASE,
				ConfigConstants.CONFIG_KEY_ALWAYS);
		userConfig.save();
		directory = createTempDirectory("testCloneRepository2");
		command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setURI(fileUri());
		git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertEquals(BranchRebaseMode.REBASE,
				git2.getRepository().getConfig().getEnum(
						BranchRebaseMode.values(),
						ConfigConstants.CONFIG_BRANCH_SECTION, "test",
						ConfigConstants.CONFIG_KEY_REBASE,
						BranchRebaseMode.NONE));

		userConfig.setString(ConfigConstants.CONFIG_BRANCH_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOSETUPREBASE,
				ConfigConstants.CONFIG_KEY_REMOTE);
		userConfig.save();
		directory = createTempDirectory("testCloneRepository2");
		command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setURI(fileUri());
		git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertEquals(BranchRebaseMode.REBASE,
				git2.getRepository().getConfig().getEnum(
						BranchRebaseMode.values(),
						ConfigConstants.CONFIG_BRANCH_SECTION, "test",
						ConfigConstants.CONFIG_KEY_REBASE,
						BranchRebaseMode.NONE));

	}

	private String fileUri() {
		return "file://" + git.getRepository().getWorkTree().getAbsolutePath();
	}

	private void setUp2() throws IOException, JGitInternalException,
			GitAPIException {
		final String fileName1 = "another-file.txt";
		final String fileName2 = "HelloWorld.txt";
		final String fileName3 = "file.txt";
		// create more commits in branch test
		git.checkout().setName("test").call();
		writeTrashFile(fileName1, fileName1);
		git.add().addFilepattern(fileName1).call();
		git.commit().setMessage(fileName1).call();
		git.tag().setName(fileName1).call();

		// create more commits in branch master
		git.checkout().setName("master")
				.call();
		assertEquals(git.getRepository().getFullBranch(), "refs/heads/master");

		writeTrashFile(fileName2, "Hello World!");
		git.add().addFilepattern(fileName2).call();
		git.commit().setMessage("Third commit").call();
		writeTrashFile(fileName3, "content");
		git.add().addFilepattern(fileName3).call();
		git.commit().setMessage("Final commit").call();
	}

	private List<RevCommit> getAsList(Iterable<RevCommit> iterable) {
		assertNotNull(iterable);
		final List<RevCommit> result = new ArrayList<>();
		final Iterator<RevCommit> it = iterable.iterator();
		while (it.hasNext()) {
			final RevCommit commit = it.next();
			result.add(commit);
		}
		return result;
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCloneRepositoryWithNegativeDepth()
			throws JGitInternalException, IllegalArgumentException {
		CloneCommand command = Git.cloneRepository();
		command.setDepth(-3);
	}

	@Test
	public void testCloneRepositoryWithDepth1() throws IOException,
			JGitInternalException, GitAPIException {
		// git clone --depth 1 \
		// https://github.com/timeraider4u/jgit_test_repo.git
		this.setUp2();
		final CloneCommand cmd = helperForDepthTestSetupCloneCommand(
				"testCloneRepositoryWithDepth1", 1);
		final Git git2 = helperForDepthTestSetupGit(cmd);
		// cat .git/shallow -> c21676d47a0f506cfa26596c5bb8f33430603054
		helperForDepthTestCheckShallowFile(git2,
				"c21676d47a0f506cfa26596c5bb8f33430603054");
		// git log -> Final commit
		helperForDepthTestCheckCommits(git2, true, false, false);
		// ls -> HelloWorld.txt, Test.txt, file.txt
		helperForDepthTestCheckFilesExist(git2);
		// git tag -l -> (empty)
		helperForDepthTestCheckTags(git2, false, false, false);
		// git branch --all -> master
		helperForDepthTestCheckBranches(git2, true, false);
		// ls .git/refs/heads/ -> master
		helperForDepthTestCheckRefs(git2, true, false);
	}

	@Test
	public void testCloneRepositoryWithDepth1WithTestBranch()
			throws IOException, JGitInternalException, GitAPIException {
		// git clone --depth 1 \
		// https://github.com/timeraider4u/jgit_test_repo.git
		// git fetch --depth=1 origin test:test
		this.setUp2();
		final CloneCommand cmd = helperForDepthTestSetupCloneCommand(
				"testCloneRepositoryWithDepth1WithTestBranch", 1);
		final List<String> branchesToClone = new ArrayList<>();
		branchesToClone.add("refs/heads/master");
		branchesToClone.add("refs/heads/test");
		cmd.setBranchesToClone(branchesToClone);
		final Git git2 = helperForDepthTestSetupGit(cmd);
		// cat .git/shallow
		// -> 4c593aa21f6c0b95881a9f49cb03bd6dcd974535
		// -> c21676d47a0f506cfa26596c5bb8f33430603054
		helperForDepthTestCheckShallowFile(git2,
				"4c593aa21f6c0b95881a9f49cb03bd6dcd974535",
				"c21676d47a0f506cfa26596c5bb8f33430603054");
		// git log -> Final commit
		helperForDepthTestCheckCommits(git2, true, false, false);
		// ls -> HelloWorld.txt, Test.txt, file.txt
		helperForDepthTestCheckFilesExist(git2);
		// git tag -l -> (empty)
		helperForDepthTestCheckTags(git2, false, false, false);
		// git branch --all -> master
		helperForDepthTestCheckBranches(git2, true, false);
		// ls .git/refs/heads/ -> master
		helperForDepthTestCheckRefs(git2, true, false);
	}

	@Test
	public void testCloneRepositoryWithDepth2() throws IOException,
			JGitInternalException, GitAPIException {
		// git clone --depth 2
		// https://github.com/timeraider4u/jgit_test_repo.git
		this.setUp2();
		final CloneCommand cmd = helperForDepthTestSetupCloneCommand(
				"testCloneRepositoryWithDepth2", 2);
				final Git git2 = helperForDepthTestSetupGit(cmd);
		// cat .git/shallow -> 9007666656678b23b58b5d88ec76107a9948c006
		helperForDepthTestCheckShallowFile(git2,
				"9007666656678b23b58b5d88ec76107a9948c006");
		// git log -> Final commit, Third commit
		helperForDepthTestCheckCommits(git2, true, true, false);
		// ls -> HelloWorld.txt, Test.txt, file.txt
		helperForDepthTestCheckFilesExist(git2);
		// git tag -l -> (empty)
		helperForDepthTestCheckTags(git2, false, false, false);
		// git branch --all -> master
		helperForDepthTestCheckBranches(git2, true, false);
		// ls .git/refs/heads/ -> master
		helperForDepthTestCheckRefs(git2, true, false);
	}

	@Test
	public void testCloneRepositoryWithDepth2000() throws IOException,
			JGitInternalException, GitAPIException {
		// git clone --depth 2000
		// https://github.com/timeraider4u/jgit_test_repo.git
		this.setUp2();
		final CloneCommand cmd = helperForDepthTestSetupCloneCommand(
				"testCloneRepositoryWithDepth2000", 2000);
		final Git git2 = helperForDepthTestSetupGit(cmd);
		// cat .git/shallow -> (no such file)
		helperForDepthTestCheckShallowFile(git2);
		// git log -> Final commit, Third commit, Initial commit
		helperForDepthTestCheckCommits(git2, true, true, true);
		// ls -> HelloWorld.txt, Test.txt, file.txt
		helperForDepthTestCheckFilesExist(git2);
		// git tag -l -> tag-initial
		helperForDepthTestCheckTags(git2, true, false, false);
		// git branch --all -> master
		helperForDepthTestCheckBranches(git2, true, false);
		// ls .git/refs/heads/ -> master
		helperForDepthTestCheckRefs(git2, true, false);
	}

	@Test
	public void testCloneRepositoryWithDepthInfinite()
			throws IOException, JGitInternalException, GitAPIException {
		// git clone https://github.com/timeraider4u/jgit_test_repo.git
		this.setUp2();
		// try {
		// Thread.sleep(40000);
		// } catch (InterruptedException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
		final CloneCommand cmd = helperForDepthTestSetupCloneCommand(
				"testCloneRepositoryWithDepthInfinite",
				Transport.DEPTH_INFINITE);
		final Git git2 = helperForDepthTestSetupGit(cmd);

		helperForDepthTestCheckShallowFile(git2);
		helperForDepthTestCheckCommits(git2, true, true, true);
		helperForDepthTestCheckFilesExist(git2);
		helperForDepthTestCheckTags(git2, true, true, true);
		helperForDepthTestCheckBranches(git2, true, false);
		helperForDepthTestCheckRefs(git2, true, true);
	}

	private CloneCommand helperForDepthTestSetupCloneCommand(
			final String testName,
			final int depth)
			throws IOException, JGitInternalException {
		final File directory = createTempDirectory(testName);
		final CloneCommand command = Git.cloneRepository();
		command.setDepth(depth);
		// command.setBranch("refs/heads/master");
		// command.setBranchesToClone(
		// Collections.singletonList("refs/heads/master"));
		command.setDirectory(directory);
		command.setURI(fileUri());
		return command;
	}

	private Git helperForDepthTestSetupGit(final CloneCommand command)
			throws IOException, JGitInternalException, GitAPIException {
		final Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertNotNull(git2);
		assertEquals("refs/heads/master", git2.getRepository().getFullBranch());
		return git2;
	}

	private void helperForDepthTestCheckRefs(final Git git2,
			final boolean hasMaster, final boolean hasTest)
			throws JGitInternalException, GitAPIException {
		final StringBuilder expectedRefsList = new StringBuilder("");
		boolean first = true;
		if (hasMaster) {
			expectedRefsList.append("refs/remotes/origin/master");
		}
		if (hasTest) {
			if (first) {
				expectedRefsList.append(", ");
			}
			expectedRefsList.append("refs/remotes/origin/test");
		}
		final String expectedRefs = expectedRefsList.toString();
		final String actualRefs = allRefNames(
				git2.branchList().setListMode(ListMode.REMOTE).call());
		assertEquals(expectedRefs, actualRefs);
		// final Ref masterRef =
		// git2.getRepository().findRef("refs/heads/master");
		// assertNotNull(masterRef);
	}

	private void helperForDepthTestCheckTags(final Git git2,
			final boolean initialTag, final boolean blobTag,
			final boolean anotherFileTag)
			throws JGitInternalException, IOException {
		int tagsCount = 0;
		if (initialTag) {
			tagsCount++;
		}
		if (blobTag) {
			tagsCount++;
		}
		if (anotherFileTag) {
			tagsCount++;
		}

		assertEquals(tagsCount, git2.getRepository().getTags().size());
		final ObjectId initial = git2.getRepository().resolve("tag-initial");
		if (initialTag) {
			assertNotNull(initial);
		} else {
			assertNull(initial);
		}
		final ObjectId blob = git2.getRepository().resolve("tag-for-blob");
		if (blobTag) {
			assertNotNull(blob);
		} else {
			assertNull(blob);
		}
		final ObjectId another = git2.getRepository()
				.resolve("another-file.txt");
		if (anotherFileTag) {
			assertNotNull(another);
		} else {
			assertNull(another);
		}
	}

	private void helperForDepthTestCheckBranches(final Git git2,
			final boolean branchMaster, final boolean branchTest)
			throws GitAPIException {
		int branchCount = 0;
		if (branchMaster) {
			branchCount++;
		}
		if (branchTest) {
			branchCount++;
		}
		final List<Ref> branches = git2.branchList().call();
		assertNotNull(branches);
		int i = 0;
		for (Ref ref : branches) {
			System.out.println("ref('" + i + "')='" + ref + "'");
			i++;
		}
		assertEquals(branchCount, branches.size());
	}

	private void helperForDepthTestCheckCommits(final Git git2,
			final boolean finalCommit, final boolean thirdCommit,
			final boolean initialCommit)
			throws JGitInternalException, GitAPIException {
		int commitCount = 0;
		if (finalCommit) {
			commitCount++;
		}
		if (thirdCommit) {
			commitCount++;
		}
		if (initialCommit) {
			commitCount++;
		}
		final List<RevCommit> commits = getAsList(git2.log().call());
		assertEquals(commitCount, commits.size());
		if (finalCommit) {
			final RevCommit commit0 = commits.get(0);
			assertNotNull(commit0);
			assertEquals("Final commit", commit0.getShortMessage());
		}
		if (thirdCommit) {
			final RevCommit commit1 = commits.get(1);
			assertNotNull(commit1);
			assertEquals("Third commit", commit1.getShortMessage());
		}
		if (initialCommit) {
			final RevCommit commit2 = commits.get(2);
			assertNotNull(commit2);
			System.out.println("	commit2='" + commit2.name() + "'");
			assertEquals("Initial commit", commit2.getShortMessage());
		}
	}

	private void helperForDepthTestCheckFilesExist(final Git git2) {
		final String fileName1 = "Test.txt";
		final String fileName2 = "HelloWorld.txt";
		final String fileName3 = "file.txt";
		assertTrue(new File(git2.getRepository().getWorkTree(),
				File.separatorChar + fileName1).exists());
		assertTrue(new File(git2.getRepository().getWorkTree(),
				File.separatorChar + fileName2).exists());
		assertTrue(new File(git2.getRepository().getWorkTree(),
				File.separatorChar + fileName3).exists());
	}

	private void helperForDepthTestCheckShallowFile(final Git git2,
			final String... expectedIds) throws IOException {
		final FileBasedShallow shallow = new FileBasedShallow(
				git2.getRepository());
		final List<ObjectId> ids = shallow.read();
		assertEquals(expectedIds.length, ids.size());
		for (int i = 0; i < expectedIds.length; i++) {
			final String expectedId = expectedIds[i];
			final ObjectId id = ids.get(i);
			assertEquals(expectedId, id.name());
		}
	}

	// TODO: add JUnit tests for complete shallow/unshallow negotiation

}
