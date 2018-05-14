/*
 * Copyright (C) 2010, 2013 Google Inc.
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
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Input stream that copies data read to another output stream.
 *
 * This stream is primarily useful with a
 * {@link org.eclipse.jgit.util.TemporaryBuffer}, where any data read or skipped
 * by the caller is also duplicated into the temporary buffer. Later the
 * temporary buffer can then be used instead of the original source stream.
 *
 * During close this stream copies any remaining data from the source stream
 * into the destination stream.
 */
public class TeeInputStream extends InputStream {
	private byte[] skipBuffer;

	private InputStream src;

	private OutputStream dst;

	/**
	 * Initialize a tee input stream.
	 *
	 * @param src
	 *            source stream to consume.
	 * @param dst
	 *            destination to copy the source to as it is consumed. Typically
	 *            this is a {@link org.eclipse.jgit.util.TemporaryBuffer}.
	 */
	public TeeInputStream(InputStream src, OutputStream dst) {
		this.src = src;
		this.dst = dst;
	}

	/** {@inheritDoc} */
	@Override
	public int read() throws IOException {
		byte[] b = skipBuffer();
		int n = read(b, 0, 1);
		return n == 1 ? b[0] & 0xff : -1;
	}

	/** {@inheritDoc} */
	@Override
	public long skip(long count) throws IOException {
		long skipped = 0;
		long cnt = count;
		final byte[] b = skipBuffer();
		while (0 < cnt) {
			final int n = src.read(b, 0, (int) Math.min(b.length, cnt));
			if (n <= 0)
				break;
			dst.write(b, 0, n);
			skipped += n;
			cnt -= n;
		}
		return skipped;
	}

	/** {@inheritDoc} */
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (len == 0)
			return 0;

		int n = src.read(b, off, len);
		if (0 < n)
			dst.write(b, off, n);
		return n;
	}

	/** {@inheritDoc} */
	@Override
	public void close() throws IOException {
		byte[] b = skipBuffer();
		for (;;) {
			int n = src.read(b);
			if (n <= 0)
				break;
			dst.write(b, 0, n);
		}
		dst.close();
		src.close();
	}

	private byte[] skipBuffer() {
		if (skipBuffer == null)
			skipBuffer = new byte[2048];
		return skipBuffer;
	}
}
