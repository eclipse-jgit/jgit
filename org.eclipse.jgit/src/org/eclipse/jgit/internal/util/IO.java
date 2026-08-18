/*
 * Copyright (C) 2026, NVIDIA Corporation.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.util;

import java.io.InterruptedIOException;

/**
 * Input/Output utilities
 */
public class IO {
	/**
	 * Check wether the current thread is interrupted and throw an
	 * InterruptedIOException if so.
	 *
	 * @throws java.io.InterruptedIOException
	 *             if the current thread is interrupted
	 */
	public static void throwIfInterrupted() throws InterruptedIOException {
		if (Thread.currentThread().isInterrupted()) {
			throw new InterruptedIOException();
		}
	}

	private IO() {
		// Don't create instances of a static only utility.
	}
}
