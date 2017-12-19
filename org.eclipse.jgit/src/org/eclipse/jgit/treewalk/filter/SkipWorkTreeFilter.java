/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2010, Marc Strapetz <marc.strapetz@syntevo.com>
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
