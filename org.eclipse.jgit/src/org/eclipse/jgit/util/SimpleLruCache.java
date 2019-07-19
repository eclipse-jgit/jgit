/*
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
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

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
public class SimpleLruCache<K, V> extends AbstractMap<K, V> {

	private volatile int maxSize;

	private ConcurrentHashMap<K, V> map;

	private ConcurrentLinkedQueue<K> queue;

	/**
	 * Construct a new cache
	 *
	 * @param maxSize
	 *            the maximum size of the cache
	 */
	public SimpleLruCache(int maxSize) {
		this.maxSize = maxSize;
		map = new ConcurrentHashMap<>(maxSize);
		queue = new ConcurrentLinkedQueue<>();
	}

	/**
	 * {@inheritDoc}
	 *
	 * @param key
	 *            key, must not be null!
	 * @param value
	 *            value, must not be null!
	 */
	@Override
	public V put(final K key, final V value) {
		queue.remove(key);
		queue.add(key);
		V oldValue = map.put(key, value);
		purge();
		return oldValue;
	}

	private void purge() {
		while (queue.size() > maxSize) {
			K lru = queue.poll();
			if (lru != null) {
				map.remove(lru);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * @param key
	 *            key, must not be null!
	 * @return the value associated to the given key or null
	 */
	@Override
	public V get(Object key) {
		if (map.containsKey(key)) {
			queue.remove(key);
			@SuppressWarnings("unchecked")
			K k = (K) key;
			queue.add(k);
		}
		return map.get(key);
	}

	/**
	 * {@inheritDoc}
	 *
	 * The returned entry set is unmodifiable
	 **/
	@Override
	public Set<Entry<K, V>> entrySet() {
		return Collections.unmodifiableSet(map.entrySet());
	}

	/**
	 * {@inheritDoc}
	 **/
	@Override
	public V remove(Object key) {
		if (map.containsKey(key)) {
			queue.remove(key);
		}
		return map.remove(key);
	}

	/**
	 * Configure the cache
	 *
	 * @param newMaxSize
	 *            the new maximum cache size, must be larger than 0
	 * @return the old maximum cache size
	 */
	public synchronized int configure(int newMaxSize) {
		if (newMaxSize <= 0) {
			throw new IllegalArgumentException(MessageFormat
					.format(JGitText.get().invalidCacheSize,
							Integer.valueOf(newMaxSize)));
		}
		int oldMaxSize = this.maxSize;
		this.maxSize = newMaxSize;
		purge();
		return oldMaxSize;
	}
}
