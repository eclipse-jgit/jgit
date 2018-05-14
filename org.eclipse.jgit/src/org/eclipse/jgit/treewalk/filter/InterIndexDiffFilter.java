/*
 * Copyright (C) 2013, Robin Rosenberg <robin.rosenberg@dewire.com>
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
