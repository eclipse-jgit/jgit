/*
 * Copyright (C) 2021 Simeon Andreev <simeon.danailov.andreev@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib.internal;

/**
 * A boolean value that can also have an unset state.
 */
public enum BooleanTriState {
	/**
	 * Value equivalent to {@code true}.
	 */
	TRUE,
	/**
	 * Value equivalent to {@code false}.
	 */
	FALSE,
	/**
	 * Value is not set.
	 */
	UNSET;
}
