/*
 * Copyright (C) 2008-2011, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.internal.storage.dfs;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.JGitText;

/**
 * Caches slices of a {@link DfsPackFile} in memory for faster read access.
 * <p>
 * The DfsBlockCache serves as a Java based "buffer cache", loading segments of
 * a DfsPackFile into the JVM heap prior to use. As JGit often wants to do reads
 * of only tiny slices of a file, the DfsBlockCache tries to smooth out these
 * tiny reads into larger block-sized IO operations.
 * <p>
 * Whenever a cache miss occurs, loading is invoked by exactly one thread for
 * the given <code>(DfsPackKey,position)</code> key tuple. This is ensured by an
 * array of locks, with the tuple hashed to a lock instance.
 * <p>
 * Its too expensive during object access to be accurate with a least recently
 * used (LRU) algorithm. Strictly ordering every read is a lot of overhead that
 * typically doesn't yield a corresponding benefit to the application. This
 * cache implements a clock replacement algorithm, giving each block one chance
 * to have been accessed during a sweep of the cache to save itself from
 * eviction.
 * <p>
 * Entities created by the cache are held under hard references, preventing the
 * Java VM from clearing anything. Blocks are discarded by the replacement
 * algorithm when adding a new block would cause the cache to exceed its
 * configured maximum size.
 * <p>
 * The key tuple is passed through to methods as a pair of parameters rather
 * than as a single Object, thus reducing the transient memory allocations of
 * callers. It is more efficient to avoid the allocation, as we can't be 100%
 * sure that a JIT would be able to stack-allocate a key tuple.
 * <p>
 * The internal hash table does not expand at runtime, instead it is fixed in
 * size at cache creation time. The internal lock table used to gate load
 * invocations is also fixed in size.
 */
public final class DfsBlockCache {
	private static volatile DfsBlockCache cache;

	static {
		reconfigure(new DfsBlockCacheConfig());
	}

	/**
	 * Modify the configuration of the window cache.
	 * <p>
	 * The new configuration is applied immediately, and the existing cache is
	 * cleared.
	 *
	 * @param cfg
	 *            the new window cache configuration.
	 * @throws IllegalArgumentException
	 *             the cache configuration contains one or more invalid
	 *             settings, usually too low of a limit.
	 */
	public static void reconfigure(DfsBlockCacheConfig cfg) {
		DfsBlockCache nc = new DfsBlockCache(cfg);
		DfsBlockCache oc = cache;
		cache = nc;

		if (oc != null) {
			for (DfsPackFile pack : oc.getPackFiles())
				pack.key.cachedSize.set(0);
		}
	}

	/** @return the currently active DfsBlockCache. */
	public static DfsBlockCache getInstance() {
		return cache;
	}

	/** Number of entries in {@link #table}. */
	private final int tableSize;

	/** Hash bucket directory; entries are chained below. */
	private final AtomicReferenceArray<HashEntry> table;

	/** Locks to prevent concurrent loads for same (PackFile,position). */
	private final ReentrantLock[] loadLocks;

	/** Maximum number of bytes the cache should hold. */
	private final long maxBytes;

	/** Pack files smaller than this size can be copied through the cache. */
	private final long maxStreamThroughCache;

	/**
	 * Suggested block size to read from pack files in.
	 * <p>
	 * If a pack file does not have a native block size, this size will be used.
	 * <p>
	 * If a pack file has a native size, a whole multiple of the native size
	 * will be used until it matches this size.
	 * <p>
	 * The value for blockSize must be a power of 2.
	 */
	private final int blockSize;

	/** As {@link #blockSize} is a power of 2, bits to shift for a / blockSize. */
	private final int blockSizeShift;

	/** Cache of pack files, indexed by description. */
	private final Map<DfsPackDescription, DfsPackFile> packCache;

	/** View of pack files in the pack cache. */
	private final Collection<DfsPackFile> packFiles;

	/** Number of times a block was found in the cache. */
	private final AtomicLong statHit;

	/** Number of times a block was not found, and had to be loaded. */
	private final AtomicLong statMiss;

	/** Number of blocks evicted due to cache being full. */
	private volatile long statEvict;

	/** Protects the clock and its related data. */
	private final ReentrantLock clockLock;

	/** Current position of the clock. */
	private Ref clockHand;

	/** Number of bytes currently loaded in the cache. */
	private volatile long liveBytes;

	@SuppressWarnings("unchecked")
	private DfsBlockCache(final DfsBlockCacheConfig cfg) {
		tableSize = tableSize(cfg);
		if (tableSize < 1)
			throw new IllegalArgumentException(JGitText.get().tSizeMustBeGreaterOrEqual1);

		table = new AtomicReferenceArray<>(tableSize);
		loadLocks = new ReentrantLock[cfg.getConcurrencyLevel()];
		for (int i = 0; i < loadLocks.length; i++)
			loadLocks[i] = new ReentrantLock(true /* fair */);

		maxBytes = cfg.getBlockLimit();
		maxStreamThroughCache = (long) (maxBytes * cfg.getStreamRatio());
		blockSize = cfg.getBlockSize();
		blockSizeShift = Integer.numberOfTrailingZeros(blockSize);

		clockLock = new ReentrantLock(true /* fair */);
		clockHand = new Ref<>(new DfsPackKey(), -1, 0, null);
		clockHand.next = clockHand;

		packCache = new ConcurrentHashMap<>(
				16, 0.75f, 1);
		packFiles = Collections.unmodifiableCollection(packCache.values());

		statHit = new AtomicLong();
		statMiss = new AtomicLong();
	}

	boolean shouldCopyThroughCache(long length) {
		return length <= maxStreamThroughCache;
	}

	/** @return total number of bytes in the cache. */
	public long getCurrentSize() {
		return liveBytes;
	}

	/** @return 0..100, defining how full the cache is. */
	public long getFillPercentage() {
		return getCurrentSize() * 100 / maxBytes;
	}

	/** @return number of requests for items in the cache. */
	public long getHitCount() {
		return statHit.get();
	}

	/** @return number of requests for items not in the cache. */
	public long getMissCount() {
		return statMiss.get();
	}

	/** @return total number of requests (hit + miss). */
	public long getTotalRequestCount() {
		return getHitCount() + getMissCount();
	}

	/** @return 0..100, defining number of cache hits. */
	public long getHitRatio() {
		long hits = statHit.get();
		long miss = statMiss.get();
		long total = hits + miss;
		if (total == 0)
			return 0;
		return hits * 100 / total;
	}

	/** @return number of evictions performed due to cache being full. */
	public long getEvictions() {
		return statEvict;
	}

	/**
	 * Get the pack files stored in this cache.
	 *
	 * @return a collection of pack files, some of which may not actually be
	 *             present; the caller should check the pack's cached size.
	 */
	public Collection<DfsPackFile> getPackFiles() {
		return packFiles;
	}

	DfsPackFile getOrCreate(DfsPackDescription dsc, DfsPackKey key) {
		// TODO This table grows without bound. It needs to clean up
		// entries that aren't in cache anymore, and aren't being used
		// by a live DfsObjDatabase reference.

		DfsPackFile pack = packCache.get(dsc);
		if (pack != null && !pack.invalid()) {
			return pack;
		}

		// 'pack' either didn't exist or was invalid. Compute a new
		// entry atomically (guaranteed by ConcurrentHashMap).
		return packCache.compute(dsc, (k, v) -> {
			if (v != null && !v.invalid()) { // valid value added by
				return v;                    // another thread
			} else {
				return new DfsPackFile(
						this, dsc, key != null ? key : new DfsPackKey());
			}
		});
	}

	private int hash(int packHash, long off) {
		return packHash + (int) (off >>> blockSizeShift);
	}

	int getBlockSize() {
		return blockSize;
	}

	private static int tableSize(final DfsBlockCacheConfig cfg) {
		final int wsz = cfg.getBlockSize();
		final long limit = cfg.getBlockLimit();
		if (wsz <= 0)
			throw new IllegalArgumentException(JGitText.get().invalidWindowSize);
		if (limit < wsz)
			throw new IllegalArgumentException(JGitText.get().windowSizeMustBeLesserThanLimit);
		return (int) Math.min(5 * (limit / wsz) / 2, Integer.MAX_VALUE);
	}

	/**
	 * Lookup a cached object, creating and loading it if it doesn't exist.
	 *
	 * @param pack
	 *            the pack that "contains" the cached object.
	 * @param position
	 *            offset within <code>pack</code> of the object.
	 * @param ctx
	 *            current thread's reader.
	 * @param packChannel
	 *            optional channel to read {@code pack}.
	 * @return the object reference.
	 * @throws IOException
	 *             the reference was not in the cache and could not be loaded.
	 */
	DfsBlock getOrLoad(DfsPackFile pack, long position, DfsReader ctx,
			@Nullable ReadableChannel packChannel) throws IOException {
		final long requestedPosition = position;
		position = pack.alignToBlock(position);

		DfsPackKey key = pack.key;
		int slot = slot(key, position);
		HashEntry e1 = table.get(slot);
		DfsBlock v = scan(e1, key, position);
		if (v != null) {
			ctx.stats.blockCacheHit++;
			statHit.incrementAndGet();
			return v;
		}

		reserveSpace(blockSize);
		ReentrantLock regionLock = lockFor(key, position);
		regionLock.lock();
		try {
			HashEntry e2 = table.get(slot);
			if (e2 != e1) {
				v = scan(e2, key, position);
				if (v != null) {
					ctx.stats.blockCacheHit++;
					statHit.incrementAndGet();
					creditSpace(blockSize);
					return v;
				}
			}

			statMiss.incrementAndGet();
			boolean credit = true;
			try {
				v = pack.readOneBlock(position, ctx, packChannel);
				credit = false;
			} finally {
				if (credit)
					creditSpace(blockSize);
			}
			if (position != v.start) {
				// The file discovered its blockSize and adjusted.
				position = v.start;
				slot = slot(key, position);
				e2 = table.get(slot);
			}

			key.cachedSize.addAndGet(v.size());
			Ref<DfsBlock> ref = new Ref<>(key, position, v.size(), v);
			ref.hot = true;
			for (;;) {
				HashEntry n = new HashEntry(clean(e2), ref);
				if (table.compareAndSet(slot, e2, n))
					break;
				e2 = table.get(slot);
			}
			addToClock(ref, blockSize - v.size());
		} finally {
			regionLock.unlock();
		}

		// If the block size changed from the default, it is possible the block
		// that was loaded is the wrong block for the requested position.
		if (v.contains(pack.key, requestedPosition))
			return v;
		return getOrLoad(pack, requestedPosition, ctx, packChannel);
	}

	@SuppressWarnings("unchecked")
	private void reserveSpace(int reserve) {
		clockLock.lock();
		try {
			long live = liveBytes + reserve;
			if (maxBytes < live) {
				Ref prev = clockHand;
				Ref hand = clockHand.next;
				do {
					if (hand.hot) {
						// Value was recently touched. Clear
						// hot and give it another chance.
						hand.hot = false;
						prev = hand;
						hand = hand.next;
						continue;
					} else if (prev == hand)
						break;

					// No recent access since last scan, kill
					// value and remove from clock.
					Ref dead = hand;
					hand = hand.next;
					prev.next = hand;
					dead.next = null;
					dead.value = null;
					live -= dead.size;
					dead.pack.cachedSize.addAndGet(-dead.size);
					statEvict++;
				} while (maxBytes < live);
				clockHand = prev;
			}
			liveBytes = live;
		} finally {
			clockLock.unlock();
		}
	}

	private void creditSpace(int credit) {
		clockLock.lock();
		liveBytes -= credit;
		clockLock.unlock();
	}

	@SuppressWarnings("unchecked")
	private void addToClock(Ref ref, int credit) {
		clockLock.lock();
		try {
			if (credit != 0)
				liveBytes -= credit;
			Ref ptr = clockHand;
			ref.next = ptr.next;
			ptr.next = ref;
			clockHand = ref;
		} finally {
			clockLock.unlock();
		}
	}

	void put(DfsBlock v) {
		put(v.pack, v.start, v.size(), v);
	}

	<T> Ref<T> put(DfsPackKey key, long pos, int size, T v) {
		int slot = slot(key, pos);
		HashEntry e1 = table.get(slot);
		Ref<T> ref = scanRef(e1, key, pos);
		if (ref != null)
			return ref;

		reserveSpace(size);
		ReentrantLock regionLock = lockFor(key, pos);
		regionLock.lock();
		try {
			HashEntry e2 = table.get(slot);
			if (e2 != e1) {
				ref = scanRef(e2, key, pos);
				if (ref != null) {
					creditSpace(size);
					return ref;
				}
			}

			key.cachedSize.addAndGet(size);
			ref = new Ref<>(key, pos, size, v);
			ref.hot = true;
			for (;;) {
				HashEntry n = new HashEntry(clean(e2), ref);
				if (table.compareAndSet(slot, e2, n))
					break;
				e2 = table.get(slot);
			}
			addToClock(ref, 0);
		} finally {
			regionLock.unlock();
		}
		return ref;
	}

	boolean contains(DfsPackKey key, long position) {
		return scan(table.get(slot(key, position)), key, position) != null;
	}

	@SuppressWarnings("unchecked")
	<T> T get(DfsPackKey key, long position) {
		T val = (T) scan(table.get(slot(key, position)), key, position);
		if (val == null)
			statMiss.incrementAndGet();
		else
			statHit.incrementAndGet();
		return val;
	}

	private <T> T scan(HashEntry n, DfsPackKey pack, long position) {
		Ref<T> r = scanRef(n, pack, position);
		return r != null ? r.get() : null;
	}

	@SuppressWarnings("unchecked")
	private <T> Ref<T> scanRef(HashEntry n, DfsPackKey pack, long position) {
		for (; n != null; n = n.next) {
			Ref<T> r = n.ref;
			if (r.pack == pack && r.position == position)
				return r.get() != null ? r : null;
		}
		return null;
	}

	void remove(DfsPackFile pack) {
		packCache.remove(pack.getPackDescription());
	}

	private int slot(DfsPackKey pack, long position) {
		return (hash(pack.hash, position) >>> 1) % tableSize;
	}

	private ReentrantLock lockFor(DfsPackKey pack, long position) {
		return loadLocks[(hash(pack.hash, position) >>> 1) % loadLocks.length];
	}

	private static HashEntry clean(HashEntry top) {
		while (top != null && top.ref.next == null)
			top = top.next;
		if (top == null)
			return null;
		HashEntry n = clean(top.next);
		return n == top.next ? top : new HashEntry(n, top.ref);
	}

	private static final class HashEntry {
		/** Next entry in the hash table's chain list. */
		final HashEntry next;

		/** The referenced object. */
		final Ref ref;

		HashEntry(HashEntry n, Ref r) {
			next = n;
			ref = r;
		}
	}

	static final class Ref<T> {
		final DfsPackKey pack;
		final long position;
		final int size;
		volatile T value;
		Ref next;
		volatile boolean hot;

		Ref(DfsPackKey pack, long position, int size, T v) {
			this.pack = pack;
			this.position = position;
			this.size = size;
			this.value = v;
		}

		T get() {
			T v = value;
			if (v != null)
				hot = true;
			return v;
		}

		boolean has() {
			return value != null;
		}
	}
}
