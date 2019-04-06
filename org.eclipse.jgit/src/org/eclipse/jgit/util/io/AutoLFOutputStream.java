/*
 * Copyright (C) 2015, Ivan Motsch <ivan.motsch@bsiag.com>
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

package org.eclipse.jgit.util.io;

import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.jgit.diff.RawText;

/**
 * An OutputStream that reduces CRLF to LF.
 *
 * Existing single CR are not changed to LF, but retained as is.
 *
 * A binary check on the first 8000 bytes is performed and in case of binary
 * files, canonicalization is turned off (for the complete file).
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
	 * <p>
	 * Constructor for AutoLFOutputStream.
	 * </p>
	 *
	 * @param out
	 *            an {@link java.io.OutputStream} object.
	 */
	public AutoLFOutputStream(OutputStream out) {
		this(out, true);
	}

	/**
	 * <p>
	 * Constructor for AutoLFOutputStream.
	 * </p>
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
		if (overflow < 0) {
			return;
		}
		final int off = startOff + startLen - overflow;
		final int len = overflow;
		if (len == 0) {
			return;
		}
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
