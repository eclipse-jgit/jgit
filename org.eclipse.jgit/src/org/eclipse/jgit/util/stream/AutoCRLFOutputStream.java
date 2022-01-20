/*
 * Copyright (C) 2011, 2013 Robin Rosenberg and others
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

import org.eclipse.jgit.diff.RawText;

/**
 * An OutputStream that expands LF to CRLF.
 *
 * Existing CRLF are not expanded to CRCRLF, but retained as is.
 *
 * A binary check on the first {@link RawText#getBufferSize()} bytes is
 * performed and in case of binary files, canonicalization is turned off (for
 * the complete file).
 */
public class AutoCRLFOutputStream extends OutputStream {

	private final OutputStream out;

	private int buf = -1;

	private byte[] binbuf = new byte[RawText.getBufferSize()];

	private byte[] onebytebuf = new byte[1];

	private int binbufcnt = 0;

	private boolean detectBinary;

	private boolean isBinary;

	/**
	 * <p>Constructor for AutoCRLFOutputStream.</p>
	 *
	 * @param out a {@link java.io.OutputStream} object.
	 */
	public AutoCRLFOutputStream(OutputStream out) {
		this(out, true);
	}

	/**
	 * <p>Constructor for AutoCRLFOutputStream.</p>
	 *
	 * @param out a {@link java.io.OutputStream} object.
	 * @param detectBinary
	 *            whether binaries should be detected
	 * @since 4.3
	 */
	public AutoCRLFOutputStream(OutputStream out, boolean detectBinary) {
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
		if (overflow > 0)
			write(b, b.length - overflow, overflow);
	}

	/** {@inheritDoc} */
	@Override
	public void write(byte[] b, int startOff, int startLen)
			throws IOException {
		final int overflow = buffer(b, startOff, startLen);
		if (overflow < 0)
			return;
		final int off = startOff + startLen - overflow;
		final int len = overflow;
		if (len == 0)
			return;
		int lastw = off;
		if (isBinary) {
			out.write(b, off, len);
			return;
		}
		for (int i = off; i < off + len; ++i) {
			final byte c = b[i];
			switch (c) {
			case '\r':
				buf = '\r';
				break;
			case '\n':
				if (buf != '\r') {
					if (lastw < i) {
						out.write(b, lastw, i - lastw);
					}
					out.write('\r');
					lastw = i;
				}
				buf = -1;
				break;
			default:
				buf = -1;
				break;
			}
		}
		if (lastw < off + len) {
			out.write(b, lastw, off + len - lastw);
		}
		if (b[off + len - 1] == '\r')
			buf = '\r';
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
			decideMode(false);
		}
		return remaining;
	}

	private void decideMode(boolean complete) throws IOException {
		if (detectBinary) {
			isBinary = RawText.isBinary(binbuf, binbufcnt, complete);
			if (!isBinary) {
				isBinary = RawText.isCrLfText(binbuf, binbufcnt, complete);
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
			decideMode(true);
		}
		buf = -1;
		out.flush();
	}

	/** {@inheritDoc} */
	@Override
	public void close() throws IOException {
		flush();
		out.close();
	}
}
