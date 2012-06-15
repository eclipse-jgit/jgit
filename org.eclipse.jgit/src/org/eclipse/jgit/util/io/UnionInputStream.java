/*
 * Copyright (C) 2009, Google Inc.
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
import java.util.Iterator;
import java.util.LinkedList;

/**
 * An InputStream which reads from one or more InputStreams.
 * <p>
 * This stream may enter into an EOF state, returning -1 from any of the read
 * methods, and then later successfully read additional bytes if a new
 * InputStream is added after reaching EOF.
 * <p>
 * Currently this stream does not support the mark/reset APIs. If mark and later
 * reset functionality is needed the caller should wrap this stream with a
 * {@link java.io.BufferedInputStream}.
 * */
public class UnionInputStream extends InputStream {
	private static final InputStream EOF = new InputStream() {
		@Override
		public int read() throws IOException {
			return -1;
		}
	};

	private final LinkedList<InputStream> streams = new LinkedList<InputStream>();

	/** Create an empty InputStream that is currently at EOF state. */
	public UnionInputStream() {
		// Do nothing.
	}

	/**
	 * Create an InputStream that is a union of the individual streams.
	 * <p>
	 * As each stream reaches EOF, it will be automatically closed before bytes
	 * from the next stream are read.
	 *
	 * @param inputStreams
	 *            streams to be pushed onto this stream.
	 */
	public UnionInputStream(InputStream... inputStreams) {
		for (InputStream i : inputStreams)
			add(i);
	}

	private InputStream head() {
		return streams.isEmpty() ? EOF : streams.getFirst();
	}

	private void pop() throws IOException {
		if (!streams.isEmpty())
			streams.removeFirst().close();
	}

	/**
	 * Add the given InputStream onto the end of the stream queue.
	 * <p>
	 * When the stream reaches EOF it will be automatically closed.
	 *
	 * @param in
	 *            the stream to add; must not be null.
	 */
	public void add(final InputStream in) {
		streams.add(in);
	}

	/**
	 * Returns true if there are no more InputStreams in the stream queue.
	 * <p>
	 * If this method returns {@code true} then all read methods will signal EOF
	 * by returning -1, until another InputStream has been pushed into the queue
	 * with {@link #add(InputStream)}.
	 *
	 * @return true if there are no more streams to read from.
	 */
	public boolean isEmpty() {
		return streams.isEmpty();
	}

	@Override
	public int read() throws IOException {
		for (;;) {
			final InputStream in = head();
			final int r = in.read();
			if (0 <= r)
				return r;
			else if (in == EOF)
				return -1;
			else
				pop();
		}
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (len == 0)
			return 0;
		for (;;) {
			final InputStream in = head();
			final int n = in.read(b, off, len);
			if (0 < n)
				return n;
			else if (in == EOF)
				return -1;
			else
				pop();
		}
	}

	@Override
	public int available() throws IOException {
		return head().available();
	}

	@Override
	public long skip(long len) throws IOException {
		long cnt = 0;
		while (0 < len) {
			final InputStream in = head();
			final long n = in.skip(len);
			if (0 < n) {
				cnt += n;
				len -= n;

			} else if (in == EOF) {
				return cnt;

			} else {
				// Is this stream at EOF? We can't tell from skip alone.
				// Read one byte to test for EOF, discard it if we aren't
				// yet at EOF.
				//
				final int r = in.read();
				if (r < 0) {
					pop();
					if (0 < cnt)
						break;
				} else {
					cnt += 1;
					len -= 1;
				}
			}
		}
		return cnt;
	}

	@Override
	public void close() throws IOException {
		IOException err = null;

		for (Iterator<InputStream> i = streams.iterator(); i.hasNext();) {
			try {
				i.next().close();
			} catch (IOException closeError) {
				err = closeError;
			}
			i.remove();
		}

		if (err != null)
			throw err;
	}
}
