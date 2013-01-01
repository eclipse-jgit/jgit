/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.treewalk.filter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Includes tree entries only if they match one or more configured paths.
 * <p>
 * Operates like {@link PathFilter} but causes the walk to abort as soon as the
 * tree can no longer match any of the paths within the group. This may bypass
 * the boolean logic of a higher level AND or OR group, but does improve
 * performance for the common case of examining one or more modified paths.
 * <p>
 * This filter is effectively an OR group around paths, with the early abort
 * feature described above.
 */
public class PathFilterGroup {
	/**
	 * Create a collection of path filters from Java strings.
	 * <p>
	 * Path strings are relative to the root of the repository. If the user's
	 * input should be assumed relative to a subdirectory of the repository the
	 * caller must prepend the subdirectory's path prior to creating the filter.
	 * <p>
	 * Path strings use '/' to delimit directories on all platforms.
	 * <p>
	 * Paths may appear in any order within the collection. Sorting may be done
	 * internally when the group is constructed if doing so will improve path
	 * matching performance.
	 *
	 * @param paths
	 *            the paths to test against. Must have at least one entry.
	 * @return a new filter for the list of paths supplied.
	 */
	public static TreeFilter createFromStrings(final Collection<String> paths) {
		if (paths.isEmpty())
			throw new IllegalArgumentException(JGitText.get().atLeastOnePathIsRequired);
		final PathFilter[] p = new PathFilter[paths.size()];
		int i = 0;
		for (final String s : paths)
			p[i++] = PathFilter.create(s);
		return create(p);
	}

	/**
	 * Create a collection of path filters from Java strings.
	 * <p>
	 * Path strings are relative to the root of the repository. If the user's
	 * input should be assumed relative to a subdirectory of the repository the
	 * caller must prepend the subdirectory's path prior to creating the filter.
	 * <p>
	 * Path strings use '/' to delimit directories on all platforms.
	 * <p>
	 * Paths may appear in any order. Sorting may be done internally when the
	 * group is constructed if doing so will improve path matching performance.
	 *
	 * @param paths
	 *            the paths to test against. Must have at least one entry.
	 * @return a new filter for the paths supplied.
	 */
	public static TreeFilter createFromStrings(final String... paths) {
		if (paths.length == 0)
			throw new IllegalArgumentException(
					JGitText.get().atLeastOnePathIsRequired);
		final int length = paths.length;
		final PathFilter[] p = new PathFilter[length];
		for (int i = 0; i < length; i++)
			p[i] = PathFilter.create(paths[i]);
		return create(p);
	}

	/**
	 * Create a collection of path filters.
	 * <p>
	 * Paths may appear in any order within the collection. Sorting may be done
	 * internally when the group is constructed if doing so will improve path
	 * matching performance.
	 *
	 * @param paths
	 *            the paths to test against. Must have at least one entry.
	 * @return a new filter for the list of paths supplied.
	 */
	public static TreeFilter create(final Collection<PathFilter> paths) {
		if (paths.isEmpty())
			throw new IllegalArgumentException(JGitText.get().atLeastOnePathIsRequired);
		final PathFilter[] p = new PathFilter[paths.size()];
		paths.toArray(p);
		return create(p);
	}

	private static TreeFilter create(final PathFilter[] p) {
		if (p.length == 1)
			return new Single(p[0]);
		return new Group(p);
	}

	static class Single extends TreeFilter {
		private final PathFilter path;

		private final byte[] raw;

		private Single(final PathFilter p) {
			path = p;
			raw = path.pathRaw;
		}

		@Override
		public boolean include(final TreeWalk walker) {
			final int cmp = walker.isPathPrefix(raw, raw.length);
			if (cmp > 0)
				throw StopWalkException.INSTANCE;
			return cmp == 0;
		}

		@Override
		public boolean shouldBeRecursive() {
			return path.shouldBeRecursive();
		}

		@Override
		public TreeFilter clone() {
			return this;
		}

		public String toString() {
			return "FAST_" + path.toString(); //$NON-NLS-1$
		}
	}

	static class Group extends TreeFilter {
		private static int pathPrefixSortCompare(byte[] p1, byte[] p2,
				boolean justMatch) {
			int ci = 0;
			while (ci < p1.length && ci < p2.length) {
				int c1 = p1[ci];
				int c2 = p2[ci];
				if (c1 == '/')
					c1 = 0;
				if (c2 == '/')
					c2 = 0;
				int cmp = c1 - c2;
				if (cmp != 0)
					return cmp;
				++ci;
			}
			if (ci < p1.length) {
				int c1 = p1[ci];
				if (c1 == '/')
					if (justMatch)
						return 0;
				return 1;
			}
			if (ci < p2.length) {
				int c2 = p2[ci];
				if (c2 == '/')
					return 0;
				return -1;
			}
			return 0;
		}

		private static final Comparator<PathFilter> PATH_PREFIX_SORT = new Comparator<PathFilter>() {
			public int compare(final PathFilter o1, final PathFilter o2) {
				return pathPrefixSortCompare(o1.pathRaw, o2.pathRaw, false);
			}

		};

		private final PathFilter[] paths;

		private Group(final PathFilter[] p) {
			paths = p;
			Arrays.sort(paths, PATH_PREFIX_SORT);
		}

		@Override
		public boolean include(final TreeWalk walker) {
			final byte[] rawPath = walker.getRawPath();
			Comparator comparator = new Comparator<Object>() {
				public int compare(Object pf, Object raw) {
					PathFilter pathFilter = (PathFilter) pf;
					int ret = -pathPrefixSortCompare(walker.getRawPath(),
							pathFilter.pathRaw, true);
					return ret;
				}
			};

			Object[] pathsObject = paths;
			Object rawObject = rawPath;
			@SuppressWarnings("unchecked")
			int position = Arrays.binarySearch(pathsObject, rawObject,
					comparator);
			if (position >= 0)
				return true;
			if (position == -paths.length - 1)
				throw StopWalkException.INSTANCE;
			return false;
		}

		@Override
		public boolean shouldBeRecursive() {
			for (final PathFilter p : paths)
				if (p.shouldBeRecursive())
					return true;
			return false;
		}

		@Override
		public TreeFilter clone() {
			return this;
		}

		public String toString() {
			final StringBuilder r = new StringBuilder();
			r.append("FAST("); //$NON-NLS-1$
			for (int i = 0; i < paths.length; i++) {
				if (i > 0)
					r.append(" OR "); //$NON-NLS-1$
				r.append(paths[i].toString());
			}
			r.append(")"); //$NON-NLS-1$
			return r.toString();
		}
	}
}
