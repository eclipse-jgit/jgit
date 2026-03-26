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

import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.GC;
import static org.eclipse.jgit.internal.storage.pack.PackExt.MULTI_PACK_INDEX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class MidxPackListTest {

	PackPool packPool = new PackPool();

	@Test
	public void getAllPlainPacks_onlyPlain() {
		DfsPackFile a = packPool.pack("a");
		DfsPackFile b = packPool.pack("b");
		DfsPackFile c = packPool.pack("c");

		MidxPackList packList = MidxPackList.create(a, b, c);
		assertEquals(List.of(a, b, c), packList.getAllPlainPacks());
	}

	@Test
	public void getAllPlainPacks_onlyMidx() {
		DfsPackFile a = packPool.pack("a");
		DfsPackFile b = packPool.pack("b");
		DfsPackFile c = packPool.pack("c");
		DfsPackFileMidx midx = packPool.midx("midx", null, "a", "b", "c");

		MidxPackList packList = MidxPackList.create(midx);
		assertEquals(List.of(a, b, c), packList.getAllPlainPacks());
	}

	@Test
	public void getAllPlainPacks_midxPlusOne() {
		DfsPackFile a = packPool.pack("a");
		DfsPackFile b = packPool.pack("b");
		DfsPackFile c = packPool.pack("c");
		DfsPackFileMidx midx = packPool.midx("midx", null, "a", "b", "c");
		DfsPackFile d = packPool.pack("d");

		MidxPackList packList = MidxPackList.create(d, midx);
		assertEquals(List.of(d, a, b, c), packList.getAllPlainPacks());
	}

	@Test
	public void getAllPlainPacks_nestedMidx() {
		DfsPackFile a = packPool.pack("a");
		DfsPackFile b = packPool.pack("b");
		DfsPackFileMidx midxBase = packPool.midx("midxBase", null, "a", "b");
		DfsPackFile c = packPool.pack("c");
		DfsPackFile d = packPool.pack("d");
		DfsPackFileMidx midxMiddle = packPool.midx("midxMiddle", midxBase, "c",
				"d");
		DfsPackFile e = packPool.pack("e");
		DfsPackFile f = packPool.pack("f");
		DfsPackFileMidx midxTip = packPool.midx("midxTip", midxMiddle, "e",
				"f");

		MidxPackList packList = MidxPackList.create(midxTip);
		assertEquals(List.of(e, f, c, d, a, b), packList.getAllPlainPacks());
	}

	@Test
	public void getAllMidxPacks_onlyPlain() {
		DfsPackFile a = packPool.pack("a");
		DfsPackFile b = packPool.pack("b");
		DfsPackFile c = packPool.pack("c");

		MidxPackList packList = MidxPackList.create(a, b, c);
		assertEquals(0, packList.getAllMidxPacks().size());
	}

	@Test
	public void getAllMidxPacks_onlyMidx() {
		packPool.pack("a");
		packPool.pack("b");
		packPool.pack("c");
		DfsPackFileMidx midx = packPool.midx("midx", null, "a", "b", "c");

		MidxPackList packList = MidxPackList.create(midx);
		assertEquals(List.of(midx), packList.getAllMidxPacks());
	}

	@Test
	public void getAllMidxPacks_midxPlusOne() {
		packPool.pack("a");
		packPool.pack("b");
		packPool.pack("c");
		DfsPackFileMidx midx = packPool.midx("midx", null, "a", "b", "c");
		DfsPackFile d = packPool.pack("d");

		MidxPackList packList = MidxPackList.create(d, midx);
		assertEquals(List.of(midx), packList.getAllMidxPacks());
	}

	@Test
	public void getAllMidxPacks_nestedMidx() {
		packPool.pack("a");
		packPool.pack("b");
		DfsPackFileMidx midxBase = packPool.midx("midxBase", null, "a", "b");
		packPool.pack("c");
		packPool.pack("d");
		DfsPackFileMidx midxMiddle = packPool.midx("midxMiddle", midxBase, "c",
				"d");
		packPool.pack("e");
		packPool.pack("f");
		DfsPackFileMidx midxTip = packPool.midx("midxTip", midxMiddle, "e",
				"f");

		MidxPackList packList = MidxPackList.create(midxTip);
		assertEquals(List.of(midxTip, midxMiddle, midxBase),
				packList.getAllMidxPacks());
	}

	@Test
	public void findAllImpactedMidx_onlyPacks() {
		DfsPackFile a = packPool.pack("a");
		DfsPackFile b = packPool.pack("b");
		DfsPackFile c = packPool.pack("c");

		MidxPackList packList = MidxPackList.create(a, b, c);
		assertEquals(Set.of(), packList.findAllCoveringMidxs(a));
		assertEquals(Set.of(), packList.findAllCoveringMidxs(b));
		assertEquals(Set.of(), packList.findAllCoveringMidxs(c));
		assertEquals(Set.of(), packList.findAllCoveringMidxs(a, b));
	}

	@Test
	public void findAllImpactedMidx_onlyMidx() {
		DfsPackFile a = packPool.pack("a");
		DfsPackFile b = packPool.pack("b");
		DfsPackFile c = packPool.pack("c");
		DfsPackFileMidx midx = packPool.midx("midx", null, "a", "b", "c");

		MidxPackList packList = MidxPackList.create(midx);
		assertEquals(Set.of(midx), packList.findAllCoveringMidxs(a));
		assertEquals(Set.of(midx), packList.findAllCoveringMidxs(b));
		assertEquals(Set.of(midx), packList.findAllCoveringMidxs(c));
		assertEquals(Set.of(midx), packList.findAllCoveringMidxs(a, b));
	}

	@Test
	public void findAllImpactedMidx_midxPlusOne() {
		packPool.pack("a");
		DfsPackFile b = packPool.pack("b");
		DfsPackFile c = packPool.pack("c");
		DfsPackFileMidx midx = packPool.midx("midx", null, "a", "b", "c");
		DfsPackFile d = packPool.pack("d");

		MidxPackList packList = MidxPackList.create(d, midx);
		assertEquals("one non covered", Set.of(),
				packList.findAllCoveringMidxs(d));
		assertEquals("one covered", Set.of(midx),
				packList.findAllCoveringMidxs(b));
		assertEquals(
				"two, only one covered", Set.of(midx),
				packList.findAllCoveringMidxs(c, d));
	}

	@Test
	public void findAllImpactedMidxs_nestedMidx() {
		DfsPackFile a = packPool.pack("a");
		DfsPackFile b = packPool.pack("b");
		DfsPackFileMidx midxBase = packPool.midx("midxBase", null, "a", "b");
		DfsPackFile c = packPool.pack("c");
		DfsPackFile d = packPool.pack("d");
		DfsPackFileMidx midxMiddle = packPool.midx("midxMiddle", midxBase, "c",
				"d");
		DfsPackFile e = packPool.pack("e");
		DfsPackFile f = packPool.pack("f");
		DfsPackFileMidx midxTip = packPool.midx("midxTip", midxMiddle, "e",
				"f");

		MidxPackList packList = MidxPackList.create(midxTip);
		// Only tip
		assertEquals(Set.of(midxTip), packList.findAllCoveringMidxs(e));
		assertEquals(Set.of(midxTip), packList.findAllCoveringMidxs(f));
		assertEquals(Set.of(midxTip), packList.findAllCoveringMidxs(e, f));

		// Tip and middle
		assertEquals(Set.of(midxTip, midxMiddle),
				packList.findAllCoveringMidxs(c));
		assertEquals(Set.of(midxTip, midxMiddle),
				packList.findAllCoveringMidxs(d));
		assertEquals(Set.of(midxTip, midxMiddle),
				packList.findAllCoveringMidxs(c, d));
		assertEquals(Set.of(midxTip, midxMiddle),
				packList.findAllCoveringMidxs(e, d));

		// All three
		assertEquals(Set.of(midxTip, midxMiddle, midxBase),
				packList.findAllCoveringMidxs(a));
		assertEquals(Set.of(midxTip, midxMiddle, midxBase),
				packList.findAllCoveringMidxs(b));
		assertEquals(Set.of(midxTip, midxMiddle, midxBase),
				packList.findAllCoveringMidxs(a, b));
		assertEquals(Set.of(midxTip, midxMiddle, midxBase),
				packList.findAllCoveringMidxs(c, a));
		assertEquals(Set.of(midxTip, midxMiddle, midxBase),
				packList.findAllCoveringMidxs(f, c, b));
	}

	@Test
	public void getTopMidxPack_noMidx_null() {
		DfsPackFile a = packPool.pack("a");
		DfsPackFile b = packPool.pack("b");
		DfsPackFile c = packPool.pack("c");
		MidxPackList packList = MidxPackList.create(a, b, c);
		assertNull(packList.getTopMidxPack());
	}

	@Test
	public void getTopMidxPack_oneMidx_returned() {
		packPool.pack("a");
		packPool.pack("b");
		packPool.pack("c");
		DfsPackFileMidx midx = packPool.midx("midx", null, "a", "b", "c");

		MidxPackList packList = MidxPackList.create(midx);
		assertEquals(midx, packList.getTopMidxPack());
	}

	@Test
	public void getTopMidxPack_multipleMidx_mostRecent() {
		packPool.pack("a");
		packPool.pack("b");
		DfsPackFileMidx midxBase = packPool.midx("midxBase", null, "a", "b");
		packPool.pack("c");
		packPool.pack("d");
		DfsPackFileMidx midxMiddle = packPool.midx("midxMiddle", midxBase, "c",
				"d");
		packPool.pack("e");
		packPool.pack("f");
		DfsPackFileMidx midxTip = packPool.midx("midxTip", midxMiddle, "e",
				"f");

		MidxPackList packList = MidxPackList.create(midxTip);
		assertEquals(midxTip, packList.getTopMidxPack());
	}

	@Test
	public void getPlainPacksNotCoveredBy_null_all() {
		DfsPackFile a = packPool.pack("a");
		DfsPackFile b = packPool.pack("b");
		DfsPackFile c = packPool.pack("c");
		MidxPackList packList = MidxPackList.create(a, b, c);
		assertEquals(List.of(a, b, c),
				packList.getPlainPacksNotCoveredBy(null));
	}

	@Test
	public void getPlainPacksNotCoveredBy_midxCoversAll_nothing()
	{
		packPool.pack("a");
		packPool.pack("b");
		packPool.pack("c");
		DfsPackFileMidx midx = packPool.midx("midx", null, "a", "b", "c");

		MidxPackList packList = MidxPackList.create(midx);
		assertEquals(List.of(), packList.getPlainPacksNotCoveredBy(midx));
	}

	@Test
	public void getPlainPacksNotCoveredBy_midxMissesOne_one()
	{
		packPool.pack("a");
		packPool.pack("b");
		packPool.pack("c");
		DfsPackFileMidx midx = packPool.midx("midx", null, "a", "b", "c");
		DfsPackFile d = packPool.pack("d");
		MidxPackList packList = MidxPackList.create(d, midx);
		assertEquals(List.of(d), packList.getPlainPacksNotCoveredBy(midx));
	}

	@Test
	public void getPlainPacksNotCoveredBy_midxChain() {
		packPool.pack("a");
		packPool.pack("b");
		DfsPackFileMidx midxBase = packPool.midx("midxBase", null, "a", "b");
		DfsPackFile c = packPool.pack("c");
		DfsPackFile d = packPool.pack("d");
		DfsPackFileMidx midxMiddle = packPool.midx("midxMiddle", midxBase, "c",
				"d");
		DfsPackFile e = packPool.pack("e");
		DfsPackFile f = packPool.pack("f");
		DfsPackFileMidx midxTip = packPool.midx("midxTip", midxMiddle, "e",
				"f");

		MidxPackList packList = MidxPackList.create(midxTip);
		assertEquals(List.of(e, f, c, d),
				packList.getPlainPacksNotCoveredBy(midxBase));
		assertEquals(List.of(e, f),
				packList.getPlainPacksNotCoveredBy(midxMiddle));
		assertEquals(List.of(), packList.getPlainPacksNotCoveredBy(midxTip));
	}


	private static final class PackPool {
		private static final DfsRepositoryDescription repoDesc = new DfsRepositoryDescription(
				"midxpacklisttest");

		private final Map<String, DfsPackFile> knownPacks = new HashMap<>();

		DfsPackFile pack(String name) {
			DfsPackDescription dsc = new DfsPackDescription(repoDesc, name, GC);
			DfsPackFile p = new DfsPackFile(DfsBlockCache.getInstance(), dsc);
			knownPacks.put(name, p);
			return p;
		}

		DfsPackFileMidx midx(String name, DfsPackFileMidx base,
				String... coveredPacks) {
			DfsPackDescription dsc = new DfsPackDescription(repoDesc, name, GC);
			dsc.addFileExt(MULTI_PACK_INDEX);
			dsc.setCoveredPacks(Arrays.stream(coveredPacks).map(knownPacks::get)
					.map(DfsPackFile::getPackDescription).toList());
			if (base != null) {
				dsc.setMultiPackIndexBase(base.getPackDescription());
			}
			return DfsPackFileMidx.create(DfsBlockCache.getInstance(), dsc,
					knownPacks.values().stream().toList(), base);
		}
	}
}
