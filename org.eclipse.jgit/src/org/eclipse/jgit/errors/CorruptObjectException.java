/*
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2008, Jonas Fonseca <fonseca@diku.dk>
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2007, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Exception thrown when an object cannot be read from Git.
 */
public class CorruptObjectException extends IOException {
	private static final long serialVersionUID = 1L;

	private ObjectChecker.ErrorType errorType;

	/**
	 * Report a specific error condition discovered in an object.
	 *
	 * @param type
	 *            type of error
	 * @param id
	 *            identity of the bad object
	 * @param why
	 *            description of the error.
	 * @since 4.2
	 */
	public CorruptObjectException(ObjectChecker.ErrorType type, AnyObjectId id,
			String why) {
		super(MessageFormat.format(JGitText.get().objectIsCorrupt3,
				type.getMessageId(), id.name(), why));
		this.errorType = type;
	}

	/**
	 * Construct a CorruptObjectException for reporting a problem specified
	 * object id
	 *
	 * @param id
	 *            a {@link org.eclipse.jgit.lib.AnyObjectId}
	 * @param why
	 *            error message
	 */
	public CorruptObjectException(AnyObjectId id, String why) {
		super(MessageFormat.format(JGitText.get().objectIsCorrupt, id.name(), why));
	}

	/**
	 * Construct a CorruptObjectException for reporting a problem specified
	 * object id
	 *
	 * @param id
	 *            a {@link org.eclipse.jgit.lib.ObjectId}
	 * @param why
	 *            error message
	 */
	public CorruptObjectException(ObjectId id, String why) {
		super(MessageFormat.format(JGitText.get().objectIsCorrupt, id.name(), why));
	}

	/**
	 * Construct a CorruptObjectException for reporting a problem not associated
	 * with a specific object id.
	 *
	 * @param why
	 *            error message
	 */
	public CorruptObjectException(String why) {
		super(why);
	}

	/**
	 * Construct a CorruptObjectException for reporting a problem not associated
	 * with a specific object id.
	 *
	 * @param why
	 *            message describing the corruption.
	 * @param cause
	 *            optional root cause exception
	 * @since 3.4
	 */
	public CorruptObjectException(String why, Throwable cause) {
		super(why);
		initCause(cause);
	}

	/**
	 * Specific error condition identified by
	 * {@link org.eclipse.jgit.lib.ObjectChecker}.
	 *
	 * @return error condition or null.
	 * @since 4.2
	 */
	@Nullable
	public ObjectChecker.ErrorType getErrorType() {
		return errorType;
	}
}
