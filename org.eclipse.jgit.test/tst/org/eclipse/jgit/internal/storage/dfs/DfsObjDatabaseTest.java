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
	public void getPackList_allInMidx() throws IOException {
		DfsPackDescription gcPack = pack("aaaa", GC, 100, PACK);
		DfsPackDescription compactPack = pack("cccc", COMPACT, 101, PACK);
		DfsPackDescription insertPack = pack("bbbb", INSERT, 102, PACK);

		DfsPackDescription multiPackIndex = pack("xxxx", GC, 104,
				MULTI_PACK_INDEX);
		multiPackIndex.setRequiredPacks(List.of("aaaa", "bbbb", "cccc"));

		db.getObjectDatabase().commitPack(
				List.of(gcPack, compactPack, insertPack, multiPackIndex),
				Collections.emptyList());

		DfsObjDatabase.PackList packList = db.getObjectDatabase().getPackList();
		assertEquals(1, packList.getVirtualPacks().length);
		assertEquals(3, packList.packs.length);
	}

	@Test
	public void getPackList_someInMidx() throws IOException {
		DfsPackDescription gcPack = pack("aaaa", GC, 100, PACK);
		DfsPackDescription compactPack = pack("cccc", COMPACT, 101, PACK);
		DfsPackDescription insertPack = pack("bbbb", INSERT, 102, PACK);

		DfsPackDescription multiPackIndex = pack("xxxx", GC, 104,
				MULTI_PACK_INDEX);
		multiPackIndex.setRequiredPacks(List.of("aaaa", "cccc"));

		db.getObjectDatabase().commitPack(
				List.of(gcPack, compactPack, insertPack, multiPackIndex),
				Collections.emptyList());

		DfsObjDatabase.PackList packList = db.getObjectDatabase().getPackList();
		assertEquals(2, packList.getVirtualPacks().length);
		assertEquals(3, packList.packs.length);
	}

	@Test
	public void getPackList_someInMidx_order() throws IOException {
		DfsPackDescription gcPack = pack("aaaa", GC, 100, PACK);
		DfsPackDescription compactPack = pack("cccc", COMPACT, 101, PACK);
		DfsPackDescription insertPack = pack("bbbb", INSERT, 102, PACK);

		DfsPackDescription multiPackIndex = pack("xxxx", GC, 104,
				MULTI_PACK_INDEX);
		multiPackIndex.setRequiredPacks(List.of("aaaa", "cccc"));

		db.getObjectDatabase().commitPack(
				List.of(gcPack, compactPack, insertPack, multiPackIndex),
				Collections.emptyList());

		DfsObjDatabase.PackList packList = db.getObjectDatabase().getPackList();
		assertEquals(2, packList.getVirtualPacks().length);
		// The insert first
		assertEquals(102, packList.getVirtualPacks()[0].getLastModified());
		// The multipack with the last modified
		assertEquals(101, packList.getVirtualPacks()[1].getLastModified());
		assertEquals(3, packList.packs.length);
	}

	private static DfsPackDescription pack(String name,
			DfsObjDatabase.PackSource source, long timeMs, PackExt ext) {
		DfsPackDescription desc = new DfsPackDescription(repoDesc, name,
				source);
		desc.setLastModified(timeMs);
		desc.addFileExt(ext);
		return desc;
	}
}
