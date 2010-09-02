/*
 * Copyright (C) 2009-2010, Google Inc.
 * Copyright (C) 2008-2009, Johannes E. Schindelin <johannes.schindelin@gmx.de>
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

package org.eclipse.jgit.diff;

import static org.eclipse.jgit.util.RawCharUtil.trimTrailingWhitespace;

/**
 * A version of {@link RawText} that ignores trailing whitespace.
 */
public class RawTextIgnoreTrailingWhitespace extends RawText {
	/** Creates RawText that ignores only trailing whitespace. */
	@SuppressWarnings("hiding")
	public static final Factory FACTORY = new Factory() {
		public RawText create(byte[] input) {
			return new RawTextIgnoreTrailingWhitespace(input);
		}
	};

	/**
	 * Create a new sequence from an existing content byte array.
	 * <p>
	 * The entire array (indexes 0 through length-1) is used as the content.
	 *
	 * @param input
	 *            the content array. The array is never modified, so passing
	 *            through cached arrays is safe.
	 */
	public RawTextIgnoreTrailingWhitespace(byte[] input) {
		super(input);
	}

	@Override
	public boolean equals(final int i, final Sequence other, final int j) {
		return equals(this, i + 1, (RawText) other, j + 1);
	}

	private static boolean equals(final RawText a, final int ai,
			final RawText b, final int bi) {
		if (a.hashes[ai] != b.hashes[bi])
			return false;

		int as = a.lines.get(ai);
		int bs = b.lines.get(bi);
		int ae = a.lines.get(ai + 1);
		int be = b.lines.get(bi + 1);

		ae = trimTrailingWhitespace(a.content, as, ae);
		be = trimTrailingWhitespace(b.content, bs, be);

		if (ae - as != be - bs)
			return false;

		while (as < ae) {
			if (a.content[as++] != b.content[bs++])
				return false;
		}
		return true;
	}

	@Override
	protected int hashLine(final byte[] raw, int ptr, int end) {
		int hash = 5381;
		end = trimTrailingWhitespace(raw, ptr, end);
		for (; ptr < end; ptr++) {
			hash = (hash << 5) ^ (raw[ptr] & 0xff);
		}
		return hash;
	}
}