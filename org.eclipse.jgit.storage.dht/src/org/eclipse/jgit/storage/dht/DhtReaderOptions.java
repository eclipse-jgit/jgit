/*
 * Copyright (C) 2011, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.storage.dht;

import org.eclipse.jgit.lib.Config;

/** Options controlling how objects are read from a DHT stored repository. */
public class DhtReaderOptions {
	/** 1024 (number of bytes in one kibibyte/kilobyte) */
	public static final int KiB = 1024;

	/** 1024 {@link #KiB} (number of bytes in one mebibyte/megabyte) */
	public static final int MiB = 1024 * KiB;

	private Timeout timeout;

	private boolean prefetchFollowEdgeHints;

	private int chunkLimit;

	private int openQueuePrefetchRatio;

	private int walkCommitsPrefetchRatio;

	private int walkTreesPrefetchRatio;

	private int writeObjectsPrefetchRatio;

	private int objectIndexConcurrentBatches;

	private int objectIndexBatchSize;

	private int deltaBaseCacheSize;

	private int deltaBaseCacheLimit;

	private int recentInfoCacheSize;

	private boolean trackFirstChunkLoad;

	/** Create a default reader configuration. */
	public DhtReaderOptions() {
		setTimeout(Timeout.seconds(5));
		setPrefetchFollowEdgeHints(true);

		setChunkLimit(5 * MiB);
		setOpenQueuePrefetchRatio(20 /* percent */);
		setWalkCommitsPrefetchRatio(20 /* percent */);
		setWalkTreesPrefetchRatio(20 /* percent */);
		setWriteObjectsPrefetchRatio(90 /* percent */);

		setObjectIndexConcurrentBatches(2);
		setObjectIndexBatchSize(512);

		setDeltaBaseCacheSize(1024);
		setDeltaBaseCacheLimit(10 * MiB);

		setRecentInfoCacheSize(4096);
	}

	/** @return default timeout to wait on long operations before aborting. */
	public Timeout getTimeout() {
		return timeout;
	}

	/**
	 * Set the default timeout to wait on long operations.
	 *
	 * @param maxWaitTime
	 *            new wait time.
	 * @return {@code this}
	 */
	public DhtReaderOptions setTimeout(Timeout maxWaitTime) {
		if (maxWaitTime == null || maxWaitTime.getTime() < 0)
			throw new IllegalArgumentException();
		timeout = maxWaitTime;
		return this;
	}

	/** @return if the prefetcher should follow edge hints (experimental) */
	public boolean isPrefetchFollowEdgeHints() {
		return prefetchFollowEdgeHints;
	}

	/**
	 * Enable (or disable) the experimental edge following feature.
	 *
	 * @param follow
	 *            true to follow the edge hints.
	 * @return {@code this}
	 */
	public DhtReaderOptions setPrefetchFollowEdgeHints(boolean follow) {
		prefetchFollowEdgeHints = follow;
		return this;
	}

	/** @return number of bytes to hold within a DhtReader. */
	public int getChunkLimit() {
		return chunkLimit;
	}

	/**
	 * Set the number of bytes hold within a DhtReader.
	 *
	 * @param maxBytes
	 * @return {@code this}
	 */
	public DhtReaderOptions setChunkLimit(int maxBytes) {
		chunkLimit = Math.max(1024, maxBytes);
		return this;
	}

	/** @return percentage of {@link #getChunkLimit()} used for prefetch, 0..100. */
	public int getOpenQueuePrefetchRatio() {
		return openQueuePrefetchRatio;
	}

	/**
	 * Set the prefetch ratio used by the open object queue.
	 *
	 * @param ratio 0..100.
	 * @return {@code this}
	 */
	public DhtReaderOptions setOpenQueuePrefetchRatio(int ratio) {
		openQueuePrefetchRatio = Math.max(0, Math.min(ratio, 100));
		return this;
	}

	/** @return percentage of {@link #getChunkLimit()} used for prefetch, 0..100. */
	public int getWalkCommitsPrefetchRatio() {
		return walkCommitsPrefetchRatio;
	}

	/**
	 * Set the prefetch ratio used by the open object queue.
	 *
	 * @param ratio 0..100.
	 * @return {@code this}
	 */
	public DhtReaderOptions setWalkCommitsPrefetchRatio(int ratio) {
		walkCommitsPrefetchRatio = Math.max(0, Math.min(ratio, 100));
		return this;
	}

	/** @return percentage of {@link #getChunkLimit()} used for prefetch, 0..100. */
	public int getWalkTreesPrefetchRatio() {
		return walkTreesPrefetchRatio;
	}

	/**
	 * Set the prefetch ratio used by the open object queue.
	 *
	 * @param ratio 0..100.
	 * @return {@code this}
	 */
	public DhtReaderOptions setWalkTreesPrefetchRatio(int ratio) {
		walkTreesPrefetchRatio = Math.max(0, Math.min(ratio, 100));
		return this;
	}

	/** @return percentage of {@link #getChunkLimit()} used for prefetch, 0..100. */
	public int getWriteObjectsPrefetchRatio() {
		return writeObjectsPrefetchRatio;
	}

	/**
	 * Set the prefetch ratio used by the open object queue.
	 *
	 * @param ratio 0..100.
	 * @return {@code this}
	 */
	public DhtReaderOptions setWriteObjectsPrefetchRatio(int ratio) {
		writeObjectsPrefetchRatio = Math.max(0, Math.min(ratio, 100));
		return this;
	}

	/** @return number of concurrent reads against ObjectIndexTable. */
	public int getObjectIndexConcurrentBatches() {
		return objectIndexConcurrentBatches;
	}

	/**
	 * Set the number of concurrent readers on ObjectIndexTable.
	 *
	 * @param batches
	 *            number of batches.
	 * @return {@code this}
	 */
	public DhtReaderOptions setObjectIndexConcurrentBatches(int batches) {
		objectIndexConcurrentBatches = Math.max(1, batches);
		return this;
	}

	/** @return number of objects to lookup in one batch. */
	public int getObjectIndexBatchSize() {
		return objectIndexBatchSize;
	}

	/**
	 * Set the number of objects to lookup at once.
	 *
	 * @param objectCnt
	 *            the number of objects in a lookup batch.
	 * @return {@code this}
	 */
	public DhtReaderOptions setObjectIndexBatchSize(int objectCnt) {
		objectIndexBatchSize = Math.max(1, objectCnt);
		return this;
	}

	/** @return size of the delta base cache hash table, in object entries. */
	public int getDeltaBaseCacheSize() {
		return deltaBaseCacheSize;
	}

	/**
	 * Set the size of the delta base cache hash table.
	 *
	 * @param slotCnt
	 *            number of slots in the hash table.
	 * @return {@code this}
	 */
	public DhtReaderOptions setDeltaBaseCacheSize(int slotCnt) {
		deltaBaseCacheSize = Math.max(1, slotCnt);
		return this;
	}

	/** @return maximum number of bytes to hold in per-reader DeltaBaseCache. */
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
	public DhtReaderOptions setDeltaBaseCacheLimit(int maxBytes) {
		deltaBaseCacheLimit = Math.max(0, maxBytes);
		return this;
	}

	/** @return number of objects to cache information on. */
	public int getRecentInfoCacheSize() {
		return recentInfoCacheSize;
	}

	/**
	 * Set the number of objects to cache information on.
	 *
	 * @param objectCnt
	 *            the number of objects to cache.
	 * @return {@code this}
	 */
	public DhtReaderOptions setRecentInfoCacheSize(int objectCnt) {
		recentInfoCacheSize = Math.max(0, objectCnt);
		return this;
	}

	/**
	 * @return true if {@link DhtReader.Statistics} includes the stack trace for
	 *         the first time a chunk is loaded. Supports debugging DHT code.
	 */
	public boolean isTrackFirstChunkLoad() {
		return trackFirstChunkLoad;
	}

	/**
	 * Set whether or not the initial load of each chunk should be tracked.
	 *
	 * @param track
	 *            true to track the stack trace of the first load.
	 * @return {@code this}.
	 */
	public DhtReaderOptions setTrackFirstChunkLoad(boolean track) {
		trackFirstChunkLoad = track;
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
	public DhtReaderOptions fromConfig(Config rc) {
		setTimeout(Timeout.getTimeout(rc, "core", "dht", "timeout", getTimeout()));
		setPrefetchFollowEdgeHints(rc.getBoolean("core", "dht", "prefetchFollowEdgeHints", isPrefetchFollowEdgeHints()));
		setChunkLimit(rc.getInt("core", "dht", "chunkLimit", getChunkLimit()));
		setOpenQueuePrefetchRatio(rc.getInt("core", "dht", "openQueuePrefetchRatio", getOpenQueuePrefetchRatio()));
		setWalkCommitsPrefetchRatio(rc.getInt("core", "dht", "walkCommitsPrefetchRatio", getWalkCommitsPrefetchRatio()));
		setWalkTreesPrefetchRatio(rc.getInt("core", "dht", "walkTreesPrefetchRatio", getWalkTreesPrefetchRatio()));
		setWriteObjectsPrefetchRatio(rc.getInt("core", "dht", "writeObjectsPrefetchRatio", getWriteObjectsPrefetchRatio()));

		setObjectIndexConcurrentBatches(rc.getInt("core", "dht", "objectIndexConcurrentBatches", getObjectIndexConcurrentBatches()));
		setObjectIndexBatchSize(rc.getInt("core", "dht", "objectIndexBatchSize", getObjectIndexBatchSize()));

		setDeltaBaseCacheSize(rc.getInt("core", "dht", "deltaBaseCacheSize", getDeltaBaseCacheSize()));
		setDeltaBaseCacheLimit(rc.getInt("core", "dht", "deltaBaseCacheLimit", getDeltaBaseCacheLimit()));

		setRecentInfoCacheSize(rc.getInt("core", "dht", "recentInfoCacheSize", getRecentInfoCacheSize()));

		setTrackFirstChunkLoad(rc.getBoolean("core", "dht", "debugTrackFirstChunkLoad", isTrackFirstChunkLoad()));
		return this;
	}
}
