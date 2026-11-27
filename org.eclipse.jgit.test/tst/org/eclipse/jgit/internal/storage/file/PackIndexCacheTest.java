/*
 * Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.eclipse.jgit.internal.storage.pack.PackExt.BITMAP_INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.INDEX;
import static org.eclipse.jgit.internal.storage.pack.PackExt.REVERSE_INDEX;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheTable.BlockCacheStats;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.junit.After;
import org.junit.Test;

public class PackIndexCacheTest extends GcTestCase {
	@After
	public void resetLimit() {
		setLimit(0);
	}

	@Test
	public void disabledByDefault() throws Exception {
		Pack pack = insertPack(10);
		assertNull(PackIndexCache.getTable());
		PackIndex idx = pack.getIndex();
		assertSame(idx, pack.getIndex());
	}

	@Test
	public void holdsAllIndexesOfPack() throws Exception {
		RevCommit tip = commitChain(10);
		tr.update("refs/heads/main", tip);
		gc.gc().get();
		setLimit(Long.MAX_VALUE);
		Pack pack = singlePack();
		// Drop the indexes gc loaded, before there was a limit.
		pack.close();

		assertNotNull(pack.getBitmapIndex());

		assertEquals(pack.getIndex().getMemorySize(), currentSize(INDEX));
		assertEquals(pack.getObjectCount() * 8, currentSize(REVERSE_INDEX));
		assertEquals(pack.getPackFile().create(BITMAP_INDEX).length(),
				currentSize(BITMAP_INDEX));
	}

	@Test
	public void dropsIndexesNotUsedRecently() throws Exception {
		Pack p1 = insertPack(20);
		Pack p2 = insertPack(20);
		Pack p3 = insertPack(20);
		long size = p1.getIndex().getMemorySize();
		p1.close();
		setLimit(2 * size);

		PackIndex idx1 = p1.getIndex();
		PackIndex idx2 = p2.getIndex();
		PackIndex idx3 = p3.getIndex();

		assertEquals(2 * size, currentSize(INDEX));
		assertEquals(1, evictions(INDEX));
		assertSame(idx2, p2.getIndex());
		assertSame(idx3, p3.getIndex());
		assertNotSame("oldest index dropped", idx1, p1.getIndex());
	}

	@Test
	public void droppedIndexesAreReloaded() throws Exception {
		List<ObjectId> blobs = new ArrayList<>();
		List<Pack> packs = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			packs.add(insertPack(20));
			blobs.add(blobId(content(i, 0)));
		}
		setLimit(1);

		for (int round = 0; round < 3; round++) {
			for (int i = 0; i < blobs.size(); i++) {
				assertTrue(packs.get(i).hasObject(blobs.get(i)));
				try (ObjectReader reader = repo.newObjectReader()) {
					assertArrayEquals(Constants.encode(content(i, 0)),
							reader.open(blobs.get(i)).getCachedBytes());
				}
			}
		}
		assertTrue(evictions(INDEX) > 0);
	}

	@Test
	public void concurrentReadsWithTinyLimit() throws Exception {
		RevCommit tip = commitChain(20);
		tr.update("refs/heads/main", tip);
		gc.gc().get();
		List<ObjectId> blobs = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			insertPack(20);
			blobs.add(blobId(content(i, 0)));
		}
		setLimit(1);

		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Future<?>> results = new ArrayList<>();
			for (int t = 0; t < 8; t++) {
				results.add(pool.submit(() -> {
					for (int round = 0; round < 50; round++) {
						try (ObjectReader reader = repo.newObjectReader()) {
							for (ObjectId id : blobs) {
								reader.open(id).getCachedBytes();
							}
							reader.open(tip).getCachedBytes();
							for (Pack p : repo.getObjectDatabase()
									.getPacks()) {
								p.getBitmapIndex();
							}
						}
					}
					return null;
				}));
			}
			for (Future<?> f : results) {
				f.get(60, TimeUnit.SECONDS);
			}
		} finally {
			pool.shutdownNow();
		}
		assertTrue(evictions(INDEX) > 0);
	}

	@Test
	public void sameLimitKeepsTable() throws Exception {
		setLimit(WindowCacheConfig.MB);
		Pack pack = insertPack(10);
		PackIndex idx = pack.getIndex();

		setLimit(WindowCacheConfig.MB);

		assertEquals(idx.getMemorySize(), currentSize(INDEX));
		assertSame(idx, pack.getIndex());
	}

	@Test
	public void readsLimitFromConfig() throws Exception {
		StoredConfig cfg = repo.getConfig();
		cfg.setString("core", null, "packedIndexGitLimit", "3m");
		WindowCacheConfig wcc = new WindowCacheConfig().fromConfig(cfg);
		assertEquals(3 * WindowCacheConfig.MB, wcc.getPackedIndexGitLimit());
		assertEquals(0, new WindowCacheConfig().getPackedIndexGitLimit());
	}

	static void setLimit(long limit) {
		WindowCacheConfig c = new WindowCacheConfig();
		c.setPackedGitLimit(128 * WindowCacheConfig.KB);
		c.setPackedGitWindowSize(8 * WindowCacheConfig.KB);
		c.setDeltaBaseCacheLimit(8 * WindowCacheConfig.KB);
		c.setPackedIndexGitLimit(limit);
		c.install();
	}

	static long currentSize(PackExt ext) {
		return stats().getCurrentSize()[ext.getPosition()];
	}

	static long evictions(PackExt ext) {
		return stats().getEvictions()[ext.getPosition()];
	}

	private static BlockCacheStats stats() {
		return PackIndexCache.getTable().getBlockCacheStats().get(0);
	}

	private int packCount;

	/** Insert blobs into a new pack, without loading any pack index. */
	private Pack insertPack(int blobs) throws Exception {
		int n = packCount++;
		Set<Pack> before = new HashSet<>(repo.getObjectDatabase().getPacks());
		try (PackInserter ins = repo.getObjectDatabase().newPackInserter()) {
			ins.checkExisting(false);
			for (int i = 0; i < blobs; i++) {
				ins.insert(Constants.OBJ_BLOB,
						Constants.encode(content(n, i)));
			}
			ins.flush();
		}
		List<Pack> added = new ArrayList<>(repo.getObjectDatabase().getPacks());
		added.removeAll(before);
		assertEquals(1, added.size());
		return added.get(0);
	}

	private static String content(int pack, int blob) {
		return "pack " + pack + " blob " + blob;
	}

	private static ObjectId blobId(String content) {
		return new ObjectInserter.Formatter().idFor(Constants.OBJ_BLOB,
				Constants.encode(content));
	}
}
