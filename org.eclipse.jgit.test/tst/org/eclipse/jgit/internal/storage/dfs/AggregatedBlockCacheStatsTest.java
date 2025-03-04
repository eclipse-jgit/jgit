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
import static org.junit.Assert.assertArrayEquals;

import java.util.List;

import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.junit.Test;

public class AggregatedBlockCacheStatsTest {
	@Test
	public void getName() {
		BlockCacheStats aggregatedBlockCacheStats = AggregatedBlockCacheStats
				.fromStatsList(List.of());

		assertThat(aggregatedBlockCacheStats.getName(),
				equalTo(AggregatedBlockCacheStats.class.getName()));
	}

	@Test
	public void getCurrentSize_aggregatesCurrentSizes() {
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

		BlockCacheStats aggregatedBlockCacheStats = AggregatedBlockCacheStats
				.fromStatsList(List.of(packStats, bitmapStats, indexStats));

		assertArrayEquals(aggregatedBlockCacheStats.getCurrentSize(),
				currentSizes);
	}

	@Test
	public void getHitCount_aggregatesHitCounts() {
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

		BlockCacheStats aggregatedBlockCacheStats = AggregatedBlockCacheStats
				.fromStatsList(List.of(packStats, bitmapStats, indexStats));

		assertArrayEquals(aggregatedBlockCacheStats.getHitCount(), hitCounts);
	}

	@Test
	public void getMissCount_aggregatesMissCounts() {
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

		BlockCacheStats aggregatedBlockCacheStats = AggregatedBlockCacheStats
				.fromStatsList(List.of(packStats, bitmapStats, indexStats));

		assertArrayEquals(aggregatedBlockCacheStats.getMissCount(), missCounts);
	}

	@Test
	public void getTotalRequestCount_aggregatesRequestCounts() {
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

		BlockCacheStats aggregatedBlockCacheStats = AggregatedBlockCacheStats
				.fromStatsList(List.of(packStats, bitmapStats, indexStats));

		assertArrayEquals(aggregatedBlockCacheStats.getTotalRequestCount(),
				totalRequestCounts);
	}

	@Test
	public void getHitRatio_aggregatesHitRatios() {
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

		BlockCacheStats aggregatedBlockCacheStats = AggregatedBlockCacheStats
				.fromStatsList(List.of(packStats, bitmapStats, indexStats));

		assertArrayEquals(aggregatedBlockCacheStats.getHitRatio(), hitRatios);
	}

	@Test
	public void getEvictions_aggregatesEvictions() {
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

		BlockCacheStats aggregatedBlockCacheStats = AggregatedBlockCacheStats
				.fromStatsList(List.of(packStats, bitmapStats, indexStats));

		assertArrayEquals(aggregatedBlockCacheStats.getEvictions(), evictions);
	}

	private static void incrementCounter(int amount, Runnable fn) {
		for (int i = 0; i < amount; i++) {
			fn.run();
		}
	}

	private static long[] createEmptyStatsArray() {
		return new long[PackExt.values().length];
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