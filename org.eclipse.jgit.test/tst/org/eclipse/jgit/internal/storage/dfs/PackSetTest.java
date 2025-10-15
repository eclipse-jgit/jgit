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

import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Deflater;

import static java.util.Arrays.asList;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class PackSetTest {
	InMemoryRepository db;

	@Before
	public void setUp() {
		db = new InMemoryRepository(new DfsRepositoryDescription("test"));
		db.getObjectDatabase().setUseMultipackIndex(true);
	}

	@Test
	public void packSet_equals() throws IOException {
		setupSixPacksThreeMidx();
		MidxPackList packList = MidxPackList
				.create(db.getObjectDatabase().getPacks());
		PackSet set1 = new PackSet(packList.getAllPlainPacks().subList(0, 3));
		PackSet set2 = new PackSet(packList.getAllPlainPacks().subList(0, 3));
		PackSet set3 = new PackSet(packList.getAllPlainPacks().subList(3, 6));

		assertEquals(set1, set2);
		assertEquals(set2, set1);
		assertNotEquals(set1, set3);
	}

	@Test
	public void packSet_contains() throws IOException {
		setupSixPacksThreeMidx();
		MidxPackList packList = MidxPackList
				.create(db.getObjectDatabase().getPacks());
		List<DfsPackFile> allPlainPacks = packList.getAllPlainPacks();
		PackSet set1 = new PackSet(allPlainPacks.subList(0, 3));

		assertTrue(set1.contains(allPlainPacks.get(0)));
		assertTrue(set1.contains(allPlainPacks.get(2)));
		assertFalse(set1.contains(allPlainPacks.get(3)));
	}

	@Test
	public void packSet_midx_equals() throws IOException {
		setupSixPacksThreeMidx();
		MidxPackList packList = MidxPackList
				.create(db.getObjectDatabase().getPacks());
		PackSet set1 = new PackSet(packList.getAllMidxPacks().subList(0, 2));
		PackSet set2 = new PackSet(packList.getAllMidxPacks().subList(0, 2));
		PackSet set3 = new PackSet(packList.getAllMidxPacks().subList(2, 3));

		assertEquals(set1, set2);
		assertEquals(set2, set1);
		assertNotEquals(set1, set3);
	}

	@Test
	public void packSet_midx_contains() throws IOException {
		setupSixPacksThreeMidx();
		MidxPackList packList = MidxPackList
				.create(db.getObjectDatabase().getPacks());
		List<DfsPackFileMidx> allMidxPacks = packList.getAllMidxPacks();
		PackSet set1 = new PackSet(allMidxPacks.subList(0, 2));

		assertTrue(set1.contains(allMidxPacks.get(0)));
		assertTrue(set1.contains(allMidxPacks.get(1)));
		assertFalse(set1.contains(allMidxPacks.get(2)));
	}

	private void setupSixPacksThreeMidx() throws IOException {
		writePackWithRandomBlob(100);
		writePackWithRandomBlob(300);
		writePackWithRandomBlob(50);
		writePackWithRandomBlob(400);
		writePackWithRandomBlob(130);
		writePackWithRandomBlob(500);
		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(6, packs.length);
		DfsPackFileMidx midxBase = writeMultipackIndex(
				Arrays.copyOfRange(packs, 4, 6), null);
		DfsPackFileMidx midxMid = writeMultipackIndex(
				Arrays.copyOfRange(packs, 2, 4), midxBase);
		writeMultipackIndex(Arrays.copyOfRange(packs, 0, 2), midxMid);
		assertEquals("only top midx", 1,
				db.getObjectDatabase().getPacks().length);
	}

	private void writeMultipackIndex() throws IOException {
		writeMultipackIndex(db.getObjectDatabase().getPacks(), null);
	}

	private DfsPackFileMidx writeMultipackIndex(DfsPackFile[] packs,
			DfsPackFileMidx base) throws IOException {
		List<DfsPackFile> packfiles = asList(packs);
		DfsPackDescription desc = DfsMidxWriter.writeMidx(
				NullProgressMonitor.INSTANCE, db.getObjectDatabase(), packfiles,
				base != null ? base.getPackDescription() : null);
		db.getObjectDatabase().commitPack(List.of(desc), null);
		return DfsPackFileMidx.create(DfsBlockCache.getInstance(), desc,
				packfiles, base);
	}

	private ObjectId writePackWithBlob(byte[] data) throws IOException {
		DfsInserter ins = (DfsInserter) db.newObjectInserter();
		ins.setCompressionLevel(Deflater.NO_COMPRESSION);
		ObjectId blobId = ins.insert(OBJ_BLOB, data);
		ins.flush();
		return blobId;
	}

	// Do not use the size twice into the same test (it gives the same blob!)
	private ObjectId writePackWithRandomBlob(int size) throws IOException {
		byte[] data = new TestRng(JGitTestUtil.getName()).nextBytes(size);
		return writePackWithBlob(data);
	}
}
