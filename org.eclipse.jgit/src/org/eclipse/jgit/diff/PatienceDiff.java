/*
 * Copyright (C) 2010, Google Inc.
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

import java.util.Arrays;

import org.eclipse.jgit.util.LongList;

/**
 * An implementation of the patience difference algorithm.
 *
 * This implementation was derived by using the 4 rules that are outlined in
 * Bram Cohen's <a href="http://bramcohen.livejournal.com/73318.html">post</a>
 * on LiveJournal.
 */
public class PatienceDiff {
	private static final int HASH_SHIFT = 32;

	/**
	 * Compute the difference between two files.
	 *
	 * @param a
	 *            the first (aka old) file.
	 * @param b
	 *            the second (aka new) file.
	 * @return the differences describing how to edit {@code a} to become
	 *         {@code b}. The list is empty if they are identical.
	 */
	public static EditList diff(RawText a, RawText b) {
		PatienceDiff d = new PatienceDiff(a, b);
		d.diff(0, a.size(), 0, b.size());
		return d.edits;
	}

	private final EditList edits;

	private final RawText a;

	private final RawText b;

	private PatienceDiff(RawText a, RawText b) {
		this.edits = new EditList();
		this.a = a;
		this.b = b;
	}

	private void diff(int as, int ae, int bs, int be) {
		// Skip over lines that are common at the start of the text.
		//
		while (as < ae && bs < be && a.equals(as, b, bs)) {
			as++;
			bs++;
		}
		if (as == ae && bs == be)
			return;

		// Skip over lines that are common at the end of the text.
		//
		while (as < ae && bs < be && a.equals(ae - 1, b, be - 1)) {
			ae--;
			be--;
		}

		// Degenerate case of single edit in the middle.
		//
		if (as == ae || bs == be) {
			edits.add(new Edit(as, ae, bs, be));
			return;
		}

		LongList matchPoints = match(index(a, as, ae), index(b, bs, be));
		if (matchPoints.size() == 0) {
			// If we have no unique common lines, replace the entire region
			// on the one side with the region from the other. But can this
			// be less than optimal? This implies we had no unique lines.
			//
			edits.add(new Edit(as, ae, bs, be));
			return;
		}

		// Find the longest common sequence we can of the matches. We'll
		// use that to split this region into three parts:
		// - edit before
		// - the common sequence
		// - edit after
		//
		int longest = 0;
		int l_as = 0, l_ae = 0, l_bs = 0, l_be = 0;
		for (int i = 0; i < matchPoints.size(); i++) {
			long rec = matchPoints.get(i);

			int m_bs = hashOf(rec);
			if (m_bs < l_be)
				continue;

			int m_as = lineOf(rec);
			int m_ae = m_as + 1;
			int m_be = m_bs + 1;

			while (as < m_as && bs < m_bs && a.equals(m_as - 1, b, m_bs - 1)) {
				m_as--;
				m_bs--;
			}
			while (m_ae < ae && m_be < be && a.equals(m_ae, b, m_be)) {
				m_ae++;
				m_be++;
			}

			if (longest < m_be - m_bs) {
				longest = m_be - m_bs;
				l_as = m_as;
				l_ae = m_ae;
				l_bs = m_bs;
				l_be = m_be;
			}
		}

		if (as < l_as || bs < l_bs)
			diff(as, l_as, bs, l_bs);

		if (l_ae < ae || l_be < be)
			diff(l_ae, ae, l_be, be);
	}

	private LongList match(long[] ah, long[] bh) {
		final LongList matches = new LongList();

		int aIdx = 0;
		int bIdx = 0;
		int aKey = hashOf(ah[aIdx]);
		int bKey = hashOf(bh[bIdx]);

		for (;;) {
			if (aKey < bKey) {
				if (++aIdx == ah.length)
					break;
				aKey = hashOf(ah[aIdx]);

			} else if (aKey > bKey) {
				if (++bIdx == bh.length)
					break;
				bKey = hashOf(bh[bIdx]);

			} else if (!isUnique(a, ah, aIdx)) {
				if (++aIdx == ah.length)
					break;
				aKey = hashOf(ah[aIdx]);

			} else if (!isUnique(b, bh, bIdx)) {
				if (++bIdx == bh.length)
					break;
				bKey = hashOf(bh[bIdx]);

			} else {
				final int aLine = lineOf(ah[aIdx]);
				final int bLine = lineOf(bh[bIdx]);

				if (a.equals(aLine, b, bLine))
					matches.add(pair(bLine, aLine));

				if (++aIdx == ah.length || ++bIdx == bh.length)
					break;
				aKey = hashOf(ah[aIdx]);
				bKey = hashOf(bh[bIdx]);
			}
		}

		matches.sort();
		return matches;
	}

	private static long[] index(RawText a, int as, int ae) {
		long[] index = new long[ae - as];
		int[] in = a.hashes;
		for (int i = 0; as < ae; as++, i++)
			index[i] = pair(in[as + 1], as);
		Arrays.sort(index);
		return index;
	}

	private static boolean isUnique(RawText raw, long[] hashes, int ptr) {
		long rec = hashes[ptr++];
		final int hash = hashOf(rec);
		final int line = lineOf(rec);
		while (ptr < hashes.length) {
			rec = hashes[ptr++];
			if (hashOf(rec) != hash)
				return true;
			if (raw.equals(line, raw, lineOf(rec)))
				return false;
		}
		return true;
	}

	private static long pair(int hash, int line) {
		return (((long) hash) << HASH_SHIFT) | line;
	}

	private static int hashOf(long v) {
		return (int) (v >>> HASH_SHIFT);
	}

	private static int lineOf(long v) {
		return (int) v;
	}
}
