/*
 * Copyright (C) 2008, Imran M Yousuf <imyousuf@smartitengineering.com>
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2009, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.junit.Test;

public class PackIndexV2Test extends PackIndexTestCase {
	@Override
	public File getFileForPack34be9032() {
		return JGitTestUtil.getTestResourceFile(
				"pack-34be9032ac282b11fa9babdc2b2a93ca996c9c2f.idxV2");
	}

	@Override
	public File getFileForPackdf2982f28() {
		return JGitTestUtil.getTestResourceFile(
				"pack-df2982f284bbabb6bdb59ee3fcc6eb0983e20371.idxV2");
	}

	@Override
	public File getFileForBadFanoutTable() {
		return JGitTestUtil.getTestResourceFile("pack-bad-fanout-table.idxV2");
	}

	/**
	 * Verify CRC32 indexing.
	 *
	 * @throws UnsupportedOperationException
	 * @throws MissingObjectException
	 */
	@Override
	@Test
	public void testCRC32() throws MissingObjectException,
			UnsupportedOperationException {
		assertTrue(smallIdx.hasCRC32Support());
		assertEquals(0x00000000C2B64258L, smallIdx.findCRC32(ObjectId
				.fromString("4b825dc642cb6eb9a060e54bf8d69288fbee4904")));
		assertEquals(0x0000000072AD57C2L, smallIdx.findCRC32(ObjectId
				.fromString("540a36d136cf413e4b064c2b0e0a4db60f77feab")));
		assertEquals(0x00000000FF10A479L, smallIdx.findCRC32(ObjectId
				.fromString("5b6e7c66c276e7610d4a73c70ec1a1f7c1003259")));
		assertEquals(0x0000000034B27DDCL, smallIdx.findCRC32(ObjectId
				.fromString("6ff87c4664981e4397625791c8ea3bbb5f2279a3")));
		assertEquals(0x000000004743F1E4L, smallIdx.findCRC32(ObjectId
				.fromString("82c6b885ff600be425b4ea96dee75dca255b69e7")));
		assertEquals(0x00000000640B358BL, smallIdx.findCRC32(ObjectId
				.fromString("902d5476fa249b7abc9d84c611577a81381f0327")));
		assertEquals(0x000000002A17CB5EL, smallIdx.findCRC32(ObjectId
				.fromString("aabf2ffaec9b497f0950352b3e582d73035c2035")));
		assertEquals(0x000000000B3B5BA6L, smallIdx.findCRC32(ObjectId
				.fromString("c59759f143fb1fe21c197981df75a7ee00290799")));
	}

	@Test
	public void testMemorySizeWithoutOffset64() {
		assertEquals(8, smallIdx.getObjectCount());
		assertEquals(0, smallIdx.getOffset64Count());
		assertEquals(8L * (Constants.OBJECT_ID_LENGTH + 8L),
				smallIdx.getMemorySize());
	}

	@Test
	public void testMemorySizeWithOffset64() throws IOException {
		List<PackedObjectInfo> entries = new ArrayList<>();
		entries.add(objectWithOffset("0000000000000000000000000000000000000001",
				12L));
		entries.add(objectWithOffset("0000000000000000000000000000000000000002",
				0x7fffffffL));
		entries.add(objectWithOffset("8000000000000000000000000000000000000003",
				0x80000000L));
		entries.add(objectWithOffset("ff00000000000000000000000000000000000004",
				10L * (1L << 30)));
		Collections.sort(entries);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BasePackIndexWriter.createVersion(out, 2).write(entries,
				new byte[Constants.OBJECT_ID_LENGTH]);
		PackIndex idx = PackIndex
				.read(new ByteArrayInputStream(out.toByteArray()));

		assertEquals(4, idx.getObjectCount());
		assertEquals(2, idx.getOffset64Count());
		assertEquals(4L * (Constants.OBJECT_ID_LENGTH + 8L) + 2L * 8L,
				idx.getMemorySize());
	}

	private static PackedObjectInfo objectWithOffset(String sha1, long offset) {
		PackedObjectInfo info = new PackedObjectInfo(ObjectId.fromString(sha1));
		info.setOffset(offset);
		return info;
	}
}
