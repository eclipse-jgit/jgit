/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

/**
 * Indicates a {@link org.eclipse.jgit.pgm.TextBuiltin} implementation has
 * failed during execution.
 * <p>
 * Typically the stack trace for a Die exception is not shown to the user as it
 * may indicate a simple error condition that the end-user can fix on their own,
 * without needing a screen of Java stack frames.
 */
public class Die extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private boolean aborted;

	/**
	 * Construct a new message explaining what has gone wrong.
	 *
	 * @param why
	 *            the message to show to the end-user.
	 */
	public Die(String why) {
		super(why);
	}

	/**
	 * Construct a new message explaining what has gone wrong.
	 *
	 * @param why
	 *            the message to show to the end-user.
	 * @param cause
	 *            why the command has failed.
	 */
	public Die(String why, Throwable cause) {
		super(why, cause);
	}

	/**
	 * Construct a new exception reflecting the fact that the
	 * command execution has been aborted before running.
	 *
	 * @param aborted boolean indicating the fact the execution has been aborted
	 * @since 3.4
	 */
	public Die(boolean aborted) {
		this(aborted, null);
	}

	/**
	 * Construct a new exception reflecting the fact that the command execution
	 * has been aborted before running.
	 *
	 * @param aborted
	 *            boolean indicating the fact the execution has been aborted
	 * @param cause
	 *            can be null
	 * @since 4.2
	 */
	public Die(boolean aborted, Throwable cause) {
		super(cause != null ? cause.getMessage() : null, cause);
		this.aborted = aborted;
	}

	/**
	 * Check if this exception should cause the execution to be aborted.
	 *
	 * @return boolean indicating that the execution should be aborted
	 * @since 3.4
	 */
	public boolean isAborted() {
		return aborted;
	}
}
