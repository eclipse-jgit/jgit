/*
 * Copyright (C) 2011, Google Inc.
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

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Fast, efficient map for {@link org.eclipse.jgit.lib.ObjectId} subclasses in
 * only one map.
 * <p>
 * To use this map type, applications must have their entry value type extend
 * from {@link org.eclipse.jgit.lib.ObjectIdOwnerMap.Entry}, which itself
 * extends from ObjectId.
 * <p>
 * Object instances may only be stored in <b>ONE</b> ObjectIdOwnerMap. This
 * restriction exists because the map stores internal map state within each
 * object instance. If an instance is be placed in another ObjectIdOwnerMap it
 * could corrupt one or both map's internal state.
 * <p>
 * If an object instance must be in more than one map, applications may use
 * ObjectIdOwnerMap for one of the maps, and
 * {@link org.eclipse.jgit.lib.ObjectIdSubclassMap} for the other map(s). It is
 * encouraged to use ObjectIdOwnerMap for the map that is accessed most often,
 * as this implementation runs faster than the more general ObjectIdSubclassMap
 * implementation.
 *
 * @param <V>
 *            type of subclass of ObjectId that will be stored in the map.
 */
public class ObjectIdOwnerMap<V extends ObjectIdOwnerMap.Entry>
		implements Iterable<V>, ObjectIdSet {
	/** Size of the initial directory, will grow as necessary. */
	private static final int INITIAL_DIRECTORY = 1024;

	/** Number of bits in a segment's index. Segments are 2^11 in size. */
	private static final int SEGMENT_BITS = 11;

	private static final int SEGMENT_SHIFT = 32 - SEGMENT_BITS;

	/**
	 * Top level directory of the segments.
	 * <p>
	 * The low {@link #bits} of the SHA-1 are used to select the segment from
	 * this directory. Each segment is constant sized at 2^SEGMENT_BITS.
	 */
	V[][] directory;

	/** Total number of objects in this map. */
	int size;

	/** The map doubles in capacity when {@link #size} reaches this target. */
	private int grow;

	/** Number of low bits used to form the index into {@link #directory}. */
	int bits;

	/** Low bit mask to index into {@link #directory}, {@code 2^bits-1}. */
	private int mask;

	/**
	 * Create an empty map.
	 */
	@SuppressWarnings("unchecked")
	public ObjectIdOwnerMap() {
		bits = 0;
		mask = 0;
		grow = computeGrowAt(bits);

		directory = (V[][]) new Entry[INITIAL_DIRECTORY][];
		directory[0] = newSegment();
	}

	/**
	 * Remove all entries from this map.
	 */
	public void clear() {
		size = 0;

		for (V[] tbl : directory) {
			if (tbl == null)
				break;
			Arrays.fill(tbl, null);
		}
	}

	/**
	 * Lookup an existing mapping.
	 *
	 * @param toFind
	 *            the object identifier to find.
	 * @return the instance mapped to toFind, or null if no mapping exists.
	 */
	@SuppressWarnings("unchecked")
	public V get(AnyObjectId toFind) {
		int h = toFind.w1;
		V obj = directory[h & mask][h >>> SEGMENT_SHIFT];
		for (; obj != null; obj = (V) obj.next)
			if (equals(obj, toFind))
				return obj;
		return null;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Returns true if this map contains the specified object.
	 */
	@Override
	public boolean contains(AnyObjectId toFind) {
		return get(toFind) != null;
	}

	/**
	 * Store an object for future lookup.
	 * <p>
	 * An existing mapping for <b>must not</b> be in this map. Callers must
	 * first call {@link #get(AnyObjectId)} to verify there is no current
	 * mapping prior to adding a new mapping, or use {@link #addIfAbsent(Entry)}.
	 *
	 * @param newValue
	 *            the object to store.
	 */
	public <Q extends V> void add(Q newValue) {
		if (++size == grow)
			grow();

		int h = newValue.w1;
		V[] table = directory[h & mask];
		h >>>= SEGMENT_SHIFT;

		newValue.next = table[h];
		table[h] = newValue;
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
	 */
	@SuppressWarnings("unchecked")
	public <Q extends V> V addIfAbsent(Q newValue) {
		int h = newValue.w1;
		V[] table = directory[h & mask];
		h >>>= SEGMENT_SHIFT;

		for (V obj = table[h]; obj != null; obj = (V) obj.next)
			if (equals(obj, newValue))
				return obj;

		newValue.next = table[h];
		table[h] = newValue;

		if (++size == grow)
			grow();
		return newValue;
	}

	/**
	 * Get number of objects in this map.
	 *
	 * @return number of objects in this map.
	 */
	public int size() {
		return size;
	}

	/**
	 * Whether this map is empty
	 *
	 * @return true if {@link #size()} is 0.
	 */
	public boolean isEmpty() {
		return size == 0;
	}

	/** {@inheritDoc} */
	@Override
	public Iterator<V> iterator() {
		return new Iterator<V>() {
			private int found;
			private int dirIdx;
			private int tblIdx;
			private V next;

			@Override
			public boolean hasNext() {
				return found < size;
			}

			@Override
			public V next() {
				if (next != null)
					return found(next);

				for (;;) {
					V[] table = directory[dirIdx];
					if (tblIdx == table.length) {
						if (++dirIdx >= (1 << bits))
							throw new NoSuchElementException();
						table = directory[dirIdx];
						tblIdx = 0;
					}

					while (tblIdx < table.length) {
						V v = table[tblIdx++];
						if (v != null)
							return found(v);
					}
				}
			}

			@SuppressWarnings("unchecked")
			private V found(V v) {
				found++;
				next = (V) v.next;
				return v;
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	@SuppressWarnings("unchecked")
	private void grow() {
		final int oldDirLen = 1 << bits;
		final int s = 1 << bits;

		bits++;
		mask = (1 << bits) - 1;
		grow = computeGrowAt(bits);

		// Quadruple the directory if it needs to expand. Expanding the
		// directory is expensive because it generates garbage, so try
		// to avoid doing it often.
		//
		final int newDirLen = 1 << bits;
		if (directory.length < newDirLen) {
			V[][] newDir = (V[][]) new Entry[newDirLen << 1][];
			System.arraycopy(directory, 0, newDir, 0, oldDirLen);
			directory = newDir;
		}

		// For every bucket of every old segment, split the chain between
		// the old segment and the new segment's corresponding bucket. To
		// select between them use the lowest bit that was just added into
		// the mask above. This causes the table to double in capacity.
		//
		for (int dirIdx = 0; dirIdx < oldDirLen; dirIdx++) {
			final V[] oldTable = directory[dirIdx];
			final V[] newTable = newSegment();

			for (int i = 0; i < oldTable.length; i++) {
				V chain0 = null;
				V chain1 = null;
				V next;

				for (V obj = oldTable[i]; obj != null; obj = next) {
					next = (V) obj.next;

					if ((obj.w1 & s) == 0) {
						obj.next = chain0;
						chain0 = obj;
					} else {
						obj.next = chain1;
						chain1 = obj;
					}
				}

				oldTable[i] = chain0;
				newTable[i] = chain1;
			}

			directory[oldDirLen + dirIdx] = newTable;
		}
	}

	@SuppressWarnings("unchecked")
	private final V[] newSegment() {
		return (V[]) new Entry[1 << SEGMENT_BITS];
	}

	private static final int computeGrowAt(int bits) {
		return 1 << (bits + SEGMENT_BITS);
	}

	private static final boolean equals(AnyObjectId firstObjectId,
			AnyObjectId secondObjectId) {
		return firstObjectId.w2 == secondObjectId.w2
				&& firstObjectId.w3 == secondObjectId.w3
				&& firstObjectId.w4 == secondObjectId.w4
				&& firstObjectId.w5 == secondObjectId.w5
				&& firstObjectId.w1 == secondObjectId.w1;
	}

	/** Type of entry stored in the {@link ObjectIdOwnerMap}. */
	public static abstract class Entry extends ObjectId {
		transient Entry next;

		/**
		 * Initialize this entry with a specific ObjectId.
		 *
		 * @param id
		 *            the id the entry represents.
		 */
		public Entry(AnyObjectId id) {
			super(id);
		}
	}
}
