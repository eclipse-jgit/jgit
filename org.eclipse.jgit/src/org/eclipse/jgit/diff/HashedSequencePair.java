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
 * Wraps two {@link Sequence} instances to cache their element hash codes.
 *
 * This pair wraps two sequences that contain cached hash codes for the input
 * sequences.
 *
 * @param <S>
 *            the base sequence type.
 */
public class HashedSequencePair<S extends Sequence> {
	private final SequenceComparator<? super S> cmp;

	private final S baseA;

	private final S baseB;

	private HashedSequence<S> cachedA;

	private HashedSequence<S> cachedB;

	/**
	 * Construct a pair to provide fast hash codes.
	 *
	 * @param cmp
	 *            the base comparator for the sequence elements.
	 * @param a
	 *            the A sequence.
	 * @param b
	 *            the B sequence.
	 */
	public HashedSequencePair(SequenceComparator<? super S> cmp, S a, S b) {
		this.cmp = cmp;
		this.baseA = a;
		this.baseB = b;
	}

	/** @return obtain a comparator that uses the cached hash codes. */
	public HashedSequenceComparator<S> getComparator() {
		return new HashedSequenceComparator<S>(cmp);
	}

	/** @return wrapper around A that includes cached hash codes. */
	public HashedSequence<S> getA() {
		if (cachedA == null)
			cachedA = wrap(baseA);
		return cachedA;
	}

	/** @return wrapper around B that includes cached hash codes. */
	public HashedSequence<S> getB() {
		if (cachedB == null)
			cachedB = wrap(baseB);
		return cachedB;
	}

	private HashedSequence<S> wrap(S base) {
		final int end = base.size();
		final int[] hashes = new int[end];
		for (int ptr = 0; ptr < end; ptr++)
			hashes[ptr] = cmp.hash(base, ptr);
		return new HashedSequence<S>(base, hashes);
	}
}
