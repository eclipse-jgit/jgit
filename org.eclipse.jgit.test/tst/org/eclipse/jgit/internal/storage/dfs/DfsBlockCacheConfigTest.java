/*
 * Copyright (C) 2016, Philipp Marx <philippmarx@gmx.de> and
 * other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v1.0 which accompanies this
 * distribution, is reproduced below, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheConfig.DEFAULT_NAME;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_CORE_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_DFS_CACHE_PREFIX;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_DFS_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_BLOCK_LIMIT;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_BLOCK_SIZE;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_CONCURRENCY_LEVEL;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PACK_EXTENSIONS;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_STREAM_RATIO;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheConfig.DfsBlockCachePackExtConfig;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

@SuppressWarnings("boxing")
public class DfsBlockCacheConfigTest {

	@Test
	public void blockSizeNotPowerOfTwoExpectsException() {
		assertThrows(JGitText.get().blockSizeNotPowerOf2,
				IllegalArgumentException.class,
				() -> new DfsBlockCacheConfig().setBlockSize(1000));
	}

	@Test
	public void negativeBlockSizeIsConvertedToDefault() {
		DfsBlockCacheConfig config = new DfsBlockCacheConfig();
		config.setBlockSize(-1);

		assertThat(config.getBlockSize(), is(512));
	}

	@Test
	public void tooSmallBlockSizeIsConvertedToDefault() {
		DfsBlockCacheConfig config = new DfsBlockCacheConfig();
		config.setBlockSize(10);

		assertThat(config.getBlockSize(), is(512));
	}

	@Test
	public void validBlockSize() {
		DfsBlockCacheConfig config = new DfsBlockCacheConfig();
		config.setBlockSize(65536);

		assertThat(config.getBlockSize(), is(65536));
	}

	@Test
	public void fromConfigs() {
		Config config = new Config();
		config.setLong(CONFIG_CORE_SECTION, CONFIG_DFS_SECTION,
				CONFIG_KEY_BLOCK_LIMIT, 50 * 1024);
		config.setInt(CONFIG_CORE_SECTION, CONFIG_DFS_SECTION,
				CONFIG_KEY_BLOCK_SIZE, 1024);
		config.setInt(CONFIG_CORE_SECTION, CONFIG_DFS_SECTION,
				CONFIG_KEY_CONCURRENCY_LEVEL, 3);
		config.setString(CONFIG_CORE_SECTION, CONFIG_DFS_SECTION,
				CONFIG_KEY_STREAM_RATIO, "0.5");

		DfsBlockCacheConfig cacheConfig = new DfsBlockCacheConfig()
				.fromConfig(config);
		assertThat(cacheConfig.getBlockLimit(), is(50L * 1024L));
		assertThat(cacheConfig.getBlockSize(), is(1024));
		assertThat(cacheConfig.getConcurrencyLevel(), is(3));
		assertThat(cacheConfig.getStreamRatio(), closeTo(0.5, 0.0001));
	}

	@Test
	public void fromConfig_blockLimitNotAMultipleOfBlockSize_throws() {
		Config config = new Config();
		config.setLong(CONFIG_CORE_SECTION, CONFIG_DFS_SECTION,
				CONFIG_KEY_BLOCK_LIMIT, 1025);
		config.setInt(CONFIG_CORE_SECTION, CONFIG_DFS_SECTION,
				CONFIG_KEY_BLOCK_SIZE, 1024);

		assertThrows(IllegalArgumentException.class,
				() -> new DfsBlockCacheConfig().fromConfig(config));
	}

	@Test
	public void fromConfig_streamRatioInvalidFormat_throws() {
		Config config = new Config();
		config.setString(CONFIG_CORE_SECTION, CONFIG_DFS_SECTION,
				CONFIG_KEY_STREAM_RATIO, "0.a5");

		assertThrows(IllegalArgumentException.class,
				() -> new DfsBlockCacheConfig().fromConfig(config));
	}

	@Test
	public void fromConfig_generatesDfsBlockCachePackExtConfigs() {
		Config config = new Config();
		addPackExtConfigEntry(config, "pack", List.of(PackExt.PACK),
				/* blockLimit= */ 20 * 512, /* blockSize= */ 512);

		addPackExtConfigEntry(config, "bitmap", List.of(PackExt.BITMAP_INDEX),
				/* blockLimit= */ 25 * 1024, /* blockSize= */ 1024);

		addPackExtConfigEntry(config, "index",
				List.of(PackExt.INDEX, PackExt.OBJECT_SIZE_INDEX,
						PackExt.REVERSE_INDEX),
				/* blockLimit= */ 30 * 1024, /* blockSize= */ 1024);

		DfsBlockCacheConfig cacheConfig = new DfsBlockCacheConfig()
				.fromConfig(config);
		var configs = cacheConfig.getPackExtCacheConfigurations();
		assertThat(configs, hasSize(3));
		var packConfig = getConfigForExt(configs, PackExt.PACK);
		assertThat(packConfig.getBlockLimit(), is(20L * 512L));
		assertThat(packConfig.getBlockSize(), is(512));

		var bitmapConfig = getConfigForExt(configs, PackExt.BITMAP_INDEX);
		assertThat(bitmapConfig.getBlockLimit(), is(25L * 1024L));
		assertThat(bitmapConfig.getBlockSize(), is(1024));

		var indexConfig = getConfigForExt(configs, PackExt.INDEX);
		assertThat(indexConfig.getBlockLimit(), is(30L * 1024L));
		assertThat(indexConfig.getBlockSize(), is(1024));
		assertThat(getConfigForExt(configs, PackExt.OBJECT_SIZE_INDEX),
				is(indexConfig));
		assertThat(getConfigForExt(configs, PackExt.REVERSE_INDEX),
				is(indexConfig));
	}

	@Test
	public void fromConfig_withExistingCacheHotMap_configWithPackExtConfigsHasHotMaps() {
		Config config = new Config();
		addPackExtConfigEntry(config, "pack", List.of(PackExt.PACK),
				/* blockLimit= */ 20 * 512, /* blockSize= */ 512);

		addPackExtConfigEntry(config, "bitmap", List.of(PackExt.BITMAP_INDEX),
				/* blockLimit= */ 25 * 1024, /* blockSize= */ 1024);

		addPackExtConfigEntry(config, "index",
				List.of(PackExt.INDEX, PackExt.OBJECT_SIZE_INDEX,
						PackExt.REVERSE_INDEX),
				/* blockLimit= */ 30 * 1024, /* blockSize= */ 1024);

		Map<PackExt, Integer> cacheHotMap = Map.of(PackExt.PACK, 1,
				PackExt.BITMAP_INDEX, 2, PackExt.INDEX, 3, PackExt.REFTABLE, 4);

		DfsBlockCacheConfig cacheConfig = new DfsBlockCacheConfig();
		cacheConfig.setCacheHotMap(cacheHotMap);
		cacheConfig.fromConfig(config);

		var configs = cacheConfig.getPackExtCacheConfigurations();
		assertThat(cacheConfig.getCacheHotMap(), is(cacheHotMap));
		assertThat(configs, hasSize(3));
		var packConfig = getConfigForExt(configs, PackExt.PACK);
		assertThat(packConfig.getCacheHotMap(), is(Map.of(PackExt.PACK, 1)));

		var bitmapConfig = getConfigForExt(configs, PackExt.BITMAP_INDEX);
		assertThat(bitmapConfig.getCacheHotMap(),
				is(Map.of(PackExt.BITMAP_INDEX, 2)));

		var indexConfig = getConfigForExt(configs, PackExt.INDEX);
		assertThat(indexConfig.getCacheHotMap(), is(Map.of(PackExt.INDEX, 3)));
	}

	@Test
	public void setCacheHotMap_configWithPackExtConfigs_setsHotMaps() {
		Config config = new Config();
		addPackExtConfigEntry(config, "pack", List.of(PackExt.PACK),
				/* blockLimit= */ 20 * 512, /* blockSize= */ 512);

		addPackExtConfigEntry(config, "bitmap", List.of(PackExt.BITMAP_INDEX),
				/* blockLimit= */ 25 * 1024, /* blockSize= */ 1024);

		addPackExtConfigEntry(config, "index",
				List.of(PackExt.INDEX, PackExt.OBJECT_SIZE_INDEX,
						PackExt.REVERSE_INDEX),
				/* blockLimit= */ 30 * 1024, /* blockSize= */ 1024);

		Map<PackExt, Integer> cacheHotMap = Map.of(PackExt.PACK, 1,
				PackExt.BITMAP_INDEX, 2, PackExt.INDEX, 3, PackExt.REFTABLE, 4);

		DfsBlockCacheConfig cacheConfig = new DfsBlockCacheConfig()
				.fromConfig(config);
		cacheConfig.setCacheHotMap(cacheHotMap);

		var configs = cacheConfig.getPackExtCacheConfigurations();
		assertThat(cacheConfig.getCacheHotMap(), is(cacheHotMap));
		assertThat(configs, hasSize(3));
		var packConfig = getConfigForExt(configs, PackExt.PACK);
		assertThat(packConfig.getCacheHotMap(), is(Map.of(PackExt.PACK, 1)));

		var bitmapConfig = getConfigForExt(configs, PackExt.BITMAP_INDEX);
		assertThat(bitmapConfig.getCacheHotMap(),
				is(Map.of(PackExt.BITMAP_INDEX, 2)));

		var indexConfig = getConfigForExt(configs, PackExt.INDEX);
		assertThat(indexConfig.getCacheHotMap(), is(Map.of(PackExt.INDEX, 3)));
	}

	@Test
	public void fromConfigs_baseConfigOnly_nameSetFromConfigDfsSubSection() {
		Config config = new Config();

		DfsBlockCacheConfig blockCacheConfig = new DfsBlockCacheConfig()
				.fromConfig(config);
		assertThat(blockCacheConfig.getName(), equalTo(DEFAULT_NAME));
	}

	@Test
	public void fromConfigs_namesSetFromConfigDfsCachePrefixSubSections() {
		Config config = new Config();
		config.setString(CONFIG_CORE_SECTION, CONFIG_DFS_SECTION,
				CONFIG_KEY_STREAM_RATIO, "0.5");
		config.setString(CONFIG_CORE_SECTION, CONFIG_DFS_CACHE_PREFIX + "name1",
				CONFIG_KEY_PACK_EXTENSIONS, PackExt.PACK.name());
		config.setString(CONFIG_CORE_SECTION, CONFIG_DFS_CACHE_PREFIX + "name2",
				CONFIG_KEY_PACK_EXTENSIONS, PackExt.BITMAP_INDEX.name());

		DfsBlockCacheConfig blockCacheConfig = new DfsBlockCacheConfig()
				.fromConfig(config);
		assertThat(blockCacheConfig.getName(), equalTo("dfs"));
		assertThat(
				blockCacheConfig.getPackExtCacheConfigurations().get(0)
						.getPackExtCacheConfiguration().getName(),
				equalTo("dfs.name1"));
		assertThat(
				blockCacheConfig.getPackExtCacheConfigurations().get(1)
						.getPackExtCacheConfiguration().getName(),
				equalTo("dfs.name2"));
	}

	@Test
	public void fromConfigs_dfsBlockCachePackExtConfigWithDuplicateExtensions_throws() {
		Config config = new Config();
		config.setString(CONFIG_CORE_SECTION, CONFIG_DFS_CACHE_PREFIX + "pack1",
				CONFIG_KEY_PACK_EXTENSIONS, PackExt.PACK.name());

		config.setString(CONFIG_CORE_SECTION, CONFIG_DFS_CACHE_PREFIX + "pack2",
				CONFIG_KEY_PACK_EXTENSIONS, PackExt.PACK.name());

		assertThrows(IllegalArgumentException.class,
				() -> new DfsBlockCacheConfig().fromConfig(config));
	}

	@Test
	public void fromConfigs_dfsBlockCachePackExtConfigWithEmptyExtensions_throws() {
		Config config = new Config();
		config.setString(CONFIG_CORE_SECTION, CONFIG_DFS_CACHE_PREFIX + "pack1",
				CONFIG_KEY_PACK_EXTENSIONS, "");

		assertThrows(IllegalArgumentException.class,
				() -> new DfsBlockCacheConfig().fromConfig(config));
	}

	@Test
	public void fromConfigs_dfsBlockCachePackExtConfigWithNoExtensions_throws() {
		Config config = new Config();
		config.setInt(CONFIG_CORE_SECTION, CONFIG_DFS_CACHE_PREFIX + "pack1",
				CONFIG_KEY_BLOCK_SIZE, 0);

		assertThrows(IllegalArgumentException.class,
				() -> new DfsBlockCacheConfig().fromConfig(config));
	}

	@Test
	public void fromConfigs_dfsBlockCachePackExtConfigWithUnknownExtensions_throws() {
		Config config = new Config();
		config.setString(CONFIG_CORE_SECTION,
				CONFIG_DFS_CACHE_PREFIX + "unknownExt",
				CONFIG_KEY_PACK_EXTENSIONS, "NotAKnownExt");

		assertThrows(IllegalArgumentException.class,
				() -> new DfsBlockCacheConfig().fromConfig(config));
	}

	@Test
	public void writeConfigurationDebug_writesConfigsToWriter()
			throws Exception {
		Config config = new Config();
		config.setLong(CONFIG_CORE_SECTION, CONFIG_DFS_SECTION,
				CONFIG_KEY_BLOCK_LIMIT, 50 * 1024);
		config.setInt(CONFIG_CORE_SECTION, CONFIG_DFS_SECTION,
				CONFIG_KEY_BLOCK_SIZE, 1024);
		config.setInt(CONFIG_CORE_SECTION, CONFIG_DFS_SECTION,
				CONFIG_KEY_CONCURRENCY_LEVEL, 3);
		config.setString(CONFIG_CORE_SECTION, CONFIG_DFS_SECTION,
				CONFIG_KEY_STREAM_RATIO, "0.5");
		addPackExtConfigEntry(config, "pack", List.of(PackExt.PACK),
				/* blockLimit= */ 20 * 512, /* blockSize= */ 512);

		DfsBlockCacheConfig cacheConfig = new DfsBlockCacheConfig()
				.fromConfig(config);
		Map<PackExt, Integer> hotmap = Map.of(PackExt.PACK, 10);
		cacheConfig.setCacheHotMap(hotmap);

		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		cacheConfig.print(new PrintWriter(byteArrayOutputStream, true,
				StandardCharsets.UTF_8));

		String writenConfig = byteArrayOutputStream
				.toString(StandardCharsets.UTF_8);

		List<String> writenLines = Arrays.asList(writenConfig.split("\n"));
		assertThat(writenLines,
				equalTo(List.of("Name: dfs", "  BlockLimit: " + (50 * 1024),
						"  BlockSize: 1024", "  StreamRatio: 0.5",
						"  ConcurrencyLevel: 3",
						"  CacheHotMapEntry: " + PackExt.PACK + " : " + 10,
						"  Name: dfs.pack", "    BlockLimit: " + 20 * 512,
						"    BlockSize: 512", "    StreamRatio: 0.3",
						"    ConcurrencyLevel: 32",
						"    CacheHotMapEntry: " + PackExt.PACK + " : " + 10,
						"    PackExts: " + List.of(PackExt.PACK))));
	}

	private static void addPackExtConfigEntry(Config config, String configName,
			List<PackExt> packExts, long blockLimit, int blockSize) {
		String packExtConfigName = CONFIG_DFS_CACHE_PREFIX + configName;
		config.setString(CONFIG_CORE_SECTION, packExtConfigName,
				CONFIG_KEY_PACK_EXTENSIONS, packExts.stream().map(PackExt::name)
						.collect(Collectors.joining(" ")));
		config.setLong(CONFIG_CORE_SECTION, packExtConfigName,
				CONFIG_KEY_BLOCK_LIMIT, blockLimit);
		config.setInt(CONFIG_CORE_SECTION, packExtConfigName,
				CONFIG_KEY_BLOCK_SIZE, blockSize);
	}

	private static DfsBlockCacheConfig getConfigForExt(
			List<DfsBlockCachePackExtConfig> configs, PackExt packExt) {
		for (DfsBlockCachePackExtConfig config : configs) {
			if (config.getPackExts().contains(packExt)) {
				return config.getPackExtCacheConfiguration();
			}
		}
		return null;
	}
}
