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

import static org.eclipse.jgit.transport.BasePackPushConnection.CAPABILITY_REPORT_STATUS;
import static org.eclipse.jgit.transport.BasePackPushConnection.CAPABILITY_SIDE_BAND_64K;
import static org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_OTHER_REASON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PushConnectionTest {
	private URIish uri;
	private TestProtocol<Object> testProtocol;
	private Object ctx = new Object();
	private InMemoryRepository server;
	private InMemoryRepository client;
	private List<String> processedRefs;
	private ObjectId obj1;
	private ObjectId obj2;
	private ObjectId obj3;
	private String refName = "refs/tags/blob";

	@Before
	public void setUp() throws Exception {
		server = newRepo("server");
		client = newRepo("client");
		processedRefs = new ArrayList<>();
		testProtocol = new TestProtocol<>(null, (Object req, Repository db) -> {
			ReceivePack rp = new ReceivePack(db);
			rp.setPreReceiveHook((ReceivePack receivePack,
					Collection<ReceiveCommand> cmds) -> {
				for (ReceiveCommand cmd : cmds) {
					processedRefs.add(cmd.getRefName());
				}
			});
			return rp;
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

		server.getConfig().setInt("receive", null, "maxCommandBytes", 195);
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

	@Test
	public void commandOrder() throws Exception {
		List<RemoteRefUpdate> updates = new ArrayList<>();
		try (TestRepository<?> tr = new TestRepository<>(client)) {
			// Arbitrary non-sorted order.
			for (int i = 9; i >= 0; i--) {
				String name = "refs/heads/b" + i;
				tr.branch(name).commit().create();
				RemoteRefUpdate rru = new RemoteRefUpdate(client, name, name,
						false, null, ObjectId.zeroId());
				updates.add(rru);
			}
		}

		PushResult result;
		try (Transport tn = testProtocol.open(uri, client, "server")) {
			result = tn.push(NullProgressMonitor.INSTANCE, updates);
		}

		for (RemoteRefUpdate remoteUpdate : result.getRemoteUpdates()) {
			assertEquals(
					"update should succeed on " + remoteUpdate.getRemoteName(),
					RemoteRefUpdate.Status.OK, remoteUpdate.getStatus());
		}

		List<String> expected = remoteRefNames(updates);
		assertEquals(
				"ref names processed by ReceivePack should match input ref names in order",
				expected, processedRefs);
		assertEquals(
				"remote ref names should match input ref names in order",
				expected, remoteRefNames(result.getRemoteUpdates()));
	}

	private static List<String> remoteRefNames(Collection<RemoteRefUpdate> updates) {
		List<String> result = new ArrayList<>();
		for (RemoteRefUpdate u : updates) {
			result.add(u.getRemoteName());
		}
		return result;
	}
}
