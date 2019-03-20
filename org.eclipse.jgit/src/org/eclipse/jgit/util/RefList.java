/*
 * Copyright (C) 2010, Google Inc.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BinaryOperator;
import java.util.stream.Collector;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefComparator;

/**
 * Specialized variant of an ArrayList to support a {@code RefDatabase}.
 * <p>
 * This list is a hybrid of a Map&lt;String,Ref&gt; and of a List&lt;Ref&gt;. It
 * tracks reference instances by name by keeping them sorted and performing
 * binary search to locate an entry. Lookup time is O(log N), but addition and
 * removal is O(N + log N) due to the list expansion or contraction costs.
 * <p>
 * This list type is copy-on-write. Mutation methods return a new copy of the
 * list, leaving {@code this} unmodified. As a result we cannot easily implement
 * the {@link java.util.List} interface contract.
 *
 * @param <T>
 *            the type of reference being stored in the collection.
 */
public class RefList<T extends Ref> implements Iterable<Ref> {
	private static final RefList<Ref> EMPTY = new RefList<>(new Ref[0], 0);

	/**
	 * Create an empty unmodifiable reference list.
	 *
	 * @return an empty unmodifiable reference list.
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Ref> RefList<T> emptyList() {
		return (RefList<T>) EMPTY;
	}

	final Ref[] list;

	final int cnt;

	RefList(Ref[] list, int cnt) {
		this.list = list;
		this.cnt = cnt;
	}

	/**
	 * Initialize this list to use the same backing array as another list.
	 *
	 * @param src
	 *            the source list.
	 */
	protected RefList(RefList<T> src) {
		this.list = src.list;
		this.cnt = src.cnt;
	}

	/** {@inheritDoc} */
	@Override
	public Iterator<Ref> iterator() {
		return new Iterator<Ref>() {
			private int idx;

			@Override
			public boolean hasNext() {
				return idx < cnt;
			}

			@Override
			public Ref next() {
				if (idx < cnt)
					return list[idx++];
				throw new NoSuchElementException();
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	/**
	 * Cast {@code this} as an immutable, standard {@link java.util.List}.
	 *
	 * @return {@code this} as an immutable, standard {@link java.util.List}.
	 */
	public final List<Ref> asList() {
		final List<Ref> r = Arrays.asList(list).subList(0, cnt);
		return Collections.unmodifiableList(r);
	}

	/**
	 * Get number of items in this list.
	 *
	 * @return number of items in this list.
	 */
	public final int size() {
		return cnt;
	}

	/**
	 * Get if this list is empty.
	 *
	 * @return true if the size of this list is 0.
	 */
	public final boolean isEmpty() {
		return cnt == 0;
	}

	/**
	 * Locate an entry by name.
	 *
	 * @param name
	 *            the name of the reference to find.
	 * @return the index the reference is at. If the entry is not present
	 *         returns a negative value. The insertion position for the given
	 *         name can be computed from {@code -(index + 1)}.
	 */
	public final int find(String name) {
		int high = cnt;
		if (high == 0)
			return -1;
		int low = 0;
		do {
			final int mid = (low + high) >>> 1;
			final int cmp = RefComparator.compareTo(list[mid], name);
			if (cmp < 0)
				low = mid + 1;
			else if (cmp == 0)
				return mid;
			else
				high = mid;
		} while (low < high);
		return -(low + 1);
	}

	/**
	 * Determine if a reference is present.
	 *
	 * @param name
	 *            name of the reference to find.
	 * @return true if the reference is present; false if it is not.
	 */
	public final boolean contains(String name) {
		return 0 <= find(name);
	}

	/**
	 * Get a reference object by name.
	 *
	 * @param name
	 *            the name of the reference.
	 * @return the reference object; null if it does not exist in this list.
	 */
	public final T get(String name) {
		int idx = find(name);
		return 0 <= idx ? get(idx) : null;
	}

	/**
	 * Get the reference at a particular index.
	 *
	 * @param idx
	 *            the index to obtain. Must be {@code 0 <= idx < size()}.
	 * @return the reference value, never null.
	 */
	@SuppressWarnings("unchecked")
	public final T get(int idx) {
		return (T) list[idx];
	}

	/**
	 * Obtain a builder initialized with the first {@code n} elements.
	 * <p>
	 * Copies the first {@code n} elements from this list into a new builder,
	 * which can be used by the caller to add additional elements.
	 *
	 * @param n
	 *            the number of elements to copy.
	 * @return a new builder with the first {@code n} elements already added.
	 */
	public final Builder<T> copy(int n) {
		Builder<T> r = new Builder<>(Math.max(16, n));
		r.addAll(list, 0, n);
		return r;
	}

	/**
	 * Obtain a new copy of the list after changing one element.
	 * <p>
	 * This list instance is not affected by the replacement. Because this
	 * method copies the entire list, it runs in O(N) time.
	 *
	 * @param idx
	 *            index of the element to change.
	 * @param ref
	 *            the new value, must not be null.
	 * @return copy of this list, after replacing {@code idx} with {@code ref} .
	 */
	public final RefList<T> set(int idx, T ref) {
		Ref[] newList = new Ref[cnt];
		System.arraycopy(list, 0, newList, 0, cnt);
		newList[idx] = ref;
		return new RefList<>(newList, cnt);
	}

	/**
	 * Add an item at a specific index.
	 * <p>
	 * This list instance is not affected by the addition. Because this method
	 * copies the entire list, it runs in O(N) time.
	 *
	 * @param idx
	 *            position to add the item at. If negative the method assumes it
	 *            was a direct return value from {@link #find(String)} and will
	 *            adjust it to the correct position.
	 * @param ref
	 *            the new reference to insert.
	 * @return copy of this list, after making space for and adding {@code ref}.
	 */
	public final RefList<T> add(int idx, T ref) {
		if (idx < 0)
			idx = -(idx + 1);

		Ref[] newList = new Ref[cnt + 1];
		if (0 < idx)
			System.arraycopy(list, 0, newList, 0, idx);
		newList[idx] = ref;
		if (idx < cnt)
			System.arraycopy(list, idx, newList, idx + 1, cnt - idx);
		return new RefList<>(newList, cnt + 1);
	}

	/**
	 * Remove an item at a specific index.
	 * <p>
	 * This list instance is not affected by the addition. Because this method
	 * copies the entire list, it runs in O(N) time.
	 *
	 * @param idx
	 *            position to remove the item from.
	 * @return copy of this list, after making removing the item at {@code idx}.
	 */
	public final RefList<T> remove(int idx) {
		if (cnt == 1)
			return emptyList();
		Ref[] newList = new Ref[cnt - 1];
		if (0 < idx)
			System.arraycopy(list, 0, newList, 0, idx);
		if (idx + 1 < cnt)
			System.arraycopy(list, idx + 1, newList, idx, cnt - (idx + 1));
		return new RefList<>(newList, cnt - 1);
	}

	/**
	 * Store a reference, adding or replacing as necessary.
	 * <p>
	 * This list instance is not affected by the store. The correct position is
	 * determined, and the item is added if missing, or replaced if existing.
	 * Because this method copies the entire list, it runs in O(N + log N) time.
	 *
	 * @param ref
	 *            the reference to store.
	 * @return copy of this list, after performing the addition or replacement.
	 */
	public final RefList<T> put(T ref) {
		int idx = find(ref.getName());
		if (0 <= idx)
			return set(idx, ref);
		return add(idx, ref);
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		StringBuilder r = new StringBuilder();
		r.append('[');
		if (cnt > 0) {
			r.append(list[0]);
			for (int i = 1; i < cnt; i++) {
				r.append(", "); //$NON-NLS-1$
				r.append(list[i]);
			}
		}
		r.append(']');
		return r.toString();
	}

	/**
	 * Create a {@link Collector} for {@link Ref}.
	 *
	 * @param mergeFunction
	 *            if specified the result will be sorted and deduped.
	 * @return {@link Collector} for {@link Ref}
	 * @since 5.4
	 */
	public static <T extends Ref> Collector<T, ?, RefList<T>> toRefList(
			@Nullable BinaryOperator<T> mergeFunction) {
		return Collector.of(
				() -> new Builder<>(),
				Builder<T>::add, (b1, b2) -> {
					Builder<T> b = new Builder<>();
					b.addAll(b1);
					b.addAll(b2);
					return b;
				}, (b) -> {
					if (mergeFunction != null) {
						b.sort();
						b.dedupe(mergeFunction);
					}
					return b.toRefList();
				});
	}

	/**
	 * Builder to facilitate fast construction of an immutable RefList.
	 *
	 * @param <T>
	 *            type of reference being stored.
	 */
	public static class Builder<T extends Ref> {
		private Ref[] list;

		private int size;

		/** Create an empty list ready for items to be added. */
		public Builder() {
			this(16);
		}

		/**
		 * Create an empty list with at least the specified capacity.
		 *
		 * @param capacity
		 *            the new capacity; if zero or negative, behavior is the same as
		 *            {@link #Builder()}.
		 */
		public Builder(int capacity) {
			list = new Ref[Math.max(capacity, 16)];
		}

		/** @return number of items in this builder's internal collection. */
		public int size() {
			return size;
		}

		/**
		 * Get the reference at a particular index.
		 *
		 * @param idx
		 *            the index to obtain. Must be {@code 0 <= idx < size()}.
		 * @return the reference value, never null.
		 */
		@SuppressWarnings("unchecked")
		public T get(int idx) {
			return (T) list[idx];
		}

		/**
		 * Remove an item at a specific index.
		 *
		 * @param idx
		 *            position to remove the item from.
		 */
		public void remove(int idx) {
			System.arraycopy(list, idx + 1, list, idx, size - (idx + 1));
			size--;
		}

		/**
		 * Add the reference to the end of the array.
		 * <p>
		 * References must be added in sort order, or the array must be sorted
		 * after additions are complete using {@link #sort()}.
		 *
		 * @param ref
		 */
		public void add(T ref) {
			if (list.length == size) {
				Ref[] n = new Ref[size * 2];
				System.arraycopy(list, 0, n, 0, size);
				list = n;
			}
			list[size++] = ref;
		}

		/**
		 * Add all items from another builder.
		 *
		 * @param other
		 * @since 5.4
		 */
		public void addAll(Builder other) {
			addAll(other.list, 0, other.size);
		}

		/**
		 * Add all items from a source array.
		 * <p>
		 * References must be added in sort order, or the array must be sorted
		 * after additions are complete using {@link #sort()}.
		 *
		 * @param src
		 *            the source array.
		 * @param off
		 *            position within {@code src} to start copying from.
		 * @param cnt
		 *            number of items to copy from {@code src}.
		 */
		public void addAll(Ref[] src, int off, int cnt) {
			if (list.length < size + cnt) {
				Ref[] n = new Ref[Math.max(size * 2, size + cnt)];
				System.arraycopy(list, 0, n, 0, size);
				list = n;
			}
			System.arraycopy(src, off, list, size, cnt);
			size += cnt;
		}

		/**
		 * Replace a single existing element.
		 *
		 * @param idx
		 *            index, must have already been added previously.
		 * @param ref
		 *            the new reference.
		 */
		public void set(int idx, T ref) {
			list[idx] = ref;
		}

		/** Sort the list's backing array in-place. */
		public void sort() {
			Arrays.sort(list, 0, size, RefComparator.INSTANCE);
		}

		/**
		 * Dedupe the refs in place. Must be called after {@link #sort}.
		 *
		 * @param mergeFunction
		 * @since 5.4
		 */
		@SuppressWarnings("unchecked")
		public void dedupe(BinaryOperator<T> mergeFunction) {
			if (size == 0) {
				return;
			}
			int lastElement = 0;
			for (int i = 1; i < size; i++) {
				if (RefComparator.INSTANCE.compare(list[lastElement],
						list[i]) == 0) {
					list[lastElement] = mergeFunction
							.apply((T) list[lastElement], (T) list[i]);
				} else {
					list[lastElement + 1] = list[i];
					lastElement++;
				}
			}
			size = lastElement + 1;
			Arrays.fill(list, size, list.length, null);
		}

		/** @return an unmodifiable list using this collection's backing array. */
		public RefList<T> toRefList() {
			return new RefList<>(list, size);
		}

		@Override
		public String toString() {
			return toRefList().toString();
		}
	}
}
