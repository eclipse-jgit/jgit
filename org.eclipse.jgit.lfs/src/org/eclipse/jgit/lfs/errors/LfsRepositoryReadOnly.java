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

import java.text.MessageFormat;

import org.eclipse.jgit.lfs.internal.LfsText;

/**
 * Thrown when the user has read, but not write access. Only applicable when the
 * operation in the request is "upload".
 *
 * @since 4.5
 */
public class LfsRepositoryReadOnly extends LfsException {
	private static final long serialVersionUID = 1L;

	/**
	 * <p>Constructor for LfsRepositoryReadOnly.</p>
	 *
	 * @param name
	 *            the repository name.
	 */
	public LfsRepositoryReadOnly(String name) {
		super(MessageFormat.format(LfsText.get().repositoryReadOnly, name));
	}
}
