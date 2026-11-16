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

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_CORE_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_DFS_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_MIN_BYTES_OBJ_SIZE_INDEX;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_OPTIMIZE_SELECT_REPRESENTATION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_PACK_SECTION;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource;
import org.eclipse.jgit.internal.storage.dfs.DfsReader.PackLoadListener;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.junit.Before;
import org.junit.Test;

public class DfsReaderTest {
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

	@Test
	public void readerOptions_optimizeSelectRepresentation_fromConfig() {
		DfsReaderOptions opts = new DfsReaderOptions();
		assertFalse(opts.shouldOptimizeSelectRepresentation());

		Config cfg = new Config();
		cfg.setBoolean(CONFIG_CORE_SECTION, CONFIG_DFS_SECTION,
				CONFIG_KEY_OPTIMIZE_SELECT_REPRESENTATION, true);
		opts.fromConfig(cfg);
		assertTrue(opts.shouldOptimizeSelectRepresentation());
	}

	@Test
	public void selectObjectRepresentation_deltaInCompactPack_shortCircuitsSubsequentPacks()
			throws IOException {
		byte[] baseBytes = new TestRng(JGitTestUtil.getName()).nextBytes(520);
		byte[] deltaBytes = Arrays.copyOf(baseBytes, 512);

		ObjectId baseId;
		ObjectId deltaId;
		ByteArrayOutputStream packOut = new ByteArrayOutputStream();
		try (InMemoryRepository srcRepo = new InMemoryRepository(
				new DfsRepositoryDescription("src"))) {
			try (ObjectInserter ins = srcRepo.newObjectInserter()) {
				baseId = ins.insert(OBJ_BLOB, baseBytes);
				deltaId = ins.insert(OBJ_BLOB, deltaBytes);
				ins.flush();
			}
			try (DfsReader srcCtx = srcRepo.getObjectDatabase().newReader();
					PackWriter pw = new PackWriter(new PackConfig(srcRepo),
							srcCtx)) {
				pw.preparePack(NullProgressMonitor.INSTANCE,
						Set.of(baseId, deltaId), Set.of());
				pw.writePack(NullProgressMonitor.INSTANCE,
						NullProgressMonitor.INSTANCE, packOut);
				assertEquals(1, pw.getStatistics().getTotalDeltas());
			}
		}

		try (ObjectInserter ins = db.newObjectInserter()) {
			ins.newPackParser(new ByteArrayInputStream(packOut.toByteArray()))
					.parse(NullProgressMonitor.INSTANCE);
			ins.flush();
		}

		DfsObjDatabase odb = db.getObjectDatabase();
		DfsPackCompactor compactor = new DfsPackCompactor(db);
		compactor.add(odb.getPacks()[0]);
		compactor.compact(NullProgressMonitor.INSTANCE);
		assertEquals(1,
				compactor.getNewPackStatistics().get(0).getTotalDeltas());

		// Add a newer INSERT pack after the COMPACT pack.
		insertBlobWithSize(150);

		RevBlob baseBlob;
		try (RevWalk rw = new RevWalk(db)) {
			baseBlob = rw.lookupBlob(baseId);
		}

		DfsBlockCache.reconfigure(new DfsBlockCacheConfig());
		odb.clearCache();
		assertEquals(2, odb.getPacks().length);
		odb.getReaderOptions().setOptimizeSelectRepresentation(true);
		try (DfsReader ctx = odb.newReader();
				PackWriter pw = new PackWriter(new PackConfig(db), ctx)) {
			pw.addObject(baseBlob);
			CounterPackLoadListener listener = new CounterPackLoadListener();
			ctx.addPackLoadListener(listener);
			DfsObjectToPack deltaOtp = ctx.newObjectToPack(deltaId, OBJ_BLOB);
			ctx.selectObjectRepresentation(pw, NullProgressMonitor.INSTANCE,
					Collections.singletonList(deltaOtp));
			assertSame(PackSource.COMPACT,
					deltaOtp.pack.getPackDescription().getPackSource());
			assertTrue(deltaOtp.isDeltaRepresentation());
			assertTrue(deltaOtp.isSatisfied());
			// COMPACT sorts before INSERT and satisfies deltaId via delta
			// reuse, skipping the INSERT pack index.
			assertEquals(Integer.valueOf(1),
					listener.callsPerExt.get(PackExt.INDEX));
		}
	}

	@Test
	public void selectObjectRepresentation_missingFromNormalPacks_checksGarbagePack()
			throws IOException {
		ObjectId reachableId = insertBlobWithSize(200);
		RefUpdate ru = db.updateRef("refs/heads/master");
		ru.setNewObjectId(reachableId);
		ru.forceUpdate();

		ObjectId unreachableId = insertBlobWithSize(180);

		DfsGarbageCollector gc = new DfsGarbageCollector(db);
		assertTrue(gc.pack(NullProgressMonitor.INSTANCE));

		DfsObjDatabase odb = db.getObjectDatabase();
		odb.clearCache();
		odb.getReaderOptions().setOptimizeSelectRepresentation(true);
		try (DfsReader ctx = odb.newReader();
				PackWriter pw = new PackWriter(new PackConfig(db), ctx)) {
			DfsObjectToPack reachableOtp = ctx.newObjectToPack(reachableId,
					OBJ_BLOB);
			DfsObjectToPack unreachableOtp = ctx.newObjectToPack(unreachableId,
					OBJ_BLOB);
			ctx.selectObjectRepresentation(pw, NullProgressMonitor.INSTANCE,
					List.of(reachableOtp, unreachableOtp));
			assertSame(PackSource.GC,
					reachableOtp.pack.getPackDescription().getPackSource());
			assertTrue(reachableOtp.isSatisfied());
			assertSame(PackSource.UNREACHABLE_GARBAGE,
					unreachableOtp.pack.getPackDescription().getPackSource());
			assertTrue(unreachableOtp.isFound());
		}
	}

	@Test
	public void selectObjectRepresentation_gcWholeObject_shortCircuitsSubsequentPacks()
			throws IOException {
		ObjectId oid = insertBlobWithSize(200);
		RefUpdate ru = db.updateRef("refs/heads/master");
		ru.setNewObjectId(oid);
		ru.forceUpdate();

		DfsGarbageCollector gc = new DfsGarbageCollector(db);
		gc.setGarbageTtl(0, TimeUnit.MILLISECONDS);
		assertTrue(gc.pack(NullProgressMonitor.INSTANCE));

		// Insert another blob in a new INSERT pack after GC.
		insertBlobWithSize(150);

		DfsObjDatabase odb = db.getObjectDatabase();
		odb.clearCache();
		assertEquals(2, odb.getPacks().length);

		odb.getReaderOptions().setOptimizeSelectRepresentation(true);
		try (DfsReader ctx = odb.newReader();
				PackWriter pw = new PackWriter(new PackConfig(db), ctx)) {
			CounterPackLoadListener listener = new CounterPackLoadListener();
			ctx.addPackLoadListener(listener);
			DfsObjectToPack otp = ctx.newObjectToPack(oid, OBJ_BLOB);
			ctx.selectObjectRepresentation(pw, NullProgressMonitor.INSTANCE,
					Collections.singletonList(otp));
			assertSame(PackSource.GC,
					otp.pack.getPackDescription().getPackSource());
			assertTrue(otp.isSatisfied());
			// Only the GC pack index should be loaded; the INSERT pack is
			// skipped.
			assertEquals(Integer.valueOf(1),
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

	private ObjectId insertObjectWithSize(int object_type, int size)
			throws IOException {
		TestRng testRng = new TestRng(JGitTestUtil.getName());
		ObjectId oid;
		try (ObjectInserter ins = db.newObjectInserter()) {
			oid = ins.insert(object_type, testRng.nextBytes(size));
			ins.flush();
		}
		return oid;
	}

	private void setObjectSizeIndexMinBytes(int threshold) {
		db.getConfig().setInt(CONFIG_PACK_SECTION, null,
				CONFIG_KEY_MIN_BYTES_OBJ_SIZE_INDEX, threshold);
	}
}
