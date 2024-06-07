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
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheConfig.DfsBlockCachePackExtConfig;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class DfsBlockCacheConfigTest {

	@Test
	public void blockSizeNotPowerOfTwoExpectsException() {
		assertThrows(JGitText.get().blockSizeNotPowerOf2,
				IllegalArgumentException.class,
				() -> new DfsBlockCacheConfig().setBlockSize(1000));
	}

	@Test
	@SuppressWarnings("boxing")
	public void negativeBlockSizeIsConvertedToDefault() {
		DfsBlockCacheConfig config = new DfsBlockCacheConfig();
		config.setBlockSize(-1);

		assertThat(config.getBlockSize(), is(512));
	}

	@Test
	@SuppressWarnings("boxing")
	public void tooSmallBlockSizeIsConvertedToDefault() {
		DfsBlockCacheConfig config = new DfsBlockCacheConfig();
		config.setBlockSize(10);

		assertThat(config.getBlockSize(), is(512));
	}

	@Test
	@SuppressWarnings("boxing")
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
	public void fromConfigsBlockLimitNotAMultipleOfBlockSizeFailsWithIllegalArgument() {
		Config config = new Config();
		config.setLong(CONFIG_CORE_SECTION, CONFIG_DFS_SECTION,
				CONFIG_KEY_BLOCK_LIMIT, 1025);
		config.setInt(CONFIG_CORE_SECTION, CONFIG_DFS_SECTION,
				CONFIG_KEY_BLOCK_SIZE, 1024);

		assertThrows(IllegalArgumentException.class,
				() -> new DfsBlockCacheConfig().fromConfig(config));
	}

	@Test
	public void fromConfigsStreamRatioInvalidFormatFailsWithIllegalArgument() {
		Config config = new Config();
		config.setString(CONFIG_CORE_SECTION, CONFIG_DFS_SECTION,
				CONFIG_KEY_STREAM_RATIO, "0.a5");

		assertThrows(IllegalArgumentException.class,
				() -> new DfsBlockCacheConfig().fromConfig(config));
	}

	@Test
	public void dfsBlockCachePackExtConfigFromConfigs() {
		Config config = new Config();
		String CONFIG_DFS_CACHE_PACK = CONFIG_DFS_CACHE_PREFIX + "pack";
		config.setString(CONFIG_CORE_SECTION, CONFIG_DFS_CACHE_PACK,
				CONFIG_KEY_PACK_EXTENSIONS, PackExt.PACK.name());
		config.setLong(CONFIG_CORE_SECTION, CONFIG_DFS_CACHE_PACK,
				CONFIG_KEY_BLOCK_LIMIT, 20 * 512);
		config.setInt(CONFIG_CORE_SECTION, CONFIG_DFS_CACHE_PACK,
				CONFIG_KEY_BLOCK_SIZE, 512);
		String packKey = PackExt.PACK.name();

		String CONFIG_DFS_CACHE_BITMAP = CONFIG_DFS_CACHE_PREFIX + "bitmap";
		config.setString(CONFIG_CORE_SECTION, CONFIG_DFS_CACHE_BITMAP,
				CONFIG_KEY_PACK_EXTENSIONS, PackExt.BITMAP_INDEX.name());
		config.setLong(CONFIG_CORE_SECTION, CONFIG_DFS_CACHE_BITMAP,
				CONFIG_KEY_BLOCK_LIMIT, 25 * 1024);
		config.setInt(CONFIG_CORE_SECTION, CONFIG_DFS_CACHE_BITMAP,
				CONFIG_KEY_BLOCK_SIZE, 1024);
		String bitmapKey = PackExt.BITMAP_INDEX.name();

		String CONFIG_DFS_CACHE_INDEX = CONFIG_DFS_CACHE_PREFIX + "index";
		config.setString(CONFIG_CORE_SECTION, CONFIG_DFS_CACHE_INDEX,
				CONFIG_KEY_PACK_EXTENSIONS,
				PackExt.INDEX.name() + " " + PackExt.REVERSE_INDEX.name() + " "
						+ PackExt.OBJECT_SIZE_INDEX.name());
		config.setLong(CONFIG_CORE_SECTION, CONFIG_DFS_CACHE_INDEX,
				CONFIG_KEY_BLOCK_LIMIT, 30 * 1024);
		config.setInt(CONFIG_CORE_SECTION, CONFIG_DFS_CACHE_INDEX,
				CONFIG_KEY_BLOCK_SIZE, 1024);
		String indexKey = PackExt.INDEX.name() + ","
				+ PackExt.OBJECT_SIZE_INDEX.name() + ","
				+ PackExt.REVERSE_INDEX.name();
		Set<String> keySet = Set.of(packKey, bitmapKey, indexKey);

		DfsBlockCacheConfig cacheConfig = new DfsBlockCacheConfig()
				.fromConfig(config);
		assertThat(cacheConfig.getPackExtCacheConfigurations(), hasSize(3));
		Map<String, DfsBlockCacheConfig> packExtToCacheConfig = new HashMap<>();
		for (DfsBlockCachePackExtConfig packExtConfig : cacheConfig
				.getPackExtCacheConfigurations()) {
			String key = String.join(",", packExtConfig.getPackExts().stream()
					.map(PackExt::name).sorted().collect(Collectors.toList()));
			assertThat(keySet, hasItems(key));
			assertThat(packExtToCacheConfig.keySet(), not(hasItems(key)));
			packExtToCacheConfig.put(key,
					packExtConfig.getPackExtCacheConfiguration());
		}
		assertThat(packExtToCacheConfig.get(packKey).getBlockLimit(),
				is(20L * 512L));
		assertThat(packExtToCacheConfig.get(packKey).getBlockSize(), is(512));

		assertThat(packExtToCacheConfig.get(bitmapKey).getBlockLimit(),
				is(25L * 1024L));
		assertThat(packExtToCacheConfig.get(bitmapKey).getBlockSize(),
				is(1024));

		assertThat(packExtToCacheConfig.get(indexKey).getBlockLimit(),
				is(30L * 1024L));
		assertThat(packExtToCacheConfig.get(indexKey).getBlockSize(), is(1024));
	}

	@Test
	public void dfsBlockCachePackExtConfigFromConfigsWithDuplicateExtensionsFailsWithIllegalArgument() {
		Config config = new Config();
		config.setString(CONFIG_CORE_SECTION, CONFIG_DFS_CACHE_PREFIX + "pack1",
				CONFIG_KEY_PACK_EXTENSIONS, PackExt.PACK.name());

		config.setString(CONFIG_CORE_SECTION, CONFIG_DFS_CACHE_PREFIX + "pack2",
				CONFIG_KEY_PACK_EXTENSIONS, PackExt.PACK.name());

		assertThrows(IllegalArgumentException.class,
				() -> new DfsBlockCacheConfig().fromConfig(config));
	}

	@Test
	public void dfsBlockCachePackExtConfigFromConfigsWithUnknownExtensionsFailsWithIllegalArgument() {
		Config config = new Config();
		config.setString(CONFIG_CORE_SECTION,
				CONFIG_DFS_CACHE_PREFIX + "unknownExt",
				CONFIG_KEY_PACK_EXTENSIONS, "NotAKnownExt");

		assertThrows(IllegalArgumentException.class,
				() -> new DfsBlockCacheConfig().fromConfig(config));
	}
}
