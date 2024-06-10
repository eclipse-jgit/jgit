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

import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheConfig.DfsBlockCachePackExtConfig;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheTables.DfsBlockCacheTablesFactory;

class DfsPackExtBlockCacheTablesFactory implements DfsBlockCacheTablesFactory {

	@Override
	public DfsBlockCacheTables fromPackExtCacheConfigs(
			List<DfsBlockCachePackExtConfig> packExtCacheConfigs) {
		return DfsPackExtBlockCacheTables
				.fromPackExtCacheConfigs(packExtCacheConfigs);
	}
}
