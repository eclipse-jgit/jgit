/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.manager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public class MemoizedFileSystemsSupplier<T> implements Supplier<T> {

	final Supplier<T> delegate;
	ConcurrentMap<Class<?>, T> map = new ConcurrentHashMap<>(1);

	private MemoizedFileSystemsSupplier(Supplier<T> delegate) {
		this.delegate = delegate;
	}

	@Override
	public T get() {

		T t = this.map.computeIfAbsent(MemoizedFileSystemsSupplier.class, k -> this.delegate.get());
		return t;
	}

	public static <T> Supplier<T> of(Supplier<T> provider) {
		return new MemoizedFileSystemsSupplier<>(provider);
	}
}