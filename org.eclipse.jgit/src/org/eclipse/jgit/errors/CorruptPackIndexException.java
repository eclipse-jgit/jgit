/*
 * Copyright (C) 2017, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
