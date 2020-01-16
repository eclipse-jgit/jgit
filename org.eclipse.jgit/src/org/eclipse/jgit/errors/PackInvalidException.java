/*
 * Copyright (C) 2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;

/**
 * Thrown when a PackFile previously failed and is known to be unusable
 */
public class PackInvalidException extends IOException {
	private static final long serialVersionUID = 1L;

	/**
	 * Construct a pack invalid error.
	 *
	 * @param path
	 *            path of the invalid pack file.
	 * @deprecated Use {@link #PackInvalidException(File, Throwable)}.
	 */
	@Deprecated
	public PackInvalidException(File path) {
		this(path, null);
	}

	/**
	 * Construct a pack invalid error with cause.
	 *
	 * @param path
	 *            path of the invalid pack file.
	 * @param cause
	 *            cause of the pack file becoming invalid.
	 * @since 4.5.7
	 */
	public PackInvalidException(File path, Throwable cause) {
		this(path.getAbsolutePath(), cause);
	}

	/**
	 * Construct a pack invalid error.
	 *
	 * @param path
	 *            path of the invalid pack file.
	 * @deprecated Use {@link #PackInvalidException(String, Throwable)}.
	 */
	@Deprecated
	public PackInvalidException(String path) {
		this(path, null);
	}

	/**
	 * Construct a pack invalid error with cause.
	 *
	 * @param path
	 *            path of the invalid pack file.
	 * @param cause
	 *            cause of the pack file becoming invalid.
	 * @since 4.5.7
	 */
	public PackInvalidException(String path, Throwable cause) {
		super(MessageFormat.format(JGitText.get().packFileInvalid, path), cause);
	}
}
