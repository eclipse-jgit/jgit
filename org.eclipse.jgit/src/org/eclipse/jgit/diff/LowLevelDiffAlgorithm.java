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

/** Compares two sequences primarily based upon hash codes. */
public abstract class LowLevelDiffAlgorithm extends DiffAlgorithm {
	@Override
	public <S extends Sequence> EditList diffNonCommon(
			SequenceComparator<? super S> cmp, S a, S b) {
		HashedSequencePair<S> p = new HashedSequencePair<S>(cmp, a, b);
		HashedSequenceComparator<S> hc = p.getComparator();
		HashedSequence<S> ha = p.getA();
		HashedSequence<S> hb = p.getB();
		p = null;

		EditList res = new EditList();
		Edit region = new Edit(0, a.size(), 0, b.size());
		diffNonCommon(res, hc, ha, hb, region);
		return res;
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
	 * @param edits
	 *            result list to append the region's edits onto.
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
	 * @param region
	 *            the region being compared within the two sequences.
	 */
	public abstract <S extends Sequence> void diffNonCommon(EditList edits,
			HashedSequenceComparator<S> cmp, HashedSequence<S> a,
			HashedSequence<S> b, Edit region);
}
