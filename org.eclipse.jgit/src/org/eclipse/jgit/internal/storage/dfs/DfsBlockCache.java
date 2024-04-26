/*
 * Copyright (C) 2008-2011, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import org.eclipse.jgit.internal.storage.pack.PackExt;

/**
 * Caches slices of a
 * {@link org.eclipse.jgit.internal.storage.dfs.BlockBasedFile} in memory for
 * faster read access.
 * <p>
 * The DfsBlockCache serves as a Java based "buffer cache", loading segments of
 * a BlockBasedFile into the JVM heap prior to use. As JGit often wants to do
 * reads of only tiny slices of a file, the DfsBlockCache tries to smooth out
 * these tiny reads into larger block-sized IO operations.
 * <p>
 * Its too expensive during object access to be accurate with a least recently
 * used (LRU) algorithm. Strictly ordering every read is a lot of overhead that
 * typically doesn't yield a corresponding benefit to the application.
 * <p>
 * The key tuple is passed through to methods as a pair of parameters rather
 * than as a single Object, thus reducing the transient memory allocations of
 * callers. It is more efficient to avoid the allocation, as we can't be 100%
 * sure that a JIT would be able to stack-allocate a key tuple.
 */
public final class DfsBlockCache {
	private static volatile DfsBlockCache cache;

	static {
		reconfigure(new DfsBlockCacheConfig());
	}

	/**
	 * Modify the configuration of the window cache.
	 * <p>
	 * The new configuration is applied immediately, and the existing cache is
	 * cleared.
	 *
	 * @param cfg
	 *            the new window cache configuration.
	 * @throws java.lang.IllegalArgumentException
	 *             the cache configuration contains one or more invalid
	 *             settings, usually too low of a limit.
	 */
	public static void reconfigure(DfsBlockCacheConfig cfg) {
		cache = new DfsBlockCache(cfg);
	}

	/**
	 * Get the currently active DfsBlockCache.
	 *
	 * @return the currently active DfsBlockCache.
	 */
	public static DfsBlockCache getInstance() {
		return cache;
	}

	private final DfsBlockCacheTable dfsBlockCacheTable;

	/** Maximum number of bytes the cache should hold. */
	private final long maxBytes;

	/** Pack files smaller than this size can be copied through the cache. */
	private final long maxStreamThroughCache;

	/**
	 * Suggested block size to read from pack files in.
	 * <p>
	 * If a pack file does not have a native block size, this size will be used.
	 * <p>
	 * If a pack file has a native size, a whole multiple of the native size
	 * will be used until it matches this size.
	 * <p>
	 * The value for blockSize must be a power of 2.
	 */
	private final int blockSize;

	/** Limits of cache hot count per pack file extension. */
	private final int[] cacheHotLimits = new int[PackExt.values().length];

	private DfsBlockCache(DfsBlockCacheConfig cfg) {
		maxBytes = cfg.getBlockLimit();
		blockSize = cfg.getBlockSize();
		double streamRatio = cfg.getStreamRatio();
		maxStreamThroughCache = (long) (maxBytes * streamRatio);

		dfsBlockCacheTable = new ClockBlockCacheTable(cfg);

		for (int i = 0; i < PackExt.values().length; ++i) {
			Integer limit = cfg.getCacheHotMap().get(PackExt.values()[i]);
			if (limit != null && limit.intValue() > 0) {
				cacheHotLimits[i] = limit.intValue();
			} else {
				cacheHotLimits[i] = DfsBlockCacheConfig.DEFAULT_CACHE_HOT_MAX;
			}
		}
	}

	boolean shouldCopyThroughCache(long length) {
		return length <= maxStreamThroughCache;
	}

	/**
	 * Get total number of bytes in the cache, per pack file extension.
	 *
	 * @return total number of bytes in the cache, per pack file extension.
	 */
	public long[] getCurrentSize() {
		return dfsBlockCacheTable.getDfsBlockCacheStats().getCurrentSize();
	}

	/**
	 * Get 0..100, defining how full the cache is.
	 *
	 * @return 0..100, defining how full the cache is.
	 */
	public long getFillPercentage() {
		return LongStream.of(getCurrentSize()).sum() * 100 / maxBytes;
	}

	/**
	 * Get number of requests for items in the cache, per pack file extension.
	 *
	 * @return number of requests for items in the cache, per pack file
	 *         extension.
	 */
	public long[] getHitCount() {
		return dfsBlockCacheTable.getDfsBlockCacheStats().getHitCount();
	}

	/**
	 * Get number of requests for items not in the cache, per pack file
	 * extension.
	 *
	 * @return number of requests for items not in the cache, per pack file
	 *         extension.
	 */
	public long[] getMissCount() {
		return dfsBlockCacheTable.getDfsBlockCacheStats().getMissCount();
	}

	/**
	 * Get total number of requests (hit + miss), per pack file extension.
	 *
	 * @return total number of requests (hit + miss), per pack file extension.
	 */
	public long[] getTotalRequestCount() {
		return dfsBlockCacheTable.getDfsBlockCacheStats()
				.getTotalRequestCount();
	}

	/**
	 * Get hit ratios
	 *
	 * @return hit ratios
	 */
	public long[] getHitRatio() {
		return dfsBlockCacheTable.getDfsBlockCacheStats().getHitRatio();
	}

	/**
	 * Get number of evictions performed due to cache being full, per pack file
	 * extension.
	 *
	 * @return number of evictions performed due to cache being full, per pack
	 *         file extension.
	 */
	public long[] getEvictions() {
		return dfsBlockCacheTable.getDfsBlockCacheStats().getEvictions();
	}

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
	public boolean hasBlock0(DfsStreamKey key) {
		return dfsBlockCacheTable.hasBlock0(key);
	}

	int getBlockSize() {
		return blockSize;
	}

	/**
	 * Look up a cached object, creating and loading it if it doesn't exist.
	 *
	 * @param file
	 *            the pack that "contains" the cached object.
	 * @param position
	 *            offset within <code>pack</code> of the object.
	 * @param ctx
	 *            current thread's reader.
	 * @param fileChannel
	 *            supplier for channel to read {@code pack}.
	 * @return the object reference.
	 * @throws IOException
	 *             the reference was not in the cache and could not be loaded.
	 */
	DfsBlock getOrLoad(BlockBasedFile file, long position, DfsReader ctx,
			ReadableChannelSupplier fileChannel) throws IOException {
		return dfsBlockCacheTable.getOrLoad(file, position, ctx, fileChannel);
	}

	void put(DfsBlock v) {
		dfsBlockCacheTable.put(v);
	}

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
	<T> Ref<T> getOrLoadRef(DfsStreamKey key, long position,
			RefLoader<T> loader) throws IOException {
		return dfsBlockCacheTable.getOrLoadRef(key, position, loader);
	}

	<T> Ref<T> putRef(DfsStreamKey key, long size, T v) {
		return dfsBlockCacheTable.putRef(key, size, v);
	}

	<T> Ref<T> put(DfsStreamKey key, long pos, long size, T v) {
		return dfsBlockCacheTable.put(key, pos, size, v);
	}

	boolean contains(DfsStreamKey key, long position) {
		return dfsBlockCacheTable.contains(key, position);
	}

	<T> T get(DfsStreamKey key, long position) {
		return dfsBlockCacheTable.get(key, position);
	}

	static final class Ref<T> {
		final DfsStreamKey key;

		final long position;

		final long size;

		volatile T value;

		Ref next;

		private volatile int hotCount;

		private final AtomicInteger totalHitCount = new AtomicInteger();

		Ref(DfsStreamKey key, long position, long size, T v) {
			this.key = key;
			this.position = position;
			this.size = size;
			this.value = v;
		}

		T get() {
			T v = value;
			if (v != null) {
				markHotter();
			}
			return v;
		}

		boolean has() {
			return value != null;
		}

		void markHotter() {
			int cap = DfsBlockCache
					.getInstance().cacheHotLimits[key.packExtPos];
			hotCount = Math.min(cap, hotCount + 1);
			totalHitCount.incrementAndGet();
		}

		void markColder() {
			hotCount = Math.max(0, hotCount - 1);
		}

		boolean isHot() {
			return hotCount > 0;
		}

		int getTotalHitCount() {
			return totalHitCount.get();
		}
	}

	@FunctionalInterface
	interface RefLoader<T> {
		Ref<T> load() throws IOException;
	}

	/**
	 * Supplier for readable channel
	 */
	@FunctionalInterface
	interface ReadableChannelSupplier {
		/**
		 * Get ReadableChannel
		 *
		 * @return ReadableChannel
		 * @throws IOException
		 *             if an IO error occurred
		 */
		ReadableChannel get() throws IOException;
	}
}