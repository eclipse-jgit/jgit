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
 * Thrown when there is insufficient storage on the server.
 *
 * @since 4.5
 */
public class LfsInsufficientStorage extends LfsException {
	private static final long serialVersionUID = 1L;

	/**
	 * <p>Constructor for LfsInsufficientStorage.</p>
	 *
	 * @param message
	 *            error message, which may be shown to an end-user.
	 */
	public LfsInsufficientStorage(String message) {
		super(message);
	}
}
