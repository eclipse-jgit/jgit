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
