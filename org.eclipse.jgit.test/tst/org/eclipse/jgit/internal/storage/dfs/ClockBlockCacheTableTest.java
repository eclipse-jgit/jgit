package org.eclipse.jgit.internal.storage.dfs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

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

	private static DfsBlockCacheConfig createBlockCacheConfig() {
		return new DfsBlockCacheConfig().setBlockSize(512)
				.setConcurrencyLevel(4).setBlockLimit(1024);
	}
}