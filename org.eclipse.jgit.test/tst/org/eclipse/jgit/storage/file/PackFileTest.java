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

package org.eclipse.jgit.storage.file;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.zip.Deflater;

import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.storage.pack.DeltaEncoder;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PackFileTest extends LocalDiskRepositoryTestCase {
	private int streamThreshold = 16 * 1024;

	private TestRng rng;

	private FileRepository repo;

	private TestRepository<FileRepository> tr;

	private WindowCursor wc;

	private TestRng getRng() {
		if (rng == null)
			rng = new TestRng(JGitTestUtil.getName());
		return rng;
	}

	@Before
	public void setUp() throws Exception {
		super.setUp();

		WindowCacheConfig cfg = new WindowCacheConfig();
		cfg.setStreamFileThreshold(streamThreshold);
		WindowCache.reconfigure(cfg);

		repo = createBareRepository();
		tr = new TestRepository<FileRepository>(repo);
		wc = (WindowCursor) repo.newObjectReader();
	}

	@After
	public void tearDown() throws Exception {
		if (wc != null)
			wc.release();
		WindowCache.reconfigure(new WindowCacheConfig());
		super.tearDown();
	}

	@Test
	public void testWhole_SmallObject() throws Exception {
		final int type = Constants.OBJ_BLOB;
		byte[] data = getRng().nextBytes(300);
		RevBlob id = tr.blob(data);
		tr.branch("master").commit().add("A", id).create();
		tr.packAndPrune();
		assertTrue("has blob", wc.has(id));

		ObjectLoader ol = wc.open(id);
		assertNotNull("created loader", ol);
		assertEquals(type, ol.getType());
		assertEquals(data.length, ol.getSize());
		assertFalse("is not large", ol.isLarge());
		assertTrue("same content", Arrays.equals(data, ol.getCachedBytes()));

		ObjectStream in = ol.openStream();
		assertNotNull("have stream", in);
		assertEquals(type, in.getType());
		assertEquals(data.length, in.getSize());
		byte[] data2 = new byte[data.length];
		IO.readFully(in, data2, 0, data.length);
		assertTrue("same content", Arrays.equals(data2, data));
		assertEquals("stream at EOF", -1, in.read());
		in.close();
	}

	@Test
	public void testWhole_LargeObject() throws Exception {
		final int type = Constants.OBJ_BLOB;
		byte[] data = getRng().nextBytes(streamThreshold + 5);
		RevBlob id = tr.blob(data);
		tr.branch("master").commit().add("A", id).create();
		tr.packAndPrune();
		assertTrue("has blob", wc.has(id));

		ObjectLoader ol = wc.open(id);
		assertNotNull("created loader", ol);
		assertEquals(type, ol.getType());
		assertEquals(data.length, ol.getSize());
		assertTrue("is large", ol.isLarge());
		try {
			ol.getCachedBytes();
			fail("Should have thrown LargeObjectException");
		} catch (LargeObjectException tooBig) {
			assertEquals(MessageFormat.format(
					JGitText.get().largeObjectException, id.name()), tooBig
					.getMessage());
		}

		ObjectStream in = ol.openStream();
		assertNotNull("have stream", in);
		assertEquals(type, in.getType());
		assertEquals(data.length, in.getSize());
		byte[] data2 = new byte[data.length];
		IO.readFully(in, data2, 0, data.length);
		assertTrue("same content", Arrays.equals(data2, data));
		assertEquals("stream at EOF", -1, in.read());
		in.close();
	}

	@Test
	public void testDelta_SmallObjectChain() throws Exception {
		ObjectInserter.Formatter fmt = new ObjectInserter.Formatter();
		byte[] data0 = new byte[512];
		Arrays.fill(data0, (byte) 0xf3);
		ObjectId id0 = fmt.idFor(Constants.OBJ_BLOB, data0);

		TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(64 * 1024);
		packHeader(pack, 4);
		objectHeader(pack, Constants.OBJ_BLOB, data0.length);
		deflate(pack, data0);

		byte[] data1 = clone(0x01, data0);
		byte[] delta1 = delta(data0, data1);
		ObjectId id1 = fmt.idFor(Constants.OBJ_BLOB, data1);
		objectHeader(pack, Constants.OBJ_REF_DELTA, delta1.length);
		id0.copyRawTo(pack);
		deflate(pack, delta1);

		byte[] data2 = clone(0x02, data1);
		byte[] delta2 = delta(data1, data2);
		ObjectId id2 = fmt.idFor(Constants.OBJ_BLOB, data2);
		objectHeader(pack, Constants.OBJ_REF_DELTA, delta2.length);
		id1.copyRawTo(pack);
		deflate(pack, delta2);

		byte[] data3 = clone(0x03, data2);
		byte[] delta3 = delta(data2, data3);
		ObjectId id3 = fmt.idFor(Constants.OBJ_BLOB, data3);
		objectHeader(pack, Constants.OBJ_REF_DELTA, delta3.length);
		id2.copyRawTo(pack);
		deflate(pack, delta3);

		digest(pack);
		PackParser ip = index(pack.toByteArray());
		ip.setAllowThin(true);
		ip.parse(NullProgressMonitor.INSTANCE);

		assertTrue("has blob", wc.has(id3));

		ObjectLoader ol = wc.open(id3);
		assertNotNull("created loader", ol);
		assertEquals(Constants.OBJ_BLOB, ol.getType());
		assertEquals(data3.length, ol.getSize());
		assertFalse("is large", ol.isLarge());
		assertNotNull(ol.getCachedBytes());
		assertArrayEquals(data3, ol.getCachedBytes());

		ObjectStream in = ol.openStream();
		assertNotNull("have stream", in);
		assertEquals(Constants.OBJ_BLOB, in.getType());
		assertEquals(data3.length, in.getSize());
		byte[] act = new byte[data3.length];
		IO.readFully(in, act, 0, data3.length);
		assertTrue("same content", Arrays.equals(act, data3));
		assertEquals("stream at EOF", -1, in.read());
		in.close();
	}

	@Test
	public void testDelta_LargeObjectChain() throws Exception {
		ObjectInserter.Formatter fmt = new ObjectInserter.Formatter();
		byte[] data0 = new byte[streamThreshold + 5];
		Arrays.fill(data0, (byte) 0xf3);
		ObjectId id0 = fmt.idFor(Constants.OBJ_BLOB, data0);

		TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(64 * 1024);
		packHeader(pack, 4);
		objectHeader(pack, Constants.OBJ_BLOB, data0.length);
		deflate(pack, data0);

		byte[] data1 = clone(0x01, data0);
		byte[] delta1 = delta(data0, data1);
		ObjectId id1 = fmt.idFor(Constants.OBJ_BLOB, data1);
		objectHeader(pack, Constants.OBJ_REF_DELTA, delta1.length);
		id0.copyRawTo(pack);
		deflate(pack, delta1);

		byte[] data2 = clone(0x02, data1);
		byte[] delta2 = delta(data1, data2);
		ObjectId id2 = fmt.idFor(Constants.OBJ_BLOB, data2);
		objectHeader(pack, Constants.OBJ_REF_DELTA, delta2.length);
		id1.copyRawTo(pack);
		deflate(pack, delta2);

		byte[] data3 = clone(0x03, data2);
		byte[] delta3 = delta(data2, data3);
		ObjectId id3 = fmt.idFor(Constants.OBJ_BLOB, data3);
		objectHeader(pack, Constants.OBJ_REF_DELTA, delta3.length);
		id2.copyRawTo(pack);
		deflate(pack, delta3);

		digest(pack);
		PackParser ip = index(pack.toByteArray());
		ip.setAllowThin(true);
		ip.parse(NullProgressMonitor.INSTANCE);

		assertTrue("has blob", wc.has(id3));

		ObjectLoader ol = wc.open(id3);
		assertNotNull("created loader", ol);
		assertEquals(Constants.OBJ_BLOB, ol.getType());
		assertEquals(data3.length, ol.getSize());
		assertTrue("is large", ol.isLarge());
		try {
			ol.getCachedBytes();
			fail("Should have thrown LargeObjectException");
		} catch (LargeObjectException tooBig) {
			assertEquals(MessageFormat.format(
					JGitText.get().largeObjectException, id3.name()), tooBig
					.getMessage());
		}

		ObjectStream in = ol.openStream();
		assertNotNull("have stream", in);
		assertEquals(Constants.OBJ_BLOB, in.getType());
		assertEquals(data3.length, in.getSize());
		byte[] act = new byte[data3.length];
		IO.readFully(in, act, 0, data3.length);
		assertTrue("same content", Arrays.equals(act, data3));
		assertEquals("stream at EOF", -1, in.read());
		in.close();
	}

	private byte[] clone(int first, byte[] base) {
		byte[] r = new byte[base.length];
		System.arraycopy(base, 1, r, 1, r.length - 1);
		r[0] = (byte) first;
		return r;
	}

	private byte[] delta(byte[] base, byte[] dest) throws IOException {
		ByteArrayOutputStream tmp = new ByteArrayOutputStream();
		DeltaEncoder de = new DeltaEncoder(tmp, base.length, dest.length);
		de.insert(dest, 0, 1);
		de.copy(1, base.length - 1);
		return tmp.toByteArray();
	}

	private void packHeader(TemporaryBuffer.Heap pack, int cnt)
			throws IOException {
		final byte[] hdr = new byte[8];
		NB.encodeInt32(hdr, 0, 2);
		NB.encodeInt32(hdr, 4, cnt);
		pack.write(Constants.PACK_SIGNATURE);
		pack.write(hdr, 0, 8);
	}

	private void objectHeader(TemporaryBuffer.Heap pack, int type, int sz)
			throws IOException {
		byte[] buf = new byte[8];
		int nextLength = sz >>> 4;
		buf[0] = (byte) ((nextLength > 0 ? 0x80 : 0x00) | (type << 4) | (sz & 0x0F));
		sz = nextLength;
		int n = 1;
		while (sz > 0) {
			nextLength >>>= 7;
			buf[n++] = (byte) ((nextLength > 0 ? 0x80 : 0x00) | (sz & 0x7F));
			sz = nextLength;
		}
		pack.write(buf, 0, n);
	}

	private void deflate(TemporaryBuffer.Heap pack, final byte[] content)
			throws IOException {
		final Deflater deflater = new Deflater();
		final byte[] buf = new byte[128];
		deflater.setInput(content, 0, content.length);
		deflater.finish();
		do {
			final int n = deflater.deflate(buf, 0, buf.length);
			if (n > 0)
				pack.write(buf, 0, n);
		} while (!deflater.finished());
		deflater.end();
	}

	private void digest(TemporaryBuffer.Heap buf) throws IOException {
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

	private PackParser index(byte[] raw) throws IOException {
		if (inserter == null)
			inserter = repo.newObjectInserter();
		return inserter.newPackParser(new ByteArrayInputStream(raw));
	}
}
