/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2007-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2010, Jens Baumgart <jens.baumgart@sap.com>
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

package org.eclipse.jgit.lib;

import java.io.IOException;
import java.util.HashSet;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.NotIgnoredFilter;
import org.eclipse.jgit.treewalk.filter.SkipWorkTreeFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * Compares the index, a tree, and the working directory
 * Ignored files are not taken into account.
 * The following information is retrieved:
 * <li> added files
 * <li> changed files
 * <li> removed files
 * <li> missing files
 * <li> modified files
 * <li> untracked files
 */
public class IndexDiff {

	private final static int TREE = 0;

	private final static int INDEX = 1;

	private final static int WORKDIR = 2;

	private final Repository repository;

	private final RevTree tree;

	private final WorkingTreeIterator initialWorkingTreeIterator;

	private HashSet<String> added = new HashSet<String>();

	private HashSet<String> changed = new HashSet<String>();

	private HashSet<String> removed = new HashSet<String>();

	private HashSet<String> missing = new HashSet<String>();

	private HashSet<String> modified = new HashSet<String>();

	private HashSet<String> untracked = new HashSet<String>();

	/**
	 * Construct an IndexDiff
	 *
	 * @param repository
	 * @param revstr
	 *            symbolic name e.g. HEAD
	 *            An EmptyTreeIterator is used if <code>revstr</code> cannot be resolved.
	 * @param workingTreeIterator
	 *            iterator for working directory
	 * @throws IOException
	 */
	public IndexDiff(Repository repository, String revstr,
			WorkingTreeIterator workingTreeIterator) throws IOException {
		this.repository = repository;
		ObjectId objectId = repository.resolve(revstr);
		if (objectId != null)
			tree = new RevWalk(repository).parseTree(objectId);
		else
			tree = null;
		this.initialWorkingTreeIterator = workingTreeIterator;
	}

	/**
	 * Construct an Indexdiff
	 *
	 * @param repository
	 * @param objectId
	 *            tree id. If null, an EmptyTreeIterator is used.
	 * @param workingTreeIterator
	 *            iterator for working directory
	 * @throws IOException
	 */
	public IndexDiff(Repository repository, ObjectId objectId,
			WorkingTreeIterator workingTreeIterator) throws IOException {
		this.repository = repository;
		if (objectId != null)
			tree = new RevWalk(repository).parseTree(objectId);
		else
			tree = null;
		this.initialWorkingTreeIterator = workingTreeIterator;
	}


	/**
	 * Run the diff operation. Until this is called, all lists will be empty
	 *
	 * @return if anything is different between index, tree, and workdir
	 * @throws IOException
	 */
	public boolean diff() throws IOException {
		boolean changesExist = false;
		DirCache dirCache = repository.readDirCache();
		TreeWalk treeWalk = new TreeWalk(repository);
		treeWalk.reset();
		treeWalk.setRecursive(true);
		// add the trees (tree, dirchache, workdir)
		if (tree != null)
			treeWalk.addTree(tree);
		else
			treeWalk.addTree(new EmptyTreeIterator());
		treeWalk.addTree(new DirCacheIterator(dirCache));
		treeWalk.addTree(initialWorkingTreeIterator);
		treeWalk.setFilter(TreeFilter.ANY_DIFF);
		treeWalk.setFilter(AndTreeFilter.create(new TreeFilter[] {
				new NotIgnoredFilter(WORKDIR), new SkipWorkTreeFilter(INDEX),
				TreeFilter.ANY_DIFF }));
		while (treeWalk.next()) {
			AbstractTreeIterator treeIterator = treeWalk.getTree(TREE,
					AbstractTreeIterator.class);
			DirCacheIterator dirCacheIterator = treeWalk.getTree(INDEX,
					DirCacheIterator.class);
			WorkingTreeIterator workingTreeIterator = treeWalk.getTree(WORKDIR,
					WorkingTreeIterator.class);
			FileMode fileModeTree = treeWalk.getFileMode(TREE);

			if (treeIterator != null) {
				if (dirCacheIterator != null) {
					if (!treeIterator.getEntryObjectId().equals(
							dirCacheIterator.getEntryObjectId())) {
						// in repo, in index, content diff => changed
						changed.add(dirCacheIterator.getEntryPathString());
						changesExist = true;
					}
				} else {
					// in repo, not in index => removed
					if (!fileModeTree.equals(FileMode.TYPE_TREE)) {
						removed.add(treeIterator.getEntryPathString());
						changesExist = true;
					}
				}
			} else {
				if (dirCacheIterator != null) {
					// not in repo, in index => added
					added.add(dirCacheIterator.getEntryPathString());
					changesExist = true;
				} else {
					// not in repo, not in index => untracked
					if (workingTreeIterator != null
							&& !workingTreeIterator.isEntryIgnored()) {
						untracked.add(workingTreeIterator.getEntryPathString());
						changesExist = true;
					}
				}
			}

			if (dirCacheIterator != null) {
				if (workingTreeIterator == null) {
					// in index, not in workdir => missing
					missing.add(dirCacheIterator.getEntryPathString());
					changesExist = true;
				} else {
					if (!dirCacheIterator.idEqual(workingTreeIterator)) {
						// in index, in workdir, content differs => modified
						modified.add(dirCacheIterator.getEntryPathString());
						changesExist = true;
					}
				}
			}
		}
		return changesExist;
	}

	/**
	 * @return list of files added to the index, not in the tree
	 */
	public HashSet<String> getAdded() {
		return added;
	}

	/**
	 * @return list of files changed from tree to index
	 */
	public HashSet<String> getChanged() {
		return changed;
	}

	/**
	 * @return list of files removed from index, but in tree
	 */
	public HashSet<String> getRemoved() {
		return removed;
	}

	/**
	 * @return list of files in index, but not filesystem
	 */
	public HashSet<String> getMissing() {
		return missing;
	}

	/**
	 * @return list of files on modified on disk relative to the index
	 */
	public HashSet<String> getModified() {
		return modified;
	}

	/**
	 * @return list of files on modified on disk relative to the index
	 */
	public HashSet<String> getUntracked() {
		return untracked;
	}

}
