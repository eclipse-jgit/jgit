/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2007-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
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

import java.io.File;
import java.io.IOException;
import java.util.HashSet;

import org.eclipse.jgit.lib.GitIndex.Entry;

/**
 * Compares the Index, a Tree, and the working directory
 */
public class IndexDiff {
	private GitIndex index;
	private Tree tree;

	/**
	 * Construct an indexdiff for diffing the workdir against
	 * the index.
	 *
	 * @param repository
	 * @throws IOException
	 */
	public IndexDiff(Repository repository) throws IOException {
		this.tree = repository.mapTree(Constants.HEAD);
		this.index = repository.getIndex();
	}

	/**
	 * Construct an indexdiff for diffing the workdir against both
	 * the index and a tree.
	 *
	 * @param tree
	 * @param index
	 */
	public IndexDiff(Tree tree, GitIndex index) {
		this.tree = tree;
		this.index = index;
	}

	boolean anyChanges = false;

	/**
	 * Run the diff operation. Until this is called, all lists will be empty
	 * @return if anything is different between index, tree, and workdir
	 * @throws IOException
	 */
	public boolean diff() throws IOException {
		final File root = index.getRepository().getWorkDir();
		new IndexTreeWalker(index, tree, root, new AbstractIndexTreeVisitor() {
			public void visitEntry(TreeEntry treeEntry, Entry indexEntry, File file) {
				if (treeEntry == null) {
					added.add(indexEntry.getName());
					anyChanges = true;
				} else if (indexEntry == null) {
					if (!(treeEntry instanceof Tree))
						removed.add(treeEntry.getFullName());
					anyChanges = true;
				} else {
					if (!treeEntry.getId().equals(indexEntry.getObjectId())) {
						changed.add(indexEntry.getName());
						anyChanges = true;
					}
				}

				if (indexEntry != null) {
					if (!file.exists()) {
						missing.add(indexEntry.getName());
						anyChanges = true;
					} else {
						if (indexEntry.isModified(root, true)) {
							modified.add(indexEntry.getName());
							anyChanges = true;
						}
					}
				}
			}
		}).walk();

		return anyChanges;
	}

	HashSet<String> added = new HashSet<String>();
	HashSet<String> changed = new HashSet<String>();
	HashSet<String> removed = new HashSet<String>();
	HashSet<String> missing = new HashSet<String>();
	HashSet<String> modified = new HashSet<String>();

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
}
