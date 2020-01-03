/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;

/**
 * Searches text using only substring search.
 * <p>
 * Instances are thread-safe. Multiple concurrent threads may perform matches on
 * different character sequences at the same time.
 */
public class RawSubStringPattern {
	private final String needleString;

	private final byte[] needle;

	/**
	 * Construct a new substring pattern.
	 *
	 * @param patternText
	 *            text to locate. This should be a literal string, as no
	 *            meta-characters are supported by this implementation. The
	 *            string may not be the empty string.
	 */
	public RawSubStringPattern(String patternText) {
		if (patternText.length() == 0)
			throw new IllegalArgumentException(JGitText.get().cannotMatchOnEmptyString);
		needleString = patternText;

		final byte[] b = Constants.encode(patternText);
		needle = new byte[b.length];
		for (int i = 0; i < b.length; i++)
			needle[i] = lc(b[i]);
	}

	/**
	 * Match a character sequence against this pattern.
	 *
	 * @param rcs
	 *            the sequence to match. Must not be null but the length of the
	 *            sequence is permitted to be 0.
	 * @return offset within <code>rcs</code> of the first occurrence of this
	 *         pattern; -1 if this pattern does not appear at any position of
	 *         <code>rcs</code>.
	 */
	public int match(RawCharSequence rcs) {
		final int needleLen = needle.length;
		final byte first = needle[0];

		final byte[] text = rcs.buffer;
		int matchPos = rcs.startPtr;
		final int maxPos = rcs.endPtr - needleLen;

		OUTER: for (; matchPos <= maxPos; matchPos++) {
			if (neq(first, text[matchPos])) {
				while (++matchPos <= maxPos && neq(first, text[matchPos])) {
					/* skip */
				}
				if (matchPos > maxPos)
					return -1;
			}

			int si = matchPos + 1;
			for (int j = 1; j < needleLen; j++, si++) {
				if (neq(needle[j], text[si]))
					continue OUTER;
			}
			return matchPos;
		}
		return -1;
	}

	private static final boolean neq(byte a, byte b) {
		return a != b && a != lc(b);
	}

	private static final byte lc(byte q) {
		return (byte) StringUtils.toLowerCase((char) (q & 0xff));
	}

	/**
	 * Get the literal pattern string this instance searches for.
	 *
	 * @return the pattern string given to our constructor.
	 */
	public String pattern() {
		return needleString;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return pattern();
	}
}
