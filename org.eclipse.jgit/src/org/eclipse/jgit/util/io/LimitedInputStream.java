/*
 * Copyright (C) 2007 The Guava Authors
 * Copyright (C) 2014, Sasa Zivkov <sasa.zivkov@sap.com>, SAP AG
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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.internal.JGitText;

/**
 * Wraps a {@link java.io.InputStream}, limiting the number of bytes which can
 * be read.
 *
 * This class was copied and modifed from the Google Guava 16.0. Differently
 * from the original Guava code, when a caller tries to read from this stream
 * past the given limit and the wrapped stream hasn't yet reached its EOF this
 * class will call the limitExceeded method instead of returning EOF.
 *
 * @since 3.3
 */
public abstract class LimitedInputStream extends FilterInputStream {

	private long left;
	/** Max number of bytes to be read from the wrapped stream */
	protected final long limit;
	private long mark = -1;

	/**
	 * Create a new LimitedInputStream
	 *
	 * @param in an InputStream
	 * @param limit max number of bytes to read from the InputStream
	 */
	protected LimitedInputStream(InputStream in, long limit) {
		super(in);
		left = limit;
		this.limit = limit;
	}

	/** {@inheritDoc} */
	@Override
	public int available() throws IOException {
		return (int) Math.min(in.available(), left);
	}

	// it's okay to mark even if mark isn't supported, as reset won't work
	/** {@inheritDoc} */
	@Override
	public synchronized void mark(int readLimit) {
		in.mark(readLimit);
		mark = left;
	}

	/** {@inheritDoc} */
	@Override
	public int read() throws IOException {
		if (left == 0) {
			if (in.available() == 0) {
				return -1;
			}
			limitExceeded();
		}

		int result = in.read();
		if (result != -1) {
			--left;
		}
		return result;
	}

	/** {@inheritDoc} */
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (left == 0) {
			if (in.available() == 0) {
				return -1;
			}
			limitExceeded();
		}

		len = (int) Math.min(len, left);
		int result = in.read(b, off, len);
		if (result != -1) {
			left -= result;
		}
		return result;
	}

	/** {@inheritDoc} */
	@Override
	public synchronized void reset() throws IOException {
		if (!in.markSupported())
			throw new IOException(JGitText.get().unsupportedMark);

		if (mark == -1)
			throw new IOException(JGitText.get().unsetMark);

		in.reset();
		left = mark;
	}

	/** {@inheritDoc} */
	@Override
	public long skip(long n) throws IOException {
		n = Math.min(n, left);
		long skipped = in.skip(n);
		left -= skipped;
		return skipped;
	}

	/**
	 * Called when trying to read past the given {@link #limit} and the wrapped
	 * InputStream {@link #in} hasn't yet reached its EOF
	 *
	 * @throws java.io.IOException
	 *             subclasses can throw an {@link java.io.IOException} when the
	 *             limit is exceeded. The throws java.io.IOException will be
	 *             forwarded back to the caller of the read method which read
	 *             the stream past the limit.
	 */
	protected abstract void limitExceeded() throws IOException;
}
