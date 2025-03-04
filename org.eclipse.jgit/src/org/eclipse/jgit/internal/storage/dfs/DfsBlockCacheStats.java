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

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.internal.storage.pack.PackExt;

/**
 * Keeps track of stats for a Block Cache table.
 */
class DfsBlockCacheStats implements BlockCacheStats {
	private final String name;

	/**
	 * Number of times a block was found in the cache, per pack file extension.
	 */
	private final AtomicReference<AtomicLong[]> statHit;

	/**
	 * Number of times a block was not found, and had to be loaded, per pack
	 * file extension.
	 */
	private final AtomicReference<AtomicLong[]> statMiss;

	/**
	 * Number of blocks evicted due to cache being full, per pack file
	 * extension.
	 */
	private final AtomicReference<AtomicLong[]> statEvict;

	/**
	 * Number of bytes currently loaded in the cache, per pack file extension.
	 */
	private final AtomicReference<AtomicLong[]> liveBytes;

	DfsBlockCacheStats() {
		this(""); //$NON-NLS-1$
	}

	DfsBlockCacheStats(String name) {
		this.name = name;
		statHit = new AtomicReference<>(newCounters());
		statMiss = new AtomicReference<>(newCounters());
		statEvict = new AtomicReference<>(newCounters());
		liveBytes = new AtomicReference<>(newCounters());
	}

	@Override
	public String getName() {
		return name;
	}

	/**
	 * Increment the {@code statHit} count.
	 *
	 * @param key
	 *            key identifying which liveBytes entry to update.
	 */
	void incrementHit(DfsStreamKey key) {
		getStat(statHit, key).incrementAndGet();
	}

	/**
	 * Increment the {@code statMiss} count.
	 *
	 * @param key
	 *            key identifying which liveBytes entry to update.
	 */
	void incrementMiss(DfsStreamKey key) {
		getStat(statMiss, key).incrementAndGet();
	}

	/**
	 * Increment the {@code statEvict} count.
	 *
	 * @param key
	 *            key identifying which liveBytes entry to update.
	 */
	void incrementEvict(DfsStreamKey key) {
		getStat(statEvict, key).incrementAndGet();
	}

	/**
	 * Add {@code size} to the {@code liveBytes} count.
	 *
	 * @param key
	 *            key identifying which liveBytes entry to update.
	 * @param size
	 *            amount to increment the count by.
	 */
	void addToLiveBytes(DfsStreamKey key, long size) {
		getStat(liveBytes, key).addAndGet(size);
	}

	@Override
	public long[] getCurrentSize() {
		return getStatVals(liveBytes);
	}

	@Override
	public long[] getHitCount() {
		return getStatVals(statHit);
	}

	@Override
	public long[] getMissCount() {
		return getStatVals(statMiss);
	}

	@Override
	public long[] getTotalRequestCount() {
		AtomicLong[] hit = statHit.get();
		AtomicLong[] miss = statMiss.get();
		long[] cnt = new long[Math.max(hit.length, miss.length)];
		for (int i = 0; i < hit.length; i++) {
			cnt[i] += hit[i].get();
		}
		for (int i = 0; i < miss.length; i++) {
			cnt[i] += miss[i].get();
		}
		return cnt;
	}

	@Override
	public long[] getHitRatio() {
		AtomicLong[] hit = statHit.get();
		AtomicLong[] miss = statMiss.get();
		long[] ratio = new long[Math.max(hit.length, miss.length)];
		for (int i = 0; i < ratio.length; i++) {
			if (i >= hit.length) {
				ratio[i] = 0;
			} else if (i >= miss.length) {
				ratio[i] = 100;
			} else {
				long hitVal = hit[i].get();
				long missVal = miss[i].get();
				long total = hitVal + missVal;
				ratio[i] = total == 0 ? 0 : hitVal * 100 / total;
			}
		}
		return ratio;
	}

	@Override
	public long[] getEvictions() {
		return getStatVals(statEvict);
	}

	private static AtomicLong[] newCounters() {
		AtomicLong[] ret = new AtomicLong[PackExt.values().length];
		for (int i = 0; i < ret.length; i++) {
			ret[i] = new AtomicLong();
		}
		return ret;
	}

	private static long[] getStatVals(AtomicReference<AtomicLong[]> stat) {
		AtomicLong[] stats = stat.get();
		long[] cnt = new long[stats.length];
		for (int i = 0; i < stats.length; i++) {
			cnt[i] = stats[i].get();
		}
		return cnt;
	}

	private static AtomicLong getStat(AtomicReference<AtomicLong[]> stats,
			DfsStreamKey key) {
		int pos = key.packExtPos;
		while (true) {
			AtomicLong[] vals = stats.get();
			if (pos < vals.length) {
				return vals[pos];
			}
			AtomicLong[] expect = vals;
			vals = new AtomicLong[Math.max(pos + 1, PackExt.values().length)];
			System.arraycopy(expect, 0, vals, 0, expect.length);
			for (int i = expect.length; i < vals.length; i++) {
				vals[i] = new AtomicLong();
			}
			if (stats.compareAndSet(expect, vals)) {
				return vals[pos];
			}
		}
	}
}
