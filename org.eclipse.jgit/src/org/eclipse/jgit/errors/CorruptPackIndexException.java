/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

import org.eclipse.jgit.annotations.Nullable;

/**
 * Exception thrown when encounters a corrupt pack index file.
 *
 * @since 4.9
 */
public class CorruptPackIndexException extends Exception {
	private static final long serialVersionUID = 1L;

	/** The error type of a corrupt index file. */
	public enum ErrorType {
		/** Offset does not match index in pack file. */
		MISMATCH_OFFSET,
		/** CRC does not match CRC of the object data in pack file. */
		MISMATCH_CRC,
		/** CRC is not present in index file. */
		MISSING_CRC,
		/** Object in pack is not present in index file. */
		MISSING_OBJ,
		/** Object in index file is not present in pack file. */
		UNKNOWN_OBJ,
	}

	private ErrorType errorType;

	/**
	 * Report a specific error condition discovered in an index file.
	 *
	 * @param message
	 *            the error message.
	 * @param errorType
	 *            the error type of corruption.
	 */
	public CorruptPackIndexException(String message, ErrorType errorType) {
		super(message);
		this.errorType = errorType;
	}

	/**
	 * Specific the reason of the corrupt index file.
	 *
	 * @return error condition or null.
	 */
	@Nullable
	public ErrorType getErrorType() {
		return errorType;
	}
}
