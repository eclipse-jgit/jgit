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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.util.NB;
import org.junit.Test;

public class ChunkIndexTest {
	@Test
	public void testSingleObject_NotFound() throws DhtException {
		List<PackedObjectInfo> objs = list(object(1, 1));
		ChunkIndex idx = index(objs);
		assertEquals(-1, idx.findOffset(ObjectId.zeroId()));
	}

	@Test
	public void testSingleObject_Offset1() throws DhtException {
		assertEquals(header(0, 1), header(list(object(1, 0))));

		List<PackedObjectInfo> objs = list(object(0x1200, 255));
		ChunkIndex idx = index(objs);

		assertEquals(header(0, 1), header(objs));
		assertEquals(1, idx.getObjectCount());
		assertEquals(2 + 20 + 1, idx.getIndexSize());

		assertEquals(objs.get(0), idx.getObjectId(0));
		assertEquals(objs.get(0).getOffset(), idx.getOffset(0));
		assertEquals(objs.get(0).getOffset(), idx.findOffset(objs.get(0)));
	}

	@Test
	public void testSingleObject_Offset2() throws DhtException {
		assertEquals(header(0, 2), header(list(object(1, 1 << 8))));

		List<PackedObjectInfo> objs = list(object(0x1200, 0xab34));
		ChunkIndex idx = index(objs);

		assertEquals(header(0, 2), header(objs));
		assertEquals(1, idx.getObjectCount());
		assertEquals(2 + 20 + 2, idx.getIndexSize());

		assertEquals(objs.get(0), idx.getObjectId(0));
		assertEquals(objs.get(0).getOffset(), idx.getOffset(0));
		assertEquals(objs.get(0).getOffset(), idx.findOffset(objs.get(0)));
	}

	@Test
	public void testSingleObject_Offset3() throws DhtException {
		assertEquals(header(0, 3), header(list(object(1, 1 << 16))));

		List<PackedObjectInfo> objs = list(object(0x1200, 0xab1234));
		ChunkIndex idx = index(objs);

		assertEquals(header(0, 3), header(objs));
		assertEquals(1, idx.getObjectCount());
		assertEquals(2 + 20 + 3, idx.getIndexSize());

		assertEquals(objs.get(0), idx.getObjectId(0));
		assertEquals(objs.get(0).getOffset(), idx.getOffset(0));
		assertEquals(objs.get(0).getOffset(), idx.findOffset(objs.get(0)));
	}

	@Test
	public void testSingleObject_Offset4() throws DhtException {
		assertEquals(header(0, 4), header(list(object(1, 1 << 24))));

		List<PackedObjectInfo> objs = list(object(0x1200, 0x7bcdef42));
		ChunkIndex idx = index(objs);

		assertEquals(header(0, 4), header(objs));
		assertEquals(1, idx.getObjectCount());
		assertEquals(objs.get(0), idx.getObjectId(0));

		assertEquals(2 + 20 + 4, idx.getIndexSize());
		assertEquals(objs.get(0).getOffset(), idx.getOffset(0));
		assertEquals(objs.get(0).getOffset(), idx.findOffset(objs.get(0)));
	}

	@Test
	public void testObjects3() throws DhtException {
		List<PackedObjectInfo> objs = objects(2, 3, 1);
		ChunkIndex idx = index(objs);

		assertEquals(header(0, 1), header(objs));
		assertEquals(3, idx.getObjectCount());
		assertEquals(2 + 3 * 20 + 3 * 1, idx.getIndexSize());
		assertTrue(isSorted(objs));

		for (int i = 0; i < objs.size(); i++) {
			assertEquals(objs.get(i), idx.getObjectId(i));
			assertEquals(objs.get(i).getOffset(), idx.getOffset(i));
			assertEquals(objs.get(i).getOffset(), idx.findOffset(objs.get(i)));
		}
	}

	@Test
	public void testObjects255_SameBucket() throws DhtException {
		int[] ints = new int[255];
		for (int i = 0; i < 255; i++)
			ints[i] = i;
		List<PackedObjectInfo> objs = objects(ints);
		ChunkIndex idx = index(objs);

		assertEquals(header(1, 2), header(objs));
		assertEquals(255, idx.getObjectCount());
		assertEquals(2 + 256 + 255 * 20 + 255 * 2 //
				+ 12 + 4 * 256, idx.getIndexSize());
		assertTrue(isSorted(objs));

		for (int i = 0; i < objs.size(); i++) {
			assertEquals(objs.get(i), idx.getObjectId(i));
			assertEquals(objs.get(i).getOffset(), idx.getOffset(i));
			assertEquals(objs.get(i).getOffset(), idx.findOffset(objs.get(i)));
		}
	}

	@Test
	public void testObjects512_ManyBuckets() throws DhtException {
		int[] ints = new int[512];
		for (int i = 0; i < 256; i++) {
			ints[i] = (i << 8) | 0;
			ints[i + 256] = (i << 8) | 1;
		}
		List<PackedObjectInfo> objs = objects(ints);
		ChunkIndex idx = index(objs);

		assertEquals(header(1, 2), header(objs));
		assertEquals(512, idx.getObjectCount());
		assertEquals(2 + 256 + 512 * 20 + 512 * 2 //
				+ 12 + 4 * 256, idx.getIndexSize());
		assertTrue(isSorted(objs));

		for (int i = 0; i < objs.size(); i++) {
			assertEquals(objs.get(i), idx.getObjectId(i));
			assertEquals(objs.get(i).getOffset(), idx.getOffset(i));
			assertEquals(objs.get(i).getOffset(), idx.findOffset(objs.get(i)));
		}
	}

	@Test
	public void testFanout2() throws DhtException {
		List<PackedObjectInfo> objs = new ArrayList<PackedObjectInfo>(65280);
		MutableObjectId idBuf = new MutableObjectId();
		for (int i = 0; i < 256; i++) {
			idBuf.setByte(2, i & 0xff);
			for (int j = 0; j < 255; j++) {
				idBuf.setByte(3, j & 0xff);
				PackedObjectInfo oe = new PackedObjectInfo(idBuf);
				oe.setOffset((i << 8) | j);
				objs.add(oe);
			}
		}
		ChunkIndex idx = index(objs);

		assertEquals(header(2, 2), header(objs));
		assertEquals(256 * 255, idx.getObjectCount());
		assertTrue(isSorted(objs));

		for (int i = 0; i < objs.size(); i++) {
			assertEquals(objs.get(i), idx.getObjectId(i));
			assertEquals(objs.get(i).getOffset(), idx.getOffset(i));
			assertEquals(objs.get(i).getOffset(), idx.findOffset(objs.get(i)));
		}
	}

	@Test
	public void testFanout3() throws DhtException {
		List<PackedObjectInfo> objs = new ArrayList<PackedObjectInfo>(1 << 16);
		MutableObjectId idBuf = new MutableObjectId();
		for (int i = 0; i < 256; i++) {
			idBuf.setByte(2, i & 0xff);
			for (int j = 0; j < 256; j++) {
				idBuf.setByte(3, j & 0xff);
				PackedObjectInfo oe = new PackedObjectInfo(idBuf);
				oe.setOffset((i << 8) | j);
				objs.add(oe);
			}
		}
		ChunkIndex idx = index(objs);

		assertEquals(header(3, 2), header(objs));
		assertEquals(256 * 256, idx.getObjectCount());
		assertTrue(isSorted(objs));

		for (int i = 0; i < objs.size(); i++) {
			assertEquals(objs.get(i), idx.getObjectId(i));
			assertEquals(objs.get(i).getOffset(), idx.getOffset(i));
			assertEquals(objs.get(i).getOffset(), idx.findOffset(objs.get(i)));
		}
	}

	@Test
	public void testObjects65280_ManyBuckets() throws DhtException {
		List<PackedObjectInfo> objs = new ArrayList<PackedObjectInfo>(65280);
		MutableObjectId idBuf = new MutableObjectId();
		for (int i = 0; i < 256; i++) {
			idBuf.setByte(0, i & 0xff);
			for (int j = 0; j < 255; j++) {
				idBuf.setByte(3, j & 0xff);
				PackedObjectInfo oe = new PackedObjectInfo(idBuf);
				oe.setOffset((i << 8) | j);
				objs.add(oe);
			}
		}
		ChunkIndex idx = index(objs);

		assertEquals(header(1, 2), header(objs));
		assertEquals(65280, idx.getObjectCount());
		assertTrue(isSorted(objs));

		for (int i = 0; i < objs.size(); i++) {
			assertEquals(objs.get(i), idx.getObjectId(i));
			assertEquals(objs.get(i).getOffset(), idx.getOffset(i));
			assertEquals(objs.get(i).getOffset(), idx.findOffset(objs.get(i)));
		}
	}

	private boolean isSorted(List<PackedObjectInfo> objs) {
		PackedObjectInfo last = objs.get(0);
		for (int i = 1; i < objs.size(); i++) {
			PackedObjectInfo oe = objs.get(i);
			if (oe.compareTo(last) <= 0)
				return false;
		}
		return true;
	}

	private List<PackedObjectInfo> list(PackedObjectInfo... all) {
		List<PackedObjectInfo> objs = new ArrayList<PackedObjectInfo>();
		for (PackedObjectInfo o : all)
			objs.add(o);
		return objs;
	}

	private int header(int fanoutTable, int offsetTable) {
		return (0x01 << 8) | (fanoutTable << 3) | offsetTable;
	}

	private int header(List<PackedObjectInfo> objs) {
		byte[] index = ChunkIndex.create(objs);
		return NB.decodeUInt16(index, 0);
	}

	private ChunkIndex index(List<PackedObjectInfo> objs) throws DhtException {
		ChunkKey key = null;
		byte[] index = ChunkIndex.create(objs);
		return ChunkIndex.fromBytes(key, index, 0, index.length);
	}

	private List<PackedObjectInfo> objects(int... values) {
		List<PackedObjectInfo> objs = new ArrayList<PackedObjectInfo>();
		for (int i = 0; i < values.length; i++)
			objs.add(object(values[i], i * 10));
		return objs;
	}

	private PackedObjectInfo object(int id, int off) {
		MutableObjectId idBuf = new MutableObjectId();
		idBuf.setByte(0, (id >>> 8) & 0xff);
		idBuf.setByte(1, id & 0xff);

		PackedObjectInfo obj = new PackedObjectInfo(idBuf);
		obj.setOffset(off);
		return obj;
	}
}
