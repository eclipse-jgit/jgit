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
 * An extended form of Bram Cohen's {@link PatienceDiff}.
 *
 * This algorithm exists primarily as a fallback for {@code PatienceDiff},
 * providing a stable method of computing a difference when there are no more
 * common unique elements in a region.
 *
 * Unlike {@code PatienceDiff}, this implementation builds a histogram of
 * element occurrences in sequence A and finds the longest common subsequence
 * between A and B that has the fewest occurrences in A. At each LCS point the
 * sequence is split and the algorithm is recursively applied to the before and
 * after regions until no more common elements exist.
 *
 * Because construction of the histogram is more expensive than the simpler
 * common unique list used by {@code PatienceDiff} this algorithm will run
 * slower than {@code PatienceDiff}, but should produce the same results
 * whenever there is a common unique element.
 *
 * @see PatienceDiff
 */
public class HistogramDiff extends DiffAlgorithm {
	/** Singleton instance of HistogramDiff. */
	public static final HistogramDiff INSTANCE = new HistogramDiff();

	private HistogramDiff() {
		// Singleton algorithm.
	}

	public <S extends Sequence> EditList diffNonCommon(
			SequenceComparator<? super S> cmp, S a, S b) {
		State<S> s = new State<S>(new HashedSequencePair<S>(cmp, a, b));
		s.diffReplace(new Edit(0, s.a.size(), 0, s.b.size()));
		return s.edits;
	}

	<S extends Sequence> void diffNonCommon(EditList edits,
			HashedSequenceComparator<S> cmp, HashedSequence<S> a,
			HashedSequence<S> b, Edit region) {
		State<S> s = new State<S>(edits, cmp, a, b);
		s.diffReplace(region);
	}

	private class State<S extends Sequence> {
		private final HashedSequenceComparator<S> cmp;

		private final HashedSequence<S> a;

		private final HashedSequence<S> b;

		/** Result edits we have determined that must be made to convert a to b. */
		final EditList edits;

		State(HashedSequencePair<S> p) {
			this.cmp = p.getComparator();
			this.a = p.getA();
			this.b = p.getB();
			this.edits = new EditList();
		}

		State(EditList edits, HashedSequenceComparator<S> cmp,
				HashedSequence<S> a, HashedSequence<S> b) {
			this.cmp = cmp;
			this.a = a;
			this.b = b;
			this.edits = edits;
		}

		void diffReplace(Edit r) {
			Edit lcs = new HistogramDiffIndex<S>(cmp, a, b, r)
					.findLongestCommonSequence();
			if (lcs != null) {
				diff(r.before(lcs));
				diff(r.after(lcs));
			} else {
				edits.add(r);
			}
		}

		private void diff(Edit r) {
			switch (r.getType()) {
			case INSERT:
			case DELETE:
				edits.add(r);
				break;

			case REPLACE:
				diffReplace(r);
				break;

			case EMPTY:
			default:
				throw new IllegalStateException();
			}
		}
	}
}
