/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
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

import java.io.IOException;

import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;

/**
 * A performance optimized variant of {@link TreeFilter#ANY_DIFF} which should
 * be used when among the walked trees there is a {@link DirCacheIterator} and a
 * {@link WorkingTreeIterator}. Please see the documentation of
 * {@link TreeFilter#ANY_DIFF} for a basic description of the semantics.
 * <p>
 * This filter tries to avoid computing content ids of the files in the
 * working-tree. In contrast to {@link TreeFilter#ANY_DIFF} this filter takes
 * care to first compare the entry from the {@link DirCacheIterator} with the
 * entries from all other iterators besides the {@link WorkingTreeIterator}.
 * Since all those entries have fast access to content ids that is very fast. If
 * a difference is detected in this step this filter decides to include that
 * path before even looking at the working-tree entry.
 * <p>
 * If no difference is found then we have to compare index and working-tree as
 * the last step. By making use of
 * {@link WorkingTreeIterator#isModified(org.eclipse.jgit.dircache.DirCacheEntry, boolean, boolean)}
 * we can avoid the computation of the content id if the file is not dirty.
 * <p>
 * Instances of this filter should not be used for multiple {@link TreeWalk}s.
 * Always construct a new instance of this filter for each TreeWalk.
 */
public class IndexWorkingTreeDiff extends TreeFilter {
	private final int dirCacheIndex;

	private final int workingTreeIndex;

	/**
	 * Creates a new instance of this filter. Do not use an instance of this
	 * filter in multiple treewalks.
	 *
	 * @param dirCacheIndex
	 *            the index of the {@link DirCacheIterator} in the associated
	 *            treewalk
	 * @param workingTreeIndex
	 *            the index of the {@link WorkingTreeIterator} in the associated
	 *            treewalk
	 */
	public IndexWorkingTreeDiff(int dirCacheIndex, int workingTreeIndex) {
		this.dirCacheIndex = dirCacheIndex;
		this.workingTreeIndex = workingTreeIndex;
	}

	@Override
	public boolean include(TreeWalk walker) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		final int n = walker.getTreeCount();

		// Try the inexpensive comparisons between index and all real trees
		// first. Only if we don't find a diff here we have to bother with
		// the working tree
		final int m = walker.getRawMode(dirCacheIndex);
		for (int i = 0; i < n; i++)
			if (i != dirCacheIndex
					&& i != workingTreeIndex
					&& (walker.getRawMode(i) != m || !walker.idEqual(
							dirCacheIndex, i)))
				return true;

		if (m == 0) // no entry for this path in index
			// we have already checked that the index filemode is equal
			// to the filemodes of all other trees besides the working
			// tree. If additionally the index filemode is MISSING then
			// we know that the worktree filemode differs. If it wouldn't
			// differ all filemodes of all trees would be MISSING - and
			// this can't be. Means: we found a diff!
			return true;
		final int wm = walker.getRawMode(workingTreeIndex);
		if (wm == 0) // no entry for this path in working-tree
			return true;

		// Only one chance left to detect a diff: between index and working
		// tree.
		// Make use of the WorkingTreeIterator#isModified() method to avoid
		// computing SHA1 on filesystem content if not really needed.
		WorkingTreeIterator work = walker.getTree(workingTreeIndex, WorkingTreeIterator.class);
		DirCacheIterator dircache = walker.getTree(dirCacheIndex, DirCacheIterator.class);
		return work.isModified(dircache.getDirCacheEntry(), false, false);
	}

	@Override
	public boolean shouldBeRecursive() {
		return false;
	}

	@Override
	public TreeFilter clone() {
		return this;
	}

	@Override
	public String toString() {
		return "INDEX_WORKINGTREE_DIFF";
	}
}
