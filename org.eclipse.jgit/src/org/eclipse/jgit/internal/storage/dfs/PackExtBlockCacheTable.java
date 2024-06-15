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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCache.ReadableChannelSupplier;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCache.Ref;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCache.RefLoader;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheConfig.DfsBlockCachePackExtConfig;
import org.eclipse.jgit.internal.storage.pack.PackExt;

/**
 * A table that holds multiple cache tables accessed by {@link PackExt} types.
 *
 * <p>
 * Allows the separation of entries from different {@link PackExt} types to
 * limit churn in cache caused by entries of differing sizes.
 * <p>
 * Separating these tables enables the fine-tuning of cache tables per extension
 * type.
 */
class PackExtBlockCacheTable implements DfsBlockCacheTable {
	private final DfsBlockCacheTable defaultBlockCacheTable;

	// Holds the unique tables backing the extBlockCacheTables values.
	private final List<DfsBlockCacheTable> blockCacheTableList;

	// Holds the mapping of PackExt to DfsBlockCacheTables.
	// The relation between the size of extBlockCacheTables entries and
	// blockCacheTableList entries is:
	// blockCacheTableList.size() <= extBlockCacheTables.size()
	private final Map<PackExt, DfsBlockCacheTable> extBlockCacheTables;

	private PackExtBlockCacheTable(DfsBlockCacheTable defaultBlockCacheTable,
			List<DfsBlockCacheTable> blockCacheTableList,
			Map<PackExt, DfsBlockCacheTable> extBlockCacheTables) {
		this.defaultBlockCacheTable = defaultBlockCacheTable;
		this.blockCacheTableList = blockCacheTableList;
		this.extBlockCacheTables = extBlockCacheTables;
	}

	/**
	 * Builds the PackExtBlockCacheTable from a list of
	 * {@link DfsBlockCachePackExtConfig}s.
	 *
	 * @param cacheConfig
	 *            {@link DfsBlockCacheConfig} containing
	 *            {@link DfsBlockCachePackExtConfig}s used to configure
	 *            PackExtBlockCacheTable. The {@link DfsBlockCacheConfig} holds
	 *            the configuration for the default cache table.
	 * @return the cache table built from the given configs.
	 * @throws IllegalArgumentException
	 *             when no {@link DfsBlockCachePackExtConfig} exists in the
	 *             {@link DfsBlockCacheConfig}.
	 */
	static PackExtBlockCacheTable fromBlockCacheConfigs(
			DfsBlockCacheConfig cacheConfig) {
		DfsBlockCacheTable defaultTable = new ClockBlockCacheTable(cacheConfig);
		List<PackExtsCacheTablePair> blockCacheTableList = new ArrayList<>();
		List<DfsBlockCachePackExtConfig> packExtConfigs = cacheConfig
				.getPackExtCacheConfigurations();
		if (packExtConfigs == null || packExtConfigs.size() == 0) {
			throw new IllegalArgumentException(
					JGitText.get().noPackExtConfigurationGiven);
		}
		for (DfsBlockCachePackExtConfig packExtCacheConfig : packExtConfigs) {
			blockCacheTableList.add(
					new PackExtsCacheTablePair(packExtCacheConfig.getPackExts(),
							new ClockBlockCacheTable(packExtCacheConfig
									.getPackExtCacheConfiguration())));
		}
		return fromCacheTables(defaultTable, blockCacheTableList);
	}

	/**
	 * Creates a new PackExtBlockCacheTable from the combination of a default
	 * {@link DfsBlockCacheTable} and a list of {@link PackExtsCacheTablePair}s.
	 * <p>
	 * This method allows for the PackExtBlockCacheTable to handle a mapping of
	 * {@link PackExt}s to arbitrarily defined {@link DfsBlockCacheTable}
	 * implementations. This is especially useful for users wishing to implement
	 * custom cache tables.
	 *
	 * @param defaultBlockCacheTable
	 *            the default table used when a handling a {@link PackExt} type
	 *            that does not map to a {@link DfsBlockCacheTable} mapped by
	 *            packExtsCacheTablePairs.
	 * @param packExtsCacheTablePairs
	 *            the mapping of {@link PackExt}s to
	 *            {@link DfsBlockCacheTable}s. A single
	 *            {@link DfsBlockCacheTable} can be defined for multiple
	 *            {@link PackExt}s in a many-to-one relationship.
	 * @return the PackExtBlockCacheTable created from the
	 *         defaultBlockCacheTable and packExtsCacheTablePairs mapping.
	 * @throws IllegalArgumentException
	 *             when a {@link PackExt} is defined for multiple
	 *             {@link DfsBlockCacheTable}s.
	 */
	public static PackExtBlockCacheTable fromCacheTables(
			DfsBlockCacheTable defaultBlockCacheTable,
			List<PackExtsCacheTablePair> packExtsCacheTablePairs) {
		List<DfsBlockCacheTable> blockCacheTables = new ArrayList<>();
		blockCacheTables.add(defaultBlockCacheTable);
		blockCacheTables.addAll(packExtsCacheTablePairs.stream()
				.map(PackExtsCacheTablePair::getBlockCacheTable)
				.collect(Collectors.toList()));
		if (blockCacheTables.size() != Set.copyOf(blockCacheTables).size()) {
			throw new IllegalArgumentException(
					JGitText.get().duplicateCacheTablesGiven);
		}
		Map<PackExt, DfsBlockCacheTable> packExtDfsBlockCacheTableMap = new HashMap<>();
		for (PackExtsCacheTablePair tablePair : packExtsCacheTablePairs) {
			Set<PackExt> packExtsDuplicates = intersection(
					packExtDfsBlockCacheTableMap.keySet(),
					tablePair.getPackExts());
			if (packExtsDuplicates.size() > 0) {
				String duplicatePackExts = packExtsDuplicates.stream()
						.map(PackExt::toString)
						.collect(Collectors.joining(","));
				throw new IllegalArgumentException(MessageFormat.format(
						JGitText.get().duplicatePackExtensionsForCacheTables,
						duplicatePackExts));
			}
			for (PackExt packExt : tablePair.getPackExts()) {
				packExtDfsBlockCacheTableMap.put(packExt,
						tablePair.getBlockCacheTable());
			}
		}
		return new PackExtBlockCacheTable(defaultBlockCacheTable,
				blockCacheTables, packExtDfsBlockCacheTableMap);
	}

	private static <T> Set<T> intersection(Set<T> first, Set<T> second) {
		Set<T> ret = new HashSet<>();
		for (T entry : second) {
			if (first.contains(entry)) {
				ret.add(entry);
			}
		}
		return ret;
	}

	@Override
	public boolean hasBlock0(DfsStreamKey key) {
		return getTable(key).hasBlock0(key);
	}

	@Override
	public DfsBlock getOrLoad(BlockBasedFile file, long position,
			DfsReader dfsReader, ReadableChannelSupplier fileChannel)
			throws IOException {
		return getTable(file.ext).getOrLoad(file, position, dfsReader,
				fileChannel);
	}

	@Override
	public <T> Ref<T> getOrLoadRef(DfsStreamKey key, long position,
			RefLoader<T> loader) throws IOException {
		return getTable(key).getOrLoadRef(key, position, loader);
	}

	@Override
	public void put(DfsBlock v) {
		getTable(v.stream).put(v);
	}

	@Override
	public <T> Ref<T> put(DfsStreamKey key, long pos, long size, T v) {
		return getTable(key).put(key, pos, size, v);
	}

	@Override
	public <T> Ref<T> putRef(DfsStreamKey key, long size, T v) {
		return getTable(key).putRef(key, size, v);
	}

	@Override
	public boolean contains(DfsStreamKey key, long position) {
		return getTable(key).contains(key, position);
	}

	@Override
	public <T> T get(DfsStreamKey key, long position) {
		return getTable(key).get(key, position);
	}

	@Override
	public BlockCacheStats getBlockCacheStats() {
		return new CacheStats(PackExtBlockCacheTable.class.getSimpleName(),
				blockCacheTableList.stream()
						.map(DfsBlockCacheTable::getBlockCacheStats)
						.collect(Collectors.toList()));
	}

	@Override
	public List<BlockCacheStats> getAllCachesBlockCacheStats() {
		return blockCacheTableList.stream().flatMap(
				cacheTable -> cacheTable.getAllCachesBlockCacheStats().stream())
				.collect(Collectors.toList());
	}

	private DfsBlockCacheTable getTable(PackExt packExt) {
		return extBlockCacheTables.getOrDefault(packExt,
				defaultBlockCacheTable);
	}

	private DfsBlockCacheTable getTable(DfsStreamKey key) {
		return extBlockCacheTables.getOrDefault(getPackExt(key),
				defaultBlockCacheTable);
	}

	private static PackExt getPackExt(DfsStreamKey key) {
		return PackExt.values()[key.packExtPos];
	}

	private static class CacheStats implements BlockCacheStats {
		private final String label;

		private final List<BlockCacheStats> blockCacheStats;

		private CacheStats(String label,
				List<BlockCacheStats> blockCacheStats) {
			this.label = label;
			this.blockCacheStats = blockCacheStats;
		}

		@Override
		public String getLabel() {
			return label;
		}

		@Override
		public long[] getCurrentSize() {
			long[] sums = emptyPackStats();
			for (BlockCacheStats blockCacheStatsEntry : blockCacheStats) {
				sums = add(sums, blockCacheStatsEntry.getCurrentSize());
			}
			return sums;
		}

		@Override
		public long[] getHitCount() {
			long[] sums = emptyPackStats();
			for (BlockCacheStats blockCacheStatsEntry : blockCacheStats) {
				sums = add(sums, blockCacheStatsEntry.getHitCount());
			}
			return sums;
		}

		@Override
		public long[] getMissCount() {
			long[] sums = emptyPackStats();
			for (BlockCacheStats blockCacheStatsEntry : blockCacheStats) {
				sums = add(sums, blockCacheStatsEntry.getMissCount());
			}
			return sums;
		}

		@Override
		public long[] getTotalRequestCount() {
			long[] sums = emptyPackStats();
			for (BlockCacheStats blockCacheStatsEntry : blockCacheStats) {
				sums = add(sums, blockCacheStatsEntry.getTotalRequestCount());
			}
			return sums;
		}

		@Override
		public long[] getHitRatio() {
			long[] hit = getHitCount();
			long[] miss = getMissCount();
			long[] ratio = new long[Math.max(hit.length, miss.length)];
			for (int i = 0; i < ratio.length; i++) {
				if (i >= hit.length) {
					ratio[i] = 0;
				} else if (i >= miss.length) {
					ratio[i] = 100;
				} else {
					long total = hit[i] + miss[i];
					ratio[i] = total == 0 ? 0 : hit[i] * 100 / total;
				}
			}
			return ratio;
		}

		@Override
		public long[] getEvictions() {
			long[] sums = emptyPackStats();
			for (BlockCacheStats blockCacheStatsEntry : blockCacheStats) {
				sums = add(sums, blockCacheStatsEntry.getEvictions());
			}
			return sums;
		}

		private static long[] emptyPackStats() {
			return new long[PackExt.values().length];
		}

		private static long[] add(long[] first, long[] second) {
			long[] sums = new long[Integer.max(first.length, second.length)];
			int i;
			for (i = 0; i < Integer.min(first.length, second.length); i++) {
				sums[i] = first[i] + second[i];
			}
			for (int j = i; j < first.length; j++) {
				sums[j] = first[i];
			}
			for (int j = i; j < second.length; j++) {
				sums[j] = second[i];
			}
			return sums;
		}
	}

	/**
	 * Class holding a pair defining 1 or more {@link PackExt}s mapping to a
	 * {@link DfsBlockCacheTable}.
	 */
	static class PackExtsCacheTablePair {
		private final Set<PackExt> packExts;

		private final DfsBlockCacheTable blockCacheTable;

		PackExtsCacheTablePair(Set<PackExt> packExts,
				DfsBlockCacheTable blockCacheTable) {
			this.packExts = packExts;
			this.blockCacheTable = blockCacheTable;
		}

		/**
		 * @return the set of {@link PackExt}s held by this class.
		 */
		public Set<PackExt> getPackExts() {
			return packExts;
		}

		/**
		 * @return the {@link DfsBlockCacheTable} held by this class.
		 */
		public DfsBlockCacheTable getBlockCacheTable() {
			return blockCacheTable;
		}
	}
}
