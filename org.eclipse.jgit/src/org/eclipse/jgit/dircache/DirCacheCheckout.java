/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Roger C. Soares <rogersoares@intelinet.com.br>
 * Copyright (C) 2006, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2010, Chrisian Halstrick <christian.halstrick@sap.com> and
 * other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v1.0 which accompanies this
 * distribution, is reproduced below, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.dircache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.errors.CheckoutConflictException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.IndexWriteException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig.AutoCRLF;
import org.eclipse.jgit.lib.CoreConfig.SymLinks;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.NameConflictTreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.SystemReader;
import org.eclipse.jgit.util.io.AutoCRLFOutputStream;

/**
 * This class handles checking out one or two trees merging with the index.
 */
public class DirCacheCheckout {
	private Repository repo;

	private HashMap<String, ObjectId> updated = new HashMap<String, ObjectId>();

	private ArrayList<String> conflicts = new ArrayList<String>();

	private ArrayList<String> removed = new ArrayList<String>();

	private ObjectId mergeCommitTree;

	private DirCache dc;

	private DirCacheBuilder builder;

	private NameConflictTreeWalk walk;

	private ObjectId headCommitTree;

	private WorkingTreeIterator workingTree;

	private boolean failOnConflict = true;

	private ArrayList<String> toBeDeleted = new ArrayList<String>();

	/**
	 * @return a list of updated paths and objectIds
	 */
	public Map<String, ObjectId> getUpdated() {
		return updated;
	}

	/**
	 * @return a list of conflicts created by this checkout
	 */
	public List<String> getConflicts() {
		return conflicts;
	}

	/**
	 * @return a list of paths (relative to the start of the working tree) of
	 *         files which couldn't be deleted during last call to
	 *         {@link #checkout()} . {@link #checkout()} detected that these
	 *         files should be deleted but the deletion in the filesystem failed
	 *         (e.g. because a file was locked). To have a consistent state of
	 *         the working tree these files have to be deleted by the callers of
	 *         {@link DirCacheCheckout}.
	 */
	public List<String> getToBeDeleted() {
		return toBeDeleted;
	}

	/**
	 * @return a list of all files removed by this checkout
	 */
	public List<String> getRemoved() {
		return removed;
	}

	/**
	 * Constructs a DirCacheCeckout for merging and checking out two trees (HEAD
	 * and mergeCommitTree) and the index.
	 *
	 * @param repo
	 *            the repository in which we do the checkout
	 * @param headCommitTree
	 *            the id of the tree of the head commit
	 * @param dc
	 *            the (already locked) Dircache for this repo
	 * @param mergeCommitTree
	 *            the id of the tree we want to fast-forward to
	 * @param workingTree
	 *            an iterator over the repositories Working Tree
	 * @throws IOException
	 */
	public DirCacheCheckout(Repository repo, ObjectId headCommitTree, DirCache dc,
			ObjectId mergeCommitTree, WorkingTreeIterator workingTree)
			throws IOException {
		this.repo = repo;
		this.dc = dc;
		this.headCommitTree = headCommitTree;
		this.mergeCommitTree = mergeCommitTree;
		this.workingTree = workingTree;
	}

	/**
	 * Constructs a DirCacheCeckout for merging and checking out two trees (HEAD
	 * and mergeCommitTree) and the index. As iterator over the working tree
	 * this constructor creates a standard {@link FileTreeIterator}
	 *
	 * @param repo
	 *            the repository in which we do the checkout
	 * @param headCommitTree
	 *            the id of the tree of the head commit
	 * @param dc
	 *            the (already locked) Dircache for this repo
	 * @param mergeCommitTree
	 *            the id of the tree we want to fast-forward to
	 * @throws IOException
	 */
	public DirCacheCheckout(Repository repo, ObjectId headCommitTree,
			DirCache dc, ObjectId mergeCommitTree) throws IOException {
		this(repo, headCommitTree, dc, mergeCommitTree, new FileTreeIterator(repo));
	}

	/**
	 * Constructs a DirCacheCeckout for checking out one tree, merging with the
	 * index.
	 *
	 * @param repo
	 *            the repository in which we do the checkout
	 * @param dc
	 *            the (already locked) Dircache for this repo
	 * @param mergeCommitTree
	 *            the id of the tree we want to fast-forward to
	 * @param workingTree
	 *            an iterator over the repositories Working Tree
	 * @throws IOException
	 */
	public DirCacheCheckout(Repository repo, DirCache dc,
			ObjectId mergeCommitTree, WorkingTreeIterator workingTree)
			throws IOException {
		this(repo, null, dc, mergeCommitTree, workingTree);
	}

	/**
	 * Constructs a DirCacheCeckout for checking out one tree, merging with the
	 * index. As iterator over the working tree this constructor creates a
	 * standard {@link FileTreeIterator}
	 *
	 * @param repo
	 *            the repository in which we do the checkout
	 * @param dc
	 *            the (already locked) Dircache for this repo
	 * @param mergeCommitTree
	 *            the id of the tree of the
	 * @throws IOException
	 */
	public DirCacheCheckout(Repository repo, DirCache dc,
			ObjectId mergeCommitTree) throws IOException {
		this(repo, null, dc, mergeCommitTree, new FileTreeIterator(repo));
	}

	/**
	 * Scan head, index and merge tree. Used during normal checkout or merge
	 * operations.
	 *
	 * @throws CorruptObjectException
	 * @throws IOException
	 */
	public void preScanTwoTrees() throws CorruptObjectException, IOException {
		removed.clear();
		updated.clear();
		conflicts.clear();
		walk = new NameConflictTreeWalk(repo);
		builder = dc.builder();

		addTree(walk, headCommitTree);
		addTree(walk, mergeCommitTree);
		walk.addTree(new DirCacheBuildIterator(builder));
		walk.addTree(workingTree);

		while (walk.next()) {
			processEntry(walk.getTree(0, CanonicalTreeParser.class),
					walk.getTree(1, CanonicalTreeParser.class),
					walk.getTree(2, DirCacheBuildIterator.class),
					walk.getTree(3, WorkingTreeIterator.class));
			if (walk.isSubtree())
				walk.enterSubtree();
		}
	}

	private void addTree(TreeWalk tw, ObjectId id) throws MissingObjectException, IncorrectObjectTypeException, IOException {
		if (id == null)
			tw.addTree(new EmptyTreeIterator());
		else
			tw.addTree(id);
	}

	/**
	 * Scan index and merge tree (no HEAD). Used e.g. for initial checkout when
	 * there is no head yet.
	 *
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws CorruptObjectException
	 * @throws IOException
	 */
	public void prescanOneTree()
			throws MissingObjectException, IncorrectObjectTypeException,
			CorruptObjectException, IOException {
		removed.clear();
		updated.clear();
		conflicts.clear();

		builder = dc.builder();

		walk = new NameConflictTreeWalk(repo);
		addTree(walk, mergeCommitTree);
		walk.addTree(new DirCacheBuildIterator(builder));
		walk.addTree(workingTree);

		while (walk.next()) {
			processEntry(walk.getTree(0, CanonicalTreeParser.class),
					walk.getTree(1, DirCacheBuildIterator.class),
					walk.getTree(2, WorkingTreeIterator.class));
			if (walk.isSubtree())
				walk.enterSubtree();
		}
		conflicts.removeAll(removed);
	}

	/**
	 * Processing an entry in the context of {@link #prescanOneTree()} when only
	 * one tree is given
	 *
	 * @param m the tree to merge
	 * @param i the index
	 * @param f the working tree
	 * @throws IOException
	 */
	void processEntry(CanonicalTreeParser m, DirCacheBuildIterator i,
			WorkingTreeIterator f) throws IOException {
		if (m != null) {
			checkValidPath(m);
			// There is an entry in the merge commit. Means: we want to update
			// what's currently in the index and working-tree to that one
			if (i == null) {
				// The index entry is missing
				if (f != null && !FileMode.TREE.equals(f.getEntryFileMode())
						&& !f.isEntryIgnored()) {
					// don't overwrite an untracked and not ignored file
					conflicts.add(walk.getPathString());
				} else
					update(m.getEntryPathString(), m.getEntryObjectId(),
						m.getEntryFileMode());
			} else if (f == null || !m.idEqual(i)) {
				// The working tree file is missing or the merge content differs
				// from index content
				update(m.getEntryPathString(), m.getEntryObjectId(),
						m.getEntryFileMode());
			} else if (i.getDirCacheEntry() != null) {
				// The index contains a file (and not a folder)
				if (f.isModified(i.getDirCacheEntry(), true,
						this.walk.getObjectReader())
						|| i.getDirCacheEntry().getStage() != 0)
					// The working tree file is dirty or the index contains a
					// conflict
					update(m.getEntryPathString(), m.getEntryObjectId(),
							m.getEntryFileMode());
				else {
					// update the timestamp of the index with the one from the
					// file if not set, as we are sure to be in sync here.
					DirCacheEntry entry = i.getDirCacheEntry();
					if (entry.getLastModified() == 0)
						entry.setLastModified(f.getEntryLastModified());
					keep(entry);
				}
			} else
				// The index contains a folder
				keep(i.getDirCacheEntry());
		} else {
			// There is no entry in the merge commit. Means: we want to delete
			// what's currently in the index and working tree
			if (f != null) {
				// There is a file/folder for that path in the working tree
				if (walk.isDirectoryFileConflict()) {
					conflicts.add(walk.getPathString());
				} else {
					// No file/folder conflict exists. All entries are files or
					// all entries are folders
					if (i != null) {
						// ... and the working tree contained a file or folder
						// -> add it to the removed set and remove it from
						// conflicts set
						remove(i.getEntryPathString());
						conflicts.remove(i.getEntryPathString());
					} else {
						// untracked file, neither contained in tree to merge
						// nor in index
					}
				}
			} else {
				// There is no file/folder for that path in the working tree,
				// nor in the merge head.
				// The only entry we have is the index entry. Like the case
				// where there is a file with the same name, remove it,
			}
		}
	}

	/**
	 * Execute this checkout
	 *
	 * @return <code>false</code> if this method could not delete all the files
	 *         which should be deleted (e.g. because of of the files was
	 *         locked). In this case {@link #getToBeDeleted()} lists the files
	 *         which should be tried to be deleted outside of this method.
	 *         Although <code>false</code> is returned the checkout was
	 *         successful and the working tree was updated for all other files.
	 *         <code>true</code> is returned when no such problem occurred
	 *
	 * @throws IOException
	 */
	public boolean checkout() throws IOException {
		try {
			return doCheckout();
		} finally {
			dc.unlock();
		}
	}

	private boolean doCheckout() throws CorruptObjectException, IOException,
			MissingObjectException, IncorrectObjectTypeException,
			CheckoutConflictException, IndexWriteException {
		toBeDeleted.clear();
		ObjectReader objectReader = repo.getObjectDatabase().newReader();
		try {
			if (headCommitTree != null)
				preScanTwoTrees();
			else
				prescanOneTree();

			if (!conflicts.isEmpty()) {
				if (failOnConflict)
					throw new CheckoutConflictException(conflicts.toArray(new String[conflicts.size()]));
				else
					cleanUpConflicts();
			}

			// update our index
			builder.finish();

			File file = null;
			String last = null;
			// when deleting files process them in the opposite order as they have
			// been reported. This ensures the files are deleted before we delete
			// their parent folders
			for (int i = removed.size() - 1; i >= 0; i--) {
				String r = removed.get(i);
				file = new File(repo.getWorkTree(), r);
				if (!file.delete() && repo.getFS().exists(file)) {
					// The list of stuff to delete comes from the index
					// which will only contain a directory if it is
					// a submodule, in which case we shall not attempt
					// to delete it. A submodule is not empty, so it
					// is safe to check this after a failed delete.
					if (!repo.getFS().isDirectory(file))
						toBeDeleted.add(r);
				} else {
					if (last != null && !isSamePrefix(r, last))
						removeEmptyParents(new File(repo.getWorkTree(), last));
					last = r;
				}
			}
			if (file != null)
				removeEmptyParents(file);

			for (String path : updated.keySet()) {
				// ... create/overwrite this file ...
				file = new File(repo.getWorkTree(), path);
				if (!file.getParentFile().mkdirs()) {
					// ignore
				}

				DirCacheEntry entry = dc.getEntry(path);

				// submodules are handled with separate operations
				if (FileMode.GITLINK.equals(entry.getRawMode()))
					continue;

				checkoutEntry(repo, file, entry, objectReader);
			}

			// commit the index builder - a new index is persisted
			if (!builder.commit())
				throw new IndexWriteException();
		} finally {
			objectReader.release();
		}
		return toBeDeleted.size() == 0;
	}

	private static boolean isSamePrefix(String a, String b) {
		int as = a.lastIndexOf('/');
		int bs = b.lastIndexOf('/');
		return a.substring(0, as + 1).equals(b.substring(0, bs + 1));
	}

	 private void removeEmptyParents(File f) {
		File parentFile = f.getParentFile();

		while (parentFile != null && !parentFile.equals(repo.getWorkTree())) {
			if (!parentFile.delete())
				break;
			parentFile = parentFile.getParentFile();
		}
	}

	/**
	 * Compares whether two pairs of ObjectId and FileMode are equal.
	 *
	 * @param id1
	 * @param mode1
	 * @param id2
	 * @param mode2
	 * @return <code>true</code> if FileModes and ObjectIds are equal.
	 *         <code>false</code> otherwise
	 */
	private boolean equalIdAndMode(ObjectId id1, FileMode mode1, ObjectId id2,
			FileMode mode2) {
		if (!mode1.equals(mode2))
			return false;
		return id1 != null ? id1.equals(id2) : id2 == null;
	}

	/**
	 * Here the main work is done. This method is called for each existing path
	 * in head, index and merge. This method decides what to do with the
	 * corresponding index entry: keep it, update it, remove it or mark a
	 * conflict.
	 *
	 * @param h
	 *            the entry for the head
	 * @param m
	 *            the entry for the merge
	 * @param i
	 *            the entry for the index
	 * @param f
	 *            the file in the working tree
	 * @throws IOException
	 */

	void processEntry(CanonicalTreeParser h, CanonicalTreeParser m,
			DirCacheBuildIterator i, WorkingTreeIterator f) throws IOException {
		DirCacheEntry dce = i != null ? i.getDirCacheEntry() : null;

		String name = walk.getPathString();

		if (m != null)
			checkValidPath(m);

		if (i == null && m == null && h == null) {
			// File/Directory conflict case #20
			if (walk.isDirectoryFileConflict())
				// TODO: check whether it is always correct to report a conflict here
				conflict(name, null, null, null);

			// file only exists in working tree -> ignore it
			return;
		}

		ObjectId iId = (i == null ? null : i.getEntryObjectId());
		ObjectId mId = (m == null ? null : m.getEntryObjectId());
		ObjectId hId = (h == null ? null : h.getEntryObjectId());
		FileMode iMode = (i == null ? null : i.getEntryFileMode());
		FileMode mMode = (m == null ? null : m.getEntryFileMode());
		FileMode hMode = (h == null ? null : h.getEntryFileMode());

		/**
		 * <pre>
		 *  File/Directory conflicts:
		 *  the following table from ReadTreeTest tells what to do in case of directory/file
		 *  conflicts. I give comments here
		 *
		 *      H        I       M     Clean     H==M     H==I    I==M         Result
		 *      ------------------------------------------------------------------
		 * 1    D        D       F       Y         N       Y       N           Update
		 * 2    D        D       F       N         N       Y       N           Conflict
		 * 3    D        F       D                 Y       N       N           Keep
		 * 4    D        F       D                 N       N       N           Conflict
		 * 5    D        F       F       Y         N       N       Y           Keep
		 * 5b   D        F       F       Y         N       N       N           Conflict
		 * 6    D        F       F       N         N       N       Y           Keep
		 * 6b   D        F       F       N         N       N       N           Conflict
		 * 7    F        D       F       Y         Y       N       N           Update
		 * 8    F        D       F       N         Y       N       N           Conflict
		 * 9    F        D       F       Y         N       N       N           Update
		 * 10   F        D       D                 N       N       Y           Keep
		 * 11   F        D       D                 N       N       N           Conflict
		 * 12   F        F       D       Y         N       Y       N           Update
		 * 13   F        F       D       N         N       Y       N           Conflict
		 * 14   F        F       D                 N       N       N           Conflict
		 * 15   0        F       D                 N       N       N           Conflict
		 * 16   0        D       F       Y         N       N       N           Update
		 * 17   0        D       F                 N       N       N           Conflict
		 * 18   F        0       D                                             Update
		 * 19   D        0       F                                             Update
		 * 20   0        0       F       N (worktree=dir)                      Conflict
		 * </pre>
		 */

		// The information whether head,index,merge iterators are currently
		// pointing to file/folder/non-existing is encoded into this variable.
		//
		// To decode write down ffMask in hexadecimal form. The last digit
		// represents the state for the merge iterator, the second last the
		// state for the index iterator and the third last represents the state
		// for the head iterator. The hexadecimal constant "F" stands for
		// "file", a "D" stands for "directory" (tree), and a "0" stands for
		// non-existing. Symbolic links and git links are treated as File here.
		//
		// Examples:
		// ffMask == 0xFFD -> Head=File, Index=File, Merge=Tree
		// ffMask == 0xDD0 -> Head=Tree, Index=Tree, Merge=Non-Existing

		int ffMask = 0;
		if (h != null)
			ffMask = FileMode.TREE.equals(hMode) ? 0xD00 : 0xF00;
		if (i != null)
			ffMask |= FileMode.TREE.equals(iMode) ? 0x0D0 : 0x0F0;
		if (m != null)
			ffMask |= FileMode.TREE.equals(mMode) ? 0x00D : 0x00F;

		// Check whether we have a possible file/folder conflict. Therefore we
		// need a least one file and one folder.
		if (((ffMask & 0x222) != 0x000)
				&& (((ffMask & 0x00F) == 0x00D) || ((ffMask & 0x0F0) == 0x0D0) || ((ffMask & 0xF00) == 0xD00))) {

			// There are 3*3*3=27 possible combinations of file/folder
			// conflicts. Some of them are not-relevant because
			// they represent no conflict, e.g. 0xFFF, 0xDDD, ... The following
			// switch processes all relevant cases.
			switch (ffMask) {
			case 0xDDF: // 1 2
				if (isModified(name)) {
					conflict(name, dce, h, m); // 1
				} else {
					update(name, mId, mMode); // 2
				}

				break;
			case 0xDFD: // 3 4
				keep(dce);
				break;
			case 0xF0D: // 18
				remove(name);
				break;
			case 0xDFF: // 5 5b 6 6b
				if (equalIdAndMode(iId, iMode, mId, mMode))
					keep(dce); // 5 6
				else
					conflict(name, dce, h, m); // 5b 6b
				break;
			case 0xFDD: // 10 11
				// TODO: make use of tree extension as soon as available in jgit
				// we would like to do something like
				// if (!equalIdAndMode(iId, iMode, mId, mMode)
				//   conflict(name, i.getDirCacheEntry(), h, m);
				// But since we don't know the id of a tree in the index we do
				// nothing here and wait that conflicts between index and merge
				// are found later
				break;
			case 0xD0F: // 19
				update(name, mId, mMode);
				break;
			case 0xDF0: // conflict without a rule
			case 0x0FD: // 15
				conflict(name, dce, h, m);
				break;
			case 0xFDF: // 7 8 9
				if (equalIdAndMode(hId, hMode, mId, mMode)) {
					if (isModified(name))
						conflict(name, dce, h, m); // 8
					else
						update(name, mId, mMode); // 7
				} else if (!isModified(name))
					update(name, mId, mMode); // 9
				else
					// To be confirmed - this case is not in the table.
					conflict(name, dce, h, m);
				break;
			case 0xFD0: // keep without a rule
				keep(dce);
				break;
			case 0xFFD: // 12 13 14
				if (equalIdAndMode(hId, hMode, iId, iMode))
					if (f == null
							|| f.isModified(dce, true,
									this.walk.getObjectReader()))
						conflict(name, dce, h, m);
					else
						remove(name);
				else
					conflict(name, dce, h, m);
				break;
			case 0x0DF: // 16 17
				if (!isModified(name))
					update(name, mId, mMode);
				else
					conflict(name, dce, h, m);
				break;
			default:
				keep(dce);
			}
			return;
		}

		// if we have no file at all then there is nothing to do
		if ((ffMask & 0x222) == 0)
			return;

		if ((ffMask == 0x00F) && f != null && FileMode.TREE.equals(f.getEntryFileMode())) {
			// File/Directory conflict case #20
			conflict(name, null, h, m);
		}

		if (i == null) {
			// Nothing in Index
			// At least one of Head, Index, Merge is not empty
			// make sure not to overwrite untracked files
			if (f != null) {
				// A submodule is not a file. We should ignore it
				if (!FileMode.GITLINK.equals(mMode)) {
					// a dirty worktree: the index is empty but we have a
					// workingtree-file
					if (mId == null
							|| !equalIdAndMode(mId, mMode,
									f.getEntryObjectId(), f.getEntryFileMode())) {
						conflict(name, null, h, m);
						return;
					}
				}
			}

			/**
			 * <pre>
			 * 	          I (index)     H        M     H==M  Result
			 * 	        -------------------------------------------
			 * 	        0 nothing    nothing  nothing        (does not happen)
			 * 	        1 nothing    nothing  exists         use M
			 * 	        2 nothing    exists   nothing        remove path from index
			 * 	        3 nothing    exists   exists   yes   keep index
			 * 	          nothing    exists   exists   no    fail
			 * </pre>
			 */

			if (h == null)
				// Nothing in Head
				// Nothing in Index
				// At least one of Head, Index, Merge is not empty
				// -> only Merge contains something for this path. Use it!
				// Potentially update the file
				update(name, mId, mMode); // 1
			else if (m == null)
				// Nothing in Merge
				// Something in Head
				// Nothing in Index
				// -> only Head contains something for this path and it should
				// be deleted. Potentially removes the file!
				remove(name); // 2
			else { // 3
				// Something in Merge
				// Something in Head
				// Nothing in Index
				// -> Head and Merge contain something (maybe not the same) and
				// in the index there is nothing (e.g. 'git rm ...' was
				// called before). Ignore the cached deletion and use what we
				// find in Merge. Potentially updates the file.
				if (equalIdAndMode(hId, hMode, mId, mMode))
					keep(dce);
				else
					conflict(name, dce, h, m);
			}
		} else {
			// Something in Index
			if (h == null) {
				// Nothing in Head
				// Something in Index
				/**
				 * <pre>
				 * 	          clean I==H  I==M       H        M        Result
				 * 	         -----------------------------------------------------
				 * 	        4 yes   N/A   N/A     nothing  nothing  keep index
				 * 	        5 no    N/A   N/A     nothing  nothing  keep index
				 *
				 * 	        6 yes   N/A   yes     nothing  exists   keep index
				 * 	        7 no    N/A   yes     nothing  exists   keep index
				 * 	        8 yes   N/A   no      nothing  exists   fail
				 * 	        9 no    N/A   no      nothing  exists   fail
				 * </pre>
				 */

				if (m == null || equalIdAndMode(mId, mMode, iId, iMode)) {
					// Merge contains nothing or the same as Index
					// Nothing in Head
					// Something in Index
					if (m==null && walk.isDirectoryFileConflict()) {
						// Nothing in Merge and current path is part of
						// File/Folder conflict
						// Nothing in Head
						// Something in Index
						if (dce != null
								&& (f == null || f.isModified(dce, true,
										this.walk.getObjectReader())))
							// No file or file is dirty
							// Nothing in Merge and current path is part of
							// File/Folder conflict
							// Nothing in Head
							// Something in Index
							// -> File folder conflict and Merge wants this
							// path to be removed. Since the file is dirty
							// report a conflict
							conflict(name, dce, h, m);
						else
							// A file is present and file is not dirty
							// Nothing in Merge and current path is part of
							// File/Folder conflict
							// Nothing in Head
							// Something in Index
							// -> File folder conflict and Merge wants this path
							// to be removed. Since the file is not dirty remove
							// file and index entry
							remove(name);
					} else
						// Something in Merge or current path is not part of
						// File/Folder conflict
						// Merge contains nothing or the same as Index
						// Nothing in Head
						// Something in Index
						// -> Merge contains nothing new. Keep the index.
						keep(dce);
				} else
					// Merge contains something and it is not the same as Index
					// Nothing in Head
					// Something in Index
					// -> Index contains something new (different from Head)
					// and Merge is different from Index. Report a conflict
					conflict(name, dce, h, m);
			} else if (m == null) {
				// Nothing in Merge
				// Something in Head
				// Something in Index

				/**
				 * <pre>
				 * 	           clean I==H  I==M       H        M        Result
				 * 	         -----------------------------------------------------
				 * 	        10 yes   yes   N/A     exists   nothing  remove path from index
				 * 	        11 no    yes   N/A     exists   nothing  fail
				 * 	        12 yes   no    N/A     exists   nothing  fail
				 * 	        13 no    no    N/A     exists   nothing  fail
				 * </pre>
				 */

				if (iMode == FileMode.GITLINK) {
					// A submodule in Index
					// Nothing in Merge
					// Something in Head
					// Submodules that disappear from the checkout must
					// be removed from the index, but not deleted from disk.
					remove(name);
				} else {
					// Something different from a submodule in Index
					// Nothing in Merge
					// Something in Head
					if (equalIdAndMode(hId, hMode, iId, iMode)) {
						// Index contains the same as Head
						// Something different from a submodule in Index
						// Nothing in Merge
						// Something in Head
						if (f == null
								|| f.isModified(dce, true,
										this.walk.getObjectReader()))
							// file is dirty
							// Index contains the same as Head
							// Something different from a submodule in Index
							// Nothing in Merge
							// Something in Head
							// -> file is dirty but is should be removed. That's
							// a conflict
							conflict(name, dce, h, m);
						else
							// file doesn't exist or is clean
							// Index contains the same as Head
							// Something different from a submodule in Index
							// Nothing in Merge
							// Something in Head
							// -> Remove from index and delete the file
							remove(name);
					} else
						// Index contains something different from Head
						// Something different from a submodule in Index
						// Nothing in Merge
						// Something in Head
						// -> Something new is in index (and maybe even on the
						// filesystem). But Merge wants the path to be removed.
						// Report a conflict
						conflict(name, dce, h, m);
				}
			} else {
				// Something in Merge
				// Something in Head
				// Something in Index
				if (!equalIdAndMode(hId, hMode, mId, mMode)
						&& !equalIdAndMode(hId, hMode, iId, iMode)
						&& !equalIdAndMode(mId, mMode, iId, iMode))
					// All three contents in Head, Merge, Index differ from each
					// other
					// -> All contents differ. Report a conflict.
					conflict(name, dce, h, m);
				else
					// At least two of the contents of Head, Index, Merge
					// are the same
					// Something in Merge
					// Something in Head
					// Something in Index

					if (equalIdAndMode(hId, hMode, iId, iMode)
						&& !equalIdAndMode(mId, mMode, iId, iMode)) {
						// Head contains the same as Index. Merge differs
						// Something in Merge

					// For submodules just update the index with the new SHA-1
					if (dce != null
							&& FileMode.GITLINK.equals(dce.getFileMode())) {
						// Index and Head contain the same submodule. Merge
						// differs
						// Something in Merge
						// -> Nothing new in index. Move to merge.
						// Potentially updates the file

						// TODO check that we don't overwrite some unsaved
						// file content
						update(name, mId, mMode);
					} else if (dce != null
							&& (f != null && f.isModified(dce, true,
									this.walk.getObjectReader()))) {
						// File exists and is dirty
						// Head and Index don't contain a submodule
						// Head contains the same as Index. Merge differs
						// Something in Merge
						// -> Merge wants the index and file to be updated
						// but the file is dirty. Report a conflict
						conflict(name, dce, h, m);
					} else {
						// File doesn't exist or is clean
						// Head and Index don't contain a submodule
						// Head contains the same as Index. Merge differs
						// Something in Merge
						// -> Standard case when switching between branches:
						// Nothing new in index but something different in
						// Merge. Update index and file
						update(name, mId, mMode);
					}
				} else {
					// Head differs from index or merge is same as index
					// At least two of the contents of Head, Index, Merge
					// are the same
					// Something in Merge
					// Something in Head
					// Something in Index

					// Can be formulated as: Either all three states are
					// equal or Merge is equal to Head or Index and differs
					// to the other one.
					// -> In all three cases we don't touch index and file.

					keep(dce);
				}
			}
		}
	}

	/**
	 * A conflict is detected - add the three different stages to the index
	 * @param path the path of the conflicting entry
	 * @param e the previous index entry
	 * @param h the first tree you want to merge (the HEAD)
	 * @param m the second tree you want to merge
	 */
	private void conflict(String path, DirCacheEntry e, AbstractTreeIterator h, AbstractTreeIterator m) {
		conflicts.add(path);

		DirCacheEntry entry;
		if (e != null) {
			entry = new DirCacheEntry(e.getPathString(), DirCacheEntry.STAGE_1);
			entry.copyMetaData(e, true);
			builder.add(entry);
		}

		if (h != null && !FileMode.TREE.equals(h.getEntryFileMode())) {
			entry = new DirCacheEntry(h.getEntryPathString(), DirCacheEntry.STAGE_2);
			entry.setFileMode(h.getEntryFileMode());
			entry.setObjectId(h.getEntryObjectId());
			builder.add(entry);
		}

		if (m != null && !FileMode.TREE.equals(m.getEntryFileMode())) {
			entry = new DirCacheEntry(m.getEntryPathString(), DirCacheEntry.STAGE_3);
			entry.setFileMode(m.getEntryFileMode());
			entry.setObjectId(m.getEntryObjectId());
			builder.add(entry);
		}
	}

	private void keep(DirCacheEntry e) {
		if (e != null && !FileMode.TREE.equals(e.getFileMode()))
			builder.add(e);
	}

	private void remove(String path) {
		removed.add(path);
	}

	private void update(String path, ObjectId mId, FileMode mode) {
		if (!FileMode.TREE.equals(mode)) {
			updated.put(path, mId);
			DirCacheEntry entry = new DirCacheEntry(path, DirCacheEntry.STAGE_0);
			entry.setObjectId(mId);
			entry.setFileMode(mode);
			builder.add(entry);
		}
	}

	/**
	 * If <code>true</code>, will scan first to see if it's possible to check
	 * out, otherwise throw {@link CheckoutConflictException}. If
	 * <code>false</code>, it will silently deal with the problem.
	 *
	 * @param failOnConflict
	 */
	public void setFailOnConflict(boolean failOnConflict) {
		this.failOnConflict = failOnConflict;
	}

	/**
	 * This method implements how to handle conflicts when
	 * {@link #failOnConflict} is false
	 *
	 * @throws CheckoutConflictException
	 */
	private void cleanUpConflicts() throws CheckoutConflictException {
		// TODO: couldn't we delete unsaved worktree content here?
		for (String c : conflicts) {
			File conflict = new File(repo.getWorkTree(), c);
			if (!conflict.delete())
				throw new CheckoutConflictException(MessageFormat.format(
						JGitText.get().cannotDeleteFile, c));
			removeEmptyParents(conflict);
		}
		for (String r : removed) {
			File file = new File(repo.getWorkTree(), r);
			if (!file.delete())
				throw new CheckoutConflictException(
						MessageFormat.format(JGitText.get().cannotDeleteFile,
								file.getAbsolutePath()));
			removeEmptyParents(file);
		}
	}

	private boolean isModified(String path) throws CorruptObjectException, IOException {
		NameConflictTreeWalk tw = new NameConflictTreeWalk(repo);
		tw.addTree(new DirCacheIterator(dc));
		tw.addTree(new FileTreeIterator(repo));
		tw.setRecursive(true);
		tw.setFilter(PathFilter.create(path));
		DirCacheIterator dcIt;
		WorkingTreeIterator wtIt;
		while(tw.next()) {
			dcIt = tw.getTree(0, DirCacheIterator.class);
			wtIt = tw.getTree(1, WorkingTreeIterator.class);
			if (dcIt == null || wtIt == null)
				return true;
			if (wtIt.isModified(dcIt.getDirCacheEntry(), true,
					this.walk.getObjectReader())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Updates the file in the working tree with content and mode from an entry
	 * in the index. The new content is first written to a new temporary file in
	 * the same directory as the real file. Then that new file is renamed to the
	 * final filename. Use this method only for checkout of a single entry.
	 * Otherwise use
	 * {@code checkoutEntry(Repository, File f, DirCacheEntry, ObjectReader)}
	 * instead which allows to reuse one {@code ObjectReader} for multiple
	 * entries.
	 *
	 * <p>
	 * TODO: this method works directly on File IO, we may need another
	 * abstraction (like WorkingTreeIterator). This way we could tell e.g.
	 * Eclipse that Files in the workspace got changed
	 * </p>
	 *
	 * @param repository
	 * @param f
	 *            the file to be modified. The parent directory for this file
	 *            has to exist already
	 * @param entry
	 *            the entry containing new mode and content
	 * @throws IOException
	 */
	public static void checkoutEntry(final Repository repository, File f,
			DirCacheEntry entry) throws IOException {
		ObjectReader or = repository.newObjectReader();
		try {
			checkoutEntry(repository, f, entry, repository.newObjectReader());
		} finally {
			or.release();
		}
	}

	/**
	 * Updates the file in the working tree with content and mode from an entry
	 * in the index. The new content is first written to a new temporary file in
	 * the same directory as the real file. Then that new file is renamed to the
	 * final filename.
	 *
	 * <p>
	 * TODO: this method works directly on File IO, we may need another
	 * abstraction (like WorkingTreeIterator). This way we could tell e.g.
	 * Eclipse that Files in the workspace got changed
	 * </p>
	 *
	 * @param repo
	 * @param f
	 *            the file to be modified. The parent directory for this file
	 *            has to exist already
	 * @param entry
	 *            the entry containing new mode and content
	 * @param or
	 *            object reader to use for checkout
	 * @throws IOException
	 */
	public static void checkoutEntry(final Repository repo, File f,
			DirCacheEntry entry, ObjectReader or) throws IOException {
		ObjectLoader ol = or.open(entry.getObjectId());
		File parentDir = f.getParentFile();
		parentDir.mkdirs();
		FS fs = repo.getFS();
		WorkingTreeOptions opt = repo.getConfig().get(WorkingTreeOptions.KEY);
		if (entry.getFileMode() == FileMode.SYMLINK
				&& opt.getSymLinks() == SymLinks.TRUE) {
			byte[] bytes = ol.getBytes();
			String target = RawParseUtils.decode(bytes);
			fs.createSymLink(f, target);
			entry.setLength(bytes.length);
			entry.setLastModified(fs.lastModified(f));
		} else {
			File tmpFile = File.createTempFile(
					"._" + f.getName(), null, parentDir); //$NON-NLS-1$
			FileOutputStream rawChannel = new FileOutputStream(tmpFile);
			OutputStream channel;
			if (opt.getAutoCRLF() == AutoCRLF.TRUE)
				channel = new AutoCRLFOutputStream(rawChannel);
			else
				channel = rawChannel;
			try {
				ol.copyTo(channel);
			} finally {
				channel.close();
			}
			if (opt.isFileMode() && fs.supportsExecute()) {
				if (FileMode.EXECUTABLE_FILE.equals(entry.getRawMode())) {
					if (!fs.canExecute(tmpFile))
						fs.setExecute(tmpFile, true);
				} else {
					if (fs.canExecute(tmpFile))
						fs.setExecute(tmpFile, false);
				}
			}
			try {
				FileUtils.rename(tmpFile, f);
			} catch (IOException e) {
				throw new IOException(MessageFormat.format(
						JGitText.get().couldNotWriteFile, tmpFile.getPath(),
						f.getPath()));
			}
		}
		entry.setLastModified(f.lastModified());
		if (opt.getAutoCRLF() != AutoCRLF.FALSE)
			entry.setLength(f.length()); // AutoCRLF wants on-disk-size
		else
			entry.setLength((int) ol.getSize());
	}

	private static void checkValidPath(CanonicalTreeParser t)
			throws InvalidPathException {
		ObjectChecker chk = new ObjectChecker()
			.setSafeForWindows(SystemReader.getInstance().isWindows())
			.setSafeForMacOS(SystemReader.getInstance().isMacOS());
		for (CanonicalTreeParser i = t; i != null; i = i.getParent())
			checkValidPathSegment(chk, i);
	}

	/**
	 * Check if path is a valid path for a checked out file name or ref name.
	 *
	 * @param path
	 * @throws InvalidPathException
	 *             if the path is invalid
	 * @since 3.3
	 */
	public static void checkValidPath(String path) throws InvalidPathException {
		ObjectChecker chk = new ObjectChecker()
			.setSafeForWindows(SystemReader.getInstance().isWindows())
			.setSafeForMacOS(SystemReader.getInstance().isMacOS());

		byte[] bytes = Constants.encode(path);
		int segmentStart = 0;
		try {
			for (int i = 0; i < bytes.length; i++) {
				if (bytes[i] == '/') {
					chk.checkPathSegment(bytes, segmentStart, i);
					segmentStart = i + 1;
				}
			}
			chk.checkPathSegment(bytes, segmentStart, bytes.length);
		} catch (CorruptObjectException e) {
			throw new InvalidPathException(e.getMessage());
		}
	}

	private static void checkValidPathSegment(ObjectChecker chk,
			CanonicalTreeParser t) throws InvalidPathException {
		try {
			int ptr = t.getNameOffset();
			int end = ptr + t.getNameLength();
			chk.checkPathSegment(t.getEntryPathBuffer(), ptr, end);
		} catch (CorruptObjectException err) {
			String path = t.getEntryPathString();
			InvalidPathException i = new InvalidPathException(path);
			i.initCause(err);
			throw i;
		}
	}
}
