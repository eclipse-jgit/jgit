/*
 * Copyright (C) 2021 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util.stream;

import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.jgit.util.Base85;

/**
 * An {@link OutputStream} that encodes data for a git binary patch.
 *
 * @since 5.12
 */
public class BinaryHunkOutputStream extends OutputStream {

	private static final int MAX_BYTES = 52;

	private final OutputStream out;

	private final byte[] buffer = new byte[MAX_BYTES];

	private int pos;

	/**
	 * Creates a new {@link BinaryHunkOutputStream}.
	 *
	 * @param out
	 *            {@link OutputStream} to write the encoded data to
	 */
	public BinaryHunkOutputStream(OutputStream out) {
		this.out = out;
	}

	/**
	 * Flushes and closes this stream, and closes the underlying
	 * {@link OutputStream}.
	 */
	@Override
	public void close() throws IOException {
		flush();
		out.close();
	}

	/**
	 * Writes any buffered output as a binary patch line to the underlying
	 * {@link OutputStream} and flushes that stream, too.
	 */
	@Override
	public void flush() throws IOException {
		if (pos > 0) {
			encode(buffer, 0, pos);
			pos = 0;
		}
		out.flush();
	}

	@Override
	public void write(int b) throws IOException {
		buffer[pos++] = (byte) b;
		if (pos == buffer.length) {
			encode(buffer, 0, pos);
			pos = 0;
		}
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		if (len == 0) {
			return;
		}
		int toCopy = len;
		int in = off;
		if (pos > 0) {
			// Fill the buffer
			int chunk = Math.min(toCopy, buffer.length - pos);
			System.arraycopy(b, in, buffer, pos, chunk);
			in += chunk;
			pos += chunk;
			toCopy -= chunk;
			if (pos == buffer.length) {
				encode(buffer, 0, pos);
				pos = 0;
			}
			if (toCopy == 0) {
				return;
			}
		}
		while (toCopy >= MAX_BYTES) {
			encode(b, in, MAX_BYTES);
			toCopy -= MAX_BYTES;
			in += MAX_BYTES;
		}
		if (toCopy > 0) {
			System.arraycopy(b, in, buffer, 0, toCopy);
			pos = toCopy;
		}
	}

	private void encode(byte[] data, int off, int length) throws IOException {
		if (length <= 26) {
			out.write('A' + length - 1);
		} else {
			out.write('a' + length - 27);
		}
		out.write(Base85.encode(data, off, length));
		out.write('\n');
	}
}
