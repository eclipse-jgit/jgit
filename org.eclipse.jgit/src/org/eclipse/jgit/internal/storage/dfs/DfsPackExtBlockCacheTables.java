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
import java.util.stream.Collectors;

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
	 *
	 * @param packExt
	 *            the {@link PackExt} for which a {@link DfsBlockCacheTable}
	 *            will be returned if found.
	 * @return the {@link DfsBlockCacheTable} returned for the {@link PackExt}
	 *         if found, {@code null} otherwise.
	 */
	@Nullable
	DfsBlockCacheTable getTable(PackExt packExt) {
		return extBlockCacheTables.getOrDefault(packExt, null);
	}

	/**
	 * Returns the {@link DfsBlockCacheTable} for the {@link DfsStreamKey} if it
	 * is being handled by this DfsPackExtBlockCacheTables instance, or
	 * {@code null} otherwise.
	 *
	 * @param key
	 *            the {@link DfsStreamKey} for which a
	 *            {@link DfsBlockCacheTable} will be returned if found.
	 * @return the {@link DfsBlockCacheTable} returned for the
	 *         {@link DfsStreamKey}'s {@link PackExt} if found, {@code null}
	 *         otherwise.
	 */
	@Nullable
	DfsBlockCacheTable getTable(DfsStreamKey key) {
		return extBlockCacheTables.getOrDefault(getPackExt(key), null);
	}

	/**
	 * Returns the list of {@link DfsBlockCacheStats} for the list of
	 * {@link DfsBlockCacheTable}s.
	 *
	 * @return the list of {@link DfsBlockCacheStats} for the list of
	 *         {@link DfsBlockCacheTable}s.
	 */
	List<DfsBlockCacheStats> getBlockCacheTableListStats() {
		return blockCacheTableList.stream()
				.map(DfsBlockCacheTable::getDfsBlockCacheStats)
				.collect(Collectors.toList());
	}

	private static PackExt getPackExt(DfsStreamKey key) {
		return PackExt.values()[key.packExtPos];
	}
}
