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

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_MIN_BYTES_OBJ_SIZE_INDEX;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_PACK_SECTION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Deflater;

import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource;
import org.eclipse.jgit.internal.storage.dfs.DfsReader.PackLoadListener;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
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

		DfsReader reader = db.getObjectDatabase().newReader();
		DfsPackFile pack = db.getObjectDatabase().getPacks()[0];
		assertTrue(pack.hasObjectSizeIndex(reader));
		assertEquals(800, pack.getIndexedObjectSize(reader, blobId));
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
				PackWriter pw = new PackWriter(ctx);
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
}
