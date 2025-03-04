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

import java.util.List;

import org.eclipse.jgit.internal.storage.pack.PackExt;

/**
 * Aggregates values for all given {@link BlockCacheStats}.
 */
class AggregatedBlockCacheStats implements BlockCacheStats {
	private final List<BlockCacheStats> blockCacheStats;

	static BlockCacheStats fromStatsList(
			List<BlockCacheStats> blockCacheStats) {
		if (blockCacheStats.size() == 1) {
			return blockCacheStats.get(0);
		}
		return new AggregatedBlockCacheStats(blockCacheStats);
	}

	private AggregatedBlockCacheStats(List<BlockCacheStats> blockCacheStats) {
		this.blockCacheStats = blockCacheStats;
	}

	@Override
	public String getName() {
		return AggregatedBlockCacheStats.class.getName();
	}

	@Override
	public long[] getCurrentSize() {
		long[] sums = emptyPackStats();
		for (BlockCacheStats blockCacheStatsEntry : blockCacheStats) {
			sums = add(sums, blockCacheStatsEntry.getCurrentSize());
		}
		return sums;
	}

	@Override
	public long[] getHitCount() {
		long[] sums = emptyPackStats();
		for (BlockCacheStats blockCacheStatsEntry : blockCacheStats) {
			sums = add(sums, blockCacheStatsEntry.getHitCount());
		}
		return sums;
	}

	@Override
	public long[] getMissCount() {
		long[] sums = emptyPackStats();
		for (BlockCacheStats blockCacheStatsEntry : blockCacheStats) {
			sums = add(sums, blockCacheStatsEntry.getMissCount());
		}
		return sums;
	}

	@Override
	public long[] getTotalRequestCount() {
		long[] sums = emptyPackStats();
		for (BlockCacheStats blockCacheStatsEntry : blockCacheStats) {
			sums = add(sums, blockCacheStatsEntry.getTotalRequestCount());
		}
		return sums;
	}

	@Override
	public long[] getHitRatio() {
		long[] hit = getHitCount();
		long[] miss = getMissCount();
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

	@Override
	public long[] getEvictions() {
		long[] sums = emptyPackStats();
		for (BlockCacheStats blockCacheStatsEntry : blockCacheStats) {
			sums = add(sums, blockCacheStatsEntry.getEvictions());
		}
		return sums;
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
}
