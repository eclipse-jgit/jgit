/*
 * Copyright (C) 2017 David Pursehouse <david.pursehouse@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.SubmoduleConfig.FetchRecurseSubmodulesMode;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.submodule.SubmoduleStatus;
import org.eclipse.jgit.submodule.SubmoduleStatusType;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Before;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class FetchAndPullCommandsRecurseSubmodulesTest extends RepositoryTestCase {
	@DataPoints
	public static boolean[] useFetch = { true, false };

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

	private final String PATH = "sub";

	@Before
	public void setUpSubmodules() throws Exception {
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
		Repository r2 = sub1Git.submoduleAdd().setPath(PATH)
				.setURI(sub2.getDirectory().toURI().toString()).call();
		assertNotNull(r2);
		addRepoToClose(r2);
		RevCommit sub1Head = sub1Git.commit().setAll(true)
				.setMessage("Adding submodule").call();
		assertNotNull(sub1Head);

		// Add submodule 1 to default repository
		Repository r1 = git.submoduleAdd().setPath(PATH)
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

	@Theory
	public void shouldNotFetchSubmodulesWhenNo(boolean fetch) throws Exception {
		FetchResult result = execute(FetchRecurseSubmodulesMode.NO, fetch);
		assertTrue(result.submoduleResults().isEmpty());
		assertSubmoduleFetchHeads(submodule1Head, submodule2Head);
	}

	@Theory
	public void shouldFetchSubmodulesWhenYes(boolean fetch) throws Exception {
		FetchResult result = execute(FetchRecurseSubmodulesMode.YES, fetch);
		assertTrue(result.submoduleResults().containsKey("sub"));
		FetchResult subResult = result.submoduleResults().get("sub");
		assertTrue(subResult.submoduleResults().containsKey("sub"));
		assertSubmoduleFetchHeads(commit1, commit2);
	}

	@Theory
	public void shouldFetchSubmodulesWhenOnDemandAndRevisionChanged(
			boolean fetch) throws Exception {
		RevCommit update = updateSubmoduleRevision();
		FetchResult result = execute(FetchRecurseSubmodulesMode.ON_DEMAND,
				fetch);

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

	@Theory
	public void shouldNotFetchSubmodulesWhenOnDemandAndRevisionNotChanged(
			boolean fetch) throws Exception {
		FetchResult result = execute(FetchRecurseSubmodulesMode.ON_DEMAND,
				fetch);
		assertTrue(result.submoduleResults().isEmpty());
		assertSubmoduleFetchHeads(submodule1Head, submodule2Head);
	}

	@Theory
	public void shouldNotFetchSubmodulesWhenSubmoduleConfigurationSetToNo(
			boolean fetch) throws Exception {
		StoredConfig config = git2.getRepository().getConfig();
		config.setEnum(ConfigConstants.CONFIG_SUBMODULE_SECTION, PATH,
				ConfigConstants.CONFIG_KEY_FETCH_RECURSE_SUBMODULES,
				FetchRecurseSubmodulesMode.NO);
		config.save();
		updateSubmoduleRevision();
		FetchResult result = execute(null, fetch);
		assertTrue(result.submoduleResults().isEmpty());
		assertSubmoduleFetchHeads(submodule1Head, submodule2Head);
	}

	@Theory
	public void shouldFetchSubmodulesWhenSubmoduleConfigurationSetToYes(
			boolean fetch) throws Exception {
		StoredConfig config = git2.getRepository().getConfig();
		config.setEnum(ConfigConstants.CONFIG_SUBMODULE_SECTION, PATH,
				ConfigConstants.CONFIG_KEY_FETCH_RECURSE_SUBMODULES,
				FetchRecurseSubmodulesMode.YES);
		config.save();
		FetchResult result = execute(null, fetch);
		assertTrue(result.submoduleResults().containsKey("sub"));
		FetchResult subResult = result.submoduleResults().get("sub");
		assertTrue(subResult.submoduleResults().containsKey("sub"));
		assertSubmoduleFetchHeads(commit1, commit2);
	}

	@Theory
	public void shouldNotFetchSubmodulesWhenFetchConfigurationSetToNo(
			boolean fetch) throws Exception {
		StoredConfig config = git2.getRepository().getConfig();
		config.setEnum(ConfigConstants.CONFIG_FETCH_SECTION, null,
				ConfigConstants.CONFIG_KEY_RECURSE_SUBMODULES,
				FetchRecurseSubmodulesMode.NO);
		config.save();
		updateSubmoduleRevision();
		FetchResult result = execute(null, fetch);
		assertTrue(result.submoduleResults().isEmpty());
		assertSubmoduleFetchHeads(submodule1Head, submodule2Head);
	}

	@Theory
	public void shouldFetchSubmodulesWhenFetchConfigurationSetToYes(
			boolean fetch) throws Exception {
		StoredConfig config = git2.getRepository().getConfig();
		config.setEnum(ConfigConstants.CONFIG_FETCH_SECTION, null,
				ConfigConstants.CONFIG_KEY_RECURSE_SUBMODULES,
				FetchRecurseSubmodulesMode.YES);
		config.save();
		FetchResult result = execute(null, fetch);
		assertTrue(result.submoduleResults().containsKey("sub"));
		FetchResult subResult = result.submoduleResults().get("sub");
		assertTrue(subResult.submoduleResults().containsKey("sub"));
		assertSubmoduleFetchHeads(commit1, commit2);
	}

	private RevCommit updateSubmoduleRevision() throws Exception {
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

		return update;
	}

	private FetchResult execute(FetchRecurseSubmodulesMode mode, boolean fetch)
			throws Exception {
		FetchResult result;

		if (fetch) {
			result = git2.fetch().setRemote(REMOTE).setRefSpecs(REFSPEC)
					.setRecurseSubmodules(mode).call();
		} else {
			// For the purposes of this test we don't need to care about the
			// pull result, or the result of pull with merge. We are only
			// interested in checking whether or not the submodules were updated
			// as expected. Setting to rebase makes it easier to assert about
			// the state of the parent repository head, i.e. we know it should
			// be at the submodule update commit, and don't need to consider a
			// merge commit created by the pull.
			result = git2.pull().setRemote(REMOTE).setRebase(true)
					.setRecurseSubmodules(mode).call().getFetchResult();
		}
		assertNotNull(result);
		return result;
	}

	private void assertSubmoduleFetchHeads(ObjectId expectedHead1,
			ObjectId expectedHead2) throws Exception {
		Object newHead1 = null;
		ObjectId newHead2 = null;
		try (SubmoduleWalk walk = SubmoduleWalk
				.forIndex(git2.getRepository())) {
			assertTrue(walk.next());
			try (Repository r = walk.getRepository()) {
				newHead1 = r.resolve(Constants.FETCH_HEAD);
				try (SubmoduleWalk walk2 = SubmoduleWalk.forIndex(r)) {
					assertTrue(walk2.next());
					try (Repository r2 = walk2.getRepository()) {
						newHead2 = r2.resolve(Constants.FETCH_HEAD);
					}
				}
			}
		}
		assertEquals(expectedHead1, newHead1);
		assertEquals(expectedHead2, newHead2);
	}
}
