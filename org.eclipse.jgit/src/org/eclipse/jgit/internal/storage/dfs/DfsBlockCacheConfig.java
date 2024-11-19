/*
 * Copyright (C) 2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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

import java.io.PrintWriter;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.lib.Config;

/**
 * Configuration parameters for
 * {@link org.eclipse.jgit.internal.storage.dfs.DfsBlockCache}.
 */
public class DfsBlockCacheConfig {
	/** 1024 (number of bytes in one kibibyte/kilobyte) */
	public static final int KB = 1024;

	/** 1024 {@link #KB} (number of bytes in one mebibyte/megabyte) */
	public static final int MB = 1024 * KB;

	/** Default number of max cache hits. */
	public static final int DEFAULT_CACHE_HOT_MAX = 1;

	static final String DEFAULT_NAME = "<default>"; //$NON-NLS-1$

	private String name;

	private long blockLimit;

	private int blockSize;

	private double streamRatio;

	private int concurrencyLevel;

	private Consumer<Long> refLock;

	private Map<PackExt, Integer> cacheHotMap;

	private IndexEventConsumer indexEventConsumer;

	private List<DfsBlockCachePackExtConfig> packExtCacheConfigurations;

	/**
	 * Create a default configuration.
	 */
	public DfsBlockCacheConfig() {
		name = DEFAULT_NAME;
		setBlockLimit(32 * MB);
		setBlockSize(64 * KB);
		setStreamRatio(0.30);
		setConcurrencyLevel(32);
		cacheHotMap = Collections.emptyMap();
		packExtCacheConfigurations = Collections.emptyList();
	}

	/**
	 * Print the current cache configuration to the given {@link PrintWriter}.
	 *
	 * @param writer
	 *            {@link PrintWriter} to write the cache's configuration to.
	 */
	public void print(PrintWriter writer) {
		print(/* linePrefix= */ "", /* pad= */ "  ", writer); //$NON-NLS-1$//$NON-NLS-2$
	}

	/**
	 * Print the current cache configuration to the given {@link PrintWriter}.
	 *
	 * @param linePrefix
	 *            prefix to prepend all writen lines with. Ex a string of 0 or
	 *            more " " entries.
	 * @param pad
	 *            filler used to extend linePrefix. Ex a multiple of " ".
	 * @param writer
	 *            {@link PrintWriter} to write the cache's configuration to.
	 */
	@SuppressWarnings("nls")
	private void print(String linePrefix, String pad, PrintWriter writer) {
		String currentPrefixLevel = linePrefix;
		if (!name.isEmpty() || !packExtCacheConfigurations.isEmpty()) {
			writer.println(linePrefix + "Name: "
					+ (name.isEmpty() ? DEFAULT_NAME : this.name));
			currentPrefixLevel += pad;
		}
		writer.println(currentPrefixLevel + "BlockLimit: " + blockLimit);
		writer.println(currentPrefixLevel + "BlockSize: " + blockSize);
		writer.println(currentPrefixLevel + "StreamRatio: " + streamRatio);
		writer.println(
				currentPrefixLevel + "ConcurrencyLevel: " + concurrencyLevel);
		for (Map.Entry<PackExt, Integer> entry : cacheHotMap.entrySet()) {
			writer.println(currentPrefixLevel + "CacheHotMapEntry: "
					+ entry.getKey() + " : " + entry.getValue());
		}
		for (DfsBlockCachePackExtConfig extConfig : packExtCacheConfigurations) {
			extConfig.print(currentPrefixLevel, pad, writer);
		}
	}

	/**
	 * Get the name for the block cache configured by this cache config.
	 *
	 * @return the name for the block cache configured by this cache config.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Set the name for the block cache configured by this cache config.
	 * <p>
	 * Made visible for testing.
	 *
	 * @param name
	 *            the name for the block cache configured by this cache config.
	 * @return {@code this}
	 */
	DfsBlockCacheConfig setName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * Get maximum number bytes of heap memory to dedicate to caching pack file
	 * data.
	 *
	 * @return maximum number bytes of heap memory to dedicate to caching pack
	 *         file data. <b>Default is 32 MB.</b>
	 */
	public long getBlockLimit() {
		return blockLimit;
	}

	/**
	 * Set maximum number bytes of heap memory to dedicate to caching pack file
	 * data.
	 * <p>
	 * It is strongly recommended to set the block limit to be an integer
	 * multiple of the block size. This constraint is not enforced by this
	 * method (since it may be called before {@link #setBlockSize(int)}), but it
	 * is enforced by {@link #fromConfig(Config)}.
	 *
	 * @param newLimit
	 *            maximum number bytes of heap memory to dedicate to caching
	 *            pack file data; must be positive.
	 * @return {@code this}
	 */
	public DfsBlockCacheConfig setBlockLimit(long newLimit) {
		if (newLimit <= 0) {
			throw new IllegalArgumentException(
					MessageFormat.format(JGitText.get().blockLimitNotPositive,
							Long.valueOf(newLimit)));
		}
		blockLimit = newLimit;
		return this;
	}

	/**
	 * Get size in bytes of a single window mapped or read in from the pack
	 * file.
	 *
	 * @return size in bytes of a single window mapped or read in from the pack
	 *         file. <b>Default is 64 KB.</b>
	 */
	public int getBlockSize() {
		return blockSize;
	}

	/**
	 * Set size in bytes of a single window read in from the pack file.
	 *
	 * @param newSize
	 *            size in bytes of a single window read in from the pack file.
	 *            The value must be a power of 2.
	 * @return {@code this}
	 */
	public DfsBlockCacheConfig setBlockSize(int newSize) {
		int size = Math.max(512, newSize);
		if ((size & (size - 1)) != 0) {
			throw new IllegalArgumentException(
					JGitText.get().blockSizeNotPowerOf2);
		}
		blockSize = size;
		return this;
	}

	/**
	 * Get the estimated number of threads concurrently accessing the cache.
	 *
	 * @return the estimated number of threads concurrently accessing the cache.
	 *         <b>Default is 32.</b>
	 */
	public int getConcurrencyLevel() {
		return concurrencyLevel;
	}

	/**
	 * Set the estimated number of threads concurrently accessing the cache.
	 *
	 * @param newConcurrencyLevel
	 *            the estimated number of threads concurrently accessing the
	 *            cache.
	 * @return {@code this}
	 */
	public DfsBlockCacheConfig setConcurrencyLevel(
			final int newConcurrencyLevel) {
		concurrencyLevel = newConcurrencyLevel;
		return this;
	}

	/**
	 * Get highest percentage of {@link #getBlockLimit()} a single pack can
	 * occupy while being copied by the pack reuse strategy.
	 *
	 * @return highest percentage of {@link #getBlockLimit()} a single pack can
	 *         occupy while being copied by the pack reuse strategy. <b>Default
	 *         is 0.30, or 30%</b>.
	 */
	public double getStreamRatio() {
		return streamRatio;
	}

	/**
	 * Set percentage of cache to occupy with a copied pack.
	 *
	 * @param ratio
	 *            percentage of cache to occupy with a copied pack.
	 * @return {@code this}
	 */
	public DfsBlockCacheConfig setStreamRatio(double ratio) {
		streamRatio = Math.max(0, Math.min(ratio, 1.0));
		return this;
	}

	/**
	 * Get the consumer of the object reference lock wait time in milliseconds.
	 *
	 * @return consumer of wait time in milliseconds.
	 */
	public Consumer<Long> getRefLockWaitTimeConsumer() {
		return refLock;
	}

	/**
	 * Set the consumer for lock wait time.
	 *
	 * @param c
	 *            consumer of wait time in milliseconds.
	 * @return {@code this}
	 */
	public DfsBlockCacheConfig setRefLockWaitTimeConsumer(Consumer<Long> c) {
		refLock = c;
		return this;
	}

	/**
	 * Get the map of hot count per pack extension for {@code DfsBlockCache}.
	 *
	 * @return map of hot count per pack extension for {@code DfsBlockCache}.
	 */
	public Map<PackExt, Integer> getCacheHotMap() {
		return cacheHotMap;
	}

	/**
	 * Set the map of hot count per pack extension for {@code DfsBlockCache}.
	 *
	 * @param cacheHotMap
	 *            map of hot count per pack extension for {@code DfsBlockCache}.
	 * @return {@code this}
	 */
	/*
	 * TODO The cache HotMap configuration should be set as a config option and
	 * not passed in through a setter.
	 */
	public DfsBlockCacheConfig setCacheHotMap(
			Map<PackExt, Integer> cacheHotMap) {
		this.cacheHotMap = Collections.unmodifiableMap(cacheHotMap);
		setCacheHotMapToPackExtConfigs(this.cacheHotMap);
		return this;
	}

	private void setCacheHotMapToPackExtConfigs(
			Map<PackExt, Integer> cacheHotMap) {
		for (DfsBlockCachePackExtConfig packExtConfig : packExtCacheConfigurations) {
			packExtConfig.setCacheHotMap(cacheHotMap);
		}
	}

	/**
	 * Get the consumer of cache index events.
	 *
	 * @return consumer of cache index events.
	 */
	public IndexEventConsumer getIndexEventConsumer() {
		return indexEventConsumer;
	}

	/**
	 * Set the consumer of cache index events.
	 *
	 * @param indexEventConsumer
	 *            consumer of cache index events.
	 * @return {@code this}
	 */
	public DfsBlockCacheConfig setIndexEventConsumer(
			IndexEventConsumer indexEventConsumer) {
		this.indexEventConsumer = indexEventConsumer;
		return this;
	}

	/**
	 * Get the list of pack ext cache configs.
	 *
	 * @return the list of pack ext cache configs.
	 */
	List<DfsBlockCachePackExtConfig> getPackExtCacheConfigurations() {
		return packExtCacheConfigurations;
	}

	/**
	 * Set the list of pack ext cache configs.
	 *
	 * Made visible for testing.
	 *
	 * @param packExtCacheConfigurations
	 *            the list of pack ext cache configs to set.
	 * @return {@code this}
	 */
	DfsBlockCacheConfig setPackExtCacheConfigurations(
			List<DfsBlockCachePackExtConfig> packExtCacheConfigurations) {
		this.packExtCacheConfigurations = packExtCacheConfigurations;
		return this;
	}

	/**
	 * Update properties by setting fields from the configuration.
	 * <p>
	 * If a property is not defined in the configuration, then it is left
	 * unmodified.
	 * <p>
	 * Enforces certain constraints on the combination of settings in the
	 * config, for example that the block limit is a multiple of the block size.
	 *
	 * @param rc
	 *            configuration to read properties from.
	 * @return {@code this}
	 */
	public DfsBlockCacheConfig fromConfig(Config rc) {
		fromConfig(CONFIG_CORE_SECTION, CONFIG_DFS_SECTION, rc);
		loadPackExtConfigs(rc);
		return this;
	}

	private void fromConfig(String section, String subSection, Config rc) {
		long cfgBlockLimit = rc.getLong(section, subSection,
				CONFIG_KEY_BLOCK_LIMIT, getBlockLimit());
		int cfgBlockSize = rc.getInt(section, subSection, CONFIG_KEY_BLOCK_SIZE,
				getBlockSize());
		if (cfgBlockLimit % cfgBlockSize != 0) {
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().blockLimitNotMultipleOfBlockSize,
					Long.valueOf(cfgBlockLimit), Long.valueOf(cfgBlockSize)));
		}

		// Set name only if `core dfs` is configured, otherwise fall back to the
		// default.
		if (rc.getSubsections(section).contains(subSection)) {
			this.name = subSection;
		}
		setBlockLimit(cfgBlockLimit);
		setBlockSize(cfgBlockSize);

		setConcurrencyLevel(rc.getInt(section, subSection,
				CONFIG_KEY_CONCURRENCY_LEVEL, getConcurrencyLevel()));

		String v = rc.getString(section, subSection, CONFIG_KEY_STREAM_RATIO);
		if (v != null) {
			try {
				setStreamRatio(Double.parseDouble(v));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(MessageFormat.format(
						JGitText.get().enumValueNotSupported3, section,
						subSection, CONFIG_KEY_STREAM_RATIO, v), e);
			}
		}
	}

	private void loadPackExtConfigs(Config config) {
		List<String> subSections = config.getSubsections(CONFIG_CORE_SECTION)
				.stream()
				.filter(section -> section.startsWith(CONFIG_DFS_CACHE_PREFIX))
				.collect(Collectors.toList());
		if (subSections.size() == 0) {
			return;
		}
		ArrayList<DfsBlockCachePackExtConfig> cacheConfigs = new ArrayList<>();
		Set<PackExt> extensionsSeen = new HashSet<>();
		for (String subSection : subSections) {
			var cacheConfig = DfsBlockCachePackExtConfig.fromConfig(config,
					CONFIG_CORE_SECTION, subSection);
			Set<PackExt> packExtsDuplicates = intersection(extensionsSeen,
					cacheConfig.packExts);
			if (packExtsDuplicates.size() > 0) {
				String duplicatePackExts = packExtsDuplicates.stream()
						.map(PackExt::toString)
						.collect(Collectors.joining(",")); //$NON-NLS-1$
				throw new IllegalArgumentException(MessageFormat.format(
						JGitText.get().duplicatePackExtensionsSet,
						CONFIG_CORE_SECTION, subSection,
						CONFIG_KEY_PACK_EXTENSIONS, duplicatePackExts));
			}
			extensionsSeen.addAll(cacheConfig.packExts);
			cacheConfigs.add(cacheConfig);
		}
		packExtCacheConfigurations = cacheConfigs;
		setCacheHotMapToPackExtConfigs(this.cacheHotMap);
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

	/** Consumer of DfsBlockCache loading and eviction events for indexes. */
	public interface IndexEventConsumer {
		/**
		 * Accept an event of an index requested. It could be loaded from either
		 * cache or storage.
		 *
		 * @param packExtPos
		 *            position in {@code PackExt} enum
		 * @param cacheHit
		 *            true if an index was already in cache. Otherwise, the
		 *            index was loaded from storage into the cache in the
		 *            current request,
		 * @param loadMicros
		 *            time to load an index from cache or storage in
		 *            microseconds
		 * @param bytes
		 *            number of bytes loaded
		 * @param lastEvictionDuration
		 *            time since last eviction, 0 if was not evicted yet
		 */
		void acceptRequestedEvent(int packExtPos, boolean cacheHit,
				long loadMicros, long bytes, Duration lastEvictionDuration);

		/**
		 * Accept an event of an index evicted from cache.
		 *
		 * @param packExtPos
		 *            position in {@code PackExt} enum
		 * @param bytes
		 *            number of bytes evicted
		 * @param totalCacheHitCount
		 *            number of times an index was accessed while in cache
		 * @param lastEvictionDuration
		 *            time since last eviction, 0 if was not evicted yet
		 */
		default void acceptEvictedEvent(int packExtPos, long bytes,
				int totalCacheHitCount, Duration lastEvictionDuration) {
			// Off by default.
		}

		/**
		 * Whether evicted events should be reported
		 *
		 * @return true if reporting evicted events is enabled.
		 */
		default boolean shouldReportEvictedEvent() {
			return false;
		}
	}

	/**
	 * A configuration for a single cache table storing 1 or more Pack
	 * extensions.
	 * <p>
	 * The current pack ext cache tables implementation supports the same
	 * parameters the ClockBlockCacheTable (current default implementation).
	 * <p>
	 * Configuration falls back to the defaults coded values defined in the
	 * {@link DfsBlockCacheConfig} when not set on each cache table
	 * configuration and NOT the values of the basic dfs section.
	 * <p>
	 * <code>
	 *
	 * Format:
	 * [core "dfs.packCache"]
	 *   packExtensions = "PACK"
	 *   blockSize = 512
	 *   blockLimit = 100
	 *   concurrencyLevel = 5
	 *
	 * [core "dfs.multipleExtensionCache"]
	 *   packExtensions = "INDEX REFTABLE BITMAP_INDEX"
	 *   blockSize = 512
	 *   blockLimit = 100
	 *   concurrencyLevel = 5
	 * </code>
	 */
	static class DfsBlockCachePackExtConfig {
		// Set of pack extensions that will map to the cache instance.
		private final EnumSet<PackExt> packExts;

		// Configuration for the cache instance.
		private final DfsBlockCacheConfig packExtCacheConfiguration;

		/**
		 * Made visible for testing.
		 *
		 * @param packExts
		 *            Set of {@link PackExt}s associated to this cache config.
		 * @param packExtCacheConfiguration
		 *            {@link DfsBlockCacheConfig} for this cache config.
		 */
		DfsBlockCachePackExtConfig(EnumSet<PackExt> packExts,
				DfsBlockCacheConfig packExtCacheConfiguration) {
			this.packExts = packExts;
			this.packExtCacheConfiguration = packExtCacheConfiguration;
		}

		Set<PackExt> getPackExts() {
			return packExts;
		}

		DfsBlockCacheConfig getPackExtCacheConfiguration() {
			return packExtCacheConfiguration;
		}

		void setCacheHotMap(Map<PackExt, Integer> cacheHotMap) {
			Map<PackExt, Integer> packExtHotMap = packExts.stream()
					.filter(cacheHotMap::containsKey)
					.collect(Collectors.toUnmodifiableMap(Function.identity(),
							cacheHotMap::get));
			packExtCacheConfiguration.setCacheHotMap(packExtHotMap);
		}

		private static DfsBlockCachePackExtConfig fromConfig(Config config,
				String section, String subSection) {
			String packExtensions = config.getString(section, subSection,
					CONFIG_KEY_PACK_EXTENSIONS);
			if (packExtensions == null) {
				throw new IllegalArgumentException(
						JGitText.get().noPackExtGivenForConfiguration);
			}
			String[] extensions = packExtensions.split(" ", -1); //$NON-NLS-1$
			Set<PackExt> packExts = new HashSet<>(extensions.length);
			for (String extension : extensions) {
				try {
					packExts.add(PackExt.valueOf(extension));
				} catch (IllegalArgumentException e) {
					throw new IllegalArgumentException(MessageFormat.format(
							JGitText.get().unknownPackExtension, section,
							subSection, CONFIG_KEY_PACK_EXTENSIONS, extension),
							e);
				}
			}

			DfsBlockCacheConfig dfsBlockCacheConfig = new DfsBlockCacheConfig();
			dfsBlockCacheConfig.fromConfig(section, subSection, config);
			return new DfsBlockCachePackExtConfig(EnumSet.copyOf(packExts),
					dfsBlockCacheConfig);
		}

		void print(String linePrefix, String pad, PrintWriter writer) {
			packExtCacheConfiguration.print(linePrefix, pad, writer);
			writer.println(linePrefix + pad + "PackExts: " //$NON-NLS-1$
					+ packExts.stream().sorted().collect(Collectors.toList()));
		}
	}
}
