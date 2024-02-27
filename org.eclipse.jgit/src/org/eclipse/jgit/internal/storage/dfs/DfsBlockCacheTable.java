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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.internal.storage.pack.PackExt;

/**
 * Block cache table.
 */
public interface DfsBlockCacheTable {
	/**
	 * Quickly check if the cache contains block 0 of the given stream.
	 * <p>
	 * This can be useful for sophisticated pre-read algorithms to quickly
	 * determine if a file is likely already in cache, especially small
	 * reftables which may be smaller than a typical DFS block size.
	 *
	 * @param key
	 *            the file to check.
	 * @return true if block 0 (the first block) is in the cache.
	 */
	boolean hasBlock0(DfsStreamKey key);

	/**
	 * Look up a cached object, creating and loading it if it doesn't exist.
	 *
	 * @param file
	 *            the pack that "contains" the cached object.
	 * @param position
	 *            offset within <code>pack</code> of the object.
	 * @param dfsReader
	 *            current thread's reader.
	 * @param fileChannel
	 *            supplier for channel to read {@code pack}.
	 * @return the object reference.
	 * @throws IOException
	 *             the reference was not in the cache and could not be loaded.
	 */
	DfsBlock getOrLoad(BlockBasedFile file, long position, DfsReader dfsReader,
			DfsBlockCache.ReadableChannelSupplier fileChannel)
			throws IOException;

	/**
	 * Look up a cached object, creating and loading it if it doesn't exist.
	 *
	 * @param key
	 *            the stream key of the pack.
	 * @param position
	 *            the position in the key. The default should be 0.
	 * @param loader
	 *            the function to load the reference.
	 * @return the object reference.
	 * @throws IOException
	 *             the reference was not in the cache and could not be loaded.
	 */
	<T> DfsBlockCache.Ref<T> getOrLoadRef(DfsStreamKey key, long position,
			DfsBlockCache.RefLoader<T> loader) throws IOException;

	/**
	 * Put a block in the block cache.
	 *
	 * @param v
	 *            the block to put in the cache.
	 */
	void put(DfsBlock v);

	/**
	 * Put a block in the block cache.
	 *
	 * @param key
	 *            the stream key of the pack.
	 * @param pos
	 *            the position in the key.
	 * @param size
	 *            the size of the object.
	 * @param v
	 *            the object to put in the block cache.
	 * @return the object reference.
	 */
	<T> DfsBlockCache.Ref<T> put(DfsStreamKey key, long pos, long size, T v);

	/**
	 * Put an object in the block cache.
	 *
	 * @param key
	 *            the stream key of the pack.
	 * @param size
	 *            the size of the object.
	 * @param v
	 *            the object to put in the block cache.
	 * @return the object reference.
	 */
	<T> DfsBlockCache.Ref<T> putRef(DfsStreamKey key, long size, T v);

	/**
	 * Check if the block cache contains an object identified by (key,
	 * position).
	 *
	 * @param key
	 *            the stream key of the pack.
	 * @param position
	 *            the position in the key.
	 * @return if the block cache contains the object identified by (key,
	 *         position).
	 */
	boolean contains(DfsStreamKey key, long position);

	/**
	 * Get the object identified by (key, position) from the block cache.
	 *
	 * @param key
	 *            the stream key of the pack.
	 * @param position
	 *            the position in the key.
	 * @return the object identified by (key, position).
	 */
	<T> T get(DfsStreamKey key, long position);

	/**
	 * Get the DfsBlockCacheStats object for this block cache table's
	 * statistics.
	 *
	 * @return the DfsBlockCacheStats tracking this block cache table's
	 *         statistics.
	 */
	DfsBlockCacheStats getDfsBlockCacheStats();

	/**
	 * Keeps track of stats for a Block Cache table.
	 */
	class DfsBlockCacheStats {
		/**
		 * Number of times a block was found in the cache, per pack file
		 * extension.
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
		 * Number of bytes currently loaded in the cache, per pack file
		 * extension.
		 */
		private final AtomicReference<AtomicLong[]> liveBytes;

		DfsBlockCacheStats() {
			statHit = new AtomicReference<>(newCounters());
			statMiss = new AtomicReference<>(newCounters());
			statEvict = new AtomicReference<>(newCounters());
			liveBytes = new AtomicReference<>(newCounters());
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

		/**
		 * Get total number of bytes in the cache, per pack file extension.
		 *
		 * @return total number of bytes in the cache, per pack file extension.
		 */
		long[] getCurrentSize() {
			return getStatVals(liveBytes);
		}

		/**
		 * Get number of requests for items in the cache, per pack file
		 * extension.
		 *
		 * @return the number of requests for items in the cache, per pack file
		 *         extension.
		 */
		long[] getHitCount() {
			return getStatVals(statHit);
		}

		/**
		 * Get number of requests for items not in the cache, per pack file
		 * extension.
		 *
		 * @return the number of requests for items not in the cache, per pack
		 *         file extension.
		 */
		long[] getMissCount() {
			return getStatVals(statMiss);
		}

		/**
		 * Get total number of requests (hit + miss), per pack file extension.
		 *
		 * @return total number of requests (hit + miss), per pack file
		 *         extension.
		 */
		long[] getTotalRequestCount() {
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

		/**
		 * Get hit ratios.
		 *
		 * @return hit ratios.
		 */
		long[] getHitRatio() {
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

		/**
		 * Get number of evictions performed due to cache being full, per pack
		 * file extension.
		 *
		 * @return the number of evictions performed due to cache being full,
		 *         per pack file extension.
		 */
		long[] getEvictions() {
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
				vals = new AtomicLong[Math.max(pos + 1,
						PackExt.values().length)];
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
}