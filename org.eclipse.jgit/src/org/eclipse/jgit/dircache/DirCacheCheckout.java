/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com> and
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
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.errors.CheckoutConflictException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectWriter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryConfig;
import org.eclipse.jgit.lib.Tree;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.NameConflictTreeWalk;

/**
 * This class handles checking out one or two trees merging with the index. This
 * class does similar things than {@code WorkDirCheckout} but uses
 * {@link DirCache} instead of {@code GitIndex}
 */
public class DirCacheCheckout {
	private Repository repo;

	private HashMap<String, ObjectId> updated = new HashMap<String, ObjectId>();

	private ArrayList<String> conflicts = new ArrayList<String>();

	private ArrayList<String> removed = new ArrayList<String>();

	private Tree mergeCommitTree;

	private DirCache dc;

	private DirCacheBuilder builder;

	private Tree headCommitTree;

	private boolean failOnConflict = true;

	/**
	 * @return a list of updated pathes and objectIds
	 */
	public HashMap<String, ObjectId> getUpdated() {
		return updated;
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

	/**
	 * Create a checkout class for checking out one tree, merging with the index
	 *
	 * @param repo the repository in which we do the checkout
	 * @param headCommitTree the id of the tree of the head commit
	 * @param dc
	 * @param mergeCommitTree the id of the tree of the
	 * @throws IOException
	 */
	public DirCacheCheckout(Repository repo, Tree headCommitTree, DirCache dc,
			Tree mergeCommitTree) throws IOException {
		this.repo = repo;
		this.dc = dc;
		this.headCommitTree = headCommitTree;
		this.mergeCommitTree = mergeCommitTree;
	}

	/**
	 * Scan head, index and merge tree (no HEAD). Used during normal checkout or
	 * merge operations.
	 *
	 * @param headCommitTree
	 * @param dc
	 * @param mTreeId
	 * @throws CorruptObjectException
	 * @throws IOException
	 */
	public void preScanTwoTrees(ObjectId headCommitTree,
			DirCache dc, ObjectId mTreeId) throws CorruptObjectException,
			IOException {
		removed.clear();
		updated.clear();
		conflicts.clear();
		NameConflictTreeWalk tw = new NameConflictTreeWalk(repo);
		builder = dc.builder();

		tw.reset();
		tw.addTree(headCommitTree);
		tw.addTree(mTreeId);
		tw.addTree(new DirCacheBuildIterator(builder));

		while (tw.next()) {
			processEntry(tw.getTree(0, CanonicalTreeParser.class),
					tw.getTree(1, CanonicalTreeParser.class),
					tw.getTree(2, DirCacheBuildIterator.class));
			if (tw.isSubtree())
				tw.enterSubtree();
		}
	}

	/**
	 * Scan index and merge tree (no HEAD). Used e.g. for initial checkout when
	 * you have no head yet.
	 *
	 * @param dc
	 * @param mTreeId
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws CorruptObjectException
	 * @throws IOException
	 */
	public void prescanOneTree(DirCache dc, ObjectId mTreeId)
			throws MissingObjectException, IncorrectObjectTypeException,
			CorruptObjectException, IOException {
		removed.clear();
		updated.clear();
		conflicts.clear();

		builder = dc.builder();

		NameConflictTreeWalk tw = new NameConflictTreeWalk(repo);
		tw.reset();
		tw.addTree(mTreeId);
		tw.addTree(new DirCacheBuildIterator(builder));

		while (tw.next()) {
			processEntry(tw.getTree(0, CanonicalTreeParser.class),
					tw.getTree(1, DirCacheBuildIterator.class));
			if (tw.isSubtree())
				tw.enterSubtree();
		}
		conflicts.removeAll(removed);
	}

	void processEntry(CanonicalTreeParser m, DirCacheBuildIterator i) {
		if (m != null) {
			if (!FileMode.TREE.equals(m.getEntryFileMode())) {
				// we ant to add a new file to the index ...
				File f = new File(repo.getWorkDir(), m.getEntryPathString());
				if (f.exists() && !f.isFile())
					// ... but the working dir contained a directory ->
					// conflict with all files under the directory
					checkConflictsWithFile(f);
			}
			update(m.getEntryPathString(), m.getEntryObjectId(),
					m.getEntryFileMode());
		} else {
			// we want to remove a path from the index ...
			File f = new File(repo.getWorkDir(), i.getEntryPathString());
			if (f.exists()) {
				// ... and the working dir contained a file or folder ->
				// add it to the removed set and remove it from conflicts set
				remove(i.getEntryPathString());
				conflicts.remove(i.getEntryPathString());
			} else {
				keep(i.getDirCacheEntry());
			}
		}
	}

	/**
	 * Execute this checkout
	 *
	 * @throws IOException
	 */
	public void checkout() throws IOException {
		if (headCommitTree != null)
			preScanTwoTrees(headCommitTree.getTreeId(), dc,
					mergeCommitTree.getTreeId());
		else
			prescanOneTree(dc, mergeCommitTree.getTreeId());

		if (!conflicts.isEmpty()) {
			if (failOnConflict) {
				dc.unlock();
				throw new IOException("dirty files exist; refusing to merge.");
			} else
				cleanUpConflicts();
		}

		// update our index
		builder.finish();

		File file;
		for (String p : updated.keySet()) {
			// ... create/overwrite this file ...
			file = new File(repo.getWorkDir(), p);
			file.getParentFile().mkdirs();
			file.createNewFile();
			DirCacheEntry entry = dc.getEntry(p);
			entry.checkoutEntry(repo, file, config_filemode());
			entry.setLastModified(file.lastModified());
			entry.setLength((int) file.length());
		}

		for (String r : removed) {
			file = new File(repo.getWorkDir(), r);
			file.delete();
			removeEmptyParents(file);
		}

		// commit the index builder - a new index is persisted
		if (!builder.commit()) {
			dc.unlock();
			throw new IOException(
					"couldn't commit the index; refusing to merge");
		}
	}

	private void removeEmptyParents(File f) {
		File parentFile = f.getParentFile();
		while (!parentFile.equals(repo.getWorkDir())) {
			if (parentFile.list().length == 0)
				parentFile.delete();
			else
				break;

			parentFile = parentFile.getParentFile();
		}
	}

	/**
	 * Here the main work is done. This method is called for each existing path
	 * in head, index and merge. This method decides what to do with the index:
	 * keep it, update it, remove it or mark a conflict.
	 *
	 * @param h the entry for the head
	 * @param m the entry for the merge
	 * @param i the entry for the index
	 * @throws IOException
	 */

	void processEntry(AbstractTreeIterator h, AbstractTreeIterator m,
			DirCacheBuildIterator i) throws IOException {
		DirCacheEntry dce;
		ObjectWriter ow = new ObjectWriter(repo);

		ObjectId iId = (i == null ? null : i.getEntryObjectId());
		ObjectId mId = (m == null ? null : m.getEntryObjectId());
		ObjectId hId = (h == null ? null : h.getEntryObjectId());

		String name = (i != null ? i.getEntryPathString() : (h != null ? h
				.getEntryPathString() : m.getEntryPathString()));

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
		 * 3    D        F       D                 Y       N       N           Update
		 * 4    D        F       D                 N       N       N           Update
		 * 5    D        F       F       Y         N       N       Y           Keep
		 * 6    D        F       F       N         N       N       Y           Keep
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
		 * </pre>
		 */

		// The information whether head,index,merge iterators are currently
		// pointing to file/folder/non-existing is encoded into this variable.
		//
		// To decode write down ffMask in hexadecimal form. The last digit
		// represents the state for the merge iterator, the second last the
		// state for the index iterator and the third last represents the state
		// for the head iterator. The hexadecimal constant "F" stands for
		// "file",
		// an "D" stands for "directory" (tree), and a "0" stands for
		// non-exisiting
		//
		// Examples:
		// ffMask == 0xFFD -> Head=File, Index=File, Merge=Tree
		// ffMask == 0xDD0 -> Head=Tree, Index=Tree, Merge=Non-Exisiting

		int ffMask = 0;
		if (h != null)
			ffMask = FileMode.TREE.equals(h.getEntryFileMode()) ? 0xD00 : 0xF00;
		if (i != null)
			ffMask |= FileMode.TREE.equals(i.getEntryFileMode()) ? 0x0D0
					: 0x0F0;
		if (m != null)
			ffMask |= FileMode.TREE.equals(m.getEntryFileMode()) ? 0x00D
					: 0x00F;

		// Check whether we have a possible file/folder conflicts. Therefore we
		// need a least one file and one folder.
		if (((ffMask & 0x222) != 0x000)
				&& (((ffMask & 0x00F) == 0x00D) || ((ffMask & 0x0F0) == 0x0D0) || ((ffMask & 0xF00) == 0xD00))) {

			// There are 3*3*3=27 possible combinations of file/folder
			// conflicts. Some of them are not-relevant because
			// they represent no conflict, e.g. 0xFFF, 0xDDD, ... The following
			// switch processes all relevant cases.
			switch (ffMask) {
			case 0xDDF: // 1 2
				if (hId.equals(iId)) {
					dce = i.getDirCacheEntry();
					if (dce.isModified(true, config_filemode(),
							repo.getWorkDir(), ow))
						conflict(i.getDirCacheEntry());
					else
						update(name, mId, m.getEntryFileMode());
				}
				else conflict(i.getDirCacheEntry());
				break;
			case 0xDFD: // 3 4
				// CAUTION: I put it into removed instead of updated, because
				// that's what our tests expect
				// updated.put(name, mId);
				remove(name);
				break;
			case 0xF0D: // 18
				remove(name);
				break;
			case 0xDFF: // 5 6
			case 0xFDD: // 10 11
				if (!iId.equals(mId))
				  conflict(i.getDirCacheEntry());
				break;
			case 0xD0F: // 19
			case 0xDF0: // conflict without a rule
			case 0x0FD: // 15
				conflict(i.getDirCacheEntry());
				break;
			case 0xFDF: // 7 8 9
				dce = i.getDirCacheEntry();
				if (hId.equals(mId)) {
					if (dce != null
							&& dce.isModified(true, config_filemode(),
									repo.getWorkDir(), ow))
						conflict(i.getDirCacheEntry());
					else
						update(name, mId, m.getEntryFileMode());
				} else if (dce != null
						&& !dce.isModified(true, config_filemode(),
								repo.getWorkDir(), ow))
					update(name, mId, m.getEntryFileMode());
				else
					conflict(i.getDirCacheEntry());
				break;
			case 0xFD0: // keep without a rule
				keep(i.getDirCacheEntry());
				break;
			case 0xFFD: // 12 13 14
				if (hId.equals(iId)) {
					dce = i.getDirCacheEntry();
					if (dce.isModified(true, config_filemode(),
							repo.getWorkDir(), ow))
						conflict(i.getDirCacheEntry());
					else
						remove(name);
				} else
					conflict(i.getDirCacheEntry());
				break;
			case 0x0DF: // 16 17
				dce = i.getDirCacheEntry();
				if (dce != null
						&& !dce.isModified(true, config_filemode(),
								repo.getWorkDir(), ow))
					update(name, mId, m.getEntryFileMode());
				else
					conflict(i.getDirCacheEntry());
				break;
			default:
				keep(i.getDirCacheEntry());
			}
			return;
		}

		if (i == null) {
			/**
			 * <pre>
			 * 		    I (index)                H        M        Result
			 * 	        -------------------------------------------------------
			 * 	        0 nothing             nothing  nothing  (does not happen)
			 * 	        1 nothing             nothing  exists   use M
			 * 	        2 nothing             exists   nothing  remove path from index
			 * 	        3 nothing             exists   exists   use M
			 * </pre>
			 */

			if (h == null)
				update(name, mId, m.getEntryFileMode());
			else if (m == null)
				remove(name);
			else
				update(name, mId, m.getEntryFileMode());
		} else {
			dce = i.getDirCacheEntry();
			if (h == null) {
				/**
				 * <pre>
				 * 			  clean I==H  I==M       H        M        Result
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

				if (m == null || mId.equals(iId)) {
					if (hasParentBlob(mergeCommitTree, name)) {
						if (dce != null
								&& dce.isModified(true, config_filemode(),
										repo.getWorkDir(), ow))
							conflict(i.getDirCacheEntry());
						else
							remove(name);
					} else
						keep(i.getDirCacheEntry());
				} else
					conflict(i.getDirCacheEntry());
			} else if (m == null) {
				/**
				 * <pre>
				 * 			10 yes   yes   N/A     exists   nothing  remove path from index
				 * 	        11 no    yes   N/A     exists   nothing  fail
				 * 	        12 yes   no    N/A     exists   nothing  fail
				 * 	        13 no    no    N/A     exists   nothing  fail
				 * </pre>
				 */

				if (hId.equals(iId)) {
					if (dce.isModified(true, config_filemode(),
							repo.getWorkDir(), ow))
						conflict(i.getDirCacheEntry());
					else
						remove(name);
				} else
					conflict(i.getDirCacheEntry());
			} else {
				if (!hId.equals(mId) && !hId.equals(iId) && !mId.equals(iId))
					conflict(i.getDirCacheEntry());
				else if (hId.equals(iId) && !mId.equals(iId)) {
					if (dce != null
							&& dce.isModified(true, config_filemode(),
									repo.getWorkDir(), ow))
						conflict(i.getDirCacheEntry());
					else
						update(name, mId, m.getEntryFileMode());
				} else {
					keep(i.getDirCacheEntry());
				}
			}
		}
	}

	private void conflict(DirCacheEntry e) {
		conflicts.add(e.getPathString());

		// when a conflict is detected keep the index entry
		// so that when failOnConflict is false we don't loose the path
		builder.add(e);
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

	private boolean hasParentBlob(Tree t, String name) throws IOException {
		if (name.indexOf("/") == -1)
			return false;

		String parent = name.substring(0, name.lastIndexOf("/"));
		if (t.findBlobMember(parent) != null)
			return true;
		return hasParentBlob(t, parent);
	}

	Boolean filemode;

	private boolean config_filemode() {
		// temporary till we can actually set parameters. We need to be able
		// to change this for testing.
		if (filemode == null) {
			RepositoryConfig config = repo.getConfig();
			filemode = Boolean.valueOf(config.getBoolean("core", null,
					"filemode", true));
		}
		return filemode.booleanValue();
	}

	private void checkConflictsWithFile(File file) {
		if (file.isDirectory()) {
			ArrayList<String> childFiles = listFiles(file);
			conflicts.addAll(childFiles);
		} else {
			File parent = file.getParentFile();
			while (!parent.equals(repo.getWorkDir())) {
				if (parent.isDirectory())
					break;
				if (parent.exists() && parent.isFile()) {
					conflicts.add(Repository.stripWorkDir(repo.getWorkDir(),
							parent));
					break;
				}
				parent = parent.getParentFile();
			}
		}
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
				list.add(Repository.stripWorkDir(repo.getWorkDir(), f));
			}
		}
	}

	/**
	 * If <code>true</code>, will scan first to see if it's possible to check out,
	 * otherwise throw {@link CheckoutConflictException}. If <code>false</code>,
	 * it will silently deal with the problem.
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
		for (String c : conflicts) {
			File conflict = new File(repo.getWorkDir(), c);
			if (!conflict.delete())
				throw new CheckoutConflictException(MessageFormat.format(JGitText.get().cannotDeleteFile, c));
			removeEmptyParents(conflict);
		}
		for (String r : removed) {
			File file = new File(repo.getWorkDir(), r);
			file.delete();
			removeEmptyParents(file);
		}
	}
}
