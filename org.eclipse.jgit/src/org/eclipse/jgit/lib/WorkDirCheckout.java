/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Roger C. Soares <rogersoares@intelinet.com.br>
 * Copyright (C) 2006, Shawn O. Pearce <spearce@spearce.org>
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.jgit.errors.CheckoutConflictException;
import org.eclipse.jgit.lib.GitIndex.Entry;

/**
 * This class handles checking out one or two trees merging
 * with the index (actually a tree too).
 *
 * Three-way merges are no performed. See {@link #setFailOnConflict(boolean)}.
 */
public class WorkDirCheckout {
	Repository repo;

	File root;

	GitIndex index;

	private boolean failOnConflict = true;

	Tree merge;


	/**
	 * If <code>true</code>, will scan first to see if it's possible to check out,
	 * otherwise throw {@link CheckoutConflictException}. If <code>false</code>,
	 * it will silently deal with the problem.
	 * @param failOnConflict
	 */
	public void setFailOnConflict(boolean failOnConflict) {
		this.failOnConflict = failOnConflict;
	}

	WorkDirCheckout(Repository repo, File workDir,
			GitIndex oldIndex, GitIndex newIndex) throws IOException {
		this.repo = repo;
		this.root = workDir;
		this.index = oldIndex;
		this.merge = repo.mapTree(newIndex.writeTree());
	}

	/**
	 * Create a checkout class for checking out one tree, merging with the index
	 *
	 * @param repo
	 * @param root workdir
	 * @param index current index
	 * @param merge tree to check out
	 */
	public WorkDirCheckout(Repository repo, File root,
			GitIndex index, Tree merge) {
		this.repo = repo;
		this.root = root;
		this.index = index;
		this.merge = merge;
	}

	/**
	 * Create a checkout class for merging and checking our two trees and the index.
	 *
	 * @param repo
	 * @param root workdir
	 * @param head
	 * @param index
	 * @param merge
	 */
	public WorkDirCheckout(Repository repo, File root, Tree head, GitIndex index, Tree merge) {
		this(repo, root, index, merge);
		this.head = head;
	}

	/**
	 * Execute this checkout
	 *
	 * @throws IOException
	 */
	public void checkout() throws IOException {
		if (head == null)
			prescanOneTree();
		else prescanTwoTrees();
		if (!conflicts.isEmpty()) {
			if (failOnConflict) {
				String[] entries = conflicts.toArray(new String[0]);
				throw new CheckoutConflictException(entries);
			}
		}

		cleanUpConflicts();
		if (head == null)
			checkoutOutIndexNoHead();
		else checkoutTwoTrees();
	}

	private void checkoutTwoTrees() throws FileNotFoundException, IOException {
		for (String path : removed) {
			index.remove(root, new File(root, path));
		}

		for (java.util.Map.Entry<String, ObjectId> entry : updated.entrySet()) {
			Entry newEntry = index.addEntry(merge.findBlobMember(entry.getKey()));
			index.checkoutEntry(root, newEntry);
		}
	}

	ArrayList<String> conflicts  = new ArrayList<String>();
	ArrayList<String> removed = new ArrayList<String>();

	Tree head = null;

	HashMap<String, ObjectId> updated = new HashMap<String, ObjectId>();

	private void checkoutOutIndexNoHead() throws IOException {
		new IndexTreeWalker(index, merge, root, new AbstractIndexTreeVisitor() {
			public void visitEntry(TreeEntry m, Entry i, File f) throws IOException {
				if (m == null) {
					index.remove(root, f);
					return;
				}

				boolean needsCheckout = false;
				if (i == null)
					needsCheckout = true;
				else if (i.getObjectId().equals(m.getId())) {
					if (i.isModified(root, true))
						needsCheckout = true;
				} else needsCheckout = true;

				if (needsCheckout) {
					Entry newEntry = index.addEntry(m);
					index.checkoutEntry(root, newEntry);
				}
			}
		}).walk();
	}

	private void cleanUpConflicts() throws CheckoutConflictException {
		for (String c : conflicts) {
			File conflict = new File(root, c);
			if (!conflict.delete())
				throw new CheckoutConflictException("Cannot delete file: " + c);
			removeEmptyParents(conflict);
		}
		for (String r : removed) {
			File file = new File(root, r);
			file.delete();
			removeEmptyParents(file);
		}
	}

	private void removeEmptyParents(File f) {
		File parentFile = f.getParentFile();
		while (!parentFile.equals(root)) {
			if (parentFile.list().length == 0)
				parentFile.delete();
			else break;

			parentFile = parentFile.getParentFile();
		}
	}

	void prescanOneTree() throws IOException {
		new IndexTreeWalker(index, merge, root, new AbstractIndexTreeVisitor() {
			public void visitEntry(TreeEntry m, Entry i, File file) throws IOException {
				if (m != null) {
					if (!file.isFile()) {
						checkConflictsWithFile(file);
					}
				} else {
					if (file.exists())  {
						removed.add(i.getName());
						conflicts.remove(i.getName());
					}
				}
			}
		}).walk();
		conflicts.removeAll(removed);
	}

	private ArrayList<String> listFiles(File file) {
		ArrayList<String> list = new ArrayList<String>();
		listFiles(file, list);
		return list;
	}

	private void listFiles(File dir, ArrayList<String> list) {
		for (File f : dir.listFiles()) {
			if (f.isDirectory())
				listFiles(f, list);
			else {
				list.add(Repository.stripWorkDir(root, f));
			}
		}
	}

	/**
	 * @return a list of conflicts created by this checkout
	 */
	public ArrayList<String> getConflicts() {
		return conflicts;
	}

	/**
	 * @return a list of all files removed by this checkout
	 */
	public ArrayList<String> getRemoved() {
		return removed;
	}

	void prescanTwoTrees() throws IOException {
		new IndexTreeWalker(index, head, merge, root, new AbstractIndexTreeVisitor() {
			public void visitEntry(TreeEntry treeEntry, TreeEntry auxEntry,
					Entry indexEntry, File file) throws IOException {
				if (treeEntry instanceof Tree || auxEntry instanceof Tree) {
					throw new IllegalArgumentException("Can't pass me a tree!");
				}
				processEntry(treeEntry, auxEntry, indexEntry);
			}

			@Override
			public void finishVisitTree(Tree tree, Tree auxTree, String curDir) throws IOException {
				if (curDir.length() == 0) return;

				if (auxTree != null) {
					if (index.getEntry(curDir) != null)
						removed.add(curDir);
				}
			}

		}).walk();

		// if there's a conflict, don't list it under
		// to-be-removed, since that messed up our next
		// section
		removed.removeAll(conflicts);

		for (String path : updated.keySet()) {
			if (index.getEntry(path) == null) {
				File file = new File(root, path);
				if (file.isFile())
					conflicts.add(path);
				else if (file.isDirectory()) {
					checkConflictsWithFile(file);
				}
			}
		}


		conflicts.removeAll(removed);
	}

	void processEntry(TreeEntry h, TreeEntry m, Entry i) throws IOException {
				ObjectId iId = (i == null ? null : i.getObjectId());
				ObjectId mId = (m == null ? null : m.getId());
				ObjectId hId = (h == null ? null : h.getId());

				String name = (i != null ? i.getName() :
					(h != null ? h.getFullName() :
						m.getFullName()));

				if (i == null) {
					/*
				    I (index)                H        M        Result
			        -------------------------------------------------------
			        0 nothing             nothing  nothing  (does not happen)
			        1 nothing             nothing  exists   use M
			        2 nothing             exists   nothing  remove path from index
			        3 nothing             exists   exists   use M */

					if (h == null) {
						updated.put(name,mId);
					} else if (m == null) {
						removed.add(name);
					} else {
						updated.put(name, mId);
					}
				} else if (h == null) {
					/*
					  clean I==H  I==M       H        M        Result
			         -----------------------------------------------------
			        4 yes   N/A   N/A     nothing  nothing  keep index
			        5 no    N/A   N/A     nothing  nothing  keep index

			        6 yes   N/A   yes     nothing  exists   keep index
			        7 no    N/A   yes     nothing  exists   keep index
			        8 yes   N/A   no      nothing  exists   fail
			        9 no    N/A   no      nothing  exists   fail       */

					if (m == null || mId.equals(iId)) {
						if (hasParentBlob(merge, name)) {
							if (i.isModified(root, true)) {
								conflicts.add(name);
							} else {
								removed.add(name);
							}
						}
					} else {
						conflicts.add(name);
					}
				} else if (m == null) {
					/*
					10 yes   yes   N/A     exists   nothing  remove path from index
			        11 no    yes   N/A     exists   nothing  fail
			        12 yes   no    N/A     exists   nothing  fail
			        13 no    no    N/A     exists   nothing  fail
					 */

					if (hId.equals(iId)) {
						if (i.isModified(root, true)) {
							conflicts.add(name);
						} else {
							removed.add(name);
						}
					} else {
						conflicts.add(name);
					}
				} else {
					if (!hId.equals(mId) && !hId.equals(iId)
							&& !mId.equals(iId)) {
						conflicts.add(name);
					} else if (hId.equals(iId) && !mId.equals(iId)) {
						if (i.isModified(root, true))
							conflicts.add(name);
						else updated.put(name, mId);
					}
				}
			}

	private boolean hasParentBlob(Tree t, String name) throws IOException {
		if (name.indexOf("/") == -1) return false;

		String parent = name.substring(0, name.lastIndexOf("/"));
		if (t.findBlobMember(parent) != null)
			return true;
		return hasParentBlob(t, parent);
	}

	private void checkConflictsWithFile(File file) {
		if (file.isDirectory()) {
			ArrayList<String> childFiles = listFiles(file);
			conflicts.addAll(childFiles);
		} else {
			File parent = file.getParentFile();
			while (!parent.equals(root)) {
				if (parent.isDirectory())
					break;
				if (parent.isFile()) {
					conflicts.add(Repository.stripWorkDir(root, parent));
					break;
				}
				parent = parent.getParentFile();
			}
		}
	}
}
