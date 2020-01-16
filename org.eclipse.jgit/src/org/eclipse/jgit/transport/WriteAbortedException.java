/*
 * Copyright (C) 2015, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.io.IOException;

/**
 * An exception to be thrown when the write operation is aborted.
 * <p>
 * That can be thrown inside
 * {@link org.eclipse.jgit.transport.ObjectCountCallback#setObjectCount(long)}.
 *
 * @since 4.1
 */
public class WriteAbortedException extends IOException {
	private static final long serialVersionUID = 1L;

	/**
	 * Construct a {@code WriteAbortedException}.
	 */
	public WriteAbortedException() {
	}

	/**
	 * Construct a {@code WriteAbortedException}.
	 *
	 * @param s message describing the issue
	 */
	public WriteAbortedException(String s) {
		super(s);
	}

	/**
	 * Construct a {@code WriteAbortedException}.
	 *
	 * @param s
	 *            message describing the issue
	 * @param why
	 *            a lower level implementation specific issue.
	 */
	public WriteAbortedException(String s, Throwable why) {
		super(s, why);
	}
}
