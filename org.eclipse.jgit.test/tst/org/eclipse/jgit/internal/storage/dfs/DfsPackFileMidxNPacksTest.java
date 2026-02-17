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
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_BITMAP_DISTANT_COMMIT_SPAN;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_PACK_SECTION;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
import java.util.zip.Deflater;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.dfs.DfsPackFileMidx.DfsPackOffset;
import org.eclipse.jgit.internal.storage.dfs.DfsPackFileMidx.VOffsetCalculator;
import org.eclipse.jgit.internal.storage.dfs.DfsPackFileMidxNPacks.VOffsetCalculatorNPacks;
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
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.junit.Before;
import org.junit.Test;

import com.googlecode.javaewah.EWAHCompressedBitmap;

public class DfsPackFileMidxNPacksTest {

	private static final ObjectId NOT_IN_PACK = ObjectId
			.fromString("3f306cb3fcd5116919fecad615524bd6e6ea4ba7");

	InMemoryRepository db;

	@Before
	public void setUp() {
		db = new InMemoryRepository(new DfsRepositoryDescription("test"));
	}

	@Test
	public void midx_findIdxPosition() throws IOException {
		ObjectId o1 = writePackWithBlob("something".getBytes(UTF_8));
		ObjectId o2 = writePackWithBlob("something else".getBytes(UTF_8));
		ObjectId o3 = writePackWithBlob("and more".getBytes(UTF_8));
		DfsPackFile midx = writeMultipackIndex();

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertEquals(2, midx.findIdxPosition(ctx, o1));
			assertEquals(0, midx.findIdxPosition(ctx, o2));
			assertEquals(1, midx.findIdxPosition(ctx, o3));
			assertEquals(-1, midx.findIdxPosition(ctx, NOT_IN_PACK));
		}
	}

	@Test
	public void midx_findIdxPosition_withBase() throws IOException {
		ObjectId o1 = writePackWithBlob("o1".getBytes(UTF_8));
		ObjectId o2 = writePackWithBlob("o2".getBytes(UTF_8));
		ObjectId o3 = writePackWithBlob("o3".getBytes(UTF_8));
		ObjectId o4 = writePackWithBlob("o4".getBytes(UTF_8));
		ObjectId o5 = writePackWithBlob("o5".getBytes(UTF_8));
		ObjectId o6 = writePackWithBlob("o6".getBytes(UTF_8));
		DfsPackFile[] packs = db.getObjectDatabase().getPacks();

		// Packs are in reverse insertion order
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 4, 6), null);
		DfsPackFileMidx midxMid = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 2, 4), midxBase);
		DfsPackFileMidx midxTip = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 0, 2), midxMid);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertEquals(0, midxTip.findIdxPosition(ctx, o1));
			assertEquals(1, midxTip.findIdxPosition(ctx, o2));
			assertEquals(2, midxTip.findIdxPosition(ctx, o3));
			assertEquals(3, midxTip.findIdxPosition(ctx, o4));
			// In sha1 order
			assertEquals(5, midxTip.findIdxPosition(ctx, o5));
			assertEquals(4, midxTip.findIdxPosition(ctx, o6));
		}
	}

	@Test
	public void midx_getObjectAt() throws IOException {
		ObjectId o1 = writePackWithBlob("something".getBytes(UTF_8));
		ObjectId o2 = writePackWithBlob("something else".getBytes(UTF_8));
		ObjectId o3 = writePackWithBlob("and more".getBytes(UTF_8));
		DfsPackFileMidx midx = writeMultipackIndex();

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertEquals(o1, midx.getObjectAt(ctx, 2));
			assertEquals(o2, midx.getObjectAt(ctx, 0));
			assertEquals(o3, midx.getObjectAt(ctx, 1));
		}
	}

	@Test
	public void midx_getObjectAt_withBase() throws IOException {
		ObjectId o1 = writePackWithBlob("o1".getBytes(UTF_8));
		ObjectId o2 = writePackWithBlob("o2".getBytes(UTF_8));
		ObjectId o3 = writePackWithBlob("o3".getBytes(UTF_8));
		ObjectId o4 = writePackWithBlob("o4".getBytes(UTF_8));
		ObjectId o5 = writePackWithBlob("o5".getBytes(UTF_8));
		ObjectId o6 = writePackWithBlob("o6".getBytes(UTF_8));
		DfsPackFile[] packs = db.getObjectDatabase().getPacks();

		// Packs are in reverse insertion order
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 4, 6), null);
		DfsPackFileMidx midxMid = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 2, 4), midxBase);
		DfsPackFileMidx midxTip = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 0, 2), midxMid);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertEquals(o1, midxTip.getObjectAt(ctx, 0));
			assertEquals(o2, midxTip.getObjectAt(ctx, 1));
			assertEquals(o3, midxTip.getObjectAt(ctx, 2));
			assertEquals(o4, midxTip.getObjectAt(ctx, 3));
			// In sha1 order
			assertEquals(o5, midxTip.getObjectAt(ctx, 5));
			assertEquals(o6, midxTip.getObjectAt(ctx, 4));
		}
	}

	@Test
	public void midx_hasObject() throws IOException {
		ObjectId o1 = writePackWithRandomBlob(100);
		ObjectId o2 = writePackWithRandomBlob(200);
		ObjectId o3 = writePackWithRandomBlob(150);
		DfsPackFile midx = writeMultipackIndex();

		// DfsPackFile midx = readDfsPackFileMidx();
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertTrue(midx.hasObject(ctx, o1));
			assertTrue(midx.hasObject(ctx, o2));
			assertTrue(midx.hasObject(ctx, o3));
			assertFalse(midx.hasObject(ctx, NOT_IN_PACK));
		}
	}

	@Test
	public void midx_hasObject_withBase() throws IOException {
		ObjectId o1 = writePackWithRandomBlob(100);
		ObjectId o2 = writePackWithRandomBlob(200);
		ObjectId o3 = writePackWithRandomBlob(150);
		ObjectId o4 = writePackWithRandomBlob(400);
		ObjectId o5 = writePackWithRandomBlob(500);
		ObjectId o6 = writePackWithRandomBlob(600);
		DfsPackFile[] packs = db.getObjectDatabase().getPacks();

		// Packs are in reverse insertion order
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 3, 6), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 0, 3), midxBase);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertTrue(midxBase.hasObject(ctx, o1));
			assertTrue(midxBase.hasObject(ctx, o2));
			assertTrue(midxBase.hasObject(ctx, o3));
			// These are not in base
			assertFalse(midxBase.hasObject(ctx, o4));
			assertFalse(midxBase.hasObject(ctx, o5));
			assertFalse(midxBase.hasObject(ctx, o6));

			// Top midx has all objects
			assertTrue(midxTip.hasObject(ctx, o1));
			assertTrue(midxTip.hasObject(ctx, o2));
			assertTrue(midxTip.hasObject(ctx, o3));
			assertTrue(midxTip.hasObject(ctx, o4));
			assertTrue(midxTip.hasObject(ctx, o5));
			assertTrue(midxTip.hasObject(ctx, o6));
		}
	}

	@Test
	public void midx_get() throws IOException {
		byte[] contentOne = "ONE".getBytes(UTF_8);
		byte[] contentTwo = "TWO".getBytes(UTF_8);
		byte[] contentThree = "THREE".getBytes(UTF_8);

		ObjectId o1 = writePackWithBlob(contentOne);
		ObjectId o2 = writePackWithBlob(contentTwo);
		ObjectId o3 = writePackWithBlob(contentThree);
		DfsPackFile midx = writeMultipackIndex();

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			ObjectLoader objectLoader = midx.get(ctx, o1);
			assertArrayEquals(contentOne, safeGetBytes(objectLoader));
			objectLoader = midx.get(ctx, o2);
			assertArrayEquals(contentTwo, safeGetBytes(objectLoader));
			objectLoader = midx.get(ctx, o3);
			assertArrayEquals(contentThree, safeGetBytes(objectLoader));
			assertNull(midx.get(ctx, NOT_IN_PACK));
		}
	}

	@Test
	public void midx_get_withBase() throws IOException {
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
				Arrays.copyOfRange(packs, 3, 6), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 0, 3), midxBase);

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
	public void midx_load() throws IOException {
		byte[] contentOne = "ONE".getBytes(UTF_8);
		byte[] contentTwo = "TWO".getBytes(UTF_8);
		byte[] contentThree = "THREE".getBytes(UTF_8);

		ObjectId o1 = writePackWithBlob(contentOne);
		ObjectId o2 = writePackWithBlob(contentTwo);
		ObjectId o3 = writePackWithBlob(contentThree);
		DfsPackFile midx = writeMultipackIndex();

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			ObjectLoader objectLoader = midx.load(ctx,
					midx.findOffset(ctx, o1));
			assertArrayEquals(contentOne, safeGetBytes(objectLoader));
			objectLoader = midx.load(ctx, midx.findOffset(ctx, o2));
			assertArrayEquals(contentTwo, safeGetBytes(objectLoader));
			objectLoader = midx.load(ctx, midx.findOffset(ctx, o3));
			assertArrayEquals(contentThree, safeGetBytes(objectLoader));

			assertThrows(IllegalArgumentException.class,
					() -> midx.load(ctx, 500));
			assertNull(midx.load(ctx, -1));
		}
	}

	@Test
	public void midx_load_withBase() throws IOException {
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
				Arrays.copyOfRange(packs, 3, 6), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 0, 3), midxBase);

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
	public void midx_findOffset() throws IOException {
		ObjectId o1 = writePackWithRandomBlob(100);
		ObjectId o2 = writePackWithRandomBlob(200);
		ObjectId o3 = writePackWithRandomBlob(150);
		// Packs are written in the midx in reverse insertion time
		DfsPackFileMidx midx = writeMultipackIndex();

		DfsPackFile packOne = findPack(o1);
		DfsPackFile packTwo = findPack(o2);
		long packTwoSize = packTwo.getPackDescription().getFileSize(PACK);
		DfsPackFile packThree = findPack(o3);
		long packThreeSize = packThree.getPackDescription().getFileSize(PACK);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			long posOne = midx.findOffset(ctx, o1);
			DfsPackOffset po = midx.getOffsetCalculator()
					.decode(posOne);
			assertEquals(12, po.getPackOffset());
			assertEquals(packThreeSize + packTwoSize, po.getPackStart());
			assertEquals(packOne.getPackDescription(),
					po.getPack().getPackDescription());

			long posTwo = midx.findOffset(ctx, o2);
			po = midx.getOffsetCalculator().decode(posTwo);
			assertEquals(12, po.getPackOffset());
			assertEquals(packThreeSize, po.getPackStart());
			assertEquals(packTwo.getPackDescription(),
					po.getPack().getPackDescription());

			long posThree = midx.findOffset(ctx, o3);
			po = midx.getOffsetCalculator().decode(posThree);
			assertEquals(12, po.getPackOffset());
			assertEquals(0, po.getPackStart());
			assertEquals(packThree.getPackDescription(),
					po.getPack().getPackDescription());

			long posNon = midx.findOffset(ctx, NOT_IN_PACK);
			assertEquals(-1, posNon);
		}
	}

	@Test
	public void midx_resolve() throws Exception {
		ObjectId o1 = writePackWithRandomBlob(100);
		writePackWithRandomBlob(200);
		ObjectId o2 = writePackWithRandomBlob(150);
		DfsPackFile midx = writeMultipackIndex();

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			Set<ObjectId> matches = new HashSet<>();
			midx.resolve(ctx, matches, o1.abbreviate(6), 100);
			assertEquals(1, matches.size());

			matches.clear();
			midx.resolve(ctx, matches, o2.abbreviate(6), 100);
			assertEquals(1, matches.size());

			matches = new HashSet<>();
			midx.resolve(ctx, matches, NOT_IN_PACK.abbreviate(8), 100);
			assertEquals(0, matches.size());
		}
	}

	@Test
	public void midx_resolve_withBase() throws Exception {
		ObjectId o1 = writePackWithRandomBlob(100);
		ObjectId o2 = writePackWithRandomBlob(200);
		writePackWithRandomBlob(150);
		ObjectId o4 = writePackWithRandomBlob(400);
		writePackWithRandomBlob(500);
		writePackWithRandomBlob(600);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		// Packs are in reverse insertion order
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 3, 6), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 0, 3), midxBase);

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
	public void midx_findAllFromPack() throws Exception {
		ObjectId o1 = writePackWithRandomBlob(100);
		ObjectId o2 = writePackWithRandomBlob(200);
		ObjectId o3 = writePackWithRandomBlob(150);
		DfsPackFile midx = writeMultipackIndex();

		List<ObjectToPack> otps = List.of(new DfsObjectToPack(o1, OBJ_BLOB),
				new DfsObjectToPack(o2, OBJ_BLOB),
				new DfsObjectToPack(o3, OBJ_BLOB),
				new DfsObjectToPack(NOT_IN_PACK, OBJ_BLOB));

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			List<DfsObjectToPack> allFromPack = midx.findAllFromPack(ctx, otps,
					true);
			assertEquals(3, allFromPack.size());

			// Objects are in (pack, offset) order (i.e. reverse pack insert)
			DfsObjectToPack oneToPack = allFromPack.get(0);
			assertEquals(midx.findOffset(ctx, o3), oneToPack.getOffset());

			DfsObjectToPack twoToPack = allFromPack.get(1);
			assertEquals(midx.findOffset(ctx, o2), twoToPack.getOffset());

			DfsObjectToPack threeToPack = allFromPack.get(2);
			assertEquals(midx.findOffset(ctx, o1), threeToPack.getOffset());
		}
	}

	@Test
	public void midx_findAllFromPack_withBase() throws Exception {
		ObjectId o1 = writePackWithRandomBlob(100);
		ObjectId o2 = writePackWithRandomBlob(200);
		ObjectId o3 = writePackWithRandomBlob(150);
		ObjectId o4 = writePackWithRandomBlob(400);
		ObjectId o5 = writePackWithRandomBlob(500);
		ObjectId o6 = writePackWithRandomBlob(600);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		// Packs are in reverse insertion order (o6 -> o1)
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 3, 6), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 0, 3), midxBase);

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
			// base(o3 < o2 < o1) -> tip (06 < 05 < o4)
			DfsObjectToPack oneToPack = allFromPack.get(0);
			assertEquals(midxTip.findOffset(ctx, o3), oneToPack.getOffset());

			DfsObjectToPack twoToPack = allFromPack.get(1);
			assertEquals(midxTip.findOffset(ctx, o2), twoToPack.getOffset());

			DfsObjectToPack threeToPack = allFromPack.get(2);
			assertEquals(midxTip.findOffset(ctx, o1), threeToPack.getOffset());

			DfsObjectToPack fourToPack = allFromPack.get(3);
			assertEquals(midxTip.findOffset(ctx, o6), fourToPack.getOffset());

			DfsObjectToPack fiveToPack = allFromPack.get(4);
			assertEquals(midxTip.findOffset(ctx, o5), fiveToPack.getOffset());

			DfsObjectToPack sixToPack = allFromPack.get(5);
			assertEquals(midxTip.findOffset(ctx, o4), sixToPack.getOffset());
		}
	}

	@Test
	public void midx_copyPackAsIs() throws Exception {
		writePackWithRandomBlob(100);
		writePackWithRandomBlob(200);
		writePackWithRandomBlob(150);

		long expectedPackSize = Arrays.stream(db.getObjectDatabase().getPacks())
				.mapToLong(pack -> pack.getPackDescription().getFileSize(PACK))
				.map(size -> size - 12 - 20) // remove header + CRC
				.sum();
		DfsPackFile midx = writeMultipackIndex();

		try (DfsReader ctx = db.getObjectDatabase().newReader();
				PackWriter pw = new PackWriter(new PackConfig(), ctx);
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				PackOutputStream out = new PackOutputStream(
						NullProgressMonitor.INSTANCE, os, pw)) {
			midx.copyPackAsIs(out, ctx);
			out.flush();
			assertEquals(expectedPackSize, os.size());
		}
	}

	@Test
	public void midx_copyPackAsIs_withBase() throws Exception {
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
				Arrays.copyOfRange(packs, 3, 6), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 0, 3), midxBase);

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

	@Test
	public void midx_copyAsIs() throws Exception {
		writePackWithRandomBlob(100);
		ObjectId blob = writePackWithRandomBlob(200);
		writePackWithRandomBlob(150);
		DfsPackFileMidx midx = writeMultipackIndex();

		assertEquals(213, copyAsIs(midx, blob).length);
	}

	@Test
	public void midx_copyAsIs_withBase() throws Exception {
		writePackWithRandomBlob(100);
		ObjectId baseObject = writePackWithRandomBlob(200);
		writePackWithRandomBlob(150);
		writePackWithRandomBlob(400);
		ObjectId tipObject = writePackWithRandomBlob(500);
		writePackWithRandomBlob(600);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		// Packs are in reverse insertion order
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 3, 6), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 0, 3), midxBase);

		assertEquals(213, copyAsIs(midxTip, baseObject).length);
		assertEquals(513, copyAsIs(midxTip, tipObject).length);
	}

	private byte[] copyAsIs(DfsPackFileMidx midx, ObjectId oid)
			throws Exception {
		try (DfsReader ctx = db.getObjectDatabase().newReader();
				PackWriter pw = new PackWriter(new PackConfig(), ctx);
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				PackOutputStream out = new PackOutputStream(
						NullProgressMonitor.INSTANCE, os, pw)) {
			// Object in the base
			ObjectToPack otp = new DfsObjectToPack(oid, OBJ_BLOB);
			DfsObjectToPack inPack = midx
					.findAllFromPack(ctx, List.of(otp), false).get(0);
			DfsObjectRepresentation r = new DfsObjectRepresentation(midx);
			midx.fillRepresentation(r, inPack.getOffset(), ctx);
			inPack.select(r);
			midx.copyAsIs(out, inPack, false, ctx);
			out.flush();
			return os.toByteArray();
		}
	}

	@Test
	public void midx_getDeltaHeader() {
		// TODO(ifrade): Implement
	}

	@Test
	public void midx_getObjectType() throws Exception {
		ObjectId commit = writePackWithCommit();
		ObjectId blob = writePackWithRandomBlob(200);
		DfsPackFile midx = writeMultipackIndex();

		try (RevWalk rw = new RevWalk(db);
				DfsReader ctx = db.getObjectDatabase().newReader()) {
			RevCommit aCommit = rw.parseCommit(commit);
			RevTree aTree = rw.parseTree(aCommit.getTree());

			long commitPos = midx.findOffset(ctx, commit);
			assertEquals(OBJ_COMMIT, midx.getObjectType(ctx, commitPos));

			long treePos = midx.findOffset(ctx, aTree);
			assertEquals(OBJ_TREE, midx.getObjectType(ctx, treePos));

			long blobPos = midx.findOffset(ctx, blob);
			assertEquals(OBJ_BLOB, midx.getObjectType(ctx, blobPos));

			assertThrows(IllegalArgumentException.class,
					() -> midx.getObjectType(ctx, 12000));
		}
	}

	@Test
	public void midx_getObjectType_withBase() throws Exception {
		// This first commit creates two packs
		ObjectId commit = writePackWithCommit();
		ObjectId blob = writePackWithRandomBlob(200);
		writePackWithCommit();
		writePackWithCommit();
		writePackWithRandomBlob(300);
		ObjectId newCommit = writePackWithCommit();

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		// Packs are in reverse insertion order
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 4, 7), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 0, 4), midxBase);

		try (RevWalk rw = new RevWalk(db);
				DfsReader ctx = db.getObjectDatabase().newReader()) {
			RevCommit aCommit = rw.parseCommit(commit);
			RevTree aTree = rw.parseTree(aCommit.getTree());

			long commitPos = midxTip.findOffset(ctx, commit);
			assertEquals(OBJ_COMMIT, midxTip.getObjectType(ctx, commitPos));

			long treePos = midxTip.findOffset(ctx, aTree);
			assertEquals(OBJ_TREE, midxTip.getObjectType(ctx, treePos));

			long blobPos = midxTip.findOffset(ctx, blob);
			assertEquals(OBJ_BLOB, midxTip.getObjectType(ctx, blobPos));

			long commitPosTip = midxTip.findOffset(ctx, newCommit);
			assertEquals(OBJ_COMMIT, midxTip.getObjectType(ctx, commitPosTip));

			assertThrows(IllegalArgumentException.class,
					() -> midxTip.getObjectType(ctx, 12000));
		}
	}

	@Test
	public void midx_getObjectSize_byId() throws Exception {
		ObjectId commit = writePackWithCommit();
		ObjectId blob = writePackWithRandomBlob(200);
		DfsPackFile midx = writeMultipackIndex();

		try (RevWalk rw = new RevWalk(db);
				DfsReader ctx = db.getObjectDatabase().newReader()) {
			RevCommit aCommit = rw.parseCommit(commit);
			RevTree aTree = rw.parseTree(aCommit.getTree());
			RevBlob aBlob = rw.lookupBlob(blob);

			assertEquals(168, midx.getObjectSize(ctx, aCommit));
			assertEquals(33, midx.getObjectSize(ctx, aTree));
			assertEquals(200, midx.getObjectSize(ctx, aBlob));

			assertEquals(-1, midx.getObjectSize(ctx, NOT_IN_PACK));
		}
	}

	@Test
	public void midx_getObjectSize_byId_withBase() throws Exception {
		ObjectId commit = writePackWithCommit();
		ObjectId blob = writePackWithRandomBlob(200);

		writePackWithRandomBlob(300);
		ObjectId blobTwo = writePackWithRandomBlob(100);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		// Packs are in reverse insertion order
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 3, 5), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 0, 3), midxBase);

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
	public void midx_getObjectSize_byOffset() throws Exception {
		ObjectId commit = writePackWithCommit();
		ObjectId blob = writePackWithRandomBlob(200);
		DfsPackFile midx = writeMultipackIndex();

		try (RevWalk rw = new RevWalk(db);
				DfsReader ctx = db.getObjectDatabase().newReader()) {
			RevCommit aCommit = rw.parseCommit(commit);
			RevTree aTree = rw.parseTree(aCommit.getTree());
			RevBlob aBlob = rw.lookupBlob(blob);

			assertEquals(168,
					midx.getObjectSize(ctx, midx.findOffset(ctx, aCommit)));
			assertEquals(33,
					midx.getObjectSize(ctx, midx.findOffset(ctx, aTree)));
			assertEquals(200,
					midx.getObjectSize(ctx, midx.findOffset(ctx, aBlob)));

			assertEquals(-1, midx.getObjectSize(ctx, -1));
		}
	}

	@Test
	public void midx_getObjectSize_byOffset_withBase() throws Exception {
		ObjectId commit = writePackWithCommit();
		ObjectId blob = writePackWithRandomBlob(200);

		writePackWithRandomBlob(300);
		ObjectId blobTwo = writePackWithRandomBlob(100);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		// Packs are in reverse insertion order
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 3, 5), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 0, 3), midxBase);

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
	public void midx_objectSizeIndex_disabled() throws Exception {
		writePackWithCommit();
		writePackWithRandomBlob(200);
		DfsPackFile midx = writeMultipackIndex();

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertFalse(midx.hasObjectSizeIndex(ctx));
		}
	}

	@Test
	public void midx_fillRepresentation() throws Exception {
		writePackWithRandomBlob(200);
		RevCommit commit = writePackWithCommit();
		DfsPackFile midx = writeMultipackIndex();

		DfsObjectRepresentation rep = fillRepresentation(midx, commit,
				OBJ_COMMIT);
		assertEquals(midx, rep.pack);
		assertEquals(347, rep.offset);
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
	public void midx_fillRepresentation_withBase() throws Exception {
		RevCommit commitInBase = writePackWithCommit();
		ObjectId blob = writePackWithRandomBlob(300);
		writePackWithRandomBlob(500);
		writePackWithRandomBlob(100);
		RevCommit commitInTip = writePackWithCommit();

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(6, packs.length);
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 3, 6), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 0, 3), midxBase);

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			// Commit in tip midx
			DfsObjectRepresentation rep = fillRepresentation(midxTip,
					commitInTip, OBJ_COMMIT);
			assertEquals(midxTip.getPackDescription(),
					rep.pack.getPackDescription());
			assertEquals(midxTip.findOffset(ctx, commitInTip), rep.offset);
			assertEquals(151, rep.length);

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
			assertEquals(311, rep.length);
		}
	}

	@Test
	public void midx_getBitmapIndex_gc() throws Exception {
		RevCommit c1 = writePackWithCommit();
		RevCommit c2 = writePackWithCommit();
		gcWithBitmaps();

		ObjectId blob = writePackWithRandomBlob(300);
		DfsPackFileMidx dfsPackFileMidx = writeMultipackIndex();
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			PackBitmapIndex bitmapIndex = dfsPackFileMidx.getBitmapIndex(ctx);
			assertNotNull(bitmapIndex);
			// Both commits have same tree and blob
			assertEquals(4, bitmapIndex.getObjectCount());
			assertEquals(1, bitmapIndex.findPosition(c1));
			assertEquals(0, bitmapIndex.findPosition(c2));
			assertEquals(-1, bitmapIndex.findPosition(blob));
		}
	}

	@Test
	public void midx_getBitmapIndex_midx() throws Exception {
		RevCommit c1 = writePackWithCommit();
		RevCommit c2 = writePackWithCommit();
		gcWithBitmaps();

		RevCommit c3 = writePackWithCommit();
		DfsPackFileMidx dfsPackFileMidx = writeMultipackIndexWithBitmaps();
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			ctx.getOptions().setUseMidxBitmaps(true);
			PackBitmapIndex bitmapIndex = dfsPackFileMidx.getBitmapIndex(ctx);
			assertNotNull(bitmapIndex);
			assertEquals(3, bitmapIndex.getBitmapCount());
			// Both commits have same tree and blob
			assertEquals(5, bitmapIndex.getObjectCount());

			assertNotNull(bitmapIndex.getBitmap(c3));
			assertNotNull(bitmapIndex.getBitmap(c2));
			assertNotNull(bitmapIndex.getBitmap(c1));

			EWAHCompressedBitmap bitmapC3 = bitmapIndex.getBitmap(c3);
			EWAHCompressedBitmap bitmapC2 = bitmapIndex.getBitmap(c2);
			assertEquals(1, bitmapC3.andNot(bitmapC2).cardinality());
		}
	}

	@Test
	public void midx_getAllCoveredPacks() throws Exception {
		writePackWithCommit();
		writePackWithRandomBlob(300);
		writePackWithRandomBlob(500);

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(4, packs.length);
		DfsPackFileMidx midx = MidxTestUtils.writeMultipackIndex(db, packs,
				null);

		assertEquals(4, midx.getAllCoveredPacks().size());
		List<DfsPackDescription> expected = Arrays.stream(packs)
				.map(DfsPackFile::getPackDescription).toList();
		List<DfsPackDescription> actual = midx.getAllCoveredPacks().stream()
				.map(DfsPackFile::getPackDescription).toList();
		assertEquals(expected, actual);
	}

	@Test
	public void midx_getAllCoveredPacks_withBase() throws Exception {
		writePackWithCommit();
		writePackWithRandomBlob(300);
		writePackWithRandomBlob(500);
		writePackWithRandomBlob(100);
		writePackWithCommit();

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(6, packs.length);
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 4, 6), null);
		DfsPackFileMidx midxMiddle = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 2, 4), midxBase);
		DfsPackFileMidx midxTip = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 0, 2), midxMiddle);

		assertEquals(6, midxTip.getAllCoveredPacks().size());
		List<DfsPackDescription> expected = Arrays.stream(packs)
				.map(DfsPackFile::getPackDescription).toList();
		List<DfsPackDescription> actual = midxTip.getAllCoveredPacks().stream()
				.map(DfsPackFile::getPackDescription).toList();
		assertEquals(expected, actual);
	}

	@Test
	public void midx_getCoveredPacks_withBase_onlyTopMidx() throws Exception {
		writePackWithCommit();
		writePackWithRandomBlob(300);
		writePackWithRandomBlob(500);
		writePackWithRandomBlob(100);
		writePackWithCommit();

		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		assertEquals(6, packs.length);
		DfsPackFileMidx midxBase = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 3, 6), null);
		DfsPackFileMidx midxTip = MidxTestUtils.writeMultipackIndex(db,
				Arrays.copyOfRange(packs, 0, 3), midxBase);

		assertEquals(3, midxTip.getCoveredPacks().size());
		List<DfsPackDescription> expected = Arrays
				.stream(Arrays.copyOfRange(packs, 0, 3))
				.map(DfsPackFile::getPackDescription).toList();
		List<DfsPackDescription> actual = midxTip.getCoveredPacks().stream()
				.map(DfsPackFile::getPackDescription).toList();
		assertEquals(expected, actual);
	}

	@Test
	public void midx_corrupt() throws Exception {
		RevCommit commit = writePackWithCommit();
		writePackWithRandomBlob(200);
		DfsPackFile midx = writeMultipackIndex();
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertFalse(midx.isCorrupt(midx.findOffset(ctx, commit)));
		}
	}

	@Test
	public void packwriter_via_midx() throws Exception {
		RevCommit commit = writePackWithCommit();
		ObjectId blob = writePackWithBlob("booooohooooo".getBytes(UTF_8));
		ObjectId notPacked = writePackWithBlob("baaaaahaa".getBytes(UTF_8));
		writeMultipackIndex();

		byte[] writtenPack;
		try (DfsReader ctx = db.getObjectDatabase().newReader();
				RevWalk rw = new RevWalk(ctx);
				PackWriter pw = new PackWriter(db, ctx)) {
			pw.addObject(rw.lookupBlob(blob));
			pw.addObject(rw.lookupCommit(commit));

			try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				CounterProgressMonitor cpm = new CounterProgressMonitor();
				pw.writePack(NullProgressMonitor.INSTANCE, cpm, out);
				out.flush();
				assertEquals(178, out.size());
				assertEquals(2, cpm.objectsPacked);
				writtenPack = out.toByteArray();
			}
		}

		try (InMemoryRepository dest = new InMemoryRepository(
				new DfsRepositoryDescription("test"));
				ObjectInserter ins = dest.getObjectDatabase().newInserter()) {
			ins.newPackParser(new ByteArrayInputStream(writtenPack))
					.parse(NullProgressMonitor.INSTANCE);
			assertTrue(dest.getObjectDatabase().has(commit));
			assertTrue(dest.getObjectDatabase().has(blob));
			assertFalse(dest.getObjectDatabase().has(notPacked));
		}
	}

	@Test
	public void getChecksum() throws Exception {
		MidxTestUtils.writePackWithBlob(db, "something");
		MidxTestUtils.writePackWithBlob(db, "something else");
		MidxTestUtils.writePackWithBlob(db, "and more");
		DfsPackFileMidx midx = writeMultipackIndex();

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			byte[] checksum = midx.getChecksum(ctx);
			assertNotNull(checksum);
			assertEquals(20, checksum.length);
			assertNotEquals('M', checksum[0]);
			assertNotEquals('I', checksum[1]);
			assertNotEquals('D', checksum[2]);
			assertNotEquals('X', checksum[3]);
		}
	}

	@Test
	public void voffsetcalculator_encode() {
		DfsPackFile one = createDfsPackFile(800);
		DfsPackFile two = createDfsPackFile(1200);
		DfsPackFile three = createDfsPackFile(900);

		VOffsetCalculatorNPacks calc = VOffsetCalculatorNPacks
				.fromPacks(new DfsPackFile[] { one, two, three }, null);

		PackOffset po = PackOffset.create(0, 12);
		assertEquals(12, calc.encode(po));
		po = PackOffset.create(1, 12);
		assertEquals(800 + 12, calc.encode(po));
		po = PackOffset.create(2, 12);
		assertEquals(800 + 1200 + 12, calc.encode(po));
	}

	@Test
	public void voffsetcalculator_decode() {
		DfsPackFile one = createDfsPackFile(800);
		DfsPackFile two = createDfsPackFile(1200);
		DfsPackFile three = createDfsPackFile(900);

		VOffsetCalculator calc = VOffsetCalculatorNPacks
				.fromPacks(new DfsPackFile[] { one, two, three }, null);

		// In first pack
		DfsPackOffset decoded = calc.decode(130);
		assertEquals(one.getPackDescription(),
				decoded.getPack().getPackDescription());
		assertEquals(130, decoded.getPackOffset());
		assertEquals(0, decoded.getPackStart());

		// In second pack
		decoded = calc.decode(812);
		assertEquals(two.getPackDescription(),
				decoded.getPack().getPackDescription());
		assertEquals(12, decoded.getPackOffset());
		assertEquals(800, decoded.getPackStart());

		// In third pack
		decoded = calc.decode(2100);
		assertEquals(two.getPackDescription(),
				decoded.getPack().getPackDescription());
		assertEquals(100, decoded.getPackOffset());
		assertEquals(2000, decoded.getPackStart());
	}

	@Test
	public void voffsetcalculator_notFound() {
		DfsPackFile one = createDfsPackFile(800);
		DfsPackFile two = createDfsPackFile(1200);
		DfsPackFile three = createDfsPackFile(900);

		VOffsetCalculatorNPacks calc = VOffsetCalculatorNPacks
				.fromPacks(new DfsPackFile[] { one, two, three }, null);

		assertEquals(-1, calc.encode(null));
		assertNull(calc.decode(-1));
	}

	@Test
	public void voffsetcalculator_maxOffset() {
		DfsPackFile one = createDfsPackFile(800);
		DfsPackFile two = createDfsPackFile(1200);
		DfsPackFile three = createDfsPackFile(900);

		long totalSize = one.getPackDescription().getFileSize(PACK)
				+ two.getPackDescription().getFileSize(PACK)
				+ three.getPackDescription().getFileSize(PACK);

		VOffsetCalculator calc = VOffsetCalculatorNPacks
				.fromPacks(new DfsPackFile[] { one, two, three }, null);

		assertEquals(totalSize, calc.getMaxOffset());
	}

	@Test
	public void voffsetcalculator_withBase_encode() {
		DfsPackFile one = createDfsPackFile(800);
		DfsPackFile two = createDfsPackFile(1200);
		DfsPackFile three = createDfsPackFile(900);

		VOffsetCalculator baseCalc = VOffsetCalculatorNPacks
				.fromPacks(new DfsPackFile[] { one, two, three }, null);

		DfsPackFile four = createDfsPackFile(900);
		DfsPackFile five = createDfsPackFile(1300);
		DfsPackFile six = createDfsPackFile(1000);

		VOffsetCalculatorNPacks calc = VOffsetCalculatorNPacks
				.fromPacks(new DfsPackFile[] { four, five, six }, baseCalc);

		// These packIds are now from the second top midx
		int firstMidxByteSize = 2900;
		PackOffset po = PackOffset.create(0, 12);
		assertEquals(12 + firstMidxByteSize, calc.encode(po));
		po = PackOffset.create(1, 12);
		assertEquals(900 + 12 + firstMidxByteSize, calc.encode(po));
		po = PackOffset.create(2, 12);
		assertEquals(900 + 1300 + 12 + firstMidxByteSize, calc.encode(po));
	}

	@Test
	public void voffsetcalculator_withBase_decode() {
		DfsPackFile one = createDfsPackFile(800);
		DfsPackFile two = createDfsPackFile(1200);
		DfsPackFile three = createDfsPackFile(900);
		VOffsetCalculator baseCalc = VOffsetCalculatorNPacks
				.fromPacks(new DfsPackFile[] { one, two, three }, null);

		DfsPackFile four = createDfsPackFile(900);
		DfsPackFile five = createDfsPackFile(1300);
		DfsPackFile six = createDfsPackFile(1000);

		VOffsetCalculator calc = VOffsetCalculatorNPacks
				.fromPacks(new DfsPackFile[] { four, five, six }, baseCalc);

		// In pack 1
		DfsPackOffset decoded = calc.decode(130);
		assertEquals(one.getPackDescription(),
				decoded.getPack().getPackDescription());
		assertEquals(130, decoded.getPackOffset());
		assertEquals(0, decoded.getPackStart());

		// In pack 3
		decoded = calc.decode(2300);
		assertEquals(three.getPackDescription(),
				decoded.getPack().getPackDescription());
		assertEquals(300, decoded.getPackOffset());
		assertEquals(2000, decoded.getPackStart());

		// In pack 4
		decoded = calc.decode(3000);
		assertEquals(four.getPackDescription(),
				decoded.getPack().getPackDescription());
		assertEquals(100, decoded.getPackOffset());
		assertEquals(2900, decoded.getPackStart());

		// In pack 6
		int packSixStart = 800 + 1200 + 900 + 900 + 1300;
		decoded = calc.decode(packSixStart + 130);
		assertEquals(six.getPackDescription(),
				decoded.getPack().getPackDescription());
		assertEquals(130, decoded.getPackOffset());
		assertEquals(packSixStart, decoded.getPackStart());
	}

	@Test
	public void voffsetcalculator_withBase_maxOffset() {
		DfsPackFile one = createDfsPackFile(800);
		DfsPackFile two = createDfsPackFile(1200);
		DfsPackFile three = createDfsPackFile(900);

		VOffsetCalculator baseCalc = VOffsetCalculatorNPacks
				.fromPacks(new DfsPackFile[] { one, two, three }, null);

		DfsPackFile four = createDfsPackFile(900);
		DfsPackFile five = createDfsPackFile(1300);
		DfsPackFile six = createDfsPackFile(1000);

		VOffsetCalculator calc = VOffsetCalculatorNPacks
				.fromPacks(new DfsPackFile[] { four, five, six }, baseCalc);

		int expectedMaxOffset = 800 + 1200 + 900 + 900 + 1300 + 1000;
		assertEquals(expectedMaxOffset, calc.getMaxOffset());
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

	private DfsPackFileMidx writeMultipackIndexWithBitmaps()
			throws IOException {
		enableMidxBitmaps(db);
		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		return MidxTestUtils.writeMultipackIndex(db, packs,
				null);
	}

	private static void enableMidxBitmaps(DfsRepository repo) {
		repo.getConfig().setInt(CONFIG_PACK_SECTION, null,
				CONFIG_KEY_BITMAP_DISTANT_COMMIT_SPAN, 1);
	}

	private void gcWithBitmaps() throws IOException {
		DfsGarbageCollector garbageCollector = new DfsGarbageCollector(db);
		garbageCollector.pack(NullProgressMonitor.INSTANCE);
	}

	private RevCommit writePackWithCommit() throws Exception {
		try (TestRepository<InMemoryRepository> repository = new TestRepository<>(
				db)) {
			Ref ref = repository.getRepository().getRefDatabase()
					.findRef("refs/heads/main");
			RevWalk rw = repository.getRevWalk();
			RevCommit parent = ref != null ? rw.parseCommit(ref.getObjectId())
					: null;
			return repository.branch("refs/heads/main").commit().parent(parent)
					.add("blob1", "blob1").create();
		}
	}

	private ObjectId writePackWithRandomBlob(int size) throws IOException {
		byte[] data = new TestRng(JGitTestUtil.getName()).nextBytes(size);
		return writePackWithBlob(data);
	}

	private ObjectId writePackWithBlob(byte[] data) throws IOException {
		DfsInserter ins = (DfsInserter) db.newObjectInserter();
		ins.setCompressionLevel(Deflater.NO_COMPRESSION);
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
