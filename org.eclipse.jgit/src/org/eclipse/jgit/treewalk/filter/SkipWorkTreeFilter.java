/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2010, Marc Strapetz <marc.strapetz@syntevo.com> and others
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
 * To be used in combination with a DirCacheIterator: includes only tree entries
 * for which 'skipWorkTree' flag is not set.
 */
public class SkipWorkTreeFilter extends TreeFilter {

	/** Index of DirCacheIterator to work on. */
	private final int treeIdx;

	/**
	 * Create a filter to work on the specified DirCacheIterator.
	 *
	 * @param treeIdx
	 *            index of DirCacheIterator to work on. If the index does not
	 *            refer to a DirCacheIterator, the filter will include all
	 *            entries.
	 */
	public SkipWorkTreeFilter(int treeIdx) {
		this.treeIdx = treeIdx;
	}

	/** {@inheritDoc} */
	@Override
	public boolean include(TreeWalk walker) {
		DirCacheIterator i = walker.getTree(treeIdx, DirCacheIterator.class);
		if (i == null)
			return true;

		DirCacheEntry e = i.getDirCacheEntry();
		return e == null || !e.isSkipWorkTree();
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
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return "SkipWorkTree(" + treeIdx + ")";
	}
}
