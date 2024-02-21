/*
 * Copyright (C) 2023, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.eclipse.jgit.lib.Constants.OBJECT_ID_STRING_LENGTH;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;
import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.junit.Test;

public class PackReverseIndexWriterV1Test {

	private static byte[] PACK_CHECKSUM = new byte[] { 'P', 'A', 'C', 'K', 'C',
			'H', 'E', 'C', 'K', 'S', 'U', 'M', '3', '4', '5', '6', '7', '8',
			'9', '0', };

	@Test
	public void write_noObjects() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackReverseIndexWriter writer = PackReverseIndexWriter.createWriter(out,
				1);
		List<PackedObjectInfo> objectsSortedByName = new ArrayList<>();

		writer.write(objectsSortedByName, PACK_CHECKSUM);

		byte[] expected = new byte[] { 'R', 'I', 'D', 'X', // magic
				0x00, 0x00, 0x00, 0x01, // file version
				0x00, 0x00, 0x00, 0x01, // oid version
				// pack checksum to copy into at byte 12
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				// checksum
				(byte) 0xd1, 0x1d, 0x17, (byte) 0xd5, (byte) 0xa1, 0x5c,
				(byte) 0x8f, 0x45, 0x7e, 0x06, (byte) 0x91, (byte) 0xf2, 0x7e,
				0x20, 0x35, 0x2c, (byte) 0xdc, 0x4c, 0x46, (byte) 0xe4, };
		System.arraycopy(PACK_CHECKSUM, 0, expected, 12, PACK_CHECKSUM.length);
		assertArrayEquals(expected, out.toByteArray());
	}

	@Test
	public void write_oneObject() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackReverseIndexWriter writer = PackReverseIndexWriter.createWriter(out,
				1);
		PackedObjectInfo a = objectInfo("a", OBJ_COMMIT, 0);
		List<PackedObjectInfo> objectsSortedByName = List.of(a);

		writer.write(objectsSortedByName, PACK_CHECKSUM);

		byte[] expected = new byte[] { 'R', 'I', 'D', 'X', // magic
				0x00, 0x00, 0x00, 0x01, // file version
				0x00, 0x00, 0x00, 0x01, // oid version
				0x00, 0x00, 0x00, 0x00, // 0: "a" -> 0
				// pack checksum to copy into at byte 16
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				// checksum
				0x48, 0x04, 0x29, 0x69, 0x2f, (byte) 0xf3, (byte) 0x80,
				(byte) 0xa1, (byte) 0xf5, (byte) 0x92, (byte) 0xc2, 0x21, 0x46,
				(byte) 0xd5, 0x08, 0x44, (byte) 0xa4, (byte) 0xf4, (byte) 0xc0,
				(byte) 0xec, };
		System.arraycopy(PACK_CHECKSUM, 0, expected, 16, PACK_CHECKSUM.length);
		assertArrayEquals(expected, out.toByteArray());
	}

	@Test
	public void write_multipleObjects() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackReverseIndexWriter writer = PackReverseIndexWriter.createWriter(out,
				1);
		PackedObjectInfo a = objectInfo("a", OBJ_BLOB, 20);
		PackedObjectInfo b = objectInfo("b", OBJ_COMMIT, 0);
		PackedObjectInfo c = objectInfo("c", OBJ_COMMIT, 52);
		PackedObjectInfo d = objectInfo("d", OBJ_TREE, 7);
		PackedObjectInfo e = objectInfo("e", OBJ_COMMIT, 38);
		List<PackedObjectInfo> objectsSortedByName = List.of(a, b, c, d, e);

		writer.write(objectsSortedByName, PACK_CHECKSUM);

		byte[] expected = new byte[] { 'R', 'I', 'D', 'X', // magic
				0x00, 0x00, 0x00, 0x01, // file version
				0x00, 0x00, 0x00, 0x01, // oid version
				0x00, 0x00, 0x00, 0x01, // offset 0: "b" -> index @ 1
				0x00, 0x00, 0x00, 0x03, // offset 7: "d" -> index @ 3
				0x00, 0x00, 0x00, 0x00, // offset 20: "a" -> index @ 0
				0x00, 0x00, 0x00, 0x04, // offset 38: "e" -> index @ 4
				0x00, 0x00, 0x00, 0x02, // offset 52: "c" -> index @ 2
				// pack checksum to copy into at byte 32
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				// checksum
				(byte) 0xe3, 0x1c, (byte) 0xd3, (byte) 0xeb, (byte) 0x83, 0x47,
				(byte) 0x80, (byte) 0xb1, (byte) 0xe0, 0x3c, 0x2b, 0x25,
				(byte) 0xd6, 0x3e, (byte) 0xdc, (byte) 0xde, (byte) 0xbe, 0x4b,
				0x0a, (byte) 0xe2, };
		System.arraycopy(PACK_CHECKSUM, 0, expected, 32, PACK_CHECKSUM.length);
		assertArrayEquals(expected, out.toByteArray());
	}

	private static PackedObjectInfo objectInfo(String objectId, int type,
			long offset) {
		assert (objectId.length() == 1);
		PackedObjectInfo objectInfo = new PackedObjectInfo(
				ObjectId.fromString(objectId.repeat(OBJECT_ID_STRING_LENGTH)));
		objectInfo.setType(type);
		objectInfo.setOffset(offset);
		return objectInfo;
	}
}
