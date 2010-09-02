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
 * Supports {@link PatienceDiff} by finding unique but common elements.
 *
 * @param <S>
 *            type of sequence the scanner will scan.
 */
final class PatienceDiffIndex<S extends Sequence> {
	private static final int A_DUPLICATE = 1;

	private static final int B_DUPLICATE = 2;

	private static final int DUPLICATE_MASK = B_DUPLICATE | A_DUPLICATE;

	private static final int A_SHIFT = 2;

	private static final int B_SHIFT = 31 + 2;

	private static final int PTR_MASK = 0x7fffffff;

	private final SequenceComparator<S> cmp;

	/** Keyed by {@code cmp.hash() & tableMask} to yield an entry offset. */
	private final int[] table;

	private final int tableMask;

	// To save memory the buckets for hash chains are stored in correlated
	// arrays. This permits us to get 3 values per entry, without paying
	// the penalty for an object header on each entry.

	/** Cached hash value for an element as returned by {@link #cmp}. */
	private final int[] hash;

	/**
	 * A matched (or partially examined) element from the two sequences.
	 *
	 * This is actually a 4-tuple: (bPtr, aPtrP1, bDuplicate, aDuplicate).
	 *
	 * bPtr and aPtr are each 31 bits. bPtr is exactly the position in the b
	 * sequence, while aPtrP1 is {@code aPtr + 1}. This permits us to determine
	 * if there is corresponding element in a by testing for aPtrP1 != 0. If it
	 * equals 0, there is no element in a. If it equals 1, element 0 of a
	 * matches with element bPtr of b.
	 *
	 * bDuplicate is 1 if this element occurs more than once in b; likewise
	 * aDuplicate is 1 if this element occurs more than once in a. These flags
	 * permit each element to only be added to the index once. As the duplicates
	 * are the low 2 bits a unique record meets (@code (rec & 2) == 0}.
	 */
	private final long[] ptrs;

	/** Array index of the next entry in the table; 0 if at end of chain. */
	private final int[] next;

	/** Total number of entries that exist in {@link #ptrs}. */
	private int entryCnt;

	long[] nCommon;

	int nCnt;

	int cIdx;

	PatienceDiffIndex(SequenceComparator<S> cmp, Edit region) {
		this.cmp = cmp;

		final int blockCnt = Math.max(region.getLengthA(), region.getLengthB());
		if (blockCnt < 1) {
			table = new int[] {};
			tableMask = 0;

			hash = new int[] {};
			ptrs = new long[] {};
			next = new int[] {};

		} else {
			table = new int[tableSize(blockCnt)];
			tableMask = table.length - 1;

			// As we insert elements we preincrement so that 0 is never a
			// valid entry. Therefore we have to allocate one extra space.
			//
			hash = new int[1 + blockCnt];
			ptrs = new long[hash.length];
			next = new int[hash.length];
		}
	}

	void scanB(S seq, int ptr, int end) {
		// We insert in ascending order so that a later scan of the table
		// from 0 through entryCnt will iterate through B in order. This
		// is the desired result ordering from match().
		//
		SCAN: for (; ptr < end; ptr++) {
			final int key = cmp.hash(seq, ptr);
			final int tIdx = key & tableMask;

			for (int eIdx = table[tIdx]; eIdx != 0; eIdx = next[eIdx]) {
				if (hash[eIdx] != key)
					continue;

				final long rec = ptrs[eIdx];
				if (cmp.equals(seq, ptr, seq, bOf(rec))) {
					ptrs[eIdx] = rec | B_DUPLICATE;
					continue SCAN;
				}
			}

			// The element is (thus far) unique. Inject it into the table.
			//
			final int eIdx = ++entryCnt;
			hash[eIdx] = key;
			ptrs[eIdx] = ((long) ptr) << B_SHIFT;
			next[eIdx] = table[tIdx];
			table[tIdx] = eIdx;
		}
	}

	void scanA(S a, int ptr, int end, S b) {
		SCAN: for (; ptr < end; ptr++) {
			final int key = cmp.hash(a, ptr);
			final int tIdx = key & tableMask;

			for (int eIdx = table[tIdx]; eIdx != 0; eIdx = next[eIdx]) {
				final long rec = ptrs[eIdx];

				if (isDuplicate(rec) || hash[eIdx] != key)
					continue;

				final int aPtr = aOfRaw(rec);
				if (aPtr != 0 && cmp.equals(a, ptr, a, aPtr - 1)) {
					ptrs[eIdx] = rec | A_DUPLICATE;
					continue SCAN;
				}

				if (cmp.equals(a, ptr, b, bOf(rec))) {
					ptrs[eIdx] = rec | (((long) (ptr + 1)) << A_SHIFT);
					continue SCAN;
				}
			}
		}
	}

	Edit match(Edit region, S a, S b, long[] pCommon, int pIdx, int pCnt) {
		// Everything inside of our ptrs table at this point is already
		// paired up, and is sorted by appearance order in B. If the
		// record exists, its certainly in B. So we just have to check
		// that it is still unique, and was paired up with an A.
		//

		nCommon = new long[entryCnt];
		Edit lcs = new Edit(0, 0);

		MATCH: for (int eIdx = 1; eIdx <= entryCnt; eIdx++) {
			final long rec = ptrs[eIdx];
			if (isDuplicate(rec) || aOfRaw(rec) == 0)
				continue;

			int bs = bOf(rec);
			if (bs < lcs.endB)
				continue;

			int as = aOf(rec);
			int be;

			while (pIdx < pCnt) {
				final long p = pCommon[pIdx];
				if (bs < bOf(p))
					break;

				be = aOfRaw(p);
				if (be <= bs) {
					pIdx++;
					continue;
				}
				bs = bOf(p);

				if (lcs.getLengthB() < be - bs) {
					as -= bOf(rec) - bs;
					lcs.beginA = as;
					lcs.beginB = bs;
					lcs.endA = as + (be - bs);
					lcs.endB = be;
					cIdx = nCnt;
				}

				nCommon[nCnt++] = rec;
				pIdx++;
				continue MATCH;
			}

			int ae = as + 1;
			be = bs + 1;

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
				cIdx = nCnt;
			}

			nCommon[nCnt++] = (((long) bs) << B_SHIFT)
					| (((long) be) << A_SHIFT);
		}

		return 0 < nCnt ? lcs : null;
	}

	private static boolean isDuplicate(long rec) {
		return (((int) rec) & DUPLICATE_MASK) != 0;
	}

	private static int aOfRaw(long rec) {
		return ((int) (rec >>> A_SHIFT)) & PTR_MASK;
	}

	private static int aOf(long rec) {
		return aOfRaw(rec) - 1;
	}

	private static int bOf(long rec) {
		return (int) (rec >>> B_SHIFT);
	}

	private static int tableSize(final int worstCaseBlockCnt) {
		int shift = 32 - Integer.numberOfLeadingZeros(worstCaseBlockCnt);
		int sz = 1 << (shift - 1);
		if (sz < worstCaseBlockCnt)
			sz <<= 1;
		return sz;
	}
}
