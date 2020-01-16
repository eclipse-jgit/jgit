/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

import org.eclipse.jgit.internal.JGitText;

/**
 * Indicates a {@link org.eclipse.jgit.lib.Repository} has no working directory,
 * and is thus bare.
 */
public class NoWorkTreeException extends IllegalStateException {
	private static final long serialVersionUID = 1L;

	/**
	 * Creates an exception indicating there is no work tree for a repository.
	 */
	public NoWorkTreeException() {
		super(JGitText.get().bareRepositoryNoWorkdirAndIndex);
	}
}
