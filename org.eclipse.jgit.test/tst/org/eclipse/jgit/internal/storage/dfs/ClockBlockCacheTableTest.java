package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheConfig.DEFAULT_NAME;
import static org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheTable.BlockCacheStats;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class ClockBlockCacheTableTest {
	private static final String NAME = "name";

	private static final int BLOCK_SIZE = 512;

	private static final int BLOCK_LIMIT = 1024;

	private static final int CONCURRENCY_LEVEL = 4;

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
	public void getDebugConfigurationWriter() {
		ClockBlockCacheTable cacheTable = new ClockBlockCacheTable(
				createBlockCacheConfig());

		assertThat(cacheTable.getDebugConfigurationWriter(), is(cacheTable));
	}

	@Test
	public void writeConfigurationDebug() {
		ClockBlockCacheTable cacheTable = new ClockBlockCacheTable(
				createBlockCacheConfig().setName(NAME));

		String pad = "  ";
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		cacheTable.writeConfigurationDebug("", pad, new PrintWriter(
				byteArrayOutputStream, true, StandardCharsets.UTF_8));
		String writenConfig = byteArrayOutputStream
				.toString(StandardCharsets.UTF_8);

		List<String> writenLines = Arrays.asList(writenConfig.split("\n"));
		assertThat(writenLines, equalTo(List.of("ClockBlockCacheTable",
				"  Name: " + NAME, "  TableSize: 5",
				"  ConcurrencyLevel: " + CONCURRENCY_LEVEL,
				"  MaxBytes: " + BLOCK_LIMIT, "  BlockSize: " + BLOCK_SIZE)));
	}

	private static DfsBlockCacheConfig createBlockCacheConfig() {
		return new DfsBlockCacheConfig().setBlockSize(BLOCK_SIZE)
				.setConcurrencyLevel(CONCURRENCY_LEVEL)
				.setBlockLimit(BLOCK_LIMIT);
	}
}
