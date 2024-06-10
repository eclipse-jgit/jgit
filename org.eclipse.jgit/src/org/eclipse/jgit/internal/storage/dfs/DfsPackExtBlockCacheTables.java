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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheConfig.DfsBlockCachePackExtConfig;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheTable.DfsBlockCacheStats;
import org.eclipse.jgit.internal.storage.pack.PackExt;

/**
 * Holds and makes available {@link DfsBlockCacheTable}s for {@link PackExt}
 * types.
 */
class DfsPackExtBlockCacheTables implements DfsBlockCacheTables {
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
	 * Builds the DfsPackExtBlockCacheTables from a list of
	 * {@link DfsBlockCachePackExtConfig}s.
	 *
	 * @param packExtCacheConfigs
	 *            list of {@link DfsBlockCachePackExtConfig}s used to configure
	 *            an instance of DfsPackExtBlockCacheTables.
	 * @return the DfsPackExtBlockCacheTables built from the given configs.
	 */
	static DfsPackExtBlockCacheTables fromPackExtCacheConfigs(
			List<DfsBlockCachePackExtConfig> packExtCacheConfigs) {
		Map<PackExt, DfsBlockCacheTable> extBlockCacheTables = new HashMap<>(
				PackExt.values().length);
		List<DfsBlockCacheTable> blockCacheTableList = new ArrayList<>(
				PackExt.values().length);
		for (DfsBlockCachePackExtConfig packExtCacheConfig : packExtCacheConfigs) {
			DfsBlockCacheTable table = new ClockBlockCacheTable(
					packExtCacheConfig.getPackExtCacheConfiguration());
			for (PackExt packExt : packExtCacheConfig.getPackExts()) {
				extBlockCacheTables.put(packExt, table);
			}
			blockCacheTableList.add(table);
		}
		return new DfsPackExtBlockCacheTables(blockCacheTableList,
				extBlockCacheTables);
	}

	@Override
	@Nullable
	public DfsBlockCacheTable getTable(PackExt packExt) {
		return extBlockCacheTables.getOrDefault(packExt, null);
	}

	@Override
	@Nullable
	public DfsBlockCacheTable getTable(DfsStreamKey key) {
		return extBlockCacheTables.getOrDefault(getPackExt(key), null);
	}

	@Override
	public List<DfsBlockCacheStats> getBlockCacheTableListStats() {
		return blockCacheTableList.stream()
				.map(DfsBlockCacheTable::getDfsBlockCacheStats)
				.collect(Collectors.toList());
	}

	private static PackExt getPackExt(DfsStreamKey key) {
		return PackExt.values()[key.packExtPos];
	}
}
