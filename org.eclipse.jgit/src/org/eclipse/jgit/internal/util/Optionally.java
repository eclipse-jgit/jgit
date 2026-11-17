/*
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.util;

import java.lang.ref.SoftReference;

import org.eclipse.jgit.annotations.Nullable;

/**
 * Interface representing a reference to a potentially mutable optional object.
 *
 * @param <T>
 *            type of the mutable optional object
 *
 * @since 6.7
 */
public interface Optionally<T> {
	/**
	 * A permanently empty Optionally
	 *
	 * @param <T>
	 *            type of the mutable optional object
	 *
	 */
	public class Empty<T> implements Optionally<T> {
		@Override
		public void clear() {
			// empty
		}

		@Override
		public T get() {
			return null;
		}
	}

	/**
	 * A permanent(hard) reference to an object
	 *
	 * @param <T>
	 *            type of the mutable optional object
	 *
	 */
	public class Hard<T> implements Optionally<T> {
		/**
		 * The mutable optional object
		 */
		protected T element;

		/**
		 * @param element
		 *            the mutable optional object
		 */
		public Hard(T element) {
			this.element = element;
		}

		@Override
		public void clear() {
			element = null;
		}

		@Override
		public T get() {
			return element;
		}
	}

	/**
	 * A SoftReference Optionally
	 *
	 * @param <T>
	 *            type of the mutable optional object
	 *
	 */
	public class Soft<T> extends SoftReference<T> implements Optionally<T> {
		/**
		 * @param t
		 *            the mutable optional object
		 */
		public Soft(T t) {
			super(t);
		}
	}

	/**
	 * The empty Optionally
	 */
	public static final Optionally<?> EMPTY = new Empty<>();

	/**
	 * Get empty Optionally
	 *
	 * @param <T>
	 *            type of the empty Optionally
	 * @return the empty Optionally
	 */
	@SuppressWarnings("unchecked")
	public static <T> Optionally<T> empty() {
		return (Optionally<T>) EMPTY;
	}

	/**
	 * Clear the object
	 */
	void clear();

	/**
	 * Get the object. Returns {@code null} instead of an
	 * {@link java.util.Optional} so that callers on hot paths do not allocate.
	 *
	 * @return the object, or {@code null} if there is none or it was cleared
	 *         or garbage collected
	 * @since 6.10.2
	 */
	@Nullable
	T get();
}
