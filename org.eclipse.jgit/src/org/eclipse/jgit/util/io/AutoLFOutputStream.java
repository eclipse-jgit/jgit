/*
 * Copyright (C) 2015, Ivan Motsch <ivan.motsch@bsiag.com>
 * Copyright (C) 2020, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util.io;

import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.jgit.diff.RawText;

/**
 * An OutputStream that reduces CRLF to LF.
 * <p>
 * Existing single CR are not changed to LF, but retained as is.
 * </p>
 * <p>
 * A binary check on the first 8000 bytes is performed and in case of binary
 * files, canonicalization is turned off (for the complete file). If the binary
 * check determines that the input is not binary but text with CR/LF,
 * canonicalization is also turned off.
 * </p>
 *
 * @since 4.3
 */
public class AutoLFOutputStream extends OutputStream {

	static final int BUFFER_SIZE = 8000;

	private final OutputStream out;

	private int buf = -1;

	private byte[] binbuf = new byte[BUFFER_SIZE];

	private byte[] onebytebuf = new byte[1];

	private int binbufcnt = 0;

	private boolean detectBinary;

	private boolean isBinary;

	/**
	 * Constructor for AutoLFOutputStream.
	 *
	 * @param out
	 *            an {@link java.io.OutputStream} object.
	 */
	public AutoLFOutputStream(OutputStream out) {
		this(out, true);
	}

	/**
	 * Constructor for AutoLFOutputStream.
	 *
	 * @param out
	 *            an {@link java.io.OutputStream} object.
	 * @param detectBinary
	 *            whether binaries should be detected
	 */
	public AutoLFOutputStream(OutputStream out, boolean detectBinary) {
		this.out = out;
		this.detectBinary = detectBinary;
	}

	/** {@inheritDoc} */
	@Override
	public void write(int b) throws IOException {
		onebytebuf[0] = (byte) b;
		write(onebytebuf, 0, 1);
	}

	/** {@inheritDoc} */
	@Override
	public void write(byte[] b) throws IOException {
		int overflow = buffer(b, 0, b.length);
		if (overflow > 0) {
			write(b, b.length - overflow, overflow);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void write(byte[] b, int startOff, int startLen)
			throws IOException {
		final int overflow = buffer(b, startOff, startLen);
		if (overflow <= 0) {
			return;
		}
		final int off = startOff + startLen - overflow;
		final int len = overflow;
		int lastw = off;
		if (isBinary) {
			out.write(b, off, len);
			return;
		}
		for (int i = off; i < off + len; ++i) {
			final byte c = b[i];
			switch (c) {
			case '\r':
				// skip write r but backlog r
				if (lastw < i) {
					out.write(b, lastw, i - lastw);
				}
				lastw = i + 1;
				buf = '\r';
				break;
			case '\n':
				if (buf == '\r') {
					out.write('\n');
					lastw = i + 1;
					buf = -1;
				} else {
					if (lastw < i + 1) {
						out.write(b, lastw, i + 1 - lastw);
					}
					lastw = i + 1;
				}
				break;
			default:
				if (buf == '\r') {
					out.write('\r');
					lastw = i;
				}
				buf = -1;
				break;
			}
		}
		if (lastw < off + len) {
			out.write(b, lastw, off + len - lastw);
		}
	}

	private int buffer(byte[] b, int off, int len) throws IOException {
		if (binbufcnt > binbuf.length) {
			return len;
		}
		int copy = Math.min(binbuf.length - binbufcnt, len);
		System.arraycopy(b, off, binbuf, binbufcnt, copy);
		binbufcnt += copy;
		int remaining = len - copy;
		if (remaining > 0) {
			decideMode();
		}
		return remaining;
	}

	private void decideMode() throws IOException {
		if (detectBinary) {
			isBinary = RawText.isBinary(binbuf, binbufcnt);
			if (!isBinary) {
				isBinary = RawText.isCrLfText(binbuf, binbufcnt);
			}
			detectBinary = false;
		}
		int cachedLen = binbufcnt;
		binbufcnt = binbuf.length + 1; // full!
		write(binbuf, 0, cachedLen);
	}

	/** {@inheritDoc} */
	@Override
	public void flush() throws IOException {
		if (binbufcnt <= binbuf.length) {
			decideMode();
		}
		out.flush();
	}

	/** {@inheritDoc} */
	@Override
	public void close() throws IOException {
		flush();
		if (buf == '\r') {
			out.write(buf);
			buf = -1;
		}
		out.close();
	}
}
