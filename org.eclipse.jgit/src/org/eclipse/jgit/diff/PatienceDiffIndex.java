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
 * This index object is constructed once for each region being considered by the
 * main {@link PatienceDiff} algorithm, which really means its once for each
 * recursive step. Each index instance processes a fixed sized region from the
 * sequences, and during recursion the region is split into two smaller segments
 * and processed again.
 *
 * Index instances from a higher level invocation message some state into a
 * lower level invocation by passing the {@link #nCommon} array from the higher
 * invocation into the two sub-steps as {@link #pCommon}. This permits some
 * matching work that was already done in the higher invocation to be reused in
 * the sub-step and can save a lot of time when element equality is expensive.
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

	private final HashedSequenceComparator<S> cmp;

	private final HashedSequence<S> a;

	private final HashedSequence<S> b;

	private final Edit region;

	/** Pairs of beginB, endB indices previously found to be common and unique. */
	private final long[] pCommon;

	/** First valid index in {@link #pCommon}. */
	private final int pBegin;

	/** 1 past the last valid entry in {@link #pCommon}. */
	private final int pEnd;

	/** Keyed by {@link #hash(HashedSequence, int)} to get an entry offset. */
	private final int[] table;

	/** Number of low bits to discard from a key to index {@link #table}. */
	private final int keyShift;

	// To save memory the buckets for hash chains are stored in correlated
	// arrays. This permits us to get 3 values per entry, without paying
	// the penalty for an object header on each entry.

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

	/** Number of entries in {@link #ptrs} that are actually unique. */
	private int uniqueCommonCnt;

	/**
	 * Pairs of beginB, endB indices found to be common and unique.
	 *
	 * In order to find the longest common (but unique) sequence within a
	 * region, we also found all of the other common but unique sequences in
	 * that same region. This array stores all of those results, allowing them
	 * to be passed into the subsequent recursive passes so we can later reuse
	 * these matches and avoid recomputing the same points again.
	 */
	long[] nCommon;

	/** Number of items in {@link #nCommon}. */
	int nCnt;

	/** Index of the longest common subsequence in {@link #nCommon}. */
	int cIdx;

	PatienceDiffIndex(HashedSequenceComparator<S> cmp, //
			HashedSequence<S> a, //
			HashedSequence<S> b, //
			Edit region, //
			long[] pCommon, int pIdx, int pCnt) {
		this.cmp = cmp;
		this.a = a;
		this.b = b;
		this.region = region;
		this.pCommon = pCommon;
		this.pBegin = pIdx;
		this.pEnd = pCnt;

		final int sz = region.getLengthB();
		final int tableBits = tableBits(sz);
		table = new int[1 << tableBits];
		keyShift = 32 - tableBits;

		// As we insert elements we preincrement so that 0 is never a
		// valid entry. Therefore we have to allocate one extra space.
		//
		ptrs = new long[1 + sz];
		next = new int[ptrs.length];
	}

	/**
	 * Index elements in sequence B for later matching with sequence A.
	 *
	 * This is the first stage of preparing an index to find the longest common
	 * sequence. Elements of sequence B in the range [ptr, end) are scanned in
	 * order and added to the internal hashtable.
	 *
	 * If prior matches were given in the constructor, these may be used to
	 * fast-forward through sections of B to avoid unnecessary recomputation.
	 */
	private void scanB() {
		// We insert in ascending order so that a later scan of the table
		// from 0 through entryCnt will iterate through B in order. This
		// is the desired result ordering from match().
		//
		int ptr = region.beginB;
		final int end = region.endB;
		int pIdx = pBegin;
		SCAN: while (ptr < end) {
			final int tIdx = hash(b, ptr);

			if (pIdx < pEnd) {
				final long priorRec = pCommon[pIdx];
				if (ptr == bOf(priorRec)) {
					// We know this region is unique from a prior pass.
					// Insert the start point, and skip right to the end.
					//
					insertB(tIdx, ptr);
					pIdx++;
					ptr = aOfRaw(priorRec);
					continue SCAN;
				}
			}

			// We aren't sure what the status of this element is. Add
			// it to our hashtable, and flag it as duplicate if there
			// was already a different entry present.
			//
			for (int eIdx = table[tIdx]; eIdx != 0; eIdx = next[eIdx]) {
				final long rec = ptrs[eIdx];
				if (cmp.equals(b, ptr, b, bOf(rec))) {
					ptrs[eIdx] = rec | B_DUPLICATE;
					ptr++;
					continue SCAN;
				}
			}

			insertB(tIdx, ptr);
			ptr++;
		}
	}

	private void insertB(final int tIdx, int ptr) {
		final int eIdx = ++entryCnt;
		ptrs[eIdx] = ((long) ptr) << B_SHIFT;
		next[eIdx] = table[tIdx];
		table[tIdx] = eIdx;
	}

	/**
	 * Index elements in sequence A for later matching.
	 *
	 * This is the second stage of preparing an index to find the longest common
	 * sequence. The state requires {@link #scanB()} to have been invoked first.
	 *
	 * Each element of A in the range [ptr, end) are searched for in the
	 * internal hashtable, to see if B has already registered a location.
	 *
	 * If prior matches were given in the constructor, these may be used to
	 * fast-forward through sections of A to avoid unnecessary recomputation.
	 */
	private void scanA() {
		int ptr = region.beginA;
		final int end = region.endA;
		int pLast = pBegin - 1;
		SCAN: while (ptr < end) {
			final int tIdx = hash(a, ptr);

			for (int eIdx = table[tIdx]; eIdx != 0; eIdx = next[eIdx]) {
				final long rec = ptrs[eIdx];
				final int bs = bOf(rec);

				if (isDuplicate(rec) || !cmp.equals(a, ptr, b, bs))
					continue;

				final int aPtr = aOfRaw(rec);
				if (aPtr != 0 && cmp.equals(a, ptr, a, aPtr - 1)) {
					ptrs[eIdx] = rec | A_DUPLICATE;
					uniqueCommonCnt--;
					ptr++;
					continue SCAN;
				}

				// This element is both common and unique. Link the
				// two sequences together at this point.
				//
				ptrs[eIdx] = rec | (((long) (ptr + 1)) << A_SHIFT);
				uniqueCommonCnt++;

				if (pBegin < pEnd) {
					// If we have prior match point data, we might be able
					// to locate the length of the match and skip past all
					// of those elements. We try to take advantage of the
					// fact that pCommon is sorted by B, and its likely that
					// matches in A appear in the same order as they do in B.
					//
					for (int pIdx = pLast + 1;; pIdx++) {
						if (pIdx == pEnd)
							pIdx = pBegin;
						else if (pIdx == pLast)
							break;

						final long priorRec = pCommon[pIdx];
						final int priorB = bOf(priorRec);
						if (bs < priorB)
							break;
						if (bs == priorB) {
							ptr += aOfRaw(priorRec) - priorB;
							pLast = pIdx;
							continue SCAN;
						}
					}
				}

				ptr++;
				continue SCAN;
			}

			ptr++;
		}
	}

	/**
	 * Scan all potential matches and find the longest common sequence.
	 *
	 * If this method returns non-null, the caller should copy out the
	 * {@link #nCommon} array and pass that through to the recursive sub-steps
	 * so that existing common matches can be reused rather than recomputed.
	 *
	 * @return an edit covering the longest common sequence. Null if there are
	 *         no common unique sequences present.
	 */
	Edit findLongestCommonSequence() {
		scanB();
		scanA();

		if (uniqueCommonCnt == 0)
			return null;

		nCommon = new long[uniqueCommonCnt];
		int pIdx = pBegin;
		Edit lcs = new Edit(0, 0);

		MATCH: for (int eIdx = 1; eIdx <= entryCnt; eIdx++) {
			final long rec = ptrs[eIdx];
			if (isDuplicate(rec) || aOfRaw(rec) == 0)
				continue;

			int bs = bOf(rec);
			if (bs < lcs.endB)
				continue;

			int as = aOf(rec);
			if (pIdx < pEnd) {
				final long priorRec = pCommon[pIdx];
				if (bs == bOf(priorRec)) {
					// We had a prior match and we know its unique.
					// Reuse its region rather than computing again.
					//
					int be = aOfRaw(priorRec);

					if (lcs.getLengthB() < be - bs) {
						as -= bOf(rec) - bs;
						lcs.beginA = as;
						lcs.beginB = bs;
						lcs.endA = as + (be - bs);
						lcs.endB = be;
						cIdx = nCnt;
					}

					nCommon[nCnt] = priorRec;
					if (++nCnt == uniqueCommonCnt)
						break MATCH;

					pIdx++;
					continue MATCH;
				}
			}

			// We didn't have prior match data, or this is the first time
			// seeing this particular pair. Extend the region as large as
			// possible and remember it for future use.
			//
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
				cIdx = nCnt;
			}

			nCommon[nCnt] = (((long) bs) << B_SHIFT) | (((long) be) << A_SHIFT);
			if (++nCnt == uniqueCommonCnt)
				break MATCH;
		}

		return lcs;
	}

	private int hash(HashedSequence<S> s, int idx) {
		return (cmp.hash(s, idx) * 0x9e370001 /* mix bits */) >>> keyShift;
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

	private static int tableBits(final int sz) {
		int bits = 31 - Integer.numberOfLeadingZeros(sz);
		if (bits == 0)
			bits = 1;
		if (1 << bits < sz)
			bits++;
		return bits;
	}
}
