/*
 * Copyright (C) 2013, Robin Rosenberg <robin.rosenberg@dewire.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.treewalk.filter;

import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * A filter for extracting changes between two versions of the dircache. In
 * addition to what {@link org.eclipse.jgit.treewalk.filter.TreeFilter#ANY_DIFF}
 * would do, it also detects changes that will affect decorations and show up in
 * an attempt to commit.
 */
public final class InterIndexDiffFilter extends TreeFilter {
	private static final int baseTree = 0;

	/**
	 * Predefined InterIndexDiffFilter for finding changes between two dircaches
	 */
	public static final TreeFilter INSTANCE = new InterIndexDiffFilter();

	/** {@inheritDoc} */
	@Override
	public boolean include(TreeWalk walker) {
		final int n = walker.getTreeCount();
		if (n == 1) // Assume they meant difference to empty tree.
			return true;

		final int m = walker.getRawMode(baseTree);
		for (int i = 1; i < n; i++) {
			DirCacheIterator baseDirCache = walker.getTree(baseTree,
					DirCacheIterator.class);
			DirCacheIterator newDirCache = walker.getTree(i,
					DirCacheIterator.class);
			if (baseDirCache != null && newDirCache != null) {
				DirCacheEntry baseDci = baseDirCache.getDirCacheEntry();
				DirCacheEntry newDci = newDirCache.getDirCacheEntry();
				if (baseDci != null && newDci != null) {
					if (baseDci.isAssumeValid() != newDci.isAssumeValid())
						return true;
					if (baseDci.isAssumeValid()) // && newDci.isAssumeValid()
						return false;
				}
			}
			if (walker.getRawMode(i) != m || !walker.idEqual(i, baseTree))
				return true;
		}
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public boolean shouldBeRecursive() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public TreeFilter clone() {
		return this;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return "INTERINDEX_DIFF"; //$NON-NLS-1$
	}
}
