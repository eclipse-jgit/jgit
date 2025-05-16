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
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.Deflater;

import org.eclipse.jgit.internal.storage.file.PackIndex;
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
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.revwalk.RevCommit;
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
	public void midx_hasObject()
			throws IOException {
		ObjectId o1 = writePackWithRandomBlob(100);
		ObjectId o2 = writePackWithRandomBlob(200);
		ObjectId o3 = writePackWithRandomBlob(150);
		writeMultipackIndex();

		DfsPackFile midx = readDfsPackFileMidx();
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertTrue(midx.hasObject(ctx, o1));
			assertTrue(midx.hasObject(ctx, o2));
			assertTrue(midx.hasObject(ctx, o3));
			assertFalse(midx.hasObject(ctx, NOT_IN_PACK));
		}
	}

	@Test
	public void midx_get()
			throws IOException {
		byte[] contentOne = "ONE".getBytes(UTF_8);
		byte[] contentTwo = "TWO".getBytes(UTF_8);
		byte[] contentThree = "THREE".getBytes(UTF_8);

		ObjectId o1 = writePackWithBlob(contentOne);
		ObjectId o2 = writePackWithBlob(contentTwo);
		ObjectId o3 = writePackWithBlob(contentThree);
		writeMultipackIndex();

		DfsPackFile midx = readDfsPackFileMidx();
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			ObjectLoader objectLoader = midx.get(ctx, o1);
			assertArrayEquals(contentOne, objectLoader.getBytes());
			objectLoader = midx.get(ctx, o2);
			assertArrayEquals(contentTwo, objectLoader.getBytes());
			objectLoader = midx.get(ctx, o3);
			assertArrayEquals(contentThree, objectLoader.getBytes());
			assertNull(midx.get(ctx, NOT_IN_PACK));
		}
	}

	@Test
	public void midx_load()
			throws IOException {
		byte[] contentOne = "ONE".getBytes(UTF_8);
		byte[] contentTwo = "TWO".getBytes(UTF_8);
		byte[] contentThree = "THREE".getBytes(UTF_8);

		ObjectId o1 = writePackWithBlob(contentOne);
		ObjectId o2 = writePackWithBlob(contentTwo);
		ObjectId o3 = writePackWithBlob(contentThree);
		writeMultipackIndex();

		DfsPackFile midx = readDfsPackFileMidx();
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			ObjectLoader objectLoader = midx.load(ctx,
					midx.findOffset(ctx, o1));
			assertArrayEquals(contentOne, objectLoader.getBytes());
			objectLoader = midx.load(ctx, midx.findOffset(ctx, o2));
			assertArrayEquals(contentTwo, objectLoader.getBytes());
			objectLoader = midx.load(ctx, midx.findOffset(ctx, o3));
			assertArrayEquals(contentThree, objectLoader.getBytes());

			assertThrows(IllegalArgumentException.class,
					() -> midx.load(ctx, 500));
		}
	}

	@Test
	public void midx_findOffset()
			throws IOException {
		ObjectId o1 = writePackWithRandomBlob(100);
		ObjectId o2 = writePackWithRandomBlob(200);
		ObjectId o3 = writePackWithRandomBlob(150);
		writeMultipackIndex();

		DfsPackFile midx = readDfsPackFileMidx();
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			long posOne = midx.findOffset(ctx, o1);
			assertInRange(posOne, 0, 4);

			long posTwo = midx.findOffset(ctx, o2);
			assertInRange(posTwo, 0, 4);
			assertTrue(posTwo != posOne);

			long posThree = midx.findOffset(ctx, o3);
			assertInRange(posThree, 0, 4);
			assertTrue(posThree != posOne);
			assertTrue(posThree != posTwo);

			long posNon = midx.findOffset(ctx, NOT_IN_PACK);
			assertEquals(-1, posNon);
		}
	}

	@Test
	public void midx_resolve() throws Exception {
		ObjectId o1 = writePackWithRandomBlob(100);
		writePackWithRandomBlob(200);
		writePackWithRandomBlob(150);
		writeMultipackIndex();
		DfsPackFile midx = readDfsPackFileMidx();
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
		writeMultipackIndex();
		DfsPackFile midx = readDfsPackFileMidx();

		List<ObjectToPack> otps = List.of(new DfsObjectToPack(o1, OBJ_BLOB),
				new DfsObjectToPack(o2, OBJ_BLOB),
				new DfsObjectToPack(o3, OBJ_BLOB),
				new DfsObjectToPack(NOT_IN_PACK, OBJ_BLOB));

		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			List<DfsObjectToPack> allFromPack = midx.findAllFromPack(ctx, otps,
					true);
			assertEquals(3, allFromPack.size());

			DfsObjectToPack oneToPack = allFromPack
					.get(allFromPack.indexOf(o1));
			assertEquals(midx.findOffset(ctx, o1), oneToPack.getOffset());

			DfsObjectToPack twoToPack = allFromPack
					.get(allFromPack.indexOf(o2));
			assertEquals(midx.findOffset(ctx, o2), twoToPack.getOffset());

			DfsObjectToPack threeToPack = allFromPack
					.get(allFromPack.indexOf(o3));
			assertEquals(midx.findOffset(ctx, o3), threeToPack.getOffset());
		}
	}

	@Test
	public void midx_copyPackAsIs() throws Exception {
		writePackWithRandomBlob(100);
		writePackWithRandomBlob(200);
		writePackWithRandomBlob(150);

		long expectedPackSize = Arrays.stream(db.getObjectDatabase().getPacks())
				.mapToLong(pack -> pack.getPackDescription()
						.getFileSize(PackExt.PACK))
				.map(size -> size - 12 - 20) // remove header + CRC
				.sum();
		writeMultipackIndex();
		DfsPackFile midx = readDfsPackFileMidx();

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
	public void midx_copyAsIs() {

	}

	@Test
	public void midx_getDeltaHeader() {}

	@Test
	public void midx_getObjectType() {}

	@Test
	public void midx_getObjectSize_byId() {}

	@Test
	public void midx_getObjectSize_byOffset() {

	}

	@Test
	public void midx_objectSizeIndex_disabled() {}

	@Test
	public void midx_fillRepresentation() {
	}

	@Test
	public void midx_corrupt() {}

	private void writeMultipackIndex()
			throws IOException {
		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		Map<String, PackIndex> forMidx = new HashMap<>(packs.length);
		List<DfsPackDescription> requiredPacks = new ArrayList<>(packs.length);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			for (DfsPackFile pack : packs) {
				forMidx.put(pack.getPackDescription().getPackName(),
						pack.getPackIndex(ctx));
				requiredPacks.add(pack.getPackDescription());
			}
		}
		MultiPackIndexWriter w = new MultiPackIndexWriter();
		DfsPackDescription desc = db.getObjectDatabase().newPack(GC);
		try (DfsOutputStream out = db.getObjectDatabase().writeFile(desc,
				PackExt.MULTI_PACK_INDEX)) {
			w.write(NullProgressMonitor.INSTANCE, out, forMidx);
			desc.setRequiredPacks(requiredPacks);
			desc.addFileExt(PackExt.MULTI_PACK_INDEX);
		}
		db.getObjectDatabase().commitPack(List.of(desc), null);
		DfsPackFileMidx dfsPackFileMidx = DfsPackFileMidx.create(
				DfsBlockCache.getInstance(), desc,
				Arrays.stream(packs).toList());
		db.getObjectDatabase().addPack(dfsPackFileMidx);
	}

	private DfsPackFile readDfsPackFileMidx() throws IOException {
		Optional<DfsPackFile> midxPack = Arrays
				.stream(db.getObjectDatabase().getPacks())
				.filter(p -> p.getPackDescription()
						.hasFileExt(PackExt.MULTI_PACK_INDEX))
				.findFirst();
		DfsPackFile p = midxPack.orElseThrow();
		assertTrue(p instanceof DfsPackFileMidx);
		return p;
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

	private void assertInRange(long value, int min, int max) {
		assertTrue(value >= min);
		assertTrue(value < max);
	}
}
