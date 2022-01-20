/*
 * Copyright (C) 2009, 2013 Google Inc. and others
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
 */
public class UnionInputStream extends InputStream {
	private static final InputStream EOF = new InputStream() {
		@Override
		public int read() throws IOException {
			return -1;
		}
	};

	private final LinkedList<InputStream> streams = new LinkedList<>();

	/**
	 * Create an empty InputStream that is currently at EOF state.
	 */
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
	public void add(InputStream in) {
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

	/** {@inheritDoc} */
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

	/** {@inheritDoc} */
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

	/** {@inheritDoc} */
	@Override
	public int available() throws IOException {
		return head().available();
	}

	/** {@inheritDoc} */
	@Override
	public long skip(long count) throws IOException {
		long skipped = 0;
		long cnt = count;
		while (0 < cnt) {
			final InputStream in = head();
			final long n = in.skip(cnt);
			if (0 < n) {
				skipped += n;
				cnt -= n;

			} else if (in == EOF) {
				return skipped;

			} else {
				// Is this stream at EOF? We can't tell from skip alone.
				// Read one byte to test for EOF, discard it if we aren't
				// yet at EOF.
				//
				final int r = in.read();
				if (r < 0) {
					pop();
					if (0 < skipped)
						break;
				} else {
					skipped += 1;
					cnt -= 1;
				}
			}
		}
		return skipped;
	}

	/** {@inheritDoc} */
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
