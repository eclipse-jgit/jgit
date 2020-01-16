/*
 * Copyright (C) 2017 Ericsson and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

import java.io.IOException;

/**
 * Thrown when an operation was canceled
 *
 * @since 4.7
 */
public class CancelledException extends IOException {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructor for CancelledException
	 *
	 * @param message
	 *            error message
	 */
	public CancelledException(String message) {
		super(message);
	}
}
