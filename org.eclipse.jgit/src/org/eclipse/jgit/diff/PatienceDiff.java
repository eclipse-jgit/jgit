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

		switch (e.getType()) {
		case INSERT:
		case DELETE: {
			EditList r = new EditList();
			r.add(e);
			return r;
		}

		case REPLACE: {
			PatienceDiff<S> d = new PatienceDiff<S>(cmp, a, b, e);
			d.diff(e);
			return d.edits;
		}

		case EMPTY:
			return new EditList();

		default:
			throw new IllegalStateException();
		}
	}

	private final SequenceComparator<S> cmp;

	private final S a;

	private final S b;

	/** Result edits we have determined that must be made to convert a to b. */
	private final EditList edits;

	/**
	 * Temporary list holding pairs of (index_B, index_A) that are common.
	 *
	 * This list is computed during {@link #match(Edit)} and cleared during each
	 * step of the main algorithm. We keep it as an instance member so the
	 * memory allocation can be reused for each match stage, rather than
	 * churning a lot of garbage.
	 */
	private final LongList matches;

	private final LongList splitHoldingQueue;

	private final long[] aIndex;

	private final long[] bIndex;

	private PatienceDiff(SequenceComparator<S> cmp, S a, S b, Edit region) {
		this.cmp = cmp;
		this.a = a;
		this.b = b;
		this.edits = new EditList();
		this.matches = new LongList();
		this.splitHoldingQueue = new LongList();
		this.aIndex = index(a, region.beginA, region.endA);
		this.bIndex = index(b, region.beginB, region.endB);
	}

	private void diff(Edit region) {
		diff(region, new Edit(0, aIndex.length, 0, bIndex.length));
	}

	private void diff(Edit region, Edit index) {
		switch (region.getType()) {
		case INSERT:
		case DELETE:
			edits.add(region);
			return;

		case REPLACE:
			break;

		case EMPTY:
			return;
		}

		match(index);

		if (matches.size() == 0) {
			// If we have no unique common lines, replace the entire region
			// on the one side with the region from the other. But can this
			// be less than optimal? This implies we had no unique lines.
			//
			edits.add(region);
			return;
		}

		// Find the longest common sequence we can of the matches. We'll
		// use that to split this region into three parts:
		// - edit before
		// - the common sequence
		// - edit after
		//
		Edit lcs = longestCommonSubsequence(region);
		Edit rBefore = region.before(lcs);
		Edit rAfter = region.after(lcs);

		Edit iBefore = new Edit(0, 0);
		Edit iAfter = new Edit(0, 0);

		splitIndexA(index, rBefore, rAfter, iBefore, iAfter);
		splitIndexB(index, rBefore, rAfter, iBefore, iAfter);

		region = null;
		index = null;
		lcs = null;

		diff(rBefore, iBefore);
		diff(rAfter, iAfter);
	}

	private Edit longestCommonSubsequence(Edit region) {
		Edit lcs = new Edit(0, 0);
		for (int i = 0; i < matches.size(); i++) {
			long rec = matches.get(i);

			int bs = bOf(rec);
			if (bs < lcs.endB)
				continue;

			int as = aOf(rec);
			int ae = as + 1;
			int be = bs + 1;

			while (region.beginA < as && region.beginB < bs
					&& cmp.equals(a, as - 1, b, bs - 1)) {
				as--;
				bs--;
			}
			while (ae < region.endA && be < region.endB
					&& cmp.equals(a, ae, b, be)) {
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

	private void match(Edit index) {
		matches.clear();

		int aIdx = index.beginA;
		int bIdx = index.beginB;
		int aKey = hashOf(aIndex[aIdx]);
		int bKey = hashOf(bIndex[bIdx]);

		for (;;) {
			if (aKey < bKey) {
				if (++aIdx == index.endA)
					break;
				aKey = hashOf(aIndex[aIdx]);

			} else if (aKey > bKey) {
				if (++bIdx == index.endB)
					break;
				bKey = hashOf(bIndex[bIdx]);

			} else if (!isUnique(a, aIndex, aIdx, index.beginA, index.endA)) {
				if (++aIdx == index.endA)
					break;
				aKey = hashOf(aIndex[aIdx]);

			} else if (!isUnique(b, bIndex, bIdx, index.beginB, index.endB)) {
				if (++bIdx == index.endB)
					break;
				bKey = hashOf(bIndex[bIdx]);

			} else {
				final int aLine = elementOf(aIndex[aIdx]);
				final int bLine = elementOf(bIndex[bIdx]);

				if (cmp.equals(a, aLine, b, bLine))
					matches.add(pairBA(bLine, aLine));

				if (++aIdx == index.endA || ++bIdx == index.endB)
					break;
				aKey = hashOf(aIndex[aIdx]);
				bKey = hashOf(bIndex[bIdx]);
			}
		}

		matches.sort();
	}

	private void splitIndexA(Edit index, Edit rBefore, Edit rAfter,
			Edit iBefore, Edit iAfter) {
		splitHoldingQueue.clear();

		int bOut = index.beginA;
		int aOut = bOut + rBefore.getLengthA();
		final int seg1 = aOut;

		iBefore.beginA = bOut;
		iAfter.beginA = aOut;

		for (int p = index.beginA; p < seg1; p++) {
			long rec = aIndex[p];
			int line = elementOf(rec);

			if (line < rBefore.endA)
				aIndex[bOut++] = rec;

			else if (rAfter.beginA <= line) {
				long tmpRec = aIndex[aOut];
				int tmpLine = elementOf(tmpRec);
				if (tmpLine < rBefore.endA || rAfter.beginA <= tmpLine)
					splitHoldingQueue.add(tmpRec);
				aIndex[aOut++] = rec;
			}
		}

		for (int k = 0; k < splitHoldingQueue.size(); k++) {
			long rec = splitHoldingQueue.get(k);
			int line = elementOf(rec);

			if (line < rBefore.endA)
				aIndex[bOut++] = rec;

			else if (rAfter.beginA <= line) {
				long tmpRec = aIndex[aOut];
				int tmpLine = elementOf(tmpRec);
				if (tmpLine < rBefore.endA || rAfter.beginA <= tmpLine)
					splitHoldingQueue.add(tmpRec);
				aIndex[aOut++] = rec;
			}
		}

		for (int p = aOut; p < index.endA; p++) {
			long rec = aIndex[p];
			int line = elementOf(rec);

			if (line < rBefore.endA)
				aIndex[bOut++] = rec;

			else if (rAfter.beginA <= line)
				aIndex[aOut++] = rec;
		}

		iBefore.endA = bOut;
		iAfter.endA = aOut;
	}

	private void splitIndexB(Edit index, Edit rBefore, Edit rAfter,
			Edit iBefore, Edit iAfter) {
		splitHoldingQueue.clear();

		int bOut = index.beginB;
		int aOut = bOut + rBefore.getLengthB();
		final int seg1 = aOut;

		iBefore.beginB = bOut;
		iAfter.beginB = aOut;

		for (int p = index.beginB; p < seg1; p++) {
			long rec = bIndex[p];
			int line = elementOf(rec);

			if (line < rBefore.endB)
				bIndex[bOut++] = rec;

			else if (rAfter.beginB <= line) {
				long tmpRec = bIndex[aOut];
				int tmpLine = elementOf(tmpRec);
				if (tmpLine < rBefore.endB || rAfter.beginB <= tmpLine)
					splitHoldingQueue.add(tmpRec);
				bIndex[aOut++] = rec;
			}
		}

		for (int k = 0; k < splitHoldingQueue.size(); k++) {
			long rec = splitHoldingQueue.get(k);
			int line = elementOf(rec);

			if (line < rBefore.endB)
				bIndex[bOut++] = rec;

			else if (rAfter.beginB <= line) {
				long tmpRec = bIndex[aOut];
				int tmpLine = elementOf(tmpRec);
				if (tmpLine < rBefore.endB || rAfter.beginB <= tmpLine)
					splitHoldingQueue.add(tmpRec);
				bIndex[aOut++] = rec;
			}
		}

		for (int p = aOut; p < index.endB; p++) {
			long rec = bIndex[p];
			int line = elementOf(rec);

			if (line < rBefore.endB)
				bIndex[bOut++] = rec;

			else if (rAfter.beginB <= line)
				bIndex[aOut++] = rec;
		}

		iBefore.endB = bOut;
		iAfter.endB = aOut;
	}

	private long[] index(S content, int as, int ae) {
		long[] index = new long[ae - as];
		for (int i = 0; as < ae; as++, i++)
			index[i] = pairHashElement(cmp.hash(content, as), as);
		Arrays.sort(index);
		return index;
	}

	private boolean isUnique(S raw, long[] hashes, int ptr, int min, int max) {
		long rec = hashes[ptr];

		final int hash = hashOf(rec);
		final int line = elementOf(rec);

		// We might be in the middle of the range of values that match
		// our hash. If a prior record matches us, we aren't unique.
		//
		for (int i = ptr - 1; min <= i; i--) {
			rec = hashes[i];
			if (hashOf(rec) != hash)
				break;
			if (cmp.equals(raw, line, raw, elementOf(rec)))
				return false;
		}

		while (++ptr < max) {
			rec = hashes[ptr];
			if (hashOf(rec) != hash)
				break;
			if (cmp.equals(raw, line, raw, elementOf(rec)))
				return false;
		}

		return true;
	}

	// Pairs a hash code and an element.

	private static long pairHashElement(int hash, int elem) {
		return (((long) hash) << 32) | elem;
	}

	private static int hashOf(long v) {
		return (int) (v >>> 32);
	}

	private static int elementOf(long v) {
		return ((int) v);
	}

	// Pairs a B and A element together.

	private static long pairBA(int b, int a) {
		return (((long) b) << 32) | a;
	}

	private static int bOf(long v) {
		return (int) (v >>> 32);
	}

	private static int aOf(long v) {
		return (int) v;
	}
}
