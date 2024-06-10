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

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheConfig.DfsBlockCachePackExtConfig;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheTable.DfsBlockCacheStats;
import org.eclipse.jgit.internal.storage.pack.PackExt;

/**
 * Holds and makes available {@link DfsBlockCacheTable}s for {@link PackExt}
 * types.
 */
interface DfsBlockCacheTables {
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
	DfsBlockCacheTable getTable(PackExt packExt);

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
	DfsBlockCacheTable getTable(DfsStreamKey key);

	/**
	 * Returns the list of {@link DfsBlockCacheStats} for the list of
	 * {@link DfsBlockCacheTable}s.
	 *
	 * @return the list of {@link DfsBlockCacheStats} for the list of
	 *         {@link DfsBlockCacheTable}s.
	 */
	List<DfsBlockCacheStats> getBlockCacheTableListStats();

	/**
	 * Interface for Cache Table factories.
	 */
	interface DfsBlockCacheTablesFactory {

		/**
		 * Method used to create new {@link DfsBlockCacheTables} from a list of
		 * {@link DfsBlockCachePackExtConfig}s.
		 *
		 * @param packExtCacheConfigs
		 *            the {@link DfsBlockCachePackExtConfig}s used to create new
		 *            {@link DfsBlockCacheTables}.
		 * @return the newly created {@link DfsBlockCacheTables} instance.
		 */
		DfsBlockCacheTables fromPackExtCacheConfigs(
				List<DfsBlockCachePackExtConfig> packExtCacheConfigs);
	}
}
