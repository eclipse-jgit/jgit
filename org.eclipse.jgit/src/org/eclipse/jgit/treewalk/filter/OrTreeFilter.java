/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.treewalk.filter;

import java.io.IOException;
import java.util.Collection;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Includes a tree entry if any subfilters include the same tree entry.
 * <p>
 * Classic shortcut behavior is used, so evaluation of the
 * {@link org.eclipse.jgit.treewalk.filter.TreeFilter#include(TreeWalk)} method
 * stops as soon as a true result is obtained. Applications can improve
 * filtering performance by placing faster filters that are more likely to
 * accept a result earlier in the list.
 */
public abstract class OrTreeFilter extends TreeFilter {
	/**
	 * Create a filter with two filters, one of which must match.
	 *
	 * @param a
	 *            first filter to test.
	 * @param b
	 *            second filter to test.
	 * @return a filter that must match at least one input filter.
	 */
	public static TreeFilter create(TreeFilter a, TreeFilter b) {
		if (a == ALL || b == ALL)
			return ALL;
		return new Binary(a, b);
	}

	/**
	 * Create a filter around many filters, one of which must match.
	 *
	 * @param list
	 *            list of filters to match against. Must contain at least 2
	 *            filters.
	 * @return a filter that must match at least one input filter.
	 */
	public static TreeFilter create(TreeFilter[] list) {
		if (list.length == 2)
			return create(list[0], list[1]);
		if (list.length < 2)
			throw new IllegalArgumentException(JGitText.get().atLeastTwoFiltersNeeded);
		final TreeFilter[] subfilters = new TreeFilter[list.length];
		System.arraycopy(list, 0, subfilters, 0, list.length);
		return new List(subfilters);
	}

	/**
	 * Create a filter around many filters, one of which must match.
	 *
	 * @param list
	 *            list of filters to match against. Must contain at least 2
	 *            filters.
	 * @return a filter that must match at least one input filter.
	 */
	public static TreeFilter create(Collection<TreeFilter> list) {
		if (list.size() < 2)
			throw new IllegalArgumentException(JGitText.get().atLeastTwoFiltersNeeded);
		final TreeFilter[] subfilters = new TreeFilter[list.size()];
		list.toArray(subfilters);
		if (subfilters.length == 2)
			return create(subfilters[0], subfilters[1]);
		return new List(subfilters);
	}

	private static class Binary extends OrTreeFilter {
		private final TreeFilter a;

		private final TreeFilter b;

		Binary(TreeFilter one, TreeFilter two) {
			a = one;
			b = two;
		}

		@Override
		public boolean include(TreeWalk walker)
				throws MissingObjectException, IncorrectObjectTypeException,
				IOException {
			return matchFilter(walker) <= 0;
		}

		@Override
		public int matchFilter(TreeWalk walker)
				throws MissingObjectException, IncorrectObjectTypeException,
				IOException {
			final int ra = a.matchFilter(walker);
			if (ra == 0) {
				return 0;
			}
			final int rb = b.matchFilter(walker);
			if (rb == 0) {
				return 0;
			}
			if (ra == -1 || rb == -1) {
				return -1;
			}
			return 1;
		}

		@Override
		public boolean shouldBeRecursive() {
			return a.shouldBeRecursive() || b.shouldBeRecursive();
		}

		@Override
		public TreeFilter clone() {
			return new Binary(a.clone(), b.clone());
		}

		@SuppressWarnings("nls")
		@Override
		public String toString() {
			return "(" + a.toString() + " OR " + b.toString() + ")";
		}
	}

	private static class List extends OrTreeFilter {
		private final TreeFilter[] subfilters;

		List(TreeFilter[] list) {
			subfilters = list;
		}

		@Override
		public boolean include(TreeWalk walker)
				throws MissingObjectException, IncorrectObjectTypeException,
				IOException {
			return matchFilter(walker) <= 0;
		}

		@Override
		public int matchFilter(TreeWalk walker)
				throws MissingObjectException, IncorrectObjectTypeException,
				IOException {
			int m = 1;
			for (TreeFilter f : subfilters) {
				int r = f.matchFilter(walker);
				if (r == 0) {
					return 0;
				}
				if (r == -1) {
					m = -1;
				}
			}
			return m;
		}

		@Override
		public boolean shouldBeRecursive() {
			for (TreeFilter f : subfilters)
				if (f.shouldBeRecursive())
					return true;
			return false;
		}

		@Override
		public TreeFilter clone() {
			final TreeFilter[] s = new TreeFilter[subfilters.length];
			for (int i = 0; i < s.length; i++)
				s[i] = subfilters[i].clone();
			return new List(s);
		}

		@Override
		public String toString() {
			final StringBuilder r = new StringBuilder();
			r.append("("); //$NON-NLS-1$
			for (int i = 0; i < subfilters.length; i++) {
				if (i > 0)
					r.append(" OR "); //$NON-NLS-1$
				r.append(subfilters[i].toString());
			}
			r.append(")"); //$NON-NLS-1$
			return r.toString();
		}
	}
}
