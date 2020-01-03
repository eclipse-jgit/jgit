/*
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2009, Johannes Schindelin <johannes.schindelin@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

/**
 * A more efficient List&lt;Integer&gt; using a primitive integer array.
 */
public class IntList {
	private int[] entries;

	private int count;

	/**
	 * Create an empty list with a default capacity.
	 */
	public IntList() {
		this(10);
	}

	/**
	 * Create an empty list with the specified capacity.
	 *
	 * @param capacity
	 *            number of entries the list can initially hold.
	 */
	public IntList(int capacity) {
		entries = new int[capacity];
	}

	/**
	 * Get number of entries in this list.
	 *
	 * @return number of entries in this list.
	 */
	public int size() {
		return count;
	}

	/**
	 * Check if an entry appears in this collection.
	 *
	 * @param value
	 *            the value to search for.
	 * @return true of {@code value} appears in this list.
	 * @since 4.9
	 */
	public boolean contains(int value) {
		for (int i = 0; i < count; i++)
			if (entries[i] == value)
				return true;
		return false;
	}

	/**
	 * Get the value at the specified index
	 *
	 * @param i
	 *            index to read, must be in the range [0, {@link #size()}).
	 * @return the number at the specified index
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *             the index outside the valid range
	 */
	public int get(int i) {
		if (count <= i)
			throw new ArrayIndexOutOfBoundsException(i);
		return entries[i];
	}

	/**
	 * Empty this list
	 */
	public void clear() {
		count = 0;
	}

	/**
	 * Add an entry to the end of the list.
	 *
	 * @param n
	 *            the number to add.
	 */
	public void add(int n) {
		if (count == entries.length)
			grow();
		entries[count++] = n;
	}

	/**
	 * Assign an entry in the list.
	 *
	 * @param index
	 *            index to set, must be in the range [0, {@link #size()}).
	 * @param n
	 *            value to store at the position.
	 */
	public void set(int index, int n) {
		if (count < index)
			throw new ArrayIndexOutOfBoundsException(index);
		else if (count == index)
			add(n);
		else
			entries[index] = n;
	}

	/**
	 * Pad the list with entries.
	 *
	 * @param toIndex
	 *            index position to stop filling at. 0 inserts no filler. 1
	 *            ensures the list has a size of 1, adding <code>val</code> if
	 *            the list is currently empty.
	 * @param val
	 *            value to insert into padded positions.
	 */
	public void fillTo(int toIndex, int val) {
		while (count < toIndex)
			add(val);
	}

	private void grow() {
		final int[] n = new int[(entries.length + 16) * 3 / 2];
		System.arraycopy(entries, 0, n, 0, count);
		entries = n;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		final StringBuilder r = new StringBuilder();
		r.append('[');
		for (int i = 0; i < count; i++) {
			if (i > 0)
				r.append(", "); //$NON-NLS-1$
			r.append(entries[i]);
		}
		r.append(']');
		return r.toString();
	}
}
