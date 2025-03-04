package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheConfig.DEFAULT_NAME;
import static org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheTable.BlockCacheStats;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isA;

import java.util.List;

import org.junit.Test;

public class ClockBlockCacheTableTest {
	private static final String NAME = "name";

	@Test
	public void getName_nameNotConfigured_returnsDefaultName() {
		ClockBlockCacheTable cacheTable = new ClockBlockCacheTable(
				createBlockCacheConfig());

		assertThat(cacheTable.getName(), equalTo(DEFAULT_NAME));
	}

	@Test
	public void getName_nameConfigured_returnsConfiguredName() {
		ClockBlockCacheTable cacheTable = new ClockBlockCacheTable(
				createBlockCacheConfig().setName(NAME));

		assertThat(cacheTable.getName(), equalTo(NAME));
	}

	@Test
	public void getBlockCacheStats_nameNotConfigured_returnsBlockCacheStatsWithDefaultName() {
		ClockBlockCacheTable cacheTable = new ClockBlockCacheTable(
				createBlockCacheConfig());

		assertThat(cacheTable.getBlockCacheStats(), hasSize(1));
		assertThat(cacheTable.getBlockCacheStats().get(0).getName(),
				equalTo(DEFAULT_NAME));
	}

	@Test
	public void getBlockCacheStats_nameConfigured_returnsBlockCacheStatsWithConfiguredName() {
		ClockBlockCacheTable cacheTable = new ClockBlockCacheTable(
				createBlockCacheConfig().setName(NAME));

		assertThat(cacheTable.getBlockCacheStats(), hasSize(1));
		assertThat(cacheTable.getBlockCacheStats().get(0).getName(),
				equalTo(NAME));
	}

	@Test
	public void getAllBlockCacheStats() {
		ClockBlockCacheTable cacheTable = new ClockBlockCacheTable(
				createBlockCacheConfig());

		List<BlockCacheStats> blockCacheStats = cacheTable.getBlockCacheStats();
		assertThat(blockCacheStats, contains(isA(BlockCacheStats.class)));
	}

	private static DfsBlockCacheConfig createBlockCacheConfig() {
		return new DfsBlockCacheConfig().setBlockSize(512)
				.setConcurrencyLevel(4).setBlockLimit(1024);
	}
}