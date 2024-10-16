package org.eclipse.jgit.internal.storage.dfs;

/**
 * Provides methods used with Block Cache statistics.
 */
public interface BlockCacheStats {
	String getName();

	/**
	 * Get total number of bytes in the cache, per pack file extension.
	 *
	 * @return total number of bytes in the cache, per pack file extension.
	 */
	long[] getCurrentSize();

	/**
	 * Get number of requests for items in the cache, per pack file extension.
	 *
	 * @return the number of requests for items in the cache, per pack file
	 *         extension.
	 */
	long[] getHitCount();

	/**
	 * Get number of requests for items not in the cache, per pack file
	 * extension.
	 *
	 * @return the number of requests for items not in the cache, per pack file
	 *         extension.
	 */
	long[] getMissCount();

	/**
	 * Get total number of requests (hit + miss), per pack file extension.
	 *
	 * @return total number of requests (hit + miss), per pack file extension.
	 */
	long[] getTotalRequestCount();

	/**
	 * Get hit ratios.
	 *
	 * @return hit ratios.
	 */
	long[] getHitRatio();

	/**
	 * Get number of evictions performed due to cache being full, per pack file
	 * extension.
	 *
	 * @return the number of evictions performed due to cache being full, per
	 *         pack file extension.
	 */
	long[] getEvictions();
}
