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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectReader;
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
 * {@link WorkingTreeIterator#isModified(org.eclipse.jgit.dircache.DirCacheEntry, boolean, ObjectReader)}
 * we can avoid the computation of the content id if the file is not dirty.
 * <p>
 * Instances of this filter should not be used for multiple {@link TreeWalk}s.
 * Always construct a new instance of this filter for each TreeWalk.
 */
public class IndexDiffFilter extends TreeFilter {
	private final int dirCache;

	private final int workingTree;

	private final boolean honorIgnores;

	private final Set<String> ignoredPaths = new HashSet<String>();

	private final LinkedList<String> untrackedParentFolders = new LinkedList<String>();

	private final LinkedList<String> untrackedFolders = new LinkedList<String>();

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
	public IndexDiffFilter(int dirCacheIndex, int workingTreeIndex) {
		this(dirCacheIndex, workingTreeIndex, true /* honor ignores */);
	}

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
	 * @param honorIgnores
	 *            true if the filter should skip working tree files that are
	 *            declared as ignored by the standard exclude mechanisms..
	 */
	public IndexDiffFilter(int dirCacheIndex, int workingTreeIndex,
			boolean honorIgnores) {
		this.dirCache = dirCacheIndex;
		this.workingTree = workingTreeIndex;
		this.honorIgnores = honorIgnores;
	}

	@Override
	public boolean include(TreeWalk tw) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		final int cnt = tw.getTreeCount();
		final int wm = tw.getRawMode(workingTree);
		WorkingTreeIterator wi = workingTree(tw);
		String path = tw.getPathString();

		DirCacheIterator di = tw.getTree(dirCache, DirCacheIterator.class);
		if (di != null) {
			DirCacheEntry dce = di.getDirCacheEntry();
			if (dce != null)
				if (dce.isAssumeValid())
					return false;
		}

		if (!tw.isPostOrderTraversal()) {
			// detect untracked Folders
			// Whenever we enter a folder in the workingtree assume it will
			// contain only untracked files and add it to
			// untrackedParentFolders. If we later find tracked files we will
			// remove it from this list
			if (FileMode.TREE.equals(wm)
					&& !(honorIgnores && wi.isEntryIgnored())) {
				// Clean untrackedParentFolders. This potentially moves entries
				// from untrackedParentFolders to untrackedFolders
				copyUntrackedFolders(path);
				// add the folder we just entered to untrackedParentFolders
				untrackedParentFolders.addFirst(path);
			}

			// detect untracked Folders
			// Whenever we see a tracked file we know that all of its parent
			// folders do not belong into untrackedParentFolders anymore. Clean
			// it.
			for (int i = 0; i < cnt; i++) {
				int rmode = tw.getRawMode(i);
				if (i != workingTree && rmode != FileMode.TYPE_MISSING
						&& FileMode.TREE.equals(rmode)) {
					untrackedParentFolders.clear();
					break;
				}
			}
		}

		// If the working tree file doesn't exist, it does exist for at least
		// one other so include this difference.
		if (wm == 0)
			return true;

		// If the path does not appear in the DirCache and its ignored
		// we can avoid returning a result here, but only if its not in any
		// other tree.
		final int dm = tw.getRawMode(dirCache);
		if (dm == FileMode.TYPE_MISSING) {
			if (honorIgnores && wi.isEntryIgnored()) {
				ignoredPaths.add(wi.getEntryPathString());
				int i = 0;
				for (; i < cnt; i++) {
					if (i == dirCache || i == workingTree)
						continue;
					if (tw.getRawMode(i) != FileMode.TYPE_MISSING)
						break;
				}

				// If i is cnt then the path does not appear in any other tree,
				// and this working tree entry can be safely ignored.
				return i == cnt ? false : true;
			} else {
				// In working tree and not ignored, and not in DirCache.
				return true;
			}
		}

		// Always include subtrees as WorkingTreeIterator cannot provide
		// efficient elimination of unmodified subtrees.
		if (tw.isSubtree())
			return true;

		// Try the inexpensive comparisons between index and all real trees
		// first. Only if we don't find a diff here we have to bother with
		// the working tree
		for (int i = 0; i < cnt; i++) {
			if (i == dirCache || i == workingTree)
				continue;
			if (tw.getRawMode(i) != dm || !tw.idEqual(i, dirCache))
				return true;
		}

		// Only one chance left to detect a diff: between index and working
		// tree. Make use of the WorkingTreeIterator#isModified() method to
		// avoid computing SHA1 on filesystem content if not really needed.
		return wi.isModified(di.getDirCacheEntry(), true, tw.getObjectReader());
	}

	/**
	 * Copy all entries which are still in untrackedParentFolders and which
	 * belong to a path this treewalk has left into untrackedFolders. It is sure
	 * that we will not find any tracked files underneath these paths. Therefore
	 * these paths definitely belong to untracked folders.
	 *
	 * @param currentPath
	 *            the current path of the treewalk
	 */
	private void copyUntrackedFolders(String currentPath) {
		String pathToBeSaved = null;
		while (!untrackedParentFolders.isEmpty()
				&& !currentPath.startsWith(untrackedParentFolders.getFirst()
						+ "/")) //$NON-NLS-1$
			pathToBeSaved = untrackedParentFolders.removeFirst();
		if (pathToBeSaved != null) {
			while (!untrackedFolders.isEmpty()
					&& untrackedFolders.getLast().startsWith(pathToBeSaved))
				untrackedFolders.removeLast();
			untrackedFolders.addLast(pathToBeSaved);
		}
	}

	private WorkingTreeIterator workingTree(TreeWalk tw) {
		return tw.getTree(workingTree, WorkingTreeIterator.class);
	}

	@Override
	public boolean shouldBeRecursive() {
		// We cannot compare subtrees in the working tree, so encourage
		// use of recursive walks where the subtrees are always dived into.
		return true;
	}

	@Override
	public TreeFilter clone() {
		return this;
	}

	@Override
	public String toString() {
		return "INDEX_DIFF_FILTER"; //$NON-NLS-1$
	}

	/**
	 * The method returns the list of ignored files and folders. Only the root
	 * folder of an ignored folder hierarchy is reported. If a/b/c is listed in
	 * the .gitignore then you should not expect a/b/c/d/e/f to be reported
	 * here. Only a/b/c will be reported. Furthermore only ignored files /
	 * folders are returned that are NOT in the index.
	 *
	 * @return ignored paths
	 */
	public Set<String> getIgnoredPaths() {
		return ignoredPaths;
	}

	/**
	 * @return all paths of folders which contain only untracked files/folders.
	 *         If on the associated treewalk postorder traversal was turned on
	 *         (see {@link TreeWalk#setPostOrderTraversal(boolean)}) then an
	 *         empty list will be returned.
	 */
	public List<String> getUntrackedFolders() {
		LinkedList<String> ret = new LinkedList<String>(untrackedFolders);
		if (!untrackedParentFolders.isEmpty()) {
			String toBeAdded = untrackedParentFolders.getLast();
			while (!ret.isEmpty() && ret.getLast().startsWith(toBeAdded))
				ret.removeLast();
			ret.addLast(toBeAdded);
		}
		return ret;
	}
}
