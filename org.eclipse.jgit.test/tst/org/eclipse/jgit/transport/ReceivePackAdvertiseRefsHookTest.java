/*
 * Copyright (C) 2010, Google Inc.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Deflater;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.UnpackException;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.ObjectDirectory;
import org.eclipse.jgit.storage.pack.BinaryDelta;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ReceivePackAdvertiseRefsHookTest extends LocalDiskRepositoryTestCase {
	private static final NullProgressMonitor PM = NullProgressMonitor.INSTANCE;

	private static final String R_MASTER = Constants.R_HEADS + Constants.MASTER;

	private static final String R_PRIVATE = Constants.R_HEADS + "private";

	private Repository src;

	private Repository dst;

	private RevCommit A, B, P;

	private RevBlob a, b;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

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
		Transport t = Transport.open(src, uriOf(dst));
		try {
			t.fetch(PM, Collections.singleton(new RefSpec("+refs/*:refs/*")));
			assertEquals(B, src.resolve(R_MASTER));
		} finally {
			t.close();
		}

		// Now put private stuff into dst.
		//
		b = d.blob("b");
		P = d.commit(d.tree(d.file("b", b)), A);
		d.update(R_PRIVATE, P);
	}

	@Override
	@After
	public void tearDown() throws Exception {
		if (src != null)
			src.close();
		if (dst != null)
			dst.close();
		super.tearDown();
	}

	@Test
	public void testFilterHidesPrivate() throws Exception {
		Map<String, Ref> refs;
		TransportLocal t = new TransportLocal(src, uriOf(dst), dst.getDirectory()) {
			@Override
			ReceivePack createReceivePack(final Repository db) {
				db.close();
				dst.incrementOpen();

				final ReceivePack rp = super.createReceivePack(dst);
				rp.setAdvertiseRefsHook(new HidePrivateHook());
				return rp;
			}
		};
		try {
			PushConnection c = t.openPush();
			try {
				refs = c.getRefsMap();
			} finally {
				c.close();
			}
		} finally {
			t.close();
		}

		assertNotNull(refs);
		assertNull("no private", refs.get(R_PRIVATE));
		assertNull("no HEAD", refs.get(Constants.HEAD));
		assertEquals(1, refs.size());

		Ref master = refs.get(R_MASTER);
		assertNotNull("has master", master);
		assertEquals(B, master.getObjectId());
	}

	@Test
	public void testSuccess() throws Exception {
		// Manually force a delta of an object so we reuse it later.
		//
		TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(1024);

		packHeader(pack, 2);
		pack.write((Constants.OBJ_BLOB) << 4 | 1);
		deflate(pack, new byte[] { 'a' });

		pack.write((Constants.OBJ_REF_DELTA) << 4 | 4);
		a.copyRawTo(pack);
		deflate(pack, new byte[] { 0x1, 0x1, 0x1, 'b' });

		digest(pack);
		openPack(pack);

		// Verify the only storage of b is our packed delta above.
		//
		ObjectDirectory od = (ObjectDirectory) src.getObjectDatabase();
		assertTrue("has b", src.hasObject(b));
		assertFalse("b not loose", od.fileFor(b).exists());

		// Now use b but in a different commit than what is hidden.
		//
		TestRepository<Repository> s = new TestRepository<Repository>(src);
		RevCommit N = s.commit().parent(B).add("q", b).create();
		s.update(R_MASTER, N);

		// Push this new content to the remote, doing strict validation.
		//
		TransportLocal t = new TransportLocal(src, uriOf(dst), dst.getDirectory()) {
			@Override
			ReceivePack createReceivePack(final Repository db) {
				db.close();
				dst.incrementOpen();

				final ReceivePack rp = super.createReceivePack(dst);
				rp.setCheckReceivedObjects(true);
				rp.setCheckReferencedObjectsAreReachable(true);
				rp.setAdvertiseRefsHook(new HidePrivateHook());
				return rp;
			}
		};
		RemoteRefUpdate u = new RemoteRefUpdate( //
				src, //
				R_MASTER, // src name
				R_MASTER, // dst name
				false, // do not force update
				null, // local tracking branch
				null // expected id
		);
		PushResult r;
		try {
			t.setPushThin(true);
			r = t.push(PM, Collections.singleton(u));
		} finally {
			t.close();
		}

		assertNotNull("have result", r);
		assertNull("private not advertised", r.getAdvertisedRef(R_PRIVATE));
		assertSame("master updated", RemoteRefUpdate.Status.OK, u.getStatus());
		assertEquals(N, dst.resolve(R_MASTER));
	}

	@Test
	public void testCreateBranchAtHiddenCommitFails() throws Exception {
		final TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(64);
		packHeader(pack, 0);
		digest(pack);

		final TemporaryBuffer.Heap inBuf = new TemporaryBuffer.Heap(256);
		final PacketLineOut inPckLine = new PacketLineOut(inBuf);
		inPckLine.writeString(ObjectId.zeroId().name() + ' ' + P.name() + ' '
				+ "refs/heads/s" + '\0'
				+ BasePackPushConnection.CAPABILITY_REPORT_STATUS);
		inPckLine.end();
		pack.writeTo(inBuf, PM);

		final TemporaryBuffer.Heap outBuf = new TemporaryBuffer.Heap(1024);
		final ReceivePack rp = new ReceivePack(dst);
		rp.setCheckReceivedObjects(true);
		rp.setCheckReferencedObjectsAreReachable(true);
		rp.setAdvertiseRefsHook(new HidePrivateHook());
		try {
			receive(rp, inBuf, outBuf);
			fail("Expected UnpackException");
		} catch (UnpackException failed) {
			Throwable err = failed.getCause();
			assertTrue(err instanceof MissingObjectException);
			MissingObjectException moe = (MissingObjectException) err;
			assertEquals(P, moe.getObjectId());
		}

		final PacketLineIn r = asPacketLineIn(outBuf);
		String master = r.readString();
		int nul = master.indexOf('\0');
		assertTrue("has capability list", nul > 0);
		assertEquals(B.name() + ' ' + R_MASTER, master.substring(0, nul));
		assertSame(PacketLineIn.END, r.readString());

		assertEquals("unpack error Missing commit " + P.name(), r.readString());
		assertEquals("ng refs/heads/s n/a (unpacker error)", r.readString());
		assertSame(PacketLineIn.END, r.readString());
	}

	private static void receive(final ReceivePack rp,
			final TemporaryBuffer.Heap inBuf, final TemporaryBuffer.Heap outBuf)
			throws IOException {
		rp.receive(new ByteArrayInputStream(inBuf.toByteArray()), outBuf, null);
	}

	@Test
	public void testUsingHiddenDeltaBaseFails() throws Exception {
		byte[] delta = { 0x1, 0x1, 0x1, 'c' };
		TestRepository<Repository> s = new TestRepository<Repository>(src);
		RevCommit N = s.commit().parent(B).add("q",
				s.blob(BinaryDelta.apply(dst.open(b).getCachedBytes(), delta)))
				.create();

		final TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(1024);
		packHeader(pack, 3);
		copy(pack, src.open(N));
		copy(pack, src.open(s.parseBody(N).getTree()));
		pack.write((Constants.OBJ_REF_DELTA) << 4 | 4);
		b.copyRawTo(pack);
		deflate(pack, delta);
		digest(pack);

		final TemporaryBuffer.Heap inBuf = new TemporaryBuffer.Heap(1024);
		final PacketLineOut inPckLine = new PacketLineOut(inBuf);
		inPckLine.writeString(ObjectId.zeroId().name() + ' ' + N.name() + ' '
				+ "refs/heads/s" + '\0'
				+ BasePackPushConnection.CAPABILITY_REPORT_STATUS);
		inPckLine.end();
		pack.writeTo(inBuf, PM);

		final TemporaryBuffer.Heap outBuf = new TemporaryBuffer.Heap(1024);
		final ReceivePack rp = new ReceivePack(dst);
		rp.setCheckReceivedObjects(true);
		rp.setCheckReferencedObjectsAreReachable(true);
		rp.setAdvertiseRefsHook(new HidePrivateHook());
		try {
			receive(rp, inBuf, outBuf);
			fail("Expected UnpackException");
		} catch (UnpackException failed) {
			Throwable err = failed.getCause();
			assertTrue(err instanceof MissingObjectException);
			MissingObjectException moe = (MissingObjectException) err;
			assertEquals(b, moe.getObjectId());
		}

		final PacketLineIn r = asPacketLineIn(outBuf);
		String master = r.readString();
		int nul = master.indexOf('\0');
		assertTrue("has capability list", nul > 0);
		assertEquals(B.name() + ' ' + R_MASTER, master.substring(0, nul));
		assertSame(PacketLineIn.END, r.readString());

		assertEquals("unpack error Missing blob " + b.name(), r.readString());
		assertEquals("ng refs/heads/s n/a (unpacker error)", r.readString());
		assertSame(PacketLineIn.END, r.readString());
	}

	@Test
	public void testUsingHiddenCommonBlobFails() throws Exception {
		// Try to use the 'b' blob that is hidden.
		//
		TestRepository<Repository> s = new TestRepository<Repository>(src);
		RevCommit N = s.commit().parent(B).add("q", s.blob("b")).create();

		// But don't include it in the pack.
		//
		final TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(1024);
		packHeader(pack, 2);
		copy(pack, src.open(N));
		copy(pack,src.open(s.parseBody(N).getTree()));
		digest(pack);

		final TemporaryBuffer.Heap inBuf = new TemporaryBuffer.Heap(1024);
		final PacketLineOut inPckLine = new PacketLineOut(inBuf);
		inPckLine.writeString(ObjectId.zeroId().name() + ' ' + N.name() + ' '
				+ "refs/heads/s" + '\0'
				+ BasePackPushConnection.CAPABILITY_REPORT_STATUS);
		inPckLine.end();
		pack.writeTo(inBuf, PM);

		final TemporaryBuffer.Heap outBuf = new TemporaryBuffer.Heap(1024);
		final ReceivePack rp = new ReceivePack(dst);
		rp.setCheckReceivedObjects(true);
		rp.setCheckReferencedObjectsAreReachable(true);
		rp.setAdvertiseRefsHook(new HidePrivateHook());
		try {
			receive(rp, inBuf, outBuf);
			fail("Expected UnpackException");
		} catch (UnpackException failed) {
			Throwable err = failed.getCause();
			assertTrue(err instanceof MissingObjectException);
			MissingObjectException moe = (MissingObjectException) err;
			assertEquals(b, moe.getObjectId());
		}

		final PacketLineIn r = asPacketLineIn(outBuf);
		String master = r.readString();
		int nul = master.indexOf('\0');
		assertTrue("has capability list", nul > 0);
		assertEquals(B.name() + ' ' + R_MASTER, master.substring(0, nul));
		assertSame(PacketLineIn.END, r.readString());

		assertEquals("unpack error Missing blob " + b.name(), r.readString());
		assertEquals("ng refs/heads/s n/a (unpacker error)", r.readString());
		assertSame(PacketLineIn.END, r.readString());
	}

	@Test
	public void testUsingUnknownBlobFails() throws Exception {
		// Try to use the 'n' blob that is not on the server.
		//
		TestRepository<Repository> s = new TestRepository<Repository>(src);
		RevBlob n = s.blob("n");
		RevCommit N = s.commit().parent(B).add("q", n).create();

		// But don't include it in the pack.
		//
		final TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(1024);
		packHeader(pack, 2);
		copy(pack, src.open(N));
		copy(pack,src.open(s.parseBody(N).getTree()));
		digest(pack);

		final TemporaryBuffer.Heap inBuf = new TemporaryBuffer.Heap(1024);
		final PacketLineOut inPckLine = new PacketLineOut(inBuf);
		inPckLine.writeString(ObjectId.zeroId().name() + ' ' + N.name() + ' '
				+ "refs/heads/s" + '\0'
				+ BasePackPushConnection.CAPABILITY_REPORT_STATUS);
		inPckLine.end();
		pack.writeTo(inBuf, PM);

		final TemporaryBuffer.Heap outBuf = new TemporaryBuffer.Heap(1024);
		final ReceivePack rp = new ReceivePack(dst);
		rp.setCheckReceivedObjects(true);
		rp.setCheckReferencedObjectsAreReachable(true);
		rp.setAdvertiseRefsHook(new HidePrivateHook());
		try {
			receive(rp, inBuf, outBuf);
			fail("Expected UnpackException");
		} catch (UnpackException failed) {
			Throwable err = failed.getCause();
			assertTrue(err instanceof MissingObjectException);
			MissingObjectException moe = (MissingObjectException) err;
			assertEquals(n, moe.getObjectId());
		}

		final PacketLineIn r = asPacketLineIn(outBuf);
		String master = r.readString();
		int nul = master.indexOf('\0');
		assertTrue("has capability list", nul > 0);
		assertEquals(B.name() + ' ' + R_MASTER, master.substring(0, nul));
		assertSame(PacketLineIn.END, r.readString());

		assertEquals("unpack error Missing blob " + n.name(), r.readString());
		assertEquals("ng refs/heads/s n/a (unpacker error)", r.readString());
		assertSame(PacketLineIn.END, r.readString());
	}

	@Test
	public void testUsingUnknownTreeFails() throws Exception {
		TestRepository<Repository> s = new TestRepository<Repository>(src);
		RevCommit N = s.commit().parent(B).add("q", s.blob("a")).create();
		RevTree t = s.parseBody(N).getTree();

		// Don't include the tree in the pack.
		//
		final TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(1024);
		packHeader(pack, 1);
		copy(pack, src.open(N));
		digest(pack);

		final TemporaryBuffer.Heap inBuf = new TemporaryBuffer.Heap(1024);
		final PacketLineOut inPckLine = new PacketLineOut(inBuf);
		inPckLine.writeString(ObjectId.zeroId().name() + ' ' + N.name() + ' '
				+ "refs/heads/s" + '\0'
				+ BasePackPushConnection.CAPABILITY_REPORT_STATUS);
		inPckLine.end();
		pack.writeTo(inBuf, PM);

		final TemporaryBuffer.Heap outBuf = new TemporaryBuffer.Heap(1024);
		final ReceivePack rp = new ReceivePack(dst);
		rp.setCheckReceivedObjects(true);
		rp.setCheckReferencedObjectsAreReachable(true);
		rp.setAdvertiseRefsHook(new HidePrivateHook());
		try {
			receive(rp, inBuf, outBuf);
			fail("Expected UnpackException");
		} catch (UnpackException failed) {
			Throwable err = failed.getCause();
			assertTrue(err instanceof MissingObjectException);
			MissingObjectException moe = (MissingObjectException) err;
			assertEquals(t, moe.getObjectId());
		}

		final PacketLineIn r = asPacketLineIn(outBuf);
		String master = r.readString();
		int nul = master.indexOf('\0');
		assertTrue("has capability list", nul > 0);
		assertEquals(B.name() + ' ' + R_MASTER, master.substring(0, nul));
		assertSame(PacketLineIn.END, r.readString());

		assertEquals("unpack error Missing tree " + t.name(), r.readString());
		assertEquals("ng refs/heads/s n/a (unpacker error)", r.readString());
		assertSame(PacketLineIn.END, r.readString());
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
			buf[size++] = (byte) ((nextLength > 0 ? 0x80 : 0x00) | (dataLength & 0x7F));
			dataLength = nextLength;
		}
		tinyPack.write(buf, 0, size);
		deflate(tinyPack, content);
	}

	private static void deflate(TemporaryBuffer.Heap tinyPack,
			final byte[] content)
			throws IOException {
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

	private ObjectInserter inserter;

	@After
	public void release() {
		if (inserter != null)
			inserter.release();
	}

	private void openPack(TemporaryBuffer.Heap buf) throws IOException {
		if (inserter == null)
			inserter = src.newObjectInserter();

		final byte[] raw = buf.toByteArray();
		PackParser p = inserter.newPackParser(new ByteArrayInputStream(raw));
		p.setAllowThin(true);
		p.parse(PM);
	}

	private static PacketLineIn asPacketLineIn(TemporaryBuffer.Heap buf)
			throws IOException {
		return new PacketLineIn(new ByteArrayInputStream(buf.toByteArray()));
	}

	private static final class HidePrivateHook extends AbstractAdvertiseRefsHook {
		public Map<String, Ref> getAdvertisedRefs(Repository r, RevWalk revWalk) {
			Map<String, Ref> refs = new HashMap<String, Ref>(r.getAllRefs());
			assertNotNull(refs.remove(R_PRIVATE));
			return refs;
		}
	}

	private static URIish uriOf(Repository r) throws URISyntaxException {
		return new URIish(r.getDirectory().getAbsolutePath());
	}
}
