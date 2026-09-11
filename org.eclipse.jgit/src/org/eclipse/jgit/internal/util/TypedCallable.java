/*
 * Copyright (c) 2026 Vector Informatik GmbH
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.util;

import java.util.concurrent.Callable;

/**
 * A Callable with a parameterized exception type
 *
 * @param <T>
 *            The result type of {@link #call()}
 * @param <E>
 *            The exception type thrown by {@link #call()}
 */
@FunctionalInterface
public interface TypedCallable<T, E extends Exception> extends Callable<T> {

	@Override
	T call() throws E;

}
