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

import static org.eclipse.jgit.lib.Constants.OBJECT_ID_STRING_LENGTH;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.junit.Test;

public class PackReverseIndexV1WriteReadTest {

	private static byte[] PACK_CHECKSUM = new byte[] { 'P', 'A', 'C', 'K', 'C',
			'H', 'E', 'C', 'K', 'S', 'U', 'M', '3', '4', '5', '6', '7', '8',
			'9', '0', };

	@Test
	public void writeThenRead_noObjects() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackReverseIndexWriter writer = PackReverseIndexWriter.createWriter(out,
				1);
		List<PackedObjectInfo> objectsSortedByName = new ArrayList<>();

		// write
		writer.write(objectsSortedByName, PACK_CHECKSUM);

		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

		// read
		PackReverseIndex noObjectsReverseIndex = PackReverseIndex.read(in, 0,
				() -> null);

		// use
		assertThrows(AssertionError.class,
				() -> noObjectsReverseIndex.findObjectByPosition(0));
	}

	@Test
	public void writeThenRead_oneObject() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackReverseIndexWriter writer = PackReverseIndexWriter.createWriter(out,
				1);
		PackedObjectInfo a = objectInfo("a", OBJ_COMMIT, 0);
		List<PackedObjectInfo> objectsSortedByName = List.of(a);

		// write
		writer.write(objectsSortedByName, PACK_CHECKSUM);

		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		PackIndex mockForwardIndex = mock(PackIndex.class);
		when(mockForwardIndex.getObjectId(0)).thenReturn(a);

		// read
		PackReverseIndex oneObjectReverseIndex = PackReverseIndex.read(in, 1,
				() -> mockForwardIndex);

		// use
		assertEquals(a, oneObjectReverseIndex.findObjectByPosition(0));
	}

	@Test
	public void writeThenRead_multipleObjectsLargeOffsets() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PackReverseIndexWriter writer = PackReverseIndexWriter.createWriter(out,
				1);
		PackedObjectInfo a = objectInfo("a", OBJ_BLOB, 200000000);
		PackedObjectInfo b = objectInfo("b", OBJ_COMMIT, 0);
		PackedObjectInfo c = objectInfo("c", OBJ_COMMIT, 52000000000L);
		PackedObjectInfo d = objectInfo("d", OBJ_TREE, 7);
		PackedObjectInfo e = objectInfo("e", OBJ_COMMIT, 38000000000L);
		List<PackedObjectInfo> objectsSortedByName = List.of(a, b, c, d, e);

		writer.write(objectsSortedByName, PACK_CHECKSUM);

		// write
		writer.write(objectsSortedByName, PACK_CHECKSUM);

		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		PackIndex mockForwardIndex = mock(PackIndex.class);
		when(mockForwardIndex.getObjectId(4)).thenReturn(e);

		// read
		PackReverseIndex multipleObjectsReverseIndex = PackReverseIndex.read(in,
				5, () -> mockForwardIndex);

		// use with minimal mocked forward index use
		assertEquals(e, multipleObjectsReverseIndex.findObjectByPosition(3));
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
