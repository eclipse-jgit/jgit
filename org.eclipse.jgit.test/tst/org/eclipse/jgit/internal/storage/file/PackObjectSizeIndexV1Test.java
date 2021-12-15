/*
 * Copyright (C) 2021, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.junit.Test;

public class PackObjectSizeIndexV1Test {
	private final static ObjectId OBJ_ID = ObjectId
			.fromString("b8b1d53172fb3fb19647adce4b38fab4371c2454");

	private final static long GB = 1 << 30;

	@Test
	public void write_only32bits() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackObjectSizeIndexWriter writer = PackObjectSizeIndexWriter
				.createWriter(out, 0);
		List<PackedObjectInfo> objs = new ArrayList<>();
		// writer will order by offset
		objs.add(blobObjInfo(7, 100));
		objs.add(blobObjInfo(5, 400));
		objs.add(blobObjInfo(9, 200));

		writer.write(objs);
		byte[] expected = new byte[] {
				-1, 's', 'i', 'z', // header
				0x00, 0x00, 0x00, 0x01, // version
				0x00, 0x00, 0x00, 0x00, // minimum object size
				0x00, 0x00, 0x00, 0x03, // 32 bit offsets
				0x00, 0x00, 0x00, 0x00, // 64 bit offsets
				0x00, 0x00, 0x00, 0x05, // offset 0
				0x00, 0x00, 0x00, 0x07, // offset 1
				0x00, 0x00, 0x00, 0x09, // offset 2
				0x00, 0x00, 0x01, (byte) 0x90, // size 400
				0x00, 0x00, 0x00, 0x64, // size 100
				0x00, 0x00, 0x00, (byte) 0xc8, // size 200
				0x00, 0x00, 0x00, 0x00 // 64bit sizes counter
		};
		byte[] output = out.toByteArray();
		assertArrayEquals(expected, output);
	}

	@Test
	public void write_64bitsSize() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackObjectSizeIndexWriter writer = PackObjectSizeIndexWriter
				.createWriter(out, 0);
		List<PackedObjectInfo> objs = new ArrayList<>();
		objs.add(blobObjInfo(0, 100));
		objs.add(blobObjInfo(1, 3 * GB));
		objs.add(blobObjInfo(2, 4 * GB));
		objs.add(blobObjInfo(3, 400));

		writer.write(objs);
		byte[] expected = new byte[] { -1, 's', 'i', 'z', // header
				0x00, 0x00, 0x00, 0x01, // version
				0x00, 0x00, 0x00, 0x00, // minimum object size
				0x00, 0x00, 0x00, 0x04, // 32 bit offsets (4)
				0x00, 0x00, 0x00, 0x00, // 64 bit offsets (0)
				0x00, 0x00, 0x00, 0x00, // offset32[0] = 0
				0x00, 0x00, 0x00, 0x01, // offset32[1] = 1
				0x00, 0x00, 0x00, 0x02, // offset32[2] = 2
				0x00, 0x00, 0x00, 0x03, // offset32[3] = 3
				// 32
				0x00, 0x00, 0x00, 0x64, // size32[0] = 100
				(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, // -1
				(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xfe, // -2
				0x00, 0x00, 0x01, (byte) 0x90, // size32[3] = 400
				0x00, 0x00, 0x00, (byte) 0x02, // sizes > 2Gb (2)
				0x00, 0x00, 0x00, 0x00, // size64[0] = 3Gb
				(byte) 0xc0, 0x00, 0x00, 0x00,
				0x00, 0x00, 0x00, 0x01, // size64[1] = 4Gb
				(byte) 0x00, 0x00, 0x00, 0x00,
				0x00, 0x00, 0x00, 0x00 // next level count
		};
		byte[] output = out.toByteArray();
		assertArrayEquals(expected, output);
	}

	@Test
	public void write_64bitsOffset() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackObjectSizeIndexWriter writer = PackObjectSizeIndexWriter
				.createWriter(out, 0);
		List<PackedObjectInfo> objs = new ArrayList<>();
		objs.add(blobObjInfo(0, 100));
		objs.add(blobObjInfo(1L << 34, 200));
		objs.add(blobObjInfo(2, 400));

		writer.write(objs);
		byte[] expected = new byte[] { -1, 's', 'i', 'z', // header
				0x00, 0x00, 0x00, 0x01, // version
				0x00, 0x00, 0x00, 0x00, // minimum object size
				0x00, 0x00, 0x00, 0x02, // 32 bit offsets (2)
				0x00, 0x00, 0x00, 0x01, // 64 bit offsets (1)
				0x00, 0x00, 0x00, 0x00, // offset32[0] = 0
				0x00, 0x00, 0x00, 0x02, // offset32[1] = 2
				0x00, 0x00, 0x00, 0x04, //
				0x00, 0x00, 0x00, 0x00, // offset64[0] = (1 << 34)
				0x00, 0x00, 0x00, 0x64, // size32[0] = 100
				0x00, 0x00, 0x01, (byte) 0x90, // size32[1] = 400
				0x00, 0x00, 0x00, (byte) 0xc8, // size32[2] = 200
				0x00, 0x00, 0x00, (byte) 0x00, // sizes > 2Gb (0)
		};
		byte[] output = out.toByteArray();
		assertArrayEquals(expected, output);
	}

	@Test
	public void write_64bitsOffsetsAndSizes() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackObjectSizeIndexWriter writer = PackObjectSizeIndexWriter
				.createWriter(out, 0);
		List<PackedObjectInfo> objs = new ArrayList<>();
		objs.add(blobObjInfo(0, 100));
		objs.add(blobObjInfo(1L << 34, 200));
		objs.add(blobObjInfo(3, 3 * GB));
		objs.add(blobObjInfo(2, 400));

		writer.write(objs);
		byte[] expected = new byte[] { -1, 's', 'i', 'z', // header
				0x00, 0x00, 0x00, 0x01, // version
				0x00, 0x00, 0x00, 0x00, // minimum object size
				0x00, 0x00, 0x00, 0x03, // 32 bit offsets (3)
				0x00, 0x00, 0x00, 0x01, // 64 bit offsets (1)
				0x00, 0x00, 0x00, 0x00, // offset32[0] = 0
				0x00, 0x00, 0x00, 0x02, // offset32[1] = 2
				0x00, 0x00, 0x00, 0x03, // offset32[2] = 3
				0x00, 0x00, 0x00, 0x04, //
				// 32
				0x00, 0x00, 0x00, 0x00, // offset64[0] = (1 << 34)
				0x00, 0x00, 0x00, 0x64, // size32[0] = 100
				0x00, 0x00, 0x01, (byte) 0x90, // size32[1] = 400
				(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, // -1
				0x00, 0x00, 0x00, (byte) 0xc8, // size32[3] = 200
				0x00, 0x00, 0x00, (byte) 0x01, // sizes > 2Gb (1)
				0x00, 0x00, 0x00, 0x00, //
				(byte) 0xc0, 0x00, 0x00, 0x00, // size64[0] = 3Gb
				0x00, 0x00, 0x00, 0x00, // next level count
		};
		byte[] output = out.toByteArray();
		assertArrayEquals(expected, output);
	}

	@Test
	public void write_allObjectsTooSmall() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackObjectSizeIndexWriter writer = PackObjectSizeIndexWriter
				.createWriter(out, 1 << 10);
		List<PackedObjectInfo> objs = new ArrayList<>();
		// too small blobs
		objs.add(blobObjInfo(0, 100));
		objs.add(blobObjInfo(1L << 34, 200));
		objs.add(blobObjInfo(2, 400));

		writer.write(objs);
		byte[] expected = new byte[] { -1, 's', 'i', 'z', // header
				0x00, 0x00, 0x00, 0x01, // version
				0x00, 0x00, 0x04, 0x00, // minimum object size
				0x00, 0x00, 0x00, 0x00, // 32 bit offsets (0)
				0x00, 0x00, 0x00, 0x00, // 64 bit offsets (0)
				0x00, 0x00, 0x00, 0x00, // 64 bit sizes size
		};
		byte[] output = out.toByteArray();
		assertArrayEquals(expected, output);
	}

	@Test
	public void write_onlyBlobsIndexed() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackObjectSizeIndexWriter writer = PackObjectSizeIndexWriter
				.createWriter(out, 0);
		List<PackedObjectInfo> objs = new ArrayList<>();
		// writer will order by offset
		objs.add(objInfo(Constants.OBJ_COMMIT, 1000, 1000));
		objs.add(objInfo(Constants.OBJ_TAG, 1001, 1000));
		objs.add(blobObjInfo(7, 100));
		objs.add(blobObjInfo(5, 400));
		objs.add(blobObjInfo(9, 200));

		writer.write(objs);
		byte[] expected = new byte[] { -1, 's', 'i', 'z', // header
				0x00, 0x00, 0x00, 0x01, // version
				0x00, 0x00, 0x00, 0x00, // minimum object size
				0x00, 0x00, 0x00, 0x03, // 32 bit offsets
				0x00, 0x00, 0x00, 0x00, // 64 bit offsets
				0x00, 0x00, 0x00, 0x05, // offset 0
				0x00, 0x00, 0x00, 0x07, // offset 1
				0x00, 0x00, 0x00, 0x09, // offset 2
				0x00, 0x00, 0x01, (byte) 0x90, // size 400
				0x00, 0x00, 0x00, 0x64, // size 100
				0x00, 0x00, 0x00, (byte) 0xc8, // size 200
				0x00, 0x00, 0x00, 0x00 // 64bit sizes counter
		};
		byte[] output = out.toByteArray();
		assertArrayEquals(expected, output);
	}

	@Test
	public void write_noObjects() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackObjectSizeIndexWriter writer = PackObjectSizeIndexWriter
				.createWriter(out, 0);
		List<PackedObjectInfo> objs = new ArrayList<>();

		writer.write(objs);
		byte[] expected = new byte[] { -1, 's', 'i', 'z', // header
				0x00, 0x00, 0x00, 0x01, // version
				0x00, 0x00, 0x00, 0x00, // minimum object size
				0x00, 0x00, 0x00, 0x00, // 32 bit offsets (3)
				0x00, 0x00, 0x00, 0x00, // 64 bit offsets (1)
				0x00, 0x00, 0x00, 0x00, // 64 bit sizes size
		};
		byte[] output = out.toByteArray();
		assertArrayEquals(expected, output);
	}

	@Test
	public void read_empty() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackObjectSizeIndexWriter writer = PackObjectSizeIndexWriter
				.createWriter(out, 0);
		List<PackedObjectInfo> objs = new ArrayList<>();

		writer.write(objs);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		PackObjectSizeIndex index = PackObjectSizeIndexLoader.load(in);
		// All objects are fine in this index
		assertEquals(-1, index.getSize(0));
		assertEquals(-1, index.getSize(1));
		assertEquals(-1, index.getSize(1L << 30));
	}

	@Test
	public void read_only32bits() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackObjectSizeIndexWriter writer = PackObjectSizeIndexWriter
				.createWriter(out, 0);
		List<PackedObjectInfo> objs = new ArrayList<>();
		objs.add(blobObjInfo(0, 100));
		objs.add(blobObjInfo(1, 200));
		objs.add(blobObjInfo(2, 400));
		objs.add(blobObjInfo(3, 1500));

		writer.write(objs);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		PackObjectSizeIndex index = PackObjectSizeIndexLoader.load(in);
		assertEquals(100, index.getSize(0));
		assertEquals(200, index.getSize(1));
		assertEquals(400, index.getSize(2));
		assertEquals(1500, index.getSize(3));
	}

	@Test
	public void read_32and64bits_offsetsAndsizes() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackObjectSizeIndexWriter writer = PackObjectSizeIndexWriter
				.createWriter(out, 0);
		List<PackedObjectInfo> objs = new ArrayList<>();
		objs.add(blobObjInfo(0, 100));
		objs.add(blobObjInfo(1L << 34, 200));
		objs.add(blobObjInfo(2, 400));
		objs.add(blobObjInfo(500, 4 * GB));
		objs.add(blobObjInfo(3, 3 * GB));

		writer.write(objs);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		PackObjectSizeIndex index = PackObjectSizeIndexLoader.load(in);
		assertEquals(100, index.getSize(0));
		assertEquals(200, index.getSize(1L << 34));
		assertEquals(400, index.getSize(2));
		assertEquals(3 * GB, index.getSize(3));
		assertEquals(4 * GB, index.getSize(500));
	}

	@Test
	public void read_withMinSize() throws IOException {
		int minSize = 1000;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackObjectSizeIndexWriter writer = PackObjectSizeIndexWriter
				.createWriter(out, minSize);
		List<PackedObjectInfo> objs = new ArrayList<>();
		objs.add(blobObjInfo(0, 500));
		objs.add(blobObjInfo(1, 1000));
		objs.add(blobObjInfo(2, 1500));
		objs.add(blobObjInfo(3, 2000));
		objs.add(blobObjInfo(4, 3 * GB));

		writer.write(objs);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		PackObjectSizeIndex index = PackObjectSizeIndexLoader.load(in);
		assertEquals(-1, index.getSize(0));
		assertEquals(1000, index.getSize(1));
		assertEquals(1500, index.getSize(2));
		assertEquals(2000, index.getSize(3));
		assertEquals(3 * GB, index.getSize(4));
	}

	private static PackedObjectInfo blobObjInfo(long offset, long size) {
		return objInfo(Constants.OBJ_BLOB, offset, size);
	}

	private static PackedObjectInfo objInfo(int type, long offset, long size) {
		PackedObjectInfo objectInfo = new PackedObjectInfo(OBJ_ID);
		objectInfo.setType(type);
		objectInfo.setFullSize(size);
		objectInfo.setOffset(offset);
		return objectInfo;
	}
}
