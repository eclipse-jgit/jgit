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
 * Wraps a {@link Sequence} to have a narrower range of elements.
 *
 * This sequence acts as a proxy for the real sequence, translating element
 * indexes on the fly by adding {@code begin} to them. Sequences of this type
 * must be used with a {@link SubsequenceComparator}.
 *
 * @param <S>
 *            the base sequence type.
 */
public final class Subsequence<S extends Sequence> extends Sequence {
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

	@Override
	public int size() {
		return size;
	}
}
