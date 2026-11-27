package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheConfig.DEFAULT_NAME;
import static org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheTable.BlockCacheStats;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import org.eclipse.jgit.internal.storage.pack.PackExt;
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

	@Test
	public void getAndMarkIfCold_marksOnlyColdRef() {
		DfsBlockCache.Ref<String> ref = new DfsBlockCache.Ref<>(key("a"), 0,
				1, "a");
		assertThat(ref.isHot(), equalTo(false));

		assertThat(ref.getAndMarkIfCold(), equalTo("a"));
		assertThat(ref.isHot(), equalTo(true));
		assertThat(ref.getAndMarkIfCold(), equalTo("a"));
		assertThat(ref.getTotalHitCount(), equalTo(1));

		ref.markColder();
		assertThat(ref.getAndMarkIfCold(), equalTo("a"));
		assertThat(ref.isHot(), equalTo(true));
		assertThat(ref.getTotalHitCount(), equalTo(2));
	}

	@Test
	public void getAndMarkIfCold_evictedRef() {
		ClockBlockCacheTable cacheTable = new ClockBlockCacheTable(
				createBlockCacheConfig());
		DfsBlockCache.Ref<String> a = cacheTable.put(key("a"), 0, 512, "a");
		cacheTable.put(key("b"), 0, 512, "b");
		cacheTable.put(key("c"), 0, 512, "c");

		assertThat(a.getAndMarkIfCold(), nullValue());
	}

	private static DfsStreamKey key(String name) {
		return DfsStreamKey.of(new DfsRepositoryDescription("repo"), name,
				PackExt.INDEX);
	}

	private static DfsBlockCacheConfig createBlockCacheConfig() {
		return new DfsBlockCacheConfig().setBlockSize(512)
				.setConcurrencyLevel(4).setBlockLimit(1024);
	}
}