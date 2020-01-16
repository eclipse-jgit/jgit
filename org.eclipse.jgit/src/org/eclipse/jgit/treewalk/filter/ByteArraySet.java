/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2013, Robin Rosenberg and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.treewalk.filter;

import org.eclipse.jgit.util.RawParseUtils;

/**
 * Specialized set for byte arrays, interpreted as strings for use in
 * {@link PathFilterGroup.Group}. Most methods assume the hash is already know
 * and therefore requires the caller to supply it beforehand. The implementation
 * is a loose derivative of ObjectIdSubclassMap.
 * <p>
 * The class is only intended for use by PathFilterGroup.
 * <p>
 * The arrays stored may not be changed after adding.
 */
class ByteArraySet {

	private int size;

	private int grow;

	private int mask;

	private byte[][] table;

	/**
	 * Create an empty set.
	 *
	 * @param capacity
	 */
	ByteArraySet(int capacity) {
		initTable(1 << Integer.highestOneBit((capacity * 2) - 1));
	}

	private byte[] get(byte[] toFind, int length, int hash) {
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

	private static boolean equals(byte[] storedObj, byte[] toFind, int length) {
		if (storedObj.length != length || toFind.length < length)
			return false;
		for (int i = 0; i < length; ++i) {
			if (storedObj[i] != toFind[i])
				return false;
		}
		return true;
	}

	/**
	 * Returns true if this set contains the specified array.
	 *
	 * @param toFind
	 *            array to find.
	 * @param length
	 *            The number of bytes in toFind that are used
	 * @param hash
	 *            pre-computed hash of toFind
	 * @return true if the mapping exists for this byte array; false otherwise.
	 */
	boolean contains(byte[] toFind, int length, int hash) {
		return get(toFind, length, hash) != null;
	}

	/**
	 * Store a byte array for future lookup.
	 * <p>
	 * Stores {@code newValue}, but only if it does not already exist in the
	 * set. Callers can tell if the value is new by checking the return value
	 * with reference equality:
	 *
	 * <pre>
	 * byte[] obj = ...;
	 * boolean wasNew = map.addIfAbsent(array, length, hash) == array;
	 * </pre>
	 *
	 * @param newValue
	 *            the array to store by reference if the length is the same as
	 *            the length parameter
	 * @param length
	 *            The number of bytes in newValue that are used
	 * @param hash
	 *            pre-computed hash of toFind
	 * @return {@code newValue} if stored, or the prior value already stored and
	 *         that would have been returned had the caller used
	 *         {@code get(newValue)} first.
	 */
	byte[] addIfAbsent(final byte[] newValue, int length, int hash) {
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
		} else
			tbl[i] = valueToInsert;
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
	 * @return number of arrays in the set
	 */
	int size() {
		return size;
	}

	/** @return true if {@link #size()} is 0. */
	boolean isEmpty() {
		return size == 0;
	}

	private void insert(byte[] newValue, int hash) {
		final int msk = mask;
		int j = hash & msk;
		final byte[][] tbl = table;
		while (tbl[j] != null)
			j = (j + 1) & msk;
		tbl[j] = newValue;
	}

	private Hasher hasher = new Hasher(null, 0);

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

	/** {@inheritDoc} */
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
			sb.append('(');
			sb.append(chainlength(b));
			sb.append(')');
		}
		sb.append(']');
		return sb.toString();
	}

	private int chainlength(byte[] b) {
		Hasher h = new Hasher(b, b.length);
		int hash = h.hash();
		final int msk = mask;
		int i = hash & msk;
		final byte[][] tbl = table;
		byte[] obj;

		int n = 0;
		while ((obj = tbl[i]) != null) {
			if (equals(obj, b, b.length))
				return n;
			i = (i + 1) & msk;
			++n;
		}
		return -1;
	}

	/**
	 * An incremental hash function.
	 */
	static class Hasher {
		private int hash;

		private int pos;

		private byte[] data;

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

		int getHash() {
			return hash;
		}

		boolean hasNext() {
			return pos < length;
		}

		public int length() {
			return pos;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < pos; ++i)
				sb.append((char) data[i]);
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

}
