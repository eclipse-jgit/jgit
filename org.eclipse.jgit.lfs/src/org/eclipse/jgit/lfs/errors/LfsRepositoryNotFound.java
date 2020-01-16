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
 * Thrown when the repository does not exist for the user.
 *
 * @since 4.5
 */
public class LfsRepositoryNotFound extends LfsException {
	private static final long serialVersionUID = 1L;

	/**
	 * <p>Constructor for LfsRepositoryNotFound.</p>
	 *
	 * @param name
	 *            the repository name.
	 */
	public LfsRepositoryNotFound(String name) {
		super(MessageFormat.format(LfsText.get().repositoryNotFound, name));
	}
}
