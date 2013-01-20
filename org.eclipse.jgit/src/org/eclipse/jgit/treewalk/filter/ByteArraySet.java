/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2013, Robin Rosenberg
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

package org.eclipse.jgit.treewalk.filter;

import org.eclipse.jgit.util.RawParseUtils;

/**
 * Specialized map for byte arrays, interpreted as strings
 */
public class ByteArraySet {
	private static final int INITIAL_TABLE_SIZE = 2048;

	private int size;

	private int grow;

	private int mask;

	private byte[][] table;

	/**
	 * Create an empty map.
	 *
	 * @param capacity
	 */
	public ByteArraySet(int capacity) {
		initTable(1 << Integer.highestOneBit((capacity * 2) - 1));
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
	 * @param hash
	 * @param length
	 * @return the instance mapped to toFind, or null if no mapping exists.
	 */
	public byte[] get(final byte[] toFind, int length, int hash) {
		final int msk = mask;
		int i = hash & msk;
		final byte[][] tbl = table;
		byte[] obj;

		while ((obj = tbl[i]) != null) {
			if (equals(obj, toFind, length))
				return obj;
			i = (i + 1) & msk;
		}
		return null;
	}

	private static boolean equals(byte[] a, byte[] b, int length) {
		if (a.length < length || b.length < length)
			return false;
		for (int i = 0; i < length; ++i) {
			if (a[i] != b[i])
				return false;
		}
		return true;
	}

	/**
	 * Returns true if this map contains the specified object.
	 *
	 * @param toFind
	 *            object to find.
	 * @param hash
	 * @param length
	 * @return true if the mapping exists for this object; false otherwise.
	 */
	public boolean contains(final byte[] toFind, int length, int hash) {
		return get(toFind, length, hash) != null;
	}

	/**
	 * Store an object for future lookup.
	 * <p>
	 * Stores {@code newValue}, but only if there is not already an object for
	 * the same object name. Callers can tell if the value is new by checking
	 * the return value with reference equality:
	 *
	 * <pre>
	 * byte[] obj = ...;
	 * boolean wasNew = map.addIfAbsent(obj, length, hash) == obj;
	 * </pre>
	 *
	 * @param newValue
	 *            the object to store.
	 * @param length
	 *            part of newValue to store
	 * @param hash
	 *            preComputed hash code
	 * @return {@code newValue} if stored, or the prior value already stored and
	 *         that would have been returned had the caller used
	 *         {@code get(newValue)} first.
	 */
	public byte[] addIfAbsent(final byte[] newValue, int length, int hash) {
		final int msk = mask;
		int i = hash & msk;
		final byte[][] tbl = table;
		byte[] obj;

		while ((obj = tbl[i]) != null) {
			if (equals(obj, newValue, length))
				return obj;
			i = (i + 1) & msk;
		}

		byte[] valueToInsert = copyIfNotSameSize(newValue, length);
		if (++size == grow) {
			grow();
			insert(valueToInsert, hash);
		} else {
			tbl[i] = valueToInsert;
		}
		return valueToInsert;
	}

	private static byte[] copyIfNotSameSize(byte[] newValue, int length) {
		if (newValue.length == length)
			return newValue;
		byte[] ret = new byte[length];
		System.arraycopy(newValue, 0, ret, 0, length);
		return ret;
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

	private void insert(final byte[] newValue, int hash) {
		final int msk = mask;
		int j = hash & msk;
		final byte[][] tbl = table;
		while (tbl[j] != null)
			j = (j + 1) & msk;
		tbl[j] = newValue;
	}

	Hasher hasher = new Hasher(null, 0);

	private void grow() {
		final byte[][] oldTable = table;
		final int oldSize = table.length;

		initTable(oldSize << 1);
		for (int i = 0; i < oldSize; i++) {
			final byte[] obj = oldTable[i];
			if (obj != null) {
				hasher.init(obj, obj.length);
				insert(obj, hasher.hash());
			}
		}
	}

	private void initTable(int sz) {
		if (sz < 2)
			sz = 2;
		grow = sz >> 1;
		mask = sz - 1;
		table = new byte[sz][];
	}

	static class Hasher {
		int hash;

		int pos;

		byte[] data;

		private int length;

		Hasher(byte[] data, int length) {
			init(data, length);
		}

		void init(byte[] d, int l) {
			this.data = d;
			this.length = l;
			pos = 0;
			hash = 0;
		}

		int hash() {
			while (pos < length)
				hash = hash * 31 + data[pos++];
			return hash;
		}

		int nextHash() {
			for (;;) {
				hash = hash * 31 + data[pos];
				++pos;
				if (pos == length || data[pos] == '/')
					return hash;
			}
		}

		boolean hashNext() {
			return pos < length;
		}

		public int length() {
			return pos;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < pos; ++i) {
				sb.append((char) data[i]);
			}
			sb.append(" | "); //$NON-NLS-1$
			for (int i = pos; i < length; ++i)
				sb.append((char) data[i]);
			return sb.toString();
		}
	}

	byte[][] toArray() {
		byte[][] ret = new byte[size][];
		int i = 0;
		for (byte[] entry : table) {
			if (entry != null)
				ret[i++] = entry;
		}
		return ret;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append('[');
		for (byte[] b : table) {
			if (b == null)
				continue;
			if (sb.length() > 1)
				sb.append(" , "); //$NON-NLS-1$
			sb.append('"');
			sb.append(RawParseUtils.decode(b));
			sb.append('"');
		}
		sb.append(']');
		return sb.toString();
	}
}
