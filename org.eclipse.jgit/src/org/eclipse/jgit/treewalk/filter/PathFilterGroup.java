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

import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.ByteArraySet.Hasher;
import org.eclipse.jgit.util.RawParseUtils;

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

		private ByteArraySet fullpaths;

		private ByteArraySet prefixes;

		private byte[] max;

		private Group(final PathFilter[] pathFilters) {
			fullpaths = new ByteArraySet(pathFilters.length);
			prefixes = new ByteArraySet(pathFilters.length / 5);
			// 5 is an empirically derived ratio of #paths/#prefixes from:
			// egit/jgit: 8
			// git: 5
			// linux kernel: 13
			// eclipse.platform.ui: 7
			max = pathFilters[0].pathRaw;
			Hasher hasher = new Hasher(null, 0);
			for (PathFilter pf : pathFilters) {
				hasher.init(pf.pathRaw, pf.pathRaw.length);
				while (hasher.hasNext()) {
					int hash = hasher.nextHash();
					if (hasher.hasNext())
						prefixes.addIfAbsent(pf.pathRaw, hasher.length(), hash);
				}
				fullpaths.addIfAbsent(pf.pathRaw, pf.pathRaw.length,
						hasher.getHash());
				if (compare(max, pf.pathRaw) < 0)
					max = pf.pathRaw;
			}
			// Adjust max for the git sort order. A path we compare
			// with may end with a slash at any position (but the
			// first, but we ignore that here since it's not relevant).
			// Such paths must be included in the processing
			// before we can give up and throw a StopWalkException.
			byte[] newMax = new byte[max.length + 1];
			for (int i = 0; i < max.length; ++i)
				if ((max[i] & 0xFF) < '/')
					newMax[i] = '/';
				else
					newMax[i] = max[i];
			newMax[newMax.length - 1] = '/';
			max = newMax;
		}

		private static int compare(byte[] a, byte[] b) {
			int i = 0;
			while (i < a.length && i < b.length) {
				int ba = a[i] & 0xFF;
				int bb = b[i] & 0xFF;
				int cmp = ba - bb;
				if (cmp != 0)
					return cmp;
				++i;
			}
			return a.length - b.length;
		}

		@Override
		public boolean include(final TreeWalk walker) {

			byte[] rp = walker.getRawPath();
			Hasher hasher = new Hasher(rp, walker.getPathLength());
			while (hasher.hasNext()) {
				int hash = hasher.nextHash();
				if (fullpaths.contains(rp, hasher.length(), hash))
					return true;
				if (!hasher.hasNext())
					if (prefixes.contains(rp, hasher.length(), hash))
						return true;
			}

			final int cmp = walker.isPathPrefix(max, max.length);
			if (cmp > 0)
				throw StopWalkException.INSTANCE;

			return false;
		}

		@Override
		public boolean shouldBeRecursive() {
			return !prefixes.isEmpty();
		}

		@Override
		public TreeFilter clone() {
			return this;
		}

		public String toString() {
			final StringBuilder r = new StringBuilder();
			r.append("FAST("); //$NON-NLS-1$
			boolean first = true;
			for (byte[] p : fullpaths.toArray()) {
				if (!first) {
					r.append(" OR "); //$NON-NLS-1$
				}
				r.append(RawParseUtils.decode(p));
				first = false;
			}
			r.append(")"); //$NON-NLS-1$
			return r.toString();
		}
	}

}
