/*
 * Copyright (C) 2008-2009, Google Inc.
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

package org.eclipse.jgit.internal.storage.file;

import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.storage.file.WindowCacheConfig;

/**
 * Caches slices of a {@link PackFile} in memory for faster read access.
 * <p>
 * The WindowCache serves as a Java based "buffer cache", loading segments of a
 * PackFile into the JVM heap prior to use. As JGit often wants to do reads of
 * only tiny slices of a file, the WindowCache tries to smooth out these tiny
 * reads into larger block-sized IO operations.
 * <p>
 * Whenever a cache miss occurs, {@link #load(PackFile, long)} is invoked by
 * exactly one thread for the given <code>(PackFile,position)</code> key tuple.
 * This is ensured by an array of locks, with the tuple hashed to a lock
 * instance.
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
 * The key tuple is passed through to methods as a pair of parameters rather
 * than as a single Object, thus reducing the transient memory allocations of
 * callers. It is more efficient to avoid the allocation, as we can't be 100%
 * sure that a JIT would be able to stack-allocate a key tuple.
 * <p>
 * This cache has an implementation rule such that:
 * <ul>
 * <li>{@link #load(PackFile, long)} is invoked by at most one thread at a time
 * for a given <code>(PackFile,position)</code> tuple.</li>
 * <li>For every <code>load()</code> invocation there is exactly one
 * {@link #createRef(PackFile, long, ByteWindow)} invocation to wrap a
 * SoftReference around the cached entity.</li>
 * <li>For every Reference created by <code>createRef()</code> there will be
 * exactly one call to {@link #clear(Ref)} to cleanup any resources associated
 * with the (now expired) cached entity.</li>
 * </ul>
 * <p>
 * Therefore, it is safe to perform resource accounting increments during the
 * {@link #load(PackFile, long)} or
 * {@link #createRef(PackFile, long, ByteWindow)} methods, and matching
 * decrements during {@link #clear(Ref)}. Implementors may need to override
 * {@link #createRef(PackFile, long, ByteWindow)} in order to embed additional
 * accounting information into an implementation specific {@link Ref} subclass,
 * as the cached entity may have already been evicted by the JRE's garbage
 * collector.
 * <p>
 * To maintain higher concurrency workloads, during eviction only one thread
 * performs the eviction work, while other threads can continue to insert new
 * objects in parallel. This means that the cache can be temporarily over limit,
 * especially if the nominated eviction thread is being starved relative to the
 * other threads.
 */
public class WindowCache {
	private static final int bits(int newSize) {
		if (newSize < 4096)
			throw new IllegalArgumentException(JGitText.get().invalidWindowSize);
		if (Integer.bitCount(newSize) != 1)
			throw new IllegalArgumentException(JGitText.get().windowSizeMustBePowerOf2);
		return Integer.numberOfTrailingZeros(newSize);
	}

	private static final Random rng = new Random();

	private static volatile WindowCache cache;

	private static volatile int streamFileThreshold;

	static {
		reconfigure(new WindowCacheConfig());
	}

	/**
	 * Modify the configuration of the window cache.
	 * <p>
	 * The new configuration is applied immediately. If the new limits are
	 * smaller than what what is currently cached, older entries will be purged
	 * as soon as possible to allow the cache to meet the new limit.
	 *
	 * @deprecated use {@code cfg.install()} to avoid internal reference.
	 * @param cfg
	 *            the new window cache configuration.
	 * @throws IllegalArgumentException
	 *             the cache configuration contains one or more invalid
	 *             settings, usually too low of a limit.
	 */
	@Deprecated
	public static void reconfigure(final WindowCacheConfig cfg) {
		final WindowCache nc = new WindowCache(cfg);
		final WindowCache oc = cache;
		if (oc != null)
			oc.removeAll();
		cache = nc;
		streamFileThreshold = cfg.getStreamFileThreshold();
		DeltaBaseCache.reconfigure(cfg);
	}

	static int getStreamFileThreshold() {
		return streamFileThreshold;
	}

	static WindowCache getInstance() {
		return cache;
	}

	static final ByteWindow get(final PackFile pack, final long offset)
			throws IOException {
		final WindowCache c = cache;
		final ByteWindow r = c.getOrLoad(pack, c.toStart(offset));
		if (c != cache) {
			// The cache was reconfigured while we were using the old one
			// to load this window. The window is still valid, but our
			// cache may think its still live. Ensure the window is removed
			// from the old cache so resources can be released.
			//
			c.removeAll();
		}
		return r;
	}

	static final void purge(final PackFile pack) {
		cache.removeAll(pack);
	}

	/** ReferenceQueue to cleanup released and garbage collected windows. */
	private final ReferenceQueue<ByteWindow> queue;

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

	private final int maxFiles;

	private final long maxBytes;

	private final boolean mmap;

	private final int windowSizeShift;

	private final int windowSize;

	private final AtomicInteger openFiles;

	private final AtomicLong openBytes;

	private WindowCache(final WindowCacheConfig cfg) {
		tableSize = tableSize(cfg);
		final int lockCount = lockCount(cfg);
		if (tableSize < 1)
			throw new IllegalArgumentException(JGitText.get().tSizeMustBeGreaterOrEqual1);
		if (lockCount < 1)
			throw new IllegalArgumentException(JGitText.get().lockCountMustBeGreaterOrEqual1);

		queue = new ReferenceQueue<>();
		clock = new AtomicLong(1);
		table = new AtomicReferenceArray<>(tableSize);
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

		maxFiles = cfg.getPackedGitOpenFiles();
		maxBytes = cfg.getPackedGitLimit();
		mmap = cfg.isPackedGitMMAP();
		windowSizeShift = bits(cfg.getPackedGitWindowSize());
		windowSize = 1 << windowSizeShift;

		openFiles = new AtomicInteger();
		openBytes = new AtomicLong();

		if (maxFiles < 1)
			throw new IllegalArgumentException(JGitText.get().openFilesMustBeAtLeast1);
		if (maxBytes < windowSize)
			throw new IllegalArgumentException(JGitText.get().windowSizeMustBeLesserThanLimit);
	}

	int getOpenFiles() {
		return openFiles.get();
	}

	long getOpenBytes() {
		return openBytes.get();
	}

	private int hash(final int packHash, final long off) {
		return packHash + (int) (off >>> windowSizeShift);
	}

	private ByteWindow load(final PackFile pack, final long offset)
			throws IOException {
		if (pack.beginWindowCache())
			openFiles.incrementAndGet();
		try {
			if (mmap)
				return pack.mmap(offset, windowSize);
			return pack.read(offset, windowSize);
		} catch (IOException e) {
			close(pack);
			throw e;
		} catch (RuntimeException e) {
			close(pack);
			throw e;
		} catch (Error e) {
			close(pack);
			throw e;
		}
	}

	private Ref createRef(final PackFile p, final long o, final ByteWindow v) {
		final Ref ref = new Ref(p, o, v, queue);
		openBytes.addAndGet(ref.size);
		return ref;
	}

	private void clear(final Ref ref) {
		openBytes.addAndGet(-ref.size);
		close(ref.pack);
	}

	private void close(final PackFile pack) {
		if (pack.endWindowCache())
			openFiles.decrementAndGet();
	}

	private boolean isFull() {
		return maxFiles < openFiles.get() || maxBytes < openBytes.get();
	}

	private long toStart(final long offset) {
		return (offset >>> windowSizeShift) << windowSizeShift;
	}

	private static int tableSize(final WindowCacheConfig cfg) {
		final int wsz = cfg.getPackedGitWindowSize();
		final long limit = cfg.getPackedGitLimit();
		if (wsz <= 0)
			throw new IllegalArgumentException(JGitText.get().invalidWindowSize);
		if (limit < wsz)
			throw new IllegalArgumentException(JGitText.get().windowSizeMustBeLesserThanLimit);
		return (int) Math.min(5 * (limit / wsz) / 2, 2000000000);
	}

	private static int lockCount(final WindowCacheConfig cfg) {
		return Math.max(cfg.getPackedGitOpenFiles(), 32);
	}

	/**
	 * Lookup a cached object, creating and loading it if it doesn't exist.
	 *
	 * @param pack
	 *            the pack that "contains" the cached object.
	 * @param position
	 *            offset within <code>pack</code> of the object.
	 * @return the object reference.
	 * @throws IOException
	 *             the object reference was not in the cache and could not be
	 *             obtained by {@link #load(PackFile, long)}.
	 */
	private ByteWindow getOrLoad(final PackFile pack, final long position)
			throws IOException {
		final int slot = slot(pack, position);
		final Entry e1 = table.get(slot);
		ByteWindow v = scan(e1, pack, position);
		if (v != null)
			return v;

		synchronized (lock(pack, position)) {
			Entry e2 = table.get(slot);
			if (e2 != e1) {
				v = scan(e2, pack, position);
				if (v != null)
					return v;
			}

			v = load(pack, position);
			final Ref ref = createRef(pack, position, v);
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

	private ByteWindow scan(Entry n, final PackFile pack, final long position) {
		for (; n != null; n = n.next) {
			final Ref r = n.ref;
			if (r.pack == pack && r.position == position) {
				final ByteWindow v = r.get();
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

	/**
	 * Clear every entry from the cache.
	 * <p>
	 * This is a last-ditch effort to clear out the cache, such as before it
	 * gets replaced by another cache that is configured differently. This
	 * method tries to force every cached entry through {@link #clear(Ref)} to
	 * ensure that resources are correctly accounted for and cleaned up by the
	 * subclass. A concurrent reader loading entries while this method is
	 * running may cause resource accounting failures.
	 */
	private void removeAll() {
		for (int s = 0; s < tableSize; s++) {
			Entry e1;
			do {
				e1 = table.get(s);
				for (Entry e = e1; e != null; e = e.next)
					e.kill();
			} while (!table.compareAndSet(s, e1, null));
		}
		gc();
	}

	/**
	 * Clear all entries related to a single file.
	 * <p>
	 * Typically this method is invoked during {@link PackFile#close()}, when we
	 * know the pack is never going to be useful to us again (for example, it no
	 * longer exists on disk). A concurrent reader loading an entry from this
	 * same pack may cause the pack to become stuck in the cache anyway.
	 *
	 * @param pack
	 *            the file to purge all entries of.
	 */
	private void removeAll(final PackFile pack) {
		for (int s = 0; s < tableSize; s++) {
			final Entry e1 = table.get(s);
			boolean hasDead = false;
			for (Entry e = e1; e != null; e = e.next) {
				if (e.ref.pack == pack) {
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
				final int s = slot(r.pack, r.position);
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

	private int slot(final PackFile pack, final long position) {
		return (hash(pack.hash, position) >>> 1) % tableSize;
	}

	private Lock lock(final PackFile pack, final long position) {
		return locks[(hash(pack.hash, position) >>> 1) % locks.length];
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
	private static class Ref extends SoftReference<ByteWindow> {
		final PackFile pack;

		final long position;

		final int size;

		long lastAccess;

		private boolean cleared;

		protected Ref(final PackFile pack, final long position,
				final ByteWindow v, final ReferenceQueue<ByteWindow> queue) {
			super(v, queue);
			this.pack = pack;
			this.position = position;
			this.size = v.size();
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
