/*
 * Copyright (C) 2021, 2022 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util.io;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StreamCorruptedException;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.Base85;

/**
 * A stream that decodes git binary patch data on the fly.
 *
 * @since 5.12
 */
public class BinaryHunkInputStream extends InputStream {

	private final InputStream in;

	private int lineNumber;

	private byte[] buffer;

	private int pos = 0;

	/**
	 * Creates a new {@link BinaryHunkInputStream}.
	 *
	 * @param in
	 *            {@link InputStream} to read the base-85 encoded patch data
	 *            from
	 */
	public BinaryHunkInputStream(InputStream in) {
		this.in = in;
	}

	@Override
	public int read() throws IOException {
		if (pos < 0) {
			return -1;
		}
		if (buffer == null || pos == buffer.length) {
			fillBuffer();
		}
		if (pos >= 0) {
			return buffer[pos++] & 0xFF;
		}
		return -1;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return super.read(b, off, len);
	}

	@Override
	public void close() throws IOException {
		in.close();
		buffer = null;
	}

	private void fillBuffer() throws IOException {
		int length = in.read();
		if (length < 0) {
			pos = length;
			buffer = null;
			return;
		}
		lineNumber++;
		// Length is encoded with characters, A..Z for 1..26 and a..z for 27..52
		if ('A' <= length && length <= 'Z') {
			length = length - 'A' + 1;
		} else if ('a' <= length && length <= 'z') {
			length = length - 'a' + 27;
		} else {
			throw new StreamCorruptedException(MessageFormat.format(
					JGitText.get().binaryHunkInvalidLength,
					Integer.valueOf(lineNumber), Integer.toHexString(length)));
		}
		byte[] encoded = new byte[Base85.encodedLength(length)];
		for (int i = 0; i < encoded.length; i++) {
			int b = in.read();
			if (b < 0 || b == '\r' || b == '\n') {
				throw new EOFException(MessageFormat.format(
						JGitText.get().binaryHunkInvalidLength,
						Integer.valueOf(lineNumber)));
			}
			encoded[i] = (byte) b;
		}
		// Must be followed by a newline; tolerate EOF.
		int b = in.read();
		if (b == '\r') {
			// Be lenient and accept CR-LF, too.
			b = in.read();
		}
		if (b >= 0 && b != '\n') {
			throw new StreamCorruptedException(MessageFormat.format(
					JGitText.get().binaryHunkMissingNewline,
					Integer.valueOf(lineNumber)));
		}
		try {
			buffer = Base85.decode(encoded, length);
		} catch (IllegalArgumentException e) {
			StreamCorruptedException ex = new StreamCorruptedException(
					MessageFormat.format(JGitText.get().binaryHunkDecodeError,
							Integer.valueOf(lineNumber)));
			ex.initCause(e);
			throw ex;
		}
		pos = 0;
	}
}
