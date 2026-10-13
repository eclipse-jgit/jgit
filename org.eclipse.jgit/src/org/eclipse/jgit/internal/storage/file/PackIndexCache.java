/*
 * Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jgit.storage.file.WindowCacheConfig;

/**
 * Bounds the heap used by the indexes held by {@link Pack} instances.
 * <p>
 * A {@link Pack} keeps its primary index, reverse index and bitmap index in
 * memory once loaded. Without a limit, every pack ever touched by the JVM
 * keeps those indexes until the pack is closed. This cache accounts for the
 * estimated size of these indexes per pack and, when
 * {@link WindowCacheConfig#getPackedIndexGitLimit()} is exceeded, drops all
 * indexes of the least recently used packs. Dropped indexes are transparently
 * reloaded on next use.
 * <p>
 * Packs are evicted as a whole (never a single index of a pack) because the
 * bitmap index references the primary and reverse index; evicting only some of
 * them would create unaccounted duplicate copies on reload.
 * <p>
 * Recency is approximated with the CLOCK algorithm: a {@link Pack} sets a flag
 * on every index access, and the eviction sweep gives flagged packs a second
 * chance. Eviction only clears volatile fields of the evicted packs and never
 * takes a {@link Pack} lock, so it is safe to charge while holding one. Packs
 * that are loading an index are pinned and skipped by eviction, see
 * {@link Pack#beginIndexLoad()}.
 * <p>
 * The limit is a target, not a hard bound: callers may still hold references
 * to evicted indexes, and packs whose indexes are being loaded are never
 * evicted, so heap usage can temporarily exceed it.
 * <p>
 * Only indexes loaded while a limit is configured are accounted. The limit is
 * meant to be configured once at startup, before repositories are opened.
 */
final class PackIndexCache {
	private static final PackIndexCache INSTANCE = new PackIndexCache();

	static PackIndexCache getInstance() {
		return INSTANCE;
	}

	static void reconfigure(WindowCacheConfig cfg) {
		INSTANCE.setLimit(cfg.getPackedIndexGitLimit());
	}

	private final Object lock = new Object();

	/** Charged packs in CLOCK order; the head is the next eviction candidate. */
	private final LinkedHashMap<Pack, Long> charged = new LinkedHashMap<>();

	private volatile long limit;

	private long usedBytes;

	private long evictionCount;

	private long evictedBytes;

	PackIndexCache() {
		this(0);
	}

	PackIndexCache(long limit) {
		this.limit = limit;
	}

	void setLimit(long newLimit) {
		synchronized (lock) {
			limit = newLimit;
			if (newLimit <= 0) {
				charged.clear();
				usedBytes = 0;
			} else {
				evict(null);
			}
		}
	}

	long getLimit() {
		return limit;
	}

	/**
	 * Update the accounted size of the indexes currently held by a pack and
	 * evict other packs' indexes if the limit is exceeded.
	 *
	 * @param pack
	 *            pack which just loaded an index
	 */
	void charge(Pack pack) {
		if (limit <= 0) {
			return;
		}
		synchronized (lock) {
			if (limit <= 0) {
				return;
			}
			long weight = pack.getIndexWeight();
			Long old = weight > 0 ? charged.put(pack, Long.valueOf(weight))
					: charged.remove(pack);
			usedBytes += weight - (old != null ? old.longValue() : 0);
			if (usedBytes > limit) {
				evict(pack);
			}
		}
	}

	/**
	 * Prevent eviction of a pack's indexes until
	 * {@link Pack#endIndexLoad()}.
	 * <p>
	 * Taking the lock guarantees that an eviction of this pack is either
	 * complete or not started, so the loader never observes a partially
	 * evicted pack.
	 *
	 * @param pack
	 *            the pack about to load an index
	 */
	void pin(Pack pack) {
		synchronized (lock) {
			pack.incrementIndexLoads();
		}
	}

	/**
	 * Stop accounting for a pack whose indexes were closed.
	 *
	 * @param pack
	 *            the pack
	 */
	void remove(Pack pack) {
		if (limit <= 0) {
			return;
		}
		synchronized (lock) {
			Long old = charged.remove(pack);
			if (old != null) {
				usedBytes -= old.longValue();
			}
		}
	}

	private void evict(Pack current) {
		// Each pack is either evicted or given a second chance at most once
		// per sweep, so two passes over the table are always enough.
		int budget = 2 * charged.size() + 1;
		while (usedBytes > limit && budget-- > 0) {
			Iterator<Map.Entry<Pack, Long>> it = charged.entrySet().iterator();
			if (!it.hasNext()) {
				return;
			}
			Map.Entry<Pack, Long> head = it.next();
			Pack p = head.getKey();
			long charge = head.getValue().longValue();
			it.remove();
			// Indexes held by SoftReferences may have been cleared by the
			// garbage collector since they were charged.
			long weight = Math.min(charge, p.getIndexWeight());
			usedBytes -= charge - weight;
			if (weight == 0) {
				continue;
			}
			boolean referenced = p.clearIndexReferenced();
			if (p == current || referenced || p.isIndexLoading()) {
				charged.put(p, Long.valueOf(weight));
				continue;
			}
			usedBytes -= weight;
			evictionCount++;
			evictedBytes += weight;
			p.evictIndices();
		}
	}

	long getUsedBytes() {
		synchronized (lock) {
			return usedBytes;
		}
	}

	long getEvictionCount() {
		synchronized (lock) {
			return evictionCount;
		}
	}

	long getEvictedBytes() {
		synchronized (lock) {
			return evictedBytes;
		}
	}

	int getPackCount() {
		synchronized (lock) {
			return charged.size();
		}
	}
}
