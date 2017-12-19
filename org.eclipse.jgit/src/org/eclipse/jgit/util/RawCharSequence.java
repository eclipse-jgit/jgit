/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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
	public RawCharSequence(final byte[] buf, final int start, final int end) {
		buffer = buf;
		startPtr = start;
		endPtr = end;
	}

	/** {@inheritDoc} */
	@Override
	public char charAt(final int index) {
		return (char) (buffer[startPtr + index] & 0xff);
	}

	/** {@inheritDoc} */
	@Override
	public int length() {
		return endPtr - startPtr;
	}

	/** {@inheritDoc} */
	@Override
	public CharSequence subSequence(final int start, final int end) {
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
