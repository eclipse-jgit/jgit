/*
 * Copyright (C) 2015, Google Inc.
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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.pack.PackStatistics;
import org.eclipse.jgit.transport.BasePackFetchConnection.FetchConfig;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestProtocolTest {
	private static final RefSpec HEADS = new RefSpec("+refs/heads/*:refs/heads/*");

	private static final RefSpec MASTER = new RefSpec(
			"+refs/heads/master:refs/heads/master");

	private static final int HAVES_PER_ROUND = 32;
	private static final int MAX_HAVES = 256;

	private static class User {
		private final String name;

		private User(String name) {
			this.name = name;
		}
	}

	private static class DefaultUpload implements UploadPackFactory<User> {
		@Override
		public UploadPack create(User req, Repository db) {
			UploadPack up = new UploadPack(db);
			up.setPostUploadHook(new PostUploadHook() {
				@Override
				public void onPostUpload(PackStatistics stats) {
					havesCount = stats.getHaves();
				}
			});
			return up;
		}
	}

	private static class DefaultReceive implements ReceivePackFactory<User> {
		@Override
		public ReceivePack create(User req, Repository db) {
			return new ReceivePack(db);
		}
	}

	private static long havesCount;

	private List<TransportProtocol> protos;
	private TestRepository<InMemoryRepository> local;
	private TestRepository<InMemoryRepository> remote;

  @Before
	public void setUp() throws Exception {
		protos = new ArrayList<>();
		local = new TestRepository<>(
				new InMemoryRepository(new DfsRepositoryDescription("local")));
		remote = new TestRepository<>(
				new InMemoryRepository(new DfsRepositoryDescription("remote")));
  }

	@After
	public void tearDown() {
		for (TransportProtocol proto : protos) {
			Transport.unregister(proto);
		}
	}

	@Test
	public void testFetch() throws Exception {
		ObjectId master = remote.branch("master").commit().create();

		TestProtocol<User> proto = registerDefault();
		URIish uri = proto.register(new User("user"), remote.getRepository());

		try (Git git = new Git(local.getRepository())) {
			git.fetch()
					.setRemote(uri.toString())
					.setRefSpecs(HEADS)
					.call();
			assertEquals(master,
					local.getRepository().exactRef("refs/heads/master").getObjectId());
		}
	}

	@Test
	public void testPush() throws Exception {
		ObjectId master = local.branch("master").commit().create();

		TestProtocol<User> proto = registerDefault();
		URIish uri = proto.register(new User("user"), remote.getRepository());

		try (Git git = new Git(local.getRepository())) {
			git.push()
					.setRemote(uri.toString())
					.setRefSpecs(HEADS)
					.call();
			assertEquals(master,
					remote.getRepository().exactRef("refs/heads/master").getObjectId());
		}
	}

	@Test
	public void testFullNegotiation() throws Exception {
		TestProtocol<User> proto = registerDefault();
		URIish uri = proto.register(new User("user"), remote.getRepository());

		// Enough local branches to cause 10 rounds of negotiation,
		// and a unique remote master branch commit with a later timestamp.
		for (int i = 0; i < 10 * HAVES_PER_ROUND; i++) {
			local.branch("local-branch-" + i).commit().create();
		}
		remote.tick(11 * HAVES_PER_ROUND);
		RevCommit master = remote.branch("master").commit()
				.add("readme.txt", "unique commit").create();

		try (Git git = new Git(local.getRepository())) {
			assertNull(local.getRepository().exactRef("refs/heads/master"));
			git.fetch().setRemote(uri.toString()).setRefSpecs(MASTER).call();
			assertEquals(master, local.getRepository()
					.exactRef("refs/heads/master").getObjectId());
			assertEquals(10 * HAVES_PER_ROUND, havesCount);
		}
	}

	@Test
	public void testMaxHaves() throws Exception {
		TestProtocol<User> proto = registerDefault();
		URIish uri = proto.register(new User("user"), remote.getRepository());

		// Enough local branches to cause 10 rounds of negotiation,
		// and a unique remote master branch commit with a later timestamp.
		for (int i = 0; i < 10 * HAVES_PER_ROUND; i++) {
			local.branch("local-branch-" + i).commit().create();
		}
		remote.tick(11 * HAVES_PER_ROUND);
		RevCommit master = remote.branch("master").commit()
				.add("readme.txt", "unique commit").create();

		TestProtocol.setFetchConfig(new FetchConfig(true, MAX_HAVES));
		try (Git git = new Git(local.getRepository())) {
			assertNull(local.getRepository().exactRef("refs/heads/master"));
			git.fetch().setRemote(uri.toString()).setRefSpecs(MASTER).call();
			assertEquals(master, local.getRepository()
					.exactRef("refs/heads/master").getObjectId());
			assertTrue(havesCount <= MAX_HAVES);
		}
	}

	@Test
	public void testUploadPackFactory() throws Exception {
		ObjectId master = remote.branch("master").commit().create();

		final AtomicInteger rejected = new AtomicInteger();
		TestProtocol<User> proto = registerProto(new UploadPackFactory<User>() {
			@Override
			public UploadPack create(User req, Repository db)
					throws ServiceNotAuthorizedException {
				if (!"user2".equals(req.name)) {
					rejected.incrementAndGet();
					throw new ServiceNotAuthorizedException();
				}
				return new UploadPack(db);
			}
		}, new DefaultReceive());

		// Same repository, different users.
		URIish user1Uri = proto.register(new User("user1"), remote.getRepository());
		URIish user2Uri = proto.register(new User("user2"), remote.getRepository());

		try (Git git = new Git(local.getRepository())) {
			try {
				git.fetch()
						.setRemote(user1Uri.toString())
						.setRefSpecs(MASTER)
						.call();
			} catch (InvalidRemoteException expected) {
				// Expected.
			}
			assertEquals(1, rejected.get());
			assertNull(local.getRepository().exactRef("refs/heads/master"));

			git.fetch()
					.setRemote(user2Uri.toString())
					.setRefSpecs(MASTER)
					.call();
			assertEquals(1, rejected.get());
			assertEquals(master,
					local.getRepository().exactRef("refs/heads/master").getObjectId());
		}
	}

	@Test
	public void testReceivePackFactory() throws Exception {
		ObjectId master = local.branch("master").commit().create();

		final AtomicInteger rejected = new AtomicInteger();
		TestProtocol<User> proto = registerProto(new DefaultUpload(),
				new ReceivePackFactory<User>() {
					@Override
					public ReceivePack create(User req, Repository db)
							throws ServiceNotAuthorizedException {
						if (!"user2".equals(req.name)) {
							rejected.incrementAndGet();
							throw new ServiceNotAuthorizedException();
						}
						return new ReceivePack(db);
					}
				});

		// Same repository, different users.
		URIish user1Uri = proto.register(new User("user1"), remote.getRepository());
		URIish user2Uri = proto.register(new User("user2"), remote.getRepository());

		try (Git git = new Git(local.getRepository())) {
			try {
				git.push()
						.setRemote(user1Uri.toString())
						.setRefSpecs(HEADS)
						.call();
			} catch (TransportException expected) {
				assertTrue(expected.getMessage().contains(
						JGitText.get().pushNotPermitted));
			}
			assertEquals(1, rejected.get());
			assertNull(remote.getRepository().exactRef("refs/heads/master"));

			git.push()
					.setRemote(user2Uri.toString())
					.setRefSpecs(HEADS)
					.call();
			assertEquals(1, rejected.get());
			assertEquals(master,
					remote.getRepository().exactRef("refs/heads/master").getObjectId());
		}
	}

	private TestProtocol<User> registerDefault() {
		return registerProto(new DefaultUpload(), new DefaultReceive());
	}

	private TestProtocol<User> registerProto(UploadPackFactory<User> upf,
			ReceivePackFactory<User> rpf) {
		TestProtocol<User> proto = new TestProtocol<>(upf, rpf);
		protos.add(proto);
		Transport.register(proto);
		return proto;
	}
}
