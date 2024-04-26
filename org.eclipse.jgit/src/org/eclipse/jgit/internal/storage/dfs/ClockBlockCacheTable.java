/*
 * Copyright (c) 2024, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.LongStream;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCache.ReadableChannelSupplier;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCache.Ref;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCache.RefLoader;
import org.eclipse.jgit.internal.storage.pack.PackExt;

/**
 * Default implementation of the {@link DfsBlockCacheTable}.
 * <p>
 * This cache implements a clock replacement algorithm, giving each block at
 * least one chance to have been accessed during a sweep of the cache to save
 * itself from eviction. The number of swipe chances is configurable per pack
 * extension.
 * <p>
 * Entities created by the cache are held under hard references, preventing the
 * Java VM from clearing anything. Blocks are discarded by the replacement
 * algorithm when adding a new block would cause the cache to exceed its
 * configured maximum size.
 * <p>
 * Whenever a cache miss occurs, loading is invoked by exactly one thread for
 * the given <code>(DfsStreamKey,position)</code> key tuple. This is ensured by
 * an array of locks, with the tuple hashed to a lock instance.
 * <p>
 * The internal hash table does not expand at runtime, instead it is fixed in
 * size at cache creation time. The internal lock table used to gate load
 * invocations is also fixed in size.
 */
final class ClockBlockCacheTable implements DfsBlockCacheTable {
	/** Number of entries in {@link #table}. */
	private final int tableSize;

	/** Maximum number of bytes the cache should hold. */
	private final long maxBytes;

	/**
	 * Used to reserve space for blocks.
	 * <p>
	 * The value for blockSize must be a power of 2.
	 */
	private final int blockSize;

	private final Hash hash;

	/** Hash bucket directory; entries are chained below. */
	private final AtomicReferenceArray<HashEntry> table;

	/**
	 * Locks to prevent concurrent loads for same (PackFile,position) block. The
	 * number of locks is {@link DfsBlockCacheConfig#getConcurrencyLevel()} to
	 * cap the overall concurrent block loads.
	 */
	private final ReentrantLock[] loadLocks;

	/**
	 * A separate pool of locks per pack extension to prevent concurrent loads
	 * for same index or bitmap from PackFile.
	 */
	private final ReentrantLock[][] refLocks;

	/** Protects the clock and its related data. */
	private final ReentrantLock clockLock;

	/** Current position of the clock. */
	private Ref clockHand;

	private final DfsBlockCacheStats dfsBlockCacheStats;

	/**
	 * A consumer of object reference lock wait time milliseconds. May be used
	 * to build a metric.
	 */
	private final Consumer<Long> refLockWaitTime;

	/** Consumer of loading and eviction events of indexes. */
	private final DfsBlockCacheConfig.IndexEventConsumer indexEventConsumer;

	/** Stores timestamps of the last eviction of indexes. */
	private final Map<EvictKey, Long> indexEvictionMap = new ConcurrentHashMap<>();

	ClockBlockCacheTable(DfsBlockCacheConfig cfg) {
		this.tableSize = tableSize(cfg);
		if (tableSize < 1) {
			throw new IllegalArgumentException(
					JGitText.get().tSizeMustBeGreaterOrEqual1);
		}
		int concurrencyLevel = cfg.getConcurrencyLevel();
		this.maxBytes = cfg.getBlockLimit();
		this.blockSize = cfg.getBlockSize();
		int blockSizeShift = Integer.numberOfTrailingZeros(blockSize);
		this.hash = new Hash(blockSizeShift);
		table = new AtomicReferenceArray<>(tableSize);

		loadLocks = new ReentrantLock[concurrencyLevel];
		for (int i = 0; i < loadLocks.length; i++) {
			loadLocks[i] = new ReentrantLock(/* fair= */ true);
		}
		refLocks = new ReentrantLock[PackExt.values().length][concurrencyLevel];
		for (int i = 0; i < PackExt.values().length; i++) {
			for (int j = 0; j < concurrencyLevel; ++j) {
				refLocks[i][j] = new ReentrantLock(/* fair= */ true);
			}
		}

		clockLock = new ReentrantLock(/* fair= */ true);
		String none = ""; //$NON-NLS-1$
		clockHand = new Ref<>(
				DfsStreamKey.of(new DfsRepositoryDescription(none), none, null),
				-1, 0, null);
		clockHand.next = clockHand;

		this.dfsBlockCacheStats = new DfsBlockCacheStats();
		this.refLockWaitTime = cfg.getRefLockWaitTimeConsumer();
		this.indexEventConsumer = cfg.getIndexEventConsumer();
	}

	@Override
	public DfsBlockCacheStats getDfsBlockCacheStats() {
		return dfsBlockCacheStats;
	}

	@Override
	public boolean hasBlock0(DfsStreamKey key) {
		HashEntry e1 = table.get(slot(key, 0));
		DfsBlock v = scan(e1, key, 0);
		return v != null && v.contains(key, 0);
	}

	@Override
	public DfsBlock getOrLoad(BlockBasedFile file, long position, DfsReader ctx,
			ReadableChannelSupplier fileChannel) throws IOException {
		final long requestedPosition = position;
		position = file.alignToBlock(position);

		DfsStreamKey key = file.key;
		int slot = slot(key, position);
		HashEntry e1 = table.get(slot);
		DfsBlock v = scan(e1, key, position);
		if (v != null && v.contains(key, requestedPosition)) {
			ctx.stats.blockCacheHit++;
			dfsBlockCacheStats.incrementHit(key);
			return v;
		}

		reserveSpace(blockSize, key);
		ReentrantLock regionLock = lockFor(key, position);
		regionLock.lock();
		try {
			HashEntry e2 = table.get(slot);
			if (e2 != e1) {
				v = scan(e2, key, position);
				if (v != null) {
					ctx.stats.blockCacheHit++;
					dfsBlockCacheStats.incrementHit(key);
					creditSpace(blockSize, key);
					return v;
				}
			}

			dfsBlockCacheStats.incrementMiss(key);
			boolean credit = true;
			try {
				v = file.readOneBlock(position, ctx, fileChannel.get());
				credit = false;
			} finally {
				if (credit) {
					creditSpace(blockSize, key);
				}
			}
			if (position != v.start) {
				// The file discovered its blockSize and adjusted.
				position = v.start;
				slot = slot(key, position);
				e2 = table.get(slot);
			}

			Ref<DfsBlock> ref = new Ref<>(key, position, v.size(), v);
			ref.markHotter();
			for (;;) {
				HashEntry n = new HashEntry(HashEntry.clean(e2), ref);
				if (table.compareAndSet(slot, e2, n)) {
					break;
				}
				e2 = table.get(slot);
			}
			addToClock(ref, blockSize - v.size());
		} finally {
			regionLock.unlock();
		}

		// If the block size changed from the default, it is possible the
		// block
		// that was loaded is the wrong block for the requested position.
		if (v.contains(file.key, requestedPosition)) {
			return v;
		}
		return getOrLoad(file, requestedPosition, ctx, fileChannel);
	}

	@Override
	public <T> Ref<T> getOrLoadRef(DfsStreamKey key, long position,
			RefLoader<T> loader) throws IOException {
		long start = System.nanoTime();
		int slot = slot(key, position);
		HashEntry e1 = table.get(slot);
		Ref<T> ref = scanRef(e1, key, position);
		if (ref != null) {
			dfsBlockCacheStats.incrementHit(key);
			reportIndexRequested(ref, true /* cacheHit= */, start);
			return ref;
		}

		ReentrantLock regionLock = lockForRef(key);
		long lockStart = System.currentTimeMillis();
		regionLock.lock();
		try {
			HashEntry e2 = table.get(slot);
			if (e2 != e1) {
				ref = scanRef(e2, key, position);
				if (ref != null) {
					dfsBlockCacheStats.incrementHit(key);
					reportIndexRequested(ref, true /* cacheHit= */, start);
					return ref;
				}
			}

			if (refLockWaitTime != null) {
				refLockWaitTime.accept(
						Long.valueOf(System.currentTimeMillis() - lockStart));
			}
			dfsBlockCacheStats.incrementMiss(key);
			ref = loader.load();
			ref.markHotter();
			// Reserve after loading to get the size of the object
			reserveSpace(ref.size, key);
			for (;;) {
				HashEntry n = new HashEntry(HashEntry.clean(e2), ref);
				if (table.compareAndSet(slot, e2, n)) {
					break;
				}
				e2 = table.get(slot);
			}
			addToClock(ref, 0);
		} finally {
			regionLock.unlock();
		}
		reportIndexRequested(ref, /* cacheHit= */ false, start);
		return ref;
	}

	@Override
	public void put(DfsBlock v) {
		put(v.stream, v.start, v.size(), v);
	}

	@Override
	public <T> Ref<T> put(DfsStreamKey key, long pos, long size, T v) {
		int slot = slot(key, pos);
		HashEntry e1 = table.get(slot);
		Ref<T> ref = scanRef(e1, key, pos);
		if (ref != null) {
			return ref;
		}

		reserveSpace(size, key);
		ReentrantLock regionLock = lockFor(key, pos);
		regionLock.lock();
		try {
			HashEntry e2 = table.get(slot);
			if (e2 != e1) {
				ref = scanRef(e2, key, pos);
				if (ref != null) {
					creditSpace(size, key);
					return ref;
				}
			}

			ref = new Ref<>(key, pos, size, v);
			ref.markHotter();
			for (;;) {
				HashEntry n = new HashEntry(HashEntry.clean(e2), ref);
				if (table.compareAndSet(slot, e2, n)) {
					break;
				}
				e2 = table.get(slot);
			}
			addToClock(ref, 0);
		} finally {
			regionLock.unlock();
		}
		return ref;
	}

	@Override
	public <T> Ref<T> putRef(DfsStreamKey key, long size, T v) {
		return put(key, 0, size, v);
	}

	@Override
	public boolean contains(DfsStreamKey key, long position) {
		return scan(table.get(slot(key, position)), key, position) != null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T get(DfsStreamKey key, long position) {
		T val = (T) scan(table.get(slot(key, position)), key, position);
		if (val == null) {
			dfsBlockCacheStats.incrementMiss(key);
		} else {
			dfsBlockCacheStats.incrementHit(key);
		}
		return val;
	}

	private int slot(DfsStreamKey key, long position) {
		return (hash.hash(key.hash, position) >>> 1) % tableSize;
	}

	@SuppressWarnings("unchecked")
	private void reserveSpace(long reserve, DfsStreamKey key) {
		clockLock.lock();
		try {
			long live = LongStream.of(dfsBlockCacheStats.getCurrentSize()).sum()
					+ reserve;
			if (maxBytes < live) {
				Ref prev = clockHand;
				Ref hand = clockHand.next;
				do {
					if (hand.isHot()) {
						// Value was recently touched. Cache is still hot so
						// give it another chance, but cool it down a bit.
						hand.markColder();
						prev = hand;
						hand = hand.next;
						continue;
					} else if (prev == hand) {
						break;
					}

					// No recent access since last scan, kill
					// value and remove from clock.
					Ref dead = hand;
					hand = hand.next;
					prev.next = hand;
					dead.next = null;
					dead.value = null;
					live -= dead.size;
					dfsBlockCacheStats.addToLiveBytes(dead.key, -dead.size);
					dfsBlockCacheStats.incrementEvict(dead.key);
					reportIndexEvicted(dead);
				} while (maxBytes < live);
				clockHand = prev;
			}
			dfsBlockCacheStats.addToLiveBytes(key, reserve);
		} finally {
			clockLock.unlock();
		}
	}

	private void creditSpace(long credit, DfsStreamKey key) {
		clockLock.lock();
		try {
			dfsBlockCacheStats.addToLiveBytes(key, -credit);
		} finally {
			clockLock.unlock();
		}
	}

	@SuppressWarnings("unchecked")
	private void addToClock(Ref ref, long credit) {
		clockLock.lock();
		try {
			if (credit != 0) {
				dfsBlockCacheStats.addToLiveBytes(ref.key, -credit);
			}
			Ref ptr = clockHand;
			ref.next = ptr.next;
			ptr.next = ref;
			clockHand = ref;
		} finally {
			clockLock.unlock();
		}
	}

	private <T> T scan(HashEntry n, DfsStreamKey key, long position) {
		Ref<T> r = scanRef(n, key, position);
		return r != null ? r.get() : null;
	}

	@SuppressWarnings("unchecked")
	private <T> Ref<T> scanRef(HashEntry n, DfsStreamKey key, long position) {
		for (; n != null; n = n.next) {
			Ref<T> r = n.ref;
			if (r.position == position && r.key.equals(key)) {
				return r.get() != null ? r : null;
			}
		}
		return null;
	}

	private ReentrantLock lockFor(DfsStreamKey key, long position) {
		return loadLocks[(hash.hash(key.hash, position) >>> 1)
				% loadLocks.length];
	}

	private ReentrantLock lockForRef(DfsStreamKey key) {
		int slot = (key.hash >>> 1) % refLocks[key.packExtPos].length;
		return refLocks[key.packExtPos][slot];
	}

	private void reportIndexRequested(Ref<?> ref, boolean cacheHit,
			long start) {
		if (indexEventConsumer == null || !isIndexExtPos(ref.key.packExtPos)) {
			return;
		}
		EvictKey evictKey = createEvictKey(ref);
		Long prevEvictedTime = indexEvictionMap.get(evictKey);
		long now = System.nanoTime();
		long sinceLastEvictionNanos = prevEvictedTime == null ? 0L
				: now - prevEvictedTime.longValue();
		indexEventConsumer.acceptRequestedEvent(ref.key.packExtPos, cacheHit,
				(now - start) / 1000L /* micros */, ref.size,
				Duration.ofNanos(sinceLastEvictionNanos));
	}

	private void reportIndexEvicted(Ref<?> dead) {
		if (indexEventConsumer == null
				|| !indexEventConsumer.shouldReportEvictedEvent()
				|| !isIndexExtPos(dead.key.packExtPos)) {
			return;
		}
		EvictKey evictKey = createEvictKey(dead);
		Long prevEvictedTime = indexEvictionMap.get(evictKey);
		long now = System.nanoTime();
		long sinceLastEvictionNanos = prevEvictedTime == null ? 0L
				: now - prevEvictedTime.longValue();
		indexEvictionMap.put(evictKey, Long.valueOf(now));
		indexEventConsumer.acceptEvictedEvent(dead.key.packExtPos, dead.size,
				dead.getTotalHitCount(),
				Duration.ofNanos(sinceLastEvictionNanos));
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

		private static HashEntry clean(HashEntry top) {
			while (top != null && top.ref.next == null) {
				top = top.next;
			}
			if (top == null) {
				return null;
			}
			HashEntry n = clean(top.next);
			return n == top.next ? top : new HashEntry(n, top.ref);
		}
	}

	private EvictKey createEvictKey(Ref<?> ref) {
		return new EvictKey(hash, ref);
	}

	private static boolean isIndexExtPos(int packExtPos) {
		return packExtPos == PackExt.INDEX.getPosition()
				|| packExtPos == PackExt.REVERSE_INDEX.getPosition()
				|| packExtPos == PackExt.BITMAP_INDEX.getPosition();
	}

	private static int tableSize(DfsBlockCacheConfig cfg) {
		final int wsz = cfg.getBlockSize();
		final long limit = cfg.getBlockLimit();
		if (wsz <= 0) {
			throw new IllegalArgumentException(
					JGitText.get().invalidWindowSize);
		}
		if (limit < wsz) {
			throw new IllegalArgumentException(
					JGitText.get().windowSizeMustBeLesserThanLimit);
		}
		return (int) Math.min(5 * (limit / wsz) / 2, Integer.MAX_VALUE);
	}

	private static final class Hash {
		/**
		 * As {@link #blockSize} is a power of 2, bits to shift for a /
		 * blockSize.
		 */
		private final int blockSizeShift;

		Hash(int blockSizeShift) {
			this.blockSizeShift = blockSizeShift;
		}

		int hash(int packHash, long off) {
			return packHash + (int) (off >>> blockSizeShift);
		}
	}

	private static final class EvictKey {
		/**
		 * Provides the hash function to be used for this key's hashCode method.
		 */
		private final Hash hash;

		private final int keyHash;

		private final int packExtPos;

		private final long position;

		EvictKey(Hash hash, Ref<?> ref) {
			this.hash = hash;
			keyHash = ref.key.hash;
			packExtPos = ref.key.packExtPos;
			position = ref.position;
		}

		@Override
		public boolean equals(Object object) {
			if (object instanceof EvictKey) {
				EvictKey other = (EvictKey) object;
				return keyHash == other.keyHash
						&& packExtPos == other.packExtPos
						&& position == other.position;
			}
			return false;
		}

		@Override
		public int hashCode() {
			return hash.hash(keyHash, position);
		}
	}
}
