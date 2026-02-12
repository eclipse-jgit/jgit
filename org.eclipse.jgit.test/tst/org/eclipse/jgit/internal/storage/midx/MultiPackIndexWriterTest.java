/*
 * Copyright (C) 2025, Google LLC
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.midx;

import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.CHUNK_LOOKUP_WIDTH;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_BITMAPPEDPACKS;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_LARGEOFFSETS;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_OBJECTOFFSETS;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_OIDFANOUT;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_OIDLOOKUP;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_PACKNAMES;
import static org.eclipse.jgit.internal.storage.midx.MultiPackIndexConstants.MIDX_CHUNKID_REVINDEX;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.junit.FakeIndexFactory;
import org.eclipse.jgit.junit.FakeIndexFactory.IndexObject;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.util.NB;
import org.junit.Test;

@SuppressWarnings("boxing")
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

		PackIndexMerger data = PackIndexMerger.builder()
				.addPack("packname1", index1).addPack("packname2", index2)
				.build();

		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(NullProgressMonitor.INSTANCE, out, data);
		// header (12 bytes)
		// + chunkHeader (7 * 12 bytes)
		// + fanout table (256 * 4 bytes)
		// + OIDs (6 * 20 bytes)
		// + (pack, offset) pairs (6 * 8)
		// + RIDX (6 * 4 bytes)
		// + bitmap pack segments (2 * 8)
		// + packfile names (2 * 10)
		// + checksum (20)
		assertEquals(1368, out.size());
		List<Integer> chunkIds = readChunkIds(out);
		assertEquals(6, chunkIds.size());
		assertEquals(0, chunkIds.indexOf(MIDX_CHUNKID_OIDFANOUT));
		assertEquals(1, chunkIds.indexOf(MIDX_CHUNKID_OIDLOOKUP));
		assertEquals(2, chunkIds.indexOf(MIDX_CHUNKID_OBJECTOFFSETS));
		assertEquals(3, chunkIds.indexOf(MIDX_CHUNKID_REVINDEX));
		assertEquals(4, chunkIds.indexOf(MIDX_CHUNKID_BITMAPPEDPACKS));
		assertEquals(5, chunkIds.indexOf(MIDX_CHUNKID_PACKNAMES));
	}

	@Test
	public void write_smallOffset_limit() throws IOException {
		PackIndex index1 = indexOf(
				object("0000000000000000000000000000000000000001", 500),
				object("0000000000000000000000000000000000000003", 1500),
				object("0000000000000000000000000000000000000005",
						(1L << 32) - 1));
		PackIndex index2 = indexOf(
				object("0000000000000000000000000000000000000002", 500),
				object("0000000000000000000000000000000000000004", 1500),
				object("0000000000000000000000000000000000000006", 3000));
		PackIndexMerger data = PackIndexMerger.builder()
				.addPack("packname1", index1).addPack("packname2", index2)
				.build();

		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(NullProgressMonitor.INSTANCE, out, data);
		// header (12 bytes)
		// + chunkHeader (7 * 12 bytes)
		// + fanout table (256 * 4 bytes)
		// + OIDs (6 * 20 bytes)
		// + (pack, offset) pairs (6 * 8)
		// + RIDX (6 * 4 bytes)
		// + bitmap pack segments (2 * 8)
		// + packfile names (2 * 10)
		// + checksum (20)
		assertEquals(1368, out.size());
		List<Integer> chunkIds = readChunkIds(out);
		assertEquals(6, chunkIds.size());
		assertEquals(0, chunkIds.indexOf(MIDX_CHUNKID_OIDFANOUT));
		assertEquals(1, chunkIds.indexOf(MIDX_CHUNKID_OIDLOOKUP));
		assertEquals(2, chunkIds.indexOf(MIDX_CHUNKID_OBJECTOFFSETS));
		assertEquals(3, chunkIds.indexOf(MIDX_CHUNKID_REVINDEX));
		assertEquals(4, chunkIds.indexOf(MIDX_CHUNKID_BITMAPPEDPACKS));
		assertEquals(5, chunkIds.indexOf(MIDX_CHUNKID_PACKNAMES));
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
		PackIndexMerger data = PackIndexMerger.builder()
				.addPack("bbbbbbbbb", index1).addPack("aaaaaaaaa", index2)
				.build();

		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		MultiPackIndexWriter.Result result = writer
				.write(NullProgressMonitor.INSTANCE, out, data);
		// header (12 bytes)
		// + chunkHeader (8 * 12 bytes)
		// + fanout table (256 * 4 bytes)
		// + OIDs (6 * 20 bytes)
		// + (pack, offset) pairs (6 * 8)
		// + (large-offset) (1 * 8)
		// + RIDX (6 * 4 bytes)
		// + bitmap pack segments (2 * 8)
		// + packfile names (2 * 10)
		// + checksum (20)
		assertEquals(1388, out.size());
		List<Integer> chunkIds = readChunkIds(out);
		assertEquals(7, chunkIds.size());
		assertEquals(0, chunkIds.indexOf(MIDX_CHUNKID_OIDFANOUT));
		assertEquals(1, chunkIds.indexOf(MIDX_CHUNKID_OIDLOOKUP));
		assertEquals(2, chunkIds.indexOf(MIDX_CHUNKID_OBJECTOFFSETS));
		assertEquals(3, chunkIds.indexOf(MIDX_CHUNKID_LARGEOFFSETS));
		assertEquals(4, chunkIds.indexOf(MIDX_CHUNKID_REVINDEX));
		assertEquals(5, chunkIds.indexOf(MIDX_CHUNKID_BITMAPPEDPACKS));
		assertEquals(6, chunkIds.indexOf(MIDX_CHUNKID_PACKNAMES));

		assertEquals("bbbbbbbbb", result.packNames().get(0));
		assertEquals("aaaaaaaaa", result.packNames().get(1));
	}

	@Test
	public void jgit_emptyMidx() throws IOException {
		PackIndex idxOne = FakeIndexFactory.indexOf(List.of());
		PackIndex idxTwo = FakeIndexFactory.indexOf(List.of());
		PackIndexMerger packs = PackIndexMerger.builder().addPack("p1", idxOne)
				.addPack("p2", idxTwo).build();
		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(NullProgressMonitor.INSTANCE, out, packs);
		List<Integer> chunkIds = readChunkIds(out);
		assertEquals(1162, out.size());
		assertEquals(6, chunkIds.size());
		assertEquals(0, chunkIds.indexOf(MIDX_CHUNKID_OIDFANOUT));
		assertEquals(1, chunkIds.indexOf(MIDX_CHUNKID_OIDLOOKUP));
		assertEquals(2, chunkIds.indexOf(MIDX_CHUNKID_OBJECTOFFSETS));
		assertEquals(3, chunkIds.indexOf(MIDX_CHUNKID_REVINDEX));
		assertEquals(4, chunkIds.indexOf(MIDX_CHUNKID_BITMAPPEDPACKS));
		assertEquals(5, chunkIds.indexOf(MIDX_CHUNKID_PACKNAMES));
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
