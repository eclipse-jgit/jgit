/*
 * Copyright (C) 2019, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_COMMIT_GRAPH_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_MIN_BYTES_OBJ_SIZE_INDEX;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_READ_CHANGED_PATHS;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_PACK_SECTION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Deflater;

import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource;
import org.eclipse.jgit.internal.storage.dfs.DfsReader.PackLoadListener;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndex;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Before;
import org.junit.Test;

public class DfsPackFileTest {
	InMemoryRepository db;
	boolean bypassCache;
	boolean clearCache;

	@Before
	public void setUp() {
		db = new InMemoryRepository(new DfsRepositoryDescription("test"));
	}

	@Test
	public void testCopyPackBypassCachesSmallCached() throws IOException {
		bypassCache = true;
		clearCache = false;
		setupPack(512, 256);
		assertPackSize();
	}

	@Test
	public void testCopyPackBypassCacheSmallNoCache() throws IOException {
		bypassCache = true;
		clearCache = true;
		setupPack(512, 256);
		assertPackSize();
	}

	@Test
	public void testCopyPackBypassCacheLargeCached() throws IOException {
		bypassCache = true;
		clearCache = false;
		setupPack(512, 8000);
		assertPackSize();
	}

	@Test
	public void testCopyPackBypassCacheLargeNoCache() throws IOException {
		bypassCache = true;
		clearCache = true;
		setupPack(512, 8000);
		assertPackSize();
	}

	@Test
	public void testCopyPackThroughCacheSmallCached() throws IOException {
		bypassCache = false;
		clearCache = false;
		setupPack(512, 256);
		assertPackSize();
	}

	@Test
	public void testCopyPackThroughCacheSmallNoCache() throws IOException {
		bypassCache = false;
		clearCache = true;
		setupPack(512, 256);
		assertPackSize();
	}

	@Test
	public void testCopyPackThroughCacheLargeCached() throws IOException {
		bypassCache = false;
		clearCache = false;
		setupPack(512, 8000);
		assertPackSize();
	}

	@Test
	public void testCopyPackThroughCacheLargeNoCache() throws IOException {
		bypassCache = false;
		clearCache = true;
		setupPack(512, 8000);
		assertPackSize();
	}

	@Test
	public void testLoadObjectSizeIndex() throws IOException {
		bypassCache = false;
		clearCache = true;
		setObjectSizeIndexMinBytes(0);
		ObjectId blobId = setupPack(512, 800);

		db.getObjectDatabase().getReaderOptions().setUseObjectSizeIndex(true);
		DfsReader reader = db.getObjectDatabase().newReader();
		DfsPackFile pack = db.getObjectDatabase().getPacks()[0];
		assertTrue(pack.hasObjectSizeIndex(reader));
		assertEquals(800, pack.getIndexedObjectSize(reader, blobId));
	}

	@Test
	public void testGetBitmapIndex() throws IOException {
		bypassCache = false;
		clearCache = true;
		ObjectId objectId = setupPack(512, 800);

		// Add a ref for GC
		BatchRefUpdate batchRefUpdate = db.getRefDatabase().newBatchUpdate();
		batchRefUpdate.addCommand(new ReceiveCommand(ObjectId.zeroId(),
				objectId, "refs/heads/master"));
		try (RevWalk rw = new RevWalk(db)) {
			batchRefUpdate.execute(rw, NullProgressMonitor.INSTANCE);
		}
		DfsGarbageCollector gc = new DfsGarbageCollector(db);
		gc.pack(NullProgressMonitor.INSTANCE);

		DfsReader reader = db.getObjectDatabase().newReader();
		PackBitmapIndex bitmapIndex = db.getObjectDatabase().getPacks()[0]
				.getBitmapIndex(reader);
		assertNotNull(bitmapIndex);
		assertEquals(1, bitmapIndex.getObjectCount());
	}

	@Test
	public void testGetBitmapIndex_noBitmaps() throws IOException {
		bypassCache = false;
		clearCache = true;
		setupPack(512, 800);

		DfsReader reader = db.getObjectDatabase().newReader();
		PackBitmapIndex bitmapIndex = db.getObjectDatabase().getPacks()[0]
				.getBitmapIndex(reader);
		assertNull(bitmapIndex);
	}

	@Test
	public void testLoadObjectSizeIndex_noIndex() throws IOException {
		bypassCache = false;
		clearCache = true;
		setObjectSizeIndexMinBytes(-1);
		setupPack(512, 800);

		DfsReader reader = db.getObjectDatabase().newReader();
		DfsPackFile pack = db.getObjectDatabase().getPacks()[0];
		assertFalse(pack.hasObjectSizeIndex(reader));
	}

	private static class TestPackLoadListener implements PackLoadListener {
		final Map<PackExt, Integer> indexLoadCount = new HashMap<>();

		int blockLoadCount;

		@SuppressWarnings("boxing")
		@Override
		public void onIndexLoad(String packName, PackSource src, PackExt ext,
				long size, Object loadedIdx) {
			indexLoadCount.merge(ext, 1, Integer::sum);
		}

		@Override
		public void onBlockLoad(String packName, PackSource src, PackExt ext, long position,
				DfsBlockData dfsBlockData) {
			blockLoadCount += 1;
		}
	}

	@Test
	public void testIndexLoadCallback_indexNotInCache() throws IOException {
		bypassCache = false;
		clearCache = true;
		setObjectSizeIndexMinBytes(-1);
		setupPack(512, 800);

		TestPackLoadListener tal = new TestPackLoadListener();
		DfsReader reader = db.getObjectDatabase().newReader();
		reader.addPackLoadListener(tal);
		DfsPackFile pack = db.getObjectDatabase().getPacks()[0];
		pack.getPackIndex(reader);

		assertEquals(1, tal.indexLoadCount.get(PackExt.INDEX).intValue());
	}

	@Test
	public void testIndexLoadCallback_indexInCache() throws IOException {
		bypassCache = false;
		clearCache = false;
		setObjectSizeIndexMinBytes(-1);
		setupPack(512, 800);

		TestPackLoadListener tal = new TestPackLoadListener();
		DfsReader reader = db.getObjectDatabase().newReader();
		reader.addPackLoadListener(tal);
		DfsPackFile pack = db.getObjectDatabase().getPacks()[0];
		pack.getPackIndex(reader);
		pack.getPackIndex(reader);
		pack.getPackIndex(reader);

		assertEquals(1, tal.indexLoadCount.get(PackExt.INDEX).intValue());
	}

	@Test
	public void testIndexLoadCallback_multipleReads() throws IOException {
		bypassCache = false;
		clearCache = true;
		setObjectSizeIndexMinBytes(-1);
		setupPack(512, 800);

		TestPackLoadListener tal = new TestPackLoadListener();
		DfsReader reader = db.getObjectDatabase().newReader();
		reader.addPackLoadListener(tal);
		DfsPackFile pack = db.getObjectDatabase().getPacks()[0];
		pack.getPackIndex(reader);
		pack.getPackIndex(reader);
		pack.getPackIndex(reader);

		assertEquals(1, tal.indexLoadCount.get(PackExt.INDEX).intValue());
	}


	@Test
	public void testBlockLoadCallback_loadInCache() throws IOException {
		bypassCache = false;
		clearCache = true;
		setObjectSizeIndexMinBytes(-1);
		setupPack(512, 800);

		TestPackLoadListener tal = new TestPackLoadListener();
		DfsReader reader = db.getObjectDatabase().newReader();
		reader.addPackLoadListener(tal);
		DfsPackFile pack = db.getObjectDatabase().getPacks()[0];
		ObjectId anObject = pack.getPackIndex(reader).getObjectId(0);
		pack.get(reader, anObject).getBytes();
		assertEquals(2, tal.blockLoadCount);
	}

	@Test
	public void testExistenceOfBloomFilterAlongWithCommitGraph()
			throws Exception {
		try (TestRepository<InMemoryRepository> repository = new TestRepository<>(
				db)) {
			repository.branch("/refs/heads/main").commit().add("blob1", "blob1")
					.create();
		}
		setReadChangedPaths(true);
		DfsGarbageCollector gc = new DfsGarbageCollector(db);
		gc.setWriteCommitGraph(true).setWriteBloomFilter(true)
				.pack(NullProgressMonitor.INSTANCE);

		DfsReader reader = db.getObjectDatabase().newReader();
		CommitGraph cg = db.getObjectDatabase().getPacks()[0]
				.getCommitGraph(reader);
		assertNotNull(cg);
		assertEquals(1, cg.getCommitCnt());
		assertNotNull(cg.getChangedPathFilter(0));
	}

	private ObjectId setupPack(int bs, int ps) throws IOException {
		DfsBlockCacheConfig cfg = new DfsBlockCacheConfig().setBlockSize(bs)
				.setBlockLimit(bs * 100).setStreamRatio(bypassCache ? 0F : 1F);
		DfsBlockCache.reconfigure(cfg);

		byte[] data = new TestRng(JGitTestUtil.getName()).nextBytes(ps);
		DfsInserter ins = (DfsInserter) db.newObjectInserter();
		ins.setCompressionLevel(Deflater.NO_COMPRESSION);
		ObjectId blobId = ins.insert(Constants.OBJ_BLOB, data);
		ins.flush();

		if (clearCache) {
			DfsBlockCache.reconfigure(cfg);
			db.getObjectDatabase().clearCache();
		}
		return blobId;
	}

	private void assertPackSize() throws IOException {
		try (DfsReader ctx = db.getObjectDatabase().newReader();
		     PackWriter pw = new PackWriter(new PackConfig(), ctx);
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				PackOutputStream out = new PackOutputStream(
						NullProgressMonitor.INSTANCE, os, pw)) {
			DfsPackFile pack = db.getObjectDatabase().getPacks()[0];
			long packSize = pack.getPackDescription().getFileSize(PackExt.PACK);
			pack.copyPackAsIs(out, ctx);
			assertEquals(packSize - (12 + 20), os.size());
		}
	}

	private void setObjectSizeIndexMinBytes(int threshold) {
		db.getConfig().setInt(CONFIG_PACK_SECTION, null,
				CONFIG_KEY_MIN_BYTES_OBJ_SIZE_INDEX, threshold);
	}

	private void setReadChangedPaths(boolean enable) {
		db.getConfig().setBoolean(CONFIG_COMMIT_GRAPH_SECTION, null,
				CONFIG_KEY_READ_CHANGED_PATHS, enable);
	}
}
