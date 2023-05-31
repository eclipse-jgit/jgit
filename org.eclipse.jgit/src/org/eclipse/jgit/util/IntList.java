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
	 * Create a list initialized with the values of the given range.
	 *
	 * @param start
	 *            the beginning of the range, inclusive
	 * @param end
	 *            the end of the range, exclusive
	 * @return the list initialized with the given range
	 * @since 6.6
	 */
	public static IntList filledWithRange(int start, int end) {
		IntList list = new IntList(end - start);
		for (int val = start; val < end; val++) {
			list.add(val);
		}
		return list;
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

	/**
	 * Sort the entries of the list in-place, according to the comparator.
	 *
	 * @param comparator
	 *            provides the comparison values for sorting the entries
	 * @since 6.6
	 */
	public void sort(IntComparator comparator) {
		quickSort(0, count - 1, comparator);
	}

	/**
	 * Quick sort has average time complexity of O(n log n) and O(log n) space
	 * complexity (for recursion on the stack).
	 * <p>
	 * Implementation based on https://www.baeldung.com/java-quicksort.
	 *
	 * @param begin
	 *            the index to begin partitioning at, inclusive
	 * @param end
	 *            the index to end partitioning at, inclusive
	 * @param comparator
	 *            provides the comparison values for sorting the entries
	 */
	private void quickSort(int begin, int end, IntComparator comparator) {
		if (begin < end) {
			int partitionIndex = partition(begin, end, comparator);

			quickSort(begin, partitionIndex - 1, comparator);
			quickSort(partitionIndex + 1, end, comparator);
		}
	}

	private int partition(int begin, int end, IntComparator comparator) {
		int pivot = entries[end];
		int writeSmallerIdx = (begin - 1);

		for (int findSmallerIdx = begin; findSmallerIdx < end; findSmallerIdx++) {
			if (comparator.compare(entries[findSmallerIdx], pivot) <= 0) {
				writeSmallerIdx++;

				int biggerVal = entries[writeSmallerIdx];
				entries[writeSmallerIdx] = entries[findSmallerIdx];
				entries[findSmallerIdx] = biggerVal;
			}
		}

		int pivotIdx = writeSmallerIdx + 1;
		entries[end] = entries[pivotIdx];
		entries[pivotIdx] = pivot;

		return pivotIdx;
	}

	private void grow() {
		final int[] n = new int[(entries.length + 16) * 3 / 2];
		System.arraycopy(entries, 0, n, 0, count);
		entries = n;
	}

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

	/**
	 * A comparator of primitive ints.
	 *
	 * @since 6.6
	 */
	public interface IntComparator {

		/**
		 * Compares the two int arguments for order.
		 *
		 * @param first
		 *            the first int to compare
		 * @param second
		 *            the second int to compare
		 * @return a negative number if first &lt; second, 0 if first == second, or
		 *         a positive number if first &gt; second
		 */
		int compare(int first, int second);
	}
}
