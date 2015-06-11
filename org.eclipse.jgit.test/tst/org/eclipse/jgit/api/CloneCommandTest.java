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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.submodule.SubmoduleStatus;
import org.eclipse.jgit.submodule.SubmoduleStatusType;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
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
		try (
				Git git2 = Git.cloneRepository()
						.setDirectory(directory)
						.setURI(fileUri())
						.call();
				Repository db2 = git2.getRepository()) {
			assertNotNull(db2.resolve("tag-for-blob"));
			assertEquals(db2.getFullBranch(), "refs/heads/test");
			assertEquals("origin",
					db2.getConfig().getString("branch", "test", "remote"));
			assertEquals("refs/heads/test",
					db2.getConfig().getString("branch", "test", "merge"));
			assertEquals(2,
					git2.branchList()
							.setListMode(ListMode.REMOTE)
							.call()
							.size());
			assertEquals(new RefSpec("+refs/heads/*:refs/remotes/origin/*"),
					fetchRefSpec(db2));
		}
	}

	@Test
	public void testCloneRepositoryExplicitGitDir() throws IOException,
			JGitInternalException, GitAPIException {
		File directory = createTempDirectory("testCloneRepository");
		try (
				Git git2 = Git.cloneRepository()
						.setDirectory(directory)
						.setGitDir(new File(directory, ".git"))
						.setURI(fileUri())
						.call();
				Repository db2 = git2.getRepository()) {
			assertEquals(directory, db2.getWorkTree());
			assertEquals(new File(directory, ".git"), db2.getDirectory());
		}
	}

	@Test
	public void testCloneRepositoryExplicitGitDirNonStd() throws IOException,
			JGitInternalException, GitAPIException {
		File directory = createTempDirectory("testCloneRepository");
		File gDir = createTempDirectory("testCloneRepository.git");
		try (
				Git git2 = Git.cloneRepository()
						.setDirectory(directory)
						.setGitDir(gDir)
						.setURI(fileUri())
						.call();
				Repository db2 = git2.getRepository()) {
			assertEquals(directory, db2.getWorkTree());
			assertEquals(gDir, db2.getDirectory());
			assertTrue(new File(directory, ".git").isFile());
			assertFalse(new File(gDir, ".git").exists());
		}
	}

	@Test
	public void testCloneRepositoryExplicitGitDirBare() throws IOException,
			JGitInternalException, GitAPIException {
		File gDir = createTempDirectory("testCloneRepository.git");
		try (
				Git git2 = Git.cloneRepository()
						.setBare(true)
						.setGitDir(gDir)
						.setURI(fileUri())
						.call();
				Repository db2 = git2.getRepository()) {
			try {
				db2.getWorkTree();
				fail("Expected NoWorkTreeException");
			} catch (NoWorkTreeException e) {
				assertEquals(gDir, db2.getDirectory());
			}
		}
	}

	@Test
	public void testBareCloneRepository() throws IOException,
			JGitInternalException, GitAPIException, URISyntaxException {
		File directory = createTempDirectory("testCloneRepository_bare");
		try (
				Git git2 = Git.cloneRepository()
						.setBare(true)
						.setDirectory(directory)
						.setURI(fileUri())
						.call();
				Repository db2 = git2.getRepository()) {
			assertEquals(new RefSpec("+refs/heads/*:refs/heads/*"),
					fetchRefSpec(db2));
		}
	}

	@Test
	public void testCloneRepositoryCustomRemote() throws Exception {
		File directory = createTempDirectory("testCloneRemoteUpstream");
		try (
				Git git2 = Git.cloneRepository()
						.setDirectory(directory)
						.setRemote("upstream")
						.setURI(fileUri())
						.call();
				Repository db2 = git2.getRepository()) {
			assertEquals("+refs/heads/*:refs/remotes/upstream/*",
					db2.getConfig().getStringList(
							"remote", "upstream", "fetch")[0]);
			assertEquals("upstream",
					db2.getConfig().getString("branch", "test", "remote"));
			assertEquals(db.resolve("test"),
					git2.getRepository().resolve("upstream/test"));
		}
	}

	@Test
	public void testBareCloneRepositoryCustomRemote() throws Exception {
		File directory = createTempDirectory("testCloneRemoteUpstream_bare");
		try (
				Git git2 = Git.cloneRepository()
						.setBare(true)
						.setDirectory(directory)
						.setRemote("upstream")
						.setURI(fileUri())
						.call();
				Repository db2 = git2.getRepository()) {
			assertEquals("+refs/heads/*:refs/heads/*",
					db2.getConfig().getStringList(
							"remote", "upstream", "fetch")[0]);
			assertEquals("upstream",
					db2.getConfig().getString("branch", "test", "remote"));
			assertNull(git2.getRepository().resolve("upstream/test"));
		}
	}

	@Test
	public void testBareCloneRepositoryNullRemote() throws Exception {
		File directory = createTempDirectory("testCloneRemoteNull_bare");
		try (
				Git git2 = Git.cloneRepository()
						.setBare(true)
						.setDirectory(directory)
						.setRemote(null)
						.setURI(fileUri())
						.call();
				Repository db2 = git2.getRepository()) {
			assertEquals("+refs/heads/*:refs/heads/*",
					db2.getConfig().getStringList(
							"remote", "origin", "fetch")[0]);
			assertEquals("origin",
					db2.getConfig().getString("branch", "test", "remote"));
		}
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
		try (
				Git git2 = Git.cloneRepository()
						.setBranch("refs/heads/master")
						.setDirectory(directory)
						.setURI(fileUri())
						.call();
				Repository db2 = git2.getRepository()) {
			assertEquals("refs/heads/master", db2.getFullBranch());
			assertEquals(
					"refs/heads/master, refs/remotes/origin/master, refs/remotes/origin/test",
					allRefNames(git2.branchList().setListMode(ListMode.ALL).call()));
		}

		// Same thing, but now without checkout
		directory = createTempDirectory("testCloneRepositoryWithBranch_bare");
		try (
				Git git2 = Git.cloneRepository()
						.setBranch("refs/heads/master")
						.setDirectory(directory)
						.setURI(fileUri())
						.setNoCheckout(true)
						.call();
				Repository db2 = git2.getRepository()) {
			assertEquals("refs/heads/master", db2.getFullBranch());
			assertEquals("refs/remotes/origin/master, refs/remotes/origin/test",
					allRefNames(git2.branchList().setListMode(ListMode.ALL).call()));
		}

		// Same thing, but now test with bare repo
		directory = createTempDirectory("testCloneRepositoryWithBranch_bare");
		try (
				Git git2 = Git.cloneRepository()
						.setBranch("refs/heads/master")
						.setDirectory(directory)
						.setURI(fileUri())
						.setBare(true)
						.call();
				Repository db2 = git2.getRepository()) {
			assertEquals("refs/heads/master", db2.getFullBranch());
			assertEquals("refs/heads/master, refs/heads/test",
					allRefNames(git2.branchList().setListMode(ListMode.ALL).call()));
		}
	}

	@Test
	public void testCloneRepositoryWithBranchShortName() throws Exception {
		File directory = createTempDirectory("testCloneRepositoryWithBranch");
		try (
				Git git2 = Git.cloneRepository()
						.setBranch("test")
						.setDirectory(directory)
						.setURI(fileUri())
						.call();
				Repository db2 = git2.getRepository()) {
			assertEquals("refs/heads/test", db2.getFullBranch());
		}
	}

	@Test
	public void testCloneRepositoryWithTagName() throws Exception {
		File directory = createTempDirectory("testCloneRepositoryWithBranch");
		try (
				Git git2 = Git.cloneRepository()
						.setBranch("tag-initial")
						.setDirectory(directory)
						.setURI(fileUri())
						.call();
				Repository db2 = git2.getRepository()) {
			ObjectId taggedCommit = db.resolve("tag-initial^{commit}");
			assertEquals(taggedCommit.name(), db2.getFullBranch());
		}
	}

	@Test
	public void testCloneRepositoryOnlyOneBranch() throws IOException,
			JGitInternalException, GitAPIException {
		File directory = createTempDirectory("testCloneRepositoryWithBranch");
		try (
				Git git2 = Git.cloneRepository()
						.setBranch("refs/heads/master")
						.setBranchesToClone(
								Collections.singletonList("refs/heads/master"))
						.setDirectory(directory)
						.setURI(fileUri())
						.call();
				Repository db2 = git2.getRepository()) {
			assertEquals("refs/heads/master", db2.getFullBranch());
			assertEquals("refs/remotes/origin/master",
					allRefNames(git2.branchList().setListMode(ListMode.REMOTE).call()));
		}

		// Same thing, but now test with bare repo
		directory = createTempDirectory("testCloneRepositoryWithBranch_bare");
		try (
				Git git2 = Git.cloneRepository()
						.setBranch("refs/heads/master")
						.setBranchesToClone(
								Collections.singletonList("refs/heads/master"))
						.setDirectory(directory)
						.setURI(fileUri())
						.setBare(true)
						.call();
				Repository db2 = git2.getRepository()) {
			assertEquals("refs/heads/master", db2.getFullBranch());
			assertEquals("refs/heads/master",
					allRefNames(git2.branchList().setListMode(ListMode.ALL).call()));
		}
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
		Git.cloneRepository()
				.setDirectory(directory)
				.setURI(fileUri())
				.call()
				.getRepository()
				.close();
		try (
				Git git2 = Git.cloneRepository()
						.setDirectory(directory)
						.setURI(fileUri())
						.call();
				Repository db2 = git2.getRepository()) {
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
		try (
				Git git2 = Git.cloneRepository()
						.setDirectory(directory)
						.setURI(fileUri())
						.call();
				Repository db2 = git2.getRepository()) {
			assertEquals(Constants.MASTER, db2.getBranch());
		}
	}

	@Test
	public void testCloneRepositoryWithSubmodules() throws Exception {
		git.checkout().setName(Constants.MASTER).call();

		String file = "file.txt";
		writeTrashFile(file, "content");
		git.add().addFilepattern(file).call();
		RevCommit commit = git.commit().setMessage("create file").call();

		String path = "sub";
		String uri = db.getDirectory().toURI().toString();
		git.submoduleAdd()
				.setPath(path)
				.setURI(uri)
				.call()
				.close();
		git.add()
				.addFilepattern(path)
				.addFilepattern(Constants.DOT_GIT_MODULES)
				.call();
		git.commit().setMessage("adding submodule").call();
		try (SubmoduleWalk walk = SubmoduleWalk.forIndex(db)) {
			assertTrue(walk.next());
			Repository subRepo = walk.getRepository();
			addRepoToClose(subRepo);
			assertNotNull(subRepo);
			assertEquals(new File(db.getWorkTree(), walk.getPath()),
					subRepo.getWorkTree());
			assertEquals(
					new File(
							new File(db.getDirectory(), "modules"),
							walk.getPath()),
					subRepo.getDirectory());
		}

		File directory = createTempDirectory("testCloneRepositoryWithSubmodules");
		try (
				Git git2 = Git.cloneRepository()
						.setDirectory(directory)
						.setCloneSubmodules(true)
						.setURI(fileUri())
						.call();
				Repository db2 = git2.getRepository()) {
			assertEquals(Constants.MASTER, db2.getBranch());
			assertTrue(
					new File(db2.getWorkTree(),
							path + File.separatorChar + file)
					.exists());

			Map<String, SubmoduleStatus> statuses =
					git2.submoduleStatus().call();
			SubmoduleStatus pathStatus = statuses.get(path);
			assertNotNull(pathStatus);
			assertEquals(SubmoduleStatusType.INITIALIZED,
					pathStatus.getType());
			assertEquals(commit, pathStatus.getHeadId());
			assertEquals(commit, pathStatus.getIndexId());

			try (SubmoduleWalk walk = SubmoduleWalk.forIndex(db2)) {
				assertTrue(walk.next());
				Repository clonedSub1 = walk.getRepository();
				addRepoToClose(clonedSub1);
				assertNotNull(clonedSub1);
				assertEquals(new File(db2.getWorkTree(), walk.getPath()),
						clonedSub1.getWorkTree());
				assertEquals(
						new File(
								new File(db2.getDirectory(), "modules"),
								walk.getPath()),
						clonedSub1.getDirectory());
			}
		}
	}

	@Test
	public void testCloneRepositoryWithNestedSubmodules() throws Exception {
		git.checkout().setName(Constants.MASTER).call();

		File submodule1 = createTempDirectory("testCloneRepositoryWithNestedSubmodules1");
		File submodule2 = createTempDirectory("testCloneRepositoryWithNestedSubmodules2");
		try (
				Git sub1Git = Git.init()
						.setDirectory(submodule1)
						.call();
				Repository sub1 = sub1Git.getRepository();
				Git sub2Git = Git.init()
						.setDirectory(submodule2)
						.call();
				Repository sub2 = sub2Git.getRepository()) {
			String file = "file.txt";
			String path = "sub";

			// Populate submodule 1
			write(new File(sub1.getWorkTree(), file), "content");
			sub1Git.add().addFilepattern(file).call();
			RevCommit commit = sub1Git.commit()
					.setMessage("create file")
					.call();
			assertNotNull(commit);

			// Populate submodule 2
			write(new File(sub2.getWorkTree(), file), "content");
			sub2Git.add().addFilepattern(file).call();
			RevCommit sub2Head = sub2Git.commit()
					.setMessage("create file")
					.call();
			assertNotNull(sub2Head);

			// Add submodule 2 to submodule 1
			RevCommit sub1Head;
			sub1Git.submoduleAdd()
					.setPath(path)
					.setURI(sub2.getDirectory().toURI().toString())
					.call()
					.close();
			sub1Head = sub1Git.commit()
					.setAll(true)
					.setMessage("Adding submodule")
					.call();
			assertNotNull(sub1Head);

			// Add submodule 1 to default repository
			git.submoduleAdd()
					.setPath(path)
					.setURI(sub1.getDirectory().toURI().toString())
					.call()
					.close();
			assertNotNull(git.commit()
					.setAll(true)
					.setMessage("Adding submodule")
					.call());

			// Clone default repository and include submodules
			File directory = createTempDirectory("testCloneRepositoryWithNestedSubmodules");
			try (
					Git git2 = Git.cloneRepository()
							.setDirectory(directory)
							.setCloneSubmodules(true)
							.setURI(db.getDirectory().toURI().toString())
							.call();
					Repository db2 = git2.getRepository()) {
				assertEquals(Constants.MASTER, db2.getBranch());
				assertTrue(
						new File(db2.getWorkTree(),
								path + File.separatorChar + file)
						.exists());
				assertTrue(
						new File(db2.getWorkTree(),
								path + File.separatorChar
										+ path + File.separatorChar
										+ file)
						.exists());

				Map<String, SubmoduleStatus> statuses =
						git2.submoduleStatus().call();
				SubmoduleStatus pathStatus = statuses.get(path);
				assertNotNull(pathStatus);
				assertEquals(SubmoduleStatusType.INITIALIZED, pathStatus.getType());
				assertEquals(sub1Head, pathStatus.getHeadId());
				assertEquals(sub1Head, pathStatus.getIndexId());

				SubmoduleWalk walk = SubmoduleWalk.forIndex(db2);
				assertTrue(walk.next());
				try (Repository clonedSub1 = walk.getRepository()) {
					assertNotNull(clonedSub1);
					assertEquals(
							new File(db2.getWorkTree(), walk.getPath()),
							clonedSub1.getWorkTree());
					assertEquals(
							new File(
									new File(db2.getDirectory(), "modules"),
									walk.getPath()),
							clonedSub1.getDirectory());
					statuses = new Git(clonedSub1).submoduleStatus().call();
				}
				pathStatus = statuses.get(path);
				assertNotNull(pathStatus);
				assertEquals(SubmoduleStatusType.INITIALIZED, pathStatus.getType());
				assertEquals(sub2Head, pathStatus.getHeadId());
				assertEquals(sub2Head, pathStatus.getIndexId());
				assertFalse(walk.next());
			}
		}
	}

	@Test
	public void testCloneWithAutoSetupRebase() throws Exception {
		File directory = createTempDirectory("testCloneRepository1");
		try (
				Git git2 = Git.cloneRepository()
						.setDirectory(directory)
						.setURI(fileUri())
						.call();
				Repository db2 = git2.getRepository()) {
			assertFalse(
					db2.getConfig()
							.getBoolean("branch", "test", "rebase", false));
		}

		FileBasedConfig userConfig = SystemReader.getInstance().openUserConfig(
				null, git.getRepository().getFS());
		userConfig.setString(ConfigConstants.CONFIG_BRANCH_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOSETUPREBASE,
				ConfigConstants.CONFIG_KEY_ALWAYS);
		userConfig.save();
		directory = createTempDirectory("testCloneRepository2");
		try (
				Git git2 = Git.cloneRepository()
						.setDirectory(directory)
						.setURI(fileUri())
						.call();
				Repository db2 = git2.getRepository()) {
			assertTrue(
					db2.getConfig()
							.getBoolean("branch", "test", "rebase", false));
		}

		userConfig.setString(ConfigConstants.CONFIG_BRANCH_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOSETUPREBASE,
				ConfigConstants.CONFIG_KEY_REMOTE);
		userConfig.save();
		directory = createTempDirectory("testCloneRepository2");
		try (
				Git git2 = Git.cloneRepository()
						.setDirectory(directory)
						.setURI(fileUri())
						.call();
				Repository db2 = git2.getRepository()) {
			assertTrue(
					db2.getConfig()
							.getBoolean("branch", "test", "rebase", false));
		}
	}

	private String fileUri() {
		return "file://" + git.getRepository().getWorkTree().getAbsolutePath();
	}
}
