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

package org.eclipse.jgit.storage.dht;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.storage.dht.DhtReader.ChunkAndOffset;

/**
 * Caches recently used {@link PackChunk} in memory for faster read access.
 * <p>
 * During a miss, older entries are evicted from the cache so long as
 * {@link #isFull()} returns true.
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
 * To maintain higher concurrency workloads, during eviction only one thread
 * performs the eviction work, while other threads can continue to insert new
 * objects in parallel. This means that the cache can be temporarily over limit,
 * especially if the nominated eviction thread is being starved relative to the
 * other threads.
 */
public class ChunkCache {
	private static final Random rng = new Random();

	private static volatile ChunkCache cache;

	static {
		cache = new ChunkCache(new ChunkCacheConfig());
	}

	/**
	 * Modify the configuration of the chunk cache.
	 * <p>
	 * The new configuration is applied immediately. If the new limits are
	 * smaller than what what is currently cached, older entries will be purged
	 * as soon as possible to allow the cache to meet the new limit.
	 *
	 * @param cfg
	 *            the new chunk cache configuration.
	 * @throws IllegalArgumentException
	 *             the cache configuration contains one or more invalid
	 *             settings, usually too low of a limit.
	 */
	public static void reconfigure(ChunkCacheConfig cfg) {
		ChunkCache nc = new ChunkCache(cfg);
		cache = nc;
	}

	static ChunkCache get() {
		return cache;
	}

	/** ReferenceQueue to cleanup released and garbage collected windows. */
	private final ReferenceQueue<PackChunk> queue;

	/** Number of entries in {@link #table}. */
	private final int tableSize;

	/** Access clock for loose LRU. */
	private final AtomicLong clock;

	/** Hash bucket directory; entries are chained below. */
	private final AtomicReferenceArray<Entry> table;

	/** Locks to prevent concurrent loads for same (ChunkKey,position). */
	private final Lock[] locks;

	/** Lock to elect the eviction thread after a load occurs. */
	private final ReentrantLock evictLock;

	/** Number of {@link #table} buckets to scan for an eviction window. */
	private final int evictBatch;

	private final long maxBytes;

	private final AtomicLong openBytes;

	private ChunkCache(ChunkCacheConfig cfg) {
		tableSize = tableSize(cfg);
		final int lockCount = lockCount(cfg);
		if (tableSize < 0)
			throw new IllegalArgumentException();
		if (lockCount < 0)
			throw new IllegalArgumentException();

		queue = new ReferenceQueue<PackChunk>();
		clock = new AtomicLong(1);
		table = new AtomicReferenceArray<Entry>(tableSize);
		locks = new Lock[lockCount];
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

		maxBytes = cfg.getChunkCacheLimit();
		openBytes = new AtomicLong();
	}

	long getOpenBytes() {
		return openBytes.get();
	}

	private Ref createRef(ChunkKey key, PackChunk v) {
		final Ref ref = new Ref(key, v, queue);
		openBytes.addAndGet(ref.size);
		return ref;
	}

	private void clear(Ref ref) {
		openBytes.addAndGet(-ref.size);
	}

	private boolean isFull() {
		return maxBytes < openBytes.get();
	}

	private static int tableSize(ChunkCacheConfig cfg) {
		final int csz = 1 * ChunkCacheConfig.MiB;
		final long limit = cfg.getChunkCacheLimit();
		if (limit == 0)
			return 0;
		if (csz <= 0)
			throw new IllegalArgumentException();
		if (limit < csz)
			throw new IllegalArgumentException();
		return (int) Math.min(5 * (limit / csz) / 2, 2000000000);
	}

	private static int lockCount(ChunkCacheConfig cfg) {
		if (cfg.getChunkCacheLimit() == 0)
			return 0;
		return 32;
	}

	PackChunk get(ChunkKey chunkKey) {
		if (tableSize == 0)
			return null;
		return scan(table.get(slot(chunkKey)), chunkKey);
	}

	ChunkAndOffset find(RepositoryKey repo, AnyObjectId objId) {
		// TODO(spearce) This method violates our no-collision rules.
		// Its possible for a duplicate object to be uploaded into a new
		// chunk, and have that get used if the new chunk is pulled into
		// the process cache for a different object.

		for (int slot = 0; slot < tableSize; slot++) {
			for (Entry e = table.get(slot); e != null; e = e.next) {
				PackChunk chunk = e.ref.get();
				if (chunk != null) {
					int pos = chunk.findOffset(repo, objId);
					if (0 <= pos) {
						hit(e.ref);
						return new ChunkAndOffset(chunk, pos);
					}
				}
			}
		}
		return null;
	}

	PackChunk put(PackChunk chunk) {
		if (tableSize == 0)
			return chunk;

		final ChunkKey chunkKey = chunk.getChunkKey();
		final int slot = slot(chunkKey);
		final Entry e1 = table.get(slot);
		PackChunk v = scan(e1, chunkKey);
		if (v != null)
			return v;

		synchronized (lock(chunkKey)) {
			Entry e2 = table.get(slot);
			if (e2 != e1) {
				v = scan(e2, chunkKey);
				if (v != null)
					return v;
			}

			v = chunk;
			final Ref ref = createRef(chunkKey, v);
			hit(ref);
			for (;;) {
				final Entry n = new Entry(clean(e2), ref);
				if (table.compareAndSet(slot, e2, n))
					break;
				e2 = table.get(slot);
			}
		}

		if (evictLock.tryLock()) {
			try {
				gc();
				evict();
			} finally {
				evictLock.unlock();
			}
		}

		return v;
	}

	private PackChunk scan(Entry n, ChunkKey chunk) {
		for (; n != null; n = n.next) {
			Ref r = n.ref;
			if (r.chunk.equals(chunk)) {
				PackChunk v = r.get();
				if (v != null) {
					hit(r);
					return v;
				}
				n.kill();
				break;
			}
		}
		return null;
	}

	private void hit(final Ref r) {
		// We don't need to be 100% accurate here. Its sufficient that at least
		// one thread performs the increment. Any other concurrent access at
		// exactly the same time can simply use the same clock value.
		//
		// Consequently we attempt the set, but we don't try to recover should
		// it fail. This is why we don't use getAndIncrement() here.
		//
		final long c = clock.get();
		clock.compareAndSet(c, c + 1);
		r.lastAccess = c;
	}

	private void evict() {
		while (isFull()) {
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
				old.kill();
				gc();
				final Entry e1 = table.get(slot);
				table.compareAndSet(slot, e1, clean(e1));
			}
		}
	}

	private void gc() {
		Ref r;
		while ((r = (Ref) queue.poll()) != null) {
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
				final int s = slot(r.chunk);
				final Entry e1 = table.get(s);
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

	private int slot(ChunkKey chunk) {
		return (chunk.hashCode() >>> 1) % tableSize;
	}

	private Lock lock(ChunkKey chunk) {
		return locks[(chunk.hashCode() >>> 1) % locks.length];
	}

	private static Entry clean(Entry top) {
		while (top != null && top.dead) {
			top.ref.enqueue();
			top = top.next;
		}
		if (top == null)
			return null;
		final Entry n = clean(top.next);
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

	/** A soft reference wrapped around a cached object. */
	private static class Ref extends SoftReference<PackChunk> {
		final ChunkKey chunk;

		final int size;

		long lastAccess;

		private boolean cleared;

		Ref(ChunkKey chunk, PackChunk v, ReferenceQueue<PackChunk> queue) {
			super(v, queue);
			this.chunk = chunk;
			this.size = v.getTotalSize();
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
