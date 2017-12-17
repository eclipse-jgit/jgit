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
 * Wrap another comparator for use with
 * {@link org.eclipse.jgit.diff.HashedSequence}.
 * <p>
 * This comparator acts as a proxy for the real comparator, evaluating the
 * cached hash code before testing the underlying comparator's equality.
 * Comparators of this type must be used with a
 * {@link org.eclipse.jgit.diff.HashedSequence}.
 * <p>
 * To construct an instance of this type use
 * {@link org.eclipse.jgit.diff.HashedSequencePair}.
 *
 * @param <S>
 *            the base sequence type.
 */
public final class HashedSequenceComparator<S extends Sequence> extends
		SequenceComparator<HashedSequence<S>> {
	private final SequenceComparator<? super S> cmp;

	HashedSequenceComparator(SequenceComparator<? super S> cmp) {
		this.cmp = cmp;
	}

	/** {@inheritDoc} */
	@Override
	public boolean equals(HashedSequence<S> a, int ai, //
			HashedSequence<S> b, int bi) {
		return a.hashes[ai] == b.hashes[bi]
				&& cmp.equals(a.base, ai, b.base, bi);
	}

	/** {@inheritDoc} */
	@Override
	public int hash(HashedSequence<S> seq, int ptr) {
		return seq.hashes[ptr];
	}
}
