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

package org.eclipse.jgit.storage.dfs;

import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.JGitText;

/**
 * Caches slices of a {@link DfsPackFile} in memory for faster read access.
 * <p>
 * The DfsBlockCache serves as a Java based "buffer cache", loading segments of
 * a PackFile into the JVM heap prior to use. As JGit often wants to do reads of
 * only tiny slices of a file, the DfsBlockCache tries to smooth out these tiny
 * reads into larger block-sized IO operations.
 * <p>
 * Whenever a cache miss occurs, loading is invoked by exactly one thread for
 * the given <code>(DfsPackKey,position)</code> key tuple. This is ensured by an
 * array of locks, with the tuple hashed to a lock instance.
 * <p>
 * Its too expensive during object access to be 100% accurate with a least
 * recently used (LRU) algorithm. Strictly ordering every read is a lot of
 * overhead that typically doesn't yield a corresponding benefit to the
 * application.
 * <p>
 * This cache implements a loose LRU policy by randomly picking a window
 * comprised of roughly 10% of the cache, and evicting the oldest accessed entry
 * within that window.
 * <p>
 * Entities created by the cache are held under SoftReferences, permitting the
 * Java runtime's garbage collector to evict entries when heap memory gets low.
 * Most JREs implement a loose least recently used algorithm for this eviction.
 * <p>
 * The internal hash table does not expand at runtime, instead it is fixed in
 * size at cache creation time. The internal lock table used to gate load
 * invocations is also fixed in size.
 * <p>
 * The key tuple is passed through to methods as a pair of parameters rather
 * than as a single Object, thus reducing the transient memory allocations of
 * callers. It is more efficient to avoid the allocation, as we can't be 100%
 * sure that a JIT would be able to stack-allocate a key tuple.
 * <p>
 * To maintain higher concurrency workloads, during eviction only one thread
 * performs the eviction work, while other threads can continue to insert new
 * objects in parallel. This means that the cache can be temporarily over limit,
 * especially if the nominated eviction thread is being starved relative to the
 * other threads.
 */
public final class DfsBlockCache {
	private static final Random rng = new Random();

	private static volatile DfsBlockCache cache;

	static {
		reconfigure(new DfsBlockCacheConfig());
	}

	/**
	 * Modify the configuration of the window cache.
	 * <p>
	 * The new configuration is applied immediately. If the new limits are
	 * smaller than what what is currently cached, older entries will be purged
	 * as soon as possible to allow the cache to meet the new limit.
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

		if (oc != null && oc.readAheadService != null)
			oc.readAheadService.shutdown();
	}

	/** @return the currently active DfsBlockCache. */
	public static DfsBlockCache getInstance() {
		return cache;
	}

	/** ReferenceQueue to cleanup released and garbage collected windows. */
	private final ReferenceQueue<Object> deadBlockQueue;

	/** Number of entries in {@link #table}. */
	private final int tableSize;

	/** Access clock for loose LRU. */
	private final AtomicLong clock;

	/** Hash bucket directory; entries are chained below. */
	private final AtomicReferenceArray<Entry> table;

	/** Locks to prevent concurrent loads for same (PackFile,position). */
	private final Lock[] locks;

	/** Lock to elect the eviction thread after a load occurs. */
	private final ReentrantLock evictLock;

	/** Number of {@link #table} buckets to scan for an eviction window. */
	private final int evictBatch;

	/** Maximum number of bytes the cache should hold. */
	private final long maxBytes;

	/**
	 * Suggested block size to read from pack files in.
	 * <p>
	 * If a pack file does not have a native block size, this size will be used.
	 * <p>
	 * If a pack file has a native size, a whole multiple of the native size
	 * will be used until it matches this size.
	 */
	private final int blockSize;

	/** As {@link #blockSize} is a power of 2, bits to shift for a / blockSize. */
	private final int blockSizeShift;

	/** Number of bytes to read-ahead from current read position. */
	private final int readAheadLimit;

	/** Thread pool to handle optimistic read-ahead. */
	private final ThreadPoolExecutor readAheadService;

	/** Cache of pack files, indexed by description. */
	private final Map<DfsPackDescription, DfsPackFile> packCache;

	/** Number of bytes held by this cache. */
	private final AtomicLong openBytes;

	/** Number of times a block was found in the cache. */
	private final AtomicLong statHit;

	/** Number of times a block was not found, and had to be loaded. */
	private final AtomicLong statMiss;

	/** Number of blocks evicted due to cache being full. */
	private final AtomicLong statEvict;

	/** Number of blocks removed from the cache by the Java GC. */
	private final AtomicLong statGC;

	private DfsBlockCache(final DfsBlockCacheConfig cfg) {
		tableSize = tableSize(cfg);
		if (tableSize < 1)
			throw new IllegalArgumentException(JGitText.get().tSizeMustBeGreaterOrEqual1);

		deadBlockQueue = new ReferenceQueue<Object>();
		clock = new AtomicLong(1);
		table = new AtomicReferenceArray<Entry>(tableSize);
		locks = new Lock[32];
		for (int i = 0; i < locks.length; i++)
			locks[i] = new Lock();
		evictLock = new ReentrantLock();

		int eb = (int) (tableSize * .1);
		if (64 < eb)
			eb = 64;
		else if (eb < 4)
			eb = 4;
		if (tableSize < eb)
			eb = tableSize;
		evictBatch = eb;

		maxBytes = cfg.getBlockLimit();
		blockSize = cfg.getBlockSize();
		blockSizeShift = Integer.numberOfTrailingZeros(blockSize);

		readAheadLimit = cfg.getReadAheadLimit();
		readAheadService = cfg.getReadAheadService();

		packCache = new HashMap<DfsPackDescription, DfsPackFile>();

		openBytes = new AtomicLong();
		statHit = new AtomicLong();
		statMiss = new AtomicLong();
		statEvict = new AtomicLong();
		statGC = new AtomicLong();
	}

	/** @return total number of bytes in the cache. */
	public long getCurrentSize() {
		return openBytes.get();
	}

	/** @return 0..100, defining how full the cache is. */
	public long getFillPercentage() {
		return getCurrentSize() * 100 / maxBytes;
	}

	/** @return 0..100, defining number of cache hits. */
	public long getHitRatio() {
		long hits = statHit.get();
		long miss = statMiss.get();
		long tot = hits + miss;
		if (tot == 0)
			return 0;
		return hits * 100 / tot;
	}

	/** @return number of evictions performed due to cache being full. */
	public long getEvictions() {
		return statEvict.get();
	}

	/** @return number of evictions caused by SoftReferences killed by Java GC. */
	public long getEvictionsCausedByGC() {
		return statGC.get();
	}

	DfsPackFile getOrCreate(DfsPackDescription dsc, DfsPackKey key) {
		// TODO This table grows without bound. It needs to clean up
		// entries that aren't in cache anymore, and aren't being used
		// by a live DfsObjDatabase reference.
		synchronized (packCache) {
			DfsPackFile pack = packCache.get(dsc);
			if (pack == null) {
				if (key == null)
					key = new DfsPackKey();
				pack = new DfsPackFile(this, dsc, key);
				packCache.put(dsc, pack);
			}
			return pack;
		}
	}

	private int hash(int packHash, long off) {
		return packHash + (int) (off >>> blockSizeShift);
	}

	private <T> Ref<T> createRef(DfsPackKey pack, long pos, int size, T v) {
		Ref<T> ref = new Ref<T>(pack, pos, size, v, deadBlockQueue);
		openBytes.addAndGet(ref.size);
		return ref;
	}

	private void clear(final Ref ref) {
		openBytes.addAndGet(-ref.size);
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
		return (int) Math.min(5 * (limit / wsz) / 2, 2000000000);
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
	 * @return the object reference.
	 * @throws IOException
	 *             the reference was not in the cache and could not be loaded.
	 */
	DfsBlock getOrLoad(DfsPackFile pack, long position, DfsReader ctx)
			throws IOException {
		final long requestedPosition = position;
		position = pack.alignToBlock(position);

		DfsPackKey key = pack.key;
		int slot = slot(key, position);
		Entry e1 = table.get(slot);
		DfsBlock v = scan(e1, key, position);
		if (v != null) {
			statHit.incrementAndGet();
			return v;
		}

		synchronized (lock(key, position)) {
			Entry e2 = table.get(slot);
			if (e2 != e1) {
				v = scan(e2, key, position);
				if (v != null) {
					statHit.incrementAndGet();
					return v;
				}
			}

			statMiss.incrementAndGet();
			v = pack.readOneBlock(position, ctx);
			if (position != v.start) {
				// The file discovered its blockSize and adjusted.
				position = v.start;
				slot = slot(key, position);
				e2 = table.get(slot);
			}

			Ref<DfsBlock> ref = createRef(key, position, v.size(), v);
			hit(ref);
			for (;;) {
				Entry n = new Entry(clean(e2), ref);
				if (table.compareAndSet(slot, e2, n))
					break;
				e2 = table.get(slot);
			}
		}
		evictIfNecessary();

		// If the block size changed from the default, it is possible the block
		// that was loaded is the wrong block for the requested position.
		if (v.contains(pack.key, requestedPosition))
			return v;
		return getOrLoad(pack, requestedPosition, ctx);
	}

	void put(DfsBlock v) {
		put(v.pack, v.start, v.size(), v);
	}

	<T> Ref<T> put(DfsPackKey key, long pos, int size, T v) {
		int slot = slot(key, pos);
		Entry e1 = table.get(slot);
		Ref<T> ref = scanRef(e1, key, pos);
		if (ref != null)
			return ref;

		synchronized (lock(key, pos)) {
			Entry e2 = table.get(slot);
			if (e2 != e1) {
				ref = scanRef(e2, key, pos);
				if (ref != null)
					return ref;
			}

			ref = createRef(key, pos, size, v);
			hit(ref);
			for (;;) {
				Entry n = new Entry(clean(e2), ref);
				if (table.compareAndSet(slot, e2, n))
					break;
				e2 = table.get(slot);
			}
		}
		evictIfNecessary();
		return ref;
	}

	boolean contains(DfsPackKey key, long position) {
		return get(key, position) != null;
	}

	<T> T get(DfsPackKey key, long position) {
		T b = scan(table.get(slot(key, position)), key, position);
		if (b != null)
			statHit.incrementAndGet();
		return b;
	}

	boolean readAhead(ReadableChannel rc, DfsPackKey key, int size, long pos,
			long len, DfsReader ctx) {
		if (!ctx.wantReadAhead() || readAheadLimit <= 0 || readAheadService == null)
			return false;

		int cap = readAheadLimit / size;
		long readAheadEnd = pos + readAheadLimit;
		List<ReadAheadTask.BlockFuture> blocks = new ArrayList<ReadAheadTask.BlockFuture>(cap);
		while (pos < readAheadEnd && pos < len) {
			long end = Math.min(pos + size, len);
			if (!contains(key, pos))
				blocks.add(new ReadAheadTask.BlockFuture(key, pos, end));
			pos = end;
		}
		if (blocks.isEmpty())
			return false;

		ReadAheadTask task = new ReadAheadTask(this, rc, blocks);
		ReadAheadTask.TaskFuture t = new ReadAheadTask.TaskFuture(task);
		for (ReadAheadTask.BlockFuture b : blocks)
			b.setTask(t);
		readAheadService.execute(t);
		ctx.startedReadAhead(blocks);
		return true;
	}

	@SuppressWarnings("unchecked")
	private <T> T scan(Entry n, DfsPackKey pack, long position) {
		for (; n != null; n = n.next) {
			Ref<T> r = n.ref;
			if (r.pack == pack && r.position == position) {
				T v = r.get();
				if (v != null) {
					hit(r);
					return v;
				}
				if (!n.dead)
					statGC.incrementAndGet();
				n.kill();
				break;
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private <T> Ref<T> scanRef(Entry n, DfsPackKey pack, long position) {
		for (; n != null; n = n.next) {
			Ref<T> r = n.ref;
			if (r.pack == pack && r.position == position) {
				if (r.get() != null)
					return r;
				if (!n.dead)
					statGC.incrementAndGet();
				n.kill();
				break;
			}
		}
		return null;
	}

	<T> void hit(final Ref<T> r) {
		// We don't need to be 100% accurate here. Its sufficient that at least
		// one thread performs the increment. Any other concurrent access at
		// exactly the same time can simply use the same clock value.
		//
		// Consequently we attempt the set, but we don't try to recover should
		// it fail. This is why we don't use getAndIncrement() here.
		//
		long c = clock.get();
		clock.compareAndSet(c, c + 1);
		r.lastAccess = c;
	}

	private void evictIfNecessary() {
		if (maxBytes < openBytes.get() && evictLock.tryLock()) {
			try {
				gc();
				if (maxBytes < openBytes.get())
					evict();
			} finally {
				evictLock.unlock();
			}
		}
	}

	private void evict() {
		long target = (long) (maxBytes * 0.9);
		while (target < openBytes.get()) {
			int ptr = rng.nextInt(tableSize);
			Entry old = null;
			int slot = 0;
			for (int b = evictBatch - 1; b >= 0; b--, ptr++) {
				if (tableSize <= ptr)
					ptr = 0;
				for (Entry e = table.get(ptr); e != null; e = e.next) {
					if (e.dead)
						continue;
					if (old == null || e.ref.lastAccess < old.ref.lastAccess) {
						old = e;
						slot = ptr;
					}
				}
			}
			if (old != null) {
				statEvict.incrementAndGet();
				old.kill();
				gc();
				Entry e1 = table.get(slot);
				table.compareAndSet(slot, e1, clean(e1));
			}
		}
	}

	/**
	 * Clear all entries related to a single file.
	 * <p>
	 * Typically this method is invoked during {@link DfsPackFile#close()}, when
	 * we know the pack is never going to be useful to us again (for example, it
	 * no longer exists on disk). A concurrent reader loading an entry from this
	 * same pack may cause the pack to become stuck in the cache anyway.
	 *
	 * @param pack
	 *            the file to purge all entries of.
	 */
	void remove(DfsPackFile pack) {
		synchronized (packCache) {
			packCache.remove(pack.getPackDescription());
		}

		DfsPackKey key = pack.key;
		for (int s = 0; s < tableSize; s++) {
			Entry e1 = table.get(s);
			boolean hasDead = false;
			for (Entry e = e1; e != null; e = e.next) {
				if (e.ref.pack == key) {
					e.kill();
					hasDead = true;
				} else if (e.dead)
					hasDead = true;
			}
			if (hasDead)
				table.compareAndSet(s, e1, clean(e1));
		}
		gc();
	}

	private void gc() {
		Ref r;
		while ((r = (Ref) deadBlockQueue.poll()) != null) {
			// Sun's Java 5 and 6 implementation have a bug where a Reference
			// can be enqueued and dequeued twice on the same reference queue
			// due to a race condition within ReferenceQueue.enqueue(Reference).
			//
			// http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6837858
			//
			// We CANNOT permit a Reference to come through us twice, as it will
			// skew the resource counters we maintain. Our canClear() check here
			// provides a way to skip the redundant dequeues, if any.
			//
			if (r.canClear()) {
				clear(r);

				boolean found = false;
				int s = slot(r.pack, r.position);
				Entry e1 = table.get(s);
				for (Entry n = e1; n != null; n = n.next) {
					if (n.ref == r) {
						n.dead = true;
						found = true;
						break;
					}
				}
				if (found)
					table.compareAndSet(s, e1, clean(e1));
			}
		}
	}

	private int slot(DfsPackKey pack, long position) {
		return (hash(pack.hash, position) >>> 1) % tableSize;
	}

	private Lock lock(DfsPackKey pack, long position) {
		return locks[(hash(pack.hash, position) >>> 1) % locks.length];
	}

	private static Entry clean(Entry top) {
		while (top != null && top.dead) {
			top.ref.enqueue();
			top = top.next;
		}
		if (top == null)
			return null;
		Entry n = clean(top.next);
		return n == top.next ? top : new Entry(n, top.ref);
	}

	private static class Entry {
		/** Next entry in the hash table's chain list. */
		final Entry next;

		/** The referenced object. */
		final Ref ref;

		/**
		 * Marked true when ref.get() returns null and the ref is dead.
		 * <p>
		 * A true here indicates that the ref is no longer accessible, and that
		 * we therefore need to eventually purge this Entry object out of the
		 * bucket's chain.
		 */
		volatile boolean dead;

		Entry(final Entry n, final Ref r) {
			next = n;
			ref = r;
		}

		final void kill() {
			dead = true;
			ref.enqueue();
		}
	}

	/**
	 * A soft reference wrapped around a cached object.
	 *
	 * @param <T>
	 */
	static class Ref<T> extends SoftReference<T> {
		final DfsPackKey pack;

		final long position;

		final int size;

		long lastAccess;

		private boolean cleared;

		Ref(DfsPackKey pack, long position, int size, T v,
				ReferenceQueue<Object> queue) {
			super(v, queue);
			this.pack = pack;
			this.position = position;
			this.size = size;
		}

		final synchronized boolean canClear() {
			if (cleared)
				return false;
			cleared = true;
			return true;
		}
	}

	private static final class Lock {
		// Used only for its implicit monitor.
	}
}
