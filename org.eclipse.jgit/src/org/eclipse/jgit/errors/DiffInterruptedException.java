/*
 * Copyright (C) 2015 Ericsson and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

/**
 * Thrown when a thread executing a diff is interrupted
 *
 * @see org.eclipse.jgit.diff.MyersDiff
 * @since 4.0
 */
public class DiffInterruptedException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructor for DiffInterruptedException
	 *
	 * @param message
	 *            error message
	 * @param cause
	 *            a {@link java.lang.Throwable}
	 * @since 4.1
	 */
	public DiffInterruptedException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructor for DiffInterruptedException
	 *
	 * @param message
	 *            error message
	 * @since 4.1
	 */
	public DiffInterruptedException(String message) {
		super(message);
	}

	/**
	 * Indicates that the thread computing a diff was interrupted.
	 */
	public DiffInterruptedException() {
		super();
	}
}
