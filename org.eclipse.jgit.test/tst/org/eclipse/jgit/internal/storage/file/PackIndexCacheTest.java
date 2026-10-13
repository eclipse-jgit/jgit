/*
 * Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.lib.BitmapIndex;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.junit.After;
import org.junit.Test;

public class PackIndexCacheTest extends GcTestCase {
	private final PackIndexCache cache = PackIndexCache.getInstance();

	@After
	public void resetLimit() {
		setLimit(0);
	}

	@Test
	public void disabledByDefault() throws Exception {
		Pack pack = insertPack("a");
		pack.getIndex();
		assertEquals(0, cache.getLimit());
		assertEquals(0, cache.getUsedBytes());
		assertEquals(0, cache.getPackCount());
	}

	@Test
	public void chargesAllIndexesOfPack() throws Exception {
		setLimit(Long.MAX_VALUE);
		RevCommit tip = commitChain(10);
		tr.update("refs/heads/main", tip);
		gc.gc().get();
		Pack pack = singlePack();
		// Drop anything gc loaded so that each index is charged on load.
		pack.close();

		pack.getIndex();
		long idxOnly = cache.getUsedBytes();
		assertEquals(pack.getPackFile().create(PackExt.INDEX).length(),
				idxOnly);

		assertNotNull(pack.getBitmapIndex());
		long objectCount = pack.getObjectCount();
		assertTrue("bitmap and reverse index charged",
				cache.getUsedBytes() > idxOnly + objectCount * 4);
		assertEquals(pack.getIndexWeight(), cache.getUsedBytes());
		assertEquals(1, cache.getPackCount());

		pack.close();
		assertEquals(0, cache.getUsedBytes());
		assertEquals(0, cache.getPackCount());
	}

	@Test
	public void evictsLeastRecentlyUsedPack() throws Exception {
		Pack p1 = insertPack("a");
		Pack p2 = insertPack("b");
		Pack p3 = insertPack("c");
		long idxSize = p1.getPackFile().create(PackExt.INDEX).length();
		setLimit(2 * idxSize);
		long evictions = cache.getEvictionCount();

		p1.getIndex();
		p2.getIndex();
		assertEquals(2 * idxSize, cache.getUsedBytes());
		assertEquals(evictions, cache.getEvictionCount());

		p3.getIndex();
		assertEquals(2 * idxSize, cache.getUsedBytes());
		assertEquals(evictions + 1, cache.getEvictionCount());
		assertEquals("oldest pack evicted", 0, p1.getIndexWeight());
		assertEquals(idxSize, p2.getIndexWeight());
		assertEquals(idxSize, p3.getIndexWeight());

		// p2 was used since the last sweep, so p3 is evicted for p1.
		p2.getIndex();
		p1.getIndex();
		assertEquals(2 * idxSize, cache.getUsedBytes());
		assertEquals(idxSize, p1.getIndexWeight());
		assertEquals(idxSize, p2.getIndexWeight());
		assertEquals(0, p3.getIndexWeight());
	}

	@Test
	public void evictedIndexesAreReloaded() throws Exception {
		List<ObjectId> blobs = new ArrayList<>();
		List<Pack> packs = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			String content = "content " + i;
			Pack p = insertPack(content);
			packs.add(p);
			blobs.add(blobId(content));
		}
		setLimit(1);
		long evictions = cache.getEvictionCount();

		for (int round = 0; round < 3; round++) {
			for (int i = 0; i < blobs.size(); i++) {
				assertTrue(packs.get(i).hasObject(blobs.get(i)));
				try (ObjectReader reader = repo.newObjectReader()) {
					assertArrayEquals(
							Constants.encode("content " + i),
							reader.open(blobs.get(i)).getCachedBytes());
				}
			}
		}
		assertTrue(cache.getEvictionCount() > evictions);
		assertTrue(cache.getPackCount() <= 1);
	}

	@Test
	public void lowerLimitEvictsImmediately() throws Exception {
		Pack p1 = insertPack("a");
		Pack p2 = insertPack("b");
		setLimit(Long.MAX_VALUE);
		p1.getIndex();
		p2.getIndex();
		assertEquals(2, cache.getPackCount());
		p1.clearIndexReferenced();
		p2.clearIndexReferenced();

		long weight = p1.getIndexWeight();
		setLimit(weight);
		assertEquals(1, cache.getPackCount());
		assertEquals(weight, cache.getUsedBytes());
		assertEquals("oldest pack evicted", 0, p1.getIndexWeight());
	}

	@Test
	public void loadingPackIsNotEvicted() throws Exception {
		Pack p1 = insertPack("a");
		Pack p2 = insertPack("b");
		setLimit(Long.MAX_VALUE);
		p1.getIndex();
		p2.getIndex();
		p1.clearIndexReferenced();
		p2.clearIndexReferenced();

		p1.beginIndexLoad();
		try {
			setLimit(p1.getIndexWeight());
		} finally {
			p1.endIndexLoad();
		}
		assertTrue("loading pack kept", p1.getIndexWeight() > 0);
		assertEquals(0, p2.getIndexWeight());
	}

	@Test
	public void bitmapBuilderMatchesReloadedBitmapIndex() throws Exception {
		RevCommit tip = commitChain(10);
		tr.update("refs/heads/main", tip);
		gc.gc().get();
		Pack pack = singlePack();
		setLimit(Long.MAX_VALUE);

		try (ObjectReader reader = repo.newObjectReader()) {
			BitmapIndex bitmaps = reader.getBitmapIndex();
			BitmapBuilder need = bitmaps.newBitmapBuilder();
			need.or(bitmaps.getBitmap(tip));
			PackBitmapIndex before = pack.getBitmapIndex();

			pack.evictIndices();
			PackBitmapIndex reloaded = pack.getBitmapIndex();
			assertNotSame(before, reloaded);
			assertTrue("pack still recognized as fully included",
					need.removeAllOrNone(reloaded));
		}
	}

	@Test
	public void indexesClearedBehindCacheAreNotCountedAsEvicted()
			throws Exception {
		Pack p1 = insertPack("a");
		Pack p2 = insertPack("b");
		Pack p3 = insertPack("c");
		long idxSize = p1.getPackFile().create(PackExt.INDEX).length();
		setLimit(2 * idxSize);
		p1.getIndex();
		p2.getIndex();
		p1.clearIndexReferenced();
		p2.clearIndexReferenced();
		// Simulate the garbage collector clearing SoftReferences of p1.
		p1.evictIndices();
		long evictions = cache.getEvictionCount();

		p3.getIndex();
		assertEquals("only the stale charge was dropped", evictions,
				cache.getEvictionCount());
		assertEquals(2 * idxSize, cache.getUsedBytes());
		assertEquals(2, cache.getPackCount());
		assertEquals(idxSize, p2.getIndexWeight());
	}

	@Test
	public void disablingStopsAccounting() throws Exception {
		Pack p1 = insertPack("a");
		setLimit(Long.MAX_VALUE);
		p1.getIndex();
		assertEquals(1, cache.getPackCount());

		setLimit(0);
		assertEquals(0, cache.getPackCount());
		assertEquals(0, cache.getUsedBytes());
		assertTrue("indexes kept when disabling", p1.getIndexWeight() > 0);
	}

	@Test
	public void concurrentReadsWithTinyLimit() throws Exception {
		RevCommit tip = commitChain(20);
		tr.update("refs/heads/main", tip);
		gc.gc().get();
		List<ObjectId> blobs = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			String content = "extra " + i;
			insertPack(content);
			blobs.add(blobId(content));
		}
		setLimit(1);
		long evictions = cache.getEvictionCount();

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
		assertTrue(cache.getEvictionCount() > evictions);
	}

	@Test
	public void readsLimitFromConfig() throws Exception {
		StoredConfig cfg = repo.getConfig();
		cfg.setString("core", null, "packedIndexGitLimit", "3m");
		WindowCacheConfig wcc = new WindowCacheConfig().fromConfig(cfg);
		assertEquals(3 * WindowCacheConfig.MB, wcc.getPackedIndexGitLimit());
		assertEquals(0, new WindowCacheConfig().getPackedIndexGitLimit());
	}

	private void setLimit(long limit) {
		WindowCacheConfig c = new WindowCacheConfig();
		c.setPackedGitLimit(128 * WindowCacheConfig.KB);
		c.setPackedGitWindowSize(8 * WindowCacheConfig.KB);
		c.setDeltaBaseCacheLimit(8 * WindowCacheConfig.KB);
		c.setPackedIndexGitLimit(limit);
		c.install();
	}

	/** Insert a blob into a new pack, without loading any pack index. */
	private Pack insertPack(String content) throws Exception {
		Set<Pack> before = new HashSet<>(repo.getObjectDatabase().getPacks());
		try (PackInserter ins = repo.getObjectDatabase().newPackInserter()) {
			ins.checkExisting(false);
			ins.insert(Constants.OBJ_BLOB, Constants.encode(content));
			ins.flush();
		}
		List<Pack> added = new ArrayList<>(repo.getObjectDatabase().getPacks());
		added.removeAll(before);
		assertEquals(1, added.size());
		return added.get(0);
	}

	private static ObjectId blobId(String content) {
		return new ObjectInserter.Formatter().idFor(Constants.OBJ_BLOB,
				Constants.encode(content));
	}

	private Pack singlePack() {
		List<Pack> packs = new ArrayList<>(
				repo.getObjectDatabase().getPacks());
		assertEquals(1, packs.size());
		return packs.get(0);
	}
}
