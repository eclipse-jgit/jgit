/*
 * Copyright (C) 2026, Nvidia
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport.forwarder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Input stream that records bytes read from the delegate.
 *
 * @since 7.7
 */
public final class RecordingInputStream extends InputStream {
	final InputStream delegate;
	final ByteArrayOutputStream recorded = new ByteArrayOutputStream();

	/**
	 * @param delegate
	 *            stream to wrap
	 */
	RecordingInputStream(InputStream delegate) {
		this.delegate = delegate;
	}

	@Override
	public int read() throws IOException {
		int b = delegate.read();
		if (b >= 0) {
			recordByte((byte) b);
		}
		return b;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int read = delegate.read(b, off, len);
		if (read > 0) {
			recordBytes(b, off, read);
		}
		return read;
	}

	private void recordByte(byte b) {
		recorded.write(b);
	}

	private void recordBytes(byte[] b, int off, int len) {
		recorded.write(b, off, len);
	}

	byte[] getRecordedBytes() {
		return recorded.toByteArray();
	}
}
