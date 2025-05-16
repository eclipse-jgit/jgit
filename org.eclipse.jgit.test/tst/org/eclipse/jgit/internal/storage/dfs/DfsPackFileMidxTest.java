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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.dfs.DfsPackFileMidx.VOffsetCalculator;
import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex.PackOffset;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndexWriter;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.internal.storage.pack.PackExt;
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

public class DfsPackFileMidxTest {

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
	public void midx_findOffset() throws IOException {
		ObjectId o1 = writePackWithRandomBlob(100);
		ObjectId o2 = writePackWithRandomBlob(200);
		ObjectId o3 = writePackWithRandomBlob(150);
		DfsPackFileMidx midx = writeMultipackIndex();

		DfsPackFile packOne = findPack(o1);
		long packOneSize = packOne.getPackDescription().getFileSize(PACK);
		DfsPackFile packTwo = findPack(o2);
		long packTwoSize = packTwo.getPackDescription().getFileSize(PACK);
		DfsPackFile packThree = findPack(o3);

		// Packs have sequential names (pack-NN-INSERT) and midx uses pack-name
		// order. We rely on that to know the pack offsets
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			long posOne = midx.findOffset(ctx, o1);
			DfsPackFileMidx.DfsPackOffset po = midx.getOffsetCalculator()
					.decode(posOne);
			assertEquals(12, po.getPackOffset());
			assertEquals(0, po.getPackStart());
			assertEquals(packOne.getPackDescription(),
					po.getPack().getPackDescription());

			long posTwo = midx.findOffset(ctx, o2);
			po = midx.getOffsetCalculator().decode(posTwo);
			assertEquals(12, po.getPackOffset());
			assertEquals(packOneSize, po.getPackStart());
			assertEquals(packTwo.getPackDescription(),
					po.getPack().getPackDescription());

			long posThree = midx.findOffset(ctx, o3);
			po = midx.getOffsetCalculator().decode(posThree);
			assertEquals(12, po.getPackOffset());
			assertEquals(packOneSize + packTwoSize, po.getPackStart());
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
		writePackWithRandomBlob(150);
		DfsPackFile midx = writeMultipackIndex();

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			Set<ObjectId> matches = new HashSet<>();
			midx.resolve(ctx, matches, o1.abbreviate(6), 100);
			assertEquals(1, matches.size());

			matches = new HashSet<>();
			midx.resolve(ctx, matches, NOT_IN_PACK.abbreviate(8), 100);
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

			// Objects are in (pack, offset) order (i.e. pack)
			DfsObjectToPack oneToPack = allFromPack.get(0);
			assertEquals(midx.findOffset(ctx, o1), oneToPack.getOffset());

			DfsObjectToPack twoToPack = allFromPack.get(1);
			assertEquals(midx.findOffset(ctx, o2), twoToPack.getOffset());

			DfsObjectToPack threeToPack = allFromPack.get(2);
			assertEquals(midx.findOffset(ctx, o3), threeToPack.getOffset());
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
	public void midx_copyAsIs() throws Exception {
		writePackWithRandomBlob(100);
		ObjectId blob = writePackWithRandomBlob(200);
		writePackWithRandomBlob(150);
		DfsPackFile midx = writeMultipackIndex();

		try (DfsReader ctx = db.getObjectDatabase().newReader();
				PackWriter pw = new PackWriter(new PackConfig(), ctx);
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				PackOutputStream out = new PackOutputStream(
						NullProgressMonitor.INSTANCE, os, pw)) {
			ObjectToPack otp = new DfsObjectToPack(blob, OBJ_BLOB);
			DfsObjectToPack inPack = midx
					.findAllFromPack(ctx, List.of(otp), false).get(0);
			DfsObjectRepresentation r = new DfsObjectRepresentation(midx);
			midx.fillRepresentation(r, inPack.getOffset(), ctx);
			inPack.select(r);
			midx.copyAsIs(out, inPack, false, ctx);
			out.flush();
			assertEquals(213, os.size());
		}
	}

	@Test
	public void midx_getDeltaHeader() {
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

			long commitPos = midx.findOffset(ctx, aCommit);
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

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			ObjectToPack otp = new DfsObjectToPack(commit, OBJ_COMMIT);
			DfsObjectToPack inPack = midx
					.findAllFromPack(ctx, List.of(otp), true).get(0);
			DfsObjectRepresentation rep = new DfsObjectRepresentation(midx);
			midx.fillRepresentation(rep, inPack.getOffset(), ctx);
			assertEquals(midx, rep.pack);
			assertEquals(347, rep.offset);
			assertEquals(120, rep.length);
		}
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
	public void voffsetcalculator_encode() {
		DfsPackFile one = createDfsPackFile(800);
		DfsPackFile two = createDfsPackFile(1200);
		DfsPackFile three = createDfsPackFile(900);

		PackOffset po = new PackOffset();
		VOffsetCalculator calc = VOffsetCalculator
				.fromPacks(new DfsPackFile[] { one, two, three });

		po.setValues(0, 12);
		assertEquals(12, calc.encode(po));
		po.setValues(1, 12);
		assertEquals(800 + 12, calc.encode(po));
		po.setValues(2, 12);
		assertEquals(800 + 1200 + 12, calc.encode(po));
	}

	@Test
	public void voffsetcalculator_decode() {
		DfsPackFile one = createDfsPackFile(800);
		DfsPackFile two = createDfsPackFile(1200);
		DfsPackFile three = createDfsPackFile(900);

		VOffsetCalculator calc = VOffsetCalculator
				.fromPacks(new DfsPackFile[] { one, two, three });

		// In first pack
		DfsPackFileMidx.DfsPackOffset decoded = calc.decode(130);
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

		VOffsetCalculator calc = VOffsetCalculator
				.fromPacks(new DfsPackFile[] { one, two, three });

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

		VOffsetCalculator calc = VOffsetCalculator
				.fromPacks(new DfsPackFile[] { one, two, three });

		assertEquals(totalSize, calc.getMaxOffset());
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
		Map<String, PackIndex> forMidx = new HashMap<>(packs.length);
		Map<String, DfsPackDescription> requiredPacks = new HashMap<>(
				packs.length);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			for (DfsPackFile pack : packs) {
				forMidx.put(pack.getPackDescription().getPackName(),
						pack.getPackIndex(ctx));
				requiredPacks.put(pack.getPackDescription().getPackName(),
						pack.getPackDescription());
			}
		}
		MultiPackIndexWriter w = new MultiPackIndexWriter();
		DfsPackDescription desc = db.getObjectDatabase().newPack(GC);
		try (DfsOutputStream out = db.getObjectDatabase().writeFile(desc,
				PackExt.MULTI_PACK_INDEX)) {
			MultiPackIndexWriter.Result midxStats = w
					.write(NullProgressMonitor.INSTANCE, out, forMidx);
			desc.setCoveredPacks(midxStats.packNames().stream()
					.map(requiredPacks::get).toList());
			desc.addFileExt(PackExt.MULTI_PACK_INDEX);
		}
		db.getObjectDatabase().commitPack(List.of(desc), null);
		return DfsPackFileMidx.create(DfsBlockCache.getInstance(), desc,
				Arrays.asList(packs));
	}

	private RevCommit writePackWithCommit() throws Exception {
		try (TestRepository<InMemoryRepository> repository = new TestRepository<>(
				db)) {
			return repository.branch("/refs/heads/main").commit()
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

		}

		@Override
		public boolean isCancelled() {
			return false;
		}

		@Override
		public void showDuration(boolean enabled) {

		}
	}

	private byte[] safeGetBytes(@Nullable ObjectLoader ol) {
		assertNotNull(ol);
		byte[] data = ol.getBytes();
		assertNotNull(data);
		return data;
	}
}
