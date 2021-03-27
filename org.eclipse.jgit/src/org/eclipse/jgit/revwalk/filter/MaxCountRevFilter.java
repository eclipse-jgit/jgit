/*
 * Copyright (C) 2011, Tomasz Zarna <Tomasz.Zarna@pl.ibm.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revwalk.filter;

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Limits the number of commits output.
 */
public class MaxCountRevFilter extends RevFilter {

	private int maxCount;

	private int count;

	private RevFilter and;

	private boolean stop = false;

	private boolean requiresCommitBody = false;

	/**
	 * Create a new max count filter.
	 *
	 * @param maxCount
	 *            the limit
	 * @return a new filter
	 */
	public static RevFilter create(int maxCount) {
		return create(maxCount, null);
	}

	/**
	 * Create a new max count filter with an existing filter, if a matches the
	 * commit, then the count updates.
	 *
	 * @param maxCount
	 *            the limit
	 * @param a
	 *            the filter to be combined together
	 * @return a new filter
	 */
	public static RevFilter create(int maxCount, RevFilter a) {
		if (maxCount < 0) {
			throw new IllegalArgumentException(
					JGitText.get().maxCountMustBeNonNegative);
		}
		return new MaxCountRevFilter(maxCount, a);
	}

	/**
	 * Create a max count filter with two filters, if a and the filter which
	 * combined with b both match the commit, then the count updates.
	 *
	 * @param a
	 *            first filter to test
	 * @param b
	 *            a max count filter
	 * @return a new filter
	 */
	public static RevFilter and(RevFilter a, MaxCountRevFilter b) {
		return b.and(a);
	}

	RevFilter and(RevFilter a) {
		if (and == null) {
			and = a;
		} else {
			and = AndRevFilter.create(a, and);
		}
		requiresCommitBody = and.requiresCommitBody();
		return this;
	}

	private MaxCountRevFilter(int maxCount, RevFilter a) {
		this.count = 0;
		this.maxCount = maxCount;
		if (a != null) {
			this.and = a;
			this.requiresCommitBody = a.requiresCommitBody();
		}
		if (this.maxCount == 0) {
			this.stop = true;
		}
	}

	/** {@inheritDoc} */
	@Override
	public boolean include(RevWalk walker, RevCommit cmit)
			throws StopWalkException, MissingObjectException,
			IncorrectObjectTypeException, IOException {
		if (stop) {
			throw StopWalkException.INSTANCE;
		}
		if (and == null || and.include(walker, cmit)) {
			if (++count >= maxCount) {
				stop = true;
			}
			return true;
		} else {
			return false;
		}
	}

	/** {@inheritDoc} */
	@Override
	public boolean requiresCommitBody() {
		return requiresCommitBody;
	}

	/** {@inheritDoc} */
	@Override
	public RevFilter clone() {
		if (and == null) {
			return new MaxCountRevFilter(maxCount, null);
		} else {
			return new MaxCountRevFilter(maxCount, and.clone());
		}
	}

	@Override
	public String toString() {
		return "(" + and.toString() + " FILTER_MAX_COUNT " + maxCount + ")";
	}
}
