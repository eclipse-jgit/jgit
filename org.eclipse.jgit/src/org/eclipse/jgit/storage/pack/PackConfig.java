/*
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
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

package org.eclipse.jgit.storage.pack;

import java.util.concurrent.Executor;
import java.util.zip.Deflater;

import org.eclipse.jgit.internal.storage.file.PackIndexWriter;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

/**
 * Configuration used by a pack writer when constructing the stream.
 *
 * A configuration may be modified once created, but should not be modified
 * while it is being used by a PackWriter. If a configuration is not modified it
 * is safe to share the same configuration instance between multiple concurrent
 * threads executing different PackWriters.
 */
public class PackConfig {
	/**
	 * Default value of deltas reuse option: {@value}
	 *
	 * @see #setReuseDeltas(boolean)
	 */
	public static final boolean DEFAULT_REUSE_DELTAS = true;

	/**
	 * Default value of objects reuse option: {@value}
	 *
	 * @see #setReuseObjects(boolean)
	 */
	public static final boolean DEFAULT_REUSE_OBJECTS = true;

	/**
	 * Default value of keep old packs option: {@value}
	 * @see #setPreserveOldPacks(boolean)
	 * @since 4.7
	 */
	public static final boolean DEFAULT_PRESERVE_OLD_PACKS = false;

	/**
	 * Default value of prune old packs option: {@value}
	 * @see #setPrunePreserved(boolean)
	 * @since 4.7
	 */
	public static final boolean DEFAULT_PRUNE_PRESERVED = false;

	/**
	 * Default value of delta compress option: {@value}
	 *
	 * @see #setDeltaCompress(boolean)
	 */
	public static final boolean DEFAULT_DELTA_COMPRESS = true;

	/**
	 * Default value of delta base as offset option: {@value}
	 *
	 * @see #setDeltaBaseAsOffset(boolean)
	 */
	public static final boolean DEFAULT_DELTA_BASE_AS_OFFSET = false;

	/**
	 * Default value of maximum delta chain depth: {@value}
	 *
	 * @see #setMaxDeltaDepth(int)
	 */
	public static final int DEFAULT_MAX_DELTA_DEPTH = 50;

	/**
	 * Default window size during packing: {@value}
	 *
	 * @see #setDeltaSearchWindowSize(int)
	 */
	public static final int DEFAULT_DELTA_SEARCH_WINDOW_SIZE = 10;

	/**
	 * Default big file threshold: {@value}
	 *
	 * @see #setBigFileThreshold(int)
	 */
	public static final int DEFAULT_BIG_FILE_THRESHOLD = 50 * 1024 * 1024;

	/**
	 * Default delta cache size: {@value}
	 *
	 * @see #setDeltaCacheSize(long)
	 */
	public static final long DEFAULT_DELTA_CACHE_SIZE = 50 * 1024 * 1024;

	/**
	 * Default delta cache limit: {@value}
	 *
	 * @see #setDeltaCacheLimit(int)
	 */
	public static final int DEFAULT_DELTA_CACHE_LIMIT = 100;

	/**
	 * Default index version: {@value}
	 *
	 * @see #setIndexVersion(int)
	 */
	public static final int DEFAULT_INDEX_VERSION = 2;

	/**
	 * Default value of the build bitmaps option: {@value}
	 *
	 * @see #setBuildBitmaps(boolean)
	 * @since 3.0
	 */
	public static final boolean DEFAULT_BUILD_BITMAPS = true;

	/**
	 * Default count of most recent commits to select for bitmaps. Only applies
	 * when bitmaps are enabled: {@value}
	 *
	 * @see #setBitmapContiguousCommitCount(int)
	 * @since 4.2
	 */
	public static final int DEFAULT_BITMAP_CONTIGUOUS_COMMIT_COUNT = 100;

	/**
	 * Count at which the span between selected commits changes from
	 * "bitmapRecentCommitSpan" to "bitmapDistantCommitSpan". Only applies when
	 * bitmaps are enabled: {@value}
	 *
	 * @see #setBitmapRecentCommitCount(int)
	 * @since 4.2
	 */
	public static final int DEFAULT_BITMAP_RECENT_COMMIT_COUNT = 20000;

	/**
	 * Default spacing between commits in recent history when selecting commits
	 * for bitmaps. Only applies when bitmaps are enabled: {@value}
	 *
	 * @see #setBitmapRecentCommitSpan(int)
	 * @since 4.2
	 */
	public static final int DEFAULT_BITMAP_RECENT_COMMIT_SPAN = 100;

	/**
	 * Default spacing between commits in distant history when selecting commits
	 * for bitmaps. Only applies when bitmaps are enabled: {@value}
	 *
	 * @see #setBitmapDistantCommitSpan(int)
	 * @since 4.2
	 */
	public static final int DEFAULT_BITMAP_DISTANT_COMMIT_SPAN = 5000;

	/**
	 * Default count of branches required to activate inactive branch commit
	 * selection. If the number of branches is less than this then bitmaps for
	 * the entire commit history of all branches will be created, otherwise
	 * branches marked as "inactive" will have coverage for only partial
	 * history: {@value}
	 *
	 * @see #setBitmapExcessiveBranchCount(int)
	 * @since 4.2
	 */
	public static final int DEFAULT_BITMAP_EXCESSIVE_BRANCH_COUNT = 100;

	/**
	 * Default age at which a branch is considered inactive. Age is taken as the
	 * number of days ago that the most recent commit was made to a branch. Only
	 * affects bitmap processing if bitmaps are enabled and the
	 * "excessive branch count" has been exceeded: {@value}
	 *
	 * @see #setBitmapInactiveBranchAgeInDays(int)
	 * @since 4.2
	 */
	public static final int DEFAULT_BITMAP_INACTIVE_BRANCH_AGE_IN_DAYS = 90;

	private int compressionLevel = Deflater.DEFAULT_COMPRESSION;

	private boolean reuseDeltas = DEFAULT_REUSE_DELTAS;

	private boolean reuseObjects = DEFAULT_REUSE_OBJECTS;

	private boolean preserveOldPacks = DEFAULT_PRESERVE_OLD_PACKS;

	private boolean prunePreserved = DEFAULT_PRUNE_PRESERVED;

	private boolean deltaBaseAsOffset = DEFAULT_DELTA_BASE_AS_OFFSET;

	private boolean deltaCompress = DEFAULT_DELTA_COMPRESS;

	private int maxDeltaDepth = DEFAULT_MAX_DELTA_DEPTH;

	private int deltaSearchWindowSize = DEFAULT_DELTA_SEARCH_WINDOW_SIZE;

	private long deltaSearchMemoryLimit;

	private long deltaCacheSize = DEFAULT_DELTA_CACHE_SIZE;

	private int deltaCacheLimit = DEFAULT_DELTA_CACHE_LIMIT;

	private int bigFileThreshold = DEFAULT_BIG_FILE_THRESHOLD;

	private int threads;

	private Executor executor;

	private int indexVersion = DEFAULT_INDEX_VERSION;

	private boolean buildBitmaps = DEFAULT_BUILD_BITMAPS;

	private int bitmapContiguousCommitCount = DEFAULT_BITMAP_CONTIGUOUS_COMMIT_COUNT;

	private int bitmapRecentCommitCount = DEFAULT_BITMAP_RECENT_COMMIT_COUNT;

	private int bitmapRecentCommitSpan = DEFAULT_BITMAP_RECENT_COMMIT_SPAN;

	private int bitmapDistantCommitSpan = DEFAULT_BITMAP_DISTANT_COMMIT_SPAN;

	private int bitmapExcessiveBranchCount = DEFAULT_BITMAP_EXCESSIVE_BRANCH_COUNT;

	private int bitmapInactiveBranchAgeInDays = DEFAULT_BITMAP_INACTIVE_BRANCH_AGE_IN_DAYS;

	private boolean cutDeltaChains;

	private boolean singlePack;

	/** Create a default configuration. */
	public PackConfig() {
		// Fields are initialized to defaults.
	}

	/**
	 * Create a configuration honoring the repository's settings.
	 *
	 * @param db
	 *            the repository to read settings from. The repository is not
	 *            retained by the new configuration, instead its settings are
	 *            copied during the constructor.
	 */
	public PackConfig(Repository db) {
		fromConfig(db.getConfig());
	}

	/**
	 * Create a configuration honoring settings in a {@link Config}.
	 *
	 * @param cfg
	 *            the source to read settings from. The source is not retained
	 *            by the new configuration, instead its settings are copied
	 *            during the constructor.
	 */
	public PackConfig(Config cfg) {
		fromConfig(cfg);
	}

	/**
	 * Copy an existing configuration to a new instance.
	 *
	 * @param cfg
	 *            the source configuration to copy from.
	 */
	public PackConfig(PackConfig cfg) {
		this.compressionLevel = cfg.compressionLevel;
		this.reuseDeltas = cfg.reuseDeltas;
		this.reuseObjects = cfg.reuseObjects;
		this.preserveOldPacks = cfg.preserveOldPacks;
		this.prunePreserved = cfg.prunePreserved;
		this.deltaBaseAsOffset = cfg.deltaBaseAsOffset;
		this.deltaCompress = cfg.deltaCompress;
		this.maxDeltaDepth = cfg.maxDeltaDepth;
		this.deltaSearchWindowSize = cfg.deltaSearchWindowSize;
		this.deltaSearchMemoryLimit = cfg.deltaSearchMemoryLimit;
		this.deltaCacheSize = cfg.deltaCacheSize;
		this.deltaCacheLimit = cfg.deltaCacheLimit;
		this.bigFileThreshold = cfg.bigFileThreshold;
		this.threads = cfg.threads;
		this.executor = cfg.executor;
		this.indexVersion = cfg.indexVersion;
		this.buildBitmaps = cfg.buildBitmaps;
		this.bitmapContiguousCommitCount = cfg.bitmapContiguousCommitCount;
		this.bitmapRecentCommitCount = cfg.bitmapRecentCommitCount;
		this.bitmapRecentCommitSpan = cfg.bitmapRecentCommitSpan;
		this.bitmapDistantCommitSpan = cfg.bitmapDistantCommitSpan;
		this.bitmapExcessiveBranchCount = cfg.bitmapExcessiveBranchCount;
		this.bitmapInactiveBranchAgeInDays = cfg.bitmapInactiveBranchAgeInDays;
		this.cutDeltaChains = cfg.cutDeltaChains;
		this.singlePack = cfg.singlePack;
	}

	/**
	 * Check whether to reuse deltas existing in repository.
	 *
	 * Default setting: {@value #DEFAULT_REUSE_DELTAS}
	 *
	 * @return true if object is configured to reuse deltas; false otherwise.
	 */
	public boolean isReuseDeltas() {
		return reuseDeltas;
	}

	/**
	 * Set reuse deltas configuration option for the writer.
	 *
	 * When enabled, writer will search for delta representation of object in
	 * repository and use it if possible. Normally, only deltas with base to
	 * another object existing in set of objects to pack will be used. The
	 * exception however is thin-packs where the base object may exist on the
	 * other side.
	 *
	 * When raw delta data is directly copied from a pack file, its checksum is
	 * computed to verify the data is not corrupt.
	 *
	 * Default setting: {@value #DEFAULT_REUSE_DELTAS}
	 *
	 * @param reuseDeltas
	 *            boolean indicating whether or not try to reuse deltas.
	 */
	public void setReuseDeltas(boolean reuseDeltas) {
		this.reuseDeltas = reuseDeltas;
	}

	/**
	 * Checks whether to reuse existing objects representation in repository.
	 *
	 * Default setting: {@value #DEFAULT_REUSE_OBJECTS}
	 *
	 * @return true if writer is configured to reuse objects representation from
	 *         pack; false otherwise.
	 */
	public boolean isReuseObjects() {
		return reuseObjects;
	}

	/**
	 * Set reuse objects configuration option for the writer.
	 *
	 * If enabled, writer searches for compressed representation in a pack file.
	 * If possible, compressed data is directly copied from such a pack file.
	 * Data checksum is verified.
	 *
	 * Default setting: {@value #DEFAULT_REUSE_OBJECTS}
	 *
	 * @param reuseObjects
	 *            boolean indicating whether or not writer should reuse existing
	 *            objects representation.
	 */
	public void setReuseObjects(boolean reuseObjects) {
		this.reuseObjects = reuseObjects;
	}

	/**
	 * Checks whether to preserve old packs in a preserved directory
	 *
	 * Default setting: {@value #DEFAULT_PRESERVE_OLD_PACKS}
	 *
	 * @return true if repacking will preserve old pack files.
	 * @since 4.7
	 */
	public boolean isPreserveOldPacks() {
		return preserveOldPacks;
	}

	/**
	 * Set preserve old packs configuration option for repacking.
	 *
	 * If enabled, old pack files are moved into a preserved subdirectory instead
	 * of being deleted
	 *
	 * Default setting: {@value #DEFAULT_PRESERVE_OLD_PACKS}
	 *
	 * @param preserveOldPacks
	 *            boolean indicating whether or not preserve old pack files
	 * @since 4.7
	 */
	public void setPreserveOldPacks(boolean preserveOldPacks) {
		this.preserveOldPacks = preserveOldPacks;
	}

	/**
	 * Checks whether to remove preserved pack files in a preserved directory
	 *
	 * Default setting: {@value #DEFAULT_PRUNE_PRESERVED}
	 *
	 * @return true if repacking will remove preserved pack files.
	 * @since 4.7
	 */
	public boolean isPrunePreserved() {
		return prunePreserved;
	}

	/**
	 * Set prune preserved configuration option for repacking.
	 *
	 * If enabled, preserved pack files are removed from a preserved subdirectory
	 *
	 * Default setting: {@value #DEFAULT_PRESERVE_OLD_PACKS}
	 *
	 * @param prunePreserved
	 *            boolean indicating whether or not preserve old pack files
	 * @since 4.7
	 */
	public void setPrunePreserved(boolean prunePreserved) {
		this.prunePreserved = prunePreserved;
	}

	/**
	 * True if writer can use offsets to point to a delta base.
	 *
	 * If true the writer may choose to use an offset to point to a delta base
	 * in the same pack, this is a newer style of reference that saves space.
	 * False if the writer has to use the older (and more compatible style) of
	 * storing the full ObjectId of the delta base.
	 *
	 * Default setting: {@value #DEFAULT_DELTA_BASE_AS_OFFSET}
	 *
	 * @return true if delta base is stored as an offset; false if it is stored
	 *         as an ObjectId.
	 */
	public boolean isDeltaBaseAsOffset() {
		return deltaBaseAsOffset;
	}

	/**
	 * Set writer delta base format.
	 *
	 * Delta base can be written as an offset in a pack file (new approach
	 * reducing file size) or as an object id (legacy approach, compatible with
	 * old readers).
	 *
	 * Default setting: {@value #DEFAULT_DELTA_BASE_AS_OFFSET}
	 *
	 * @param deltaBaseAsOffset
	 *            boolean indicating whether delta base can be stored as an
	 *            offset.
	 */
	public void setDeltaBaseAsOffset(boolean deltaBaseAsOffset) {
		this.deltaBaseAsOffset = deltaBaseAsOffset;
	}

	/**
	 * Check whether the writer will create new deltas on the fly.
	 *
	 * Default setting: {@value #DEFAULT_DELTA_COMPRESS}
	 *
	 * @return true if the writer will create a new delta when either
	 *         {@link #isReuseDeltas()} is false, or no suitable delta is
	 *         available for reuse.
	 */
	public boolean isDeltaCompress() {
		return deltaCompress;
	}

	/**
	 * Set whether or not the writer will create new deltas on the fly.
	 *
	 * Default setting: {@value #DEFAULT_DELTA_COMPRESS}
	 *
	 * @param deltaCompress
	 *            true to create deltas when {@link #isReuseDeltas()} is false,
	 *            or when a suitable delta isn't available for reuse. Set to
	 *            false to write whole objects instead.
	 */
	public void setDeltaCompress(boolean deltaCompress) {
		this.deltaCompress = deltaCompress;
	}

	/**
	 * Get maximum depth of delta chain set up for the writer.
	 *
	 * Generated chains are not longer than this value.
	 *
	 * Default setting: {@value #DEFAULT_MAX_DELTA_DEPTH}
	 *
	 * @return maximum delta chain depth.
	 */
	public int getMaxDeltaDepth() {
		return maxDeltaDepth;
	}

	/**
	 * Set up maximum depth of delta chain for the writer.
	 *
	 * Generated chains are not longer than this value. Too low value causes low
	 * compression level, while too big makes unpacking (reading) longer.
	 *
	 * Default setting: {@value #DEFAULT_MAX_DELTA_DEPTH}
	 *
	 * @param maxDeltaDepth
	 *            maximum delta chain depth.
	 */
	public void setMaxDeltaDepth(int maxDeltaDepth) {
		this.maxDeltaDepth = maxDeltaDepth;
	}

	/**
	 * @return true if existing delta chains should be cut at
	 *         {@link #getMaxDeltaDepth()}. Default is false, allowing existing
	 *         chains to be of any length.
	 * @since 3.0
	 */
	public boolean getCutDeltaChains() {
		return cutDeltaChains;
	}

	/**
	 * Enable cutting existing delta chains at {@link #getMaxDeltaDepth()}.
	 *
	 * By default this is disabled and existing chains are kept at whatever
	 * length a prior packer was configured to create. This allows objects to be
	 * packed one with a large depth (for example 250), and later to quickly
	 * repack the repository with a shorter depth (such as 50), but reusing the
	 * complete delta chains created by the earlier 250 depth.
	 *
	 * @param cut
	 *            true to cut existing chains.
	 * @since 3.0
	 */
	public void setCutDeltaChains(boolean cut) {
		cutDeltaChains = cut;
	}

	/**
	 * @return true if all of refs/* should be packed in a single pack. Default
	 *        is false, packing a separate GC_REST pack for references outside
	 *        of refs/heads/* and refs/tags/*.
	 * @since 4.9
	 */
	public boolean getSinglePack() {
		return singlePack;
	}

	/**
	 * If {@code true}, packs a single GC pack for all objects reachable from
	 * refs/*. Otherwise packs the GC pack with objects reachable from
	 * refs/heads/* and refs/tags/*, and a GC_REST pack with the remaining
	 * reachable objects. Disabled by default, packing GC and GC_REST.
	 *
	 * @param single
	 *            true to pack a single GC pack rather than GC and GC_REST packs
	 * @since 4.9
	 */
	public void setSinglePack(boolean single) {
		singlePack = single;
	}

	/**
	 * Get the number of objects to try when looking for a delta base.
	 *
	 * This limit is per thread, if 4 threads are used the actual memory used
	 * will be 4 times this value.
	 *
	 * Default setting: {@value #DEFAULT_DELTA_SEARCH_WINDOW_SIZE}
	 *
	 * @return the object count to be searched.
	 */
	public int getDeltaSearchWindowSize() {
		return deltaSearchWindowSize;
	}

	/**
	 * Set the number of objects considered when searching for a delta base.
	 *
	 * Default setting: {@value #DEFAULT_DELTA_SEARCH_WINDOW_SIZE}
	 *
	 * @param objectCount
	 *            number of objects to search at once. Must be at least 2.
	 */
	public void setDeltaSearchWindowSize(int objectCount) {
		if (objectCount <= 2)
			setDeltaCompress(false);
		else
			deltaSearchWindowSize = objectCount;
	}

	/**
	 * Get maximum number of bytes to put into the delta search window.
	 *
	 * Default setting is 0, for an unlimited amount of memory usage. Actual
	 * memory used is the lower limit of either this setting, or the sum of
	 * space used by at most {@link #getDeltaSearchWindowSize()} objects.
	 *
	 * This limit is per thread, if 4 threads are used the actual memory limit
	 * will be 4 times this value.
	 *
	 * @return the memory limit.
	 */
	public long getDeltaSearchMemoryLimit() {
		return deltaSearchMemoryLimit;
	}

	/**
	 * Set the maximum number of bytes to put into the delta search window.
	 *
	 * Default setting is 0, for an unlimited amount of memory usage. If the
	 * memory limit is reached before {@link #getDeltaSearchWindowSize()} the
	 * window size is temporarily lowered.
	 *
	 * @param memoryLimit
	 *            Maximum number of bytes to load at once, 0 for unlimited.
	 */
	public void setDeltaSearchMemoryLimit(long memoryLimit) {
		deltaSearchMemoryLimit = memoryLimit;
	}

	/**
	 * Get the size of the in-memory delta cache.
	 *
	 * This limit is for the entire writer, even if multiple threads are used.
	 *
	 * Default setting: {@value #DEFAULT_DELTA_CACHE_SIZE}
	 *
	 * @return maximum number of bytes worth of delta data to cache in memory.
	 *         If 0 the cache is infinite in size (up to the JVM heap limit
	 *         anyway). A very tiny size such as 1 indicates the cache is
	 *         effectively disabled.
	 */
	public long getDeltaCacheSize() {
		return deltaCacheSize;
	}

	/**
	 * Set the maximum number of bytes of delta data to cache.
	 *
	 * During delta search, up to this many bytes worth of small or hard to
	 * compute deltas will be stored in memory. This cache speeds up writing by
	 * allowing the cached entry to simply be dumped to the output stream.
	 *
	 * Default setting: {@value #DEFAULT_DELTA_CACHE_SIZE}
	 *
	 * @param size
	 *            number of bytes to cache. Set to 0 to enable an infinite
	 *            cache, set to 1 (an impossible size for any delta) to disable
	 *            the cache.
	 */
	public void setDeltaCacheSize(long size) {
		deltaCacheSize = size;
	}

	/**
	 * Maximum size in bytes of a delta to cache.
	 *
	 * Default setting: {@value #DEFAULT_DELTA_CACHE_LIMIT}
	 *
	 * @return maximum size (in bytes) of a delta that should be cached.
	 */
	public int getDeltaCacheLimit() {
		return deltaCacheLimit;
	}

	/**
	 * Set the maximum size of a delta that should be cached.
	 *
	 * During delta search, any delta smaller than this size will be cached, up
	 * to the {@link #getDeltaCacheSize()} maximum limit. This speeds up writing
	 * by allowing these cached deltas to be output as-is.
	 *
	 * Default setting: {@value #DEFAULT_DELTA_CACHE_LIMIT}
	 *
	 * @param size
	 *            maximum size (in bytes) of a delta to be cached.
	 */
	public void setDeltaCacheLimit(int size) {
		deltaCacheLimit = size;
	}

	/**
	 * Get the maximum file size that will be delta compressed.
	 *
	 * Files bigger than this setting will not be delta compressed, as they are
	 * more than likely already highly compressed binary data files that do not
	 * delta compress well, such as MPEG videos.
	 *
	 * Default setting: {@value #DEFAULT_BIG_FILE_THRESHOLD}
	 *
	 * @return the configured big file threshold.
	 */
	public int getBigFileThreshold() {
		return bigFileThreshold;
	}

	/**
	 * Set the maximum file size that should be considered for deltas.
	 *
	 * Default setting: {@value #DEFAULT_BIG_FILE_THRESHOLD}
	 *
	 * @param bigFileThreshold
	 *            the limit, in bytes.
	 */
	public void setBigFileThreshold(int bigFileThreshold) {
		this.bigFileThreshold = bigFileThreshold;
	}

	/**
	 * Get the compression level applied to objects in the pack.
	 *
	 * Default setting: {@value java.util.zip.Deflater#DEFAULT_COMPRESSION}
	 *
	 * @return current compression level, see {@link java.util.zip.Deflater}.
	 */
	public int getCompressionLevel() {
		return compressionLevel;
	}

	/**
	 * Set the compression level applied to objects in the pack.
	 *
	 * Default setting: {@value java.util.zip.Deflater#DEFAULT_COMPRESSION}
	 *
	 * @param level
	 *            compression level, must be a valid level recognized by the
	 *            {@link java.util.zip.Deflater} class.
	 */
	public void setCompressionLevel(int level) {
		compressionLevel = level;
	}

	/**
	 * Get the number of threads used during delta compression.
	 *
	 * Default setting: 0 (auto-detect processors)
	 *
	 * @return number of threads used for delta compression. 0 will auto-detect
	 *         the threads to the number of available processors.
	 */
	public int getThreads() {
		return threads;
	}

	/**
	 * Set the number of threads to use for delta compression.
	 *
	 * During delta compression, if there are enough objects to be considered
	 * the writer will start up concurrent threads and allow them to compress
	 * different sections of the repository concurrently.
	 *
	 * An application thread pool can be set by {@link #setExecutor(Executor)}.
	 * If not set a temporary pool will be created by the writer, and torn down
	 * automatically when compression is over.
	 *
	 * Default setting: 0 (auto-detect processors)
	 *
	 * @param threads
	 *            number of threads to use. If &lt;= 0 the number of available
	 *            processors for this JVM is used.
	 */
	public void setThreads(int threads) {
		this.threads = threads;
	}

	/** @return the preferred thread pool to execute delta search on. */
	public Executor getExecutor() {
		return executor;
	}

	/**
	 * Set the executor to use when using threads.
	 *
	 * During delta compression if the executor is non-null jobs will be queued
	 * up on it to perform delta compression in parallel. Aside from setting the
	 * executor, the caller must set {@link #setThreads(int)} to enable threaded
	 * delta search.
	 *
	 * @param executor
	 *            executor to use for threads. Set to null to create a temporary
	 *            executor just for the writer.
	 */
	public void setExecutor(Executor executor) {
		this.executor = executor;
	}

	/**
	 * Get the pack index file format version this instance creates.
	 *
	 * Default setting: {@value #DEFAULT_INDEX_VERSION}
	 *
	 * @return the index version, the special version 0 designates the oldest
	 *         (most compatible) format available for the objects.
	 * @see PackIndexWriter
	 */
	public int getIndexVersion() {
		return indexVersion;
	}

	/**
	 * Set the pack index file format version this instance will create.
	 *
	 * Default setting: {@value #DEFAULT_INDEX_VERSION}
	 *
	 * @param version
	 *            the version to write. The special version 0 designates the
	 *            oldest (most compatible) format available for the objects.
	 * @see PackIndexWriter
	 */
	public void setIndexVersion(int version) {
		indexVersion = version;
	}

	/**
	 * True if writer is allowed to build bitmaps for indexes.
	 *
	 * Default setting: {@value #DEFAULT_BUILD_BITMAPS}
	 *
	 * @return true if delta base is the writer can choose to output an index
	 *         with bitmaps.
	 * @since 3.0
	 */
	public boolean isBuildBitmaps() {
		return buildBitmaps;
	}

	/**
	 * Set writer to allow building bitmaps for supported pack files.
	 *
	 * Index files can include bitmaps to speed up future ObjectWalks.
	 *
	 * Default setting: {@value #DEFAULT_BUILD_BITMAPS}
	 *
	 * @param buildBitmaps
	 *            boolean indicating whether bitmaps may be included in the
	 *            index.
	 * @since 3.0
	 */
	public void setBuildBitmaps(boolean buildBitmaps) {
		this.buildBitmaps = buildBitmaps;
	}

	/**
	 * Get the count of most recent commits for which to build bitmaps.
	 *
	 * Default setting: {@value #DEFAULT_BITMAP_CONTIGUOUS_COMMIT_COUNT}
	 *
	 * @return the count of most recent commits for which to build bitmaps
	 * @since 4.2
	 */
	public int getBitmapContiguousCommitCount() {
		return bitmapContiguousCommitCount;
	}

	/**
	 * Set the count of most recent commits for which to build bitmaps.
	 *
	 * Default setting: {@value #DEFAULT_BITMAP_CONTIGUOUS_COMMIT_COUNT}
	 *
	 * @param count
	 *            the count of most recent commits for which to build bitmaps
	 * @since 4.2
	 */
	public void setBitmapContiguousCommitCount(int count) {
		bitmapContiguousCommitCount = count;
	}

	/**
	 * Get the count at which to switch from "bitmapRecentCommitSpan" to
	 * "bitmapDistantCommitSpan".
	 *
	 * Default setting: {@value #DEFAULT_BITMAP_RECENT_COMMIT_COUNT}
	 *
	 * @return the count for switching between recent and distant spans
	 * @since 4.2
	 */
	public int getBitmapRecentCommitCount() {
		return bitmapRecentCommitCount;
	}

	/**
	 * Set the count at which to switch from "bitmapRecentCommitSpan" to
	 * "bitmapDistantCommitSpan".
	 *
	 * Default setting: {@value #DEFAULT_BITMAP_RECENT_COMMIT_COUNT}
	 *
	 * @param count
	 *            the count for switching between recent and distant spans
	 * @since 4.2
	 */
	public void setBitmapRecentCommitCount(int count) {
		bitmapRecentCommitCount = count;
	}

	/**
	 * Get the span of commits when building bitmaps for recent history.
	 *
	 * Default setting: {@value #DEFAULT_BITMAP_RECENT_COMMIT_SPAN}
	 *
	 * @return the span of commits when building bitmaps for recent history
	 * @since 4.2
	 */
	public int getBitmapRecentCommitSpan() {
		return bitmapRecentCommitSpan;
	}

	/**
	 * Set the span of commits when building bitmaps for recent history.
	 *
	 * Default setting: {@value #DEFAULT_BITMAP_RECENT_COMMIT_SPAN}
	 *
	 * @param span
	 *            the span of commits when building bitmaps for recent history
	 * @since 4.2
	 */
	public void setBitmapRecentCommitSpan(int span) {
		bitmapRecentCommitSpan = span;
	}

	/**
	 * Get the span of commits when building bitmaps for distant history.
	 *
	 * Default setting: {@value #DEFAULT_BITMAP_DISTANT_COMMIT_SPAN}
	 *
	 * @return the span of commits when building bitmaps for distant history
	 * @since 4.2
	 */
	public int getBitmapDistantCommitSpan() {
		return bitmapDistantCommitSpan;
	}

	/**
	 * Set the span of commits when building bitmaps for distant history.
	 *
	 * Default setting: {@value #DEFAULT_BITMAP_DISTANT_COMMIT_SPAN}
	 *
	 * @param span
	 *            the span of commits when building bitmaps for distant history
	 * @since 4.2
	 */
	public void setBitmapDistantCommitSpan(int span) {
		bitmapDistantCommitSpan = span;
	}

	/**
	 * Get the count of branches deemed "excessive". If the count of branches in
	 * a repository exceeds this number and bitmaps are enabled, "inactive"
	 * branches will have fewer bitmaps than "active" branches.
	 *
	 * Default setting: {@value #DEFAULT_BITMAP_EXCESSIVE_BRANCH_COUNT}
	 *
	 * @return the count of branches deemed "excessive"
	 * @since 4.2
	 */
	public int getBitmapExcessiveBranchCount() {
		return bitmapExcessiveBranchCount;
	}

	/**
	 * Set the count of branches deemed "excessive". If the count of branches in
	 * a repository exceeds this number and bitmaps are enabled, "inactive"
	 * branches will have fewer bitmaps than "active" branches.
	 *
	 * Default setting: {@value #DEFAULT_BITMAP_EXCESSIVE_BRANCH_COUNT}
	 *
	 * @param count
	 *            the count of branches deemed "excessive"
	 * @since 4.2
	 */
	public void setBitmapExcessiveBranchCount(int count) {
		bitmapExcessiveBranchCount = count;
	}

	/**
	 * Get the the age in days that marks a branch as "inactive".
	 *
	 * Default setting: {@value #DEFAULT_BITMAP_INACTIVE_BRANCH_AGE_IN_DAYS}
	 *
	 * @return the age in days that marks a branch as "inactive"
	 * @since 4.2
	 */
	public int getBitmapInactiveBranchAgeInDays() {
		return bitmapInactiveBranchAgeInDays;
	}

	/**
	 * Set the the age in days that marks a branch as "inactive".
	 *
	 * Default setting: {@value #DEFAULT_BITMAP_INACTIVE_BRANCH_AGE_IN_DAYS}
	 *
	 * @param ageInDays
	 *            the age in days that marks a branch as "inactive"
	 * @since 4.2
	 */
	public void setBitmapInactiveBranchAgeInDays(int ageInDays) {
		bitmapInactiveBranchAgeInDays = ageInDays;
	}

	/**
	 * Update properties by setting fields from the configuration.
	 *
	 * If a property's corresponding variable is not defined in the supplied
	 * configuration, then it is left unmodified.
	 *
	 * @param rc
	 *            configuration to read properties from.
	 */
	public void fromConfig(final Config rc) {
		setMaxDeltaDepth(rc.getInt("pack", "depth", getMaxDeltaDepth())); //$NON-NLS-1$ //$NON-NLS-2$
		setDeltaSearchWindowSize(rc.getInt(
				"pack", "window", getDeltaSearchWindowSize())); //$NON-NLS-1$ //$NON-NLS-2$
		setDeltaSearchMemoryLimit(rc.getLong(
				"pack", "windowmemory", getDeltaSearchMemoryLimit())); //$NON-NLS-1$ //$NON-NLS-2$
		setDeltaCacheSize(rc.getLong(
				"pack", "deltacachesize", getDeltaCacheSize())); //$NON-NLS-1$ //$NON-NLS-2$
		setDeltaCacheLimit(rc.getInt(
				"pack", "deltacachelimit", getDeltaCacheLimit())); //$NON-NLS-1$ //$NON-NLS-2$
		setCompressionLevel(rc.getInt("pack", "compression", //$NON-NLS-1$ //$NON-NLS-2$
				rc.getInt("core", "compression", getCompressionLevel()))); //$NON-NLS-1$ //$NON-NLS-2$
		setIndexVersion(rc.getInt("pack", "indexversion", getIndexVersion())); //$NON-NLS-1$ //$NON-NLS-2$
		setBigFileThreshold(rc.getInt(
				"core", "bigfilethreshold", getBigFileThreshold())); //$NON-NLS-1$ //$NON-NLS-2$
		setThreads(rc.getInt("pack", "threads", getThreads())); //$NON-NLS-1$ //$NON-NLS-2$

		// These variables aren't standardized
		//
		setReuseDeltas(rc.getBoolean("pack", "reusedeltas", isReuseDeltas())); //$NON-NLS-1$ //$NON-NLS-2$
		setReuseObjects(
				rc.getBoolean("pack", "reuseobjects", isReuseObjects())); //$NON-NLS-1$ //$NON-NLS-2$
		setDeltaCompress(
				rc.getBoolean("pack", "deltacompression", isDeltaCompress())); //$NON-NLS-1$ //$NON-NLS-2$
		setCutDeltaChains(
				rc.getBoolean("pack", "cutdeltachains", getCutDeltaChains())); //$NON-NLS-1$ //$NON-NLS-2$
		setSinglePack(
				rc.getBoolean("pack", "singlepack", getSinglePack())); //$NON-NLS-1$ //$NON-NLS-2$
		setBuildBitmaps(
				rc.getBoolean("pack", "buildbitmaps", isBuildBitmaps())); //$NON-NLS-1$ //$NON-NLS-2$
		setBitmapContiguousCommitCount(
				rc.getInt("pack", "bitmapcontiguouscommitcount", //$NON-NLS-1$ //$NON-NLS-2$
						getBitmapContiguousCommitCount()));
		setBitmapRecentCommitCount(rc.getInt("pack", "bitmaprecentcommitcount", //$NON-NLS-1$ //$NON-NLS-2$
				getBitmapRecentCommitCount()));
		setBitmapRecentCommitSpan(rc.getInt("pack", "bitmaprecentcommitspan", //$NON-NLS-1$ //$NON-NLS-2$
				getBitmapRecentCommitSpan()));
		setBitmapDistantCommitSpan(rc.getInt("pack", "bitmapdistantcommitspan", //$NON-NLS-1$ //$NON-NLS-2$
				getBitmapDistantCommitSpan()));
		setBitmapExcessiveBranchCount(rc.getInt("pack", //$NON-NLS-1$
				"bitmapexcessivebranchcount", getBitmapExcessiveBranchCount())); //$NON-NLS-1$
		setBitmapInactiveBranchAgeInDays(
				rc.getInt("pack", "bitmapinactivebranchageindays", //$NON-NLS-1$ //$NON-NLS-2$
						getBitmapInactiveBranchAgeInDays()));
	}

	@Override
	public String toString() {
		final StringBuilder b = new StringBuilder();
		b.append("maxDeltaDepth=").append(getMaxDeltaDepth()); //$NON-NLS-1$
		b.append(", deltaSearchWindowSize=").append(getDeltaSearchWindowSize()); //$NON-NLS-1$
		b.append(", deltaSearchMemoryLimit=") //$NON-NLS-1$
				.append(getDeltaSearchMemoryLimit());
		b.append(", deltaCacheSize=").append(getDeltaCacheSize()); //$NON-NLS-1$
		b.append(", deltaCacheLimit=").append(getDeltaCacheLimit()); //$NON-NLS-1$
		b.append(", compressionLevel=").append(getCompressionLevel()); //$NON-NLS-1$
		b.append(", indexVersion=").append(getIndexVersion()); //$NON-NLS-1$
		b.append(", bigFileThreshold=").append(getBigFileThreshold()); //$NON-NLS-1$
		b.append(", threads=").append(getThreads()); //$NON-NLS-1$
		b.append(", reuseDeltas=").append(isReuseDeltas()); //$NON-NLS-1$
		b.append(", reuseObjects=").append(isReuseObjects()); //$NON-NLS-1$
		b.append(", deltaCompress=").append(isDeltaCompress()); //$NON-NLS-1$
		b.append(", buildBitmaps=").append(isBuildBitmaps()); //$NON-NLS-1$
		b.append(", bitmapContiguousCommitCount=") //$NON-NLS-1$
				.append(getBitmapContiguousCommitCount());
		b.append(", bitmapRecentCommitCount=") //$NON-NLS-1$
				.append(getBitmapRecentCommitCount());
		b.append(", bitmapRecentCommitSpan=") //$NON-NLS-1$
				.append(getBitmapRecentCommitSpan());
		b.append(", bitmapDistantCommitSpan=") //$NON-NLS-1$
				.append(getBitmapDistantCommitSpan());
		b.append(", bitmapExcessiveBranchCount=") //$NON-NLS-1$
				.append(getBitmapExcessiveBranchCount());
		b.append(", bitmapInactiveBranchAge=") //$NON-NLS-1$
				.append(getBitmapInactiveBranchAgeInDays());
		b.append(", singlePack=").append(getSinglePack()); //$NON-NLS-1$
		return b.toString();
	}
}
