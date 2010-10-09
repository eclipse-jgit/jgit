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

package org.eclipse.jgit.revwalk;

/**
 * A collection of integer annotations associated with {@link RevCommit}s.
 *
 * This annotation type is suitable when the majority of RevCommit instances
 * will need a single 32 bit integer associated with them. The annotations are
 * stored in a densely packed primitive array.
 *
 * When possible, callers should prefer the specialized primitive int based
 * methods rather than the auto-boxing Integer based forms inherited from the
 * base class.
 *
 * If an annotation has not yet been assigned, 0 is returned instead of null.
 */
public final class DenseIntAnnotation extends RevAnnotation<Integer> {
	private static final int[] EMPTY_ARRAY = {};

	private int[] values = EMPTY_ARRAY;

	public Integer get(RevCommit obj) {
		return Integer.valueOf(getInt(obj));
	}

	public void set(RevCommit obj, Integer value) {
		setInt(obj, value != null ? value.intValue() : 0);
	}

	/**
	 *
	 * @param obj
	 * @return r
	 */
	public int getInt(RevCommit obj) {
		final int id = obj.annotationId;
		return id < values.length ? values[id] : 0;
	}

	/**
	 *
	 * @param obj
	 * @param value
	 */
	public void set(RevCommit obj, int value) {
		setInt(obj, value);
	}

	/**
	 *
	 * @param obj
	 * @param value
	 */
	public void setInt(RevCommit obj, int value) {
		final int id = obj.annotationId;
		ensureCapacity(id);
		values[id] = value;
	}

	/**
	 *
	 * @param obj
	 * @param delta
	 * @return r
	 */
	public int addAndGet(RevCommit obj, int delta) {
		final int id = obj.annotationId;
		ensureCapacity(id);
		return values[id] += delta;
	}

	/**
	 *
	 * @param obj
	 * @param delta
	 * @return r
	 */
	public int getAndAdd(RevCommit obj, int delta) {
		final int id = obj.annotationId;
		ensureCapacity(id);
		final int old = values[id];
		values[id] = old + delta;
		return old;
	}

	/**
	 *
	 * @param obj
	 * @return r
	 */
	public int getAndDecrement(RevCommit obj) {
		return getAndAdd(obj, -1);
	}

	/**
	 *
	 * @param obj
	 * @return r
	 */
	public int getAndIncrement(RevCommit obj) {
		return getAndAdd(obj, 1);
	}

	/**
	 *
	 * @param obj
	 * @return r
	 */
	public int decrementAndGet(RevCommit obj) {
		return addAndGet(obj, -1);
	}

	/**
	 *
	 * @param obj
	 * @return r
	 */
	public int incrementAndGet(RevCommit obj) {
		return addAndGet(obj, 1);
	}

	private void ensureCapacity(final int annotationId) {
		if (annotationId <= values.length) {
			int[] n = new int[newSize(annotationId)];
			System.arraycopy(values, 0, n, 0, values.length);
			values = n;
		}
	}

	private int newSize(int annotationId) {
		return Math.max(16, Math.max(annotationId + 1, 2 * values.length));
	}
}
