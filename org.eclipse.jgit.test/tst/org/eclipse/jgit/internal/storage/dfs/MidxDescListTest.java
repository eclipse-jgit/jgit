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

import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.junit.Test;

public class MidxDescListTest {

	private static final DfsRepositoryDescription REPO_DESC = new DfsRepositoryDescription(
			"testrepo");

	@Test
	public void findAllImpactedMidx_onlyPacks() {
		DfsPackDescription gcPack = desc("aaa", GC, PACK);
		DfsPackDescription compactPack = desc("bbb", COMPACT, PACK);
		DfsPackDescription insertPack = desc("ccc", INSERT, PACK);
		List<DfsPackDescription> packs = List.of(gcPack, compactPack,
				insertPack);

		MidxDescList packList = MidxDescList.create(packs);
		assertEquals(0, packList.findAllCoveringMidxs(packs).size());
	}

	@Test
	public void findAllImpactedMidx_onlyMidx() {
		DfsPackDescription gcPack = desc("aaa", GC, PACK);
		DfsPackDescription compactPack = desc("bbb", COMPACT, PACK);
		DfsPackDescription insertPack = desc("ccc", INSERT, PACK);
		DfsPackDescription midxPack = midxDesc("midx", null, gcPack,
				compactPack, insertPack);
		List<DfsPackDescription> packs = List.of(gcPack, compactPack,
				insertPack, midxPack);

		MidxDescList packList = MidxDescList.create(packs);
		assertEquals(1, packList.findAllCoveringMidxs(gcPack).size());
		assertEquals(1, packList.findAllCoveringMidxs(compactPack).size());
		assertEquals(1, packList.findAllCoveringMidxs(insertPack).size());

		assertEquals("multiple packs covered", 1, packList
				.findAllCoveringMidxs(List.of(gcPack, compactPack)).size());
	}

	@Test
	public void findAllImpactedMidx_midxPlusOne() {
		DfsPackDescription gcPack = desc("aaa", GC, PACK);
		DfsPackDescription compactPack = desc("bbb", COMPACT, PACK);
		DfsPackDescription insertPack = desc("ccc", INSERT, PACK);
		DfsPackDescription midxPack = midxDesc("midx", null, gcPack,
				compactPack, insertPack);
		DfsPackDescription nonMidxInsertPack = desc("non-midx", INSERT, PACK);
		List<DfsPackDescription> packs = List.of(gcPack, compactPack,
				insertPack, midxPack, nonMidxInsertPack);

		MidxDescList packList = MidxDescList.create(packs);
		assertEquals(1, packList.findAllCoveringMidxs(gcPack).size());
		assertEquals(1, packList.findAllCoveringMidxs(compactPack).size());
		assertEquals(1, packList.findAllCoveringMidxs(insertPack).size());
		assertEquals(0,
				packList.findAllCoveringMidxs(nonMidxInsertPack).size());

		assertEquals("multiple packs covered", 1, packList
				.findAllCoveringMidxs(List.of(gcPack, compactPack)).size());
		assertEquals("some covered", 1, packList
				.findAllCoveringMidxs(List.of(insertPack, nonMidxInsertPack))
				.size());
	}

	@Test
	public void findAllImpactedMidxs_nestedMidx() {
		DfsPackDescription gcPack = desc("base-gc", GC, PACK);
		DfsPackDescription compactPack = desc("base-compact1", COMPACT, PACK);
		DfsPackDescription baseMidx = midxDesc("base-midx", null, gcPack,
				compactPack);

		DfsPackDescription middlePack = desc("middle-insert", INSERT, PACK);
		DfsPackDescription middlePack2 = desc("middle-insert2", INSERT, PACK);
		DfsPackDescription middleMidx = midxDesc("middle-midx", baseMidx,
				middlePack, middlePack2);

		DfsPackDescription tipPack = desc("compact2", COMPACT, PACK);
		DfsPackDescription tipPack2 = desc("compact3", COMPACT, PACK);
		DfsPackDescription tipMidx = midxDesc("tipMidx", middleMidx, tipPack,
				tipPack2);

		List<DfsPackDescription> packs = List.of(gcPack, compactPack, baseMidx,
				middlePack, middlePack2, middleMidx, tipPack, tipPack2,
				tipMidx);

		MidxDescList packList = MidxDescList.create(packs);
		assertEquals("one covered tip midx", 1,
				packList.findAllCoveringMidxs(tipPack).size());
		assertEquals("one covered middle midx", 2,
				packList.findAllCoveringMidxs(middlePack).size());
		assertEquals("one covered base midx", 3,
				packList.findAllCoveringMidxs(compactPack).size());
		assertEquals("multiple covered in chain", 3, packList
				.findAllCoveringMidxs(List.of(tipPack, middlePack, compactPack))
				.size());
		assertEquals("midx itself is covered", 1,
				packList.findAllCoveringMidxs(tipMidx).size());
		assertEquals("inner midx is covered", 2,
				packList.findAllCoveringMidxs(middleMidx).size());
	}

	private DfsPackDescription desc(String name,
			DfsObjDatabase.PackSource source, PackExt... exts) {
		DfsPackDescription desc = new DfsPackDescription(REPO_DESC, name,
				source);
		for (PackExt ext : exts) {
			desc.addFileExt(ext);
		}
		return desc;
	}

	private DfsPackDescription midxDesc(String name, DfsPackDescription base,
			DfsPackDescription... covered) {
		DfsPackDescription midx = desc(name, GC, MULTI_PACK_INDEX);
		midx.setCoveredPacks(Arrays.stream(covered).toList());
		midx.setMultiPackIndexBase(base);
		return midx;
	}

}
