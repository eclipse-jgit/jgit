/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

/**
 * A rough character sequence around a raw byte buffer.
 * <p>
 * Characters are assumed to be 8-bit US-ASCII.
 */
public final class RawCharSequence implements CharSequence {
	/** A zero-length character sequence. */
	public static final RawCharSequence EMPTY = new RawCharSequence(null, 0, 0);

	final byte[] buffer;

	final int startPtr;

	final int endPtr;

	/**
	 * Create a rough character sequence around the raw byte buffer.
	 *
	 * @param buf
	 *            buffer to scan.
	 * @param start
	 *            starting position for the sequence.
	 * @param end
	 *            ending position for the sequence.
	 */
	public RawCharSequence(byte[] buf, int start, int end) {
		buffer = buf;
		startPtr = start;
		endPtr = end;
	}

	/** {@inheritDoc} */
	@Override
	public char charAt(int index) {
		return (char) (buffer[startPtr + index] & 0xff);
	}

	/** {@inheritDoc} */
	@Override
	public int length() {
		return endPtr - startPtr;
	}

	/** {@inheritDoc} */
	@Override
	public CharSequence subSequence(int start, int end) {
		return new RawCharSequence(buffer, startPtr + start, startPtr + end);
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		final int n = length();
		final StringBuilder b = new StringBuilder(n);
		for (int i = 0; i < n; i++)
			b.append(charAt(i));
		return b.toString();
	}
}
