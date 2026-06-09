/*
 * Copyright (C) 2026, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd.proxy;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;

import org.apache.sshd.common.util.Readable;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.eclipse.jgit.internal.transport.sshd.SshdText;

/**
 * Accumulates bytes until the first occurrence of a given byte sequence.
 */
class Accumulator {

	private final int limit;

	private final byte[] endOfData;

	private final String errorMessage;

	private ByteArrayBuffer buf = new ByteArrayBuffer();

	private int split = -1;

	Accumulator(int limit, byte[] endOfData, String errorMessage) {
		if (endOfData == null || endOfData.length == 0) {
			throw new IllegalArgumentException("end marker must be non-empty"); //$NON-NLS-1$
		}
		if (limit < endOfData.length) {
			throw new IllegalArgumentException(
					"limit must be >= length of end marker"); //$NON-NLS-1$
		}
		this.endOfData = endOfData.clone();
		this.limit = limit;
		this.errorMessage = errorMessage;
	}

	/**
	 * Accumulates the given input and returns whether the end marker was found.
	 *
	 * @param input
	 *            to accumulate
	 * @return {@code true} if the data accumulated so far contains the end
	 *         marker, {@code false} otherwise
	 * @throws IOException
	 *             if the limit is exceeded
	 */
	boolean accumulate(Readable input) throws IOException {
		int lastCheck = buf.available();
		buf.putBuffer(input);
		int len = buf.available();
		if (split < 0 && len > lastCheck && len >= endOfData.length) {
			// Avoid re-checking already checked parts of the buffer. If the
			// last check didn't find the end marker up to lastCheck, we don't
			// have to start over again at the beginning: it suffices to double
			// check the last N-1 bytes (plus then the new bytes) if N is
			// endOfData.length.
			int from = (lastCheck >= endOfData.length)
					? lastCheck + 1 - endOfData.length
					: buf.rpos();
			split = indexOf(buf.array(), from, buf.rpos() + len - from,
					endOfData);
			if (split >= 0) {
				return true;
			}
		}
		if (len > limit) {
			if (split >= 0) {
				// Usage error; no translation
				throw new IOException(
						"Limit exceeded after marker was found at index " //$NON-NLS-1$
								+ split + "; limit = " + limit); //$NON-NLS-1$
			}
			throw new IOException(
					MessageFormat.format(SshdText.get().proxyHttpMessageTooLong,
							Integer.valueOf(limit), errorMessage));
		}
		return split >= 0;
	}

	byte[] getData() {
		if (split < 0) {
			throw new IllegalStateException("Data not yet determined"); //$NON-NLS-1$
		}
		return Arrays.copyOfRange(buf.array(), buf.rpos(),
				split + endOfData.length);
	}

	byte[] getRest() {
		if (split < 0) {
			throw new IllegalStateException(
					"Data not yet determined, so rest still unknown"); //$NON-NLS-1$
		}
		return Arrays.copyOfRange(buf.array(), split + endOfData.length,
				buf.wpos());
	}

	void clear() {
		buf = new ByteArrayBuffer();
		split = -1;
	}

	private static int indexOf(byte[] data, int from, int len, byte[] needle) {
		int to = from + len - needle.length;
		for (int i = from; i <= to; i++) {
			int j = 0;
			for (int k = i; j < needle.length && data[k] == needle[j]; k++) {
				j++;
			}
			if (j == needle.length) {
				return i;
			}
		}
		return -1;
	}
}
