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
 * Wraps a {@link org.eclipse.jgit.diff.Sequence} to have a narrower range of
 * elements.
 * <p>
 * This sequence acts as a proxy for the real sequence, translating element
 * indexes on the fly by adding {@code begin} to them. Sequences of this type
 * must be used with a {@link org.eclipse.jgit.diff.SubsequenceComparator}.
 *
 * @param <S>
 *            the base sequence type.
 */
public final class Subsequence<S extends Sequence> extends Sequence {
	/**
	 * Construct a subsequence around the A region/base sequence.
	 *
	 * @param a
	 *            the A sequence.
	 * @param region
	 *            the region of {@code a} to create a subsequence around.
	 * @return subsequence of {@code base} as described by A in {@code region}.
	 */
	public static <S extends Sequence> Subsequence<S> a(S a, Edit region) {
		return new Subsequence<>(a, region.beginA, region.endA);
	}

	/**
	 * Construct a subsequence around the B region/base sequence.
	 *
	 * @param b
	 *            the B sequence.
	 * @param region
	 *            the region of {@code b} to create a subsequence around.
	 * @return subsequence of {@code base} as described by B in {@code region}.
	 */
	public static <S extends Sequence> Subsequence<S> b(S b, Edit region) {
		return new Subsequence<>(b, region.beginB, region.endB);
	}

	/**
	 * Adjust the Edit to reflect positions in the base sequence.
	 *
	 * @param e
	 *            edit to adjust in-place. Prior to invocation the indexes are
	 *            in terms of the two subsequences; after invocation the indexes
	 *            are in terms of the base sequences.
	 * @param a
	 *            the A sequence.
	 * @param b
	 *            the B sequence.
	 */
	public static <S extends Sequence> void toBase(Edit e, Subsequence<S> a,
			Subsequence<S> b) {
		e.beginA += a.begin;
		e.endA += a.begin;

		e.beginB += b.begin;
		e.endB += b.begin;
	}

	/**
	 * Adjust the Edits to reflect positions in the base sequence.
	 *
	 * @param edits
	 *            edits to adjust in-place. Prior to invocation the indexes are
	 *            in terms of the two subsequences; after invocation the indexes
	 *            are in terms of the base sequences.
	 * @param a
	 *            the A sequence.
	 * @param b
	 *            the B sequence.
	 * @return always {@code edits} (as the list was updated in-place).
	 */
	public static <S extends Sequence> EditList toBase(EditList edits,
			Subsequence<S> a, Subsequence<S> b) {
		for (Edit e : edits)
			toBase(e, a, b);
		return edits;
	}

	final S base;

	final int begin;

	private final int size;

	/**
	 * Construct a subset of another sequence.
	 *
	 * The size of the subsequence will be {@code end - begin}.
	 *
	 * @param base
	 *            the real sequence.
	 * @param begin
	 *            First element index of {@code base} that will be part of this
	 *            new subsequence. The element at {@code begin} will be this
	 *            sequence's element 0.
	 * @param end
	 *            One past the last element index of {@code base} that will be
	 *            part of this new subsequence.
	 */
	public Subsequence(S base, int begin, int end) {
		this.base = base;
		this.begin = begin;
		this.size = end - begin;
	}

	/** {@inheritDoc} */
	@Override
	public int size() {
		return size;
	}
}
