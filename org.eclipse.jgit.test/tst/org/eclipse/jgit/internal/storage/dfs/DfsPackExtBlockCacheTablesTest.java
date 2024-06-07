/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertArrayEquals;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.internal.storage.dfs.DfsBlockCache.ReadableChannelSupplier;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCache.Ref;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCache.RefLoader;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheTable.DfsBlockCacheStats;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.junit.Test;

public class DfsPackExtBlockCacheTablesTest {
	@Test
	public void getTableWithDfsStreamKeyExt() {
		DfsStreamKey packStreamKey = new TestKey(PackExt.PACK);
		DfsBlockCacheTable blockCacheTable = new TestCache();

		DfsPackExtBlockCacheTables tables = new DfsPackExtBlockCacheTables(
				List.of(blockCacheTable, new TestCache()),
				Map.of(PackExt.PACK, blockCacheTable, PackExt.BITMAP_INDEX,
						new TestCache()));

		assertThat(tables.getTable(packStreamKey), is(blockCacheTable));
	}

	@Test
	public void getTableWithDfsStreamKeyExtNotFoundReturnsNull() {
		DfsStreamKey packStreamKey = new TestKey(PackExt.PACK);
		DfsBlockCacheTable blockCacheTable = new TestCache();

		DfsPackExtBlockCacheTables tables = new DfsPackExtBlockCacheTables(
				List.of(blockCacheTable),
				Map.of(PackExt.INDEX, blockCacheTable));

		assertThat(tables.getTable(packStreamKey), nullValue());
	}

	@Test
	public void getTableWithPackExt() {
		DfsBlockCacheTable blockCacheTable = new TestCache();

		DfsPackExtBlockCacheTables tables = new DfsPackExtBlockCacheTables(
				List.of(blockCacheTable, new TestCache()),
				Map.of(PackExt.PACK, blockCacheTable, PackExt.BITMAP_INDEX,
						new TestCache()));

		assertThat(tables.getTable(PackExt.PACK), is(blockCacheTable));
	}

	@Test
	public void getTableWithPackExtNotFoundReturnsNull() {
		DfsBlockCacheTable blockCacheTable = new TestCache();

		DfsPackExtBlockCacheTables tables = new DfsPackExtBlockCacheTables(
				List.of(blockCacheTable),
				Map.of(PackExt.INDEX, blockCacheTable));

		assertThat(tables.getTable(PackExt.PACK), nullValue());
	}

	@Test
	public void getCurrentSizeConsolidatesAllTableCurrentSizes() {
		long[] sizes = emptyPackStats();
		DfsBlockCacheStats packStats = new DfsBlockCacheStats();
		packStats.addToLiveBytes(new TestKey(PackExt.PACK), 5);
		sizes[PackExt.PACK.ordinal()] = 5;
		DfsBlockCacheStats bitmapStats = new DfsBlockCacheStats();
		bitmapStats.addToLiveBytes(new TestKey(PackExt.BITMAP_INDEX), 6);
		sizes[PackExt.BITMAP_INDEX.ordinal()] = 6;
		DfsBlockCacheStats indexStats = new DfsBlockCacheStats();
		indexStats.addToLiveBytes(new TestKey(PackExt.INDEX), 7);
		sizes[PackExt.INDEX.ordinal()] = 7;

		DfsPackExtBlockCacheTables tables = new DfsPackExtBlockCacheTables(
				List.of(new TestCache(packStats), new TestCache(bitmapStats),
						new TestCache(indexStats)),
				Map.of());

		assertArrayEquals(tables.getCurrentSize(), sizes);
	}

	@Test
	public void getHitCountConsolidatesAllTableHitCounts() {
		long[] hitCounts = emptyPackStats();
		DfsBlockCacheStats packStats = new DfsBlockCacheStats();
		incrementNTimes(5,
				() -> packStats.incrementHit(new TestKey(PackExt.PACK)));
		hitCounts[PackExt.PACK.ordinal()] = 5;
		DfsBlockCacheStats bitmapStats = new DfsBlockCacheStats();
		incrementNTimes(6, () -> bitmapStats
				.incrementHit(new TestKey(PackExt.BITMAP_INDEX)));
		hitCounts[PackExt.BITMAP_INDEX.ordinal()] = 6;
		DfsBlockCacheStats indexStats = new DfsBlockCacheStats();
		incrementNTimes(7,
				() -> indexStats.incrementHit(new TestKey(PackExt.INDEX)));
		hitCounts[PackExt.INDEX.ordinal()] = 7;

		DfsPackExtBlockCacheTables tables = new DfsPackExtBlockCacheTables(
				List.of(new TestCache(packStats), new TestCache(bitmapStats),
						new TestCache(indexStats)),
				Map.of());

		assertArrayEquals(tables.getHitCount(), hitCounts);
	}

	@Test
	public void getEvictionsConsolidatesAllTableEvictions() {
		long[] evictions = emptyPackStats();
		DfsBlockCacheStats packStats = new DfsBlockCacheStats();
		incrementNTimes(5,
				() -> packStats.incrementEvict(new TestKey(PackExt.PACK)));
		evictions[PackExt.PACK.ordinal()] = 5;
		DfsBlockCacheStats bitmapStats = new DfsBlockCacheStats();
		incrementNTimes(6, () -> bitmapStats
				.incrementEvict(new TestKey(PackExt.BITMAP_INDEX)));
		evictions[PackExt.BITMAP_INDEX.ordinal()] = 6;
		DfsBlockCacheStats indexStats = new DfsBlockCacheStats();
		incrementNTimes(7,
				() -> indexStats.incrementEvict(new TestKey(PackExt.INDEX)));
		evictions[PackExt.INDEX.ordinal()] = 7;

		DfsPackExtBlockCacheTables tables = new DfsPackExtBlockCacheTables(
				List.of(new TestCache(packStats), new TestCache(bitmapStats),
						new TestCache(indexStats)),
				Map.of());

		assertArrayEquals(tables.getEvictions(), evictions);
	}

	@Test
	public void getMissCountsConsolidatesAllTableMissCounts() {
		long[] missCounts = emptyPackStats();
		DfsBlockCacheStats packStats = new DfsBlockCacheStats();
		incrementNTimes(5,
				() -> packStats.incrementMiss(new TestKey(PackExt.PACK)));
		missCounts[PackExt.PACK.ordinal()] = 5;
		DfsBlockCacheStats bitmapStats = new DfsBlockCacheStats();
		incrementNTimes(6, () -> bitmapStats
				.incrementMiss(new TestKey(PackExt.BITMAP_INDEX)));
		missCounts[PackExt.BITMAP_INDEX.ordinal()] = 6;
		DfsBlockCacheStats indexStats = new DfsBlockCacheStats();
		incrementNTimes(7,
				() -> indexStats.incrementMiss(new TestKey(PackExt.INDEX)));
		missCounts[PackExt.INDEX.ordinal()] = 7;

		DfsPackExtBlockCacheTables tables = new DfsPackExtBlockCacheTables(
				List.of(new TestCache(packStats), new TestCache(bitmapStats),
						new TestCache(indexStats)),
				Map.of());

		assertArrayEquals(tables.getMissCount(), missCounts);
	}

	@Test
	public void getTotalRequestCountsConsolidatesAllTableTotalRequestCounts() {
		long[] totalRequests = emptyPackStats();
		DfsBlockCacheStats packStats = new DfsBlockCacheStats();
		incrementNTimes(5,
				() -> packStats.incrementHit(new TestKey(PackExt.PACK)));
		incrementNTimes(5,
				() -> packStats.incrementMiss(new TestKey(PackExt.PACK)));
		totalRequests[PackExt.PACK.ordinal()] = 10;
		DfsBlockCacheStats bitmapStats = new DfsBlockCacheStats();
		incrementNTimes(6, () -> bitmapStats
				.incrementHit(new TestKey(PackExt.BITMAP_INDEX)));
		incrementNTimes(6, () -> bitmapStats
				.incrementMiss(new TestKey(PackExt.BITMAP_INDEX)));
		totalRequests[PackExt.BITMAP_INDEX.ordinal()] = 12;
		DfsBlockCacheStats indexStats = new DfsBlockCacheStats();
		incrementNTimes(7,
				() -> indexStats.incrementHit(new TestKey(PackExt.INDEX)));
		incrementNTimes(7,
				() -> indexStats.incrementMiss(new TestKey(PackExt.INDEX)));
		totalRequests[PackExt.INDEX.ordinal()] = 14;

		DfsPackExtBlockCacheTables tables = new DfsPackExtBlockCacheTables(
				List.of(new TestCache(packStats), new TestCache(bitmapStats),
						new TestCache(indexStats)),
				Map.of());

		assertArrayEquals(tables.getTotalRequestCount(), totalRequests);
	}

	private static void incrementNTimes(int n, Runnable cb) {
		for (int i = 0; i < n; i++) {
			cb.run();
		}
	}

	private static long[] emptyPackStats() {
		long[] values = new long[PackExt.values().length];
		for (int i = 0; i < PackExt.values().length; i++) {
			values[i] = 0;
		}
		return values;
	}

	private static class TestKey extends DfsStreamKey {
		TestKey(PackExt packExt) {
			super(0, packExt);
		}

		@Override
		public boolean equals(Object o) {
			return false;
		}
	}

	private static class TestCache implements DfsBlockCacheTable {
		private DfsBlockCacheStats stats = new DfsBlockCacheStats();

		TestCache() {
		}

		TestCache(DfsBlockCacheStats stats) {
			this.stats = stats;
		}

		@Override
		public boolean hasBlock0(DfsStreamKey key) {
			return false;
		}

		@Override
		public DfsBlock getOrLoad(BlockBasedFile file, long position,
				DfsReader dfsReader, ReadableChannelSupplier fileChannel)
				throws IOException {
			return null;
		}

		@Override
		public <T> Ref<T> getOrLoadRef(DfsStreamKey key, long position,
				RefLoader<T> loader) throws IOException {
			return null;
		}

		@Override
		public void put(DfsBlock v) {

		}

		@Override
		public <T> Ref<T> put(DfsStreamKey key, long pos, long size, T v) {
			return null;
		}

		@Override
		public <T> Ref<T> putRef(DfsStreamKey key, long size, T v) {
			return null;
		}

		@Override
		public boolean contains(DfsStreamKey key, long position) {
			return false;
		}

		@SuppressWarnings("TypeParameterUnusedInFormals")
		@Override
		public <T> T get(DfsStreamKey key, long position) {
			return null;
		}

		@Override
		public DfsBlockCacheStats getDfsBlockCacheStats() {
			return stats;
		}
	}
}
