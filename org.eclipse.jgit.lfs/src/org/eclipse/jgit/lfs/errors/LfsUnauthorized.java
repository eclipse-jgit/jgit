/*
 * Copyright (C) 2017, David Pursehouse <david.pursehouse@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lfs.errors;

import java.text.MessageFormat;

import org.eclipse.jgit.lfs.internal.LfsText;

/**
 * Thrown when authorization was refused for an LFS operation.
 *
 * @since 4.7
 */
public class LfsUnauthorized extends LfsException {
	private static final long serialVersionUID = 1L;

	/**
	 * <p>Constructor for LfsUnauthorized.</p>
	 *
	 * @param operation
	 *            the operation that was attempted.
	 * @param name
	 *            the repository name.
	 */
	public LfsUnauthorized(String operation, String name) {
		super(MessageFormat.format(LfsText.get().lfsUnauthorized, operation,
				name));
	}
}
