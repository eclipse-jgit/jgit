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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.junit.FakeIndexFactory;
import org.eclipse.jgit.junit.FakeIndexFactory.IndexObject;
import org.junit.Test;

public class PackIndexMergerTest {

	@Test
	public void rawIterator_noDuplicates() {
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
		PackIndexMerger merger = new PackIndexMerger(
				List.of("p1", "p2", "p3"),
				Map.of("p1", idxOne, "p2", idxTwo, "p3", idxThree));
		assertEquals(9, merger.getUniqueObjectCount());
		assertEquals(3, merger.getPackCount());
		assertFalse(merger.needsLargeOffsetsChunk());
		Iterator<PackIndexMerger.MidxMutableEntry> it = merger.rawIterator();
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
	public void rawIterator_noDuplicates_honorPackOrder() {
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
		PackIndexMerger merger = new PackIndexMerger(List.of("p3", "p2", "p1"),
				Map.of("p1", idxOne, "p2", idxTwo, "p3", idxThree));
		assertEquals(9, merger.getUniqueObjectCount());
		assertEquals(3, merger.getPackCount());
		assertFalse(merger.needsLargeOffsetsChunk());
		Iterator<PackIndexMerger.MidxMutableEntry> it = merger.rawIterator();
		assertNextEntry(it, "0000000000000000000000000000000000000001", 2, 500);
		assertNextEntry(it, "0000000000000000000000000000000000000002", 1, 501);
		assertNextEntry(it, "0000000000000000000000000000000000000003", 1, 13);
		assertNextEntry(it, "0000000000000000000000000000000000000004", 0, 502);
		assertNextEntry(it, "0000000000000000000000000000000000000005", 2, 12);
		assertNextEntry(it, "0000000000000000000000000000000000000007", 0, 14);
		assertNextEntry(it, "0000000000000000000000000000000000000010", 2,
				1500);
		assertNextEntry(it, "0000000000000000000000000000000000000012", 0,
				1502);
		assertNextEntry(it, "0000000000000000000000000000000000000015", 1,
				1501);
		assertFalse(it.hasNext());
	}

	@Test
	public void rawIterator_allDuplicates() {
		PackIndex idxOne = indexOf(
				oidOffset("0000000000000000000000000000000000000001", 500),
				oidOffset("0000000000000000000000000000000000000005", 12),
				oidOffset("0000000000000000000000000000000000000010", 1500));
		PackIndexMerger merger = new PackIndexMerger(
				List.of("p1", "p2", "p3"),
				Map.of("p1", idxOne, "p2", idxOne, "p3", idxOne));
		assertEquals(3, merger.getUniqueObjectCount());
		assertEquals(3, merger.getPackCount());
		assertFalse(merger.needsLargeOffsetsChunk());
		Iterator<PackIndexMerger.MidxMutableEntry> it = merger.rawIterator();
		assertNextEntry(it, "0000000000000000000000000000000000000001", 0, 500);
		assertNextEntry(it, "0000000000000000000000000000000000000001", 1, 500);
		assertNextEntry(it, "0000000000000000000000000000000000000001", 2, 500);
		assertNextEntry(it, "0000000000000000000000000000000000000005", 0, 12);
		assertNextEntry(it, "0000000000000000000000000000000000000005", 1, 12);
		assertNextEntry(it, "0000000000000000000000000000000000000005", 2, 12);
		assertNextEntry(it, "0000000000000000000000000000000000000010", 0,
				1500);
		assertNextEntry(it, "0000000000000000000000000000000000000010", 1,
				1500);
		assertNextEntry(it, "0000000000000000000000000000000000000010", 2,
				1500);
		assertFalse(it.hasNext());
	}

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
		PackIndexMerger merger = new PackIndexMerger(
				List.of("p1", "p2", "p3"),
				Map.of("p1", idxOne, "p2", idxTwo, "p3", idxThree));
		assertEquals(9, merger.getUniqueObjectCount());
		assertEquals(3, merger.getPackCount());
		assertFalse(merger.needsLargeOffsetsChunk());
		Iterator<PackIndexMerger.MidxMutableEntry> it = merger.bySha1Iterator();
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
	public void bySha1Iterator_allDuplicates() {
		PackIndex idxOne = indexOf(
				oidOffset("0000000000000000000000000000000000000001", 500),
				oidOffset("0000000000000000000000000000000000000005", 12),
				oidOffset("0000000000000000000000000000000000000010", 1500));
		PackIndexMerger merger = new PackIndexMerger(
				List.of("p1", "p2", "p3"),
				Map.of("p1", idxOne, "p2", idxOne, "p3", idxOne));
		assertEquals(3, merger.getUniqueObjectCount());
		assertEquals(3, merger.getPackCount());
		assertFalse(merger.needsLargeOffsetsChunk());
		Iterator<PackIndexMerger.MidxMutableEntry> it = merger.bySha1Iterator();
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
		PackIndexMerger merger = new PackIndexMerger(
				List.of("p1", "p2", "p3"),
				Map.of("p1", idxOne, "p2", idxTwo, "p3", idxThree));
		assertEquals(6, merger.getUniqueObjectCount());
		assertEquals(3, merger.getPackCount());
		assertFalse(merger.needsLargeOffsetsChunk());
		Iterator<PackIndexMerger.MidxMutableEntry> it = merger.bySha1Iterator();
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
	public void merger_noIndexes() {
		PackIndexMerger merger = new PackIndexMerger(List.of(), Map.of());
		assertEquals(0, merger.getUniqueObjectCount());
		assertFalse(merger.needsLargeOffsetsChunk());
		assertTrue(merger.getPackNames().isEmpty());
		assertEquals(0, merger.getPackCount());
		assertFalse(merger.bySha1Iterator().hasNext());
	}

	@Test
	public void merger_emptyIndexes() {
		PackIndexMerger merger = new PackIndexMerger(
				List.of("p1", "p2"),
				Map.of("p1", indexOf(), "p2", indexOf()));
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
		PackIndexMerger merger = new PackIndexMerger(
				List.of("p1", "p2"),
				Map.of("p1", idx1, "p2", idx2));
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
		PackIndexMerger merger = new PackIndexMerger(
				List.of("p1", "p2"),
				Map.of("p1", idx1, "p2", idx2));
		assertFalse(merger.needsLargeOffsetsChunk());
		assertEquals(2, merger.getOffsetsOver31BitsCount());
		assertEquals(3, merger.getUniqueObjectCount());
	}

	private static void assertNextEntry(
			Iterator<PackIndexMerger.MidxMutableEntry> it, String oid,
			int packId, long offset) {
		assertTrue(it.hasNext());
		PackIndexMerger.MidxMutableEntry e = it.next();
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
}
