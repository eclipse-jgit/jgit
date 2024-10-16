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

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.eclipse.jgit.internal.storage.pack.PackExt;

/**
 * Keeps a copy of aggregated stats for a Block Cache table.
 */
class AggregatedBlockCacheStats implements BlockCacheStats {
	private final String name;

	private final long[] currentSize;

	private final long[] hitCount;

	private final long[] missCount;

	private final long[] totalRequestCount;

	private final long[] hitRatio;

	private final long[] evictions;

	/**
	 * Aggregate the stats of multiple {@link BlockCacheStats}s.
	 *
	 * @param blockCacheStats
	 * @return
	 */
	static BlockCacheStats aggregate(String name,
			List<BlockCacheStats> blockCacheStats) {
		long[] currentSize = aggregateValues(blockCacheStats,
				BlockCacheStats::getCurrentSize);
		long[] hitCount = aggregateValues(blockCacheStats,
				BlockCacheStats::getHitCount);
		long[] missCount = aggregateValues(blockCacheStats,
				BlockCacheStats::getMissCount);
		long[] totalRequestCount = aggregateValues(blockCacheStats,
				BlockCacheStats::getTotalRequestCount);
		long[] hitRatio = aggregateHitRatio(hitCount, missCount);
		long[] evictions = aggregateValues(blockCacheStats,
				BlockCacheStats::getEvictions);
		return new AggregatedBlockCacheStats(name, currentSize, hitCount,
				missCount, totalRequestCount, hitRatio, evictions);
	}

	private static long[] aggregateValues(List<BlockCacheStats> blockCacheStats,
			Function<BlockCacheStats, long[]> fieldGetter) {
		long[] sums = emptyPackStats();
		for (BlockCacheStats blockCacheStatsEntry : blockCacheStats) {
			sums = add(sums, fieldGetter.apply(blockCacheStatsEntry));
		}
		return sums;
	}

	private static long[] aggregateHitRatio(long[] hit, long[] miss) {
		long[] ratio = new long[Math.max(hit.length, miss.length)];
		for (int i = 0; i < ratio.length; i++) {
			if (i >= hit.length) {
				ratio[i] = 0;
			} else if (i >= miss.length) {
				ratio[i] = 100;
			} else {
				long total = hit[i] + miss[i];
				ratio[i] = total == 0 ? 0 : hit[i] * 100 / total;
			}
		}
		return ratio;
	}

	private static long[] emptyPackStats() {
		return new long[PackExt.values().length];
	}

	private static long[] add(long[] first, long[] second) {
		long[] sums = new long[Integer.max(first.length, second.length)];
		int i;
		for (i = 0; i < Integer.min(first.length, second.length); i++) {
			sums[i] = first[i] + second[i];
		}
		for (int j = i; j < first.length; j++) {
			sums[j] = first[i];
		}
		for (int j = i; j < second.length; j++) {
			sums[j] = second[i];
		}
		return sums;
	}

	private AggregatedBlockCacheStats(String name, long[] currentSize,
			long[] hitCount, long[] missCount, long[] totalRequestCount,
			long[] hitRatio, long[] evictions) {
		this.name = name;
		this.currentSize = currentSize;
		this.hitCount = hitCount;
		this.missCount = missCount;
		this.totalRequestCount = totalRequestCount;
		this.hitRatio = hitRatio;
		this.evictions = evictions;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public long[] getCurrentSize() {
		return Arrays.copyOf(currentSize, currentSize.length);
	}

	@Override
	public long[] getHitCount() {
		return Arrays.copyOf(hitCount, hitCount.length);
	}

	@Override
	public long[] getMissCount() {
		return Arrays.copyOf(missCount, missCount.length);
	}

	@Override
	public long[] getTotalRequestCount() {
		return Arrays.copyOf(totalRequestCount, totalRequestCount.length);
	}

	@Override
	public long[] getHitRatio() {
		return Arrays.copyOf(hitRatio, hitRatio.length);
	}

	@Override
	public long[] getEvictions() {
		return Arrays.copyOf(evictions, evictions.length);
	}
}
