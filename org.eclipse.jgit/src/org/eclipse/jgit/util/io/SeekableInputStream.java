/*
 * Copyright (C) 2010, Google Inc.
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

/** An input stream that can be repositioned to another location. */
public abstract class SeekableInputStream extends InputStream {
	/**
	 * Open a file for random access.
	 *
	 * @param path
	 *            path of the file to read.
	 * @return stream to read the file. Caller must close the stream.
	 * @throws FileNotFoundException
	 *             the file does not exist, or cannot be opened for reading.
	 */
	public static SeekableInputStream open(File path)
			throws FileNotFoundException {
		return new FileStream(path);
	}

	/**
	 * Open a byte array for random access.
	 *
	 * @param buf
	 *            the content to return from the stream.
	 * @return a stream to read {@code buf}.
	 */
	public static SeekableInputStream open(byte[] buf) {
		return new ByteStream(buf, 0, buf.length);
	}

	/**
	 * Position the stream at the specified offset.
	 *
	 * @param offset
	 *            offset for the next byte to read.
	 * @throws IOException
	 *             if the stream cannot be repositioned, if offset is negative,
	 *             or if offset is after the end of the stream.
	 */
	public abstract void seek(long offset) throws IOException;

	/**
	 * Get the size of the stream.
	 *
	 * @return size of the stream.
	 * @throws IOException
	 *             the stream cannot be accessed.
	 */
	public abstract long size() throws IOException;

	static class FileStream extends SeekableInputStream {
		private final RandomAccessFile in;

		private long sz;

		private long ptr;

		private long mark;

		FileStream(File path) throws FileNotFoundException {
			in = new RandomAccessFile(path, "r");
			sz = -1;
		}

		@Override
		public void seek(long offset) throws IOException {
			if (ptr != offset) {
				in.seek(offset);
				ptr = offset;
			}
		}

		@Override
		public long size() throws IOException {
			if (sz < 0)
				sz = in.length();
			return sz;
		}

		@Override
		public int read() throws IOException {
			final int b = in.read();
			if (0 <= b)
				ptr++;
			return b;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			final int n = in.read(b, off, len);
			if (0 < n)
				ptr += n;
			return n;
		}

		@Override
		public long skip(long n) throws IOException {
			long r = Math.min(n, size() - ptr);
			seek(ptr + r);
			return r;
		}

		@Override
		public int available() throws IOException {
			return 1;
		}

		@Override
		public void mark(int readlimit) {
			mark = ptr;
		}

		@Override
		public void reset() throws IOException {
			seek(mark);
		}

		@Override
		public boolean markSupported() {
			return true;
		}

		@Override
		public void close() throws IOException {
			in.close();
		}
	}

	static class ByteStream extends SeekableInputStream {
		private final byte[] buf;

		private final int start;

		private final int end;

		private int ptr;

		private int mark;

		ByteStream(byte[] buf, int start, int end) {
			this.buf = buf;
			this.start = start;
			this.end = end;
			this.ptr = start;
		}

		@Override
		public void seek(long offset) throws IOException {
			ptr = start + (int) offset;
		}

		@Override
		public long size() throws IOException {
			return end - start;
		}

		@Override
		public int read() throws IOException {
			if (ptr == end)
				return -1;
			return buf[ptr++] & 0xff;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (ptr == end)
				return -1;

			int n = Math.min(len, end - ptr);
			System.arraycopy(buf, ptr, b, off, n);
			ptr += n;
			return n;
		}

		@Override
		public long skip(long cnt) throws IOException {
			int n = (int) Math.min(cnt, end - ptr);
			ptr += n;
			return n;
		}

		@Override
		public int available() throws IOException {
			return end - ptr;
		}

		@Override
		public void mark(int readlimit) {
			mark = ptr;
		}

		@Override
		public void reset() throws IOException {
			ptr = mark;
		}

		@Override
		public boolean markSupported() {
			return true;
		}

		@Override
		public void close() throws IOException {
			// Do nothing.
		}
	}
}
