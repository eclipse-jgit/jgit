/*
 * Copyright (C) 2025, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.COMPACT;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.GC;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.INSERT;
import static org.eclipse.jgit.internal.storage.pack.PackExt.MULTI_PACK_INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.PACK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.junit.Test;

public class MidxPackFilterTest {
	private static final DfsRepositoryDescription repoDesc = new DfsRepositoryDescription(
			"test");

	private static int timeCounter = 0;

	@Test
	public void useMidx_oneMidxCoversAll_onlyMidx() {
		DfsPackDescription gc = pack("aaaa", GC, PACK);
		DfsPackDescription compact = pack("cccc", COMPACT, PACK);
		DfsPackDescription compactTwo = pack("bbbb", COMPACT, PACK);
		DfsPackDescription midx = pack("midx", GC, MULTI_PACK_INDEX);
		midx.setCoveredPacks(List.of(gc, compact, compactTwo));

		List<DfsPackDescription> reorgPacks = MidxPackFilter
				.useMidx(List.of(gc, compact, compactTwo, midx));
		assertEquals(1, reorgPacks.size());
		assertEquals(midx, reorgPacks.get(0));
	}

	@Test
	public void useMidx_midxAndOneUncoveredPack_midxAndPack() {
		DfsPackDescription gc = pack("aaaa", GC, PACK);
		DfsPackDescription compact = pack("cccc", COMPACT, PACK);
		DfsPackDescription compactTwo = pack("bbbb", COMPACT, PACK);
		DfsPackDescription midx = pack("midx", GC, MULTI_PACK_INDEX);
		midx.setCoveredPacks(List.of(gc, compact, compactTwo));

		DfsPackDescription extra = pack("extra", COMPACT, PACK);

		List<DfsPackDescription> reorgPacks = MidxPackFilter
				.useMidx(List.of(gc, compact, compactTwo, midx, extra));
		assertEquals(2, reorgPacks.size());
		assertTrue(reorgPacks.contains(midx));
		assertTrue(reorgPacks.contains(extra));
	}

	@Test
	public void useMidx_noMidx_allPacks() {
		DfsPackDescription gc = pack("aaaa", GC, PACK);
		DfsPackDescription compact = pack("cccc", COMPACT, PACK);
		DfsPackDescription compactTwo = pack("bbbb", COMPACT, PACK);

		List<DfsPackDescription> reorgPacks = MidxPackFilter
				.useMidx(List.of(gc, compact, compactTwo));
		assertEquals(3, reorgPacks.size());
		assertTrue(reorgPacks.contains(gc));
		assertTrue(reorgPacks.contains(compact));
		assertTrue(reorgPacks.contains(compactTwo));
	}

	@Test
	public void useMidx_midxMissesOnePack_onlyPacks() {
		DfsPackDescription gc = pack("aaaa", GC, PACK);
		DfsPackDescription compact = pack("cccc", COMPACT, PACK);
		DfsPackDescription compactTwo = pack("bbbb", COMPACT, PACK);
		DfsPackDescription midx = pack("midx", GC, MULTI_PACK_INDEX);
		midx.setCoveredPacks(List.of(gc, compact, compactTwo));

		List<DfsPackDescription> reorgPacks = MidxPackFilter
				.useMidx(List.of(gc, compactTwo, midx)); // compact missing
		assertEquals(2, reorgPacks.size());
		assertTrue(reorgPacks.contains(gc));
		assertTrue(reorgPacks.contains(compactTwo));
	}

	@Test
	public void useMidx_nestedMidx_onlyTopMidx() {
		DfsPackDescription gc = pack("aaaa", GC, PACK);
		DfsPackDescription compact = pack("cccc", COMPACT, PACK);
		DfsPackDescription firstMidx = pack("midx1", GC, MULTI_PACK_INDEX);
		firstMidx.setCoveredPacks(List.of(gc, compact));

		DfsPackDescription compact2 = pack("dddd", COMPACT, PACK);
		DfsPackDescription compact3 = pack("eeee", COMPACT, PACK);
		DfsPackDescription topMidx = pack("midx2", GC, MULTI_PACK_INDEX);
		topMidx.setCoveredPacks(List.of(compact2, compact3));
		topMidx.setMultiPackIndexBase(firstMidx);

		List<DfsPackDescription> reorgPacks = MidxPackFilter.useMidx(
				List.of(gc, compact, firstMidx, compact2, compact3, topMidx));
		assertEquals(1, reorgPacks.size());
		assertTrue(reorgPacks.contains(topMidx));
	}

	@Test
	public void useMidx_nestedMidxAndOnePack_topMidxAndPack() {
		DfsPackDescription gc = pack("aaaa", GC, PACK);
		DfsPackDescription compact = pack("cccc", COMPACT, PACK);
		DfsPackDescription firstMidx = pack("midx1", GC, MULTI_PACK_INDEX);
		firstMidx.setCoveredPacks(List.of(gc, compact));

		DfsPackDescription compact2 = pack("dddd", COMPACT, PACK);
		DfsPackDescription compact3 = pack("eeee", COMPACT, PACK);
		DfsPackDescription topMidx = pack("midx2", GC, MULTI_PACK_INDEX);
		topMidx.setCoveredPacks(List.of(compact2, compact3));
		topMidx.setMultiPackIndexBase(firstMidx);

		DfsPackDescription uncovered = pack("uncovered", INSERT, PACK);

		List<DfsPackDescription> reorgPacks = MidxPackFilter.useMidx(List.of(gc,
				compact, firstMidx, compact2, compact3, topMidx, uncovered));
		assertEquals(2, reorgPacks.size());
		assertTrue(reorgPacks.contains(topMidx));
		assertTrue(reorgPacks.contains(uncovered));
	}

	@Test
	public void useMidx_unconnectedValidMidx_onlyTop() {

		DfsPackDescription gc = pack("aaaa", GC, PACK);
		DfsPackDescription compact = pack("cccc", COMPACT, PACK);
		DfsPackDescription firstMidx = pack("midx1", GC, MULTI_PACK_INDEX);
		firstMidx.setCoveredPacks(List.of(gc, compact));

		DfsPackDescription unconnectedValid = pack("randoMidx", GC,
				MULTI_PACK_INDEX);
		unconnectedValid.setCoveredPacks(List.of(gc));

		DfsPackDescription compact2 = pack("dddd", COMPACT, PACK);
		DfsPackDescription compact3 = pack("eeee", COMPACT, PACK);
		DfsPackDescription topMidx = pack("midx2", GC, MULTI_PACK_INDEX);
		topMidx.setCoveredPacks(List.of(compact2, compact3));
		topMidx.setMultiPackIndexBase(firstMidx);

		List<DfsPackDescription> reorgPacks = MidxPackFilter
				.useMidx(List.of(gc, compact, firstMidx, unconnectedValid,
						compact2, compact3, topMidx));
		assertEquals(1, reorgPacks.size());
		assertTrue(reorgPacks.contains(topMidx));
	}

	@Test
	public void useMidx_unconnectedInvalidMidx_onlyTop() {
		DfsPackDescription gc = pack("aaaa", GC, PACK);
		DfsPackDescription compact = pack("cccc", COMPACT, PACK);
		DfsPackDescription firstMidx = pack("midx1", GC, MULTI_PACK_INDEX);
		firstMidx.setCoveredPacks(List.of(gc, compact));

		DfsPackDescription notCommitted = pack("not-in-db", GC, PACK);
		DfsPackDescription unconnectedInvalid = pack("randoMidx", GC,
				MULTI_PACK_INDEX);
		unconnectedInvalid.setCoveredPacks(List.of(notCommitted));

		DfsPackDescription compact2 = pack("dddd", COMPACT, PACK);
		DfsPackDescription compact3 = pack("eeee", COMPACT, PACK);
		DfsPackDescription topMidx = pack("midx2", GC, MULTI_PACK_INDEX);
		topMidx.setCoveredPacks(List.of(compact2, compact3));
		topMidx.setMultiPackIndexBase(firstMidx);

		List<DfsPackDescription> reorgPacks = MidxPackFilter
				.useMidx(List.of(gc, compact, firstMidx, unconnectedInvalid,
						compact2, compact3, topMidx));
		assertEquals(1, reorgPacks.size());
		assertTrue(reorgPacks.contains(topMidx));
	}

	@Test
	public void useMidx_latestMidxInvalid_takeNext() {
		DfsPackDescription gc = pack("aaaa", GC, PACK);
		DfsPackDescription compact = pack("cccc", COMPACT, PACK);
		DfsPackDescription firstMidx = pack("midx1", GC, MULTI_PACK_INDEX);
		firstMidx.setCoveredPacks(List.of(gc, compact));

		DfsPackDescription compact2 = pack("dddd", COMPACT, PACK);
		DfsPackDescription compact3 = pack("eeee", COMPACT, PACK);
		DfsPackDescription topMidx = pack("midx2", GC, MULTI_PACK_INDEX);
		topMidx.setCoveredPacks(List.of(compact2, compact3));
		topMidx.setMultiPackIndexBase(firstMidx);

		DfsPackDescription notCommitted = pack("not-in-db", GC, PACK);
		DfsPackDescription unconnectedInvalid = pack("randoMidx", GC,
				MULTI_PACK_INDEX);
		unconnectedInvalid.setCoveredPacks(List.of(notCommitted));

		List<DfsPackDescription> reorgPacks = MidxPackFilter
				.useMidx(List.of(gc, compact, firstMidx, unconnectedInvalid,
						compact2, compact3, topMidx));
		assertEquals(1, reorgPacks.size());
		assertTrue(reorgPacks.contains(topMidx));
	}

	@Test
	public void skipMidx_oneMidxCoversAll_allPacks() {
		DfsPackDescription gc = pack("aaaa", GC, PACK);
		DfsPackDescription compact = pack("cccc", COMPACT, PACK);
		DfsPackDescription compactTwo = pack("bbbb", COMPACT, PACK);
		DfsPackDescription midx = pack("midx", GC, MULTI_PACK_INDEX);
		midx.setCoveredPacks(List.of(gc, compact, compactTwo));

		List<DfsPackDescription> reorgPacks = MidxPackFilter
				.skipMidxs(List.of(gc, compact, compactTwo, midx));
		assertEquals(3, reorgPacks.size());
		assertTrue(reorgPacks.contains(gc));
		assertTrue(reorgPacks.contains(compact));
		assertTrue(reorgPacks.contains(compactTwo));
	}

	@Test
	public void skipMidx_midxAndOneUncoveredPack_allPacks() {
		DfsPackDescription gc = pack("aaaa", GC, PACK);
		DfsPackDescription compact = pack("cccc", COMPACT, PACK);
		DfsPackDescription compactTwo = pack("bbbb", COMPACT, PACK);
		DfsPackDescription midx = pack("midx", GC, MULTI_PACK_INDEX);
		midx.setCoveredPacks(List.of(gc, compact, compactTwo));

		DfsPackDescription extra = pack("extra", COMPACT, PACK);

		List<DfsPackDescription> reorgPacks = MidxPackFilter
				.skipMidxs(List.of(gc, compact, compactTwo, midx, extra));
		assertEquals(4, reorgPacks.size());
		assertTrue(reorgPacks.contains(gc));
		assertTrue(reorgPacks.contains(compact));
		assertTrue(reorgPacks.contains(compactTwo));
		assertTrue(reorgPacks.contains(extra));
	}

	@Test
	public void skipMidx_noMidx_allPacks() {
		DfsPackDescription gc = pack("aaaa", GC, PACK);
		DfsPackDescription compact = pack("cccc", COMPACT, PACK);
		DfsPackDescription compactTwo = pack("bbbb", COMPACT, PACK);

		List<DfsPackDescription> reorgPacks = MidxPackFilter
				.skipMidxs(List.of(gc, compact, compactTwo));
		assertEquals(3, reorgPacks.size());
		assertTrue(reorgPacks.contains(gc));
		assertTrue(reorgPacks.contains(compact));
		assertTrue(reorgPacks.contains(compactTwo));
	}

	@Test
	public void skipMidx_nestedMidx_allPacks() {
		DfsPackDescription gc = pack("aaaa", GC, PACK);
		DfsPackDescription compact = pack("cccc", COMPACT, PACK);
		DfsPackDescription firstMidx = pack("midx1", GC, MULTI_PACK_INDEX);
		firstMidx.setCoveredPacks(List.of(gc, compact));

		DfsPackDescription compact2 = pack("dddd", COMPACT, PACK);
		DfsPackDescription compact3 = pack("eeee", COMPACT, PACK);
		DfsPackDescription topMidx = pack("midx2", GC, MULTI_PACK_INDEX);
		topMidx.setCoveredPacks(List.of(compact2, compact3));
		topMidx.setMultiPackIndexBase(firstMidx);

		List<DfsPackDescription> reorgPacks = MidxPackFilter.skipMidxs(
				List.of(gc, compact, firstMidx, compact2, compact3, topMidx));
		assertEquals(4, reorgPacks.size());
		assertTrue(reorgPacks.contains(gc));
		assertTrue(reorgPacks.contains(compact));
		assertTrue(reorgPacks.contains(compact2));
		assertTrue(reorgPacks.contains(compact3));
	}

	@Test
	public void skipMidx_nestedMidxAndOnePack_allPacks() {
		DfsPackDescription gc = pack("aaaa", GC, PACK);
		DfsPackDescription compact = pack("cccc", COMPACT, PACK);
		DfsPackDescription firstMidx = pack("midx1", GC, MULTI_PACK_INDEX);
		firstMidx.setCoveredPacks(List.of(gc, compact));

		DfsPackDescription compact2 = pack("dddd", COMPACT, PACK);
		DfsPackDescription compact3 = pack("eeee", COMPACT, PACK);
		DfsPackDescription topMidx = pack("midx2", GC, MULTI_PACK_INDEX);
		topMidx.setCoveredPacks(List.of(compact2, compact3));
		topMidx.setMultiPackIndexBase(firstMidx);

		DfsPackDescription uncovered = pack("uncovered", INSERT, PACK);

		List<DfsPackDescription> reorgPacks = MidxPackFilter
				.skipMidxs(List.of(gc, compact, firstMidx, compact2, compact3,
						topMidx, uncovered));
		assertEquals(5, reorgPacks.size());
		assertTrue(reorgPacks.contains(gc));
		assertTrue(reorgPacks.contains(compact));
		assertTrue(reorgPacks.contains(compact2));
		assertTrue(reorgPacks.contains(compact3));
		assertTrue(reorgPacks.contains(uncovered));
	}

	private static DfsPackDescription pack(String name,
			DfsObjDatabase.PackSource source, PackExt ext) {
		DfsPackDescription desc = new DfsPackDescription(repoDesc, name,
				source);
		desc.setLastModified(timeCounter++);
		desc.addFileExt(ext);
		return desc;
	}
}
