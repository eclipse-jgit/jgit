/*
 * Copyright (C) 2023, Google LLC
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

/**
 * A view of a byte[] as a list of integers stored in 3 bytes.
 *
 * The ints are stored in big-endian ("network order"), so
 * byte[]{aa,bb,cc} becomes the int 0x00aabbcc
 */
final class UInt24Array {

	public static final UInt24Array EMPTY = new UInt24Array(
			new byte[0]);

	private static final int ENTRY_SZ = 3;

	private final byte[] data;

	private final int size;

	UInt24Array(byte[] data) {
		this.data = data;
		this.size = data.length / ENTRY_SZ;
	}

	boolean isEmpty() {
		return size == 0;
	}

	int size() {
		return size;
	}

	int get(int index) {
		if (index < 0 || index >= size) {
			throw new IndexOutOfBoundsException(index);
		}
		int offset = index * ENTRY_SZ;
		int e = data[offset] & 0xff;
		e <<= 8;
		e |= data[offset + 1] & 0xff;
		e <<= 8;
		e |= data[offset + 2] & 0xff;
		return e;
	}

	/**
	 * Search needle in the array.
	 *
	 * This assumes a sorted array.
	 *
	 * @param needle
	 *            It cannot be bigger than 0xffffff (max unsigned three bytes).
	 * @return position of the needle in the array, -1 if not found. Runtime
	 *         exception if the value is too big for 3 bytes.
	 */
	int binarySearch(int needle) {
		if ((needle & 0xff000000) != 0) {
			throw new IllegalArgumentException("Too big value for 3 bytes"); //$NON-NLS-1$
		}
		if (size == 0) {
			return -1;
		}
		int high = size;
		if (high == 0)
			return -1;
		int low = 0;
		do {
			int mid = (low + high) >>> 1;
			int cmp;
			cmp = Integer.compare(needle, get(mid));
			if (cmp < 0)
				high = mid;
			else if (cmp == 0) {
				return mid;
			} else
				low = mid + 1;
		} while (low < high);
		return -1;
	}

	int getLastValue() {
		return get(size - 1);
	}
}
