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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
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
	private ObjectId obj1;
	private ObjectId obj2;

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

		try (ObjectInserter ins = client.newObjectInserter()) {
			obj1 = ins.insert(Constants.OBJ_BLOB, Constants.encode("test"));
			obj2 = ins.insert(Constants.OBJ_BLOB, Constants.encode("file"));
			ins.flush();
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
				obj1, "refs/heads/one",
				true /* force update */,
				null /* no local tracking ref */,
				ObjectId.zeroId()));
		cmds.add(new RemoteRefUpdate(
				null, null,
				obj2, "refs/heads/two",
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
				obj1, "refs/heads/one",
				true /* force update */,
				null /* no local tracking ref */,
				ObjectId.zeroId()));
		cmds.add(new RemoteRefUpdate(
				null, null,
				obj2, "refs/heads/two",
				true /* force update */,
				null /* no local tracking ref */,
				obj1));
		return cmds;
	}
}
