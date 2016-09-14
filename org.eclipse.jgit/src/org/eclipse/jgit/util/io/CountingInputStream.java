/*
 * Copyright (C) 2016, Google Inc.
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

/** Counts the number of bytes read. */
public class CountingInputStream extends InputStream {
	private final InputStream in;
	private long cnt;

	/** @param in the stream for the input */
	public CountingInputStream(InputStream in) {
		this.in = in;
	}

	/** @return current number of bytes read. */
	public long getCount() {
		return cnt;
	}

	@Override
	public int available() throws IOException {
		return in.available();
	}

	@Override
	public void close() throws IOException {
		in.close();
	}

	@Override
	public void mark(int readlimit) {
		in.mark(readlimit);
	}

	@Override
	public boolean markSupported() {
		return in.markSupported();
	}

	@Override
	public int read() throws IOException {
		int ret = in.read();
		if (ret != -1) {
			cnt++;
		}
		return ret;
	}

	@Override
	public int read(byte[] b) throws IOException {
		int ret = in.read(b);
		if (ret != -1) {
			cnt += ret;
		}
		return ret;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int ret = in.read(b, off, len);
		if (ret != -1) {
			cnt += ret;
		}
		return ret;
	}

	@Override
	public void reset() throws IOException {
		in.reset();
	}

	@Override
	public long skip(long n) throws IOException {
		long ret = in.skip(n);
		cnt += ret;
		return ret;
	}
}
