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

package org.eclipse.jgit.lib;

import java.io.IOException;
import java.io.InputStream;

/**
 * Stream of data coming from an object loaded by {@link org.eclipse.jgit.lib.ObjectLoader}.
 */
public abstract class ObjectStream extends InputStream {
	/**
	 * Get Git object type, see {@link Constants}.
	 *
	 * @return Git object type, see {@link Constants}.
	 */
	public abstract int getType();

	/**
	 * Get total size of object in bytes
	 *
	 * @return total size of object in bytes
	 */
	public abstract long getSize();

	/**
	 * Simple stream around the cached byte array created by a loader.
	 * <p>
	 * ObjectLoader implementations can use this stream type when the object's
	 * content is small enough to be accessed as a single byte array, but the
	 * application has still requested it in stream format.
	 */
	public static class SmallStream extends ObjectStream {
		private final int type;

		private final byte[] data;

		private int ptr;

		private int mark;

		/**
		 * Create the stream from an existing loader's cached bytes.
		 *
		 * @param loader
		 *            the loader.
		 */
		public SmallStream(ObjectLoader loader) {
			this(loader.getType(), loader.getCachedBytes());
		}

		/**
		 * Create the stream from an existing byte array and type.
		 *
		 *@param type
		 *            the type constant for the object.
		 *@param data
		 *            the fully inflated content of the object.
		 */
		public SmallStream(int type, byte[] data) {
			this.type = type;
			this.data = data;
		}

		@Override
		public int getType() {
			return type;
		}

		@Override
		public long getSize() {
			return data.length;
		}

		@Override
		public int available() {
			return data.length - ptr;
		}

		@Override
		public long skip(long n) {
			int s = (int) Math.min(available(), Math.max(0, n));
			ptr += s;
			return s;
		}

		@Override
		public int read() {
			if (ptr == data.length)
				return -1;
			return data[ptr++] & 0xff;
		}

		@Override
		public int read(byte[] b, int off, int len) {
			if (ptr == data.length)
				return -1;
			int n = Math.min(available(), len);
			System.arraycopy(data, ptr, b, off, n);
			ptr += n;
			return n;
		}

		@Override
		public boolean markSupported() {
			return true;
		}

		@Override
		public void mark(int readlimit) {
			mark = ptr;
		}

		@Override
		public void reset() {
			ptr = mark;
		}
	}

	/**
	 * Simple filter stream around another stream.
	 * <p>
	 * ObjectLoader implementations can use this stream type when the object's
	 * content is available from a standard InputStream.
	 */
	public static class Filter extends ObjectStream {
		private final int type;

		private final long size;

		private final InputStream in;

		/**
		 * Create a filter stream for an object.
		 *
		 * @param type
		 *            the type of the object.
		 * @param size
		 *            total size of the object, in bytes.
		 * @param in
		 *            stream the object's raw data is available from. This
		 *            stream should be buffered with some reasonable amount of
		 *            buffering.
		 */
		public Filter(int type, long size, InputStream in) {
			this.type = type;
			this.size = size;
			this.in = in;
		}

		@Override
		public int getType() {
			return type;
		}

		@Override
		public long getSize() {
			return size;
		}

		@Override
		public int available() throws IOException {
			return in.available();
		}

		@Override
		public long skip(long n) throws IOException {
			return in.skip(n);
		}

		@Override
		public int read() throws IOException {
			return in.read();
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			return in.read(b, off, len);
		}

		@Override
		public boolean markSupported() {
			return in.markSupported();
		}

		@Override
		public void mark(int readlimit) {
			in.mark(readlimit);
		}

		@Override
		public void reset() throws IOException {
			in.reset();
		}

		@Override
		public void close() throws IOException {
			in.close();
		}
	}
}
