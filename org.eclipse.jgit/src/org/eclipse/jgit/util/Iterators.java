/*
 * Copyright (C) 2025, NVIDIA Corporation.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import java.util.Iterator;

/**
 * Utility class for Iterators
 *
 * @since 6.10.2
 */
public class Iterators {
	/**
	 * Create an iterator which traverses an array in reverse.
	 *
	 * @param array T[]
	 * @return Iterator<T>
	 */
	@SuppressWarnings("AvoidObjectArrays")
	public static <T> Iterator<T> reverseIterator(T[] array) {
		return new Iterator<>() {
			int index = array.length;

			@Override
			public boolean hasNext() {
				return index > 0;
			}

			@Override
			public T next() {
				return array[--index];
			}
		};
	}

	/**
	 * Make an iterable for easy use in modern for loops.
	 *
	 * @param iterator Iterator<T>
	 * @return Iterable<T>
	 */
	public static <T> Iterable<T> iterable(Iterator<T> iterator) {
		return new Iterable<>() {
			@Override
			public Iterator<T> iterator() {
				return iterator;
			}
		};
	}

	private Iterators() {
	}
}
