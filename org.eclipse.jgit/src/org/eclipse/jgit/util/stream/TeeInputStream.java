/*
 * Copyright (C) 2010, 2013 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util.stream;

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
