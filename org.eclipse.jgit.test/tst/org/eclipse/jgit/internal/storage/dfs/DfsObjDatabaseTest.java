/*
 * Copyright (C) 2025, Google LLC
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
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.GC_REST;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.GC_REST_PART;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.INSERT;
import static org.eclipse.jgit.internal.storage.pack.PackExt.MULTI_PACK_INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.PACK;
import static org.eclipse.jgit.internal.storage.pack.PackExt.REFTABLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.junit.Before;
import org.junit.Test;

public class DfsObjDatabaseTest {
	InMemoryRepository db;

	private static final DfsRepositoryDescription repoDesc = new DfsRepositoryDescription(
			"test");

	@Before
	public void setUp() {
		db = new InMemoryRepository(repoDesc);
	}

	@Test
	public void getPacks_allInMidx_midxEnabled_onlyMidx() throws IOException {
		db.getObjectDatabase().setUseMultipackIndex(true);

		DfsPackDescription gcPack = pack("aaaa", GC, 100, PACK);
		DfsPackDescription compactPack = pack("cccc", COMPACT, 101, PACK);
		DfsPackDescription compactTwoPack = pack("bbbb", COMPACT, 102, PACK);

		DfsPackDescription multiPackIndex = pack("xxxx", GC, 104,
				MULTI_PACK_INDEX);
		multiPackIndex
				.setCoveredPacks(List.of(gcPack, compactPack, compactTwoPack));

		db.getObjectDatabase().commitPack(
				List.of(gcPack, compactPack, compactTwoPack, multiPackIndex),
				Collections.emptyList());

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertPackList(packs, multiPackIndex);
	}

	@Test
	public void getPacks_allInMidx_midxDisabled_packsNoMidx()
			throws IOException {
		db.getObjectDatabase().setUseMultipackIndex(false);

		DfsPackDescription gcPack = pack("aaaa", GC, 100, PACK);
		DfsPackDescription compactPack = pack("cccc", COMPACT, 101, PACK);
		DfsPackDescription compactTwoPack = pack("bbbb", COMPACT, 102, PACK);

		DfsPackDescription multiPackIndex = pack("xxxx", GC, 104,
				MULTI_PACK_INDEX);
		multiPackIndex
				.setCoveredPacks(List.of(gcPack, compactPack, compactTwoPack));

		db.getObjectDatabase().commitPack(
				List.of(gcPack, compactPack, compactTwoPack, multiPackIndex),
				Collections.emptyList());

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertPackList(packs, compactTwoPack, compactPack, gcPack);
	}

    @Test
	public void getPacks_someInMidx_midxEnabled_midxPlusUncovered()
			throws IOException {
		db.getObjectDatabase().setUseMultipackIndex(true);

		DfsPackDescription gcPack = pack("aaaa", GC, 100, PACK);
        DfsPackDescription compactPack = pack("cccc", COMPACT, 101, PACK);
		DfsPackDescription insertPack = pack("bbbb", COMPACT, 102, PACK);

        DfsPackDescription multiPackIndex = pack("xxxx", GC, 104,
                MULTI_PACK_INDEX);
        multiPackIndex.setCoveredPacks(List.of(gcPack, compactPack, insertPack));
        db.getObjectDatabase().commitPack(
                List.of(gcPack, compactPack, insertPack, multiPackIndex),
                null);

		DfsPackDescription uncoveredPack = pack("dddd", COMPACT, 103, PACK);
		db.getObjectDatabase().commitPack(List.of(uncoveredPack), null);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertPackList(packs, uncoveredPack, multiPackIndex);
    }

	@Test
	public void getPacks_someInMidx_midxDisabled_packsNoMidx()
			throws IOException {
		db.getObjectDatabase().setUseMultipackIndex(false);

		DfsPackDescription gcPack = pack("aaaa", GC, 100, PACK);
		DfsPackDescription compactPack = pack("cccc", COMPACT, 101, PACK);
		DfsPackDescription insertPack = pack("bbbb", COMPACT, 102, PACK);

		DfsPackDescription multiPackIndex = pack("xxxx", GC, 104,
				MULTI_PACK_INDEX);
		multiPackIndex
				.setCoveredPacks(List.of(gcPack, compactPack, insertPack));
		db.getObjectDatabase().commitPack(
				List.of(gcPack, compactPack, insertPack, multiPackIndex), null);

		DfsPackDescription uncoveredPack = pack("dddd", COMPACT, 103, PACK);
		db.getObjectDatabase().commitPack(List.of(uncoveredPack), null);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertPackList(packs, uncoveredPack, insertPack, compactPack, gcPack);
	}

    @Test
	public void getPacks_midxChain_midxEnabled_topMidx() throws IOException {
		db.getObjectDatabase().setUseMultipackIndex(true);

		DfsPackDescription gcPack = pack("aaaa", GC, 100, PACK);
        DfsPackDescription compactPack = pack("cccc", COMPACT, 101, PACK);
		DfsPackDescription insertPack = pack("bbbb", COMPACT, 102, PACK);

        DfsPackDescription midxBase = pack("xxxx", GC, 104,
                MULTI_PACK_INDEX);
        midxBase.setCoveredPacks(List.of(gcPack, compactPack, insertPack));
        db.getObjectDatabase().commitPack(
                List.of(gcPack, compactPack, insertPack, midxBase),
                null);

        DfsPackDescription insert1 = pack("insert1", INSERT, 105, PACK);
        DfsPackDescription insert2 = pack("insert2", INSERT, 106, PACK);
        DfsPackDescription midxTip = pack("xxx2", GC, 107, MULTI_PACK_INDEX);
        midxTip.setCoveredPacks(List.of(insert1, insert2));
        midxTip.setMultiPackIndexBase(midxBase);
        db.getObjectDatabase().commitPack(List.of(insert1, insert2, midxTip), null);

        DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertPackList(packs, midxTip);
    }

	@Test
	public void getPacks_midxChain_midxDisabled_packsNoMidx()
			throws IOException {
		db.getObjectDatabase().setUseMultipackIndex(false);

		DfsPackDescription gcPack = pack("aaaa", GC, 100, PACK);
		DfsPackDescription compactPack = pack("cccc", COMPACT, 101, PACK);
		DfsPackDescription insertPack = pack("bbbb", COMPACT, 102, PACK);

		DfsPackDescription midxBase = pack("xxxx", GC, 104, MULTI_PACK_INDEX);
		midxBase.setCoveredPacks(List.of(gcPack, compactPack, insertPack));
		db.getObjectDatabase().commitPack(
				List.of(gcPack, compactPack, insertPack, midxBase), null);

		DfsPackDescription insert1 = pack("insert1", INSERT, 105, PACK);
		DfsPackDescription insert2 = pack("insert2", INSERT, 106, PACK);
		DfsPackDescription midxTip = pack("xxx2", GC, 107, MULTI_PACK_INDEX);
		midxTip.setCoveredPacks(List.of(insert1, insert2));
		midxTip.setMultiPackIndexBase(midxBase);
		db.getObjectDatabase().commitPack(List.of(insert1, insert2, midxTip),
				null);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertPackList(packs, insert2, insert1, insertPack, compactPack,
				gcPack);
	}

	@Test
	public void getPacks_midxAndGcRestMidx_midxEnabled_bothMidx()
			throws IOException {
		db.getObjectDatabase().setUseMultipackIndex(true);

		DfsPackDescription gcPack = pack("aaaa", GC, 100, PACK);
		DfsPackDescription compactPack = pack("cccc", COMPACT, 101, PACK);
		DfsPackDescription midxGc = midx("midxGc", GC, null, 104, gcPack,
				compactPack);

		DfsPackDescription gcRestPack = pack("xxxx", GC_REST, 90, PACK);
		DfsPackDescription gcRestPartPack = pack("xxxx_part", GC_REST_PART, 91,
				PACK);
		DfsPackDescription midxGcRest = midx("midxGcRest", GC_REST, null, 105,
				gcRestPack, gcRestPartPack);

		DfsPackDescription uncoveredPack = pack("dddd", COMPACT, 103, PACK);

		db.getObjectDatabase().commitPack(List.of(gcPack, compactPack, midxGc,
				gcRestPack, gcRestPartPack, midxGcRest, uncoveredPack), null);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertPackList(packs, uncoveredPack, midxGc, midxGcRest);

		assertTrue(packs[0] instanceof DfsPackFile);
		assertFalse(packs[0] instanceof DfsPackFileMidx);

		assertTrue(packs[1] instanceof DfsPackFileMidx);
		DfsPackFileMidx midxGcFile = (DfsPackFileMidx) packs[1];
		assertEquals(2, midxGcFile.getCoveredPacks().size());

		assertTrue(packs[2] instanceof DfsPackFileMidx);
		DfsPackFileMidx midxGcRestFile = (DfsPackFileMidx) packs[2];
		assertEquals(2, midxGcRestFile.getCoveredPacks().size());

		MidxPackList midxPackList = MidxPackList.create(packs);
		assertEquals(2, midxPackList.getAllMidxPacks().size());
		assertEquals(5, midxPackList.getAllPlainPacks().size());
	}

	@Test
	public void getPacks_midxAndGcRestMidx_midxDisabled_packsNoMidx()
			throws IOException {
		db.getObjectDatabase().setUseMultipackIndex(false);

		DfsPackDescription gcPack = pack("aaaa", GC, 100, PACK);
		DfsPackDescription compactPack = pack("cccc", COMPACT, 101, PACK);
		DfsPackDescription midxGc = midx("midxGc", GC, null, 104, gcPack,
				compactPack);

		DfsPackDescription gcRestPack = pack("xxxx", GC_REST, 90, PACK);
		DfsPackDescription gcRestPartPack = pack("xxxx_part", GC_REST_PART, 91,
				PACK);
		DfsPackDescription midxGcRest = midx("midxGcRest", GC_REST, null, 105,
				gcRestPack, gcRestPartPack);

		DfsPackDescription uncoveredPack = pack("dddd", COMPACT, 103, PACK);

		db.getObjectDatabase().commitPack(List.of(gcPack, compactPack, midxGc,
				gcRestPack, gcRestPartPack, midxGcRest, uncoveredPack), null);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertPackList(packs, uncoveredPack, compactPack, gcPack,
				gcRestPartPack, gcRestPack);
		for (DfsPackFile packFile : packs) {
			assertFalse(packFile instanceof DfsPackFileMidx);
		}
	}

	@Test
	public void getPacks_multipleMidxChains_bothNested_topMidxs()
			throws IOException {
		db.getObjectDatabase().setUseMultipackIndex(true);

		DfsPackDescription gcPack = pack("aaaa", GC, 100, PACK);
		DfsPackDescription compact1 = pack("cccc", COMPACT, 101, PACK);
		DfsPackDescription midxBase = midx("midxBase", GC, null, 104, gcPack,
				compact1);

		DfsPackDescription compact2 = pack("compact2", COMPACT, 105, PACK);
		DfsPackDescription midxTip = midx("midxTip", GC, midxBase, 106,
				compact2);

		DfsPackDescription gcRestPack = pack("xxxx", GC_REST, 90, PACK);
		DfsPackDescription midxGcRestBase = midx("midxGcRestBase", GC_REST,
				null, 92, gcRestPack);

		DfsPackDescription gcRestPartPack = pack("xxxx_part", GC_REST_PART, 93,
				PACK);
		DfsPackDescription midxGcRestTip = midx("midxGcRestTip", GC_REST,
				midxGcRestBase, 94, gcRestPartPack);

		DfsPackDescription uncoveredPack = pack("uncovered", COMPACT, 103,
				PACK);

		db.getObjectDatabase().commitPack(
				List.of(gcPack, compact1, midxBase, compact2, midxTip,
						gcRestPack, midxGcRestBase, gcRestPartPack,
						midxGcRestTip, uncoveredPack),
				null);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertPackList(packs, uncoveredPack, midxTip, midxGcRestTip);

		assertTrue(packs[1] instanceof DfsPackFileMidx);
		DfsPackFileMidx tipFile = (DfsPackFileMidx) packs[1];
		assertEquals(midxBase,
				tipFile.getMultipackIndexBase().getPackDescription());

		assertTrue(packs[2] instanceof DfsPackFileMidx);
		DfsPackFileMidx gcRestTipFile = (DfsPackFileMidx) packs[2];
		assertEquals(midxGcRestBase,
				gcRestTipFile.getMultipackIndexBase().getPackDescription());

		MidxPackList midxPackList = MidxPackList.create(packs);
		assertEquals(4, midxPackList.getAllMidxPacks().size());
		assertEquals(6, midxPackList.getAllPlainPacks().size());
	}

	@Test
	public void getReftables_multipleMidxChains() throws IOException {
		db.getObjectDatabase().setUseMultipackIndex(true);

		DfsPackDescription gcPack = pack("aaaa", GC, 100, PACK, REFTABLE);
		DfsPackDescription compactPack = pack("cccc", COMPACT, 101, PACK);
		DfsPackDescription midxGc = midx("midxGc", GC, null, 104, gcPack,
				compactPack);

		DfsPackDescription gcRestPack = pack("xxxx", GC_REST, 90, PACK,
				REFTABLE);
		DfsPackDescription gcRestPartPack = pack("xxxx_part", GC_REST_PART, 91,
				PACK);
		DfsPackDescription midxGcRest = midx("midxGcRest", GC_REST, null, 105,
				gcRestPack, gcRestPartPack);

		DfsPackDescription uncoveredPack = pack("dddd", COMPACT, 103, PACK,
				REFTABLE);

		db.getObjectDatabase().commitPack(List.of(gcPack, compactPack, midxGc,
				gcRestPack, gcRestPartPack, midxGcRest, uncoveredPack), null);

		DfsReftable[] reftables = db.getObjectDatabase().getReftables();
		assertReftableList(reftables, gcRestPack, gcPack, uncoveredPack);
	}

	@Test
	public void getReftables_multipleInsideMidx() throws IOException {
		db.getObjectDatabase().setUseMultipackIndex(true);

		DfsPackDescription gcPack = pack("aaaa", GC, 100, PACK, REFTABLE);
		DfsPackDescription compactPack = pack("cccc", COMPACT, 101, PACK,
				REFTABLE);
		DfsPackDescription compactTwoPack = pack("bbbb", COMPACT, 102, PACK);

		DfsPackDescription multiPackIndex = pack("xxxx", GC, 104,
				MULTI_PACK_INDEX);
		multiPackIndex
				.setCoveredPacks(List.of(gcPack, compactPack, compactTwoPack));

		db.getObjectDatabase().commitPack(
				List.of(gcPack, compactPack, compactTwoPack, multiPackIndex),
				Collections.emptyList());

		DfsReftable[] reftables = db.getObjectDatabase().getReftables();
		assertReftableList(reftables, gcPack, compactPack);
	}

	@Test
	public void getReftables_midxChain() throws IOException {
		db.getObjectDatabase().setUseMultipackIndex(true);

		DfsPackDescription gcPack = pack("aaaa", GC, 100, PACK, REFTABLE);
		DfsPackDescription compactPack = pack("cccc", COMPACT, 101, PACK,
				REFTABLE);
		DfsPackDescription insertPack = pack("bbbb", COMPACT, 102, PACK);

		DfsPackDescription midxBase = pack("xxxx", GC, 104, MULTI_PACK_INDEX);
		midxBase.setCoveredPacks(List.of(gcPack, compactPack, insertPack));
		db.getObjectDatabase().commitPack(
				List.of(gcPack, compactPack, insertPack, midxBase), null);

		DfsPackDescription insert1 = pack("insert1", INSERT, 105, PACK,
				REFTABLE);
		DfsPackDescription insert2 = pack("insert2", INSERT, 106, PACK);
		DfsPackDescription midxTip = pack("xxx2", GC, 107, MULTI_PACK_INDEX);
		midxTip.setCoveredPacks(List.of(insert1, insert2));
		midxTip.setMultiPackIndexBase(midxBase);
		db.getObjectDatabase().commitPack(List.of(insert1, insert2, midxTip),
				null);

		DfsReftable[] reftables = db.getObjectDatabase().getReftables();
		assertReftableList(reftables, gcPack, compactPack, insert1);
	}

	@Test
	public void getReftables_inAndOutOfMidx() throws IOException {
		db.getObjectDatabase().setUseMultipackIndex(true);

		DfsPackDescription gcPack = pack("aaaa", GC, 100, PACK, REFTABLE);
		DfsPackDescription compactPack = pack("cccc", COMPACT, 101, PACK);
		DfsPackDescription insertPack = pack("bbbb", COMPACT, 102, PACK);

		DfsPackDescription multiPackIndex = pack("xxxx", GC, 104,
				MULTI_PACK_INDEX);
		multiPackIndex
				.setCoveredPacks(List.of(gcPack, compactPack, insertPack));
		db.getObjectDatabase().commitPack(
				List.of(gcPack, compactPack, insertPack, multiPackIndex), null);

		DfsPackDescription uncoveredPack = pack("dddd", COMPACT, 103, PACK,
				REFTABLE);
		db.getObjectDatabase().commitPack(List.of(uncoveredPack), null);

		DfsReftable[] reftables = db.getObjectDatabase().getReftables();
		assertReftableList(reftables, gcPack, uncoveredPack);
	}

	@Test
	public void commitPack_deleteCoveredPack_deleteMidx() throws IOException {
		db.getObjectDatabase().setUseMultipackIndex(true);

		DfsPackDescription gcPack = pack("aaaa", GC, 100, PACK, REFTABLE);
		DfsPackDescription compactPack = pack("cccc", COMPACT, 101, PACK);
		DfsPackDescription insertPack = pack("bbbb", COMPACT, 102, PACK);

		DfsPackDescription multiPackIndex = pack("xxxx", GC, 104,
				MULTI_PACK_INDEX);
		multiPackIndex
				.setCoveredPacks(List.of(gcPack, compactPack, insertPack));
		db.getObjectDatabase().commitPack(
				List.of(gcPack, compactPack, insertPack, multiPackIndex), null);

		// Delete pack covered by midx
		db.getObjectDatabase().commitPack(List.of(), List.of(insertPack));
		assertFalse(
				db.getObjectDatabase().listPacks().contains(multiPackIndex));
	}

	@Test
	public void commitPack_replaceMidxChain_packsUndisturbed()
			throws IOException {
		db.getObjectDatabase().setUseMultipackIndex(true);

		DfsPackDescription gcPack = pack("aaaa", GC, 100, PACK, REFTABLE);
		DfsPackDescription compactPack = pack("cccc", COMPACT, 101, PACK);
		DfsPackDescription midx1 = midx("midx1", null, 102, gcPack,
				compactPack);

		DfsPackDescription p3 = pack("p3", COMPACT, 103, PACK);
		DfsPackDescription p4 = pack("p4", COMPACT, 104, PACK);
		DfsPackDescription midx2 = midx("midx2", midx1, 105, p3, p4);
		db.getObjectDatabase().commitPack(
				List.of(gcPack, compactPack, midx1, p3, p4, midx2), null);

		DfsPackDescription midxAll = midx("midxAll", null, 106, gcPack,
				compactPack, p3, p4);

		// Replace the midxs
		db.getObjectDatabase().commitPack(List.of(midxAll),
				List.of(midx1, midx2));

		MidxPackList midxPackList = MidxPackList
				.create(db.getObjectDatabase().getPacks());
		assertEquals(4, midxPackList.getAllPlainPacks().size());
		assertEquals(1, midxPackList.getAllMidxPacks().size());
		assertFalse(db.getObjectDatabase().listPacks().contains(midx1));
		assertFalse(db.getObjectDatabase().listPacks().contains(midx2));
		assertTrue(db.getObjectDatabase().listPacks().contains(midxAll));
	}

	private static DfsPackDescription pack(String name,
			DfsObjDatabase.PackSource source, long timeMs, PackExt... ext) {
		DfsPackDescription desc = new DfsPackDescription(repoDesc, name,
				source);
		desc.setLastModified(timeMs);
		for (PackExt packExt : ext) {
			desc.addFileExt(packExt);
		}
		return desc;
	}

	private static DfsPackDescription midx(String name, DfsPackDescription base,
			long timeMs, DfsPackDescription... covered) {
		return midx(name, GC, base, timeMs, covered);
	}

	private static DfsPackDescription midx(String name,
			DfsObjDatabase.PackSource source, DfsPackDescription base,
			long timeMs, DfsPackDescription... covered) {
		DfsPackDescription midx = pack(name, source, timeMs, MULTI_PACK_INDEX);
		midx.setCoveredPacks(Arrays.stream(covered).toList());
		if (base != null) {
			midx.setMultiPackIndexBase(base);
		}
		return midx;
	}

	private static void assertPackList(DfsPackFile[] actual,
			DfsPackDescription... expected) {
		assertEquals(expected.length, actual.length);
		for (int i = 0; i < expected.length; i++) {
			assertEquals(expected[i], actual[i].getPackDescription());
		}
	}

	private static void assertReftableList(DfsReftable[] actual,
			DfsPackDescription... expected) {
		assertEquals(expected.length, actual.length);
		for (int i = 0; i < expected.length; i++) {
			assertEquals(expected[i], actual[i].getPackDescription());
		}
	}
}
