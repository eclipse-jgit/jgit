/*
 * Copyright (C) 2022, Google LLC and others
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
import org.eclipse.jgit.util.BlockList;
import org.junit.Test;

public class PackObjectSizeIndexV1Test {
	private static final ObjectId OBJ_ID = ObjectId
			.fromString("b8b1d53172fb3fb19647adce4b38fab4371c2454");

	private static final long GB = 1 << 30;

	private static final int MAX_24BITS_UINT = 0xffffff;

	@Test
	public void write_24bPositions_32bSizes() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackObjectSizeIndexWriter writer = PackObjectSizeIndexWriter
				.createWriter(out, 0);
		List<PackedObjectInfo> objs = new ArrayList<>();
		objs.add(blobWithSize(100));
		objs.add(blobWithSize(400));
		objs.add(blobWithSize(200));

		writer.write(objs);
		byte[] expected = new byte[] { -1, 's', 'i', 'z', // header
				0x01, // version
				0x00, 0x00, 0x00, 0x00, // minimum object size
				0x00, 0x00, 0x00, 0x03, // obj count
				0x18, // Unsigned 3 bytes
				0x00, 0x00, 0x00, 0x03, // 3 positions
				0x00, 0x00, 0x00, // positions
				0x00, 0x00, 0x01, //
				0x00, 0x00, 0x02, //
				0x00, // No more positions
				0x00, 0x00, 0x00, 0x64, // size 100
				0x00, 0x00, 0x01, (byte) 0x90, // size 400
				0x00, 0x00, 0x00, (byte) 0xc8, // size 200
				0x00, 0x00, 0x00, 0x00 // 64bit sizes counter
		};
		byte[] output = out.toByteArray();
		assertArrayEquals(expected, output);
	}

	@Test
	public void write_32bPositions_32bSizes() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackObjectSizeIndexWriter writer = PackObjectSizeIndexWriter
				.createWriter(out, 0);
		List<PackedObjectInfo> objs = new BlockList<>(9_000_000);
		// The 24 bit range is full of commits and trees
		PackedObjectInfo commit = objInfo(Constants.OBJ_COMMIT, 100);
		for (int i = 0; i <= MAX_24BITS_UINT; i++) {
			objs.add(commit);
		}
		objs.add(blobWithSize(100));
		objs.add(blobWithSize(400));
		objs.add(blobWithSize(200));

		writer.write(objs);
		byte[] expected = new byte[] { -1, 's', 'i', 'z', // header
				0x01, // version
				0x00, 0x00, 0x00, 0x00, // minimum object size
				0x00, 0x00, 0x00, 0x03, // obj count
				(byte) 0x20, // Signed 4 bytes
				0x00, 0x00, 0x00, 0x03, // 3 positions
				0x01, 0x00, 0x00, 0x00, // positions
				0x01, 0x00, 0x00, 0x01, //
				0x01, 0x00, 0x00, 0x02, //
				0x00, // No more positions
				0x00, 0x00, 0x00, 0x64, // size 100
				0x00, 0x00, 0x01, (byte) 0x90, // size 400
				0x00, 0x00, 0x00, (byte) 0xc8, // size 200
				0x00, 0x00, 0x00, 0x00 // 64bit sizes counter
		};
		byte[] output = out.toByteArray();
		assertArrayEquals(expected, output);
	}

	@Test
	public void write_24b32bPositions_32bSizes() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackObjectSizeIndexWriter writer = PackObjectSizeIndexWriter
				.createWriter(out, 0);
		List<PackedObjectInfo> objs = new BlockList<>(9_000_000);
		// The 24 bit range is full of commits and trees
		PackedObjectInfo commit = objInfo(Constants.OBJ_COMMIT, 100);
		for (int i = 0; i < MAX_24BITS_UINT; i++) {
			objs.add(commit);
		}
		objs.add(blobWithSize(100));
		objs.add(blobWithSize(400));
		objs.add(blobWithSize(200));

		writer.write(objs);
		byte[] expected = new byte[] { -1, 's', 'i', 'z', // header
				0x01, // version
				0x00, 0x00, 0x00, 0x00, // minimum object size
				0x00, 0x00, 0x00, 0x03, // obj count
				0x18, // 3 bytes
				0x00, 0x00, 0x00, 0x01, // 1 position
				(byte) 0xff, (byte) 0xff, (byte) 0xff,
				(byte) 0x20, // 4 bytes (32 bits)
				0x00, 0x00, 0x00, 0x02, // 2 positions
				0x01, 0x00, 0x00, 0x00, // positions
				0x01, 0x00, 0x00, 0x01, //
				0x00, // No more positions
				0x00, 0x00, 0x00, 0x64, // size 100
				0x00, 0x00, 0x01, (byte) 0x90, // size 400
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
		objs.add(blobWithSize(100));
		objs.add(blobWithSize(3 * GB));
		objs.add(blobWithSize(4 * GB));
		objs.add(blobWithSize(400));

		writer.write(objs);
		byte[] expected = new byte[] { -1, 's', 'i', 'z', // header
				0x01, // version
				0x00, 0x00, 0x00, 0x00, // minimum object size
				0x00, 0x00, 0x00, 0x04, // Object count
				0x18, // Unsigned 3 byte positions
				0x00, 0x00, 0x00, 0x04, // 4 positions
				0x00, 0x00, 0x00, // positions
				0x00, 0x00, 0x01, //
				0x00, 0x00, 0x02, //
				0x00, 0x00, 0x03, //
				0x00, // No more positions
				0x00, 0x00, 0x00, 0x64, // size 100
				(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, // -1 (3GB)
				(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xfe, // -2 (4GB)
				0x00, 0x00, 0x01, (byte) 0x90, // size 400
				0x00, 0x00, 0x00, (byte) 0x02, // 64bit sizes counter (2)
				0x00, 0x00, 0x00, 0x00, // size 3Gb
				(byte) 0xc0, 0x00, 0x00, 0x00, //
				0x00, 0x00, 0x00, 0x01, // size 4GB
				(byte) 0x00, 0x00, 0x00, 0x00, //
				0x00, 0x00, 0x00, 0x00 // 128bit sizes counter
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
		objs.add(blobWithSize(100));
		objs.add(blobWithSize(200));
		objs.add(blobWithSize(400));

		writer.write(objs);
		byte[] expected = new byte[] { -1, 's', 'i', 'z', // header
				0x01, // version
				0x00, 0x00, 0x04, 0x00, // minimum object size
				0x00, 0x00, 0x00, 0x00, // Object count
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
		objs.add(objInfo(Constants.OBJ_COMMIT, 1000));
		objs.add(blobWithSize(100));
		objs.add(objInfo(Constants.OBJ_TAG, 1000));
		objs.add(blobWithSize(400));
		objs.add(blobWithSize(200));

		writer.write(objs);
		byte[] expected = new byte[] { -1, 's', 'i', 'z', // header
				0x01, // version
				0x00, 0x00, 0x00, 0x00, // minimum object size
				0x00, 0x00, 0x00, 0x03, // Object count
				0x18, // Positions in 3 bytes
				0x00, 0x00, 0x00, 0x03, // 3 entries
				0x00, 0x00, 0x01, // positions
				0x00, 0x00, 0x03, //
				0x00, 0x00, 0x04, //
				0x00, // No more positions
				0x00, 0x00, 0x00, 0x64, // size 100
				0x00, 0x00, 0x01, (byte) 0x90, // size 400
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
				0x01, // version
				0x00, 0x00, 0x00, 0x00, // minimum object size
				0x00, 0x00, 0x00, 0x00, // Object count
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
		assertEquals(-1, index.getSize(0));
		assertEquals(-1, index.getSize(1));
		assertEquals(-1, index.getSize(1 << 30));
		assertEquals(0, index.getThreshold());
	}

	@Test
	public void read_only24bitsPositions() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackObjectSizeIndexWriter writer = PackObjectSizeIndexWriter
				.createWriter(out, 0);
		List<PackedObjectInfo> objs = new ArrayList<>();
		objs.add(blobWithSize(100));
		objs.add(blobWithSize(200));
		objs.add(blobWithSize(400));
		objs.add(blobWithSize(1500));

		writer.write(objs);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		PackObjectSizeIndex index = PackObjectSizeIndexLoader.load(in);
		assertEquals(100, index.getSize(0));
		assertEquals(200, index.getSize(1));
		assertEquals(400, index.getSize(2));
		assertEquals(1500, index.getSize(3));
		assertEquals(0, index.getThreshold());
	}

	@Test
	public void read_only32bitsPositions() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackObjectSizeIndexWriter writer = PackObjectSizeIndexWriter
				.createWriter(out, 2000);
		List<PackedObjectInfo> objs = new ArrayList<>();
		PackedObjectInfo smallObj = blobWithSize(100);
		for (int i = 0; i <= MAX_24BITS_UINT; i++) {
			objs.add(smallObj);
		}
		objs.add(blobWithSize(1000));
		objs.add(blobWithSize(3000));
		objs.add(blobWithSize(2500));
		objs.add(blobWithSize(1000));

		writer.write(objs);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		PackObjectSizeIndex index = PackObjectSizeIndexLoader.load(in);
		assertEquals(-1, index.getSize(5));
		assertEquals(-1, index.getSize(MAX_24BITS_UINT+1));
		assertEquals(3000, index.getSize(MAX_24BITS_UINT+2));
		assertEquals(2500, index.getSize(MAX_24BITS_UINT+3));
		assertEquals(-1, index.getSize(MAX_24BITS_UINT+4)); // Not indexed
		assertEquals(2000, index.getThreshold());
	}

	@Test
	public void read_24and32BitsPositions() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackObjectSizeIndexWriter writer = PackObjectSizeIndexWriter
				.createWriter(out, 2000);
		List<PackedObjectInfo> objs = new ArrayList<>();
		PackedObjectInfo smallObj = blobWithSize(100);
		for (int i = 0; i <= MAX_24BITS_UINT; i++) {
			if (i == 500 || i == 1000 || i == 1500) {
				objs.add(blobWithSize(2500));
				continue;
			}
			objs.add(smallObj);
		}
		objs.add(blobWithSize(3000));
		objs.add(blobWithSize(1000));
		objs.add(blobWithSize(2500));
		objs.add(blobWithSize(1000));

		writer.write(objs);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		PackObjectSizeIndex index = PackObjectSizeIndexLoader.load(in);
		// 24 bit positions
		assertEquals(-1, index.getSize(5));
		assertEquals(2500, index.getSize(500));
		// 32 bit positions
		assertEquals(3000, index.getSize(MAX_24BITS_UINT+1));
		assertEquals(-1, index.getSize(MAX_24BITS_UINT+2));
		assertEquals(2500, index.getSize(MAX_24BITS_UINT+3));
		assertEquals(-1, index.getSize(MAX_24BITS_UINT+4)); // Not indexed
		assertEquals(2000, index.getThreshold());
	}

	@Test
	public void read_only64bits() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackObjectSizeIndexWriter writer = PackObjectSizeIndexWriter
				.createWriter(out, 0);
		List<PackedObjectInfo> objs = new ArrayList<>();
		objs.add(blobWithSize(3 * GB));
		objs.add(blobWithSize(8 * GB));

		writer.write(objs);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		PackObjectSizeIndex index = PackObjectSizeIndexLoader.load(in);
		assertEquals(3 * GB, index.getSize(0));
		assertEquals(8 * GB, index.getSize(1));
		assertEquals(0, index.getThreshold());
	}

	@Test
	public void read_withMinSize() throws IOException {
		int minSize = 1000;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackObjectSizeIndexWriter writer = PackObjectSizeIndexWriter
				.createWriter(out, minSize);
		List<PackedObjectInfo> objs = new ArrayList<>();
		objs.add(blobWithSize(3 * GB));
		objs.add(blobWithSize(1500));
		objs.add(blobWithSize(500));
		objs.add(blobWithSize(1000));
		objs.add(blobWithSize(2000));

		writer.write(objs);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		PackObjectSizeIndex index = PackObjectSizeIndexLoader.load(in);
		assertEquals(3 * GB, index.getSize(0));
		assertEquals(1500, index.getSize(1));
		assertEquals(-1, index.getSize(2));
		assertEquals(1000, index.getSize(3));
		assertEquals(2000, index.getSize(4));
		assertEquals(minSize, index.getThreshold());
	}

	private static PackedObjectInfo blobWithSize(long size) {
		return objInfo(Constants.OBJ_BLOB, size);
	}

	private static PackedObjectInfo objInfo(int type, long size) {
		PackedObjectInfo objectInfo = new PackedObjectInfo(OBJ_ID);
		objectInfo.setType(type);
		objectInfo.setFullSize(size);
		return objectInfo;
	}
}
