/*
 * Copyright (C) 2011, GitHub Inc. and others
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
 * An exception occurring when a file cannot be locked
 */
public class LockFailedException extends IOException {
	private static final long serialVersionUID = 1L;

	private File file;

	/**
	 * Constructor for LockFailedException
	 *
	 * @param file
	 *            file that could not be locked
	 * @param message
	 *            exception message
	 * @param cause
	 *            cause, for later retrieval by
	 *            {@link java.lang.Throwable#getCause()}
	 * @since 4.1
	 */
	public LockFailedException(File file, String message, Throwable cause) {
		super(message, cause);
		this.file = file;
	}

	/**
	 * Construct a CannotLockException for the given file and message
	 *
	 * @param file
	 *            file that could not be locked
	 * @param message
	 *            exception message
	 */
	public LockFailedException(File file, String message) {
		super(message);
		this.file = file;
	}

	/**
	 * Construct a CannotLockException for the given file
	 *
	 * @param file
	 *            file that could not be locked
	 */
	public LockFailedException(File file) {
		this(file, MessageFormat.format(JGitText.get().cannotLock, file));
	}

	/**
	 * Get the file that could not be locked
	 *
	 * @return file
	 */
	public File getFile() {
		return file;
	}
}
