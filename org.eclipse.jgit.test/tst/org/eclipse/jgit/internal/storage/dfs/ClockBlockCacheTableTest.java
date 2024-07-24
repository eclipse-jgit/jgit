package org.eclipse.jgit.internal.storage.dfs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;

import java.util.List;

import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheTable.BlockCacheStats;
import org.junit.Test;

public class ClockBlockCacheTableTest {
	private static final String LABEL = "label";

	@Test
	public void getBlockCacheStats_labelNotConfigured_returnsBlockCacheStatsWithDefaultLabel() {
		ClockBlockCacheTable cacheTable = new ClockBlockCacheTable(
				createBlockCacheConfig());

		assertThat(cacheTable.getBlockCacheStats().getLabel(),
				equalTo(ClockBlockCacheTable.class.getSimpleName()));
	}

	@Test
	public void getBlockCacheStats_labelConfigured_returnsBlockCacheStatsWithConfiguredLabel() {
		ClockBlockCacheTable cacheTable = new ClockBlockCacheTable(
				createBlockCacheConfig().setLabel(LABEL));

		assertThat(cacheTable.getBlockCacheStats().getLabel(), equalTo(LABEL));
	}

	@Test
	public void getAllCachesBlockCacheStats() {
		ClockBlockCacheTable cacheTable = new ClockBlockCacheTable(
				createBlockCacheConfig());

		List<BlockCacheStats> blockCacheStats = cacheTable
				.getAllCachesBlockCacheStats();
		assertThat(blockCacheStats, contains(isA(BlockCacheStats.class)));
	}

	private static DfsBlockCacheConfig createBlockCacheConfig() {
		return new DfsBlockCacheConfig().setBlockSize(512)
				.setConcurrencyLevel(4).setBlockLimit(1024);
	}
}