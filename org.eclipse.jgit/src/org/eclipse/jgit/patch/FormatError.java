/*
 * Copyright (C) 2008, Google Inc.
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

package org.eclipse.jgit.patch;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Locale;

import org.eclipse.jgit.util.RawParseUtils;

/**
 * An error in a patch script
 */
public class FormatError {
	/** Classification of an error. */
	public static enum Severity {
		/** The error is unexpected, but can be worked around. */
		WARNING,

		/** The error indicates the script is severely flawed. */
		ERROR;
	}

	private final byte[] buf;

	private final int offset;

	private final Severity severity;

	private final String message;

	FormatError(final byte[] buffer, final int ptr, final Severity sev,
			final String msg) {
		buf = buffer;
		offset = ptr;
		severity = sev;
		message = msg;
	}

	/**
	 * Get the severity of the error.
	 *
	 * @return the severity of the error.
	 */
	public Severity getSeverity() {
		return severity;
	}

	/**
	 * Get a message describing the error.
	 *
	 * @return a message describing the error.
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Get the byte buffer holding the patch script.
	 *
	 * @return the byte buffer holding the patch script.
	 */
	public byte[] getBuffer() {
		return buf;
	}

	/**
	 * Get byte offset within {@link #getBuffer()} where the error is.
	 *
	 * @return byte offset within {@link #getBuffer()} where the error is.
	 */
	public int getOffset() {
		return offset;
	}

	/**
	 * Get line of the patch script the error appears on.
	 *
	 * @return line of the patch script the error appears on.
	 */
	public String getLineText() {
		final int eol = RawParseUtils.nextLF(buf, offset);
		return RawParseUtils.decode(UTF_8, buf, offset, eol);
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		final StringBuilder r = new StringBuilder();
		r.append(getSeverity().name().toLowerCase(Locale.ROOT));
		r.append(": at offset "); //$NON-NLS-1$
		r.append(getOffset());
		r.append(": "); //$NON-NLS-1$
		r.append(getMessage());
		r.append("\n"); //$NON-NLS-1$
		r.append("  in "); //$NON-NLS-1$
		r.append(getLineText());
		return r.toString();
	}
}
