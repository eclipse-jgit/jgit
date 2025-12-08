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

import static java.util.Arrays.asList;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Deflater;

import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

public class MidxPackListTest {

	InMemoryRepository db;

	@Before
	public void setUp() {
		db = new InMemoryRepository(new DfsRepositoryDescription("test"));
		db.getObjectDatabase().setUseMultipackIndex(true);
	}

	@Test
	public void getAllPlainPacks_onlyPlain() throws IOException {
		setupThreePacks();
		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		MidxPackList packList = MidxPackList.create(packs);
		assertEquals(3, packList.getAllPlainPacks().size());
	}

	@Test
	public void getAllPlainPacks_onlyMidx() throws IOException {
		setupThreePacksAndMidx();

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(1, packs.length);
		MidxPackList packList = MidxPackList.create(packs);
		assertEquals(3, packList.getAllPlainPacks().size());
	}

	@Test
	public void getAllPlainPacks_midxPlusOne() throws IOException {
		setupThreePacksAndMidx();
		writePackWithRandomBlob(60);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(2, packs.length);
		MidxPackList packList = MidxPackList.create(packs);
		assertEquals(4, packList.getAllPlainPacks().size());
	}

	@Test
	public void getAllPlainPacks_nestedMidx() throws IOException {
		setupSixPacksThreeMidx();

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(1, packs.length);
		MidxPackList packList = MidxPackList.create(packs);
		assertEquals(6, packList.getAllPlainPacks().size());
	}

	@Test
	public void getAllMidxPacks_onlyPlain() throws IOException {
		setupThreePacks();

		MidxPackList packList = MidxPackList
				.create(db.getObjectDatabase().getPacks());
		assertEquals(0, packList.getAllMidxPacks().size());
	}

	@Test
	public void getAllMidxPacks_onlyMidx() throws IOException {
		setupThreePacksAndMidx();

		MidxPackList packList = MidxPackList
				.create(db.getObjectDatabase().getPacks());
		assertEquals(1, packList.getAllMidxPacks().size());
	}

	@Test
	public void getAllMidxPacks_midxPlusOne() throws IOException {
		setupThreePacksAndMidx();
		writePackWithRandomBlob(60);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(2, packs.length);
		MidxPackList packList = MidxPackList.create(packs);
		assertEquals(1, packList.getAllMidxPacks().size());
	}

	@Test
	public void getAllMidxPacks_nestedMidx() throws IOException {
		setupSixPacksThreeMidx();

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(1, packs.length);
		MidxPackList packList = MidxPackList.create(packs);
		assertEquals(3, packList.getAllMidxPacks().size());
	}

	@Test
	public void findAllImpactedMidx_onlyPacks() throws IOException {
		setupThreePacks();

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		MidxPackList packList = MidxPackList
				.create(db.getObjectDatabase().getPacks());
		assertEquals(0, packList.findAllCoveringMidxs(asList(packs)).size());
	}

	@Test
	public void findAllImpactedMidx_onlyMidx() throws IOException {
		setupThreePacksAndMidx();

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		MidxPackList packList = MidxPackList.create(packs);
		List<DfsPackFile> covered = ((DfsPackFileMidx) packs[0])
				.getAllCoveredPacks();
		assertEquals(1, packList.findAllCoveringMidxs(covered.get(0)).size());
		assertEquals(1, packList.findAllCoveringMidxs(covered.get(1)).size());
		assertEquals(1, packList.findAllCoveringMidxs(covered.get(2)).size());

		assertEquals("multiple packs covered", 1,
				packList.findAllCoveringMidxs(covered.subList(0, 2)).size());
	}

	@Test
	public void findAllImpactedMidx_midxPlusOne() throws IOException {
		setupThreePacksAndMidx();
		writePackWithRandomBlob(60);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(2, packs.length);

		DfsPackFile uncoveredPack = packs[0];
		List<DfsPackFile> coveredPacks = ((DfsPackFileMidx) packs[1])
				.getAllCoveredPacks();
		assertEquals(3, coveredPacks.size());

		MidxPackList packList = MidxPackList.create(packs);
		assertEquals("one non covered", 0,
				packList.findAllCoveringMidxs(uncoveredPack).size());
		assertEquals("one and covered", 1,
				packList.findAllCoveringMidxs(coveredPacks.get(1)).size());
		assertEquals(
				"two, only one covered", 1, packList
						.findAllCoveringMidxs(
								List.of(uncoveredPack, coveredPacks.get(2)))
						.size());
	}

	@Test
	public void findAllImpactedMidxs_nestedMidx() throws IOException {
		setupSixPacksThreeMidx();

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(1, packs.length);
		MidxPackList packList = MidxPackList.create(packs);
		List<DfsPackFile> coveredPacks = ((DfsPackFileMidx) packs[0])
				.getAllCoveredPacks();
		assertEquals(6, coveredPacks.size());

		assertEquals("one covered tip midx", 1,
				packList.findAllCoveringMidxs(coveredPacks.get(0)).size());
		assertEquals("one covered middle midx", 2,
				packList.findAllCoveringMidxs(coveredPacks.get(2)).size());
		assertEquals("one covered base midx", 3,
				packList.findAllCoveringMidxs(coveredPacks.get(4)).size());
		assertEquals(
				"multiple covered in chain", 3, packList
						.findAllCoveringMidxs(List.of(coveredPacks.get(1),
								coveredPacks.get(2), coveredPacks.get(5)))
						.size());
	}

	@Test
	public void getTopMidxPack_noMidx_null() throws IOException {
		setupThreePacks();
		MidxPackList packList = MidxPackList
				.create(db.getObjectDatabase().getPacks());
		assertNull(packList.getTopMidxPack());
	}

	@Test
	public void getTopMidxPack_oneMidx_returned() throws IOException {
		DfsPackFileMidx midx = setupThreePacksAndMidx();
		MidxPackList packList = MidxPackList
				.create(db.getObjectDatabase().getPacks());
		assertEquals(midx.getPackDescription(),
				packList.getTopMidxPack().getPackDescription());
	}

	@Test
	public void getTopMidxPack_multipleMidx_mostRecent() throws IOException {
		writePackWithRandomBlob(100);
		writePackWithRandomBlob(300);
		writePackWithRandomBlob(50);
		writePackWithRandomBlob(400);
		writePackWithRandomBlob(130);
		writePackWithRandomBlob(500);
		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(6, packs.length);

		// midx covers first two packs
		writeMultipackIndex(Arrays.copyOfRange(packs, 4, 6), null);

		// chain of midxs covering all
		DfsPackFileMidx midxBase = writeMultipackIndex(
				Arrays.copyOfRange(packs, 4, 6), null);
		DfsPackFileMidx midxMid = writeMultipackIndex(
				Arrays.copyOfRange(packs, 2, 4), midxBase);
		DfsPackFileMidx chainTwo = writeMultipackIndex(
				Arrays.copyOfRange(packs, 0, 2), midxMid);

		MidxPackList packList = MidxPackList
				.create(db.getObjectDatabase().getPacks());
		assertEquals(chainTwo.getPackDescription(),
				packList.getTopMidxPack().getPackDescription());
	}

	@Test
	public void getPlainPacksNotCoveredBy_null_all() throws IOException {
		setupThreePacks();
		MidxPackList packList = MidxPackList
				.create(db.getObjectDatabase().getPacks());
		assertEquals(3, packList.getPlainPacksNotCoveredBy(null).size());
	}

	@Test
	public void getPlainPacksNotCoveredBy_midxCoversAll_nothing()
			throws IOException {
		DfsPackFileMidx midx = setupThreePacksAndMidx();
		MidxPackList packList = MidxPackList
				.create(db.getObjectDatabase().getPacks());
		assertEquals(0, packList.getPlainPacksNotCoveredBy(midx).size());
	}

	@Test
	public void getPlainPacksNotCoveredBy_midxMissesOne_one()
			throws IOException {
		DfsPackFileMidx midx = setupThreePacksAndMidx();
		writePackWithBlob("getPlainPacksNotCovered_missingone"
				.getBytes(StandardCharsets.UTF_8));
		MidxPackList packList = MidxPackList
				.create(db.getObjectDatabase().getPacks());
		assertEquals(1, packList.getPlainPacksNotCoveredBy(midx).size());
	}

	@Test
	public void getPlainPacksNotCoveredBy_midxChain() throws IOException {
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
		DfsPackFileMidx midxTip = writeMultipackIndex(
				Arrays.copyOfRange(packs, 0, 2), midxMid);

		MidxPackList packList = MidxPackList
				.create(db.getObjectDatabase().getPacks());
		assertEquals(4, packList.getPlainPacksNotCoveredBy(midxBase).size());
		assertEquals(2, packList.getPlainPacksNotCoveredBy(midxMid).size());
		assertEquals(0, packList.getPlainPacksNotCoveredBy(midxTip).size());
	}

	private void setupThreePacks() throws IOException {
		writePackWithRandomBlob(100);
		writePackWithRandomBlob(300);
		writePackWithRandomBlob(50);
	}

	private DfsPackFileMidx setupThreePacksAndMidx() throws IOException {
		writePackWithRandomBlob(100);
		writePackWithRandomBlob(300);
		writePackWithRandomBlob(50);
		return writeMultipackIndex();
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

	private DfsPackFileMidx writeMultipackIndex() throws IOException {
		return writeMultipackIndex(db.getObjectDatabase().getPacks(), null);
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
