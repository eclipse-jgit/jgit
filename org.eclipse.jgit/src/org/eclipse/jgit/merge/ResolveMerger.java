/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>,
 * Copyright (C) 2010-2012, Matthias Sohn <matthias.sohn@sap.com>
 * Copyright (C) 2012, Research In Motion Limited
 * Copyright (C) 2013, Obeo
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
package org.eclipse.jgit.merge;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.diff.Sequence;
import org.eclipse.jgit.diff.DiffAlgorithm.SupportedAlgorithm;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuildIterator;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.IndexWriteException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.NameConflictTreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;

/**
 * A three-way merger performing a content-merge if necessary
 */
public class ResolveMerger extends ThreeWayMerger {
	/**
	 * If the merge fails (means: not stopped because of unresolved conflicts)
	 * this enum is used to explain why it failed
	 */
	public enum MergeFailureReason {
		/** the merge failed because of a dirty index */
		DIRTY_INDEX,
		/** the merge failed because of a dirty workingtree */
		DIRTY_WORKTREE,
		/** the merge failed because of a file could not be deleted */
		COULD_NOT_DELETE
	}

	/** The tree walk which we'll iterate over to merge entries. */
	protected NameConflictTreeWalk tw;

	/**
	 * string versions of a list of commit SHA1s
	 *
	 * @since 3.0
	 */
	protected String commitNames[];

	/** Index of the base tree within the {@link #tw tree walk}. */
	protected static final int T_BASE = 0;

	/** Index of our tree in withthe {@link #tw tree walk}. */
	protected static final int T_OURS = 1;

	/** Index of their tree within the {@link #tw tree walk}. */
	protected static final int T_THEIRS = 2;

	/** Index of the index tree within the {@link #tw tree walk}. */
	protected static final int T_INDEX = 3;

	/** Index of the working directory tree within the {@link #tw tree walk}. */
	protected static final int T_FILE = 4;

	private DirCacheBuilder builder;

	/**
	 * merge result as tree
	 *
	 * @since 3.0
	 */
	protected ObjectId resultTree;

	private List<String> unmergedPaths = new ArrayList<String>();

	private List<String> modifiedFiles = new LinkedList<String>();

	private Map<String, DirCacheEntry> toBeCheckedOut = new HashMap<String, DirCacheEntry>();

	private List<String> toBeDeleted = new ArrayList<String>();

	private Map<String, MergeResult<? extends Sequence>> mergeResults = new HashMap<String, MergeResult<? extends Sequence>>();

	private Map<String, MergeFailureReason> failingPaths = new HashMap<String, MergeFailureReason>();

	/**
	 * Updated as we merge entries of the tree walk. Tells us whether we should
	 * recurse into the entry if it is a subtree.
	 */
	protected boolean enterSubtree;

	/**
	 * Set to true if this merge should work in-memory. The repos dircache and
	 * workingtree are not touched by this method. Eventually needed files are
	 * created as temporary files and a new empty, in-memory dircache will be
	 * used instead the repo's one. Often used for bare repos where the repo
	 * doesn't even have a workingtree and dircache.
	 * @since 3.0
	 */
	protected boolean inCore;

	/**
	 * Set to true if this merger should use the default dircache of the
	 * repository and should handle locking and unlocking of the dircache. If
	 * this merger should work in-core or if an explicit dircache was specified
	 * during construction then this field is set to false.
	 * @since 3.0
	 */
	protected boolean implicitDirCache;

	/**
	 * Directory cache
	 * @since 3.0
	 */
	protected DirCache dircache;

	/**
	 * The iterator to access the working tree. If set to <code>null</code> this
	 * merger will not touch the working tree.
	 * @since 3.0
	 */
	protected WorkingTreeIterator workingTreeIterator;

	/**
	 * @param local
	 * @param inCore
	 */
	protected ResolveMerger(Repository local, boolean inCore) {
		super(local);
		commitNames = new String[] { "BASE", "OURS", "THEIRS" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		this.inCore = inCore;

		if (inCore) {
			implicitDirCache = false;
			dircache = DirCache.newInCore();
		} else {
			implicitDirCache = true;
		}
	}

	/**
	 * @param local
	 */
	protected ResolveMerger(Repository local) {
		this(local, false);
	}

	@Override
	protected boolean mergeImpl() throws IOException {
		if (implicitDirCache)
			dircache = getRepository().lockDirCache();

		try {
			return mergeTrees(mergeBase(), sourceTrees[0], sourceTrees[1]);
		} finally {
			if (implicitDirCache)
				dircache.unlock();
		}
	}

	private void checkout() throws NoWorkTreeException, IOException {
		ObjectReader r = db.getObjectDatabase().newReader();
		try {
			for (Map.Entry<String, DirCacheEntry> entry : toBeCheckedOut
					.entrySet()) {
				File f = new File(db.getWorkTree(), entry.getKey());
				createDir(f.getParentFile());
				DirCacheCheckout.checkoutEntry(db, f, entry.getValue(), r);
				modifiedFiles.add(entry.getKey());
			}
			// Iterate in reverse so that "folder/file" is deleted before
			// "folder". Otherwise this could result in a failing path because
			// of a non-empty directory, for which delete() would fail.
			for (int i = toBeDeleted.size() - 1; i >= 0; i--) {
				String fileName = toBeDeleted.get(i);
				File f = new File(db.getWorkTree(), fileName);
				if (!f.delete())
					failingPaths.put(fileName,
							MergeFailureReason.COULD_NOT_DELETE);
				modifiedFiles.add(fileName);
			}
		} finally {
			r.release();
		}
	}

	private void createDir(File f) throws IOException {
		if (!f.isDirectory() && !f.mkdirs()) {
			File p = f;
			while (p != null && !p.exists())
				p = p.getParentFile();
			if (p == null || p.isDirectory())
				throw new IOException(JGitText.get().cannotCreateDirectory);
			FileUtils.delete(p);
			if (!f.mkdirs())
				throw new IOException(JGitText.get().cannotCreateDirectory);
		}
	}

	/**
	 * Reverts the worktree after an unsuccessful merge. We know that for all
	 * modified files the old content was in the old index and the index
	 * contained only stage 0. In case if inCore operation just clear the
	 * history of modified files.
	 *
	 * @throws IOException
	 * @throws CorruptObjectException
	 * @throws NoWorkTreeException
	 */
	private void cleanUp() throws NoWorkTreeException, CorruptObjectException,
			IOException {
		if (inCore) {
			modifiedFiles.clear();
			return;
		}

		DirCache dc = db.readDirCache();
		ObjectReader or = db.getObjectDatabase().newReader();
		Iterator<String> mpathsIt=modifiedFiles.iterator();
		while(mpathsIt.hasNext()) {
			String mpath=mpathsIt.next();
			DirCacheEntry entry = dc.getEntry(mpath);
			if (entry == null)
				continue;
			FileOutputStream fos = new FileOutputStream(new File(
					db.getWorkTree(), mpath));
			try {
				or.open(entry.getObjectId()).copyTo(fos);
			} finally {
				fos.close();
			}
			mpathsIt.remove();
		}
	}

	/**
	 * adds a new path with the specified stage to the index builder
	 *
	 * @param path
	 * @param p
	 * @param stage
	 * @param lastMod
	 * @param len
	 * @return the entry which was added to the index
	 */
	private DirCacheEntry add(byte[] path, CanonicalTreeParser p, int stage,
			long lastMod, long len) {
		if (p != null && !p.getEntryFileMode().equals(FileMode.TREE)) {
			DirCacheEntry e = new DirCacheEntry(path, stage);
			e.setFileMode(p.getEntryFileMode());
			e.setObjectId(p.getEntryObjectId());
			e.setLastModified(lastMod);
			e.setLength(len);
			builder.add(e);
			return e;
		}
		return null;
	}

	/**
	 * adds a entry to the index builder which is a copy of the specified
	 * DirCacheEntry
	 *
	 * @param e
	 *            the entry which should be copied
	 *
	 * @return the entry which was added to the index
	 */
	private DirCacheEntry keep(DirCacheEntry e) {
		DirCacheEntry newEntry = new DirCacheEntry(e.getPathString(),
				e.getStage());
		newEntry.setFileMode(e.getFileMode());
		newEntry.setObjectId(e.getObjectId());
		newEntry.setLastModified(e.getLastModified());
		newEntry.setLength(e.getLength());
		builder.add(newEntry);
		return newEntry;
	}

	/**
	 * Processes one path and tries to merge. This method will do all do all
	 * trivial (not content) merges and will also detect if a merge will fail.
	 * The merge will fail when one of the following is true
	 * <ul>
	 * <li>the index entry does not match the entry in ours. When merging one
	 * branch into the current HEAD, ours will point to HEAD and theirs will
	 * point to the other branch. It is assumed that the index matches the HEAD
	 * because it will only not match HEAD if it was populated before the merge
	 * operation. But the merge commit should not accidentally contain
	 * modifications done before the merge. Check the <a href=
	 * "http://www.kernel.org/pub/software/scm/git/docs/git-read-tree.html#_3_way_merge"
	 * >git read-tree</a> documentation for further explanations.</li>
	 * <li>A conflict was detected and the working-tree file is dirty. When a
	 * conflict is detected the content-merge algorithm will try to write a
	 * merged version into the working-tree. If the file is dirty we would
	 * override unsaved data.</li>
	 *
	 * @param base
	 *            the common base for ours and theirs
	 * @param ours
	 *            the ours side of the merge. When merging a branch into the
	 *            HEAD ours will point to HEAD
	 * @param theirs
	 *            the theirs side of the merge. When merging a branch into the
	 *            current HEAD theirs will point to the branch which is merged
	 *            into HEAD.
	 * @param index
	 *            the index entry
	 * @param work
	 *            the file in the working tree
	 * @return <code>false</code> if the merge will fail because the index entry
	 *         didn't match ours or the working-dir file was dirty and a
	 *         conflict occurred
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws CorruptObjectException
	 * @throws IOException
	 */
	private boolean processEntry(CanonicalTreeParser base,
			CanonicalTreeParser ours, CanonicalTreeParser theirs,
			DirCacheBuildIterator index, WorkingTreeIterator work)
			throws MissingObjectException, IncorrectObjectTypeException,
			CorruptObjectException, IOException {
		enterSubtree = true;
		final int modeO = tw.getRawMode(T_OURS);
		final int modeT = tw.getRawMode(T_THEIRS);
		final int modeB = tw.getRawMode(T_BASE);

		if (modeO == 0 && modeT == 0 && modeB == 0)
			// File is either untracked or new, staged but uncommitted
			return true;

		if (!inCore && isIndexDirty(tw)) {
			failingPaths
					.put(tw.getPathString(), MergeFailureReason.DIRTY_INDEX);
			return false;
		}

		DirCacheEntry ourDce = null;

		if (index == null || index.getDirCacheEntry() == null) {
			// create a fake DCE, but only if ours is valid. ours is kept only
			// in case it is valid, so a null ourDce is ok in all other cases.
			if (nonTree(modeO)) {
				ourDce = new DirCacheEntry(tw.getRawPath());
				ourDce.setObjectId(tw.getObjectId(T_OURS));
				ourDce.setFileMode(tw.getFileMode(T_OURS));
			}
		} else
			ourDce = index.getDirCacheEntry();

		if (nonTree(modeO) && nonTree(modeT) && tw.idEqual(T_OURS, T_THEIRS)) {
			// OURS and THEIRS have equal content. Check the file mode
			final boolean success;
			if (modeO == modeT) {
				// content and mode of OURS and THEIRS are equal: it doesn't
				// matter which one we choose. OURS is chosen. Since the index
				// is clean (the index matches already OURS) we can keep the
				// existing one.
				keep(ourDce);
				success = true;
			} else {
				// same content but different mode on OURS and THEIRS.
				// Try to merge the mode and report an error if this is not
				// possible.
				int newMode = mergeFileModes(modeB, modeO, modeT);
				if (newMode != FileMode.MISSING.getBits()) {
					if (newMode == modeO) {
						// ours version is preferred
						keep(ourDce);
						success = true;
					} else {
						// the preferred version THEIRS has a different mode
						// than ours. Check it out!
						if (isWorktreeDirty(tw, work)) {
							failingPaths.put(tw.getPathString(),
									MergeFailureReason.DIRTY_WORKTREE);
							success = false;
						} else {
							// we know about length and lastMod only after we
							// have written the new content. This will happen
							// later. Set these values to 0 for know.
							DirCacheEntry e = add(tw.getRawPath(),
									theirs, DirCacheEntry.STAGE_0, 0, 0);
							toBeCheckedOut.put(tw.getPathString(), e);
							success = true;
						}
					}
				} else {
					// FileModes are not mergeable. We found a conflict on modes.
					// For conflicting entries we don't know lastModified and
					// length.
					byte[] rawPath = tw.getRawPath();
					add(rawPath, base, DirCacheEntry.STAGE_1, 0, 0);
					add(rawPath, ours, DirCacheEntry.STAGE_2, 0, 0);
					add(rawPath, theirs, DirCacheEntry.STAGE_3, 0, 0);
					unmergedPaths.add(tw.getPathString());
					mergeResults.put(
							tw.getPathString(),
							new MergeResult<RawText>(Collections
									.<RawText> emptyList()));
					success = true;
				}
			}
			return success;
		}

		if (nonTree(modeO) && modeB == modeT && tw.idEqual(T_BASE, T_THEIRS)) {
			// THEIRS was not changed compared to BASE. All changes must be in
			// OURS. OURS is chosen. We can keep the existing entry.
			keep(ourDce);
			// no checkout needed!
			return true;
		}

		if (modeB == modeO && tw.idEqual(T_BASE, T_OURS)) {
			// OURS was not changed compared to BASE. All changes must be in
			// THEIRS. THEIRS is chosen.

			// Check worktree before checking out THEIRS
			if (isWorktreeDirty(tw, work)) {
				failingPaths.put(tw.getPathString(),
						MergeFailureReason.DIRTY_WORKTREE);
				return false;
			} else if (nonTree(modeT)) {
				// we know about length and lastMod only after we have written
				// the new content.
				// This will happen later. Set these values to 0 for know.
				DirCacheEntry e = add(tw.getRawPath(), theirs,
						DirCacheEntry.STAGE_0, 0, 0);
				if (e != null)
					toBeCheckedOut.put(tw.getPathString(), e);
				return true;
			} else if (modeT == 0 && modeB != 0) {
				// we want THEIRS ... but THEIRS contains the deletion of the
				// file. Also, do not complain if the file is already deleted
				// locally. This complements the test in isWorktreeDirty() for
				// the same case.
				if (tw.getTreeCount() > T_FILE && tw.getRawMode(T_FILE) == 0)
					return true;
				toBeDeleted.add(tw.getPathString());
				return true;
			} else {
				// fall through
			}
		}

		if (tw.isSubtree()) {
			// file/folder conflicts: here I want to detect only file/folder
			// conflict between ours and theirs. file/folder conflicts between
			// base/index/workingTree and something else are not relevant or
			// detected later
			if (nonTree(modeO) && !nonTree(modeT)) {
				byte[] rawPath = tw.getRawPath();
				if (nonTree(modeB))
					add(rawPath, base, DirCacheEntry.STAGE_1, 0, 0);
				add(rawPath, ours, DirCacheEntry.STAGE_2, 0, 0);
				unmergedPaths.add(tw.getPathString());
				enterSubtree = false;
				return true;
			} else if (nonTree(modeT) && !nonTree(modeO)) {
				byte[] rawPath = tw.getRawPath();
				if (nonTree(modeB))
					add(rawPath, base, DirCacheEntry.STAGE_1, 0, 0);
				add(rawPath, theirs, DirCacheEntry.STAGE_3, 0, 0);
				unmergedPaths.add(tw.getPathString());
				enterSubtree = false;
				return true;
			} else if (!nonTree(modeO)) {
				// ours and theirs are both folders or both files (and treewalk
				// tells us we are in a subtree because of index or working-dir).
				// If they are both folders no content-merge is required - we can
				// return here.
				return true;
			} else {
				// ours and theirs are both files, just fall out of the if block
				// and do the content merge
			}
		}

		if (nonTree(modeO) && nonTree(modeT)) {
			// Check worktree before modifying files
			if (isWorktreeDirty(tw, work)) {
				failingPaths.put(tw.getPathString(),
						MergeFailureReason.DIRTY_WORKTREE);
				return false;
			} else if (isGitLink(modeO) || isGitLink(modeT)) {
				// Don't attempt to resolve submodule link conflicts
				byte[] rawPath = tw.getRawPath();
				add(rawPath, base, DirCacheEntry.STAGE_1, 0, 0);
				add(rawPath, ours, DirCacheEntry.STAGE_2, 0, 0);
				add(rawPath, theirs, DirCacheEntry.STAGE_3, 0, 0);
				unmergedPaths.add(tw.getPathString());
				return true;
			} else {
				final File oursFile = createTempFile(
						"_ours", getRepository(), ours); //$NON-NLS-1$
				final File theirsFile = createTempFile(
						"_theirs", getRepository(), theirs); //$NON-NLS-1$
				final File baseFile = createTempFile(
						"_base", getRepository(), base); //$NON-NLS-1$

				final String filePath = tw.getPathString();
				final MergeDriver driver = findMergeDriver(getRepository(),
						filePath, ours, theirs);

				boolean success = driver.merge(getRepository(), oursFile,
						theirsFile, baseFile, commitNames);

				final File mergedFile;
				if (!inCore)
					mergedFile = updateWorkTree(getRepository(),
							tw.getPathString(),
							oursFile);
				else
					mergedFile = oursFile;
				updateIndex(getObjectInserter(), base, ours, theirs, !success,
						mergedFile);

				if (oursFile != null)
					FileUtils.delete(oursFile);
				if (theirsFile != null)
					FileUtils.delete(theirsFile);
				if (baseFile != null)
					FileUtils.delete(baseFile);

				if (!success) {
					unmergedPaths.add(tw.getPathString());
					if (driver instanceof TextMergeDriver)
						mergeResults
								.put(tw.getPathString(),
										((TextMergeDriver) driver)
												.getLowLevelResults());
					else
						mergeResults.put(
								tw.getPathString(),
								new MergeResult<RawText>(Collections
										.<RawText> emptyList()));
				}
				modifiedFiles.add(tw.getPathString());
			}
		} else if (modeO != modeT) {
			// OURS or THEIRS has been deleted
			if ((modeO != 0 && !tw.idEqual(T_BASE, T_OURS)) || (modeT != 0 && !tw.idEqual(T_BASE, T_THEIRS))) {
				byte[] rawPath = tw.getRawPath();
				add(rawPath, base, DirCacheEntry.STAGE_1, 0, 0);
				add(rawPath, ours, DirCacheEntry.STAGE_2, 0, 0);
				DirCacheEntry e = add(rawPath, theirs, DirCacheEntry.STAGE_3, 0, 0);

				// OURS was deleted, checkout THEIRS
				if (modeO == 0) {
					// Check worktree before checking out THEIRS
					if (isWorktreeDirty(tw, work)) {
						failingPaths.put(tw.getPathString(), MergeFailureReason.DIRTY_WORKTREE);
						return false;
					} else if (nonTree(modeT) && e != null) {
						toBeCheckedOut.put(tw.getPathString(), e);
					}
				}

				unmergedPaths.add(tw.getPathString());

				/*
				 * FIXME this is only true for textual files, costly, and unused
				 * later on. Merge drivers are not called in case of deletions
				 * when used from the command line. They shouldn't be called
				 * here either.
				 */
				// generate a MergeResult for the deleted file
				final SupportedAlgorithm diffAlg = getRepository().getConfig().getEnum(ConfigConstants.CONFIG_DIFF_SECTION, null,ConfigConstants.CONFIG_KEY_ALGORITHM,SupportedAlgorithm.HISTOGRAM);
				final MergeAlgorithm mergeAlgorithm = new MergeAlgorithm(DiffAlgorithm.getAlgorithm(diffAlg));
				final RawText baseText = base == null ? RawText.EMPTY_TEXT
						: new RawText(getRepository().open(
								base.getEntryObjectId(), Constants.OBJ_BLOB)
								.getCachedBytes());
				final RawText oursText = ours == null ? RawText.EMPTY_TEXT
						: new RawText(getRepository().open(
								ours.getEntryObjectId(), Constants.OBJ_BLOB)
								.getCachedBytes());
				final RawText theirsText = theirs == null ? RawText.EMPTY_TEXT
						: new RawText(getRepository().open(
								theirs.getEntryObjectId(), Constants.OBJ_BLOB)
								.getCachedBytes());
				final MergeResult<RawText> mergeResult = mergeAlgorithm.merge(
						RawTextComparator.DEFAULT, baseText, oursText,
						theirsText);
				mergeResults.put(tw.getPathString(), mergeResult);
			}
		}
		return true;
	}

	private static File createTempFile(String fileSuffix, Repository db,
			CanonicalTreeParser content) throws IOException {
		if (content == null)
			return null;

		final File outputFile = File.createTempFile("merge_", fileSuffix, null); //$NON-NLS-1$
		OutputStream output = null;
		try {
			output = new FileOutputStream(outputFile);
			db.open(content.getEntryObjectId()).copyTo(output);
		} finally {
			if (output != null)
				output.close();
		}
		return outputFile;
	}

	private static boolean isIndexDirty(TreeWalk tw) {
		final int modeI = tw.getRawMode(T_INDEX);
		final int modeO = tw.getRawMode(T_OURS);

		// Index entry has to match ours to be considered clean
		final boolean isDirty = nonTree(modeI)
				&& !(modeO == modeI && tw.idEqual(T_INDEX, T_OURS));

		return isDirty;
	}

	private static boolean isWorktreeDirty(TreeWalk tw, WorkingTreeIterator work) {
		if (work == null)
			return false;

		final int modeF = tw.getRawMode(T_FILE);
		final int modeO = tw.getRawMode(T_OURS);

		// Worktree entry has to match ours to be considered clean
		boolean isDirty = work.isModeDifferent(modeO);
		if (!isDirty && nonTree(modeF))
			isDirty = !tw.idEqual(T_FILE, T_OURS);
		// Ignore existing empty directories
		if (isDirty && modeF == FileMode.TYPE_TREE
				&& modeO == FileMode.TYPE_MISSING)
			isDirty = false;

		return isDirty;
	}

	/**
	 * Updates the index after a content merge has happened. If no conflict has
	 * occurred this includes persisting the merged content to the object
	 * database. In case of conflicts this method takes care to write the
	 * correct stages to the index.
	 *
	 * @param inserter
	 * @param base
	 * @param ours
	 * @param theirs
	 * @param hasConflicts
	 * @param mergedContent
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private void updateIndex(ObjectInserter inserter, CanonicalTreeParser base,
			CanonicalTreeParser ours, CanonicalTreeParser theirs,
			boolean hasConflicts, File mergedContent)
			throws FileNotFoundException, IOException {
		if (hasConflicts) {
			/*
			 * There were merge conflicts. The file will contain conflict
			 * markers. The index will be populated with the three stages and
			 * only the workdir (if used) contains the halfways merged content.
			 */
			final byte[] rawPath = tw.getRawPath();
			add(rawPath, base, DirCacheEntry.STAGE_1, 0, 0);
			add(rawPath, ours, DirCacheEntry.STAGE_2, 0, 0);
			add(rawPath, theirs, DirCacheEntry.STAGE_3, 0, 0);
		} else {
			// no conflict occurred, the file will contain fully merged content.
			// the index will be populated with the new merged version
			DirCacheEntry dce = new DirCacheEntry(tw.getPathString());
			int newMode = mergeFileModes(tw.getRawMode(0), tw.getRawMode(1),
					tw.getRawMode(2));
			// set the mode for the new content. Fall back to REGULAR_FILE if
			// you can't merge modes of OURS and THEIRS
			dce.setFileMode((newMode == FileMode.MISSING.getBits()) ? FileMode.REGULAR_FILE
					: FileMode.fromBits(newMode));
			dce.setLastModified(mergedContent.lastModified());
			dce.setLength((int) mergedContent.length());
			InputStream is = new FileInputStream(mergedContent);
			try {
				dce.setObjectId(inserter.insert(Constants.OBJ_BLOB,
						mergedContent.length(), is));
			} finally {
				is.close();
			}
			builder.add(dce);
		}
	}

	/**
	 * Updates the working tree after a content merge occured. This will
	 * override (or write if it does not exist) the file at the given
	 * {@code path} with the given {@code mergedContent}.
	 *
	 * @param repository
	 *            Repository containing these files.
	 * @param path
	 *            Path of the file to override.
	 * @param mergedContent
	 *            New content of the file at {@code path}.
	 * @return The updated working tree file.
	 * @throws IOException
	 */
	private static File updateWorkTree(Repository repository, String path,
			File mergedContent) throws IOException {
		File workTree = repository.getWorkTree();
		if (workTree == null)
			// TODO: This should be handled by WorkingTreeIterators which
			// support write operations
			throw new UnsupportedOperationException();

		File workTreeFile = new File(workTree, path);
		File parentFolder = workTreeFile.getParentFile();
		if (!parentFolder.exists())
			parentFolder.mkdirs();
		FS.DETECTED.copyFile(mergedContent, workTreeFile);
		return workTreeFile;
	}

	private static MergeDriver findMergeDriver(Repository repository,
			String filePath, CanonicalTreeParser ours,
			CanonicalTreeParser theirs) throws IOException {
		MergeDriver driver = MergeDriverRegistry.findMergeDriver(filePath);
		if (driver == null) {
			final boolean isBinary;
			if (ours != null
					&& !ours.getEntryObjectId().equals(ObjectId.zeroId()))
				isBinary = RawText.isBinary(repository.open(
						ours.getEntryObjectId(),
						Constants.OBJ_BLOB).getCachedBytes());
			else if (theirs != null
					&& !theirs.getEntryObjectId().equals(ObjectId.zeroId()))
				isBinary = RawText.isBinary(repository.open(
						theirs.getEntryObjectId(),
						Constants.OBJ_BLOB).getCachedBytes());
			else
				isBinary = false;

			if (isBinary)
				driver = new BinaryMergeDriver();
			else
				driver = new TextMergeDriver();
		}
		return driver;
	}

	/**
	 * Try to merge filemodes. If only ours or theirs have changed the mode
	 * (compared to base) we choose that one. If ours and theirs have equal
	 * modes return that one. If also that is not the case the modes are not
	 * mergeable. Return {@link FileMode#MISSING} int that case.
	 *
	 * @param modeB
	 *            filemode found in BASE
	 * @param modeO
	 *            filemode found in OURS
	 * @param modeT
	 *            filemode found in THEIRS
	 *
	 * @return the merged filemode or {@link FileMode#MISSING} in case of a
	 *         conflict
	 */
	private int mergeFileModes(int modeB, int modeO, int modeT) {
		if (modeO == modeT)
			return modeO;
		if (modeB == modeO)
			// Base equal to Ours -> chooses Theirs if that is not missing
			return (modeT == FileMode.MISSING.getBits()) ? modeO : modeT;
		if (modeB == modeT)
			// Base equal to Theirs -> chooses Ours if that is not missing
			return (modeO == FileMode.MISSING.getBits()) ? modeT : modeO;
		return FileMode.MISSING.getBits();
	}

	private static boolean nonTree(final int mode) {
		return mode != 0 && !FileMode.TREE.equals(mode);
	}

	private static boolean isGitLink(final int mode) {
		return FileMode.GITLINK.equals(mode);
	}

	@Override
	public ObjectId getResultTreeId() {
		return (resultTree == null) ? null : resultTree.toObjectId();
	}

	/**
	 * @param commitNames
	 *            the names of the commits as they would appear in conflict
	 *            markers
	 */
	public void setCommitNames(String[] commitNames) {
		this.commitNames = commitNames;
	}

	/**
	 * @return the names of the commits as they would appear in conflict
	 *         markers.
	 */
	public String[] getCommitNames() {
		return commitNames;
	}

	/**
	 * @return the paths with conflicts. This is a subset of the files listed
	 *         by {@link #getModifiedFiles()}
	 */
	public List<String> getUnmergedPaths() {
		return unmergedPaths;
	}

	/**
	 * @return the paths of files which have been modified by this merge. A
	 *         file will be modified if a content-merge works on this path or if
	 *         the merge algorithm decides to take the theirs-version. This is a
	 *         superset of the files listed by {@link #getUnmergedPaths()}.
	 */
	public List<String> getModifiedFiles() {
		return modifiedFiles;
	}

	/**
	 * @return a map which maps the paths of files which have to be checked out
	 *         because the merge created new fully-merged content for this file
	 *         into the index. This means: the merge wrote a new stage 0 entry
	 *         for this path.
	 */
	public Map<String, DirCacheEntry> getToBeCheckedOut() {
		return toBeCheckedOut;
	}

	/*
	 * FIXME : Not only is this specific to the resolve strategy, it is also
	 * specific to the textual merger. Furthermore, the values of this map are
	 * unused outside of unit tests, the keys are used to give feedback to the
	 * user as to which files presented merge conflicts (only with a merge
	 * called from the command line). This info is also available from
	 * #getUnmergedPaths(). This should be removed from the implementation
	 * altogether, along with the corresponding field in
	 * org.eclipse.jgit.api.MergeResult.
	 */
	/**
	 * @return the mergeResults
	 */
	public Map<String, MergeResult<? extends Sequence>> getMergeResults() {
		return mergeResults;
	}

	/**
	 * @return lists paths causing this merge to fail (not stopped because of a
	 *         conflict). <code>null</code> is returned if this merge didn't
	 *         fail.
	 */
	public Map<String, MergeFailureReason> getFailingPaths() {
		return (failingPaths.size() == 0) ? null : failingPaths;
	}

	/**
	 * Returns whether this merge failed (i.e. not stopped because of a
	 * conflict)
	 *
	 * @return <code>true</code> if a failure occurred, <code>false</code>
	 *         otherwise
	 */
	public boolean failed() {
		return failingPaths.size() > 0;
	}

	/**
	 * Sets the DirCache which shall be used by this merger. If the DirCache is
	 * not set explicitly and if this merger doesn't work in-core, this merger
	 * will implicitly get and lock a default DirCache. If the DirCache is
	 * explicitly set the caller is responsible to lock it in advance. Finally
	 * the merger will call {@link DirCache#commit()} which requires that the
	 * DirCache is locked. If the {@link #mergeImpl()} returns without throwing
	 * an exception the lock will be released. In case of exceptions the caller
	 * is responsible to release the lock.
	 *
	 * @param dc
	 *            the DirCache to set
	 */
	public void setDirCache(DirCache dc) {
		this.dircache = dc;
		implicitDirCache = false;
	}

	/**
	 * Sets the WorkingTreeIterator to be used by this merger. If no
	 * WorkingTreeIterator is set this merger will ignore the working tree and
	 * fail if a content merge is necessary.
	 * <p>
	 * TODO: enhance WorkingTreeIterator to support write operations. Then this
	 * merger will be able to merge with a different working tree abstraction.
	 *
	 * @param workingTreeIterator
	 *            the workingTreeIt to set
	 */
	public void setWorkingTreeIterator(WorkingTreeIterator workingTreeIterator) {
		this.workingTreeIterator = workingTreeIterator;
	}


	/**
	 * The resolve conflict way of three way merging
	 *
	 * @param baseTree
	 * @param headTree
	 * @param mergeTree
	 * @return whether the trees merged cleanly
	 * @throws IOException
	 * @since 3.0
	 */
	// FIXME This is "protected"... but actually unusable by subclasses.
	protected boolean mergeTrees(AbstractTreeIterator baseTree,
			RevTree headTree, RevTree mergeTree) throws IOException {

		builder = dircache.builder();
		DirCacheBuildIterator buildIt = new DirCacheBuildIterator(builder);

		tw = new NameConflictTreeWalk(db);
		tw.addTree(baseTree);
		tw.addTree(headTree);
		tw.addTree(mergeTree);
		tw.addTree(buildIt);
		if (workingTreeIterator != null)
			tw.addTree(workingTreeIterator);

		if (!mergeTreeWalk(tw)) {
			return false;
		}

		if (!inCore) {
			// No problem found. The only thing left to be done is to
			// checkout all files from "theirs" which have been selected to
			// go into the new index.
			checkout();

			// All content-merges are successfully done. If we can now write the
			// new index we are on quite safe ground. Even if the checkout of
			// files coming from "theirs" fails the user can work around such
			// failures by checking out the index again.
			if (!builder.commit()) {
				cleanUp();
				throw new IndexWriteException();
			}
			builder = null;

		} else {
			builder.finish();
			builder = null;
		}

		if (getUnmergedPaths().isEmpty() && !failed()) {
			resultTree = dircache.writeTree(getObjectInserter());
			return true;
		} else {
			resultTree = null;
			return false;
		}
	}

	/**
	 * Process the given TreeWalk's entries.
	 *
	 * @param treeWalk
	 *            The walk to iterate over.
	 * @return Whether the tress merged cleanly.
	 * @throws IOException
	 */
	protected boolean mergeTreeWalk(TreeWalk treeWalk) throws IOException {
		while (treeWalk.next()) {
			if (!processEntry(
					treeWalk.getTree(T_BASE, CanonicalTreeParser.class),
					treeWalk.getTree(T_OURS, CanonicalTreeParser.class),
					treeWalk.getTree(T_THEIRS, CanonicalTreeParser.class),
					treeWalk.getTree(T_INDEX, DirCacheBuildIterator.class),
					(tw.getTreeCount() >= T_FILE) ? null : treeWalk.getTree(
							T_FILE, WorkingTreeIterator.class))) {
				cleanUp();
				return false;
			}
			if (treeWalk.isSubtree() && enterSubtree)
				treeWalk.enterSubtree();
		}
		return true;
	}
}
