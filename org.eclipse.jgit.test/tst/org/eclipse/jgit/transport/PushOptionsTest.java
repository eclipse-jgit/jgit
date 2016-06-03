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

import static org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_OTHER_REASON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
//import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
//import org.eclipse.jgit.api.errors.GitAPIException;
//import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
// import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.TemporaryBuffer;
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

	private ObjectId obj3;
	private String refName = "refs/tags/blob";

	@Before
	public void setUp() throws Exception {
		super.setUp();
		server = newRepo("server");
		client = newRepo("client");
		testProtocol = new TestProtocol<>(null,
				new ReceivePackFactory<Object>() {
					@Override
					public ReceivePack create(Object req, Repository database)
							throws ServiceNotEnabledException,
							ServiceNotAuthorizedException {
						return new ReceivePack(database);
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


		src = createBareRepository();
		dst = createBareRepository();

		// Fill dst with a some common history.
		//
		TestRepository<Repository> d = new TestRepository<Repository>(dst);
		a = d.blob("a");
		A = d.commit(d.tree(d.file("a", a)));
		B = d.commit().parent(A).create();
		d.update(R_MASTER, B);

		// Clone from dst into src
		//
		try (Transport t = Transport.open(src,
				new URIish(dst.getDirectory().getAbsolutePath()))) {
			t.fetch(PM, Collections.singleton(new RefSpec("+refs/*:refs/*")));
			assertEquals(B, src.resolve(R_MASTER));
		}

		// Now put private stuff into dst.
		//
		b = d.blob("b");
		P = d.commit(d.tree(d.file("b", b)), A);
		d.update(R_PRIVATE, P);
	}

	@After
	public void tearDown() {
		Transport.unregister(testProtocol);
	}

	private static InMemoryRepository newRepo(String name) {
		return new InMemoryRepository(new DfsRepositoryDescription(name));
	}

	private static final NullProgressMonitor PM = NullProgressMonitor.INSTANCE;

	private static final String R_MASTER = Constants.R_HEADS + Constants.MASTER;

	private static final String R_PRIVATE = Constants.R_HEADS + "private";

	private Repository src;

	private Repository dst;

	private RevCommit A, B, P;

	private RevBlob a, b;

	@Test
	public void testPush() throws Exception {

		// create other repository
		Repository db2 = createWorkRepository();

		// setup the first repository
		System.out.println("PushOptionsTest: setting up first repository");
		System.out.println("PushOptionsTest: db = " + db.toString());
		final StoredConfig config = db.getConfig();
		config.setBoolean("receive", null, "pushoptions", true);
		RemoteConfig remoteConfig = new RemoteConfig(config, "test");
		remoteConfig.addURI(new URIish(db.getDirectory().toURI().toURL()));
		remoteConfig.update(config);
		config.save();

		/**
		 * RefAdvertiser adv = new RefAdvertiser.PacketLineOutRefAdvertiser( new
		 * PacketLineOut(null)); // replace null with output stream
		 * adv.advertiseCapability(CAPABILITY_PUSH_OPTIONS);
		 */

		try (Git git1 = new Git(db)) {
			// create some refs via commits and tag
			RevCommit commit = git1.commit().setMessage("initial commit")
					.call();
			Ref tagRef = git1.tag().setName("tag").call();

			try {
				db2.resolve(commit.getId().getName() + "^{commit}");
				fail("id shouldn't exist yet");
			} catch (MissingObjectException e) {
				// we should get here
			}

			RefSpec spec = new RefSpec("refs/heads/master:refs/heads/x");
			List<String> pushOptions = Arrays.asList("Hello", "World!");

			System.out.println("PushOptionsTest: setting up second repository");
			final StoredConfig config2 = db2.getConfig();
			config2.setBoolean("receive", null, "pushoptions", true);
			RemoteConfig remoteConfig2 = new RemoteConfig(config2, "test2");
			remoteConfig2
					.addURI(new URIish(db2.getDirectory().toURI().toURL()));
			remoteConfig2.update(config2);
			config2.save();

			ReceivePack receivePack = new ReceivePack(db2);
			TestRepository<Repository> s = new TestRepository<Repository>(src);
			RevCommit N = s.commit().parent(B).add("q", s.blob("a")).create();
			s.parseBody(N).getTree(); // RevTree t = s.parseBody(N).getTree();

			// Don't include the tree in the pack.
			//
			final TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(1024);
			packHeader(pack, 1);
			copy(pack, src.open(N));
			digest(pack);

			final TemporaryBuffer.Heap inBuf = new TemporaryBuffer.Heap(1024);
			final PacketLineOut inPckLine = new PacketLineOut(inBuf);
			inPckLine.writeString(ObjectId.zeroId().getName()
					// ObjectId.fromString(
					// "0000000000000000000000000000000000000001").name()
							+ ' '
							+ N.name()
					+ ' ' + "refs/heads/s" + '\0'
					// + BasePackPushConnection.CAPABILITY_REPORT_STATUS + ' '
					+ BasePackPushConnection.CAPABILITY_PUSH_OPTIONS);
			inPckLine.end();
			pack.writeTo(inBuf, PM);

			// PacketLineIn pckIn = newPacketLineIn(INPUT_NO_NEWLINES);
			// PushCertificateParser parser = new PushCertificateParser(db,
			// newEnabledConfig());
			// parser.receiveHeader(pckIn, false);
			// parser.addCommand(pckIn.readString());
			// assertEquals(PushCertificateParser.BEGIN_SIGNATURE,
			// pckIn.readString());
			// parser.receiveSignature(pckIn);
			//
			// PushCertificate cert = parser.build();
			// assertEquals("0.1", cert.getVersion());
			// assertEquals("Dave Borowitz", cert.getPusherIdent().getName());
			// assertEquals("dborowitz@google.com",
			// cert.getPusherIdent().getEmailAddress());
			// assertEquals(1433954361000L,
			// cert.getPusherIdent().getWhen().getTime());
			// assertEquals(-7 * 60, cert.getPusherIdent().getTimeZoneOffset());
			// assertEquals("git://localhost/repo.git", cert.getPushee());
			// assertEquals("1433954361-bde756572d665bba81d8", cert.getNonce());
			//
			// assertNotEquals(cert.getNonce(), parser.getAdvertiseNonce());
			// assertEquals(PushCertificate.NonceStatus.BAD,
			// cert.getNonceStatus());
			//
			// assertEquals(1, cert.getCommands().size());
			// ReceiveCommand cmd = cert.getCommands().get(0);

			System.out.println("PushOptionsTest: receivePack.receive(...)");

			System.out.println(
					"PushOptionsTest: receivePack.isAllowPushOptions() = "
					+ receivePack.isAllowPushOptions());

			receivePack.receive(new ByteArrayInputStream(inBuf.toByteArray()),
					System.out, null);

			System.out.println(
					"receivePack.isCapabilityEnabled(BasePackPushConnection.CAPABILITY_REPORT_STATUS): "
					+ receivePack.isCapabilityEnabled(
							BasePackPushConnection.CAPABILITY_REPORT_STATUS));
			System.out.println(
					"receivePack.isCapabilityEnabled(BasePackPushConnection.CAPABILITY_PUSH_OPTIONS): "
							+ receivePack.isCapabilityEnabled(
									BasePackPushConnection.CAPABILITY_PUSH_OPTIONS));

			PushCommand pushCommand = git1.push().setRemote("test")
					.setRefSpecs(spec).setPushOptions(pushOptions);
			System.out.println("pushCommand.call();");
			pushCommand.call();

			assertEquals(commit.getId(),
					db2.resolve(commit.getId().getName() + "^{commit}"));
			assertEquals(tagRef.getObjectId(),
					db2.resolve(tagRef.getObjectId().getName()));
			// assertTrue(remoteConfig.)
			// assertEquals(server.);
			assertEquals(pushOptions, receivePack.getPushOptions());
		}
	}

	public static class NullOutputStream extends OutputStream {
		@Override
		public void write(int b) throws IOException {
			//
		}
	}

	// retained to evaluate backwards compatibility
	// @Test
	public void testWrongOldIdDoesNotReplace() throws IOException {
		RemoteRefUpdate rru = new RemoteRefUpdate(null, null, obj2, refName,
				false, null, obj3);

		Map<String, RemoteRefUpdate> updates = new HashMap<>();
		updates.put(rru.getRemoteName(), rru);

		Transport tn = testProtocol.open(uri, client, "server");
		try {
			PushConnection connection = tn.openPush();
			try {
				connection.push(NullProgressMonitor.INSTANCE, updates);
			} finally {
				connection.close();
			}
		} finally {
			tn.close();
		}

		assertEquals(REJECTED_OTHER_REASON, rru.getStatus());
		assertEquals("invalid old id sent", rru.getMessage());
	}

	private static void packHeader(TemporaryBuffer.Heap tinyPack, int cnt)
			throws IOException {
		final byte[] hdr = new byte[8];
		NB.encodeInt32(hdr, 0, 2);
		NB.encodeInt32(hdr, 4, cnt);

		tinyPack.write(Constants.PACK_SIGNATURE);
		tinyPack.write(hdr, 0, 8);
	}

	private static void copy(TemporaryBuffer.Heap tinyPack, ObjectLoader ldr)
			throws IOException {
		final byte[] buf = new byte[64];
		final byte[] content = ldr.getCachedBytes();
		int dataLength = content.length;
		int nextLength = dataLength >>> 4;
		int size = 0;
		buf[size++] = (byte) ((nextLength > 0 ? 0x80 : 0x00)
				| (ldr.getType() << 4) | (dataLength & 0x0F));
		dataLength = nextLength;
		while (dataLength > 0) {
			nextLength >>>= 7;
			buf[size++] = (byte) ((nextLength > 0 ? 0x80 : 0x00)
					| (dataLength & 0x7F));
			dataLength = nextLength;
		}
		tinyPack.write(buf, 0, size);
		deflate(tinyPack, content);
	}

	private static void deflate(TemporaryBuffer.Heap tinyPack,
			final byte[] content) throws IOException {
		final Deflater deflater = new Deflater();
		final byte[] buf = new byte[128];
		deflater.setInput(content, 0, content.length);
		deflater.finish();
		do {
			final int n = deflater.deflate(buf, 0, buf.length);
			if (n > 0)
				tinyPack.write(buf, 0, n);
		} while (!deflater.finished());
	}

	private static void digest(TemporaryBuffer.Heap buf) throws IOException {
		MessageDigest md = Constants.newMessageDigest();
		md.update(buf.toByteArray());
		buf.write(md.digest());
	}

	public static PacketLineIn newPacketLineIn(String input) {
		return new PacketLineIn(
				new ByteArrayInputStream(Constants.encode(input)));
	}
}