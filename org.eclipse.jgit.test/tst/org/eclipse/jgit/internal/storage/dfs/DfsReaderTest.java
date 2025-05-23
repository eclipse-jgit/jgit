/*
 * Copyright (C) 2023, Google LLC. and others
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
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_MIN_BYTES_OBJ_SIZE_INDEX;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_PACK_SECTION;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.Deflater;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource;
import org.eclipse.jgit.internal.storage.dfs.DfsReader.PackLoadListener;
import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndexWriter;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.junit.Before;
import org.junit.Test;

public class DfsReaderTest {

	private final static ObjectId UNKNOWN_OID = ObjectId
			.fromRaw(new int[] { 1, 2, 3, 4, 5 });

	InMemoryRepository db;

	@Before
	public void setUp() {
		db = new InMemoryRepository(new DfsRepositoryDescription("test"));
		// These tests assume the object size index is enabled.
		db.getObjectDatabase().getReaderOptions().setUseObjectSizeIndex(true);
	}

	@Test
	public void getObjectSize_noIndex_blob() throws IOException {
		ObjectId obj = insertBlobWithSize(100);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			long size = ctx.getObjectSize(obj, OBJ_BLOB);
			assertEquals(100, size);
		}
	}

	@Test
	public void getObjectSize_noIndex_commit() throws IOException {
		ObjectId obj = insertObjectWithSize(OBJ_COMMIT, 110);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			long size = ctx.getObjectSize(obj, OBJ_COMMIT);
			assertEquals(110, size);
		}
	}

	@Test
	public void getObjectSize_index_indexedBlob() throws IOException {
		setObjectSizeIndexMinBytes(100);
		ObjectId obj = insertBlobWithSize(200);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			long size = ctx.getObjectSize(obj, OBJ_BLOB);
			assertEquals(200, size);
		}
	}

	@Test
	public void getObjectSize_index_nonIndexedBlob() throws IOException {
		setObjectSizeIndexMinBytes(100);
		ObjectId obj = insertBlobWithSize(50);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			long size = ctx.getObjectSize(obj, OBJ_BLOB);
			assertEquals(50, size);
		}
	}

	@Test
	public void getObjectSize_index_commit() throws IOException {
		setObjectSizeIndexMinBytes(100);
		insertBlobWithSize(110);
		ObjectId obj = insertObjectWithSize(OBJ_COMMIT, 120);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			long size = ctx.getObjectSize(obj, OBJ_COMMIT);
			assertEquals(120, size);
		}
	}

	@Test
	public void midx_hasObject() throws IOException {
		ObjectId o1 = insertBlobWithSize(200);
		ObjectId o2 = insertBlobWithSize(300);
		ObjectId o3 = insertBlobWithSize(400);
		assertEquals(3, db.getObjectDatabase().getPacks().length);
		replacePacksWithMultipackIndex();
		assertEquals(1, db.getObjectDatabase().getPacks().length);

		ObjectId o4 = insertBlobWithSize(500);
		assertEquals(2, db.getObjectDatabase().getPacks().length);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertTrue(ctx.has(o1));
			assertTrue(ctx.has(o2));
			assertTrue(ctx.has(o3));
			assertTrue(ctx.has(o4));
			assertFalse(ctx.has(UNKNOWN_OID));
		}
	}

	@Test
	public void midx_copy() throws IOException {
		insertBlobWithSize(200);
		ObjectId o2 = insertBlobWithSize(300);
		insertBlobWithSize(400);
		assertEquals(3, db.getObjectDatabase().getPacks().length);
		byte[] o2rawData = readRawBytes(o2, 100);

		replacePacksWithMultipackIndex();
		assertEquals(1, db.getObjectDatabase().getPacks().length);

		byte[] midxO2rawData = readRawBytes(o2, 100);
		assertArrayEquals(o2rawData, midxO2rawData);
	}

	@Test
	public void midx_copy_multiblock() throws IOException {
		DfsBlockCache.reconfigure(new DfsBlockCacheConfig().setBlockSize(512));
		insertBlobWithSize(200);
		ObjectId o2 = insertBlobWithSize(600000);
		insertBlobWithSize(400);
		assertEquals(3, db.getObjectDatabase().getPacks().length);
		byte[] o2rawData = readRawBytes(o2, 1200);

		replacePacksWithMultipackIndex();
		resetCache();

		CountBlockLoads counter = new CountBlockLoads();
		byte[] midxO2rawData = readRawBytes(o2, 1200, counter);
		assertArrayEquals(o2rawData, midxO2rawData);
		assertEquals(3, counter.blocksLoaded.size());
	}

	@Test
	public void midx_getObjectSize() throws IOException {
		ObjectId o1 = insertBlobWithSize(100);
		ObjectId o2 = insertBlobWithSize(300);
		ObjectId o3 = insertBlobWithSize(200);

		replacePacksWithMultipackIndex();
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertEquals(100, ctx.getObjectSize(o1, OBJ_BLOB));
			// Typehint is only for error reporting or trying the objsize index
			// In this case, irrelevant
			assertEquals(100, ctx.getObjectSize(o1, OBJ_COMMIT));
			assertEquals(300, ctx.getObjectSize(o2, OBJ_BLOB));
			assertEquals(200, ctx.getObjectSize(o3, OBJ_BLOB));

			assertThrows(MissingObjectException.class,
					() -> ctx.getObjectSize(UNKNOWN_OID, OBJ_BLOB));
		}
	}

	@Test
	public void midx_open() throws IOException {
		ObjectId o1 = insertBlob("content of blob 1");
		ObjectId o2 = insertBlob("content of blob 2 x");
		ObjectId o3 = insertBlob("content of blob 3 xy");

		replacePacksWithMultipackIndex();
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			ObjectLoader ol = ctx.open(o1);
			assertNotNull(ol);
			assertEquals(17, ol.getSize());
			assertEquals("content of blob 1", new String(ol.getBytes(), UTF_8));

			ol = ctx.open(o2);
			assertNotNull(ol);
			assertEquals(19, ol.getSize());
			assertEquals("content of blob 2 x",
					new String(ol.getBytes(), UTF_8));

			ol = ctx.open(o3);
			assertNotNull(ol);
			assertEquals(20, ol.getSize());
			assertEquals("content of blob 3 xy",
					new String(ol.getBytes(), UTF_8));
		}
	}

	@Test
	public void isNotLargerThan_objAboveThreshold() throws IOException {
		setObjectSizeIndexMinBytes(100);
		ObjectId obj = insertBlobWithSize(200);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertFalse("limit < threshold < obj",
					ctx.isNotLargerThan(obj, OBJ_BLOB, 50));
			assertEquals(1, ctx.stats.isNotLargerThanCallCount);
			assertEquals(1, ctx.stats.objectSizeIndexHit);
			assertEquals(0, ctx.stats.objectSizeIndexMiss);

			assertFalse("limit = threshold < obj",
					ctx.isNotLargerThan(obj, OBJ_BLOB, 100));
			assertEquals(2, ctx.stats.isNotLargerThanCallCount);
			assertEquals(2, ctx.stats.objectSizeIndexHit);
			assertEquals(0, ctx.stats.objectSizeIndexMiss);

			assertFalse("threshold < limit < obj",
					ctx.isNotLargerThan(obj, OBJ_BLOB, 150));
			assertEquals(3, ctx.stats.isNotLargerThanCallCount);
			assertEquals(3, ctx.stats.objectSizeIndexHit);
			assertEquals(0, ctx.stats.objectSizeIndexMiss);

			assertTrue("threshold < limit = obj",
					ctx.isNotLargerThan(obj, OBJ_BLOB, 200));
			assertEquals(4, ctx.stats.isNotLargerThanCallCount);
			assertEquals(4, ctx.stats.objectSizeIndexHit);
			assertEquals(0, ctx.stats.objectSizeIndexMiss);

			assertTrue("threshold < obj < limit",
					ctx.isNotLargerThan(obj, OBJ_BLOB, 250));
			assertEquals(5, ctx.stats.isNotLargerThanCallCount);
			assertEquals(5, ctx.stats.objectSizeIndexHit);
			assertEquals(0, ctx.stats.objectSizeIndexMiss);
		}
	}

	@Test
	public void isNotLargerThan_objBelowThreshold() throws IOException {
		setObjectSizeIndexMinBytes(100);
		insertBlobWithSize(1000); // index not empty
		ObjectId obj = insertBlobWithSize(50);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertFalse("limit < obj < threshold",
					ctx.isNotLargerThan(obj, OBJ_BLOB, 10));
			assertEquals(1, ctx.stats.isNotLargerThanCallCount);
			assertEquals(0, ctx.stats.objectSizeIndexHit);
			assertEquals(1, ctx.stats.objectSizeIndexMiss);

			assertTrue("limit = obj < threshold",
					ctx.isNotLargerThan(obj, OBJ_BLOB, 50));
			assertEquals(2, ctx.stats.isNotLargerThanCallCount);
			assertEquals(0, ctx.stats.objectSizeIndexHit);
			assertEquals(2, ctx.stats.objectSizeIndexMiss);

			assertTrue("obj < limit < threshold",
					ctx.isNotLargerThan(obj, OBJ_BLOB, 80));
			assertEquals(3, ctx.stats.isNotLargerThanCallCount);
			assertEquals(0, ctx.stats.objectSizeIndexHit);
			assertEquals(3, ctx.stats.objectSizeIndexMiss);

			assertTrue("obj < limit = threshold",
					ctx.isNotLargerThan(obj, OBJ_BLOB, 100));
			assertEquals(4, ctx.stats.isNotLargerThanCallCount);
			assertEquals(0, ctx.stats.objectSizeIndexHit);
			assertEquals(4, ctx.stats.objectSizeIndexMiss);

			assertTrue("obj < threshold < limit",
					ctx.isNotLargerThan(obj, OBJ_BLOB, 120));
			assertEquals(5, ctx.stats.isNotLargerThanCallCount);
			assertEquals(0, ctx.stats.objectSizeIndexHit);
			assertEquals(5, ctx.stats.objectSizeIndexMiss);
		}
	}

	@Test
	public void isNotLargerThan_emptyIdx() throws IOException {
		setObjectSizeIndexMinBytes(100);
		ObjectId obj = insertBlobWithSize(10);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertFalse(ctx.isNotLargerThan(obj, OBJ_BLOB, 0));
			assertTrue(ctx.isNotLargerThan(obj, OBJ_BLOB, 10));
			assertTrue(ctx.isNotLargerThan(obj, OBJ_BLOB, 40));
			assertTrue(ctx.isNotLargerThan(obj, OBJ_BLOB, 50));
			assertTrue(ctx.isNotLargerThan(obj, OBJ_BLOB, 100));

			assertEquals(5, ctx.stats.isNotLargerThanCallCount);
			assertEquals(5, ctx.stats.objectSizeIndexMiss);
			assertEquals(0, ctx.stats.objectSizeIndexHit);
		}
	}

	@Test
	public void isNotLargerThan_noObjectSizeIndex() throws IOException {
		setObjectSizeIndexMinBytes(-1);
		ObjectId obj = insertBlobWithSize(10);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertFalse(ctx.isNotLargerThan(obj, OBJ_BLOB, 0));
			assertTrue(ctx.isNotLargerThan(obj, OBJ_BLOB, 10));
			assertTrue(ctx.isNotLargerThan(obj, OBJ_BLOB, 40));
			assertTrue(ctx.isNotLargerThan(obj, OBJ_BLOB, 50));
			assertTrue(ctx.isNotLargerThan(obj, OBJ_BLOB, 100));

			assertEquals(5, ctx.stats.isNotLargerThanCallCount);
			assertEquals(0, ctx.stats.objectSizeIndexMiss);
			assertEquals(0, ctx.stats.objectSizeIndexHit);
		}
	}

	@Test
	public void packLoadListener_noInvocations() throws IOException {
		insertBlobWithSize(100);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			CounterPackLoadListener listener = new CounterPackLoadListener();
			ctx.addPackLoadListener(listener);
			assertEquals(null, listener.callsPerExt.get(PackExt.INDEX));
		}
	}

	@Test
	public void packLoadListener_has_openIdx() throws IOException {
		ObjectId obj = insertBlobWithSize(100);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			CounterPackLoadListener listener = new CounterPackLoadListener();
			ctx.addPackLoadListener(listener);
			boolean has = ctx.has(obj);
			assertTrue(has);
			assertEquals(Integer.valueOf(1),
					listener.callsPerExt.get(PackExt.INDEX));
		}
	}

	@Test
	public void packLoadListener_notLargerThan_openMultipleIndices()
			throws IOException {
		setObjectSizeIndexMinBytes(100);
		ObjectId obj = insertBlobWithSize(200);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			CounterPackLoadListener listener = new CounterPackLoadListener();
			ctx.addPackLoadListener(listener);
			boolean notLargerThan = ctx.isNotLargerThan(obj, OBJ_BLOB, 1000);
			assertTrue(notLargerThan);
			assertEquals(Integer.valueOf(1),
					listener.callsPerExt.get(PackExt.INDEX));
			assertEquals(Integer.valueOf(1),
					listener.callsPerExt.get(PackExt.OBJECT_SIZE_INDEX));
		}
	}

	@Test
	public void packLoadListener_has_openMultipleIndices() throws IOException {
		setObjectSizeIndexMinBytes(100);
		insertBlobWithSize(200);
		insertBlobWithSize(230);
		insertBlobWithSize(100);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			CounterPackLoadListener listener = new CounterPackLoadListener();
			ctx.addPackLoadListener(listener);
			ObjectId oid = ObjectId
					.fromString("aa48de2aa61d9dffa8a05439dc115fe82f10f129");
			boolean has = ctx.has(oid);
			assertFalse(has);
			// Open 3 indices trying to find the pack
			assertEquals(Integer.valueOf(3),
					listener.callsPerExt.get(PackExt.INDEX));
		}
	}

	@Test
	public void packLoadListener_has_repeatedCalls_openMultipleIndices()
			throws IOException {
		// Two objects NOT in the repo
		ObjectId oid = ObjectId
				.fromString("aa48de2aa61d9dffa8a05439dc115fe82f10f129");
		ObjectId oid2 = ObjectId
				.fromString("aa48de2aa61d9dffa8a05439dc115fe82f10f130");

		setObjectSizeIndexMinBytes(100);
		insertBlobWithSize(200);
		insertBlobWithSize(230);
		insertBlobWithSize(100);
		CounterPackLoadListener listener = new CounterPackLoadListener();
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			ctx.addPackLoadListener(listener);
			boolean has = ctx.has(oid);
			ctx.has(oid);
			ctx.has(oid2);
			assertFalse(has);
			// The 3 indices were loaded only once each
			assertEquals(Integer.valueOf(3),
					listener.callsPerExt.get(PackExt.INDEX));
		}
	}

	private static class CounterPackLoadListener implements PackLoadListener {
		final Map<PackExt, Integer> callsPerExt = new HashMap<>();

		@SuppressWarnings("boxing")
		@Override
		public void onIndexLoad(String packName, PackSource src, PackExt ext,
				long size, Object loadedIdx) {
			callsPerExt.merge(ext, 1, Integer::sum);
		}

		@Override
		public void onBlockLoad(String packName, PackSource src, PackExt ext,
				long size, DfsBlockData dfsBlockData) {
			// empty
		}
	}

	private ObjectId insertBlobWithSize(int size) throws IOException {
		return insertObjectWithSize(OBJ_BLOB, size);
	}

	private ObjectId insertBlob(String content) throws IOException {
		return insertObject(OBJ_BLOB, content.getBytes(UTF_8));
	}

	private ObjectId insertObjectWithSize(int object_type, int size)
			throws IOException {
		TestRng testRng = new TestRng(JGitTestUtil.getName());
		return insertObject(object_type, testRng.nextBytes(size));
	}

	private ObjectId insertObject(int object_type, byte[] data)
			throws IOException {
		ObjectId oid;
		try (ObjectInserter ins = db.newObjectInserter()) {
			((DfsInserter) ins).setCompressionLevel(Deflater.NO_COMPRESSION);
			oid = ins.insert(object_type, data);
			ins.flush();
		}
		return oid;
	}

	private void setObjectSizeIndexMinBytes(int threshold) {
		db.getConfig().setInt(CONFIG_PACK_SECTION, null,
				CONFIG_KEY_MIN_BYTES_OBJ_SIZE_INDEX, threshold);
	}

	private void replacePacksWithMultipackIndex() throws IOException {
		DfsPackFile[] packs = db.getObjectDatabase().getPacks();
		Map<String, DfsPackDescription> packsByName = new HashMap<>();
		Map<String, PackIndex> forMidx = new HashMap<>(packs.length);
		List<DfsPackDescription> requiredPacks = new ArrayList<>(packs.length);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			for (DfsPackFile pack : packs) {
				forMidx.put(pack.getPackDescription().getPackName(),
						pack.getPackIndex(ctx));
				requiredPacks.add(pack.getPackDescription());
				packsByName.put(pack.getPackDescription().getPackName(),
						pack.getPackDescription());
			}
		}

		MultiPackIndexWriter w = new MultiPackIndexWriter();
		DfsPackDescription desc = db.getObjectDatabase().newPack(GC);
		try (DfsOutputStream out = db.getObjectDatabase().writeFile(desc,
				PackExt.MULTI_PACK_INDEX)) {
			MultiPackIndexWriter.Result stats = w
					.write(NullProgressMonitor.INSTANCE, out, forMidx);
			desc.setCoveredPacks(
					stats.packNames().stream().map(packsByName::get)
							.collect(Collectors.toUnmodifiableList()));
			desc.addFileExt(PackExt.MULTI_PACK_INDEX);
		}
		db.getObjectDatabase().commitPack(List.of(desc), requiredPacks);
		assertEquals(1, db.getObjectDatabase().getPacks().length);
	}

	private byte[] readRawBytes(ObjectId oid, int len) throws IOException {
		return readRawBytes(oid, len, null);
	}

	private byte[] readRawBytes(ObjectId oid, int len,
			@Nullable PackLoadListener l) throws IOException {
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			if (l != null) {
				ctx.addPackLoadListener(l);
			}
			for (DfsPackFile pack : db.getObjectDatabase().getPacks()) {
				if (!pack.hasObject(ctx, oid)) {
					continue;
				}
				byte[] dest = new byte[len];
				long pos = pack.findOffset(ctx, oid);
				ctx.copy(pack, pos, dest, 0, len);
				return dest;
			}
		}
		return null;
	}

	private void resetCache() {
		DfsBlockCache.reconfigure(new DfsBlockCacheConfig());
	}

	private static class CountBlockLoads implements PackLoadListener {

		private Set<DfsBlockData> blocksLoaded = new HashSet<>();

		@Override
		public void onIndexLoad(String packName, PackSource src, PackExt ext,
				long size, Object loadedIdx) {

		}

		@Override
		public void onBlockLoad(String packName, PackSource src, PackExt ext,
				long position, DfsBlockData dfsBlockData) {
			blocksLoaded.add(dfsBlockData);
		}
	}
}
