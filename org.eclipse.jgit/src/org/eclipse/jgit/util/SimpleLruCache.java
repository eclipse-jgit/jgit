/*
 * Copyright (C) 2019, Marc Strapetz <marc.strapetz@syntevo.com>
 * Copyright (C) 2019, Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.util;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.JGitText;

/**
 * Simple limited size cache based on ConcurrentHashMap purging entries in LRU
 * order when reaching size limit
 *
 * @param <K>
 *            the type of keys maintained by this cache
 * @param <V>
 *            the type of mapped values
 *
 * @since 5.1.9
 */
public class SimpleLruCache<K, V> {

	private static class Entry<K, V> {

		private final K key;

		private final V value;

		// pseudo clock timestamp of the last access to this entry
		private volatile long lastAccessed;

		private long lastAccessedSorting;

		Entry(K key, V value, long lastAccessed) {
			this.key = key;
			this.value = value;
			this.lastAccessed = lastAccessed;
		}

		void copyAccessTime() {
			lastAccessedSorting = lastAccessed;
		}

		@SuppressWarnings("nls")
		@Override
		public String toString() {
			return "Entry [lastAccessed=" + lastAccessed + ", key=" + key
					+ ", value=" + value + "]";
		}
	}

	private Lock lock = new ReentrantLock();

	private Map<K, Entry<K,V>> map = new ConcurrentHashMap<>();

	private volatile int maximumSize;

	private int purgeSize;

	// pseudo clock to implement LRU order of access to entries
	private volatile long time = 0L;

	private static void checkPurgeFactor(float purgeFactor) {
		if (purgeFactor <= 0 || purgeFactor >= 1) {
			throw new IllegalArgumentException(
					MessageFormat.format(JGitText.get().invalidPurgeFactor,
							Float.valueOf(purgeFactor)));
		}
	}

	private static int purgeSize(int maxSize, float purgeFactor) {
		return (int) ((1 - purgeFactor) * maxSize);
	}

	/**
	 * Create a new cache
	 *
	 * @param maxSize
	 *            maximum size of the cache, to reduce need for synchronization
	 *            this is not a hard limit. The real size of the cache could be
	 *            slightly above this maximum if multiple threads put new values
	 *            concurrently
	 * @param purgeFactor
	 *            when the size of the map reaches maxSize the oldest entries
	 *            will be purged to free up some space for new entries,
	 *            {@code purgeFactor} is the fraction of {@code maxSize} to
	 *            purge when this happens
	 */
	public SimpleLruCache(int maxSize, float purgeFactor) {
		checkPurgeFactor(purgeFactor);
		this.maximumSize = maxSize;
		this.purgeSize = purgeSize(maxSize, purgeFactor);
	}

	/**
	 * Returns the value to which the specified key is mapped, or {@code null}
	 * if this map contains no mapping for the key.
	 *
	 * <p>
	 * More formally, if this cache contains a mapping from a key {@code k} to a
	 * value {@code v} such that {@code key.equals(k)}, then this method returns
	 * {@code v}; otherwise it returns {@code null}. (There can be at most one
	 * such mapping.)
	 *
	 * @param key
	 *            the key
	 *
	 * @throws NullPointerException
	 *             if the specified key is null
	 *
	 * @return value mapped for this key, or {@code null} if no value is mapped
	 */
	@SuppressWarnings("NonAtomicVolatileUpdate")
	public V get(Object key) {
		Entry<K, V> entry = map.get(key);
		if (entry != null) {
			entry.lastAccessed = tick();
			return entry.value;
		}
		return null;
	}

	/**
	 * Maps the specified key to the specified value in this cache. Neither the
	 * key nor the value can be null.
	 *
	 * <p>
	 * The value can be retrieved by calling the {@code get} method with a key
	 * that is equal to the original key.
	 *
	 * @param key
	 *            key with which the specified value is to be associated
	 * @param value
	 *            value to be associated with the specified key
	 * @return the previous value associated with {@code key}, or {@code null}
	 *         if there was no mapping for {@code key}
	 * @throws NullPointerException
	 *             if the specified key or value is null
	 */
	@SuppressWarnings("NonAtomicVolatileUpdate")
	public V put(@NonNull K key, @NonNull V value) {
		map.put(key, new Entry<>(key, value, tick()));
		if (map.size() > maximumSize) {
			purge();
		}
		return value;
	}

	@SuppressWarnings("NonAtomicVolatileUpdate")
	private long tick() {
		return ++time;
	}

	/**
	 * Returns the current size of this cache
	 *
	 * @return the number of key-value mappings in this cache
	 */
	public int size() {
		return map.size();
	}

	/**
	 * Reconfigures the cache. If {@code maxSize} is reduced some entries will
	 * be purged.
	 *
	 * @param maxSize
	 *            maximum size of the cache
	 *
	 * @param purgeFactor
	 *            when the size of the map reaches maxSize the oldest entries
	 *            will be purged to free up some space for new entries,
	 *            {@code purgeFactor} is the fraction of {@code maxSize} to
	 *            purge when this happens
	 */
	public void configure(int maxSize, float purgeFactor) {
		lock.lock();
		try {
			checkPurgeFactor(purgeFactor);
			this.maximumSize = maxSize;
			this.purgeSize = purgeSize(maxSize, purgeFactor);
			if (map.size() >= maximumSize) {
				purge();
			}
		} finally {
			lock.unlock();
		}
	}

	private void purge() {
		// don't try to compete if another thread already has the lock
		if (lock.tryLock()) {
			try {
				List<Entry> entriesToPurge = new ArrayList<>(map.values());
				// copy access times to avoid other threads interfere with
				// sorting
				for (Entry e : entriesToPurge) {
					e.copyAccessTime();
				}
				Collections.sort(entriesToPurge,
						Comparator.comparingLong(o -> -o.lastAccessedSorting));
				for (int index = purgeSize; index < entriesToPurge
						.size(); index++) {
					map.remove(entriesToPurge.get(index).key);
				}
			} finally {
				lock.unlock();
			}
		}
	}
}
