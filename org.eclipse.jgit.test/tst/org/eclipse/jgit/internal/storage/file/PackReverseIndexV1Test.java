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

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.PackMismatchException;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.junit.Before;
import org.junit.Test;

public class PackReverseIndexV1Test {
	private static final byte[] FAKE_PACK_CHECKSUM = new byte[] { 'P', 'A', 'C',
			'K', 'C', 'H', 'E', 'C', 'K', 'S', 'U', 'M', '3', '4', '5', '6',
			'7', '8', '9', '0', };

	private static final byte[] NO_OBJECTS = new byte[] { 'R', 'I', 'D', 'X', // magic
			0x00, 0x00, 0x00, 0x01, // file version
			0x00, 0x00, 0x00, 0x01, // oid version
			// pack checksum to copy into at byte 12
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			// checksum
			(byte) 0xd1, 0x1d, 0x17, (byte) 0xd5, (byte) 0xa1, 0x5c,
			(byte) 0x8f, 0x45, 0x7e, 0x06, (byte) 0x91, (byte) 0xf2, 0x7e, 0x20,
			0x35, 0x2c, (byte) 0xdc, 0x4c, 0x46, (byte) 0xe4, };

	private static final byte[] SMALL_PACK_CHECKSUM = new byte[] { (byte) 0xbb,
			0x1d, 0x25, 0x3d, (byte) 0xd3, (byte) 0xf0, 0x08, 0x75, (byte) 0xc8,
			0x04, (byte) 0xd0, 0x6f, 0x73, (byte) 0xe9, 0x00, (byte) 0x82,
			(byte) 0xdb, 0x09, (byte) 0xc8, 0x13, };

	private static final byte[] SMALL_CONTENTS = new byte[] { 'R', 'I', 'D',
			'X', // magic
			0x00, 0x00, 0x00, 0x01, // file version
			0x00, 0x00, 0x00, 0x01, // oid version
			0x00, 0x00, 0x00, 0x04, // offset 12: "68" -> index @ 4
			0x00, 0x00, 0x00, 0x02, // offset 165: "5c" -> index @ 2
			0x00, 0x00, 0x00, 0x03, // offset 257: "62" -> index @ 3
			0x00, 0x00, 0x00, 0x01, // offset 450: "58" -> index @ 1
			0x00, 0x00, 0x00, 0x05, // offset 556: "c5" -> index @ 5
			0x00, 0x00, 0x00, 0x00, // offset 614: "2d" -> index @ 0
			// pack checksum to copy into at byte 36
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			// checksum
			(byte) 0xf0, 0x6d, 0x03, (byte) 0xd7, 0x6f, (byte) 0x9f,
			(byte) 0xc1, 0x36, 0x26, (byte) 0xbc, (byte) 0xcb, 0x75, 0x36,
			(byte) 0xa1, 0x26, 0x6a, 0x2b, (byte) 0x84, 0x16, (byte) 0x83, };

	private PackReverseIndex emptyReverseIndex;

	/**
	 * Reverse index for the pack-cbdeda40019ae0e6e789088ea0f51f164f489d14.idx
	 * with contents `SHA-1 type size size-in-packfile offset-in-packfile` as
	 * shown by `verify-pack`:
	 * 2d04ee74dba30078c2dcdb713ddb8be4bc084d76 blob   8 17 614
	 * 58728c938a9a8b9970cc09236caf94ada4689923 blob   140 106 450
	 * 5ce00008cf3fb8f194f52742020bd40d78f3f1b3 commit 81 92 165 1 68cb1f232964f3cd698afc1dafe583937203c587
	 * 62299a7ae290d685196e948a2fcb7d8c07f95c7d tree   198 193 257
	 * 68cb1f232964f3cd698afc1dafe583937203c587 commit 220 153 12
	 * c5ab27309491cf641eb11bb4b7a78641f280b482 tree   46 58 556 1 62299a7ae290d685196e948a2fcb7d8c07f95c7d
	 */
	private PackReverseIndex smallReverseIndex;

	private final PackedObjectInfo object614 = objectInfo(
			"2d04ee74dba30078c2dcdb713ddb8be4bc084d76", OBJ_BLOB, 614);

	private final PackedObjectInfo object450 = objectInfo(
			"58728c938a9a8b9970cc09236caf94ada4689923", OBJ_BLOB, 450);

	private final PackedObjectInfo object165 = objectInfo(
			"5ce00008cf3fb8f194f52742020bd40d78f3f1b3", OBJ_COMMIT, 165);

	private final PackedObjectInfo object257 = objectInfo(
			"62299a7ae290d685196e948a2fcb7d8c07f95c7d", OBJ_TREE, 257);

	private final PackedObjectInfo object12 = objectInfo(
			"68cb1f232964f3cd698afc1dafe583937203c587", OBJ_COMMIT, 12);

	private final PackedObjectInfo object556 = objectInfo(
			"c5ab27309491cf641eb11bb4b7a78641f280b482", OBJ_TREE, 556);

	// last object's offset + last object's length
	private final long smallMaxOffset = 631;

	@Before
	public void setUp() throws Exception {
		System.arraycopy(SMALL_PACK_CHECKSUM, 0, SMALL_CONTENTS, 36,
				SMALL_PACK_CHECKSUM.length);
		ByteArrayInputStream smallIn = new ByteArrayInputStream(SMALL_CONTENTS);
		smallReverseIndex = PackReverseIndexFactory.readFromFile(smallIn, 6,
				() -> PackIndex.open(JGitTestUtil.getTestResourceFile(
						"pack-cbdeda40019ae0e6e789088ea0f51f164f489d14.idx")));

		System.arraycopy(FAKE_PACK_CHECKSUM, 0, NO_OBJECTS, 12,
				FAKE_PACK_CHECKSUM.length);
		ByteArrayInputStream emptyIn = new ByteArrayInputStream(NO_OBJECTS);
		emptyReverseIndex = PackReverseIndexFactory.readFromFile(emptyIn, 0,
				() -> null);
	}

	@Test
	public void read_unsupportedOidSHA256() {
		byte[] version2 = new byte[] { 'R', 'I', 'D', 'X', // magic
				0x00, 0x00, 0x00, 0x01, // file version
				0x00, 0x00, 0x00, 0x02, // oid version
				// pack checksum to copy into at byte 12
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				// checksum
				0x6e, 0x78, 0x75, 0x67, (byte) 0x84, (byte) 0x89, (byte) 0xde,
				(byte) 0xe3, (byte) 0x86, 0x6a, 0x3b, (byte) 0x98, 0x51,
				(byte) 0xd8, (byte) 0x8c, (byte) 0xec, 0x50, (byte) 0xe7,
				(byte) 0xfb, 0x22, };
		System.arraycopy(FAKE_PACK_CHECKSUM, 0, version2, 12,
				FAKE_PACK_CHECKSUM.length);
		ByteArrayInputStream in = new ByteArrayInputStream(version2);

		assertThrows(IOException.class,
				() -> PackReverseIndexFactory.readFromFile(in, 0, () -> null));
	}

	@Test
	public void read_objectCountTooLarge() {
		ByteArrayInputStream dummyInput = new ByteArrayInputStream(NO_OBJECTS);
		long biggerThanInt = ((long) Integer.MAX_VALUE) + 1;

		assertThrows(IllegalArgumentException.class,
				() -> PackReverseIndexFactory.readFromFile(dummyInput,
						biggerThanInt,
						() -> null));
	}

	@Test
	public void read_incorrectChecksum() {
		byte[] badChecksum = new byte[] { 'R', 'I', 'D', 'X', // magic
				0x00, 0x00, 0x00, 0x01, // file version
				0x00, 0x00, 0x00, 0x01, // oid version
				// pack checksum to copy into at byte 12
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				// checksum
				(byte) 0xf2, 0x1a, 0x1a, (byte) 0xaa, 0x32, 0x2d, (byte) 0xb9,
				(byte) 0xfd, 0x0f, (byte) 0xa5, 0x4c, (byte) 0xea, (byte) 0xcf,
				(byte) 0xbb, (byte) 0x99, (byte) 0xde, (byte) 0xd3, 0x4e,
				(byte) 0xb1, (byte) 0xee, // would be 0x74 if correct
		};
		System.arraycopy(FAKE_PACK_CHECKSUM, 0, badChecksum, 12,
				FAKE_PACK_CHECKSUM.length);
		ByteArrayInputStream in = new ByteArrayInputStream(badChecksum);
		assertThrows(CorruptObjectException.class,
				() -> PackReverseIndexFactory.readFromFile(in, 0, () -> null));
	}

	@Test
	public void findObject_noObjects() {
		assertNull(emptyReverseIndex.findObject(0));
	}

	@Test
	public void findObject_multipleObjects() {
		assertEquals(object614, smallReverseIndex.findObject(614));
		assertEquals(object450, smallReverseIndex.findObject(450));
		assertEquals(object165, smallReverseIndex.findObject(165));
		assertEquals(object257, smallReverseIndex.findObject(257));
		assertEquals(object12, smallReverseIndex.findObject(12));
		assertEquals(object556, smallReverseIndex.findObject(556));
	}

	@Test
	public void findObject_badOffset() {
		assertNull(smallReverseIndex.findObject(0));
	}

	@Test
	public void findNextOffset_noObjects() {
		assertThrows(IOException.class,
				() -> emptyReverseIndex.findNextOffset(0, Long.MAX_VALUE));
	}

	@Test
	public void findNextOffset_multipleObjects() throws CorruptObjectException {
		assertEquals(smallMaxOffset,
				smallReverseIndex.findNextOffset(614, smallMaxOffset));
		assertEquals(614,
				smallReverseIndex.findNextOffset(556, smallMaxOffset));
		assertEquals(556,
				smallReverseIndex.findNextOffset(450, smallMaxOffset));
		assertEquals(450,
				smallReverseIndex.findNextOffset(257, smallMaxOffset));
		assertEquals(257,
				smallReverseIndex.findNextOffset(165, smallMaxOffset));
		assertEquals(165, smallReverseIndex.findNextOffset(12, smallMaxOffset));
	}

	@Test
	public void findNextOffset_badOffset() {
		assertThrows(IOException.class,
				() -> smallReverseIndex.findNextOffset(0, Long.MAX_VALUE));
	}

	@Test
	public void findPosition_noObjects() {
		assertEquals(-1, emptyReverseIndex.findPosition(0));
	}

	@Test
	public void findPosition_multipleObjects() {
		assertEquals(0, smallReverseIndex.findPosition(12));
		assertEquals(1, smallReverseIndex.findPosition(165));
		assertEquals(2, smallReverseIndex.findPosition(257));
		assertEquals(3, smallReverseIndex.findPosition(450));
		assertEquals(4, smallReverseIndex.findPosition(556));
		assertEquals(5, smallReverseIndex.findPosition(614));
	}

	@Test
	public void findPosition_badOffset() {
		assertEquals(-1, smallReverseIndex.findPosition(10));
	}

	@Test
	public void findObjectByPosition_noObjects() {
		assertThrows(AssertionError.class,
				() -> emptyReverseIndex.findObjectByPosition(0));
	}

	@Test
	public void findObjectByPosition_multipleObjects() {
		assertEquals(object12, smallReverseIndex.findObjectByPosition(0));
		assertEquals(object165, smallReverseIndex.findObjectByPosition(1));
		assertEquals(object257, smallReverseIndex.findObjectByPosition(2));
		assertEquals(object450, smallReverseIndex.findObjectByPosition(3));
		assertEquals(object556, smallReverseIndex.findObjectByPosition(4));
		assertEquals(object614, smallReverseIndex.findObjectByPosition(5));
	}

	@Test
	public void findObjectByPosition_badOffset() {
		assertThrows(AssertionError.class,
				() -> smallReverseIndex.findObjectByPosition(10));
	}

	@Test
	public void verifyChecksum_match() throws IOException {
		smallReverseIndex.verifyPackChecksum("smallPackFilePath");
	}

	@Test
	public void verifyChecksum_mismatch() throws IOException {
		ByteArrayInputStream in = new ByteArrayInputStream(NO_OBJECTS);
		PackIndex mockForwardIndex = mock(PackIndex.class);
		when(mockForwardIndex.getChecksum()).thenReturn(
				new byte[] { 'D', 'I', 'F', 'F', 'P', 'A', 'C', 'K', 'C', 'H',
						'E', 'C', 'K', 'S', 'U', 'M', '7', '8', '9', '0', });
		PackReverseIndex reverseIndex = PackReverseIndexFactory.readFromFile(in,
				0,
				() -> mockForwardIndex);

		assertThrows(PackMismatchException.class,
				() -> reverseIndex.verifyPackChecksum("packFilePath"));
	}

	private static PackedObjectInfo objectInfo(String objectId, int type,
			long offset) {
		PackedObjectInfo objectInfo = new PackedObjectInfo(
				ObjectId.fromString(objectId));
		objectInfo.setType(type);
		objectInfo.setOffset(offset);
		return objectInfo;
	}
}
