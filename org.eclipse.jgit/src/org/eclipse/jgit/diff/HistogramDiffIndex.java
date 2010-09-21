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

/**
 * Support {@link HistogramDiff} by computing occurrence counts of elements.
 *
 * Each element in the range being considered is put into a hash table, tracking
 * the number of times that distinct element appears in the sequence. Once all
 * elements have been inserted from sequence A, each element of sequence B is
 * probed in the hash table and the longest common subsequence with the lowest
 * occurrence count in A is used as the result.
 *
 * @param <S>
 *            type of the base sequence.
 */
final class HistogramDiffIndex<S extends Sequence> {
	private final HashedSequenceComparator<S> cmp;

	private final HashedSequence<S> a;

	private final HashedSequence<S> b;

	private final Edit region;

	/** Keyed by {@code cmp.hash() & tableMask} to get {@link #recs} index. */
	private final int[] table;

	private final int tableMask;

	/**
	 * Pair of <code>(count, {@link #ptrs} index)</code>
	 *
	 * The upper 32 bits stores the number of occurrences of the element whose
	 * first occurrence is in the {@link #ptrs} array. The lower 32 bits stores
	 * the array index for {@link #ptrs} for the position that has the first
	 * element's index.
	 */
	private final long[] recs;

	/** Array index of the next entry in the table; 0 if at end of chain. */
	private final int[] recNext;

	/**
	 * Total number of valid pairs in {@link #recs}.
	 *
	 * After scanning this is the number of distinct elements in {@code #a}.
	 */
	private int recCnt;

	/** An element of {@code #a}. */
	private final int[] ptrs;

	/** Array index of the next entry in the table; 0 if at end of chain. */
	private final int[] ptrNext;

	private int ptrCnt;

	private Edit lcs;

	private int cnt;

	HistogramDiffIndex(HashedSequenceComparator<S> cmp, HashedSequence<S> a,
			HashedSequence<S> b, Edit r) {
		this.cmp = cmp;
		this.a = a;
		this.b = b;
		this.region = r;

		final int sz = r.endA - r.beginA;
		table = new int[tableSize(sz)];
		tableMask = table.length - 1;

		// As we insert elements we preincrement so that 0 is never a
		// valid entry. Therefore we have to allocate one extra space.
		//
		recs = new long[1 + sz];
		ptrs = new int[recs.length];
		recNext = new int[recs.length];
		ptrNext = new int[recs.length];
	}

	Edit findLongestCommonSequence() {
		scanA();

		lcs = new Edit(0, 0);
		cnt = Integer.MAX_VALUE;

		for (int bPtr = region.beginB; bPtr < region.endB;)
			bPtr = tryLongestCommonSequence(bPtr);

		return cnt != Integer.MAX_VALUE ? lcs : null;
	}

	private void scanA() {
		// Scan the elements backwards, inserting them into the hash table
		// as we go. Going in reverse places the earliest occurrence of any
		// element at the start of the chain, so we consider earlier matches
		// before later matches.
		//
		SCAN: for (int ptr = region.endA - 1; region.beginA <= ptr; ptr--) {
			final int tIdx = cmp.hash(a, ptr) & tableMask;

			for (int rIdx = table[tIdx]; rIdx != 0; rIdx = recNext[rIdx]) {
				final long rec = recs[rIdx];

				if (cmp.equals(a, ptrs[idxOf(rec)], a, ptr)) {
					// ptr is identical to another element. Insert it onto
					// the front of the existing element chain.
					//
					final int pIdx = ++ptrCnt;
					ptrs[pIdx] = ptr;
					ptrNext[pIdx] = idxOf(rec);
					recs[rIdx] = pair(cntOf(rec) + 1, pIdx);
					continue SCAN;
				}
			}

			// This is the first time we have ever seen this particular
			// element in the sequence. Construct a new chain for it.
			//
			final int pIdx = ++ptrCnt;
			ptrs[pIdx] = ptr;

			final int rIdx = ++recCnt;
			recs[rIdx] = pair(1, pIdx);
			recNext[rIdx] = table[tIdx];
			table[tIdx] = rIdx;
		}
	}

	private int tryLongestCommonSequence(final int bPtr) {
		int bNext = bPtr + 1;
		int rIdx = table[cmp.hash(b, bPtr) & tableMask];
		for (; rIdx != 0; rIdx = recNext[rIdx]) {
			final long rec = recs[rIdx];
			if (cntOf(rec) > cnt) {
				// If there are more occurrences in A, don't use this chain.
				continue;
			}

			int aIdx = idxOf(rec);
			if (!cmp.equals(a, ptrs[aIdx], b, bPtr)) {
				// This chain doesn't match the element, look for another.
				continue;
			}

			do {
				int as = ptrs[aIdx];
				int bs = bPtr;
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

				if (cntOf(rec) < cnt || lcs.getLengthA() < ae - as) {
					// If this region is the longest, or there are less
					// occurrences of it in A, its now our LCS.
					//
					lcs.beginA = as;
					lcs.beginB = bs;
					lcs.endA = ae;
					lcs.endB = be;
					cnt = cntOf(rec);
				}

				if (bNext < be)
					bNext = be;
				aIdx = ptrNext[aIdx];
			} while (aIdx != 0);
		}
		return bNext;
	}

	private static long pair(int cnt, int idx) {
		return (((long) cnt) << 32) | idx;
	}

	private static int cntOf(long rec) {
		return (int) (rec >>> 32);
	}

	private static int idxOf(long rec) {
		return (int) rec;
	}

	private static int tableSize(final int worstCaseBlockCnt) {
		int shift = 32 - Integer.numberOfLeadingZeros(worstCaseBlockCnt);
		int sz = 1 << (shift - 1);
		if (sz < worstCaseBlockCnt)
			sz <<= 1;
		return sz;
	}
}
