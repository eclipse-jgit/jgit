/*
 * Copyright (C) 2023, SAP SE and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.InvalidMarkException;
import java.util.Objects;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.JGitText;

/**
 * An {@link InputStream} backed by a {@link ByteBuffer}.
 *
 * @since 6.8
 */
public class ByteBufferInputStream extends InputStream {

	private ByteBuffer buf;

	/**
	 * Creates a {@link ByteBufferInputStream}
	 *
	 * @param buf
	 *            the ByteBuffer backing the stream
	 */
	public ByteBufferInputStream(@NonNull ByteBuffer buf) {
		this.buf = buf;
	}

	@Override
	public int read() throws IOException {
		nullCheck();
		if (buf.hasRemaining()) {
			return buf.get() & 0xFF;
		}
		return -1;
	}

	@Override
	public int read(byte[] b) throws IOException {
		nullCheck();
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		nullCheck();
		Objects.checkFromIndexSize(off, len, b.length);
		if (len == 0) {
			return 0;
		}
		int length = Math.min(buf.remaining(), len);
		if (length == 0) {
			return -1;
		}
		buf.get(b, off, length);
		return length;
	}

	@Override
	public byte[] readAllBytes() throws IOException {
		return readNBytes(buf.remaining());
	}

	@Override
	public byte[] readNBytes(int len) throws IOException {
		int l = Math.min(len, buf.remaining());
		byte[] b = new byte[l];
		read(b);
		return b;
	}

	@Override
	public int readNBytes(byte[] b, int off, int len) throws IOException {
		return read(b, off, len);
	}

	@Override
	public long skip(long n) throws IOException {
		nullCheck();
		if (n <= 0) {
			return 0;
		}
		// ByteBuffer index has type int
		int delta = n > Integer.MAX_VALUE ? buf.remaining()
				: Math.min((int) n, buf.remaining());
		buf.position(buf.position() + delta);
		return delta;
	}

	@Override
	public int available() throws IOException {
		nullCheck();
		return buf.remaining();
	}

	@Override
	public void close() {
		buf = null;
	}

	@Override
	public synchronized void mark(int readlimit) {
		buf.mark();
	}

	@Override
	public synchronized void reset() throws IOException {
		try {
			buf.reset();
		} catch (InvalidMarkException e) {
			throw new IOException(e);
		}
	}

	@Override
	public boolean markSupported() {
		return true;
	}

	private void nullCheck() throws IOException {
		if (buf == null) {
			throw new IOException(JGitText.get().inputStreamClosed);
		}
	}
}
