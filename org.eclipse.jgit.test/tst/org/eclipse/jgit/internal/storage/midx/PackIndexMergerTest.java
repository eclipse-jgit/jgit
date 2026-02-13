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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex.MutableEntry;
import org.eclipse.jgit.junit.FakeIndexFactory;
import org.eclipse.jgit.junit.FakeIndexFactory.IndexObject;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.junit.Test;

public class PackIndexMergerTest {

	@Test
	public void bySha1Iterator_noDuplicates() {
		PackIndex idxOne = indexOf(
				oidOffset("0000000000000000000000000000000000000001", 500),
				oidOffset("0000000000000000000000000000000000000005", 12),
				oidOffset("0000000000000000000000000000000000000010", 1500));
		PackIndex idxTwo = indexOf(
				oidOffset("0000000000000000000000000000000000000002", 501),
				oidOffset("0000000000000000000000000000000000000003", 13),
				oidOffset("0000000000000000000000000000000000000015", 1501));
		PackIndex idxThree = indexOf(
				oidOffset("0000000000000000000000000000000000000004", 502),
				oidOffset("0000000000000000000000000000000000000007", 14),
				oidOffset("0000000000000000000000000000000000000012", 1502));
		PackIndexMerger merger = createMergerFor("p1", idxOne, "p2", idxTwo,
				"p3", idxThree);
		assertEquals(9, merger.getUniqueObjectCount());
		assertEquals(3, merger.getPackCount());
		assertFalse(merger.needsLargeOffsetsChunk());
		Iterator<MutableEntry> it = merger.bySha1Iterator();
		assertNextEntry(it, "0000000000000000000000000000000000000001", 0, 500);
		assertNextEntry(it, "0000000000000000000000000000000000000002", 1, 501);
		assertNextEntry(it, "0000000000000000000000000000000000000003", 1, 13);
		assertNextEntry(it, "0000000000000000000000000000000000000004", 2, 502);
		assertNextEntry(it, "0000000000000000000000000000000000000005", 0, 12);
		assertNextEntry(it, "0000000000000000000000000000000000000007", 2, 14);
		assertNextEntry(it, "0000000000000000000000000000000000000010", 0,
				1500);
		assertNextEntry(it, "0000000000000000000000000000000000000012", 2,
				1502);
		assertNextEntry(it, "0000000000000000000000000000000000000015", 1,
				1501);
		assertFalse(it.hasNext());
	}

	@Test
	public void bySha1Iterator_withDuplicates() {
		PackIndex idxOne = indexOf(
				oidOffset("0000000000000000000000000000000000000001", 500),
				oidOffset("0000000000000000000000000000000000000010", 1500));
		PackIndex idxTwo = indexOf(
				oidOffset("0000000000000000000000000000000000000002", 501),
				oidOffset("0000000000000000000000000000000000000003", 13),
				oidOffset("0000000000000000000000000000000000000005", 800),
				oidOffset("0000000000000000000000000000000000000015", 1501));
		PackIndex idxThree = indexOf(
				oidOffset("0000000000000000000000000000000000000004", 502),
				oidOffset("0000000000000000000000000000000000000005", 12),
				oidOffset("0000000000000000000000000000000000000007", 14),
				oidOffset("0000000000000000000000000000000000000012", 1502));
		PackIndexMerger merger = createMergerFor("p1", idxOne, "p2", idxTwo,
				"p3", idxThree);
		assertEquals(9, merger.getUniqueObjectCount());
		assertEquals(3, merger.getPackCount());
		assertFalse(merger.needsLargeOffsetsChunk());
		Iterator<MutableEntry> it = merger.bySha1Iterator();
		assertNextEntry(it, "0000000000000000000000000000000000000001", 0, 500);
		assertNextEntry(it, "0000000000000000000000000000000000000002", 1, 501);
		assertNextEntry(it, "0000000000000000000000000000000000000003", 1, 13);
		assertNextEntry(it, "0000000000000000000000000000000000000004", 2, 502);
		assertNextEntry(it, "0000000000000000000000000000000000000005", 1, 800);
		assertNextEntry(it, "0000000000000000000000000000000000000007", 2, 14);
		assertNextEntry(it, "0000000000000000000000000000000000000010", 0,
				1500);
		assertNextEntry(it, "0000000000000000000000000000000000000012", 2,
				1502);
		assertNextEntry(it, "0000000000000000000000000000000000000015", 1,
				1501);
		assertFalse(it.hasNext());
	}

	@Test
	public void bySha1Iterator_allDuplicates() {
		PackIndex idxOne = indexOf(
				oidOffset("0000000000000000000000000000000000000001", 500),
				oidOffset("0000000000000000000000000000000000000005", 12),
				oidOffset("0000000000000000000000000000000000000010", 1500));
		PackIndexMerger merger = createMergerFor("p1", idxOne, "p2", idxOne,
				"p3", idxOne);
		assertEquals(3, merger.getUniqueObjectCount());
		assertEquals(3, merger.getPackCount());
		assertFalse(merger.needsLargeOffsetsChunk());
		Iterator<MutableEntry> it = merger.bySha1Iterator();
		assertNextEntry(it, "0000000000000000000000000000000000000001", 0, 500);
		assertNextEntry(it, "0000000000000000000000000000000000000005", 0, 12);
		assertNextEntry(it, "0000000000000000000000000000000000000010", 0,
				1500);
		assertFalse(it.hasNext());
	}

	@Test
	public void bySha1Iterator_differentIndexSizes() {
		PackIndex idxOne = indexOf(
				oidOffset("0000000000000000000000000000000000000010", 1500));
		PackIndex idxTwo = indexOf(
				oidOffset("0000000000000000000000000000000000000002", 500),
				oidOffset("0000000000000000000000000000000000000003", 12));
		PackIndex idxThree = indexOf(
				oidOffset("0000000000000000000000000000000000000004", 500),
				oidOffset("0000000000000000000000000000000000000007", 12),
				oidOffset("0000000000000000000000000000000000000012", 1500));
		PackIndexMerger merger = createMergerFor("p1", idxOne, "p2", idxTwo,
				"p3", idxThree);
		assertEquals(6, merger.getUniqueObjectCount());
		assertEquals(3, merger.getPackCount());
		assertFalse(merger.needsLargeOffsetsChunk());
		Iterator<MutableEntry> it = merger.bySha1Iterator();
		assertNextEntry(it, "0000000000000000000000000000000000000002", 1, 500);
		assertNextEntry(it, "0000000000000000000000000000000000000003", 1, 12);
		assertNextEntry(it, "0000000000000000000000000000000000000004", 2, 500);
		assertNextEntry(it, "0000000000000000000000000000000000000007", 2, 12);
		assertNextEntry(it, "0000000000000000000000000000000000000010", 0,
				1500);
		assertNextEntry(it, "0000000000000000000000000000000000000012", 2,
				1500);
		assertFalse(it.hasNext());
	}

	@Test
	public void bySha1Iterator_withAnotherMidx() throws IOException {
		PackIndex idxOne = indexOf(
				oidOffset("0000000000000000000000000000000000000010", 1500));
		PackIndex idxTwo = indexOf(
				oidOffset("0000000000000000000000000000000000000002", 500),
				oidOffset("0000000000000000000000000000000000000003", 12));
		PackIndex idxThree = indexOf(
				oidOffset("0000000000000000000000000000000000000004", 500),
				oidOffset("0000000000000000000000000000000000000007", 12),
				oidOffset("0000000000000000000000000000000000000012", 1500));
		MultiPackIndex midx = midxOf("one", idxOne, "two", idxTwo, "three",
				idxThree);

		PackIndex idxFour = indexOf(
				oidOffset("0000000000000000000000000000000000000001", 12),
				oidOffset("0000000000000000000000000000000000000007", 600),
				oidOffset("0000000000000000000000000000000000000015", 300));

		PackIndexMerger merger = PackIndexMerger.builder()
				.addMidx(midx.iterator()).addPack("four", idxFour).build();
		assertEquals(8, merger.getUniqueObjectCount());
		assertEquals(4, merger.getPackCount());
		assertFalse(merger.needsLargeOffsetsChunk());
		Iterator<MutableEntry> it = merger.bySha1Iterator();
		assertNextEntry(it, "0000000000000000000000000000000000000001", 3, 12);
		assertNextEntry(it, "0000000000000000000000000000000000000002", 1, 500);
		assertNextEntry(it, "0000000000000000000000000000000000000003", 1, 12);
		assertNextEntry(it, "0000000000000000000000000000000000000004", 2, 500);
		assertNextEntry(it, "0000000000000000000000000000000000000007", 2, 12);
		assertNextEntry(it, "0000000000000000000000000000000000000010", 0,
				1500);
		assertNextEntry(it, "0000000000000000000000000000000000000012", 2,
				1500);
		assertNextEntry(it, "0000000000000000000000000000000000000015", 3, 300);
		assertFalse(it.hasNext());
	}

	@Test
	public void merger_noIndexes() {
		PackIndexMerger merger = PackIndexMerger.builder().build();
		assertEquals(0, merger.getUniqueObjectCount());
		assertFalse(merger.needsLargeOffsetsChunk());
		assertTrue(merger.getPackNames().isEmpty());
		assertEquals(0, merger.getPackCount());
		assertFalse(merger.bySha1Iterator().hasNext());
	}

	@Test
	public void merger_emptyIndexes() {
		PackIndexMerger merger = createMergerFor("p1", indexOf(), "p2",
				indexOf());
		assertEquals(0, merger.getUniqueObjectCount());
		assertFalse(merger.needsLargeOffsetsChunk());
		assertEquals(2, merger.getPackNames().size());
		assertEquals(2, merger.getPackCount());
		assertFalse(merger.bySha1Iterator().hasNext());
	}

	@Test
	public void bySha1Iterator_largeOffsets_needsChunk() {
		PackIndex idx1 = indexOf(
				oidOffset("0000000000000000000000000000000000000002", 1L << 32),
				oidOffset("0000000000000000000000000000000000000004", 12));
		PackIndex idx2 = indexOf(oidOffset(
				"0000000000000000000000000000000000000003", (1L << 31) + 10));
		PackIndexMerger merger = createMergerFor("p1", idx1, "p2", idx2);
		assertTrue(merger.needsLargeOffsetsChunk());
		assertEquals(2, merger.getOffsetsOver31BitsCount());
		assertEquals(3, merger.getUniqueObjectCount());
	}

	@Test
	public void bySha1Iterator_largeOffsets_noChunk() {
		// If no value is over 2^32-1, then we don't need large offset
		PackIndex idx1 = indexOf(
				oidOffset("0000000000000000000000000000000000000002",
						(1L << 31) + 15),
				oidOffset("0000000000000000000000000000000000000004", 12));
		PackIndex idx2 = indexOf(oidOffset(
				"0000000000000000000000000000000000000003", (1L << 31) + 10));
		PackIndexMerger merger = createMergerFor("p1", idx1, "p2", idx2);
		assertFalse(merger.needsLargeOffsetsChunk());
		assertEquals(2, merger.getOffsetsOver31BitsCount());
		assertEquals(3, merger.getUniqueObjectCount());
	}

	@Test
	public void getObjectsPerPack_noDuplicates() {
		PackIndex idxOne = indexOf(
				oidOffset("0000000000000000000000000000000000000001", 500),
				oidOffset("0000000000000000000000000000000000000005", 12),
				oidOffset("0000000000000000000000000000000000000010", 1500));
		PackIndex idxTwo = indexOf(
				oidOffset("0000000000000000000000000000000000000002", 501),
				oidOffset("0000000000000000000000000000000000000003", 13),
				oidOffset("0000000000000000000000000000000000000015", 1501));
		PackIndex idxThree = indexOf(
				oidOffset("0000000000000000000000000000000000000004", 502),
				oidOffset("0000000000000000000000000000000000000007", 14),
				oidOffset("0000000000000000000000000000000000000012", 1502));
		PackIndexMerger merger = createMergerFor("p1", idxOne, "p2", idxTwo,
				"p3", idxThree);
		assertArrayEquals(new int[] { 3, 3, 3 }, merger.getObjectsPerPack());
	}

	@Test
	public void getObjectsPerPack_differentIndexSizes() {
		PackIndex idxOne = indexOf(
				oidOffset("0000000000000000000000000000000000000010", 1500));
		PackIndex idxTwo = indexOf(
				oidOffset("0000000000000000000000000000000000000002", 500),
				oidOffset("0000000000000000000000000000000000000003", 12));
		PackIndex idxThree = indexOf(
				oidOffset("0000000000000000000000000000000000000004", 500),
				oidOffset("0000000000000000000000000000000000000007", 12),
				oidOffset("0000000000000000000000000000000000000012", 1500));
		PackIndexMerger merger = createMergerFor("p1", idxOne, "p2", idxTwo,
				"p3", idxThree);
		assertArrayEquals(new int[] { 1, 2, 3 }, merger.getObjectsPerPack());
	}

	@Test
	public void getObjectsPerPack_allDuplicates() {
		PackIndex idxOne = indexOf(
				oidOffset("0000000000000000000000000000000000000001", 500),
				oidOffset("0000000000000000000000000000000000000005", 12),
				oidOffset("0000000000000000000000000000000000000010", 1500));
		PackIndexMerger merger = createMergerFor("p1", idxOne, "p2", idxOne,
				"p3", idxOne);
		assertArrayEquals(new int[] { 3, 0, 0 }, merger.getObjectsPerPack());
	}

	@Test
	public void getObjectsPerPack_noIndexes() {
		PackIndexMerger merger = PackIndexMerger.builder().build();
		assertArrayEquals(new int[] {}, merger.getObjectsPerPack());
	}

	@Test
	public void getObjectsPerPack_emptyIndexes() {
		PackIndexMerger merger = createMergerFor("p1", indexOf(), "p2",
				indexOf());
		assertArrayEquals(new int[] { 0, 0 }, merger.getObjectsPerPack());
	}

	private static void assertNextEntry(Iterator<MutableEntry> it, String oid,
			int packId, long offset) {
		assertTrue(it.hasNext());
		MutableEntry e = it.next();
		assertEquals(oid, e.getObjectId().name());
		assertEquals(packId, e.getPackId());
		assertEquals(offset, e.getOffset());
	}

	private static IndexObject oidOffset(String oid, long offset) {
		return new IndexObject(oid, offset);
	}

	private static PackIndex indexOf(IndexObject... objs) {
		return FakeIndexFactory.indexOf(Arrays.asList(objs));
	}

	private static MultiPackIndex midxOf(String s1, PackIndex idx1, String s2,
			PackIndex idx2, String s3, PackIndex idx3) throws IOException {
		PackIndexMerger merger = createMergerFor(s1, idx1, s2, idx2, s3, idx3);
		MultiPackIndexWriter w = new MultiPackIndexWriter();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		w.write(NullProgressMonitor.INSTANCE, out, merger);

		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		return MultiPackIndexLoader.read(in);
	}

	private static PackIndexMerger createMergerFor(String s1, PackIndex pi1,
			String s2, PackIndex pi2) {
		return PackIndexMerger.builder().addPack(s1, pi1).addPack(s2, pi2)
				.build();
	}

	private static PackIndexMerger createMergerFor(String s1, PackIndex pi1,
			String s2, PackIndex pi2, String s3, PackIndex pi3) {
		return PackIndexMerger.builder().addPack(s1, pi1).addPack(s2, pi2)
				.addPack(s3, pi3).build();
	}

}
