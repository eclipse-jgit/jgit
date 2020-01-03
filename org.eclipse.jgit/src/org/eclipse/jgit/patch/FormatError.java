/*
 * Copyright (C) 2008, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
