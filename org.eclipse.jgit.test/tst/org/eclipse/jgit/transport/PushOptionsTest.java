/*
 * Copyright (C) 2016, Google Inc.
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

package org.eclipse.jgit.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PushOptionsTest extends RepositoryTestCase {
	private URIish uri;

	private TestProtocol<Object> testProtocol;

	private Object ctx = new Object();

	private InMemoryRepository server;

	private InMemoryRepository client;

	private ObjectId obj1;

	private ObjectId obj2;

	private ReceivePack receivePack;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		server = newRepo("server");
		client = newRepo("client");

		testProtocol = new TestProtocol<>(null,
				(Object req, Repository git) -> {
					receivePack = new ReceivePack(git);
					receivePack.setAllowPushOptions(true);
					receivePack.setAtomic(true);
					return receivePack;
				});

		uri = testProtocol.register(ctx, server);

		try (ObjectInserter ins = client.newObjectInserter()) {
			obj1 = ins.insert(Constants.OBJ_BLOB, Constants.encode("test"));
			obj2 = ins.insert(Constants.OBJ_BLOB, Constants.encode("file"));
			ins.flush();
		}
	}

	@Override
	@After
	public void tearDown() {
		Transport.unregister(testProtocol);
	}

	private static InMemoryRepository newRepo(String name) {
		return new InMemoryRepository(new DfsRepositoryDescription(name));
	}

	private List<RemoteRefUpdate> commands(boolean atomicSafe)
			throws IOException {
		List<RemoteRefUpdate> cmds = new ArrayList<>();
		cmds.add(new RemoteRefUpdate(null, null, obj1, "refs/heads/one",
				true /* force update */, null /* no local tracking ref */,
				ObjectId.zeroId()));
		cmds.add(new RemoteRefUpdate(null, null, obj2, "refs/heads/two",
				true /* force update */, null /* no local tracking ref */,
				atomicSafe ? ObjectId.zeroId() : obj1));
		return cmds;
	}

	private void connectLocalToRemote(Git local, Git remote)
			throws URISyntaxException, IOException {
		StoredConfig config = local.getRepository().getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(config, "test");
		remoteConfig.addURI(new URIish(
				remote.getRepository().getDirectory().toURI().toURL()));
		remoteConfig.addFetchRefSpec(
				new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
		remoteConfig.update(config);
		config.save();
	}

	private RevCommit addCommit(Git git)
			throws IOException, NoFilepatternException, GitAPIException {
		writeTrashFile("f", "content of f");
		git.add().addFilepattern("f").call();
		return git.commit().setMessage("adding f").call();
	}

	@Test
	public void testNonAtomicPushWithOptions() throws Exception {
		PushResult r;
		server.setPerformsAtomicTransactions(false);
		List<String> pushOptions = Arrays.asList("Hello", "World!");

		try (Transport tn = testProtocol.open(uri, client, "server")) {
			tn.setPushAtomic(false);
			tn.setPushOptions(pushOptions);

			r = tn.push(NullProgressMonitor.INSTANCE, commands(false));
		}

		RemoteRefUpdate one = r.getRemoteUpdate("refs/heads/one");
		RemoteRefUpdate two = r.getRemoteUpdate("refs/heads/two");

		assertSame(RemoteRefUpdate.Status.OK, one.getStatus());
		assertSame(RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED,
				two.getStatus());
		assertEquals(pushOptions, receivePack.getPushOptions());
	}

	@Test
	public void testAtomicPushWithOptions() throws Exception {
		PushResult r;
		server.setPerformsAtomicTransactions(true);
		List<String> pushOptions = Arrays.asList("Hello", "World!");

		try (Transport tn = testProtocol.open(uri, client, "server")) {
			tn.setPushAtomic(true);
			tn.setPushOptions(pushOptions);

			r = tn.push(NullProgressMonitor.INSTANCE, commands(true));
		}

		RemoteRefUpdate one = r.getRemoteUpdate("refs/heads/one");
		RemoteRefUpdate two = r.getRemoteUpdate("refs/heads/two");

		assertSame(RemoteRefUpdate.Status.OK, one.getStatus());
		assertSame(RemoteRefUpdate.Status.OK, two.getStatus());
		assertEquals(pushOptions, receivePack.getPushOptions());
	}

	@Test
	public void testFailedAtomicPushWithOptions() throws Exception {
		PushResult r;
		server.setPerformsAtomicTransactions(true);
		List<String> pushOptions = Arrays.asList("Hello", "World!");

		try (Transport tn = testProtocol.open(uri, client, "server")) {
			tn.setPushAtomic(true);
			tn.setPushOptions(pushOptions);

			r = tn.push(NullProgressMonitor.INSTANCE, commands(false));
		}

		RemoteRefUpdate one = r.getRemoteUpdate("refs/heads/one");
		RemoteRefUpdate two = r.getRemoteUpdate("refs/heads/two");

		assertSame(RemoteRefUpdate.Status.REJECTED_OTHER_REASON,
				one.getStatus());
		assertSame(RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED,
				two.getStatus());
		assertNull(receivePack.getPushOptions());
	}

	@Test
	public void testThinPushWithOptions() throws Exception {
		PushResult r;
		List<String> pushOptions = Arrays.asList("Hello", "World!");

		try (Transport tn = testProtocol.open(uri, client, "server")) {
			tn.setPushThin(true);
			tn.setPushOptions(pushOptions);

			r = tn.push(NullProgressMonitor.INSTANCE, commands(false));
		}

		RemoteRefUpdate one = r.getRemoteUpdate("refs/heads/one");
		RemoteRefUpdate two = r.getRemoteUpdate("refs/heads/two");

		assertSame(RemoteRefUpdate.Status.OK, one.getStatus());
		assertSame(RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED,
				two.getStatus());
		assertEquals(pushOptions, receivePack.getPushOptions());
	}

	@Test
	public void testPushWithoutOptions() throws Exception {
		try (Git local = new Git(db);
				Git remote = new Git(createBareRepository())) {
			connectLocalToRemote(local, remote);

			final StoredConfig config2 = remote.getRepository().getConfig();
			config2.setBoolean("receive", null, "pushoptions", true);
			config2.save();

			RevCommit commit = addCommit(local);

			local.checkout().setName("not-pushed").setCreateBranch(true).call();
			local.checkout().setName("branchtopush").setCreateBranch(true)
					.call();

			assertNull(
					remote.getRepository().resolve("refs/heads/branchtopush"));
			assertNull(remote.getRepository().resolve("refs/heads/not-pushed"));
			assertNull(remote.getRepository().resolve("refs/heads/master"));

			PushCommand pushCommand = local.push().setRemote("test");
			pushCommand.call();

			assertEquals(commit.getId(),
					remote.getRepository().resolve("refs/heads/branchtopush"));
			assertNull(remote.getRepository().resolve("refs/heads/not-pushed"));
			assertNull(remote.getRepository().resolve("refs/heads/master"));
		}
	}

	@Test
	public void testPushWithEmptyOptions() throws Exception {
		try (Git local = new Git(db);
				Git remote = new Git(createBareRepository())) {
			connectLocalToRemote(local, remote);

			final StoredConfig config2 = remote.getRepository().getConfig();
			config2.setBoolean("receive", null, "pushoptions", true);
			config2.save();

			RevCommit commit = addCommit(local);

			local.checkout().setName("not-pushed").setCreateBranch(true).call();
			local.checkout().setName("branchtopush").setCreateBranch(true)
					.call();
			assertNull(
					remote.getRepository().resolve("refs/heads/branchtopush"));
			assertNull(remote.getRepository().resolve("refs/heads/not-pushed"));
			assertNull(remote.getRepository().resolve("refs/heads/master"));

			List<String> pushOptions = new ArrayList<>();
			PushCommand pushCommand = local.push().setRemote("test")
					.setPushOptions(pushOptions);
			pushCommand.call();

			assertEquals(commit.getId(),
					remote.getRepository().resolve("refs/heads/branchtopush"));
			assertNull(remote.getRepository().resolve("refs/heads/not-pushed"));
			assertNull(remote.getRepository().resolve("refs/heads/master"));
		}
	}

	@Test
	public void testAdvertisedButUnusedPushOptions() throws Exception {
		try (Git local = new Git(db);
				Git remote = new Git(createBareRepository())) {
			connectLocalToRemote(local, remote);

			final StoredConfig config2 = remote.getRepository().getConfig();
			config2.setBoolean("receive", null, "pushoptions", true);
			config2.save();

			RevCommit commit = addCommit(local);

			local.checkout().setName("not-pushed").setCreateBranch(true).call();
			local.checkout().setName("branchtopush").setCreateBranch(true)
					.call();

			assertNull(
					remote.getRepository().resolve("refs/heads/branchtopush"));
			assertNull(remote.getRepository().resolve("refs/heads/not-pushed"));
			assertNull(remote.getRepository().resolve("refs/heads/master"));

			PushCommand pushCommand = local.push().setRemote("test")
					.setPushOptions(null);
			pushCommand.call();

			assertEquals(commit.getId(),
					remote.getRepository().resolve("refs/heads/branchtopush"));
			assertNull(remote.getRepository().resolve("refs/heads/not-pushed"));
			assertNull(remote.getRepository().resolve("refs/heads/master"));
		}
	}

	@Test(expected = TransportException.class)
	public void testPushOptionsNotSupported() throws Exception {
		try (Git local = new Git(db);
				Git remote = new Git(createBareRepository())) {
			connectLocalToRemote(local, remote);

			final StoredConfig config2 = remote.getRepository().getConfig();
			config2.setBoolean("receive", null, "pushoptions", false);
			config2.save();

			addCommit(local);

			local.checkout().setName("not-pushed").setCreateBranch(true).call();
			local.checkout().setName("branchtopush").setCreateBranch(true)
					.call();

			assertNull(
					remote.getRepository().resolve("refs/heads/branchtopush"));
			assertNull(remote.getRepository().resolve("refs/heads/not-pushed"));
			assertNull(remote.getRepository().resolve("refs/heads/master"));

			List<String> pushOptions = new ArrayList<>();
			PushCommand pushCommand = local.push().setRemote("test")
					.setPushOptions(pushOptions);
			pushCommand.call();

			fail("should already have thrown TransportException");
		}
	}
}
