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

import static org.eclipse.jgit.util.RawCharUtil.isWhitespace;
import static org.eclipse.jgit.util.RawCharUtil.trimLeadingWhitespace;
import static org.eclipse.jgit.util.RawCharUtil.trimTrailingWhitespace;

/** Equivalence function for {@link RawText}. */
public abstract class RawTextComparator extends SequenceComparator<RawText> {
	/** No special treatment. */
	public static final RawTextComparator DEFAULT = new RawTextComparator() {
		@Override
		public boolean equals(RawText a, int ai, RawText b, int bi) {
			ai++;
			bi++;

			if (a.hashes[ai] != b.hashes[bi])
				return false;

			int as = a.lines.get(ai);
			int bs = b.lines.get(bi);
			final int ae = a.lines.get(ai + 1);
			final int be = b.lines.get(bi + 1);

			if (ae - as != be - bs)
				return false;

			while (as < ae) {
				if (a.content[as++] != b.content[bs++])
					return false;
			}
			return true;
		}

		@Override
		public int hashRegion(final byte[] raw, int ptr, final int end) {
			int hash = 5381;
			for (; ptr < end; ptr++)
				hash = (hash << 5) ^ (raw[ptr] & 0xff);
			return hash;
		}
	};

	/** Ignores all whitespace. */
	public static final RawTextComparator WS_IGNORE_ALL = new RawTextComparator() {
		@Override
		public boolean equals(RawText a, int ai, RawText b, int bi) {
			ai++;
			bi++;

			if (a.hashes[ai] != b.hashes[bi])
				return false;

			int as = a.lines.get(ai);
			int bs = b.lines.get(bi);
			int ae = a.lines.get(ai + 1);
			int be = b.lines.get(bi + 1);

			ae = trimTrailingWhitespace(a.content, as, ae);
			be = trimTrailingWhitespace(b.content, bs, be);

			while (as < ae && bs < be) {
				byte ac = a.content[as];
				byte bc = b.content[bs];

				while (as < ae - 1 && isWhitespace(ac)) {
					as++;
					ac = a.content[as];
				}

				while (bs < be - 1 && isWhitespace(bc)) {
					bs++;
					bc = b.content[bs];
				}

				if (ac != bc)
					return false;

				as++;
				bs++;
			}

			return as == ae && bs == be;
		}

		@Override
		public int hashRegion(byte[] raw, int ptr, int end) {
			int hash = 5381;
			for (; ptr < end; ptr++) {
				byte c = raw[ptr];
				if (!isWhitespace(c))
					hash = (hash << 5) ^ (c & 0xff);
			}
			return hash;
		}
	};

	/** Ignores leading whitespace. */
	public static final RawTextComparator WS_IGNORE_LEADING = new RawTextComparator() {
		@Override
		public boolean equals(RawText a, int ai, RawText b, int bi) {
			ai++;
			bi++;

			if (a.hashes[ai] != b.hashes[bi])
				return false;

			int as = a.lines.get(ai);
			int bs = b.lines.get(bi);
			int ae = a.lines.get(ai + 1);
			int be = b.lines.get(bi + 1);

			as = trimLeadingWhitespace(a.content, as, ae);
			bs = trimLeadingWhitespace(b.content, bs, be);

			if (ae - as != be - bs)
				return false;

			while (as < ae) {
				if (a.content[as++] != b.content[bs++])
					return false;
			}
			return true;
		}

		@Override
		public int hashRegion(final byte[] raw, int ptr, int end) {
			int hash = 5381;
			ptr = trimLeadingWhitespace(raw, ptr, end);
			for (; ptr < end; ptr++) {
				hash = (hash << 5) ^ (raw[ptr] & 0xff);
			}
			return hash;
		}
	};

	/** Ignores trailing whitespace. */
	public static final RawTextComparator WS_IGNORE_TRAILING = new RawTextComparator() {
		@Override
		public boolean equals(RawText a, int ai, RawText b, int bi) {
			ai++;
			bi++;

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
		public int hashRegion(final byte[] raw, int ptr, int end) {
			int hash = 5381;
			end = trimTrailingWhitespace(raw, ptr, end);
			for (; ptr < end; ptr++) {
				hash = (hash << 5) ^ (raw[ptr] & 0xff);
			}
			return hash;
		}
	};

	/** Ignores whitespace occurring between non-whitespace characters. */
	public static final RawTextComparator WS_IGNORE_CHANGE = new RawTextComparator() {
		@Override
		public boolean equals(RawText a, int ai, RawText b, int bi) {
			ai++;
			bi++;

			if (a.hashes[ai] != b.hashes[bi])
				return false;

			int as = a.lines.get(ai);
			int bs = b.lines.get(bi);
			int ae = a.lines.get(ai + 1);
			int be = b.lines.get(bi + 1);

			ae = trimTrailingWhitespace(a.content, as, ae);
			be = trimTrailingWhitespace(b.content, bs, be);

			while (as < ae && bs < be) {
				byte ac = a.content[as];
				byte bc = b.content[bs];

				if (ac != bc)
					return false;

				if (isWhitespace(ac))
					as = trimLeadingWhitespace(a.content, as, ae);
				else
					as++;

				if (isWhitespace(bc))
					bs = trimLeadingWhitespace(b.content, bs, be);
				else
					bs++;
			}
			return as == ae && bs == be;
		}

		@Override
		public int hashRegion(final byte[] raw, int ptr, int end) {
			int hash = 5381;
			end = trimTrailingWhitespace(raw, ptr, end);
			while (ptr < end) {
				byte c = raw[ptr];
				hash = (hash << 5) ^ (c & 0xff);
				if (isWhitespace(c))
					ptr = trimLeadingWhitespace(raw, ptr, end);
				else
					ptr++;
			}
			return hash;
		}
	};

	@Override
	public int hash(RawText seq, int ptr) {
		return seq.hashes[ptr + 1];
	}

	/**
	 * Compute a hash code for a region.
	 *
	 * @param raw
	 *            the raw file content.
	 * @param ptr
	 *            first byte of the region to hash.
	 * @param end
	 *            1 past the last byte of the region.
	 * @return hash code for the region <code>[ptr, end)</code> of raw.
	 */
	public abstract int hashRegion(byte[] raw, int ptr, int end);
}
