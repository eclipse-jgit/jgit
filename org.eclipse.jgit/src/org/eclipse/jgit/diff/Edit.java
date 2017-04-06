/*
 * Copyright (C) 2008-2009, Johannes E. Schindelin <johannes.schindelin@gmx.de>
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
 * A modified region detected between two versions of roughly the same content.
 * <p>
 * An edit covers the modified region only. It does not cover a common region.
 * <p>
 * Regions should be specified using 0 based notation, so add 1 to the start and
 * end marks for line numbers in a file.
 * <p>
 * An edit where {@code beginA == endA && beginB < endB} is an insert edit, that
 * is sequence B inserted the elements in region <code>[beginB, endB)</code> at
 * <code>beginA</code>.
 * <p>
 * An edit where {@code beginA < endA && beginB == endB} is a delete edit, that
 * is sequence B has removed the elements between <code>[beginA, endA)</code>.
 * <p>
 * An edit where {@code beginA < endA && beginB < endB} is a replace edit, that
 * is sequence B has replaced the range of elements between
 * <code>[beginA, endA)</code> with those found in <code>[beginB, endB)</code>.
 */
public class Edit {
	/** Type of edit */
	public static enum Type {
		/** Sequence B has inserted the region. */
		INSERT,

		/** Sequence B has removed the region. */
		DELETE,

		/** Sequence B has replaced the region with different content. */
		REPLACE,

		/** Sequence A and B have zero length, describing nothing. */
		EMPTY;
	}

	int beginA;

	int endA;

	int beginB;

	int endB;

	/**
	 * Create a new empty edit.
	 *
	 * @param as
	 *            beginA: start and end of region in sequence A; 0 based.
	 * @param bs
	 *            beginB: start and end of region in sequence B; 0 based.
	 */
	public Edit(final int as, final int bs) {
		this(as, as, bs, bs);
	}

	/**
	 * Create a new edit.
	 *
	 * @param as
	 *            beginA: start of region in sequence A; 0 based.
	 * @param ae
	 *            endA: end of region in sequence A; must be &gt;= as.
	 * @param bs
	 *            beginB: start of region in sequence B; 0 based.
	 * @param be
	 *            endB: end of region in sequence B; must be &gt; = bs.
	 */
	public Edit(final int as, final int ae, final int bs, final int be) {
		beginA = as;
		endA = ae;

		beginB = bs;
		endB = be;
	}

	/** @return the type of this region */
	public final Type getType() {
		if (beginA < endA) {
			if (beginB < endB)
				return Type.REPLACE;
			else /* if (beginB == endB) */
				return Type.DELETE;

		} else /* if (beginA == endA) */{
			if (beginB < endB)
				return Type.INSERT;
			else /* if (beginB == endB) */
				return Type.EMPTY;
		}
	}

	/** @return true if the edit is empty (lengths of both a and b is zero). */
	public final boolean isEmpty() {
		return beginA == endA && beginB == endB;
	}

	/** @return start point in sequence A. */
	public final int getBeginA() {
		return beginA;
	}

	/** @return end point in sequence A. */
	public final int getEndA() {
		return endA;
	}

	/** @return start point in sequence B. */
	public final int getBeginB() {
		return beginB;
	}

	/** @return end point in sequence B. */
	public final int getEndB() {
		return endB;
	}

	/** @return length of the region in A. */
	public final int getLengthA() {
		return endA - beginA;
	}

	/** @return length of the region in B. */
	public final int getLengthB() {
		return endB - beginB;
	}

	/**
	 * Move the edit region by the specified amount.
	 *
	 * @param amount
	 *            the region is shifted by this amount, and can be positive or
	 *            negative.
	 * @since 4.8
	 */
	public final void shift(int amount) {
		beginA += amount;
		endA += amount;
		beginB += amount;
		endB += amount;
	}

	/**
	 * Construct a new edit representing the region before cut.
	 *
	 * @param cut
	 *            the cut point. The beginning A and B points are used as the
	 *            end points of the returned edit.
	 * @return an edit representing the slice of {@code this} edit that occurs
	 *         before {@code cut} starts.
	 */
	public final Edit before(Edit cut) {
		return new Edit(beginA, cut.beginA, beginB, cut.beginB);
	}

	/**
	 * Construct a new edit representing the region after cut.
	 *
	 * @param cut
	 *            the cut point. The ending A and B points are used as the
	 *            starting points of the returned edit.
	 * @return an edit representing the slice of {@code this} edit that occurs
	 *         after {@code cut} ends.
	 */
	public final Edit after(Edit cut) {
		return new Edit(cut.endA, endA, cut.endB, endB);
	}

	/** Increase {@link #getEndA()} by 1. */
	public void extendA() {
		endA++;
	}

	/** Increase {@link #getEndB()} by 1. */
	public void extendB() {
		endB++;
	}

	/** Swap A and B, so the edit goes the other direction. */
	public void swap() {
		final int sBegin = beginA;
		final int sEnd = endA;

		beginA = beginB;
		endA = endB;

		beginB = sBegin;
		endB = sEnd;
	}

	@Override
	public int hashCode() {
		return beginA ^ endA;
	}

	@Override
	public boolean equals(final Object o) {
		if (o instanceof Edit) {
			final Edit e = (Edit) o;
			return this.beginA == e.beginA && this.endA == e.endA
					&& this.beginB == e.beginB && this.endB == e.endB;
		}
		return false;
	}

	@SuppressWarnings("nls")
	@Override
	public String toString() {
		final Type t = getType();
		return t + "(" + beginA + "-" + endA + "," + beginB + "-" + endB + ")";
	}
}
