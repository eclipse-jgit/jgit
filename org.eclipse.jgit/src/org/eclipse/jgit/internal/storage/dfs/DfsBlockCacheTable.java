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
import java.util.List;

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
	 * Get the list of {@link BlockCacheStats} held by this cache.
	 * <p>
	 * The returned list has a {@link BlockCacheStats} per configured cache
	 * table, with a minimum of 1 {@link BlockCacheStats} object returned.
	 *
	 * Use {@link AggregatedBlockCacheStats} to combine the results of the stats
	 * in the list for an aggregated view of the cache's stats.
	 *
	 * @return the list of {@link BlockCacheStats} held by this cache.
	 */
	List<BlockCacheStats> getBlockCacheStats();

	/**
	 * Get the name of the table.
	 *
	 * @return this table's name.
	 */
	String getName();

	/**
	 * Provides methods used with Block Cache statistics.
	 */
	interface BlockCacheStats {

		/**
		 * Get the name of the block cache generating this instance.
		 *
		 * @return this cache's name.
		 */
		String getName();

		/**
		 * Get total number of bytes in the cache, per pack file extension.
		 *
		 * @return total number of bytes in the cache, per pack file extension.
		 */
		long[] getCurrentSize();

		/**
		 * Get number of requests for items in the cache, per pack file
		 * extension.
		 *
		 * @return the number of requests for items in the cache, per pack file
		 *         extension.
		 */
		long[] getHitCount();

		/**
		 * Get number of requests for items not in the cache, per pack file
		 * extension.
		 *
		 * @return the number of requests for items not in the cache, per pack
		 *         file extension.
		 */
		long[] getMissCount();

		/**
		 * Get total number of requests (hit + miss), per pack file extension.
		 *
		 * @return total number of requests (hit + miss), per pack file
		 *         extension.
		 */
		long[] getTotalRequestCount();

		/**
		 * Get hit ratios.
		 *
		 * @return hit ratios.
		 */
		long[] getHitRatio();

		/**
		 * Get number of evictions performed due to cache being full, per pack
		 * file extension.
		 *
		 * @return the number of evictions performed due to cache being full,
		 *         per pack file extension.
		 */
		long[] getEvictions();
	}
}
