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

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheTable.DfsBlockCacheStats;
import org.eclipse.jgit.internal.storage.pack.PackExt;

/**
 * Holds and makes available {@link DfsBlockCacheTable}s for {@link PackExt}
 * types.
 */
class DfsPackExtBlockCacheTables {
	// Holds the unique tables backing the extBlockCacheTables values.
	private final List<DfsBlockCacheTable> blockCacheTableList;

	// Holds the mapping of PackExt to DfsBlockCacheTable. One table may be
	// accessible by multiple PackExt keys.
	private final Map<PackExt, DfsBlockCacheTable> extBlockCacheTables;

	DfsPackExtBlockCacheTables(List<DfsBlockCacheTable> blockCacheTableList,
			Map<PackExt, DfsBlockCacheTable> extBlockCacheTables) {
		this.blockCacheTableList = blockCacheTableList;
		this.extBlockCacheTables = extBlockCacheTables;
	}

	/**
	 * Returns the {@link DfsBlockCacheTable} for the {@link PackExt} if it is
	 * being handled by this DfsPackExtBlockCacheTables instance, or
	 * {@code null} otherwise.
	 */
	@Nullable
	DfsBlockCacheTable getTable(PackExt packExt) {
		return extBlockCacheTables.getOrDefault(packExt, null);
	}

	/**
	 * Returns the {@link DfsBlockCacheTable} for the {@link DfsStreamKey} if it
	 * is being handled by this DfsPackExtBlockCacheTables instance, or
	 * {@code null} otherwise.
	 */
	@Nullable
	DfsBlockCacheTable getTable(DfsStreamKey key) {
		return extBlockCacheTables.getOrDefault(getPackExt(key), null);
	}

	/**
	 * Returns the array of current sizes for the {@link DfsBlockCacheTable}s
	 * held by this DfsPackExtBlockCacheTables instance, ordered by
	 * {@link PackExt} ordering.
	 */
	long[] getCurrentSize() {
		return getStats(DfsBlockCacheStats::getCurrentSize);
	}

	/**
	 * Returns the array of hit counts for the {@link DfsBlockCacheTable}s held
	 * by this DfsPackExtBlockCacheTables instance, ordered by {@link PackExt}
	 * ordering.
	 */
	long[] getHitCount() {
		return getStats(DfsBlockCacheStats::getHitCount);
	}

	/**
	 * Returns the array of miss counts for the {@link DfsBlockCacheTable}s held
	 * by this DfsPackExtBlockCacheTables instance, ordered by {@link PackExt}
	 * ordering.
	 */
	long[] getMissCount() {
		return getStats(DfsBlockCacheStats::getMissCount);
	}

	/**
	 * Returns the array of total request (hit + miss) counts for the
	 * {@link DfsBlockCacheTable}s held by this DfsPackExtBlockCacheTables
	 * instance, ordered by {@link PackExt} ordering.
	 */
	long[] getTotalRequestCount() {
		return getStats(DfsBlockCacheStats::getTotalRequestCount);
	}

	/**
	 * Returns the array of hit ratios (hit / (hit + miss)) for the
	 * {@link DfsBlockCacheTable}s held by this DfsPackExtBlockCacheTables
	 * instance, ordered by {@link PackExt} ordering.
	 */
	long[] getHitRatio() {
		return getStats(DfsBlockCacheStats::getHitRatio);
	}

	/**
	 * Returns the array of eviction counts for the {@link DfsBlockCacheTable}s
	 * held by this DfsPackExtBlockCacheTables instance, ordered by
	 * {@link PackExt} ordering.
	 */
	long[] getEvictions() {
		return getStats(DfsBlockCacheStats::getEvictions);
	}

	private long[] getStats(Function<DfsBlockCacheStats, long[]> statsFn) {
		DfsPackExtStats packStats = new DfsPackExtStats();
		for (DfsBlockCacheTable blockCacheTable : blockCacheTableList) {
			packStats.add(
					statsFn.apply(blockCacheTable.getDfsBlockCacheStats()));
		}
		return packStats.getValues();
	}

	boolean hasBlock0(DfsStreamKey key) {
		DfsBlockCacheTable foundTable = getTable(key);
		if (foundTable != null) {
			return foundTable.hasBlock0(key);
		}
		for (DfsBlockCacheTable table : extBlockCacheTables.values()) {
			if (table.hasBlock0(key)) {
				return true;
			}
		}
		return false;
	}

	private static PackExt getPackExt(DfsStreamKey key) {
		return PackExt.values()[key.packExtPos];
	}
}
