/*
 * Copyright (C) 2011, Google Inc.
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

package org.eclipse.jgit.storage.dht;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_OFS_DELTA;
import static org.eclipse.jgit.lib.Constants.OBJ_REF_DELTA;
import static org.eclipse.jgit.lib.Constants.PACK_SIGNATURE;
import static org.eclipse.jgit.lib.Constants.newMessageDigest;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.zip.Deflater;

import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.storage.dht.spi.memory.MemoryDatabase;
import org.eclipse.jgit.storage.file.PackLock;
import org.eclipse.jgit.storage.pack.DeltaEncoder;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.junit.Before;
import org.junit.Test;

public class DhtPackParserTest {
	private MemoryDatabase db;

	@Before
	public void setUpDatabase() {
		db = new MemoryDatabase();
	}

	@Test
	public void testParse() throws IOException {
		DhtRepository repo = db.open("test.git");
		repo.create(true);

		ObjectInserter.Formatter fmt = new ObjectInserter.Formatter();
		byte[] data0 = new byte[512];
		Arrays.fill(data0, (byte) 0xf3);
		ObjectId id0 = fmt.idFor(OBJ_BLOB, data0);

		TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(64 * 1024);
		packHeader(pack, 4);
		objectHeader(pack, OBJ_BLOB, data0.length);
		deflate(pack, data0);

		byte[] data1 = clone(0x01, data0);
		byte[] delta1 = delta(data0, data1);
		ObjectId id1 = fmt.idFor(OBJ_BLOB, data1);
		objectHeader(pack, OBJ_REF_DELTA, delta1.length);
		id0.copyRawTo(pack);
		deflate(pack, delta1);

		byte[] data2 = clone(0x02, data1);
		byte[] delta2 = delta(data1, data2);
		ObjectId id2 = fmt.idFor(OBJ_BLOB, data2);
		objectHeader(pack, OBJ_REF_DELTA, delta2.length);
		id1.copyRawTo(pack);
		deflate(pack, delta2);

		byte[] data3 = clone(0x03, data2);
		byte[] delta3 = delta(data2, data3);
		ObjectId id3 = fmt.idFor(OBJ_BLOB, data3);
		objectHeader(pack, OBJ_REF_DELTA, delta3.length);
		id2.copyRawTo(pack);
		deflate(pack, delta3);
		digest(pack);

		ObjectInserter ins = repo.newObjectInserter();
		try {
			InputStream is = new ByteArrayInputStream(pack.toByteArray());
			DhtPackParser p = (DhtPackParser) ins.newPackParser(is);
			PackLock lock = p.parse(NullProgressMonitor.INSTANCE);
			assertNull(lock);
		} finally {
			ins.release();
		}

		ObjectReader ctx = repo.newObjectReader();
		try {
			assertTrue(ctx.has(id0, OBJ_BLOB));
			assertTrue(ctx.has(id1, OBJ_BLOB));
			assertTrue(ctx.has(id2, OBJ_BLOB));
			assertTrue(ctx.has(id3, OBJ_BLOB));
		} finally {
			ctx.release();
		}
	}

	@Test
	public void testLargeFragmentWithRefDelta() throws IOException {
		DhtInserterOptions insOpt = new DhtInserterOptions().setChunkSize(256);
		@SuppressWarnings("unchecked")
		DhtRepository repo = (DhtRepository) new DhtRepositoryBuilder<DhtRepositoryBuilder, DhtRepository, MemoryDatabase>()
				.setInserterOptions(insOpt).setDatabase(db) //
				.setRepositoryName("test.git") //
				.setMustExist(false) //
				.build();
		repo.create(true);

		ObjectInserter.Formatter fmt = new ObjectInserter.Formatter();
		TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(64 * 1024);
		packHeader(pack, 3);

		byte[] data3 = new byte[4];
		Arrays.fill(data3, (byte) 0xf3);
		ObjectId id3 = fmt.idFor(OBJ_BLOB, data3);
		objectHeader(pack, OBJ_BLOB, data3.length);
		deflate(pack, data3);

		byte[] data0 = newArray(insOpt.getChunkSize() * 2);
		ObjectId id0 = fmt.idFor(OBJ_BLOB, data0);
		objectHeader(pack, OBJ_BLOB, data0.length);
		store(pack, data0);
		assertTrue(pack.length() > insOpt.getChunkSize());

		byte[] data1 = clone(1, data0);
		ObjectId id1 = fmt.idFor(OBJ_BLOB, data1);
		byte[] delta1 = delta(data0, data1);
		objectHeader(pack, OBJ_REF_DELTA, delta1.length);
		id0.copyRawTo(pack);
		deflate(pack, delta1);

		digest(pack);

		ObjectInserter ins = repo.newObjectInserter();
		try {
			InputStream is = new ByteArrayInputStream(pack.toByteArray());
			DhtPackParser p = (DhtPackParser) ins.newPackParser(is);
			PackLock lock = p.parse(NullProgressMonitor.INSTANCE);
			assertNull(lock);
		} finally {
			ins.release();
		}

		ObjectReader ctx = repo.newObjectReader();
		try {
			assertTrue(ctx.has(id0, OBJ_BLOB));
			assertTrue(ctx.has(id1, OBJ_BLOB));
			assertTrue(ctx.has(id3, OBJ_BLOB));
		} finally {
			ctx.release();
		}
	}

	@Test
	public void testLargeFragmentWithOfsDelta() throws IOException {
		DhtInserterOptions insOpt = new DhtInserterOptions().setChunkSize(256);
		@SuppressWarnings("unchecked")
		DhtRepository repo = (DhtRepository) new DhtRepositoryBuilder<DhtRepositoryBuilder, DhtRepository, MemoryDatabase>()
				.setInserterOptions(insOpt).setDatabase(db) //
				.setRepositoryName("test.git") //
				.setMustExist(false) //
				.build();
		repo.create(true);

		ObjectInserter.Formatter fmt = new ObjectInserter.Formatter();
		TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(64 * 1024);
		packHeader(pack, 3);

		byte[] data3 = new byte[4];
		Arrays.fill(data3, (byte) 0xf3);
		ObjectId id3 = fmt.idFor(OBJ_BLOB, data3);
		objectHeader(pack, OBJ_BLOB, data3.length);
		deflate(pack, data3);

		byte[] data0 = newArray(insOpt.getChunkSize() * 2);
		ObjectId id0 = fmt.idFor(OBJ_BLOB, data0);
		long pos0 = pack.length();
		objectHeader(pack, OBJ_BLOB, data0.length);
		store(pack, data0);
		assertTrue(pack.length() > insOpt.getChunkSize());

		byte[] data1 = clone(1, data0);
		ObjectId id1 = fmt.idFor(OBJ_BLOB, data1);
		byte[] delta1 = delta(data0, data1);
		long pos1 = pack.length();
		objectHeader(pack, OBJ_OFS_DELTA, delta1.length);
		writeOffset(pack, pos1 - pos0);
		deflate(pack, delta1);

		digest(pack);

		ObjectInserter ins = repo.newObjectInserter();
		try {
			InputStream is = new ByteArrayInputStream(pack.toByteArray());
			DhtPackParser p = (DhtPackParser) ins.newPackParser(is);
			PackLock lock = p.parse(NullProgressMonitor.INSTANCE);
			assertNull(lock);
		} finally {
			ins.release();
		}

		ObjectReader ctx = repo.newObjectReader();
		try {
			assertTrue(ctx.has(id0, OBJ_BLOB));
			assertTrue(ctx.has(id1, OBJ_BLOB));
			assertTrue(ctx.has(id3, OBJ_BLOB));
		} finally {
			ctx.release();
		}
	}

	private byte[] newArray(int size) {
		byte[] r = new byte[size];
		for (int i = 0; i < r.length; i++)
			r[i] = (byte) (42 + i);
		return r;
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
		pack.write(PACK_SIGNATURE);
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

	private void writeOffset(TemporaryBuffer.Heap pack, long offsetDiff)
			throws IOException {
		byte[] headerBuffer = new byte[32];
		int n = headerBuffer.length - 1;
		headerBuffer[n] = (byte) (offsetDiff & 0x7F);
		while ((offsetDiff >>= 7) > 0)
			headerBuffer[--n] = (byte) (0x80 | (--offsetDiff & 0x7F));
		pack.write(headerBuffer, n, headerBuffer.length - n);
	}

	private void deflate(TemporaryBuffer.Heap pack, byte[] content)
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

	private void store(TemporaryBuffer.Heap pack, byte[] content)
			throws IOException {
		final Deflater deflater = new Deflater(Deflater.NO_COMPRESSION);
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
		MessageDigest md = newMessageDigest();
		md.update(buf.toByteArray());
		buf.write(md.digest());
	}
}
