/*
 * Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.dfs.ClockBlockCacheTable;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCache;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheConfig;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheTable;
import org.eclipse.jgit.internal.storage.dfs.DfsStreamKey;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.internal.util.Optionally;
import org.eclipse.jgit.storage.file.WindowCacheConfig;

/**
 * Bounds the heap used by the indexes of {@link Pack}s to
 * {@link WindowCacheConfig#getPackedIndexGitLimit()}.
 * <p>
 * The indexes are held by a {@link ClockBlockCacheTable}, the table that
 * {@link DfsBlockCache} uses for the blocks and indexes of the DFS backend.
 * When adding an index exceeds the limit, the table drops the indexes that
 * were not used since its last sweep, and the pack loads them again on next
 * use. Each index of a pack is a separate entry, so a cached bitmap index keeps
 * the primary and reverse index it refers to in memory even if they were
 * dropped; as in DFS the limit is a target rather than a hard bound.
 * <p>
 * The indexes of a closed pack stay in the table until it drops them. The
 * limit is meant to be set once, before repositories are opened: installing a
 * configuration with the same limit keeps the table, but indexes loaded under a
 * different limit are not accounted.
 */
final class PackIndexCache {
	/** Indexes are never looked up by key, they are only held by the table. */
	private static final long POSITION = 0;

	private static final long MIN_BLOCK_SIZE = 512;

	private static final long MAX_BLOCK_SIZE = 1 << 30;

	private static volatile DfsBlockCacheTable table;

	private static long tableLimit;

	private PackIndexCache() {
	}

	static synchronized void reconfigure(WindowCacheConfig cfg) {
		long limit = Math.max(0, cfg.getPackedIndexGitLimit());
		// A new table would not account the indexes held by the current one.
		if (limit == tableLimit) {
			return;
		}
		table = limit > 0 ? new ClockBlockCacheTable(toDfsConfig(limit))
				: null;
		tableLimit = limit;
	}

	private static DfsBlockCacheConfig toDfsConfig(long limit) {
		// The table has 2.5 * limit / blockSize hash slots. It holds a few
		// indexes per pack rather than blocks, so size it for at most 2^18
		// slots. Limits above 64 TiB are no limit in practice.
		long bytes = Math.min(limit, MAX_BLOCK_SIZE << 16);
		long blockSize = Long
				.highestOneBit(Math.max(MIN_BLOCK_SIZE, bytes >>> 16));
		return new DfsBlockCacheConfig().setBlockSize((int) blockSize)
				.setBlockLimit(Math.max(bytes, blockSize));
	}

	/**
	 * Hold an index in the cache, if a limit is configured.
	 *
	 * @param ext
	 *            type of the index
	 * @param index
	 *            the index
	 * @param size
	 *            estimated heap bytes used by the index
	 * @return the index held by the cache, or {@code null} if there is no
	 *         limit
	 */
	@Nullable
	static <T> Optionally<T> put(PackExt ext, T index, long size) {
		DfsBlockCacheTable t = table;
		if (t == null) {
			return null;
		}
		return new Cached<>(t.put(new Key(ext), POSITION, size, index));
	}

	@Nullable
	static DfsBlockCacheTable getTable() {
		return table;
	}

	private static final class Cached<T> implements Optionally<T> {
		private volatile DfsBlockCache.Ref<T> ref;

		Cached(DfsBlockCache.Ref<T> ref) {
			this.ref = ref;
		}

		@Override
		public void clear() {
			ref = null;
		}

		@Override
		public Optional<T> getOptional() {
			DfsBlockCache.Ref<T> r = ref;
			return r != null ? Optional.ofNullable(r.getAndMarkIfCold())
					: Optional.empty();
		}
	}

	/** Identifies one index of one {@link Pack} instance. */
	private static final class Key extends DfsStreamKey {
		private static final AtomicInteger nextHash = new AtomicInteger();

		Key(PackExt ext) {
			super(nextHash.incrementAndGet(), ext);
		}

		@Override
		public boolean equals(Object o) {
			return o == this;
		}

		@Override
		public int hashCode() {
			return super.hashCode();
		}
	}
}
