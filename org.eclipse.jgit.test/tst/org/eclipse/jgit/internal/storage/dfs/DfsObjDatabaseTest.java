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
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.INSERT;
import static org.eclipse.jgit.internal.storage.pack.PackExt.MULTI_PACK_INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.PACK;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
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

	private static DfsPackDescription pack(String name,
			DfsObjDatabase.PackSource source, long timeMs, PackExt ext) {
		DfsPackDescription desc = new DfsPackDescription(repoDesc, name,
				source);
		desc.setLastModified(timeMs);
		desc.addFileExt(ext);
		return desc;
	}

	private static void assertPackList(DfsPackFile[] actual,
			DfsPackDescription... expected) {
		assertEquals(expected.length, actual.length);
		for (int i = 0; i < expected.length; i++) {
			assertEquals(expected[i], actual[i].getPackDescription());
		}
	}
}
