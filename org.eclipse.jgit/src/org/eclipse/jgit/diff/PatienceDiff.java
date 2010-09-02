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
 * Bram Cohen's <a href="http://bramcohen.livejournal.com/73318.html">blog</a>.
 *
 * Because this algorithm requires finding a unique common point to center the
 * longest common subsequence around, input sequences which have no unique
 * elements create a degenerate Edit that simply replaces all of one sequence
 * with all of the other sequence. For many source code files and other human
 * maintained text, this isn't likely to occur. When it does occur, it can be
 * easier to read the resulting large-scale replace than to navigate through a
 * lot of slices of common-but-not-unique lines, like curly braces on lone
 * lines, or XML close tags. Consequently this algorithm is willing to create a
 * degenerate Edit in the worst case, in exchange for what may still be
 * perceived to be an easier to read patch script.
 *
 * In a nutshell, the implementation defines an Edit that replaces all of
 * sequence {@code a} with all of {@code b}. This Edit is reduced and/or split
 * to remove common elements, until only Edits spanning non-common elements
 * remain. Those {@link Edit}s are the differences.
 *
 * A slightly more detailed description of the implementation is:
 *
 * <ol>
 * <li>Define an Edit that spans the entire two sequences. This edit replaces
 * all of {@code a} with all of {@code b}.</li>
 *
 * <li>Shrink the Edit by shifting the starting points later in the sequence to
 * skip over any elements that are common between {@code a} and {@code b}.
 * Likewise shift the ending points earlier in the sequence to skip any trailing
 * elements that are common. The first and last element of the edit are now not
 * common, however there may be common content within interior of the Edit that
 * hasn't been discovered yet.</li>
 *
 * <li>Find unique elements within the Edit region that are in both sequences.
 * This is currently accomplished by hashing the elements, sorting the hash
 * codes, and performing a merge-join on the sorted lists.</li>
 *
 * <li>Order the common unique elements by their position within {@code b}.</li>
 *
 * <li>For each unique element, stretch an Edit around it in both directions,
 * consuming neighboring elements that are common to both sequences. Select the
 * longest such Edit out of the unique element list. During this stretching,
 * some subsequent unique elements may be consumed into an earlier's common
 * Edit. This means not all unique elements are evaluated.</li>
 *
 * <li>Split the Edit region at the longest common edit. Because step 2 shrank
 * the initial region, there must be at least one element before, and at least
 * one element after the split.</li>
 *
 * <li>Recurse on the before and after split points, starting from step 3. Step
 * 2 doesn't need to be done again because any common part was already removed
 * by the prior step 2 or 5.</li>
 * </ol>
 *
 * @param <S>
 *            type of sequence.
 */
public class PatienceDiff<S extends Sequence> {
	private static final int HASH_SHIFT = 32;

	/**
	 * Compute the difference between two sequences.
	 *
	 * @param <S>
	 *            type of sequence.
	 * @param cmp
	 *            equivalence function to compare the two sequences.
	 * @param a
	 *            the first (aka old) sequence.
	 * @param b
	 *            the second (aka new) sequence.
	 * @return the differences describing how to edit {@code a} to become
	 *         {@code b}. The list is empty if they are identical.
	 */
	public static <S extends Sequence> EditList diff(SequenceComparator<S> cmp,
			S a, S b) {
		Edit e = new Edit(0, a.size(), 0, b.size());
		e = cmp.reduceCommonStartEnd(a, b, e);

		PatienceDiff<S> d = new PatienceDiff<S>(cmp, a, b);
		d.diff(e);
		return d.edits;
	}

	private final SequenceComparator<S> cmp;

	private final S a;

	private final S b;

	private final EditList edits;

	private PatienceDiff(SequenceComparator<S> cmp, S a, S b) {
		this.cmp = cmp;
		this.a = a;
		this.b = b;
		this.edits = new EditList();
	}

	private void diff(Edit e) {
		switch (e.getType()) {
		case INSERT:
		case DELETE:
			edits.add(e);
			return;

		case REPLACE:
			break;

		case EMPTY:
			return;
		}

		LongList matchPoints = match(index(a, e.beginA, e.endA), index(b,
				e.beginB, e.endB));
		if (matchPoints.size() == 0) {
			// If we have no unique common lines, replace the entire region
			// on the one side with the region from the other. But can this
			// be less than optimal? This implies we had no unique lines.
			//
			edits.add(e);
			return;
		}

		// Find the longest common sequence we can of the matches. We'll
		// use that to split this region into three parts:
		// - edit before
		// - the common sequence
		// - edit after
		//
		Edit lcs = longestCommonSubsequence(e, matchPoints);

		diff(e.before(lcs));
		diff(e.after(lcs));
	}

	private Edit longestCommonSubsequence(Edit e, LongList matchPoints) {
		Edit lcs = new Edit(0, 0);
		for (int i = 0; i < matchPoints.size(); i++) {
			long rec = matchPoints.get(i);

			int bs = hashOf(rec);
			if (bs < lcs.endB)
				continue;

			int as = lineOf(rec);
			int ae = as + 1;
			int be = bs + 1;

			while (e.beginA < as && e.beginB < bs
					&& cmp.equals(a, as - 1, b, bs - 1)) {
				as--;
				bs--;
			}
			while (ae < e.endA && be < e.endB && cmp.equals(a, ae, b, be)) {
				ae++;
				be++;
			}

			if (lcs.getLengthB() < be - bs) {
				lcs.beginA = as;
				lcs.beginB = bs;
				lcs.endA = ae;
				lcs.endB = be;
			}
		}
		return lcs;
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

				if (cmp.equals(a, aLine, b, bLine))
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

	private long[] index(S content, int as, int ae) {
		long[] index = new long[ae - as];
		for (int i = 0; as < ae; as++, i++)
			index[i] = pair(cmp.hash(content, as), as);
		Arrays.sort(index);
		return index;
	}

	private boolean isUnique(S raw, long[] hashes, int ptr) {
		long rec = hashes[ptr];
		final int hash = hashOf(rec);
		final int line = lineOf(rec);

		// We might be in the middle of the range of values that match
		// our hash. If a prior record matches us, we aren't unique.
		//
		for (int i = ptr - 1; 0 <= i; i--) {
			rec = hashes[i];
			if (hashOf(rec) != hash)
				break;
			if (cmp.equals(raw, line, raw, lineOf(rec)))
				return false;
		}

		while (++ptr < hashes.length) {
			rec = hashes[ptr];
			if (hashOf(rec) != hash)
				return true;
			if (cmp.equals(raw, line, raw, lineOf(rec)))
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
