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

import java.io.IOException;
import java.io.InputStream;

/**
 * Input stream that records bytes read from the delegate, bounded by the
 * maximum pkt-line size (65520 bytes) to avoid unbounded memory use.
 */
final class RecordingInputStream extends InputStream {
	/**
	 * Maximum pkt-line size per protocol (4-byte length + up to 65516 data
	 * bytes).
	 */
	private static final int MAX_PKT_LINE = 65520;

	private final InputStream delegate;

	private final byte[] recorded = new byte[MAX_PKT_LINE];

	private int recordedLen;

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
		if (b >= 0 && recordedLen < MAX_PKT_LINE) {
			recorded[recordedLen++] = (byte) b;
		}
		return b;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int read = delegate.read(b, off, len);
		if (read > 0 && recordedLen < MAX_PKT_LINE) {
			int toCopy = Math.min(read, MAX_PKT_LINE - recordedLen);
			System.arraycopy(b, off, recorded, recordedLen, toCopy);
			recordedLen += toCopy;
		}
		return read;
	}

	byte[] getRecordedBytes() {
		byte[] result = new byte[recordedLen];
		System.arraycopy(recorded, 0, result, 0, recordedLen);
		return result;
	}
}
