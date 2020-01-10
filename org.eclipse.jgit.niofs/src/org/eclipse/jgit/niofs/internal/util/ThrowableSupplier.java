/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.util;

/**
 * Represents a supplier of results.
 * <p>
 * Borrowed from java 8 java.util.function, just added `throws` support.
 */
@FunctionalInterface
public interface ThrowableSupplier<T> {

	/**
	 * Gets a result.
	 * 
	 * @return a result
	 */
	T get() throws Throwable;
}
