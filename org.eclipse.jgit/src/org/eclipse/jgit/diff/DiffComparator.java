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
 * Equivalence function for a sequence compared in a difference algorithm.
 *
 * Difference algorithms can use a comparator to compare portions of two
 * sequences and discover the minimal edits required to transform from one
 * sequence to the other sequence.
 *
 * Indexes within a sequence are zero-based.
 *
 * @param <S>
 *            type of sequence the comparator supports.
 */
public abstract class DiffComparator<S> {
	/**
	 * Get the length of the sequence.
	 *
	 * @param seq
	 *            the sequence.
	 * @return number of items in the sequence.
	 */
	public abstract int size(S seq);

	/**
	 * Compare two items to determine if they are identical.
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
	 * @param seq
	 *            the sequence.
	 * @param ptr
	 *            the item to obtain the hash for.
	 * @return hash the hash value.
	 */
	public abstract int hash(S seq, int ptr);
}
