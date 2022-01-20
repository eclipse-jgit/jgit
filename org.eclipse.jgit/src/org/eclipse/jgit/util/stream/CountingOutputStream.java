/*
 * Copyright (C) 2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util.stream;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Counts the number of bytes written.
 */
public class CountingOutputStream extends OutputStream {
	private final OutputStream out;
	private long cnt;

	/**
	 * Initialize a new counting stream.
	 *
	 * @param out
	 *            stream to output all writes to.
	 */
	public CountingOutputStream(OutputStream out) {
		this.out = out;
	}

	/**
	 * Get current number of bytes written.
	 *
	 * @return current number of bytes written.
	 */
	public long getCount() {
		return cnt;
	}

	/** {@inheritDoc} */
	@Override
	public void write(int val) throws IOException {
		out.write(val);
		cnt++;
	}

	/** {@inheritDoc} */
	@Override
	public void write(byte[] buf, int off, int len) throws IOException {
		out.write(buf, off, len);
		cnt += len;
	}

	/** {@inheritDoc} */
	@Override
	public void flush() throws IOException {
		out.flush();
	}

	/** {@inheritDoc} */
	@Override
	public void close() throws IOException {
		out.close();
	}
}
