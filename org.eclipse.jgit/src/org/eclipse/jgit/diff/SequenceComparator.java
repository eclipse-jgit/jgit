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
 * Equivalence function for a {@link Sequence} compared by difference algorithm.
 * <p>
 * Difference algorithms can use a comparator to compare portions of two
 * sequences and discover the minimal edits required to transform from one
 * sequence to the other sequence.
 * <p>
 * Indexes within a sequence are zero-based.
 *
 * @param <S>
 *            type of sequence the comparator supports.
 */
public abstract class SequenceComparator<S extends Sequence> {
	/**
	 * Compare two items to determine if they are equivalent.
	 *
	 * It is permissible to compare sequence {@code a} with itself (by passing
	 * {@code a} again in position {@code b}).
	 *
	 * @param a
	 *            the first sequence.
	 * @param ai
	 *            item of {@code ai} to compare.
	 * @param b
	 *            the second sequence.
	 * @param bi
	 *            item of {@code bi} to compare.
	 * @return true if the two items are identical according to this function's
	 *         equivalence rule.
	 */
	public abstract boolean equals(S a, int ai, S b, int bi);

	/**
	 * Get a hash value for an item in a sequence.
	 *
	 * If two items are equal according to this comparator's
	 * {@link #equals(Sequence, int, Sequence, int)} method, then this hash
	 * method must produce the same integer result for both items.
	 *
	 * It is not required for two items to have different hash values if they
	 * are are unequal according to the {@code equals()} method.
	 *
	 * @param seq
	 *            the sequence.
	 * @param ptr
	 *            the item to obtain the hash for.
	 * @return hash the hash value.
	 */
	public abstract int hash(S seq, int ptr);

	/**
	 * Modify the edit to remove common leading and trailing items.
	 *
	 * The supplied edit {@code e} is reduced in size by moving the beginning A
	 * and B points so the edit does not cover any items that are in common
	 * between the two sequences. The ending A and B points are also shifted to
	 * remove common items from the end of the region.
	 *
	 * @param a
	 *            the first sequence.
	 * @param b
	 *            the second sequence.
	 * @param e
	 *            the edit to start with and update.
	 * @return {@code e} if it was updated in-place, otherwise a new edit
	 *         containing the reduced region.
	 */
	public Edit reduceCommonStartEnd(S a, S b, Edit e) {
		// Skip over items that are common at the start.
		//
		while (e.beginA < e.endA && e.beginB < e.endB
				&& equals(a, e.beginA, b, e.beginB)) {
			e.beginA++;
			e.beginB++;
		}

		// Skip over items that are common at the end.
		//
		while (e.beginA < e.endA && e.beginB < e.endB
				&& equals(a, e.endA - 1, b, e.endB - 1)) {
			e.endA--;
			e.endB--;
		}

		return e;
	}
}
