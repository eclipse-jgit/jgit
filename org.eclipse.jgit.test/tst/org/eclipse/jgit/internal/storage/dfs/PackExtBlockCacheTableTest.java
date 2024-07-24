/*
 * Copyright (c) 2024, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheTable.BlockCacheStats;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.internal.storage.dfs.DfsBlockCache.Ref;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCache.RefLoader;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheConfig.DfsBlockCachePackExtConfig;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.junit.Test;
import org.mockito.Mockito;

@SuppressWarnings({ "boxing", "unchecked" })
public class PackExtBlockCacheTableTest {
	private static final String CACHE_NAME = "CacheName";

	@Test
	public void fromBlockCacheConfigs_createsDfsPackExtBlockCacheTables() {
		DfsBlockCacheConfig cacheConfig = new DfsBlockCacheConfig();
		cacheConfig.setPackExtCacheConfigurations(
				List.of(new DfsBlockCachePackExtConfig(EnumSet.of(PackExt.PACK),
						new DfsBlockCacheConfig())));
		assertNotNull(
				PackExtBlockCacheTable.fromBlockCacheConfigs(cacheConfig));
	}

	@Test
	public void fromBlockCacheConfigs_noPackExtConfigurationGiven_packExtCacheConfigurationsIsEmpty_throws() {
		DfsBlockCacheConfig cacheConfig = new DfsBlockCacheConfig();
		cacheConfig.setPackExtCacheConfigurations(List.of());
		assertThrows(IllegalArgumentException.class,
				() -> PackExtBlockCacheTable
						.fromBlockCacheConfigs(cacheConfig));
	}

	@Test
	public void hasBlock0_packExtMapsToCacheTable_callsBitmapIndexCacheTable() {
		DfsStreamKey streamKey = new TestKey(PackExt.BITMAP_INDEX);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.hasBlock0(any(DfsStreamKey.class)))
				.thenReturn(true);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				Map.of(PackExt.BITMAP_INDEX, bitmapIndexCacheTable));

		assertTrue(tables.hasBlock0(streamKey));
	}

	@Test
	public void hasBlock0_packExtDoesNotMapToCacheTable_callsDefaultCache() {
		DfsStreamKey streamKey = new TestKey(PackExt.PACK);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.hasBlock0(any(DfsStreamKey.class)))
				.thenReturn(true);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				Map.of(PackExt.BITMAP_INDEX, bitmapIndexCacheTable));

		assertTrue(tables.hasBlock0(streamKey));
	}

	@Test
	public void getOrLoad_packExtMapsToCacheTable_callsBitmapIndexCacheTable()
			throws Exception {
		BlockBasedFile blockBasedFile = new BlockBasedFile(null,
				mock(DfsPackDescription.class), PackExt.BITMAP_INDEX) {
			// empty
		};
		DfsBlock dfsBlock = mock(DfsBlock.class);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.getOrLoad(any(BlockBasedFile.class),
				anyLong(), any(DfsReader.class),
				any(DfsBlockCache.ReadableChannelSupplier.class)))
				.thenReturn(mock(DfsBlock.class));
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.getOrLoad(any(BlockBasedFile.class),
				anyLong(), any(DfsReader.class),
				any(DfsBlockCache.ReadableChannelSupplier.class)))
				.thenReturn(dfsBlock);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				Map.of(PackExt.BITMAP_INDEX, bitmapIndexCacheTable));

		assertThat(
				tables.getOrLoad(blockBasedFile, 0, mock(DfsReader.class),
						mock(DfsBlockCache.ReadableChannelSupplier.class)),
				sameInstance(dfsBlock));
	}

	@Test
	public void getOrLoad_packExtDoesNotMapToCacheTable_callsDefaultCache()
			throws Exception {
		BlockBasedFile blockBasedFile = new BlockBasedFile(null,
				mock(DfsPackDescription.class), PackExt.PACK) {
			// empty
		};
		DfsBlock dfsBlock = mock(DfsBlock.class);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.getOrLoad(any(BlockBasedFile.class),
				anyLong(), any(DfsReader.class),
				any(DfsBlockCache.ReadableChannelSupplier.class)))
				.thenReturn(dfsBlock);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.getOrLoad(any(BlockBasedFile.class),
				anyLong(), any(DfsReader.class),
				any(DfsBlockCache.ReadableChannelSupplier.class)))
				.thenReturn(mock(DfsBlock.class));

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				Map.of(PackExt.BITMAP_INDEX, bitmapIndexCacheTable));

		assertThat(
				tables.getOrLoad(blockBasedFile, 0, mock(DfsReader.class),
						mock(DfsBlockCache.ReadableChannelSupplier.class)),
				sameInstance(dfsBlock));
	}

	@Test
	public void getOrLoadRef_packExtMapsToCacheTable_callsBitmapIndexCacheTable()
			throws Exception {
		Ref<Integer> ref = mock(Ref.class);
		DfsStreamKey dfsStreamKey = new TestKey(PackExt.BITMAP_INDEX);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.getOrLoadRef(any(DfsStreamKey.class),
				anyLong(), any(RefLoader.class))).thenReturn(mock(Ref.class));
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.getOrLoadRef(any(DfsStreamKey.class),
				anyLong(), any(RefLoader.class))).thenReturn(ref);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				Map.of(PackExt.BITMAP_INDEX, bitmapIndexCacheTable));

		assertThat(tables.getOrLoadRef(dfsStreamKey, 0, mock(RefLoader.class)),
				sameInstance(ref));
	}

	@Test
	public void getOrLoadRef_packExtDoesNotMapToCacheTable_callsDefaultCache()
			throws Exception {
		Ref<Integer> ref = mock(Ref.class);
		DfsStreamKey dfsStreamKey = new TestKey(PackExt.PACK);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.getOrLoadRef(any(DfsStreamKey.class),
				anyLong(), any(RefLoader.class))).thenReturn(ref);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.getOrLoadRef(any(DfsStreamKey.class),
				anyLong(), any(RefLoader.class))).thenReturn(mock(Ref.class));

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				Map.of(PackExt.BITMAP_INDEX, bitmapIndexCacheTable));

		assertThat(tables.getOrLoadRef(dfsStreamKey, 0, mock(RefLoader.class)),
				sameInstance(ref));
	}

	@Test
	public void putDfsBlock_packExtMapsToCacheTable_callsBitmapIndexCacheTable() {
		DfsStreamKey dfsStreamKey = new TestKey(PackExt.BITMAP_INDEX);
		DfsBlock dfsBlock = new DfsBlock(dfsStreamKey, 0, new byte[0]);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				Map.of(PackExt.BITMAP_INDEX, bitmapIndexCacheTable));

		tables.put(dfsBlock);
		Mockito.verify(bitmapIndexCacheTable, times(1)).put(dfsBlock);
	}

	@Test
	public void putDfsBlock_packExtDoesNotMapToCacheTable_callsDefaultCache() {
		DfsStreamKey dfsStreamKey = new TestKey(PackExt.PACK);
		DfsBlock dfsBlock = new DfsBlock(dfsStreamKey, 0, new byte[0]);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				Map.of(PackExt.BITMAP_INDEX, bitmapIndexCacheTable));

		tables.put(dfsBlock);
		Mockito.verify(defaultBlockCacheTable, times(1)).put(dfsBlock);
	}

	@Test
	public void putDfsStreamKey_packExtMapsToCacheTable_callsBitmapIndexCacheTable() {
		DfsStreamKey dfsStreamKey = new TestKey(PackExt.BITMAP_INDEX);
		Ref<Integer> ref = mock(Ref.class);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.put(any(DfsStreamKey.class), anyLong(),
				anyLong(), anyInt())).thenReturn(mock(Ref.class));
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.put(any(DfsStreamKey.class), anyLong(),
				anyLong(), anyInt())).thenReturn(ref);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				Map.of(PackExt.BITMAP_INDEX, bitmapIndexCacheTable));

		assertThat(tables.put(dfsStreamKey, 0, 0, 0), sameInstance(ref));
	}

	@Test
	public void putDfsStreamKey_packExtDoesNotMapToCacheTable_callsDefaultCache() {
		DfsStreamKey dfsStreamKey = new TestKey(PackExt.PACK);
		Ref<Integer> ref = mock(Ref.class);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.put(any(DfsStreamKey.class), anyLong(),
				anyLong(), anyInt())).thenReturn(ref);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.put(any(DfsStreamKey.class), anyLong(),
				anyLong(), anyInt())).thenReturn(mock(Ref.class));

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				Map.of(PackExt.BITMAP_INDEX, bitmapIndexCacheTable));

		assertThat(tables.put(dfsStreamKey, 0, 0, 0), sameInstance(ref));
	}

	@Test
	public void putRef_packExtMapsToCacheTable_callsBitmapIndexCacheTable() {
		DfsStreamKey dfsStreamKey = new TestKey(PackExt.BITMAP_INDEX);
		Ref<Integer> ref = mock(Ref.class);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.putRef(any(DfsStreamKey.class), anyLong(),
				anyInt())).thenReturn(mock(Ref.class));
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.putRef(any(DfsStreamKey.class), anyLong(),
				anyInt())).thenReturn(ref);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				Map.of(PackExt.BITMAP_INDEX, bitmapIndexCacheTable));

		assertThat(tables.putRef(dfsStreamKey, 0, 0), sameInstance(ref));
	}

	@Test
	public void putRef_packExtDoesNotMapToCacheTable_callsDefaultCache() {
		DfsStreamKey dfsStreamKey = new TestKey(PackExt.PACK);
		Ref<Integer> ref = mock(Ref.class);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.putRef(any(DfsStreamKey.class), anyLong(),
				anyInt())).thenReturn(ref);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.putRef(any(DfsStreamKey.class), anyLong(),
				anyInt())).thenReturn(mock(Ref.class));

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				Map.of(PackExt.BITMAP_INDEX, bitmapIndexCacheTable));

		assertThat(tables.putRef(dfsStreamKey, 0, 0), sameInstance(ref));
	}

	@Test
	public void contains_packExtMapsToCacheTable_callsBitmapIndexCacheTable() {
		DfsStreamKey streamKey = new TestKey(PackExt.BITMAP_INDEX);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.contains(any(DfsStreamKey.class), anyLong()))
				.thenReturn(true);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				Map.of(PackExt.BITMAP_INDEX, bitmapIndexCacheTable));

		assertTrue(tables.contains(streamKey, 0));
	}

	@Test
	public void contains_packExtDoesNotMapToCacheTable_callsDefaultCache() {
		DfsStreamKey streamKey = new TestKey(PackExt.PACK);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.contains(any(DfsStreamKey.class),
				anyLong())).thenReturn(true);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				Map.of(PackExt.BITMAP_INDEX, bitmapIndexCacheTable));

		assertTrue(tables.contains(streamKey, 0));
	}

	@Test
	public void get_packExtMapsToCacheTable_callsBitmapIndexCacheTable() {
		DfsStreamKey dfsStreamKey = new TestKey(PackExt.BITMAP_INDEX);
		Ref<Integer> ref = mock(Ref.class);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.get(any(DfsStreamKey.class), anyLong()))
				.thenReturn(mock(Ref.class));
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.get(any(DfsStreamKey.class), anyLong()))
				.thenReturn(ref);

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				Map.of(PackExt.BITMAP_INDEX, bitmapIndexCacheTable));

		assertThat(tables.get(dfsStreamKey, 0), sameInstance(ref));
	}

	@Test
	public void get_packExtDoesNotMapToCacheTable_callsDefaultCache() {
		DfsStreamKey dfsStreamKey = new TestKey(PackExt.PACK);
		Ref<Integer> ref = mock(Ref.class);
		DfsBlockCacheTable defaultBlockCacheTable = mock(
				DfsBlockCacheTable.class);
		when(defaultBlockCacheTable.get(any(DfsStreamKey.class), anyLong()))
				.thenReturn(ref);
		DfsBlockCacheTable bitmapIndexCacheTable = mock(
				DfsBlockCacheTable.class);
		when(bitmapIndexCacheTable.get(any(DfsStreamKey.class), anyLong()))
				.thenReturn(mock(Ref.class));

		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				defaultBlockCacheTable,
				Map.of(PackExt.BITMAP_INDEX, bitmapIndexCacheTable));

		assertThat(tables.get(dfsStreamKey, 0), sameInstance(ref));
	}

	@Test
	public void getName() {
		DfsBlockCacheStats packStats = new DfsBlockCacheStats();
		PackExtBlockCacheTable tables = PackExtBlockCacheTable.fromCacheTables(
				cacheTableWithStats(/* name= */ "defaultName", packStats),
				Map.of(PackExt.PACK, cacheTableWithStats(/* name= */ "packName",
						packStats)));

		assertThat(tables.getName(), equalTo("defaultName,packName"));
	}

	@Test
	public void getAllBlockCacheStats() {
		String defaultTableName = "default table";
		DfsBlockCacheStats defaultStats = new DfsBlockCacheStats(
				defaultTableName);
		incrementCounter(4,
				() -> defaultStats.incrementHit(new TestKey(PackExt.REFTABLE)));

		String packTableName = "pack table";
		DfsBlockCacheStats packStats = new DfsBlockCacheStats(packTableName);
		incrementCounter(5,
				() -> packStats.incrementHit(new TestKey(PackExt.PACK)));

		String bitmapTableName = "bitmap table";
		DfsBlockCacheStats bitmapStats = new DfsBlockCacheStats(
				bitmapTableName);
		incrementCounter(6, () -> bitmapStats
				.incrementHit(new TestKey(PackExt.BITMAP_INDEX)));

		DfsBlockCacheTable defaultTable = cacheTableWithStats(defaultStats);
		DfsBlockCacheTable packTable = cacheTableWithStats(packStats);
		DfsBlockCacheTable bitmapTable = cacheTableWithStats(bitmapStats);
		PackExtBlockCacheTable tables = PackExtBlockCacheTable
				.fromCacheTables(defaultTable, Map.of(PackExt.PACK, packTable,
						PackExt.BITMAP_INDEX, bitmapTable));

		List<BlockCacheStats> statsList = tables.getBlockCacheStats();
		assertThat(statsList, hasSize(3));

		long[] defaultTableHitCounts = createEmptyStatsArray();
		defaultTableHitCounts[PackExt.REFTABLE.getPosition()] = 4;
		assertArrayEquals(
				getCacheStatsByName(statsList, defaultTableName).getHitCount(),
				defaultTableHitCounts);

		long[] packTableHitCounts = createEmptyStatsArray();
		packTableHitCounts[PackExt.PACK.getPosition()] = 5;
		assertArrayEquals(
				getCacheStatsByName(statsList, packTableName).getHitCount(),
				packTableHitCounts);

		long[] bitmapHitCounts = createEmptyStatsArray();
		bitmapHitCounts[PackExt.BITMAP_INDEX.getPosition()] = 6;
		assertArrayEquals(
				getCacheStatsByName(statsList, bitmapTableName).getHitCount(),
				bitmapHitCounts);
	}

	@Test
	public void getBlockCacheStats_getCurrentSize_consolidatesAllTableCurrentSizes() {
		long[] currentSizes = createEmptyStatsArray();

		DfsBlockCacheStats packStats = new DfsBlockCacheStats();
		packStats.addToLiveBytes(new TestKey(PackExt.PACK), 5);
		currentSizes[PackExt.PACK.getPosition()] = 5;

		DfsBlockCacheStats bitmapStats = new DfsBlockCacheStats();
		bitmapStats.addToLiveBytes(new TestKey(PackExt.BITMAP_INDEX), 6);
		currentSizes[PackExt.BITMAP_INDEX.getPosition()] = 6;

		DfsBlockCacheStats indexStats = new DfsBlockCacheStats();
		indexStats.addToLiveBytes(new TestKey(PackExt.INDEX), 7);
		currentSizes[PackExt.INDEX.getPosition()] = 7;

		PackExtBlockCacheTable tables = PackExtBlockCacheTable
				.fromCacheTables(cacheTableWithStats(packStats),
						Map.of(PackExt.BITMAP_INDEX,
								cacheTableWithStats(bitmapStats), PackExt.INDEX,
								cacheTableWithStats(indexStats)));

		assertArrayEquals(AggregatedBlockCacheStats
				.fromStatsList(tables.getBlockCacheStats()).getCurrentSize(),
				currentSizes);
	}

	@Test
	public void getBlockCacheStats_GetHitCount_consolidatesAllTableHitCounts() {
		long[] hitCounts = createEmptyStatsArray();

		DfsBlockCacheStats packStats = new DfsBlockCacheStats();
		incrementCounter(5,
				() -> packStats.incrementHit(new TestKey(PackExt.PACK)));
		hitCounts[PackExt.PACK.getPosition()] = 5;

		DfsBlockCacheStats bitmapStats = new DfsBlockCacheStats();
		incrementCounter(6, () -> bitmapStats
				.incrementHit(new TestKey(PackExt.BITMAP_INDEX)));
		hitCounts[PackExt.BITMAP_INDEX.getPosition()] = 6;

		DfsBlockCacheStats indexStats = new DfsBlockCacheStats();
		incrementCounter(7,
				() -> indexStats.incrementHit(new TestKey(PackExt.INDEX)));
		hitCounts[PackExt.INDEX.getPosition()] = 7;

		PackExtBlockCacheTable tables = PackExtBlockCacheTable
				.fromCacheTables(cacheTableWithStats(packStats),
						Map.of(PackExt.BITMAP_INDEX,
								cacheTableWithStats(bitmapStats), PackExt.INDEX,
								cacheTableWithStats(indexStats)));

		assertArrayEquals(AggregatedBlockCacheStats
				.fromStatsList(tables.getBlockCacheStats()).getHitCount(),
				hitCounts);
	}

	@Test
	public void getBlockCacheStats_getMissCount_consolidatesAllTableMissCounts() {
		long[] missCounts = createEmptyStatsArray();

		DfsBlockCacheStats packStats = new DfsBlockCacheStats();
		incrementCounter(5,
				() -> packStats.incrementMiss(new TestKey(PackExt.PACK)));
		missCounts[PackExt.PACK.getPosition()] = 5;

		DfsBlockCacheStats bitmapStats = new DfsBlockCacheStats();
		incrementCounter(6, () -> bitmapStats
				.incrementMiss(new TestKey(PackExt.BITMAP_INDEX)));
		missCounts[PackExt.BITMAP_INDEX.getPosition()] = 6;

		DfsBlockCacheStats indexStats = new DfsBlockCacheStats();
		incrementCounter(7,
				() -> indexStats.incrementMiss(new TestKey(PackExt.INDEX)));
		missCounts[PackExt.INDEX.getPosition()] = 7;

		PackExtBlockCacheTable tables = PackExtBlockCacheTable
				.fromCacheTables(cacheTableWithStats(packStats),
						Map.of(PackExt.BITMAP_INDEX,
								cacheTableWithStats(bitmapStats), PackExt.INDEX,
								cacheTableWithStats(indexStats)));

		assertArrayEquals(AggregatedBlockCacheStats
				.fromStatsList(tables.getBlockCacheStats()).getMissCount(),
				missCounts);
	}

	@Test
	public void getBlockCacheStats_getTotalRequestCount_consolidatesAllTableTotalRequestCounts() {
		long[] totalRequestCounts = createEmptyStatsArray();

		DfsBlockCacheStats packStats = new DfsBlockCacheStats();
		incrementCounter(5, () -> {
			packStats.incrementHit(new TestKey(PackExt.PACK));
			packStats.incrementMiss(new TestKey(PackExt.PACK));
		});
		totalRequestCounts[PackExt.PACK.getPosition()] = 10;

		DfsBlockCacheStats bitmapStats = new DfsBlockCacheStats();
		incrementCounter(6, () -> {
			bitmapStats.incrementHit(new TestKey(PackExt.BITMAP_INDEX));
			bitmapStats.incrementMiss(new TestKey(PackExt.BITMAP_INDEX));
		});
		totalRequestCounts[PackExt.BITMAP_INDEX.getPosition()] = 12;

		DfsBlockCacheStats indexStats = new DfsBlockCacheStats();
		incrementCounter(7, () -> {
			indexStats.incrementHit(new TestKey(PackExt.INDEX));
			indexStats.incrementMiss(new TestKey(PackExt.INDEX));
		});
		totalRequestCounts[PackExt.INDEX.getPosition()] = 14;

		PackExtBlockCacheTable tables = PackExtBlockCacheTable
				.fromCacheTables(cacheTableWithStats(packStats),
						Map.of(PackExt.BITMAP_INDEX,
								cacheTableWithStats(bitmapStats), PackExt.INDEX,
								cacheTableWithStats(indexStats)));

		assertArrayEquals(AggregatedBlockCacheStats
				.fromStatsList(tables.getBlockCacheStats())
				.getTotalRequestCount(), totalRequestCounts);
	}

	@Test
	public void getBlockCacheStats_getHitRatio_consolidatesAllTableHitRatios() {
		long[] hitRatios = createEmptyStatsArray();

		DfsBlockCacheStats packStats = new DfsBlockCacheStats();
		incrementCounter(5,
				() -> packStats.incrementHit(new TestKey(PackExt.PACK)));
		hitRatios[PackExt.PACK.getPosition()] = 100;

		DfsBlockCacheStats bitmapStats = new DfsBlockCacheStats();
		incrementCounter(6, () -> {
			bitmapStats.incrementHit(new TestKey(PackExt.BITMAP_INDEX));
			bitmapStats.incrementMiss(new TestKey(PackExt.BITMAP_INDEX));
		});
		hitRatios[PackExt.BITMAP_INDEX.getPosition()] = 50;

		DfsBlockCacheStats indexStats = new DfsBlockCacheStats();
		incrementCounter(7,
				() -> indexStats.incrementMiss(new TestKey(PackExt.INDEX)));
		hitRatios[PackExt.INDEX.getPosition()] = 0;

		PackExtBlockCacheTable tables = PackExtBlockCacheTable
				.fromCacheTables(cacheTableWithStats(packStats),
						Map.of(PackExt.BITMAP_INDEX,
								cacheTableWithStats(bitmapStats), PackExt.INDEX,
								cacheTableWithStats(indexStats)));

		assertArrayEquals(AggregatedBlockCacheStats
				.fromStatsList(tables.getBlockCacheStats()).getHitRatio(),
				hitRatios);
	}

	@Test
	public void getBlockCacheStats_getEvictions_consolidatesAllTableEvictions() {
		long[] evictions = createEmptyStatsArray();

		DfsBlockCacheStats packStats = new DfsBlockCacheStats();
		incrementCounter(5,
				() -> packStats.incrementEvict(new TestKey(PackExt.PACK)));
		evictions[PackExt.PACK.getPosition()] = 5;

		DfsBlockCacheStats bitmapStats = new DfsBlockCacheStats();
		incrementCounter(6, () -> bitmapStats
				.incrementEvict(new TestKey(PackExt.BITMAP_INDEX)));
		evictions[PackExt.BITMAP_INDEX.getPosition()] = 6;

		DfsBlockCacheStats indexStats = new DfsBlockCacheStats();
		incrementCounter(7,
				() -> indexStats.incrementEvict(new TestKey(PackExt.INDEX)));
		evictions[PackExt.INDEX.getPosition()] = 7;

		PackExtBlockCacheTable tables = PackExtBlockCacheTable
				.fromCacheTables(cacheTableWithStats(packStats),
						Map.of(PackExt.BITMAP_INDEX,
								cacheTableWithStats(bitmapStats), PackExt.INDEX,
								cacheTableWithStats(indexStats)));

		assertArrayEquals(AggregatedBlockCacheStats
				.fromStatsList(tables.getBlockCacheStats()).getEvictions(),
				evictions);
	}

	private BlockCacheStats getCacheStatsByName(
			List<BlockCacheStats> blockCacheStats, String name) {
		for (BlockCacheStats entry : blockCacheStats) {
			if (entry.getName().equals(name)) {
				return entry;
			}
		}
		return null;
	}

	private static void incrementCounter(int amount, Runnable fn) {
		for (int i = 0; i < amount; i++) {
			fn.run();
		}
	}

	private static long[] createEmptyStatsArray() {
		return new long[PackExt.values().length];
	}

	private static DfsBlockCacheTable cacheTableWithStats(
			BlockCacheStats dfsBlockCacheStats) {
		return cacheTableWithStats(CACHE_NAME, dfsBlockCacheStats);
	}

	private static DfsBlockCacheTable cacheTableWithStats(String name,
			BlockCacheStats dfsBlockCacheStats) {
		DfsBlockCacheTable cacheTable = mock(DfsBlockCacheTable.class);
		when(cacheTable.getName()).thenReturn(name);
		when(cacheTable.getBlockCacheStats())
				.thenReturn(List.of(dfsBlockCacheStats));
		return cacheTable;
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
}
