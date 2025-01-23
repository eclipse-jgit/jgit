/*
 * Copyright (C) 2025, Google Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.midx;

import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.CHUNK_ID_OBJECT_LARGE_OFFSET;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.CHUNK_ID_OBJECT_OFFSET;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.CHUNK_ID_OID_FANOUT;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.CHUNK_ID_OID_LOOKUP;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.CHUNK_ID_PACKFILE_NAMES;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.CHUNK_ID_RIDX;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.CHUNK_LOOKUP_WIDTH;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.junit.FakeIndexFactory;
import org.eclipse.jgit.junit.FakeIndexFactory.IndexObject;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.util.NB;
import org.junit.Test;

public class MultiPackIndexWriterTest {

	@Test
	public void write_allSmallOffsets() throws IOException {
		PackIndex index1 = indexOf(
				object("0000000000000000000000000000000000000001", 500),
				object("0000000000000000000000000000000000000003", 1500),
				object("0000000000000000000000000000000000000005", 3000));
		PackIndex index2 = indexOf(
				object("0000000000000000000000000000000000000002", 500),
				object("0000000000000000000000000000000000000004", 1500),
				object("0000000000000000000000000000000000000006", 3000));

		Map<String, PackIndex> data = Map.of("packname1", index1, "packname2",
				index2);

		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(NullProgressMonitor.INSTANCE, out, data);
		// header (12 bytes)
		// + chunkHeader (6 * 12 bytes)
		// + fanout table (256 * 4 bytes)
		// + OIDs (6 * 20 bytes)
		// + (pack, offset) pairs (6 * 8)
		// + RIDX (6 * 4 bytes)
		// + packfile names (2 * 10)
		// + checksum (20)
		assertEquals(1340, out.size());
		List<Integer> chunkIds = readChunkIds(out);
		assertEquals(5, chunkIds.size());
		assertEquals(0, chunkIds.indexOf(CHUNK_ID_OID_FANOUT));
		assertEquals(1, chunkIds.indexOf(CHUNK_ID_OID_LOOKUP));
		assertEquals(2, chunkIds.indexOf(CHUNK_ID_OBJECT_OFFSET));
		assertEquals(3, chunkIds.indexOf(CHUNK_ID_RIDX));
		assertEquals(4, chunkIds.indexOf(CHUNK_ID_PACKFILE_NAMES));
	}

	@Test
	public void write_smallOffset_limit() throws IOException {
		PackIndex index1 = indexOf(
				object("0000000000000000000000000000000000000001", 500),
				object("0000000000000000000000000000000000000003", 1500),
				object("0000000000000000000000000000000000000005", (1L << 32) -1));
		PackIndex index2 = indexOf(
				object("0000000000000000000000000000000000000002", 500),
				object("0000000000000000000000000000000000000004", 1500),
				object("0000000000000000000000000000000000000006", 3000));
		Map<String, PackIndex> data =
				Map.of("packname1", index1, "packname2", index2);

		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(NullProgressMonitor.INSTANCE, out, data);
		// header (12 bytes)
		// + chunkHeader (6 * 12 bytes)
		// + fanout table (256 * 4 bytes)
		// + OIDs (6 * 20 bytes)
		// + (pack, offset) pairs (6 * 8)
		// + RIDX (6 * 4 bytes)
		// + packfile names (2 * 10)
		// + checksum (20)
		assertEquals(1340, out.size());
		List<Integer> chunkIds = readChunkIds(out);
		assertEquals(5, chunkIds.size());
		assertEquals(0, chunkIds.indexOf(CHUNK_ID_OID_FANOUT));
		assertEquals(1, chunkIds.indexOf(CHUNK_ID_OID_LOOKUP));
		assertEquals(2, chunkIds.indexOf(CHUNK_ID_OBJECT_OFFSET));
		assertEquals(3, chunkIds.indexOf(CHUNK_ID_RIDX));
		assertEquals(4, chunkIds.indexOf(CHUNK_ID_PACKFILE_NAMES));
	}

	@Test
	public void write_largeOffset() throws IOException {
		PackIndex index1 = indexOf(
				object("0000000000000000000000000000000000000001", 500),
				object("0000000000000000000000000000000000000003", 1500),
				object("0000000000000000000000000000000000000005", 1L << 32));
		PackIndex index2 = indexOf(
				object("0000000000000000000000000000000000000002", 500),
				object("0000000000000000000000000000000000000004", 1500),
				object("0000000000000000000000000000000000000006", 3000));
		Map<String, PackIndex> data =
				Map.of("packname1", index1, "packname2", index2);

		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(NullProgressMonitor.INSTANCE, out, data);
		// header (12 bytes)
		// + chunkHeader (7 * 12 bytes)
		// + fanout table (256 * 4 bytes)
		// + OIDs (6 * 20 bytes)
		// + (pack, offset) pairs (6 * 8)
		// + (large-offset) (1 * 8)
		// + RIDX (6 * 4 bytes)
		// + packfile names (2 * 10)
		// + checksum (20)
		assertEquals(1360, out.size());
		List<Integer> chunkIds = readChunkIds(out);
		assertEquals(6, chunkIds.size());
		assertEquals(0, chunkIds.indexOf(CHUNK_ID_OID_FANOUT));
		assertEquals(1, chunkIds.indexOf(CHUNK_ID_OID_LOOKUP));
		assertEquals(2, chunkIds.indexOf(CHUNK_ID_OBJECT_OFFSET));
		assertEquals(3, chunkIds.indexOf(CHUNK_ID_OBJECT_LARGE_OFFSET));
		assertEquals(4, chunkIds.indexOf(CHUNK_ID_RIDX));
		assertEquals(5, chunkIds.indexOf(CHUNK_ID_PACKFILE_NAMES));
	}

	private List<Integer> readChunkIds(ByteArrayOutputStream out) {
		List<Integer> chunkIds = new ArrayList<>();
		byte[] raw = out.toByteArray();
		int numChunks = raw[6];
		int position = 12;
		for (int i = 0; i < numChunks; i++) {
			chunkIds.add(NB.decodeInt32(raw, position));
			position += CHUNK_LOOKUP_WIDTH;
		}
		return chunkIds;
	}

	private static PackIndex indexOf(IndexObject... objs) {
		return FakeIndexFactory.indexOf(Arrays.asList(objs));
	}

	private static IndexObject object(String name, long offset) {
		return new IndexObject(name, offset);
	}
}
