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

import static org.eclipse.jgit.transport.BasePackPushConnection.CAPABILITY_REPORT_STATUS;
import static org.eclipse.jgit.transport.BasePackPushConnection.CAPABILITY_SIDE_BAND_64K;
import static org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_OTHER_REASON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PushConnectionTest {
	private URIish uri;
	private TestProtocol<Object> testProtocol;
	private Object ctx = new Object();
	private InMemoryRepository server;
	private InMemoryRepository client;
	private ObjectId obj1;
	private ObjectId obj2;
	private ObjectId obj3;
	private String refName = "refs/tags/blob";

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

		try (ObjectInserter ins = server.newObjectInserter()) {
			obj1 = ins.insert(Constants.OBJ_BLOB, Constants.encode("test"));
			obj3 = ins.insert(Constants.OBJ_BLOB, Constants.encode("not"));
			ins.flush();

			RefUpdate u = server.updateRef(refName);
			u.setNewObjectId(obj1);
			assertEquals(RefUpdate.Result.NEW, u.update());
		}

		try (ObjectInserter ins = client.newObjectInserter()) {
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
	public void testWrongOldIdDoesNotReplace() throws IOException {
		RemoteRefUpdate rru = new RemoteRefUpdate(null, null, obj2, refName,
				false, null, obj3);

		Map<String, RemoteRefUpdate> updates = new HashMap<>();
		updates.put(rru.getRemoteName(), rru);

		try (Transport tn = testProtocol.open(uri, client, "server");
				PushConnection connection = tn.openPush()) {
			connection.push(NullProgressMonitor.INSTANCE, updates);
		}

		assertEquals(REJECTED_OTHER_REASON, rru.getStatus());
		assertEquals("invalid old id sent", rru.getMessage());
	}

	@Test
	public void invalidCommand() throws IOException {
		try (Transport tn = testProtocol.open(uri, client, "server");
				InternalPushConnection c = (InternalPushConnection) tn.openPush()) {
			StringWriter msgs = new StringWriter();
			PacketLineOut pckOut = c.pckOut;

			@SuppressWarnings("resource")
			SideBandInputStream in = new SideBandInputStream(c.in,
					NullProgressMonitor.INSTANCE, msgs, null);

			// Explicitly invalid command, but sane enough capabilities.
			StringBuilder buf = new StringBuilder();
			buf.append("42");
			buf.append(' ');
			buf.append(obj2.name());
			buf.append(' ');
			buf.append("refs/heads/A" + obj2.name());
			buf.append('\0').append(CAPABILITY_SIDE_BAND_64K);
			buf.append(' ').append(CAPABILITY_REPORT_STATUS);
			buf.append('\n');
			pckOut.writeString(buf.toString());
			pckOut.end();

			try {
				in.read();
				fail("expected TransportException");
			} catch (TransportException e) {
				assertEquals(
						"remote: error: invalid protocol: wanted 'old new ref'",
						e.getMessage());
			}
		}
	}

	@Test
	public void limitCommandBytes() throws IOException {
		Map<String, RemoteRefUpdate> updates = new HashMap<>();
		for (int i = 0; i < 4; i++) {
			RemoteRefUpdate rru = new RemoteRefUpdate(
					null, null, obj2, "refs/test/T" + i,
					false, null, ObjectId.zeroId());
			updates.put(rru.getRemoteName(), rru);
		}

		server.getConfig().setInt("receive", null, "maxCommandBytes", 190);
		try (Transport tn = testProtocol.open(uri, client, "server");
				PushConnection connection = tn.openPush()) {
			try {
				connection.push(NullProgressMonitor.INSTANCE, updates);
				fail("server did not abort");
			} catch (TransportException e) {
				String msg = e.getMessage();
				assertEquals("remote: Too many commands", msg);
			}
		}
	}
}
