/*
 * Copyright (C) 2018, Konrad Windszus <konrad_w@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util;

import java.util.LinkedHashMap;

/**
 * Map with only up to n entries. If a new entry is added so that the map
 * contains more than those n entries the least-recently used entry is removed
 * from the map.
 *
 * @param <K>
 *            the type of keys maintained by this map
 * @param <V>
 *            the type of mapped values
 *
 * @since 5.4
 */
public class LRUMap<K, V> extends LinkedHashMap<K, V> {

	private static final long serialVersionUID = 4329609127403759486L;

	private final int limit;

	/**
	 * Constructs an empty map which may contain at most the given amount of
	 * entries.
	 *
	 * @param initialCapacity
	 *            the initial capacity
	 * @param limit
	 *            the number of entries the map should have at most
	 */
	public LRUMap(int initialCapacity, int limit) {
		super(initialCapacity, 0.75f, true);
		this.limit = limit;
	}

	@Override
	protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
		return size() > limit;
	}
}
