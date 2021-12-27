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
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_DFS_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_DELTA_BASE_CACHE_LIMIT;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_STREAM_BUFFER;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_STREAM_FILE_TRESHOLD;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.pack.PackConfig;

/**
 * Options controlling how objects are read from a DFS stored repository.
 */
public class DfsReaderOptions {
	/** 1024 (number of bytes in one kibibyte/kilobyte) */
	public static final int KiB = 1024;

	/** 1024 {@link #KiB} (number of bytes in one mebibyte/megabyte) */
	public static final int MiB = 1024 * KiB;

	private int deltaBaseCacheLimit;
	private int streamFileThreshold;

	private int streamPackBufferSize;

	private boolean loadRevIndexInParallel;

	/**
	 * Create a default reader configuration.
	 */
	public DfsReaderOptions() {
		setDeltaBaseCacheLimit(10 * MiB);
		setStreamFileThreshold(PackConfig.DEFAULT_BIG_FILE_THRESHOLD);
	}

	/**
	 * Get maximum number of bytes to hold in per-reader DeltaBaseCache.
	 *
	 * @return maximum number of bytes to hold in per-reader DeltaBaseCache.
	 */
	public int getDeltaBaseCacheLimit() {
		return deltaBaseCacheLimit;
	}

	/**
	 * Set the maximum number of bytes in the DeltaBaseCache.
	 *
	 * @param maxBytes
	 *            the new limit.
	 * @return {@code this}
	 */
	public DfsReaderOptions setDeltaBaseCacheLimit(int maxBytes) {
		deltaBaseCacheLimit = Math.max(0, maxBytes);
		return this;
	}

	/**
	 * Get the size threshold beyond which objects must be streamed.
	 *
	 * @return the size threshold beyond which objects must be streamed.
	 */
	public int getStreamFileThreshold() {
		return streamFileThreshold;
	}

	/**
	 * Set new byte limit for objects that must be streamed.
	 *
	 * @param newLimit
	 *            new byte limit for objects that must be streamed. Objects
	 *            smaller than this size can be obtained as a contiguous byte
	 *            array, while objects bigger than this size require using an
	 *            {@link org.eclipse.jgit.lib.ObjectStream}.
	 * @return {@code this}
	 */
	public DfsReaderOptions setStreamFileThreshold(int newLimit) {
		streamFileThreshold = Math.max(0, newLimit);
		return this;
	}

	/**
	 * Get number of bytes to use for buffering when streaming a pack file
	 * during copying.
	 *
	 * @return number of bytes to use for buffering when streaming a pack file
	 *         during copying. If 0 the block size of the pack is used.
	 */
	public int getStreamPackBufferSize() {
		return streamPackBufferSize;
	}

	/**
	 * Set new buffer size in bytes for buffers used when streaming pack files
	 * during copying.
	 *
	 * @param bufsz
	 *            new buffer size in bytes for buffers used when streaming pack
	 *            files during copying.
	 * @return {@code this}
	 */
	public DfsReaderOptions setStreamPackBufferSize(int bufsz) {
		streamPackBufferSize = Math.max(0, bufsz);
		return this;
	}

	/**
	 * Check if reverse index should be loaded in parallel.
	 *
	 * @return true if reverse index is loaded in parallel for bitmap index.
	 */
	public boolean shouldLoadRevIndexInParallel() {
		return loadRevIndexInParallel;
	}

	/**
	 * Enable (or disable) parallel loading of reverse index.
	 *
	 * @param loadRevIndexInParallel
	 *            whether to load reverse index in parallel.
	 * @return {@code this}
	 */
	public DfsReaderOptions setLoadRevIndexInParallel(
			boolean loadRevIndexInParallel) {
		this.loadRevIndexInParallel = loadRevIndexInParallel;
		return this;
	}

	/**
	 * Update properties by setting fields from the configuration.
	 * <p>
	 * If a property is not defined in the configuration, then it is left
	 * unmodified.
	 *
	 * @param rc
	 *            configuration to read properties from.
	 * @return {@code this}
	 */
	public DfsReaderOptions fromConfig(Config rc) {
		setDeltaBaseCacheLimit(rc.getInt(
				CONFIG_CORE_SECTION,
				CONFIG_DFS_SECTION,
				CONFIG_KEY_DELTA_BASE_CACHE_LIMIT,
				getDeltaBaseCacheLimit()));

		long maxMem = Runtime.getRuntime().maxMemory();
		long sft = rc.getLong(
				CONFIG_CORE_SECTION,
				CONFIG_DFS_SECTION,
				CONFIG_KEY_STREAM_FILE_TRESHOLD,
				getStreamFileThreshold());
		sft = Math.min(sft, maxMem / 4); // don't use more than 1/4 of the heap
		sft = Math.min(sft, Integer.MAX_VALUE); // cannot exceed array length
		setStreamFileThreshold((int) sft);

		setStreamPackBufferSize(rc.getInt(
				CONFIG_CORE_SECTION,
				CONFIG_DFS_SECTION,
				CONFIG_KEY_STREAM_BUFFER,
				getStreamPackBufferSize()));
		return this;
	}
}
