/*
 * Copyright (C) 2016, David Pursehouse <david.pursehouse@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lfs.errors;

/**
 * Thrown when there is a validation error with one or more of the objects in
 * the request.
 *
 * @since 4.5
 */
public class LfsValidationError extends LfsException {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructor for LfsValidationError.
	 *
	 * @param message
	 *            error message, which may be shown to an end-user.
	 */
	public LfsValidationError(String message) {
		super(message);
	}
}
