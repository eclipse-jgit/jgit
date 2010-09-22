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

import org.eclipse.jgit.JGitText;

/**
 * Supports {@link PatienceDiff} by finding unique but common elements.
 *
 * This index object is constructed once for each region being considered by the
 * main {@link PatienceDiff} algorithm, which really means its once for each
 * recursive step. Each index instance processes a fixed sized region from the
 * sequences, and during recursion the region is split into two smaller segments
 * and processed again.
 *
 * @param <S>
 *            type of sequence the scanner will scan.
 */
final class PatienceDiffIndex<S extends Sequence> {
	private static final int DUPLICATE = 1 << 0;

	private static final int COMMON = 1 << 1;

	private static final int PTR_SHIFT = 2;

	private static final int MAX_PTR = 1 << (32 - PTR_SHIFT);

	private final HashedSequenceComparator<S> cmp;

	private final HashedSequence<S> a;

	private final HashedSequence<S> b;

	private final Edit region;

	/** Keyed by {@code cmp.hash() & tableMask} to yield an entry offset. */
	private final int[] table;

	private final int tableMask;

	// To save memory the buckets for hash chains are stored in correlated
	// arrays. This permits us to get 2 values per entry, without paying
	// the penalty for an object header on each entry.

	/**
	 * Tuple of {@code aPtr, common, duplicate}.
	 *
	 * aPtr is an index of an element in sequence {@link #a}, and is only 30
	 * bits wide. Due to this narrowed limitation sequences with element
	 * addresses over 2^30 aren't supported.
	 *
	 * Common is a single bit and is set to true if this element also appears in
	 * {@link #b}.
	 *
	 * Duplicate is a single bit set if this element appears more than once in
	 * sequence {@link #a}, or appears again in sequence {@link #b} after common
	 * was already set.
	 */
	private final int[] recs;

	/** Array index of the next entry in the table; 0 if at end of chain. */
	private final int[] next;

	/** Total number of entries that exist in {@link #recs}. */
	private int entryCnt;

	private LCS lcs;

	PatienceDiffIndex(HashedSequenceComparator<S> cmp, HashedSequence<S> a,
			HashedSequence<S> b, Edit region) {
		this.cmp = cmp;
		this.a = a;
		this.b = b;
		this.region = region;

		if (region.endA >= MAX_PTR)
			throw new IllegalArgumentException(
					JGitText.get().sequenceTooLargeForDiffAlgorithm);

		final int sz = region.getLengthA();
		table = new int[tableSize(sz)];
		tableMask = table.length - 1;

		// As we insert elements we preincrement so that 0 is never a
		// valid entry. Therefore we have to allocate one extra space.
		//
		recs = new int[1 + sz];
		next = new int[recs.length];
	}

	private void scanA() {
		SCAN: for (int ptr = region.beginA; ptr < region.endA; ptr++) {
			final int tIdx = cmp.hash(a, ptr) & tableMask;

			for (int eIdx = table[tIdx]; eIdx != 0; eIdx = next[eIdx]) {
				final int rec = recs[eIdx];
				if (cmp.equals(a, ptr, a, rec >>> PTR_SHIFT)) {
					recs[eIdx] = rec | DUPLICATE;
					continue SCAN;
				}
			}

			final int eIdx = ++entryCnt;
			recs[eIdx] = ptr << PTR_SHIFT;
			next[eIdx] = table[tIdx];
			table[tIdx] = eIdx;
		}
	}

	Edit findLongestCommonSequence() {
		scanA();

		for (int bPtr = region.beginB; bPtr < region.endB;)
			bPtr = tryLongestCommonSequence(bPtr);

		while (lcs != null && (recs[lcs.rIdx] & DUPLICATE) != 0)
			lcs = lcs.prior;
		if (lcs != null)
			lcs.prior = null;
		return lcs;
	}

	private int tryLongestCommonSequence(int bs) {
		int eIdx = table[cmp.hash(b, bs) & tableMask];
		for (; eIdx != 0; eIdx = next[eIdx]) {
			final int rec = recs[eIdx];
			if ((rec & DUPLICATE) != 0)
				continue;

			int as = rec >>> PTR_SHIFT;
			if (!cmp.equals(a, as, b, bs))
				continue;

			if ((rec & COMMON) != 0) {
				recs[eIdx] = rec | DUPLICATE;
				while (lcs != null && (recs[lcs.rIdx] & DUPLICATE) != 0)
					lcs = lcs.prior;
				return bs + 1;
			}
			recs[eIdx] = rec | COMMON;

			while (region.beginA < as && region.beginB < bs
					&& cmp.equals(a, as - 1, b, bs - 1)) {
				as--;
				bs--;
			}

			int ae = as + 1;
			int be = bs + 1;
			while (ae < region.endA && be < region.endB
					&& cmp.equals(a, ae, b, be)) {
				ae++;
				be++;
			}

			if (lcs == null || lcs.getLengthA() < ae - as)
				lcs = new LCS(lcs, eIdx, as, ae, bs, be);
			return be;
		}
		return bs + 1;
	}

	private static int tableSize(final int worstCaseBlockCnt) {
		int shift = 32 - Integer.numberOfLeadingZeros(worstCaseBlockCnt);
		int sz = 1 << (shift - 1);
		if (sz < worstCaseBlockCnt)
			sz <<= 1;
		return sz;
	}

	private static class LCS extends Edit {
		LCS prior;

		final int rIdx;

		LCS(LCS prior, int rIdx, int as, int ae, int bs, int be) {
			super(as, ae, bs, be);
			this.prior = prior;
			this.rIdx = rIdx;
		}
	}
}
