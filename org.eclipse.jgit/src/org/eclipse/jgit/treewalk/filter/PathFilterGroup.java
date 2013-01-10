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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
			throw new IllegalArgumentException(
					JGitText.get().atLeastOnePathIsRequired);
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
			throw new IllegalArgumentException(
					JGitText.get().atLeastOnePathIsRequired);
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

		private Set<String> fullpaths;

		private Set<String> prefixes;

		private PathFilter max;

		private boolean shouldBeRecursive;

		private Group(final PathFilter[] p) {
			fullpaths = new HashSet<String>(p.length);
			prefixes = new HashSet<String>(p.length / 5);
			// 5 is an empiric ratio of #aths/#prefixes from:
			// egit/jgit: 8
			// git: 5
			// linux kernel: 13
			// eclipse.platform.ui: 7
			for (PathFilter pf : p) {
				fullpaths.add(pf.getPath());
				if (max == null || max.getPath().compareTo(pf.getPath()) < 0)
					max = pf;
				for (int i = pf.getPath().indexOf('/'); i > 0; i = pf.getPath()
						.indexOf('/', i + 1)) {
					prefixes.add(pf.getPath().substring(0, i));
					shouldBeRecursive = true;
				}
			}
		}

		@Override
		public boolean include(final TreeWalk walker) {

			String walkString = walker.getPathString();
			if (fullpaths.contains(walkString))
				return true;
			if (prefixes.contains(walkString))
				return true;
			for (int i = walkString.indexOf('/'); i > 0; i = walkString
					.indexOf('/', i + 1)) {
				String prefix = walkString.substring(0, i);
				if (fullpaths.contains(prefix))
					return true;
			}
			byte[] r = max.pathRaw;
			final int cmp = walker.isPathPrefix(r, r.length);
			if (cmp > 0)
				throw StopWalkException.INSTANCE;

			return false;
		}

		@Override
		public boolean shouldBeRecursive() {
			return shouldBeRecursive;
		}

		@Override
		public TreeFilter clone() {
			return this;
		}

		public String toString() {
			final StringBuilder r = new StringBuilder();
			r.append("FAST("); //$NON-NLS-1$
			boolean first = true;
			for (String p : fullpaths) {
				if (!first) {
					r.append(" OR "); //$NON-NLS-1$
					first = false;
				}
				r.append(p.toString());
			}
			r.append(")"); //$NON-NLS-1$
			return r.toString();
		}
	}
}
