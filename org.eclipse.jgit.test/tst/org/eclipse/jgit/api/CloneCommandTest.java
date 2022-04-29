/*
 * Copyright (C) 2011, 2013 Chris Aniszczyk <caniszczyk@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
import java.util.stream.Stream;

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
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.submodule.SubmoduleStatus;
import org.eclipse.jgit.submodule.SubmoduleStatusType;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.URIish;
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
		Ref head = git.tag().setName("tag-initial").setMessage("Tag initial")
				.call();

		// create a test branch and switch to it
		git.checkout().setCreateBranch(true).setName("test").call();
		// create a non-standard ref
		RefUpdate ru = db.updateRef("refs/meta/foo/bar");
		ru.setNewObjectId(head.getObjectId());
		ru.update();

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
		assertTagOption(git2.getRepository(), TagOpt.AUTO_FOLLOW);
	}

	@Test
	public void testCloneRepository_refLogForLocalRefs()
			throws IOException, JGitInternalException, GitAPIException {
		File directory = createTempDirectory("testCloneRepository");
		CloneCommand command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setURI(fileUri());
		Git git2 = command.call();
		Repository clonedRepo = git2.getRepository();
		addRepoToClose(clonedRepo);

		List<Ref> clonedRefs = clonedRepo.getRefDatabase().getRefs();
		Stream<Ref> remoteRefs = clonedRefs.stream()
				.filter(CloneCommandTest::isRemote);
		Stream<Ref> localHeadsRefs = clonedRefs.stream()
				.filter(CloneCommandTest::isLocalHead);

		remoteRefs.forEach(ref -> assertFalse(
				"Ref " + ref.getName()
						+ " is remote and should not have a reflog",
				hasRefLog(clonedRepo, ref)));
		localHeadsRefs.forEach(ref -> assertTrue(
				"Ref " + ref.getName()
						+ " is local head and should have a reflog",
				hasRefLog(clonedRepo, ref)));
	}

	private static boolean isRemote(Ref ref) {
		return ref.getName().startsWith(Constants.R_REMOTES);
	}

	private static boolean isLocalHead(Ref ref) {
		return !isRemote(ref) && ref.getName().startsWith(Constants.R_HEADS);
	}

	private static boolean hasRefLog(Repository repo, Ref ref) {
		try {
			return repo.getReflogReader(ref.getName()).getLastEntry() != null;
		} catch (IOException ioe) {
			throw new IllegalStateException(ioe);
		}
	}

	@Test
	public void testCloneRepositoryExplicitGitDir() throws IOException,
			JGitInternalException, GitAPIException {
		File directory = createTempDirectory("testCloneRepository");
		CloneCommand command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setGitDir(new File(directory, Constants.DOT_GIT));
		command.setURI(fileUri());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertEquals(directory, git2.getRepository().getWorkTree());
		assertEquals(new File(directory, Constants.DOT_GIT), git2.getRepository()
				.getDirectory());
	}

	@Test
	public void testCloneRepositoryDefaultDirectory()
			throws URISyntaxException, JGitInternalException {
		CloneCommand command = Git.cloneRepository().setURI(fileUri());

		command.verifyDirectories(new URIish(fileUri()));
		File directory = command.getDirectory();
		assertEquals(git.getRepository().getWorkTree().getName(), directory.getName());
	}

	@Test
	public void testCloneBareRepositoryDefaultDirectory()
			throws URISyntaxException, JGitInternalException {
		CloneCommand command = Git.cloneRepository().setURI(fileUri()).setBare(true);

		command.verifyDirectories(new URIish(fileUri()));
		File directory = command.getDirectory();
		assertEquals(git.getRepository().getWorkTree().getName() + Constants.DOT_GIT_EXT, directory.getName());
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
		assertTrue(new File(directory, Constants.DOT_GIT).isFile());
		assertFalse(new File(gDir, Constants.DOT_GIT).exists());
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

		assertEquals("refs/heads/master", git2.getRepository().getFullBranch());
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

		assertEquals("refs/heads/master", git2.getRepository().getFullBranch());
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

		ObjectId taggedCommit = db.resolve("tag-initial^{commit}");
		assertEquals(taggedCommit.name(), git2
				.getRepository().getFullBranch());
	}

	@Test
	public void testCloneRepositoryOnlyOneBranch() throws Exception {
		File directory = createTempDirectory("testCloneRepositoryWithBranch");
		CloneCommand command = Git.cloneRepository();
		command.setBranch("refs/heads/master");
		command.setBranchesToClone(Collections
				.singletonList("refs/heads/master"));
		command.setDirectory(directory);
		command.setURI(fileUri());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertNull(git2.getRepository().resolve("tag-for-blob"));
		assertNotNull(git2.getRepository().resolve("tag-initial"));
		assertEquals("refs/heads/master", git2.getRepository().getFullBranch());
		assertEquals("refs/remotes/origin/master", allRefNames(git2
				.branchList().setListMode(ListMode.REMOTE).call()));
		RemoteConfig cfg = new RemoteConfig(git2.getRepository().getConfig(),
				Constants.DEFAULT_REMOTE_NAME);
		List<RefSpec> specs = cfg.getFetchRefSpecs();
		assertEquals(1, specs.size());
		assertEquals(
				new RefSpec("+refs/heads/master:refs/remotes/origin/master"),
				specs.get(0));
	}

	@Test
	public void testBareCloneRepositoryOnlyOneBranch() throws Exception {
		File directory = createTempDirectory(
				"testCloneRepositoryWithBranch_bare");
		CloneCommand command = Git.cloneRepository();
		command.setBranch("refs/heads/master");
		command.setBranchesToClone(Collections
				.singletonList("refs/heads/master"));
		command.setDirectory(directory);
		command.setURI(fileUri());
		command.setBare(true);
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertNull(git2.getRepository().resolve("tag-for-blob"));
		assertNotNull(git2.getRepository().resolve("tag-initial"));
		assertEquals("refs/heads/master", git2.getRepository().getFullBranch());
		assertEquals("refs/heads/master", allRefNames(git2.branchList()
				.setListMode(ListMode.ALL).call()));
		RemoteConfig cfg = new RemoteConfig(git2.getRepository().getConfig(),
				Constants.DEFAULT_REMOTE_NAME);
		List<RefSpec> specs = cfg.getFetchRefSpecs();
		assertEquals(1, specs.size());
		assertEquals(
				new RefSpec("+refs/heads/master:refs/heads/master"),
				specs.get(0));
	}

	@Test
	public void testBareCloneRepositoryMirror() throws Exception {
		File directory = createTempDirectory(
				"testCloneRepositoryWithBranch_mirror");
		CloneCommand command = Git.cloneRepository();
		command.setBranch("refs/heads/master");
		command.setMirror(true); // implies bare repository
		command.setDirectory(directory);
		command.setURI(fileUri());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertTrue(git2.getRepository().isBare());
		assertNotNull(git2.getRepository().resolve("tag-for-blob"));
		assertNotNull(git2.getRepository().resolve("tag-initial"));
		assertEquals("refs/heads/master", git2.getRepository().getFullBranch());
		assertEquals("refs/heads/master, refs/heads/test", allRefNames(
				git2.branchList().setListMode(ListMode.ALL).call()));
		assertNotNull(git2.getRepository().exactRef("refs/meta/foo/bar"));
		RemoteConfig cfg = new RemoteConfig(git2.getRepository().getConfig(),
				Constants.DEFAULT_REMOTE_NAME);
		List<RefSpec> specs = cfg.getFetchRefSpecs();
		assertEquals(1, specs.size());
		assertEquals(new RefSpec("+refs/*:refs/*"),
				specs.get(0));
	}

	@Test
	public void testCloneRepositoryOnlyOneTag() throws Exception {
		File directory = createTempDirectory("testCloneRepositoryWithBranch");
		CloneCommand command = Git.cloneRepository();
		command.setBranch("tag-initial");
		command.setBranchesToClone(
				Collections.singletonList("refs/tags/tag-initial"));
		command.setDirectory(directory);
		command.setURI(fileUri());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertNull(git2.getRepository().resolve("tag-for-blob"));
		assertNull(git2.getRepository().resolve("refs/heads/master"));
		assertNotNull(git2.getRepository().resolve("tag-initial"));
		ObjectId taggedCommit = db.resolve("tag-initial^{commit}");
		assertEquals(taggedCommit.name(), git2.getRepository().getFullBranch());
		RemoteConfig cfg = new RemoteConfig(git2.getRepository().getConfig(),
				Constants.DEFAULT_REMOTE_NAME);
		List<RefSpec> specs = cfg.getFetchRefSpecs();
		assertEquals(1, specs.size());
		assertEquals(
				new RefSpec("+refs/tags/tag-initial:refs/tags/tag-initial"),
				specs.get(0));
	}

	@Test
	public void testCloneRepositoryAllBranchesTakesPreference()
			throws Exception {
		File directory = createTempDirectory(
				"testCloneRepositoryAllBranchesTakesPreference");
		CloneCommand command = Git.cloneRepository();
		command.setCloneAllBranches(true);
		command.setBranchesToClone(
				Collections.singletonList("refs/heads/test"));
		command.setDirectory(directory);
		command.setURI(fileUri());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertEquals("refs/heads/test", git2.getRepository().getFullBranch());
		// Expect both remote branches to exist; setCloneAllBranches(true)
		// should override any setBranchesToClone().
		assertNotNull(
				git2.getRepository().resolve("refs/remotes/origin/master"));
		assertNotNull(git2.getRepository().resolve("refs/remotes/origin/test"));
		RemoteConfig cfg = new RemoteConfig(git2.getRepository().getConfig(),
				Constants.DEFAULT_REMOTE_NAME);
		List<RefSpec> specs = cfg.getFetchRefSpecs();
		assertEquals(1, specs.size());
		assertEquals(new RefSpec("+refs/heads/*:refs/remotes/origin/*"),
				specs.get(0));
	}

	@Test
	public void testCloneRepositoryAllBranchesIndependent() throws Exception {
		File directory = createTempDirectory(
				"testCloneRepositoryAllBranchesIndependent");
		CloneCommand command = Git.cloneRepository();
		command.setCloneAllBranches(true);
		command.setBranchesToClone(
				Collections.singletonList("refs/heads/test"));
		command.setCloneAllBranches(false);
		command.setDirectory(directory);
		command.setURI(fileUri());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertEquals("refs/heads/test", git2.getRepository().getFullBranch());
		// Expect only the test branch; allBranches was re-set to false
		assertNull(git2.getRepository().resolve("refs/remotes/origin/master"));
		assertNotNull(git2.getRepository().resolve("refs/remotes/origin/test"));
		RemoteConfig cfg = new RemoteConfig(git2.getRepository().getConfig(),
				Constants.DEFAULT_REMOTE_NAME);
		List<RefSpec> specs = cfg.getFetchRefSpecs();
		assertEquals(1, specs.size());
		assertEquals(new RefSpec("+refs/heads/test:refs/remotes/origin/test"),
				specs.get(0));
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

		try (SubmoduleWalk walk = SubmoduleWalk
				.forIndex(git2.getRepository())) {
			assertTrue(walk.next());
			try (Repository clonedSub1 = walk.getRepository()) {
				assertNotNull(clonedSub1);
				assertEquals(new File(git2.getRepository().getWorkTree(),
						walk.getPath()), clonedSub1.getWorkTree());
				assertEquals(
						new File(new File(git2.getRepository().getDirectory(),
								"modules"), walk.getPath()),
						clonedSub1.getDirectory());
				status = new SubmoduleStatusCommand(clonedSub1);
				statuses = status.call();
			}
			assertFalse(walk.next());
		}
		pathStatus = statuses.get(path);
		assertNotNull(pathStatus);
		assertEquals(SubmoduleStatusType.INITIALIZED, pathStatus.getType());
		assertEquals(sub2Head, pathStatus.getHeadId());
		assertEquals(sub2Head, pathStatus.getIndexId());
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

		StoredConfig userConfig = SystemReader.getInstance()
				.getUserConfig();
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

	@Test
	public void testCloneWithPullMerge() throws Exception {
		File directory = createTempDirectory("testCloneRepository1");
		try (Git g = Git.init().setDirectory(directory).setBare(false).call()) {
			g.remoteAdd().setName(Constants.DEFAULT_REMOTE_NAME)
					.setUri(new URIish(fileUri())).call();
			PullResult result = g.pull().setRebase(false).call();
			assertTrue(result.isSuccessful());
			assertEquals("refs/heads/master",
					g.getRepository().getFullBranch());
			checkFile(new File(directory, "Test.txt"), "Hello world");
		}
	}

	@Test
	public void testCloneWithPullRebase() throws Exception {
		File directory = createTempDirectory("testCloneRepository1");
		try (Git g = Git.init().setDirectory(directory).setBare(false).call()) {
			g.remoteAdd().setName(Constants.DEFAULT_REMOTE_NAME)
					.setUri(new URIish(fileUri())).call();
			PullResult result = g.pull().setRebase(true).call();
			assertTrue(result.isSuccessful());
			assertEquals("refs/heads/master",
					g.getRepository().getFullBranch());
			checkFile(new File(directory, "Test.txt"), "Hello world");
		}
	}

	@Test
	public void testCloneNoTags() throws IOException, JGitInternalException,
			GitAPIException, URISyntaxException {
		File directory = createTempDirectory("testCloneRepository");
		CloneCommand command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setURI(fileUri());
		command.setNoTags();
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertNotNull(git2.getRepository().resolve("refs/heads/test"));
		assertNull(git2.getRepository().resolve("tag-initial"));
		assertNull(git2.getRepository().resolve("tag-for-blob"));
		assertTagOption(git2.getRepository(), TagOpt.NO_TAGS);
	}

	@Test
	public void testCloneFollowTags() throws IOException, JGitInternalException,
			GitAPIException, URISyntaxException {
		File directory = createTempDirectory("testCloneRepository");
		CloneCommand command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setURI(fileUri());
		command.setBranch("refs/heads/master");
		command.setBranchesToClone(
				Collections.singletonList("refs/heads/master"));
		command.setTagOption(TagOpt.FETCH_TAGS);
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertNull(git2.getRepository().resolve("refs/heads/test"));
		assertNotNull(git2.getRepository().resolve("tag-initial"));
		assertNotNull(git2.getRepository().resolve("tag-for-blob"));
		assertTagOption(git2.getRepository(), TagOpt.FETCH_TAGS);
	}

	@Test
	public void testCloneWithHeadSymRefIsMasterCopy() throws IOException, GitAPIException {
		// create a branch with the same head as master and switch to it
		git.checkout().setStartPoint("master").setCreateBranch(true).setName("master-copy").call();

		// when we clone the HEAD symref->master-copy means we start on master-copy and not master
		File directory = createTempDirectory("testCloneRepositorySymRef_master-copy");
		CloneCommand command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setURI(fileUri());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertEquals("refs/heads/master-copy", git2.getRepository().getFullBranch());
	}

	@Test
	public void testCloneWithHeadSymRefIsNonMasterCopy() throws IOException, GitAPIException {
		// create a branch with the same head as test and switch to it
		git.checkout().setStartPoint("test").setCreateBranch(true).setName("test-copy").call();

		File directory = createTempDirectory("testCloneRepositorySymRef_test-copy");
		CloneCommand command = Git.cloneRepository();
		command.setDirectory(directory);
		command.setURI(fileUri());
		Git git2 = command.call();
		addRepoToClose(git2.getRepository());
		assertEquals("refs/heads/test-copy", git2.getRepository().getFullBranch());
	}

	private void assertTagOption(Repository repo, TagOpt expectedTagOption)
			throws URISyntaxException {
		RemoteConfig remoteConfig = new RemoteConfig(
				repo.getConfig(), "origin");
		assertEquals(expectedTagOption, remoteConfig.getTagOpt());
	}

	private String fileUri() {
		return "file://" + git.getRepository().getWorkTree().getAbsolutePath();
	}
}
