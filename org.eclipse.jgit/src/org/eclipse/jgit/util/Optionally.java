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

package org.eclipse.jgit.util;

import java.lang.ref.SoftReference;
import java.util.Optional;

public interface Optionally<T> {
	public class Empty<T> implements Optionally<T> {
		@Override
		public void clear() {
		}

		@Override
		public Optional<T> getOptional() {
			return Optional.empty();
		}
	}

	public class Hard<T> implements Optionally<T> {
		protected T element;

		public Hard(T element) {
			this.element = element;
		}

		@Override
		public void clear() {
			element = null;
		}

		@Override
		public Optional<T> getOptional() {
			return Optional.ofNullable(element);
		}
	}

	public class Soft<T> extends SoftReference<T> implements Optionally<T> {
		public Soft(T t) {
			super(t);
		}

		@Override
		public Optional<T> getOptional() {
			return Optional.ofNullable(get());
		}
	}

	public static final Optionally<?> EMPTY = new Empty<>();

	@SuppressWarnings("unchecked")
	public static <T> Optionally<T> empty() {
		return (Optionally<T>) EMPTY;
	}

	void clear();

	Optional<T> getOptional();
}
