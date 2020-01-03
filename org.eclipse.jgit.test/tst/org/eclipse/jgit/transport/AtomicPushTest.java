/*
 * Copyright (C) 2015, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AtomicPushTest {
	private URIish uri;
	private TestProtocol<Object> testProtocol;
	private Object ctx = new Object();
	private InMemoryRepository server;
	private InMemoryRepository client;
	private ObjectId commit1;
	private ObjectId commit2;

	@Before
	public void setUp() throws Exception {
		server = newRepo("server");
		client = newRepo("client");
		testProtocol = new TestProtocol<>(
				null,
				new ReceivePackFactory<Object>() {
					@Override
					public ReceivePack create(Object req, Repository db)
							throws ServiceNotEnabledException,
							ServiceNotAuthorizedException {
						return new ReceivePack(db);
					}
				});
		uri = testProtocol.register(ctx, server);

		try (TestRepository<?> clientRepo = new TestRepository<>(client)) {
			commit1 = clientRepo.commit().noFiles().message("test commit 1")
					.create();
			commit2 = clientRepo.commit().noFiles().message("test commit 2")
					.create();
		}
	}

	@After
	public void tearDown() {
		Transport.unregister(testProtocol);
	}

	private static InMemoryRepository newRepo(String name) {
		return new InMemoryRepository(new DfsRepositoryDescription(name));
	}

	@Test
	public void pushNonAtomic() throws Exception {
		PushResult r;
		server.setPerformsAtomicTransactions(false);
		try (Transport tn = testProtocol.open(uri, client, "server")) {
			tn.setPushAtomic(false);
			r = tn.push(NullProgressMonitor.INSTANCE, commands());
		}

		RemoteRefUpdate one = r.getRemoteUpdate("refs/heads/one");
		RemoteRefUpdate two = r.getRemoteUpdate("refs/heads/two");
		assertSame(RemoteRefUpdate.Status.OK, one.getStatus());
		assertSame(
				RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED,
				two.getStatus());
	}

	@Test
	public void pushAtomicClientGivesUpEarly() throws Exception {
		PushResult r;
		try (Transport tn = testProtocol.open(uri, client, "server")) {
			tn.setPushAtomic(true);
			r = tn.push(NullProgressMonitor.INSTANCE, commands());
		}

		RemoteRefUpdate one = r.getRemoteUpdate("refs/heads/one");
		RemoteRefUpdate two = r.getRemoteUpdate("refs/heads/two");
		assertSame(
				RemoteRefUpdate.Status.REJECTED_OTHER_REASON,
				one.getStatus());
		assertSame(
				RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED,
				two.getStatus());
		assertEquals(JGitText.get().transactionAborted, one.getMessage());
	}

	@Test
	public void pushAtomicDisabled() throws Exception {
		List<RemoteRefUpdate> cmds = new ArrayList<>();
		cmds.add(new RemoteRefUpdate(
				null, null,
				commit1, "refs/heads/one",
				true /* force update */,
				null /* no local tracking ref */,
				ObjectId.zeroId()));
		cmds.add(new RemoteRefUpdate(
				null, null,
				commit2, "refs/heads/two",
				true /* force update */,
				null /* no local tracking ref */,
				ObjectId.zeroId()));

		server.setPerformsAtomicTransactions(false);
		try (Transport tn = testProtocol.open(uri, client, "server")) {
			tn.setPushAtomic(true);
			tn.push(NullProgressMonitor.INSTANCE, cmds);
			fail("did not throw TransportException");
		} catch (TransportException e) {
			assertEquals(
					uri + ": " + JGitText.get().atomicPushNotSupported,
					e.getMessage());
		}
	}

	private List<RemoteRefUpdate> commands() throws IOException {
		List<RemoteRefUpdate> cmds = new ArrayList<>();
		cmds.add(new RemoteRefUpdate(
				null, null,
				commit1, "refs/heads/one",
				true /* force update */,
				null /* no local tracking ref */,
				ObjectId.zeroId()));
		cmds.add(new RemoteRefUpdate(
				null, null,
				commit2, "refs/heads/two",
				true /* force update */,
				null /* no local tracking ref */,
				commit1));
		return cmds;
	}
}
