/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
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

package org.eclipse.jgit.lib;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Fast, efficient map specifically for {@link ObjectId} subclasses.
 * <p>
 * This map provides an efficient translation from any ObjectId instance to a
 * cached subclass of ObjectId that has the same value.
 * <p>
 * If object instances are stored in only one map, {@link ObjectIdOwnerMap} is a
 * more efficient implementation.
 *
 * @param <V>
 *            type of subclass of ObjectId that will be stored in the map.
 */
public class ObjectIdSubclassMap<V extends ObjectId>
		implements Iterable<V>, ObjectIdSet {
	private static final int INITIAL_TABLE_SIZE = 2048;

	int size;

	private int grow;

	private int mask;

	V[] table;

	/** Create an empty map. */
	public ObjectIdSubclassMap() {
		initTable(INITIAL_TABLE_SIZE);
	}

	/** Remove all entries from this map. */
	public void clear() {
		size = 0;
		initTable(INITIAL_TABLE_SIZE);
	}

	/**
	 * Lookup an existing mapping.
	 *
	 * @param toFind
	 *            the object identifier to find.
	 * @return the instance mapped to toFind, or null if no mapping exists.
	 */
	public V get(final AnyObjectId toFind) {
		final int msk = mask;
		int i = toFind.w1 & msk;
		final V[] tbl = table;
		V obj;

		while ((obj = tbl[i]) != null) {
			if (AnyObjectId.equals(obj, toFind))
				return obj;
			i = (i + 1) & msk;
		}
		return null;
	}

	/**
	 * Returns true if this map contains the specified object.
	 *
	 * @param toFind
	 *            object to find.
	 * @return true if the mapping exists for this object; false otherwise.
	 */
	@Override
	public boolean contains(final AnyObjectId toFind) {
		return get(toFind) != null;
	}

	/**
	 * Store an object for future lookup.
	 * <p>
	 * An existing mapping for <b>must not</b> be in this map. Callers must
	 * first call {@link #get(AnyObjectId)} to verify there is no current
	 * mapping prior to adding a new mapping, or use
	 * {@link #addIfAbsent(ObjectId)}.
	 *
	 * @param newValue
	 *            the object to store.
	 * @param <Q>
	 *            type of instance to store.
	 */
	public <Q extends V> void add(final Q newValue) {
		if (++size == grow)
			grow();
		insert(newValue);
	}

	/**
	 * Store an object for future lookup.
	 * <p>
	 * Stores {@code newValue}, but only if there is not already an object for
	 * the same object name. Callers can tell if the value is new by checking
	 * the return value with reference equality:
	 *
	 * <pre>
	 * V obj = ...;
	 * boolean wasNew = map.addIfAbsent(obj) == obj;
	 * </pre>
	 *
	 * @param newValue
	 *            the object to store.
	 * @return {@code newValue} if stored, or the prior value already stored and
	 *         that would have been returned had the caller used
	 *         {@code get(newValue)} first.
	 * @param <Q>
	 *            type of instance to store.
	 */
	public <Q extends V> V addIfAbsent(final Q newValue) {
		final int msk = mask;
		int i = newValue.w1 & msk;
		final V[] tbl = table;
		V obj;

		while ((obj = tbl[i]) != null) {
			if (AnyObjectId.equals(obj, newValue))
				return obj;
			i = (i + 1) & msk;
		}

		if (++size == grow) {
			grow();
			insert(newValue);
		} else {
			tbl[i] = newValue;
		}
		return newValue;
	}

	/**
	 * @return number of objects in map
	 */
	public int size() {
		return size;
	}

	/** @return true if {@link #size()} is 0. */
	public boolean isEmpty() {
		return size == 0;
	}

	@Override
	public Iterator<V> iterator() {
		return new Iterator<V>() {
			private int found;

			private int i;

			@Override
			public boolean hasNext() {
				return found < size;
			}

			@Override
			public V next() {
				while (i < table.length) {
					final V v = table[i++];
					if (v != null) {
						found++;
						return v;
					}
				}
				throw new NoSuchElementException();
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	private void insert(final V newValue) {
		final int msk = mask;
		int j = newValue.w1 & msk;
		final V[] tbl = table;
		while (tbl[j] != null)
			j = (j + 1) & msk;
		tbl[j] = newValue;
	}

	private void grow() {
		final V[] oldTable = table;
		final int oldSize = table.length;

		initTable(oldSize << 1);
		for (int i = 0; i < oldSize; i++) {
			final V obj = oldTable[i];
			if (obj != null)
				insert(obj);
		}
	}

	private void initTable(int sz) {
		grow = sz >> 1;
		mask = sz - 1;
		table = createArray(sz);
	}

	@SuppressWarnings("unchecked")
	private final V[] createArray(final int sz) {
		return (V[]) new ObjectId[sz];
	}
}
