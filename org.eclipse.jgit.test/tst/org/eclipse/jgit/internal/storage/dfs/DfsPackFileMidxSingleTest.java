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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource.GC;
import static org.eclipse.jgit.internal.storage.pack.PackExt.PACK;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.dfs.DfsPackFileMidx.DfsPackOffset;
import org.eclipse.jgit.internal.storage.dfs.DfsPackFileMidxSingle.SingleVOffsetCalculator;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex.PackOffset;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.junit.Before;
import org.junit.Test;

public class DfsPackFileMidxSingleTest {

	private static final ObjectId NOT_IN_PACK = ObjectId
			.fromString("3f306cb3fcd5116919fecad615524bd6e6ea4ba7");

	InMemoryRepository db;

	@Before
	public void setUp() {
		db = new InMemoryRepository(new DfsRepositoryDescription("test"));
	}

	@Test
	public void findIdxPosition() throws IOException {
		ObjectId[] oids = MidxTestUtils.writePackWithBlobs(db, "something",
				"something else", "and more");
		// oids = [a4..., 33..., 64...]
		DfsPackFileMidx midx = MidxTestUtils.writeSinglePackMidx(db);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertEquals(2, midx.findIdxPosition(ctx, oids[0]));
			assertEquals(0, midx.findIdxPosition(ctx, oids[1]));
			assertEquals(1, midx.findIdxPosition(ctx, oids[2]));
			assertEquals(-1, midx.findIdxPosition(ctx, NOT_IN_PACK));
		}
	}

	@Test
	public void findIdxPosition_withBase() throws IOException {
		ObjectId o1 = MidxTestUtils.writePackWithBlob(db, "o1"); // 38
		ObjectId o2 = MidxTestUtils.writePackWithBlob(db, "o2"); // 4a
		ObjectId o3 = MidxTestUtils.writePackWithBlob(db, "o3"); // 45
		ObjectId o4 = MidxTestUtils.writePackWithBlob(db, "o4"); // 4b
		ObjectId o5 = MidxTestUtils.writePackWithBlob(db, "o5"); // 68
		ObjectId o6 = MidxTestUtils.writePackWithBlob(db, "o6"); // 4d
		DfsPackFile[] packs = db.getObjectDatabase().getPacks();

		// Packs are in reverse insertion order
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 3, 6), null);
		DfsPackFileMidx midxMid = MidxTestUtils.writeSinglePackMidx(db,
				packs[2], midxBase);
		DfsPackFileMidx midxTip = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 0, 2), midxMid);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertEquals(0, midxTip.findIdxPosition(ctx, o1));
			assertEquals(1, midxTip.findIdxPosition(ctx, o3));
			assertEquals(2, midxTip.findIdxPosition(ctx, o2));
			assertEquals(3, midxTip.findIdxPosition(ctx, o4));
			assertEquals(4, midxTip.findIdxPosition(ctx, o6));
			assertEquals(5, midxTip.findIdxPosition(ctx, o5));
		}
	}

	@Test
	public void getObjectAt() throws IOException {
		ObjectId[] oids = MidxTestUtils.writePackWithBlobs(db, "something",
				"something else", "and more");
		// oids = [a4..., 33..., 64...]
		DfsPackFileMidx midx = MidxTestUtils.writeSinglePackMidx(db);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertEquals(oids[0], midx.getObjectAt(ctx, 2));
			assertEquals(oids[1], midx.getObjectAt(ctx, 0));
			assertEquals(oids[2], midx.getObjectAt(ctx, 1));
		}
	}

	@Test
	public void getObjectAt_withBase() throws IOException {
		ObjectId o1 = MidxTestUtils.writePackWithBlob(db, "o1");
		ObjectId o2 = MidxTestUtils.writePackWithBlob(db, "o2");
		ObjectId o3 = MidxTestUtils.writePackWithBlob(db, "o3");
		ObjectId o4 = MidxTestUtils.writePackWithBlob(db, "o4");
		ObjectId o5 = MidxTestUtils.writePackWithBlob(db, "o5");
		ObjectId o6 = MidxTestUtils.writePackWithBlob(db, "o6");
		DfsPackFile[] packs = db.getObjectDatabase().getPacks();

		// Packs are in reverse insertion order
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 3, 6), null);
		DfsPackFileMidx midxMid = MidxTestUtils.writeSinglePackMidx(db,
				packs[2], midxBase);
		DfsPackFileMidx midxTip = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 0, 2), midxMid);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertEquals(o1, midxTip.getObjectAt(ctx, 0));
			assertEquals(o3, midxTip.getObjectAt(ctx, 1));
			assertEquals(o2, midxTip.getObjectAt(ctx, 2));
			assertEquals(o4, midxTip.getObjectAt(ctx, 3));
			// In sha1 order
			assertEquals(o6, midxTip.getObjectAt(ctx, 4));
			assertEquals(o5, midxTip.getObjectAt(ctx, 5));
		}
	}

	@Test
	public void hasObject() throws IOException {
		ObjectId[] oids = MidxTestUtils.writePackWithBlobs(db, "aaaa", "bbbb",
				"cccc");
		DfsPackFile midx = MidxTestUtils.writeSinglePackMidx(db);

		// DfsPackFile midx = readDfsPackFileMidx();
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertTrue(midx.hasObject(ctx, oids[0]));
			assertTrue(midx.hasObject(ctx, oids[1]));
			assertTrue(midx.hasObject(ctx, oids[2]));
			assertFalse(midx.hasObject(ctx, NOT_IN_PACK));
		}
	}

	@Test
	public void hasObject_withBase() throws IOException {
		ObjectId o1 = writePackWithRandomBlob(100);
		ObjectId o2 = writePackWithRandomBlob(200);
		ObjectId o3 = writePackWithRandomBlob(150);
		ObjectId o4 = writePackWithRandomBlob(400);
		ObjectId o5 = writePackWithRandomBlob(500);
		ObjectId o6 = writePackWithRandomBlob(600);
		DfsPackFile[] packs = db.getObjectDatabase().getPacks();

		// Packs are in reverse insertion order
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 1, 6), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeSinglePackMidx(db,
				packs[0], midxBase);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertTrue(midxBase.hasObject(ctx, o1));
			assertTrue(midxBase.hasObject(ctx, o2));
			assertTrue(midxBase.hasObject(ctx, o3));
			assertTrue(midxBase.hasObject(ctx, o4));
			assertTrue(midxBase.hasObject(ctx, o5));
			// This is not in base
			assertFalse(midxBase.hasObject(ctx, o6));

			// Top midx has all objects
			assertTrue(midxTip.hasObject(ctx, o1));
			assertTrue(midxTip.hasObject(ctx, o2));
			assertTrue(midxTip.hasObject(ctx, o3));
			assertTrue(midxTip.hasObject(ctx, o4));
			assertTrue(midxTip.hasObject(ctx, o5));
			assertTrue(midxTip.hasObject(ctx, o6));

			assertFalse(midxTip.hasObject(ctx, NOT_IN_PACK));
		}
	}

	@Test
	public void get() throws IOException {
		byte[] contentOne = "ONE".getBytes(UTF_8);
		byte[] contentTwo = "TWO".getBytes(UTF_8);
		byte[] contentThree = "THREE".getBytes(UTF_8);

		ObjectId[] oids = MidxTestUtils.writePackWithBlobs(db, "ONE", "TWO",
				"THREE");
		DfsPackFile midx = MidxTestUtils.writeSinglePackMidx(db);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			ObjectLoader objectLoader = midx.get(ctx, oids[0]);
			assertArrayEquals(contentOne, safeGetBytes(objectLoader));
			objectLoader = midx.get(ctx, oids[1]);
			assertArrayEquals(contentTwo, safeGetBytes(objectLoader));
			objectLoader = midx.get(ctx, oids[2]);
			assertArrayEquals(contentThree, safeGetBytes(objectLoader));
			assertNull(midx.get(ctx, NOT_IN_PACK));
		}
	}

	@Test
	public void get_withBase() throws IOException {
		byte[] contentOne = "ONE".getBytes(UTF_8);
		byte[] contentTwo = "TWO".getBytes(UTF_8);
		byte[] contentThree = "THREE".getBytes(UTF_8);
		byte[] contentFour = "FOUR".getBytes(UTF_8);
		byte[] contentFive = "FIVE".getBytes(UTF_8);
		byte[] contentSix = "SIX".getBytes(UTF_8);

		ObjectId o1 = writePackWithBlob(contentOne);
		ObjectId o2 = writePackWithBlob(contentTwo);
		ObjectId o3 = writePackWithBlob(contentThree);
		ObjectId o4 = writePackWithBlob(contentFour);
		ObjectId o5 = writePackWithBlob(contentFive);
		ObjectId o6 = writePackWithBlob(contentSix);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		// Packs are in reverse insertion order
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 1, 6), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeSinglePackMidx(db,
				packs[0], midxBase);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			ObjectLoader objectLoader = midxTip.get(ctx, o1);
			assertArrayEquals(contentOne, safeGetBytes(objectLoader));
			objectLoader = midxTip.get(ctx, o2);
			assertArrayEquals(contentTwo, safeGetBytes(objectLoader));
			objectLoader = midxTip.get(ctx, o3);
			assertArrayEquals(contentThree, safeGetBytes(objectLoader));
			objectLoader = midxTip.get(ctx, o4);
			assertArrayEquals(contentFour, safeGetBytes(objectLoader));
			objectLoader = midxTip.get(ctx, o5);
			assertArrayEquals(contentFive, safeGetBytes(objectLoader));
			objectLoader = midxTip.get(ctx, o6);
			assertArrayEquals(contentSix, safeGetBytes(objectLoader));

			assertNull(midxTip.get(ctx, NOT_IN_PACK));
		}
	}

	@Test
	public void load() throws IOException {
		byte[] contentOne = "ONE".getBytes(UTF_8);
		byte[] contentTwo = "TWO".getBytes(UTF_8);
		byte[] contentThree = "THREE".getBytes(UTF_8);

		ObjectId[] oids = MidxTestUtils.writePackWithBlobs(db, "ONE", "TWO",
				"THREE");
		DfsPackFile midx = MidxTestUtils.writeSinglePackMidx(db);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			ObjectLoader objectLoader = midx.load(ctx,
					midx.findOffset(ctx, oids[0]));
			assertArrayEquals(contentOne, safeGetBytes(objectLoader));
			objectLoader = midx.load(ctx, midx.findOffset(ctx, oids[1]));
			assertArrayEquals(contentTwo, safeGetBytes(objectLoader));
			objectLoader = midx.load(ctx, midx.findOffset(ctx, oids[2]));
			assertArrayEquals(contentThree, safeGetBytes(objectLoader));

			assertThrows(IllegalArgumentException.class,
					() -> midx.load(ctx, 500));
			assertNull(midx.load(ctx, -1));
		}
	}

	@Test
	public void load_withBase() throws IOException {
		byte[] contentOne = "ONE".getBytes(UTF_8);
		byte[] contentTwo = "TWO".getBytes(UTF_8);
		byte[] contentThree = "THREE".getBytes(UTF_8);
		byte[] contentFour = "FOUR".getBytes(UTF_8);
		byte[] contentFive = "FIVE".getBytes(UTF_8);
		byte[] contentSix = "SIX".getBytes(UTF_8);

		ObjectId o1 = writePackWithBlob(contentOne);
		ObjectId o2 = writePackWithBlob(contentTwo);
		ObjectId o3 = writePackWithBlob(contentThree);
		ObjectId o4 = writePackWithBlob(contentFour);
		ObjectId o5 = writePackWithBlob(contentFive);
		ObjectId o6 = writePackWithBlob(contentSix);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		// Packs are in reverse insertion order
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 1, 6), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeSinglePackMidx(db,
				packs[0], midxBase);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			ObjectLoader objectLoader = midxTip.load(ctx,
					midxTip.findOffset(ctx, o1));
			assertArrayEquals(contentOne, safeGetBytes(objectLoader));
			objectLoader = midxTip.load(ctx, midxTip.findOffset(ctx, o2));
			assertArrayEquals(contentTwo, safeGetBytes(objectLoader));
			objectLoader = midxTip.load(ctx, midxTip.findOffset(ctx, o3));
			assertArrayEquals(contentThree, safeGetBytes(objectLoader));
			objectLoader = midxTip.load(ctx, midxTip.findOffset(ctx, o4));
			assertArrayEquals(contentFour, safeGetBytes(objectLoader));
			objectLoader = midxTip.load(ctx, midxTip.findOffset(ctx, o5));
			assertArrayEquals(contentFive, safeGetBytes(objectLoader));
			objectLoader = midxTip.load(ctx, midxTip.findOffset(ctx, o6));
			assertArrayEquals(contentSix, safeGetBytes(objectLoader));

			assertNull(midxTip.load(ctx, midxTip.findOffset(ctx, NOT_IN_PACK)));
		}
	}

	@Test
	public void findOffset() throws IOException {
		ObjectId[] oids = MidxTestUtils.writePackWithBlobs(db, "ONE", "TWO",
				"THREE");
		DfsPackFileMidx midx = MidxTestUtils.writeSinglePackMidx(db);
		DfsPackFile realPack = findPack(oids[0]);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertEquals(realPack.findOffset(ctx, oids[0]),
					midx.findOffset(ctx, oids[0]));
			assertEquals(realPack.findOffset(ctx, oids[1]),
					midx.findOffset(ctx, oids[1]));
			assertEquals(realPack.findOffset(ctx, oids[2]),
					midx.findOffset(ctx, oids[2]));

			long posNon = midx.findOffset(ctx, NOT_IN_PACK);
			assertEquals(-1, posNon);
		}
	}

	@Test
	public void findOffset_withBase() throws IOException {
		byte[] contentOne = "ONE".getBytes(UTF_8);
		byte[] contentTwo = "TWO".getBytes(UTF_8);
		byte[] contentThree = "THREE".getBytes(UTF_8);
		byte[] contentFour = "FOUR".getBytes(UTF_8);
		byte[] contentFive = "FIVE".getBytes(UTF_8);
		byte[] contentSix = "SIX".getBytes(UTF_8);

		ObjectId o1 = writePackWithBlob(contentOne);
		ObjectId o2 = writePackWithBlob(contentTwo);
		ObjectId o3 = writePackWithBlob(contentThree);
		ObjectId o4 = writePackWithBlob(contentFour);
		ObjectId o5 = writePackWithBlob(contentFive);
		ObjectId o6 = writePackWithBlob(contentSix);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		// Packs are in reverse insertion order
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 1, 6), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeSinglePackMidx(db,
				packs[0], midxBase);
		DfsPackFile coveredPack = findPack(o6);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertEquals(coveredPack.findOffset(ctx, o6) + midxBase.length,
					midxTip.findOffset(ctx, o6));
			assertEquals(findPack(o5).findOffset(ctx, o5),
					midxTip.findOffset(ctx, o5));

			// WE just check the offsets are in incremental order.
			// midx orders first by pack, and we passed the most recent first,
			// so in midx-offset order, o6 > o1 > o2 > o3 > o4 > o5
			assertTrue(
					midxTip.findOffset(ctx, o4) > midxTip.findOffset(ctx, o5));
			assertTrue(
					midxTip.findOffset(ctx, o3) > midxTip.findOffset(ctx, o4));

			assertTrue(
					midxTip.findOffset(ctx, o2) > midxTip.findOffset(ctx, o3));

			assertTrue(
					midxTip.findOffset(ctx, o1) > midxTip.findOffset(ctx, o2));
			assertTrue(
					midxTip.findOffset(ctx, o6) > midxTip.findOffset(ctx, o1));

			long posNon = midxTip.findOffset(ctx, NOT_IN_PACK);
			assertEquals(-1, posNon);
		}
	}

	@Test
	public void resolve() throws Exception {
		// These oids do NOT have same prefix
		ObjectId[] oids = MidxTestUtils.writePackWithBlobs(db, "AAAAAA",
				"BBBBBB", "CCCCCCC");
		DfsPackFile midx = MidxTestUtils.writeSinglePackMidx(db);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			Set<ObjectId> matches = new HashSet<>();
			midx.resolve(ctx, matches, oids[0].abbreviate(6), 100);
			assertEquals(1, matches.size());

			matches.clear();
			midx.resolve(ctx, matches, oids[1].abbreviate(6), 100);
			assertEquals(1, matches.size());

			matches = new HashSet<>();
			midx.resolve(ctx, matches, NOT_IN_PACK.abbreviate(8), 100);
			assertEquals(0, matches.size());
		}
	}

	@Test
	public void resolve_withBase() throws Exception {
		ObjectId o1 = writePackWithRandomBlob(100);
		ObjectId o2 = writePackWithRandomBlob(200);
		writePackWithRandomBlob(150);
		ObjectId o4 = writePackWithRandomBlob(400);
		writePackWithRandomBlob(500);
		writePackWithRandomBlob(600);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		// Packs are in reverse insertion order
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 1, 6), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeSinglePackMidx(db,
				packs[0], midxBase);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			Set<ObjectId> matches = new HashSet<>();
			// Test with an ID that exists in the base
			assertTrue(midxTip.hasObject(ctx, o1));
			midxTip.resolve(ctx, matches, o1.abbreviate(6), 100);
			assertEquals(1, matches.size());
			assertTrue(matches.contains(o1));

			matches.clear();
			// Test with an ID that exists in the tip
			midxTip.resolve(ctx, matches, o4.abbreviate(6), 100);
			assertTrue(matches.contains(o4));
			assertEquals(1, matches.size());

			matches.clear();
			midxTip.resolve(ctx, matches, o2.abbreviate(6), 1);
			assertTrue(matches.contains(o2));
			assertEquals(1, matches.size());

			matches.clear();
			midxTip.resolve(ctx, matches, NOT_IN_PACK.abbreviate(8), 100);
			assertEquals(0, matches.size());
		}
	}

	@Test
	public void findAllFromPack() throws Exception {
		ObjectId[] objectIds = MidxTestUtils.writePackWithBlobs(db, "aaaaaaaa",
				"bbbbbbbbb", "cccccccccc");
		DfsPackFile midx = writeMultipackIndex();

		List<ObjectToPack> otps = List.of(
				new DfsObjectToPack(objectIds[0], OBJ_BLOB),
				new DfsObjectToPack(objectIds[1], OBJ_BLOB),
				new DfsObjectToPack(objectIds[2], OBJ_BLOB),
				new DfsObjectToPack(NOT_IN_PACK, OBJ_BLOB));

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			List<DfsObjectToPack> allFromPack = midx.findAllFromPack(ctx, otps,
					true);
			assertEquals(3, allFromPack.size());

			DfsObjectToPack oneToPack = allFromPack.get(0);
			assertEquals(midx.findOffset(ctx, objectIds[0]),
					oneToPack.getOffset());

			DfsObjectToPack twoToPack = allFromPack.get(1);
			assertEquals(midx.findOffset(ctx, objectIds[1]),
					twoToPack.getOffset());

			DfsObjectToPack threeToPack = allFromPack.get(2);
			assertEquals(midx.findOffset(ctx, objectIds[2]),
					threeToPack.getOffset());
		}
	}

	@Test
	public void findAllFromPack_withBase() throws Exception {
		ObjectId o1 = writePackWithRandomBlob(100);
		ObjectId o2 = writePackWithRandomBlob(200);
		ObjectId o3 = writePackWithRandomBlob(150);
		ObjectId o4 = writePackWithRandomBlob(400);
		ObjectId o5 = writePackWithRandomBlob(500);
		ObjectId o6 = writePackWithRandomBlob(600);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		// Packs are in reverse insertion order (o6 -> o1)
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 1, 6), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeSinglePackMidx(db,
				packs[0], midxBase);

		List<ObjectToPack> otps = List.of(new DfsObjectToPack(o1, OBJ_BLOB),
				new DfsObjectToPack(o4, OBJ_BLOB),
				new DfsObjectToPack(o2, OBJ_BLOB),
				new DfsObjectToPack(o5, OBJ_BLOB),
				new DfsObjectToPack(NOT_IN_PACK, OBJ_BLOB),
				new DfsObjectToPack(o3, OBJ_BLOB),
				new DfsObjectToPack(o6, OBJ_BLOB));

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			List<DfsObjectToPack> allFromPack = midxTip.findAllFromPack(ctx,
					otps, true);
			assertEquals(6, allFromPack.size());

			// Objects are in midx-offset order which is:
			// base(o5< o4 < o3 < o2 < o1) -> tip (06)
			DfsObjectToPack oneToPack = allFromPack.get(0);
			assertEquals(midxTip.findOffset(ctx, o5), oneToPack.getOffset());

			DfsObjectToPack twoToPack = allFromPack.get(1);
			assertEquals(midxTip.findOffset(ctx, o4), twoToPack.getOffset());

			DfsObjectToPack threeToPack = allFromPack.get(2);
			assertEquals(midxTip.findOffset(ctx, o3), threeToPack.getOffset());

			DfsObjectToPack fourToPack = allFromPack.get(3);
			assertEquals(midxTip.findOffset(ctx, o2), fourToPack.getOffset());

			DfsObjectToPack fiveToPack = allFromPack.get(4);
			assertEquals(midxTip.findOffset(ctx, o1), fiveToPack.getOffset());

			DfsObjectToPack sixToPack = allFromPack.get(5);
			assertEquals(midxTip.findOffset(ctx, o6), sixToPack.getOffset());
		}
	}

	@Test
	public void copyPackAsIs() throws Exception {
		ObjectId[] objectIds = MidxTestUtils.writePackWithBlobs(db, "aaaaaa",
				"bbbbbbbb", "ccccccccc");
		DfsPackFileMidx midx = MidxTestUtils.writeSinglePackMidx(db);
		DfsPackFile pack = findPack(objectIds[0]);
		assertArrayEquals(copyPackAsIs(pack), copyPackAsIs(midx));
	}

	@Test
	public void copyPackAsIs_withBase() throws Exception {
		writePackWithRandomBlob(100);
		writePackWithRandomBlob(200);
		writePackWithRandomBlob(150);
		writePackWithRandomBlob(400);
		writePackWithRandomBlob(500);
		writePackWithRandomBlob(600);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		long expectedPackSize = Arrays.stream(packs)
				.mapToLong(pack -> pack.getPackDescription().getFileSize(PACK))
				.map(size -> size - 12 - 20) // remove header + CRC
				.sum();

		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 1, 6), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeSinglePackMidx(db,
				packs[0], midxBase);

		try (DfsReader ctx = db.getObjectDatabase().newReader();
				PackWriter pw = new PackWriter(new PackConfig(), ctx);
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				PackOutputStream out = new PackOutputStream(
						NullProgressMonitor.INSTANCE, os, pw)) {
			midxTip.copyPackAsIs(out, ctx);
			out.flush();
			assertEquals(expectedPackSize, os.size());
		}
	}

	private byte[] copyPackAsIs(DfsPackFile source) throws IOException {
		try (DfsReader ctx = db.getObjectDatabase().newReader();
				PackWriter pw = new PackWriter(new PackConfig(), ctx)) {
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			PackOutputStream out = new PackOutputStream(
					NullProgressMonitor.INSTANCE, os, pw);
			source.copyPackAsIs(out, ctx);
			out.flush();
			return os.toByteArray();
		}
	}

	@Test
	public void copyAsIs() throws Exception {
		ObjectId blob = writePackWithRandomBlob(200);
		DfsPackFileMidx midx = MidxTestUtils.writeSinglePackMidx(db);

		assertEquals(213, copyAsIs(midx, blob).length);
	}

	@Test
	public void copyAsIs_withBase() throws Exception {
		writePackWithRandomBlob(100);
		ObjectId baseObject = writePackWithRandomBlob(200);
		writePackWithRandomBlob(150);
		writePackWithRandomBlob(400);
		MidxTestUtils.writePackWithBlob(db, "woohooABCxxxxx11111");
		ObjectId tipObject = writePackWithRandomBlob(600);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		// Packs are in reverse insertion order
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 1, 6), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeSinglePackMidx(db,
				packs[0], midxBase);

		assertEquals(213, copyAsIs(midxTip, baseObject).length);

		DfsPackFile pack = findPack(tipObject);
		byte[] fromPack = copyAsIs(pack, tipObject);
		// Reparsing produces the same id
		byte[] fromMidx = copyAsIs(midxTip, tipObject);
		assertArrayEquals(fromPack, fromMidx);
	}

	private byte[] copyAsIs(DfsPackFile pack, ObjectId oid) throws Exception {
		try (DfsReader ctx = db.getObjectDatabase().newReader();
				PackWriter pw = new PackWriter(new PackConfig(), ctx);
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				PackOutputStream out = new PackOutputStream(
						NullProgressMonitor.INSTANCE, os, pw)) {
			// Object in the base
			ObjectToPack otp = new DfsObjectToPack(oid, OBJ_BLOB);
			DfsObjectToPack inPack = pack
					.findAllFromPack(ctx, List.of(otp), false).get(0);
			DfsObjectRepresentation r = new DfsObjectRepresentation(pack);
			pack.fillRepresentation(r, inPack.getOffset(), ctx);
			inPack.select(r);
			pack.copyAsIs(out, inPack, false, ctx);
			out.flush();
			return os.toByteArray();
		}
	}

	@Test
	public void getDeltaHeader() {
		// TODO(ifrade): Implement
	}

	@Test
	public void getObjectType() throws Exception {
		CommitObjects commitObjects = writePackWithOneCommit();
		gcWithBitmaps();
		DfsPackFile midx = MidxTestUtils.writeSinglePackMidx(db);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			long commitPos = midx.findOffset(ctx, commitObjects.commit());
			assertEquals(OBJ_COMMIT, midx.getObjectType(ctx, commitPos));

			long treePos = midx.findOffset(ctx, commitObjects.tree());
			assertEquals(OBJ_TREE, midx.getObjectType(ctx, treePos));

			long blobPos = midx.findOffset(ctx, commitObjects.blob());
			assertEquals(OBJ_BLOB, midx.getObjectType(ctx, blobPos));

			assertThrows(IllegalArgumentException.class,
					() -> midx.getObjectType(ctx, 12000));
		}
	}

	@Test
	public void getObjectType_withBase() throws Exception {
		// This first commit creates two packs
		CommitObjects objs = writePackWithOneCommit();
		writePackWithCommit();

		writePackWithCommit();
		writePackWithRandomBlob(300);
		ObjectId newCommit = writePackWithCommit();

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		// Packs are in reverse insertion order
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 1, 7), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeSinglePackMidx(db,
				packs[0], midxBase);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			long commitPos = midxTip.findOffset(ctx, objs.commit());
			assertEquals(OBJ_COMMIT, midxTip.getObjectType(ctx, commitPos));

			long treePos = midxTip.findOffset(ctx, objs.tree());
			assertEquals(OBJ_TREE, midxTip.getObjectType(ctx, treePos));

			long blobPos = midxTip.findOffset(ctx, objs.blob());
			assertEquals(OBJ_BLOB, midxTip.getObjectType(ctx, blobPos));

			long commitPosTip = midxTip.findOffset(ctx, newCommit);
			assertEquals(OBJ_COMMIT, midxTip.getObjectType(ctx, commitPosTip));

			assertThrows(IllegalArgumentException.class,
					() -> midxTip.getObjectType(ctx, 12000));
		}
	}

	@Test
	public void getObjectSize_byId() throws Exception {
		CommitObjects objs = writePackWithOneCommit();
		gcWithBitmaps();
		DfsPackFile midx = MidxTestUtils.writeSinglePackMidx(db);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertEquals(168, midx.getObjectSize(ctx, objs.commit()));
			assertEquals(33, midx.getObjectSize(ctx, objs.tree()));
			assertEquals(5, midx.getObjectSize(ctx, objs.blob()));

			assertEquals(-1, midx.getObjectSize(ctx, NOT_IN_PACK));
		}
	}

	@Test
	public void getObjectSize_byId_withBase() throws Exception {
		ObjectId commit = writePackWithCommit();
		ObjectId blob = writePackWithRandomBlob(200);

		writePackWithRandomBlob(300);
		ObjectId blobTwo = writePackWithRandomBlob(100);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		// Packs are in reverse insertion order
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 1, 5), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeSinglePackMidx(db,
				packs[0], midxBase);

		try (RevWalk rw = new RevWalk(db);
				DfsReader ctx = db.getObjectDatabase().newReader()) {
			RevCommit aCommit = rw.parseCommit(commit);
			RevTree aTree = rw.parseTree(aCommit.getTree());

			assertEquals(168, midxTip.getObjectSize(ctx, aCommit));
			assertEquals(33, midxTip.getObjectSize(ctx, aTree));
			assertEquals(200, midxTip.getObjectSize(ctx, blob));
			assertEquals(100, midxTip.getObjectSize(ctx, blobTwo));

			assertEquals(-1, midxTip.getObjectSize(ctx, NOT_IN_PACK));
		}
	}

	@Test
	public void getObjectSize_byOffset() throws Exception {
		ObjectId commit = writePackWithCommit();
		gcWithBitmaps();
		DfsPackFile midx = MidxTestUtils.writeSinglePackMidx(db);

		try (RevWalk rw = new RevWalk(db);
				DfsReader ctx = db.getObjectDatabase().newReader()) {
			RevCommit aCommit = rw.parseCommit(commit);
			RevTree aTree = rw.parseTree(aCommit.getTree());

			assertEquals(168,
					midx.getObjectSize(ctx, midx.findOffset(ctx, aCommit)));
			assertEquals(33,
					midx.getObjectSize(ctx, midx.findOffset(ctx, aTree)));

			assertEquals(-1, midx.getObjectSize(ctx, -1));
		}
	}

	@Test
	public void getObjectSize_byOffset_withBase() throws Exception {
		ObjectId commit = writePackWithCommit();
		ObjectId blob = writePackWithRandomBlob(200);

		writePackWithRandomBlob(300);
		ObjectId blobTwo = writePackWithRandomBlob(100);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		// Packs are in reverse insertion order
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 1, 5), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeSinglePackMidx(db,
				packs[0], midxBase);

		try (RevWalk rw = new RevWalk(db);
				DfsReader ctx = db.getObjectDatabase().newReader()) {
			RevCommit aCommit = rw.parseCommit(commit);
			RevTree aTree = rw.parseTree(aCommit.getTree());

			assertEquals(168, midxTip.getObjectSize(ctx,
					midxTip.findOffset(ctx, aCommit)));
			assertEquals(33,
					midxTip.getObjectSize(ctx, midxTip.findOffset(ctx, aTree)));
			assertEquals(200,
					midxTip.getObjectSize(ctx, midxTip.findOffset(ctx, blob)));
			assertEquals(100, midxTip.getObjectSize(ctx,
					midxTip.findOffset(ctx, blobTwo)));

			assertEquals(-1, midxTip.getObjectSize(ctx, -1));
			assertEquals(-1, midxTip.getObjectSize(ctx,
					midxTip.findOffset(ctx, NOT_IN_PACK)));
		}
	}

	@Test
	public void objectSizeIndex_disabled() throws Exception {
		writePackWithRandomBlob(200);
		DfsPackFile midx = MidxTestUtils.writeSinglePackMidx(db);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertFalse(midx.hasObjectSizeIndex(ctx));
		}
	}

	@Test
	public void fillRepresentation() throws Exception {
		RevCommit commit = writePackWithCommit();
		gcWithBitmaps();
		DfsPackFile midx = MidxTestUtils.writeSinglePackMidx(db);

		DfsObjectRepresentation rep = fillRepresentation(midx, commit,
				OBJ_COMMIT);
		assertEquals(midx, rep.pack);
		assertEquals(12, rep.offset);
		assertEquals(120, rep.length);
	}

	DfsObjectRepresentation fillRepresentation(DfsPackFile midx,
			ObjectId commit, int typeHint) throws Exception {
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			ObjectToPack otp = new DfsObjectToPack(commit, typeHint);
			DfsObjectToPack inPack = midx
					.findAllFromPack(ctx, List.of(otp), true).get(0);
			DfsObjectRepresentation rep = new DfsObjectRepresentation(midx);
			midx.fillRepresentation(rep, inPack.getOffset(), ctx);
			return rep;
		}
	}

	@Test
	public void fillRepresentation_withBase() throws Exception {
		RevCommit commitInBase = writePackWithCommit();
		ObjectId blob = writePackWithRandomBlob(300);
		writePackWithRandomBlob(500);
		writePackWithRandomBlob(100);
		RevCommit commitInTip = writePackWithCommit();

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(6, packs.length);
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 1, 6), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeSinglePackMidx(db,
				packs[0], midxBase);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			// Commit in tip midx
			DfsObjectRepresentation rep = fillRepresentation(midxTip,
					commitInTip, OBJ_COMMIT);
			assertEquals(midxTip.getPackDescription(),
					rep.pack.getPackDescription());
			assertEquals(midxTip.findOffset(ctx, commitInTip), rep.offset);
			assertEquals(148, rep.length);

			// Commit in base midx
			rep = fillRepresentation(midxTip, commitInBase, OBJ_COMMIT);
			assertEquals(midxTip.getPackDescription(),
					rep.pack.getPackDescription());
			assertEquals(midxTip.findOffset(ctx, commitInBase), rep.offset);
			assertEquals(120, rep.length);

			// Blob in base
			rep = fillRepresentation(midxTip, blob, OBJ_COMMIT);
			assertEquals(midxTip.getPackDescription(),
					rep.pack.getPackDescription());
			assertEquals(midxTip.findOffset(ctx, blob), rep.offset);
			assertEquals(281, rep.length);
		}
	}

	@Test
	public void getBitmapIndex() throws Exception {
		RevCommit c1 = writePackWithCommit();
		RevCommit c2 = writePackWithCommit();
		gcWithBitmaps();

		DfsPackFileMidx midx = MidxTestUtils.writeSinglePackMidx(db);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			PackBitmapIndex bitmapIndex = midx.getBitmapIndex(ctx);
			assertNotNull(bitmapIndex);
			assertEquals(4, bitmapIndex.getObjectCount());
			assertEquals(1, bitmapIndex.findPosition(c1));
			assertEquals(0, bitmapIndex.findPosition(c2));
		}
	}

	@Test
	public void getAllCoveredPacks() throws Exception {
		ObjectId[] objectIds = MidxTestUtils.writePackWithBlobs(db, "aaaaaaaa",
				"bbbbbbb", "ccccccc");
		DfsPackFileMidx midx = MidxTestUtils.writeSinglePackMidx(db);
		DfsPackFile realPack = findPack(objectIds[0]);

		assertEquals(1, midx.getAllCoveredPacks().size());
		assertEquals(realPack.getPackDescription(),
				midx.getAllCoveredPacks().get(0).getPackDescription());
	}

	@Test
	public void getAllCoveredPacks_withBase() throws Exception {
		writePackWithCommit();
		writePackWithRandomBlob(300);
		writePackWithRandomBlob(500);
		writePackWithRandomBlob(100);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(5, packs.length);
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 1, 5), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeSinglePackMidx(db,
				packs[0], midxBase);

		assertEquals(5, midxTip.getAllCoveredPacks().size());
		List<DfsPackDescription> expected = Arrays.stream(packs)
				.map(DfsPackFile::getPackDescription).toList();
		List<DfsPackDescription> actual = midxTip.getAllCoveredPacks().stream()
				.map(DfsPackFile::getPackDescription).toList();
		assertEquals(expected, actual);
	}

	@Test
	public void getCoveredPacks_withBase_onlyTopMidx() throws Exception {
		writePackWithCommit();
		writePackWithRandomBlob(300);
		writePackWithRandomBlob(500);
		writePackWithRandomBlob(100);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(5, packs.length);
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 1, 5), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeSinglePackMidx(db,
				packs[0], midxBase);

		assertEquals(1, midxTip.getCoveredPacks().size());
		assertEquals(packs[0].getPackDescription(),
				midxTip.getAllCoveredPacks().get(0).getPackDescription());
	}

	@Test
	public void corrupt() throws Exception {
		RevCommit commit = writePackWithCommit();
		DfsPackFile pack = findPack(commit);
		DfsPackFileMidx midx = MidxTestUtils.writeSinglePackMidx(db, pack);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertFalse(midx.isCorrupt(midx.findOffset(ctx, commit)));
		}
	}

	@Test
	public void packwriter_via_midx() throws Exception {
		ObjectId[] oids = MidxTestUtils.writePackWithBlobs(db, "xxxxxxxyyyy",
				"booooohooooo", "baaaaahaaaaaa");
		ObjectId blob = oids[0];
		ObjectId blobTwo = oids[1];
		ObjectId notPacked = oids[2];

		MidxTestUtils.writeSinglePackMidx(db);

		db.getObjectDatabase().setUseMultipackIndex(true);
		byte[] writtenPack;
		try (DfsReader ctx = db.getObjectDatabase().newReader();
				RevWalk rw = new RevWalk(ctx);
				PackWriter pw = new PackWriter(db, ctx)) {
			pw.addObject(rw.lookupBlob(blob));
			pw.addObject(rw.lookupBlob(blobTwo));

			try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				CounterProgressMonitor cpm = new CounterProgressMonitor();
				pw.writePack(NullProgressMonitor.INSTANCE, cpm, out);
				out.flush();
				assertEquals(79, out.size());
				assertEquals(2, cpm.objectsPacked);
				writtenPack = out.toByteArray();
			}
		}

		try (InMemoryRepository dest = new InMemoryRepository(
				new DfsRepositoryDescription("test"));
				ObjectInserter ins = dest.getObjectDatabase().newInserter()) {
			ins.newPackParser(new ByteArrayInputStream(writtenPack))
					.parse(NullProgressMonitor.INSTANCE);
			assertTrue(dest.getObjectDatabase().has(blob));
			assertTrue(dest.getObjectDatabase().has(blobTwo));
			assertFalse(dest.getObjectDatabase().has(notPacked));
		}
	}

	@Test
	public void voffsetcalculator_encode() {
		DfsPackFile pack = createDfsPackFile(900);
		SingleVOffsetCalculator calc = new SingleVOffsetCalculator(pack, null);

		PackOffset po = PackOffset.create(0, 12);
		assertEquals(12, calc.encode(po));
		po = PackOffset.create(0, 800);
		assertEquals(800, calc.encode(po));

		// Invalid packId
		assertThrows(IllegalArgumentException.class,
				() -> calc.encode(PackOffset.create(1, 12)));
	}

	@Test
	public void voffsetcalculator_decode() {
		DfsPackFile pack = createDfsPackFile(800);
		SingleVOffsetCalculator calc = new SingleVOffsetCalculator(pack, null);

		DfsPackOffset decoded = calc.decode(130);
		assertEquals(pack.getPackDescription(),
				decoded.getPack().getPackDescription());
		assertEquals(130, decoded.getPackOffset());
		assertEquals(0, decoded.getPackStart());

		decoded = calc.decode(0);
		assertEquals(pack.getPackDescription(),
				decoded.getPack().getPackDescription());
		assertEquals(0, decoded.getPackOffset());
		assertEquals(0, decoded.getPackStart());

		assertThrows("too big", IllegalArgumentException.class,
				() -> calc.decode(900));
	}

	@Test
	public void voffsetcalculator_notFound() {
		DfsPackFile pack = createDfsPackFile(800);
		SingleVOffsetCalculator calc = new SingleVOffsetCalculator(pack, null);

		assertEquals(-1, calc.encode(null));
		assertNull(calc.decode(-1));
	}

	@Test
	public void voffsetcalculator_maxOffset() {
		DfsPackFile pack = createDfsPackFile(800);
		SingleVOffsetCalculator calc = new SingleVOffsetCalculator(pack, null);
		assertEquals(800, calc.getMaxOffset());
	}

	@Test
	public void voffsetcalculator_withBase_encode() {
		DfsPackFile one = createDfsPackFile(800);
		SingleVOffsetCalculator calcOne = new SingleVOffsetCalculator(one,
				null);
		DfsPackFile two = createDfsPackFile(1200);
		SingleVOffsetCalculator calcTwo = new SingleVOffsetCalculator(two,
				calcOne);

		PackOffset po = PackOffset.create(0, 12);
		assertEquals(12 + 800, calcTwo.encode(po));
	}

	@Test
	public void voffsetcalculator_withBase_decode() {
		DfsPackFile one = createDfsPackFile(800);
		SingleVOffsetCalculator calcOne = new SingleVOffsetCalculator(one,
				null);
		DfsPackFile two = createDfsPackFile(1200);
		SingleVOffsetCalculator calcTwo = new SingleVOffsetCalculator(two,
				calcOne);

		// In top pack
		DfsPackOffset decoded = calcTwo.decode(900);
		assertEquals(two.getPackDescription(),
				decoded.getPack().getPackDescription());
		assertEquals(100, decoded.getPackOffset());
		assertEquals(800, decoded.getPackStart());

		// In parent pack
		decoded = calcTwo.decode(700);
		assertEquals(one.getPackDescription(),
				decoded.getPack().getPackDescription());
		assertEquals(700, decoded.getPackOffset());
		assertEquals(0, decoded.getPackStart());
	}

	@Test
	public void voffsetcalculator_withBase_maxOffset() {
		DfsPackFile one = createDfsPackFile(800);
		SingleVOffsetCalculator calcOne = new SingleVOffsetCalculator(one,
				null);
		DfsPackFile two = createDfsPackFile(1200);
		SingleVOffsetCalculator calcTwo = new SingleVOffsetCalculator(two,
				calcOne);

		assertEquals(2000, calcTwo.getMaxOffset());
	}

	private static DfsPackFile createDfsPackFile(int size) {
		DfsPackDescription desc = new DfsPackDescription(
				new DfsRepositoryDescription("the_repo"), "pack_blabla", GC);
		desc.addFileExt(PACK);
		desc.setFileSize(PACK, size);
		desc.setObjectCount(1);
		return new DfsPackFile(null, desc);
	}

	private DfsPackFileMidx writeMultipackIndex() throws IOException {
		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		return MidxTestUtils.writeMultipackIndex(db, packs, null);
	}

	private void gcWithBitmaps() throws IOException {
		DfsGarbageCollector garbageCollector = new DfsGarbageCollector(db);
		garbageCollector.pack(NullProgressMonitor.INSTANCE);
	}

	private RevCommit writePackWithCommit() throws Exception {
		try (TestRepository<InMemoryRepository> repository = new TestRepository<>(
				db)) {
			// commitCounter++;
			return repository.branch("/refs/heads/main").commit()
					.add("blob" + commitCounter, "blob" + commitCounter)
					.create();
		}
	}

	record CommitObjects(RevCommit commit, RevTree tree, RevBlob blob) {
	}

	private static int commitCounter = 1;

	private CommitObjects writePackWithOneCommit() throws Exception {
		try (TestRepository<InMemoryRepository> repository = new TestRepository<>(
				db)) {
			RevBlob blob = repository.blob("blob" + commitCounter);
			RevCommit revCommit = repository.branch("/refs/heads/main").commit()
					.add("blob" + commitCounter, blob).create();
			commitCounter++;
			return new CommitObjects(revCommit, revCommit.getTree(), blob);
		}
	}

	private ObjectId writePackWithRandomBlob(int size) throws IOException {
		byte[] data = new TestRng(JGitTestUtil.getName()).nextBytes(size);
		return writePackWithBlob(data);
	}

	private ObjectId writePackWithBlob(byte[] data) throws IOException {
		DfsInserter ins = (DfsInserter) db.newObjectInserter();

		ObjectId blobId = ins.insert(OBJ_BLOB, data);
		ins.flush();
		return blobId;
	}

	private DfsPackFile findPack(ObjectId oid) throws IOException {
		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			for (DfsPackFile pack : packs) {
				if (pack.hasObject(ctx, oid)) {
					return pack;
				}
			}
		}
		throw new IllegalArgumentException("Object not in any pack");
	}

	private static final class CounterProgressMonitor
			implements ProgressMonitor {

		int objectsPacked = 0;

		@Override
		public void start(int totalTasks) {
			// empty
		}

		@Override
		public void beginTask(String title, int totalWork) {
			System.out.println("Starting " + title);
		}

		@Override
		public void update(int completed) {
			objectsPacked += 1;
		}

		@Override
		public void endTask() {
			// empty
		}

		@Override
		public boolean isCancelled() {
			return false;
		}

		@Override
		public void showDuration(boolean enabled) {
			// empty
		}
	}

	private byte[] safeGetBytes(@Nullable ObjectLoader ol) {
		assertNotNull(ol);
		byte[] data = ol.getBytes();
		assertNotNull(data);
		return data;
	}
}
