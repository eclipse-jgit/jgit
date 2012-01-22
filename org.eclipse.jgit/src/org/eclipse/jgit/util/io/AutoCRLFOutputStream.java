/*
 * Copyright (C) 2011, Robin Rosenberg
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

package org.eclipse.jgit.util.io;

import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.jgit.diff.RawText;

/**
 * An OutputStream that expands LF to CRLF.
 * <p>
 * Existing CRLF are not expanded to CRCRLF, but retained as is.
 */
public class AutoCRLFOutputStream extends OutputStream {

	private final OutputStream out;

	private int buf = -1;

	private byte[] binbuf = new byte[8000];

	private int binbufcnt = 0;

	private boolean isBinary;

	/**
	 * @param out
	 */
	public AutoCRLFOutputStream(OutputStream out) {
		this.out = out;
	}

	@Override
	public void write(int b) throws IOException {
		int overflow = buffer((byte) b);
		if (overflow >= 0)
			return;
		if (isBinary) {
			out.write(b);
			return;
		}
		if (b == '\n') {
			if (buf == '\r') {
				out.write('\n');
				buf = -1;
			} else if (buf == -1) {
				out.write('\r');
				out.write('\n');
				buf = -1;
			}
		} else if (b == '\r') {
			out.write(b);
			buf = '\r';
		} else {
			out.write(b);
			buf = -1;
		}
	}

	@Override
	public void write(byte[] b) throws IOException {
		int overflow = buffer(b, 0, b.length);
		if (overflow > 0)
			write(b, b.length - overflow, overflow);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		int overflow = buffer(b, off, len);
		if (overflow < 0)
			return;
		off = off + len - overflow;
		len = overflow;
		if (len == 0)
			return;
		int lastw = off;
		if (isBinary) {
			out.write(b, off, len);
			return;
		}
		for (int i = off; i < off + len; ++i) {
			byte c = b[i];
			if (c == '\r') {
				buf = '\r';
			} else if (c == '\n') {
				if (buf != '\r') {
					if (lastw < i) {
						out.write(b, lastw, i - lastw);
					}
					out.write('\r');
					lastw = i;
				}
				buf = -1;
			} else {
				buf = -1;
			}
		}
		if (lastw < off + len) {
			out.write(b, lastw, off + len - lastw);
		}
		if (b[off + len - 1] == '\r')
			buf = '\r';
	}

	private int buffer(byte b) throws IOException {
		if (binbufcnt > binbuf.length)
			return 1;
		binbuf[binbufcnt++] = b;
		if (binbufcnt == binbuf.length)
			decideMode();
		return 0;
	}

	private int buffer(byte[] b, int off, int len) throws IOException {
		if (binbufcnt > binbuf.length)
			return len;
		int copy = Math.min(binbuf.length - binbufcnt, len);
		System.arraycopy(b, off, binbuf, binbufcnt, copy);
		binbufcnt += copy;
		int remaining = len - copy;
		if (remaining > 0)
			decideMode();
		return remaining;
	}

	private void decideMode() throws IOException {
		isBinary = RawText.isBinary(binbuf, binbufcnt);
		int cachedLen = binbufcnt;
		binbufcnt = binbuf.length + 1; // full!
		write(binbuf, 0, cachedLen);
	}

	@Override
	public void flush() throws IOException {
		if (binbufcnt < binbuf.length)
			decideMode();
		buf = -1;
		out.flush();
	}

	@Override
	public void close() throws IOException {
		flush();
		out.close();
	}
}
