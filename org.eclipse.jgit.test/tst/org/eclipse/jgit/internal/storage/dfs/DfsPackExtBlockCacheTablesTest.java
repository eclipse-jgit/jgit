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
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.internal.storage.dfs.DfsBlockCache.ReadableChannelSupplier;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCache.Ref;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCache.RefLoader;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheConfig.DfsBlockCachePackExtConfig;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheTable.DfsBlockCacheStats;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.junit.Test;

public class DfsPackExtBlockCacheTablesTest {
	@Test
	public void fromPackExtCacheConfigsMultipleCacheInstances() {
		List<DfsBlockCachePackExtConfig> configs = List.of(
				new DfsBlockCachePackExtConfig(Set.of(PackExt.PACK),
						new DfsBlockCacheConfig()),
				new DfsBlockCachePackExtConfig(Set.of(PackExt.INDEX),
						new DfsBlockCacheConfig()));

		DfsPackExtBlockCacheTables tables = DfsPackExtBlockCacheTables
				.fromPackExtCacheConfigs(configs);

		assertThat(tables.getTable(PackExt.PACK),
				instanceOf(ClockBlockCacheTable.class));
		assertThat(tables.getTable(PackExt.INDEX),
				instanceOf(ClockBlockCacheTable.class));
		assertThat(tables.getTable(PackExt.PACK),
				not(is(tables.getTable(PackExt.INDEX))));
	}

	@Test
	public void fromPackExtCacheConfigsExtensionsFromSingleConfigShareCacheInstance() {
		List<DfsBlockCachePackExtConfig> configs = List
				.of(new DfsBlockCachePackExtConfig(
						Set.of(PackExt.INDEX, PackExt.REVERSE_INDEX),
						new DfsBlockCacheConfig()));

		DfsPackExtBlockCacheTables tables = DfsPackExtBlockCacheTables
				.fromPackExtCacheConfigs(configs);

		assertThat(tables.getTable(PackExt.INDEX),
				instanceOf(ClockBlockCacheTable.class));
		assertThat(tables.getTable(PackExt.REVERSE_INDEX),
				instanceOf(ClockBlockCacheTable.class));
		assertThat(tables.getTable(PackExt.REVERSE_INDEX),
				is(tables.getTable(PackExt.INDEX)));
	}

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
		DfsBlockCacheStats packStats = new DfsBlockCacheStats();
		packStats.addToLiveBytes(new TestKey(PackExt.PACK), 5);
		long[] packStatsArray = createStatsArray(PackExt.PACK, 5);

		DfsBlockCacheStats bitmapStats = new DfsBlockCacheStats();
		bitmapStats.addToLiveBytes(new TestKey(PackExt.BITMAP_INDEX), 6);
		long[] bitmapStatsArray = createStatsArray(PackExt.BITMAP_INDEX, 6);

		DfsBlockCacheStats indexStats = new DfsBlockCacheStats();
		indexStats.addToLiveBytes(new TestKey(PackExt.INDEX), 7);
		long[] indexStatsArray = createStatsArray(PackExt.INDEX, 7);

		DfsPackExtBlockCacheTables tables = new DfsPackExtBlockCacheTables(
				List.of(new TestCache(packStats), new TestCache(bitmapStats),
						new TestCache(indexStats)),
				Map.of());

		assertThat(tables.getBlockCacheTableListStats(), hasSize(3));
		assertThat(
				tables.getBlockCacheTableListStats().stream()
						.map(DfsBlockCacheStats::getCurrentSize)
						.collect(Collectors.toList()),
				hasItems(packStatsArray, bitmapStatsArray, indexStatsArray));
	}

	private static long[] createStatsArray(PackExt packExt, long value) {
		long[] values = new long[PackExt.values().length];
		for (int i = 0; i < PackExt.values().length; i++) {
			values[i] = 0;
		}
		values[packExt.getPosition()] = value;
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
