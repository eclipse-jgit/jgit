package org.eclipse.jgit.internal.storage.dfs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class ClockBlockCacheTableTest {

	@Test
	public void getDebugConfigurationWriter() {
		ClockBlockCacheTable cacheTable = new ClockBlockCacheTable(
				createBlockCacheConfig());

		assertThat(cacheTable.getDebugConfigurationWriter(), is(cacheTable));
	}

	@Test
	public void writeConfigurationDebug() {
		ClockBlockCacheTable cacheTable = new ClockBlockCacheTable(
				createBlockCacheConfig());

		String pad = "  ";
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		cacheTable.writeConfigurationDebug("", pad, new PrintWriter(
				byteArrayOutputStream, true, StandardCharsets.UTF_8));
		String writenConfig = byteArrayOutputStream
				.toString(StandardCharsets.UTF_8);

		List<String> writenLines = Arrays.asList(writenConfig.split("\n"));
		assertThat(writenLines,
				equalTo(List.of("ClockBlockCacheTable",
						"  Label: ClockBlockCacheTable", "  TableSize: 5",
						"  ConcurrencyLevel: 4", "  MaxBytes: 1024",
						"  BlockSize: 512")));
	}

	private static DfsBlockCacheConfig createBlockCacheConfig() {
		return new DfsBlockCacheConfig().setBlockSize(512)
				.setConcurrencyLevel(4).setBlockLimit(1024);
	}
}