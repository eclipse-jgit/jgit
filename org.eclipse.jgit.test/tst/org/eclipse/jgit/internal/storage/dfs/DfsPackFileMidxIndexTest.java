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

import static org.eclipse.jgit.internal.storage.dfs.MidxTestUtils.writeMultipackIndex;
import static org.eclipse.jgit.internal.storage.dfs.MidxTestUtils.writePackWithBlobs;
import static org.eclipse.jgit.internal.storage.dfs.MidxTestUtils.writeSinglePackMidx;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.file.PackReverseIndex;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DfsPackFileMidxIndexTest {

	private static final ObjectId NOT_IN_PACK = ObjectId
			.fromString("3f306cb3fcd5116919fecad615524bd6e6ea4ba7");

	private static final List<String> BLOBS = List.of("blob one", "blob two",
			"blob three", "blob four", "blob five", "blob six");

	@Parameters(name = "{0}")
	public static Iterable<TestInput> data() throws IOException {
		return List.of(setupOneMidxOverOnePack(), setupOneMidxOverNPacks(),
				setupMidxChainEachOverNPacks(),
				setupMidxChainSingleAndNPacks());
	}

	private record TestInput(String testDesc, DfsRepository db,
			DfsPackFileMidx midx, ObjectId[] oids) {
		@Override
		public String toString() {
			return testDesc;
		}

	}

	private TestInput ti;

	public DfsPackFileMidxIndexTest(TestInput ti) {
		this.ti = ti;
	}

	@Test
	public void getPackIndex_getObjectCount() {
		try (DfsReader ctx = ti.db().getObjectDatabase().newReader()) {
			assertEquals(ti.oids().length,
					ti.midx().getPackIndex(ctx).getObjectCount());
		}
	}

	@Test
	public void getPackIndex_position_findPosition_getObjectId() {
		try (DfsReader ctx = ti.db.getObjectDatabase().newReader()) {
			PackIndex idx = ti.midx().getPackIndex(ctx);
			for (int i = 0; i < ti.oids().length; i++) {
				ObjectId expected = ti.oids()[i];
				int position = idx.findPosition(expected);
				assertNotEquals(-1, position);
				ObjectId actual = idx.getObjectId(position);
				assertEquals(expected, actual);
			}
			assertEquals(-1, idx.findPosition(NOT_IN_PACK));
		}
	}

	@Test
	public void getPackIndex_offset_findOffset_getOffset() {
		try (DfsReader ctx = ti.db.getObjectDatabase().newReader()) {
			PackIndex idx = ti.midx().getPackIndex(ctx);
			for (int i = 0; i < ti.oids().length; i++) {
				ObjectId oid = ti.oids()[i];
				int oidPosition = idx.findPosition(oid);

				long offsetById = idx.findOffset(oid);
				long offsetByPos = idx.getOffset(oidPosition);
				assertEquals(offsetById, offsetByPos);
			}
			assertEquals(-1, idx.findOffset(NOT_IN_PACK));
		}
	}

	@Test
	public void getPackIndex_objects_contains_hasObjects() {
		try (DfsReader ctx = ti.db.getObjectDatabase().newReader()) {
			PackIndex idx = ti.midx().getPackIndex(ctx);
			for (int i = 0; i < ti.oids().length; i++) {
				ObjectId oid = ti.oids()[i];
				assertTrue(idx.contains(oid));
				assertTrue(idx.hasObject(oid));
			}
			assertFalse(idx.contains(NOT_IN_PACK));
			assertFalse(idx.hasObject(NOT_IN_PACK));
		}
	}

	@Test
	public void getPackIndex_resolve() throws IOException {
		try (DfsReader ctx = ti.db.getObjectDatabase().newReader()) {
			PackIndex idx = ti.midx().getPackIndex(ctx);
			Set<ObjectId> matches = new HashSet<>();
			// Sha1 of "blob two" = ae4116e0972d85cd751b458fea94ca9eb84dd692
			idx.resolve(matches, AbbreviatedObjectId.fromString("ae411"), 100);
			assertEquals(1, matches.size());
		}
	}

	@Test
	public void getReverseIndex_findObject() throws IOException {
		try (DfsReader ctx = ti.db.getObjectDatabase().newReader()) {
			PackIndex idx = ti.midx().getPackIndex(ctx);
			PackReverseIndex ridx = ti.midx().getReverseIdx(ctx);
			for (ObjectId oid : ti.oids()) {
				long offset = idx.findOffset(oid);
				assertEquals(oid, ridx.findObject(offset));
			}
		}
	}

	@Test
	public void getReverseIndex_findObjectByPosition() throws IOException {
		try (DfsReader ctx = ti.db.getObjectDatabase().newReader()) {
			PackIndex idx = ti.midx().getPackIndex(ctx);
			ObjectId[] offsetOrder = ti.oids().clone();
			Arrays.sort(offsetOrder, Comparator.comparingLong(idx::findOffset));

			PackReverseIndex ridx = ti.midx().getReverseIdx(ctx);
			for (int i = 0; i < offsetOrder.length; i++) {
				assertEquals(offsetOrder[i], ridx.findObjectByPosition(i));
			}
		}
	}

	@Test
	public void getReverseIndex_findPosition() throws IOException {
		try (DfsReader ctx = ti.db.getObjectDatabase().newReader()) {
			PackIndex idx = ti.midx().getPackIndex(ctx);
			ObjectId[] offsetOrder = ti.oids().clone();
			Arrays.sort(offsetOrder, Comparator.comparingLong(idx::findOffset));

			PackReverseIndex ridx = ti.midx().getReverseIdx(ctx);
			for (int i = 0; i < offsetOrder.length; i++) {
				long offset = idx.findOffset(offsetOrder[i]);
				int position = ridx.findPosition(offset);
				assertEquals(i, position);
			}
		}
	}
	static TestInput setupOneMidxOverOnePack() throws IOException {
		InMemoryRepository db = new InMemoryRepository(
				new DfsRepositoryDescription("one_midx_one_pack"));
		ObjectId[] objectIds = writePackWithBlobs(db,
				BLOBS.toArray(String[]::new));
		DfsPackFileMidx midx1 = writeSinglePackMidx(db);
		return new TestInput("one midx - one pack", db, midx1, objectIds);
	}

	static TestInput setupOneMidxOverNPacks() throws IOException {
		InMemoryRepository db = new InMemoryRepository(
				new DfsRepositoryDescription("one_midx_n_packs"));

		ObjectId[] objectIds = BLOBS.stream().map(s -> {
			try {
				return MidxTestUtils.writePackWithBlob(db, s);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}).toArray(ObjectId[]::new);
		DfsPackFileMidx midx1 = writeMultipackIndex(db,
				db.getObjectDatabase().getPacks(), null);
		return new TestInput("one midx - n packs", db, midx1, objectIds);
	}

	static TestInput setupMidxChainEachOverNPacks() throws IOException {
		InMemoryRepository db = new InMemoryRepository(
				new DfsRepositoryDescription("two_midx_3_packs_each"));

		ObjectId[] objectIds = BLOBS.stream().map(s -> {
			try {
				return MidxTestUtils.writePackWithBlob(db, s);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}).toArray(ObjectId[]::new);
		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		// If the amount of blobs (i.e. packs), adjust the ranges covered by
		// midx.
		assertEquals(6, BLOBS.size());
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 3, 6), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 0, 3), midxBase);
		return new TestInput("two midx - 3 packs each", db, midxTip, objectIds);
	}

	static TestInput setupMidxChainSingleAndNPacks() throws IOException {
		InMemoryRepository db = new InMemoryRepository(
				new DfsRepositoryDescription("two_midx_3_packs_each"));

		ObjectId[] objectIds = BLOBS.stream().map(s -> {
			try {
				return MidxTestUtils.writePackWithBlob(db, s);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}).toArray(ObjectId[]::new);
		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		// If the amount of blobs (i.e. packs), adjust the ranges covered by
		// midx.
		assertEquals(6, BLOBS.size());
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 1, 6), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 0, 1), midxBase);
		return new TestInput("two midx - 1 pack, 5 packs", db, midxTip,
				objectIds);
	}
}
