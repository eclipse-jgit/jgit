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
 * Compares two {@link Sequence}s to create an {@link EditList} of changes.
 *
 * An algorithm's {@code diff} method must be callable from concurrent threads
 * without data collisions. This permits some algorithms to use a singleton
 * pattern, with concurrent invocations using the same singleton. Other
 * algorithms may support parameterization, in which case the caller can create
 * a unique instance per thread.
 */
public abstract class DiffAlgorithm {
	/**
	 * Supported diff algorithm
	 */
	public enum SupportedAlgorithm {
		/**
		 * Myers diff algorithm
		 */
		MYERS,

		/**
		 * Histogram diff algorithm
		 */
		HISTOGRAM
	}

	/**
	 * @param alg
	 *            the diff algorithm for which an implementation should be
	 *            returned
	 * @return an implementation of the specified diff algorithm
	 */
	public static DiffAlgorithm getAlgorithm(SupportedAlgorithm alg) {
		switch (alg) {
		case MYERS:
			return MyersDiff.INSTANCE;
		case HISTOGRAM:
			return new HistogramDiff();
		default:
			throw new IllegalArgumentException();
		}
	}

	/**
	 * Compare two sequences and identify a list of edits between them.
	 *
	 * @param <S>
	 *            type of sequence being compared.
	 * @param cmp
	 *            the comparator supplying the element equivalence function.
	 * @param a
	 *            the first (also known as old or pre-image) sequence. Edits
	 *            returned by this algorithm will reference indexes using the
	 *            'A' side: {@link Edit#getBeginA()}, {@link Edit#getEndA()}.
	 * @param b
	 *            the second (also known as new or post-image) sequence. Edits
	 *            returned by this algorithm will reference indexes using the
	 *            'B' side: {@link Edit#getBeginB()}, {@link Edit#getEndB()}.
	 * @return a modifiable edit list comparing the two sequences. If empty, the
	 *         sequences are identical according to {@code cmp}'s rules. The
	 *         result list is never null.
	 */
	public <S extends Sequence> EditList diff(
			SequenceComparator<? super S> cmp, S a, S b) {
		Edit region = cmp.reduceCommonStartEnd(a, b, coverEdit(a, b));

		switch (region.getType()) {
		case INSERT:
		case DELETE:
			return EditList.singleton(region);

		case REPLACE: {
			SubsequenceComparator<S> cs = new SubsequenceComparator<S>(cmp);
			Subsequence<S> as = Subsequence.a(a, region);
			Subsequence<S> bs = Subsequence.b(b, region);
			EditList e = Subsequence.toBase(diffNonCommon(cs, as, bs), as, bs);

			// The last insertion may need to be shifted later if it
			// inserts elements that were previously reduced out as
			// common at the end.
			//
			Edit last = e.get(e.size() - 1);
			if (last.getType() == Edit.Type.INSERT) {
				while (last.endB < b.size()
						&& cmp.equals(b, last.beginB, b, last.endB)) {
					last.beginA++;
					last.endA++;
					last.beginB++;
					last.endB++;
				}
			}

			return e;
		}

		case EMPTY:
			return new EditList(0);

		default:
			throw new IllegalStateException();
		}
	}

	private static <S extends Sequence> Edit coverEdit(S a, S b) {
		return new Edit(0, a.size(), 0, b.size());
	}

	/**
	 * Compare two sequences and identify a list of edits between them.
	 *
	 * This method should be invoked only after the two sequences have been
	 * proven to have no common starting or ending elements. The expected
	 * elimination of common starting and ending elements is automatically
	 * performed by the {@link #diff(SequenceComparator, Sequence, Sequence)}
	 * method, which invokes this method using {@link Subsequence}s.
	 *
	 * @param <S>
	 *            type of sequence being compared.
	 * @param cmp
	 *            the comparator supplying the element equivalence function.
	 * @param a
	 *            the first (also known as old or pre-image) sequence. Edits
	 *            returned by this algorithm will reference indexes using the
	 *            'A' side: {@link Edit#getBeginA()}, {@link Edit#getEndA()}.
	 * @param b
	 *            the second (also known as new or post-image) sequence. Edits
	 *            returned by this algorithm will reference indexes using the
	 *            'B' side: {@link Edit#getBeginB()}, {@link Edit#getEndB()}.
	 * @return a modifiable edit list comparing the two sequences.
	 */
	public abstract <S extends Sequence> EditList diffNonCommon(
			SequenceComparator<? super S> cmp, S a, S b);
}
