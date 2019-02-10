/*
 * Copyright (C) 2010, 2014 Chris Aniszczyk <caniszczyk@gmail.com> and others
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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

import org.eclipse.jgit.api.errors.DetachedHeadException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.hooks.PrePushHook;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushConfig.PushDefault;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefLeaseSpec;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

public class PushCommandTest extends RepositoryTestCase {

	@Test
	public void testPush() throws JGitInternalException, IOException,
			GitAPIException, URISyntaxException {

		// create other repository
		Repository db2 = createWorkRepository();
		addRepoToClose(db2);
		final StoredConfig config2 = db2.getConfig();

		// this tests that this config can be parsed properly
		config2.setString("fsck", "", "missingEmail", "ignore");
		config2.save();

		// setup the first repository
		final StoredConfig config = db.getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(config, "test");
		URIish uri = new URIish(db2.getDirectory().toURI().toURL());
		remoteConfig.addURI(uri);
		remoteConfig.update(config);
		config.save();

		try (Git git1 = new Git(db)) {
			// create some refs via commits and tag
			RevCommit commit = git1.commit().setMessage("initial commit").call();
			Ref tagRef = git1.tag().setName("tag").call();

			try {
				db2.resolve(commit.getId().getName() + "^{commit}");
				fail("id shouldn't exist yet");
			} catch (MissingObjectException e) {
				// we should get here
			}

			RefSpec spec = new RefSpec("refs/heads/master:refs/heads/x");
			git1.push().setRemote("test").setRefSpecs(spec)
					.call();

			assertEquals(commit.getId(),
					db2.resolve(commit.getId().getName() + "^{commit}"));
			assertEquals(tagRef.getObjectId(),
					db2.resolve(tagRef.getObjectId().getName()));
		}
	}

	@Test
	public void testPrePushHook() throws JGitInternalException, IOException,
			GitAPIException, URISyntaxException {

		// create other repository
		Repository db2 = createWorkRepository();

		// setup the first repository
		final StoredConfig config = db.getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(config, "test");
		URIish uri = new URIish(db2.getDirectory().toURI().toURL());
		remoteConfig.addURI(uri);
		remoteConfig.update(config);
		config.save();

		File hookOutput = new File(getTemporaryDirectory(), "hookOutput");
		writeHookFile(PrePushHook.NAME, "#!/bin/sh\necho 1:$1, 2:$2, 3:$3 >\""
				+ hookOutput.toPath() + "\"\ncat - >>\"" + hookOutput.toPath()
				+ "\"\nexit 0");

		try (Git git1 = new Git(db)) {
			// create some refs via commits and tag
			RevCommit commit = git1.commit().setMessage("initial commit").call();

			RefSpec spec = new RefSpec("refs/heads/master:refs/heads/x");
			git1.push().setRemote("test").setRefSpecs(spec).call();
			assertEquals("1:test, 2:" + uri + ", 3:\n" + "refs/heads/master "
					+ commit.getName() + " refs/heads/x "
					+ ObjectId.zeroId().name() + "\n", read(hookOutput));
		}
	}

	private File writeHookFile(String name, String data)
			throws IOException {
		File path = new File(db.getWorkTree() + "/.git/hooks/", name);
		JGitTestUtil.write(path, data);
		FS.DETECTED.setExecute(path, true);
		return path;
	}


	@Test
	public void testTrackingUpdate() throws Exception {
		Repository db2 = createBareRepository();

		String remote = "origin";
		String branch = "refs/heads/master";
		String trackingBranch = "refs/remotes/" + remote + "/master";

		try (Git git = new Git(db)) {
			RevCommit commit1 = git.commit().setMessage("Initial commit")
					.call();

			RefUpdate branchRefUpdate = db.updateRef(branch);
			branchRefUpdate.setNewObjectId(commit1.getId());
			branchRefUpdate.update();

			RefUpdate trackingBranchRefUpdate = db.updateRef(trackingBranch);
			trackingBranchRefUpdate.setNewObjectId(commit1.getId());
			trackingBranchRefUpdate.update();

			final StoredConfig config = db.getConfig();
			RemoteConfig remoteConfig = new RemoteConfig(config, remote);
			URIish uri = new URIish(db2.getDirectory().toURI().toURL());
			remoteConfig.addURI(uri);
			remoteConfig.addFetchRefSpec(new RefSpec("+refs/heads/*:refs/remotes/"
					+ remote + "/*"));
			remoteConfig.update(config);
			config.save();


			RevCommit commit2 = git.commit().setMessage("Commit to push").call();

			RefSpec spec = new RefSpec(branch + ":" + branch);
			Iterable<PushResult> resultIterable = git.push().setRemote(remote)
					.setRefSpecs(spec).call();

			PushResult result = resultIterable.iterator().next();
			TrackingRefUpdate trackingRefUpdate = result
					.getTrackingRefUpdate(trackingBranch);

			assertNotNull(trackingRefUpdate);
			assertEquals(trackingBranch, trackingRefUpdate.getLocalName());
			assertEquals(branch, trackingRefUpdate.getRemoteName());
			assertEquals(commit2.getId(), trackingRefUpdate.getNewObjectId());
			assertEquals(commit2.getId(), db.resolve(trackingBranch));
			assertEquals(commit2.getId(), db2.resolve(branch));
		}
	}

	/**
	 * Check that pushes over file protocol lead to appropriate ref-updates.
	 *
	 * @throws Exception
	 */
	@Test
	public void testPushRefUpdate() throws Exception {
		try (Git git = new Git(db);
				Git git2 = new Git(createBareRepository())) {
			final StoredConfig config = git.getRepository().getConfig();
			RemoteConfig remoteConfig = new RemoteConfig(config, "test");
			URIish uri = new URIish(git2.getRepository().getDirectory().toURI()
					.toURL());
			remoteConfig.addURI(uri);
			remoteConfig.addPushRefSpec(new RefSpec("+refs/heads/*:refs/heads/*"));
			remoteConfig.update(config);
			config.save();

			writeTrashFile("f", "content of f");
			git.add().addFilepattern("f").call();
			RevCommit commit = git.commit().setMessage("adding f").call();

			assertEquals(null, git2.getRepository().resolve("refs/heads/master"));
			git.push().setRemote("test").call();
			assertEquals(commit.getId(),
					git2.getRepository().resolve("refs/heads/master"));

			git.branchCreate().setName("refs/heads/test").call();
			git.checkout().setName("refs/heads/test").call();

			for (int i = 0; i < 6; i++) {
				writeTrashFile("f" + i, "content of f" + i);
				git.add().addFilepattern("f" + i).call();
				commit = git.commit().setMessage("adding f" + i).call();
				git.push().setRemote("test").call();
				git2.getRepository().getRefDatabase().getRefs();
				assertEquals("failed to update on attempt " + i, commit.getId(),
						git2.getRepository().resolve("refs/heads/test"));
			}
		}
	}

	/**
	 * Check that the push refspec is read from config.
	 *
	 * @throws Exception
	 */
	@Test
	public void testPushWithRefSpecFromConfig() throws Exception {
		try (Git git = new Git(db);
				Git git2 = new Git(createBareRepository())) {
			final StoredConfig config = git.getRepository().getConfig();
			RemoteConfig remoteConfig = new RemoteConfig(config, "test");
			URIish uri = new URIish(git2.getRepository().getDirectory().toURI()
					.toURL());
			remoteConfig.addURI(uri);
			remoteConfig.addPushRefSpec(new RefSpec("HEAD:refs/heads/newbranch"));
			remoteConfig.update(config);
			config.save();

			writeTrashFile("f", "content of f");
			git.add().addFilepattern("f").call();
			RevCommit commit = git.commit().setMessage("adding f").call();

			assertEquals(null, git2.getRepository().resolve("refs/heads/master"));
			git.push().setRemote("test").call();
			assertEquals(commit.getId(),
					git2.getRepository().resolve("refs/heads/newbranch"));
		}
	}

	/**
	 * Check that only HEAD is pushed if no refspec is given.
	 *
	 * @throws Exception
	 */
	@Test
	public void testPushWithoutPushRefSpec() throws Exception {
		try (Git git = new Git(db);
				Git git2 = new Git(createBareRepository())) {
			final StoredConfig config = git.getRepository().getConfig();
			RemoteConfig remoteConfig = new RemoteConfig(config, "test");
			URIish uri = new URIish(git2.getRepository().getDirectory().toURI()
					.toURL());
			remoteConfig.addURI(uri);
			remoteConfig.addFetchRefSpec(new RefSpec(
					"+refs/heads/*:refs/remotes/origin/*"));
			remoteConfig.update(config);
			config.save();

			writeTrashFile("f", "content of f");
			git.add().addFilepattern("f").call();
			RevCommit commit = git.commit().setMessage("adding f").call();

			git.checkout().setName("not-pushed").setCreateBranch(true).call();
			git.checkout().setName("branchtopush").setCreateBranch(true).call();

			assertEquals(null,
					git2.getRepository().resolve("refs/heads/branchtopush"));
			assertEquals(null, git2.getRepository()
					.resolve("refs/heads/not-pushed"));
			assertEquals(null, git2.getRepository().resolve("refs/heads/master"));
			git.push().setRemote("test").call();
			assertEquals(commit.getId(),
					git2.getRepository().resolve("refs/heads/branchtopush"));
			assertEquals(null, git2.getRepository()
					.resolve("refs/heads/not-pushed"));
			assertEquals(null, git2.getRepository().resolve("refs/heads/master"));
		}
	}

	/**
	 * Check that pushing from a detached HEAD without refspec throws a
	 * DetachedHeadException.
	 *
	 * @throws Exception
	 */
	@Test
	public void testPushDefaultDetachedHead() throws Exception {
		try (Git git = new Git(db);
				Git git2 = new Git(createBareRepository())) {
			final StoredConfig config = git.getRepository().getConfig();
			RemoteConfig remoteConfig = new RemoteConfig(config, "test");
			URIish uri = new URIish(
					git2.getRepository().getDirectory().toURI().toURL());
			remoteConfig.addURI(uri);
			remoteConfig.addFetchRefSpec(
					new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
			remoteConfig.update(config);
			config.save();

			writeTrashFile("f", "content of f");
			git.add().addFilepattern("f").call();
			RevCommit commit = git.commit().setMessage("adding f").call();

			git.checkout().setName("not-pushed").setCreateBranch(true).call();
			git.checkout().setName("branchtopush").setCreateBranch(true).call();
			git.checkout().setName(commit.getName()).call();
			String head = git.getRepository().getFullBranch();
			assertTrue(ObjectId.isId(head));
			assertEquals(commit.getName(), head);
			assertThrows(DetachedHeadException.class,
					() -> git.push().setRemote("test").call());
		}
	}

	/**
	 * Check that push.default=nothing without refspec throws an
	 * InvalidRefNameException.
	 *
	 * @throws Exception
	 */
	@Test
	public void testPushDefaultNothing() throws Exception {
		try (Git git = new Git(db);
				Git git2 = new Git(createBareRepository())) {
			final StoredConfig config = git.getRepository().getConfig();
			RemoteConfig remoteConfig = new RemoteConfig(config, "test");
			URIish uri = new URIish(
					git2.getRepository().getDirectory().toURI().toURL());
			remoteConfig.addURI(uri);
			remoteConfig.addFetchRefSpec(
					new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
			remoteConfig.update(config);
			config.save();

			writeTrashFile("f", "content of f");
			git.add().addFilepattern("f").call();
			git.commit().setMessage("adding f").call();

			git.checkout().setName("not-pushed").setCreateBranch(true).call();
			git.checkout().setName("branchtopush").setCreateBranch(true).call();

			assertEquals(null,
					git2.getRepository().resolve("refs/heads/branchtopush"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/not-pushed"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/master"));
			assertThrows(InvalidRefNameException.class,
					() -> git.push().setRemote("test")
							.setPushDefault(PushDefault.NOTHING).call());
		}
	}

	/**
	 * Check that push.default=matching without refspec pushes all matching
	 * branches.
	 *
	 * @throws Exception
	 */
	@Test
	public void testPushDefaultMatching() throws Exception {
		try (Git git = new Git(db);
				Git git2 = new Git(createBareRepository())) {
			final StoredConfig config = git.getRepository().getConfig();
			RemoteConfig remoteConfig = new RemoteConfig(config, "test");
			URIish uri = new URIish(
					git2.getRepository().getDirectory().toURI().toURL());
			remoteConfig.addURI(uri);
			remoteConfig.addFetchRefSpec(
					new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
			remoteConfig.update(config);
			config.save();

			writeTrashFile("f", "content of f");
			git.add().addFilepattern("f").call();
			RevCommit commit = git.commit().setMessage("adding f").call();

			git.checkout().setName("also-pushed").setCreateBranch(true).call();
			git.checkout().setName("branchtopush").setCreateBranch(true).call();

			assertEquals(null,
					git2.getRepository().resolve("refs/heads/branchtopush"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/also-pushed"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/master"));
			git.push().setRemote("test").setPushDefault(PushDefault.MATCHING)
					.call();
			assertEquals(commit.getId(),
					git2.getRepository().resolve("refs/heads/branchtopush"));
			assertEquals(commit.getId(),
					git2.getRepository().resolve("refs/heads/also-pushed"));
			assertEquals(commit.getId(),
					git2.getRepository().resolve("refs/heads/master"));
			assertEquals(commit.getId(), git.getRepository()
					.resolve("refs/remotes/origin/branchtopush"));
			assertEquals(commit.getId(), git.getRepository()
					.resolve("refs/remotes/origin/also-pushed"));
			assertEquals(commit.getId(),
					git.getRepository().resolve("refs/remotes/origin/master"));
		}
	}

	/**
	 * Check that push.default=upstream without refspec pushes only the current
	 * branch to the configured upstream.
	 *
	 * @throws Exception
	 */
	@Test
	public void testPushDefaultUpstream() throws Exception {
		try (Git git = new Git(db);
				Git git2 = new Git(createBareRepository())) {
			StoredConfig config = git.getRepository().getConfig();
			RemoteConfig remoteConfig = new RemoteConfig(config, "test");
			URIish uri = new URIish(
					git2.getRepository().getDirectory().toURI().toURL());
			remoteConfig.addURI(uri);
			remoteConfig.addFetchRefSpec(
					new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
			remoteConfig.update(config);
			config.save();

			writeTrashFile("f", "content of f");
			git.add().addFilepattern("f").call();
			RevCommit commit = git.commit().setMessage("adding f").call();

			git.checkout().setName("not-pushed").setCreateBranch(true).call();
			git.checkout().setName("branchtopush").setCreateBranch(true).call();

			config = git.getRepository().getConfig();
			config.setString("branch", "branchtopush", "remote", "test");
			config.setString("branch", "branchtopush", "merge",
					"refs/heads/upstreambranch");
			config.save();

			assertEquals(null,
					git2.getRepository().resolve("refs/heads/branchtopush"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/upstreambranch"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/not-pushed"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/master"));
			git.push().setRemote("test").setPushDefault(PushDefault.UPSTREAM)
					.call();
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/branchtopush"));
			assertEquals(commit.getId(),
					git2.getRepository().resolve("refs/heads/upstreambranch"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/not-pushed"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/master"));
			assertEquals(commit.getId(), git.getRepository()
					.resolve("refs/remotes/origin/upstreambranch"));
			assertEquals(null, git.getRepository()
					.resolve("refs/remotes/origin/branchtopush"));
		}
	}

	/**
	 * Check that push.default=upstream without refspec throws an
	 * InvalidRefNameException if the current branch has no upstream.
	 *
	 * @throws Exception
	 */
	@Test
	public void testPushDefaultUpstreamNoTracking() throws Exception {
		try (Git git = new Git(db);
				Git git2 = new Git(createBareRepository())) {
			StoredConfig config = git.getRepository().getConfig();
			RemoteConfig remoteConfig = new RemoteConfig(config, "test");
			URIish uri = new URIish(
					git2.getRepository().getDirectory().toURI().toURL());
			remoteConfig.addURI(uri);
			remoteConfig.addFetchRefSpec(
					new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
			remoteConfig.update(config);
			config.save();

			writeTrashFile("f", "content of f");
			git.add().addFilepattern("f").call();
			git.commit().setMessage("adding f").call();

			git.checkout().setName("not-pushed").setCreateBranch(true).call();
			git.checkout().setName("branchtopush").setCreateBranch(true).call();

			config = git.getRepository().getConfig();
			config.setString("branch", "branchtopush", "remote", "test");
			config.save();

			assertThrows(InvalidRefNameException.class,
					() -> git.push().setRemote("test")
							.setPushDefault(PushDefault.UPSTREAM).call());
		}
	}

	/**
	 * Check that push.default=upstream without refspec throws an
	 * InvalidRefNameException if the push remote is not the same as the fetch
	 * remote.
	 *
	 * @throws Exception
	 */
	@Test
	public void testPushDefaultUpstreamTriangular() throws Exception {
		try (Git git = new Git(db);
				Git git2 = new Git(createBareRepository())) {
			StoredConfig config = git.getRepository().getConfig();
			RemoteConfig remoteConfig = new RemoteConfig(config, "test");
			URIish uri = new URIish(
					git2.getRepository().getDirectory().toURI().toURL());
			remoteConfig.addURI(uri);
			remoteConfig.addFetchRefSpec(
					new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
			remoteConfig.update(config);
			config.save();

			writeTrashFile("f", "content of f");
			git.add().addFilepattern("f").call();
			git.commit().setMessage("adding f").call();

			git.checkout().setName("not-pushed").setCreateBranch(true).call();
			git.checkout().setName("branchtopush").setCreateBranch(true).call();

			config = git.getRepository().getConfig();
			// Don't configure a remote; it'll default to "origin".
			config.setString("branch", "branchtopush", "merge",
					"upstreambranch");
			config.save();

			assertThrows(InvalidRefNameException.class,
					() -> git.push().setRemote("test")
							.setPushDefault(PushDefault.UPSTREAM).call());
		}
	}

	/**
	 * Check that push.default=simple without refspec pushes only the current
	 * branch to the configured upstream name.
	 *
	 * @throws Exception
	 */
	@Test
	public void testPushDefaultSimple() throws Exception {
		try (Git git = new Git(db);
				Git git2 = new Git(createBareRepository())) {
			StoredConfig config = git.getRepository().getConfig();
			RemoteConfig remoteConfig = new RemoteConfig(config, "test");
			URIish uri = new URIish(
					git2.getRepository().getDirectory().toURI().toURL());
			remoteConfig.addURI(uri);
			remoteConfig.addFetchRefSpec(
					new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
			remoteConfig.update(config);
			config.save();

			writeTrashFile("f", "content of f");
			git.add().addFilepattern("f").call();
			RevCommit commit = git.commit().setMessage("adding f").call();

			git.checkout().setName("not-pushed").setCreateBranch(true).call();
			git.checkout().setName("branchtopush").setCreateBranch(true).call();

			config = git.getRepository().getConfig();
			config.setString("branch", "branchtopush", "remote", "test");
			config.setString("branch", "branchtopush", "merge",
					"refs/heads/branchtopush");
			config.save();

			assertEquals(null,
					git2.getRepository().resolve("refs/heads/branchtopush"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/not-pushed"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/master"));
			git.push().setRemote("test").setPushDefault(PushDefault.SIMPLE)
					.call();
			assertEquals(commit.getId(),
					git2.getRepository().resolve("refs/heads/branchtopush"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/not-pushed"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/master"));
			assertEquals(commit.getId(), git.getRepository()
					.resolve("refs/remotes/origin/branchtopush"));
		}
	}

	/**
	 * Check that push.default=simple without refspec pushes only the current
	 * branch to a branch with the same name in a triangular workflow.
	 *
	 * @throws Exception
	 */
	@Test
	public void testPushDefaultSimpleTriangular() throws Exception {
		try (Git git = new Git(db);
				Git git2 = new Git(createBareRepository())) {
			StoredConfig config = git.getRepository().getConfig();
			RemoteConfig remoteConfig = new RemoteConfig(config, "test");
			URIish uri = new URIish(
					git2.getRepository().getDirectory().toURI().toURL());
			remoteConfig.addURI(uri);
			remoteConfig.addFetchRefSpec(
					new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
			remoteConfig.update(config);
			config.save();

			writeTrashFile("f", "content of f");
			git.add().addFilepattern("f").call();
			RevCommit commit = git.commit().setMessage("adding f").call();

			git.checkout().setName("not-pushed").setCreateBranch(true).call();
			git.checkout().setName("branchtopush").setCreateBranch(true).call();

			config = git.getRepository().getConfig();
			// Don't set remote, it'll default to "origin". Configure a
			// different
			// branch name; should be ignored.
			config.setString("branch", "branchtopush", "merge",
					"refs/heads/upstreambranch");
			config.save();

			assertEquals(null,
					git2.getRepository().resolve("refs/heads/branchtopush"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/upstreambranch"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/not-pushed"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/master"));
			git.push().setRemote("test").setPushDefault(PushDefault.SIMPLE)
					.call();
			assertEquals(commit.getId(),
					git2.getRepository().resolve("refs/heads/branchtopush"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/upstreambranch"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/not-pushed"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/master"));
			assertEquals(commit.getId(), git.getRepository()
					.resolve("refs/remotes/origin/branchtopush"));
		}
	}

	/**
	 * Check that push.default=simple without refspec throws an
	 * InvalidRefNameException if the current branch has no upstream.
	 *
	 * @throws Exception
	 */
	@Test
	public void testPushDefaultSimpleNoTracking() throws Exception {
		try (Git git = new Git(db);
				Git git2 = new Git(createBareRepository())) {
			StoredConfig config = git.getRepository().getConfig();
			RemoteConfig remoteConfig = new RemoteConfig(config, "test");
			URIish uri = new URIish(
					git2.getRepository().getDirectory().toURI().toURL());
			remoteConfig.addURI(uri);
			remoteConfig.addFetchRefSpec(
					new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
			remoteConfig.update(config);
			config.save();

			writeTrashFile("f", "content of f");
			git.add().addFilepattern("f").call();
			git.commit().setMessage("adding f").call();

			git.checkout().setName("not-pushed").setCreateBranch(true).call();
			git.checkout().setName("branchtopush").setCreateBranch(true).call();

			config = git.getRepository().getConfig();
			config.setString("branch", "branchtopush", "remote", "test");
			config.save();

			assertThrows(InvalidRefNameException.class,
					() -> git.push().setRemote("test")
							.setPushDefault(PushDefault.SIMPLE).call());
		}
	}

	/**
	 * Check that push.default=simple without refspec throws an
	 * InvalidRefNameException if the current branch has an upstream with a
	 * different name.
	 *
	 * @throws Exception
	 */
	@Test
	public void testPushDefaultSimpleDifferentTracking() throws Exception {
		try (Git git = new Git(db);
				Git git2 = new Git(createBareRepository())) {
			StoredConfig config = git.getRepository().getConfig();
			RemoteConfig remoteConfig = new RemoteConfig(config, "test");
			URIish uri = new URIish(
					git2.getRepository().getDirectory().toURI().toURL());
			remoteConfig.addURI(uri);
			remoteConfig.addFetchRefSpec(
					new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
			remoteConfig.update(config);
			config.save();

			writeTrashFile("f", "content of f");
			git.add().addFilepattern("f").call();
			git.commit().setMessage("adding f").call();

			git.checkout().setName("not-pushed").setCreateBranch(true).call();
			git.checkout().setName("branchtopush").setCreateBranch(true).call();

			config = git.getRepository().getConfig();
			config.setString("branch", "branchtopush", "remote", "test");
			config.setString("branch", "branchtopush", "merge",
					"refs/heads/upstreambranch");
			config.save();

			assertThrows(InvalidRefNameException.class,
					() -> git.push().setRemote("test")
							.setPushDefault(PushDefault.SIMPLE).call());
		}
	}

	/**
	 * Check that if no PushDefault is set, the value is read from the git
	 * config.
	 *
	 * @throws Exception
	 */
	@Test
	public void testPushDefaultFromConfig() throws Exception {
		try (Git git = new Git(db);
				Git git2 = new Git(createBareRepository())) {
			StoredConfig config = git.getRepository().getConfig();
			RemoteConfig remoteConfig = new RemoteConfig(config, "test");
			URIish uri = new URIish(
					git2.getRepository().getDirectory().toURI().toURL());
			remoteConfig.addURI(uri);
			remoteConfig.addFetchRefSpec(
					new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
			remoteConfig.update(config);
			config.setString("push", null, "default", "upstream");
			config.save();

			writeTrashFile("f", "content of f");
			git.add().addFilepattern("f").call();
			RevCommit commit = git.commit().setMessage("adding f").call();

			git.checkout().setName("not-pushed").setCreateBranch(true).call();
			git.checkout().setName("branchtopush").setCreateBranch(true).call();

			config = git.getRepository().getConfig();
			config.setString("branch", "branchtopush", "remote", "test");
			config.setString("branch", "branchtopush", "merge",
					"refs/heads/upstreambranch");
			config.save();

			assertEquals(null,
					git2.getRepository().resolve("refs/heads/branchtopush"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/upstreambranch"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/not-pushed"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/master"));
			PushCommand cmd = git.push();
			cmd.setRemote("test").setPushDefault(null).call();
			assertEquals(PushDefault.UPSTREAM, cmd.getPushDefault());
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/branchtopush"));
			assertEquals(commit.getId(),
					git2.getRepository().resolve("refs/heads/upstreambranch"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/not-pushed"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/master"));
			assertEquals(commit.getId(), git.getRepository()
					.resolve("refs/remotes/origin/upstreambranch"));
			assertEquals(null, git.getRepository()
					.resolve("refs/remotes/origin/branchtopush"));
		}
	}

	/**
	 * Check that if no PushDefault is set and none is set in the git config, it
	 * defaults to "simple".
	 *
	 * @throws Exception
	 */
	@Test
	public void testPushDefaultFromConfigDefault() throws Exception {
		try (Git git = new Git(db);
				Git git2 = new Git(createBareRepository())) {
			StoredConfig config = git.getRepository().getConfig();
			RemoteConfig remoteConfig = new RemoteConfig(config, "test");
			URIish uri = new URIish(
					git2.getRepository().getDirectory().toURI().toURL());
			remoteConfig.addURI(uri);
			remoteConfig.addFetchRefSpec(
					new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
			remoteConfig.update(config);
			config.save();

			writeTrashFile("f", "content of f");
			git.add().addFilepattern("f").call();
			RevCommit commit = git.commit().setMessage("adding f").call();

			git.checkout().setName("not-pushed").setCreateBranch(true).call();
			git.checkout().setName("branchtopush").setCreateBranch(true).call();

			config = git.getRepository().getConfig();
			config.setString("branch", "branchtopush", "remote", "test");
			config.setString("branch", "branchtopush", "merge",
					"refs/heads/branchtopush");
			config.save();

			assertEquals(null,
					git2.getRepository().resolve("refs/heads/branchtopush"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/not-pushed"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/master"));
			PushCommand cmd = git.push();
			cmd.setRemote("test").setPushDefault(null).call();
			assertEquals(PushDefault.SIMPLE, cmd.getPushDefault());
			assertEquals(commit.getId(),
					git2.getRepository().resolve("refs/heads/branchtopush"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/not-pushed"));
			assertEquals(null,
					git2.getRepository().resolve("refs/heads/master"));
			assertEquals(commit.getId(), git.getRepository()
					.resolve("refs/remotes/origin/branchtopush"));
		}
	}

	/**
	 * Check that missing refs don't cause errors during push
	 *
	 * @throws Exception
	 */
	@Test
	public void testPushAfterGC() throws Exception {
		// create other repository
		Repository db2 = createWorkRepository();
		addRepoToClose(db2);

		// setup the first repository
		final StoredConfig config = db.getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(config, "test");
		URIish uri = new URIish(db2.getDirectory().toURI().toURL());
		remoteConfig.addURI(uri);
		remoteConfig.update(config);
		config.save();

		try (Git git1 = new Git(db);
				Git git2 = new Git(db2)) {
			// push master (with a new commit) to the remote
			git1.commit().setMessage("initial commit").call();

			RefSpec spec = new RefSpec("refs/heads/*:refs/heads/*");
			git1.push().setRemote("test").setRefSpecs(spec).call();

			// create an unrelated ref and a commit on our remote
			git2.branchCreate().setName("refs/heads/other").call();
			git2.checkout().setName("refs/heads/other").call();

			writeTrashFile("a", "content of a");
			git2.add().addFilepattern("a").call();
			RevCommit commit2 = git2.commit().setMessage("adding a").call();

			// run a gc to ensure we have a bitmap index
			Properties res = git1.gc().setExpire(null).call();
			assertEquals(8, res.size());

			// create another commit so we have something else to push
			writeTrashFile("b", "content of b");
			git1.add().addFilepattern("b").call();
			RevCommit commit3 = git1.commit().setMessage("adding b").call();

			try {
				// Re-run the push.  Failure may happen here.
				git1.push().setRemote("test").setRefSpecs(spec).call();
			} catch (TransportException e) {
				assertTrue("should be caused by a MissingObjectException", e
						.getCause().getCause() instanceof MissingObjectException);
				fail("caught MissingObjectException for a change we don't have");
			}

			// Remote will have both a and b.  Master will have only b
			try {
				db.resolve(commit2.getId().getName() + "^{commit}");
				fail("id shouldn't exist locally");
			} catch (MissingObjectException e) {
				// we should get here
			}
			assertEquals(commit2.getId(),
					db2.resolve(commit2.getId().getName() + "^{commit}"));
			assertEquals(commit3.getId(),
					db2.resolve(commit3.getId().getName() + "^{commit}"));
		}
	}

	@Test
	public void testPushWithLease() throws JGitInternalException, IOException,
			GitAPIException, URISyntaxException {

		// create other repository
		Repository db2 = createWorkRepository();
		addRepoToClose(db2);

		// setup the first repository
		final StoredConfig config = db.getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(config, "test");
		URIish uri = new URIish(db2.getDirectory().toURI().toURL());
		remoteConfig.addURI(uri);
		remoteConfig.update(config);
		config.save();

		try (Git git1 = new Git(db)) {
			// create one commit and push it
			RevCommit commit = git1.commit().setMessage("initial commit").call();
			git1.branchCreate().setName("initial").call();

			RefSpec spec = new RefSpec("refs/heads/master:refs/heads/x");
			git1.push().setRemote("test").setRefSpecs(spec)
					.call();

			assertEquals(commit.getId(),
					db2.resolve(commit.getId().getName() + "^{commit}"));
			//now try to force-push a new commit, with a good lease

			git1.commit().setMessage("second commit").call();
			Iterable<PushResult> results =
					git1.push().setRemote("test").setRefSpecs(spec)
							.setRefLeaseSpecs(new RefLeaseSpec("refs/heads/x", "initial"))
							.call();
			for (PushResult result : results) {
				RemoteRefUpdate update = result.getRemoteUpdate("refs/heads/x");
				assertEquals(update.getStatus(), RemoteRefUpdate.Status.OK);
			}

			git1.commit().setMessage("third commit").call();
			//now try to force-push a new commit, with a bad lease

			results =
					git1.push().setRemote("test").setRefSpecs(spec)
							.setRefLeaseSpecs(new RefLeaseSpec("refs/heads/x", "initial"))
							.call();
			for (PushResult result : results) {
				RemoteRefUpdate update = result.getRemoteUpdate("refs/heads/x");
				assertEquals(update.getStatus(), RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED);
			}
		}
	}
}
