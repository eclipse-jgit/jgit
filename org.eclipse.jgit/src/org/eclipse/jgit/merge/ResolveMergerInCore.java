/*
 * Copyright (C) 2010, Dmitry Fink <dmitry.fink@gmail.com>
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.diff.Sequence;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuildIterator;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.NameConflictTreeWalk;

/**
 * A three-way merger performing a content-merge if necessary
 */
public class ResolveMergerInCore extends ThreeWayMerger {
	/**
	 * If the merge fails abnormally (means: not because of unresolved
	 * conflicts) this enum is used to explain why it failed
	 */
	public enum MergeFailureReason {
		/** the merge failed because of a dirty index */
		DIRTY_INDEX,
		/** the merge failed because of a dirty workingtree */
		DIRTY_WORKTREE
	}

	private NameConflictTreeWalk tw;

	private String commitNames[];

	private static final int T_BASE = 0;

	private static final int T_OURS = 1;

	private static final int T_THEIRS = 2;

	private static final int T_INDEX = 3;

	private DirCacheBuilder builder;

	private ObjectId resultTree;

	private List<String> unmergedPathes = new ArrayList<String>();

	private List<String> modifiedFiles = new LinkedList<String>();

	private Map<String, MergeResult<? extends Sequence>> mergeResults = new HashMap<String, MergeResult<? extends Sequence>>();

	private Map<String, MergeFailureReason> failingPathes = new HashMap<String, MergeFailureReason>();

	private ObjectInserter oi;

	private boolean enterSubtree;

	private final DirCache dircache;

	/**
	 * @param local
	 */
	protected ResolveMergerInCore(Repository local) {
		super(local);
		commitNames = new String[] { "BASE", "OURS", "THEIRS" };
		oi = getObjectInserter();
		dircache = DirCache.newInCore();
	}

	@Override
	protected boolean mergeImpl() throws IOException {

		builder = dircache.builder();
		DirCacheBuildIterator buildIt = new DirCacheBuildIterator(builder);

		tw = new NameConflictTreeWalk(db);
		tw.reset();
		tw.addTree(mergeBase());
		tw.addTree(sourceTrees[0]);
		tw.addTree(sourceTrees[1]);
		tw.addTree(buildIt);

		while (tw.next()) {
			if (!processEntry(
					tw.getTree(T_BASE, CanonicalTreeParser.class),
					tw.getTree(T_OURS, CanonicalTreeParser.class),
					tw.getTree(T_THEIRS, CanonicalTreeParser.class),
					tw.getTree(T_INDEX, DirCacheBuildIterator.class))) {
				return false;
			}
			if (tw.isSubtree() && enterSubtree)
				tw.enterSubtree();
		}

		builder.finish();
		builder = null;

		if (getUnmergedPathes().isEmpty()) {
			resultTree = dircache.writeTree(oi);
			oi.flush();
			return true;
		} else {
			resultTree = null;
			return false;
		}
	}

	/**
	 * adds a new path with the specified stage to the index builder
	 *
	 * @param path
	 * @param p
	 * @param stage
	 * @return the entry which was added to the index
	 */
	private DirCacheEntry add(byte[] path, CanonicalTreeParser p, int stage) {
		if (p != null && !p.getEntryFileMode().equals(FileMode.TREE)) {
			DirCacheEntry e = new DirCacheEntry(path, stage);
			e.setFileMode(p.getEntryFileMode());
			e.setObjectId(p.getEntryObjectId());
			builder.add(e);
			return e;
		}
		return null;
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
	 * @return <code>false</code> if the merge will fail because the index entry
	 *         didn't match ours or the working-dir file was dirty and a
	 *         conflict occured
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws CorruptObjectException
	 * @throws IOException
	 */
	private boolean processEntry(CanonicalTreeParser base,
			CanonicalTreeParser ours, CanonicalTreeParser theirs,
			DirCacheBuildIterator index)
			throws MissingObjectException, IncorrectObjectTypeException,
			CorruptObjectException, IOException {
		enterSubtree = true;
		final int modeO = tw.getRawMode(T_OURS);
		final int modeI = tw.getRawMode(T_INDEX);

		// Each index entry has to match ours, means: it has to be clean
		if (nonTree(modeI) && !(tw.idEqual(T_INDEX, T_OURS) && modeO == modeI)) {
			failingPathes.put(tw.getPathString(),
					MergeFailureReason.DIRTY_INDEX);
			return false;
		}

		final int modeT = tw.getRawMode(T_THEIRS);
		if (nonTree(modeO) && modeO == modeT && tw.idEqual(T_OURS, T_THEIRS)) {
			// ours and theirs are equal: it doesn'nt matter
			// which one we choose. OURS is choosen here.
			add(tw.getRawPath(), ours, DirCacheEntry.STAGE_0);
			return true;
		}

		final int modeB = tw.getRawMode(T_BASE);
		if (nonTree(modeO) && modeB == modeT && tw.idEqual(T_BASE, T_THEIRS)) {
			// THEIRS was not changed compared to base. All changes must be in
			// OURS. Choose OURS.
			add(tw.getRawPath(), ours, DirCacheEntry.STAGE_0);
			return true;
		}

		if (nonTree(modeT) && modeB == modeO && tw.idEqual(T_BASE, T_OURS)) {
			// OURS was not changed compared to base. All changes must be in
			// THEIRS. Choose THEIRS.
			add(tw.getRawPath(), theirs, DirCacheEntry.STAGE_0);
			return true;
		}

		if (tw.isSubtree()) {
			// file/folder conflicts: here I want to detect only file/folder
			// conflict between ours and theirs. file/folder conflicts between
			// base/index/workingTree and something else are not relevant or
			// detected later
			if (nonTree(modeO) && !nonTree(modeT)) {
				if (nonTree(modeB))
					add(tw.getRawPath(), base, DirCacheEntry.STAGE_1);
				add(tw.getRawPath(), ours, DirCacheEntry.STAGE_2);
				unmergedPathes.add(tw.getPathString());
				enterSubtree = false;
				return true;
			}
			if (nonTree(modeT) && !nonTree(modeO)) {
				if (nonTree(modeB))
					add(tw.getRawPath(), base, DirCacheEntry.STAGE_1);
				add(tw.getRawPath(), theirs, DirCacheEntry.STAGE_3);
				unmergedPathes.add(tw.getPathString());
				enterSubtree = false;
				return true;
			}

			// ours and theirs are both folders or both files (and treewalk
			// tells us we are in a subtree because of index or working-dir).
			// If they are both folders no content-merge is required - we can
			// return here.
			if (!nonTree(modeO))
				return true;

			// ours and theirs are both files, just fall out of the if block
			// and do the content merge
		}

		if (nonTree(modeO) && nonTree(modeT)) {
			if (!contentMerge(base, ours, theirs)) {
				unmergedPathes.add(tw.getPathString());
			}
			modifiedFiles.add(tw.getPathString());
		}
		return true;
	}

	private boolean contentMerge(CanonicalTreeParser base,
			CanonicalTreeParser ours, CanonicalTreeParser theirs)
			throws FileNotFoundException, IllegalStateException, IOException {
		MergeFormatter fmt = new MergeFormatter();

		// do the merge
		MergeResult<RawText> result = MergeAlgorithm.merge(
				RawTextComparator.DEFAULT,
				getRawText(base.getEntryObjectId(), db),
				getRawText(ours.getEntryObjectId(), db),
				getRawText(theirs.getEntryObjectId(), db));

		if (result.containsConflicts()) {
			add(tw.getRawPath(), base, DirCacheEntry.STAGE_1);
			add(tw.getRawPath(), ours, DirCacheEntry.STAGE_2);
			add(tw.getRawPath(), theirs, DirCacheEntry.STAGE_3);
			mergeResults.put(tw.getPathString(), result);
			return false;
		} else {
			File of = File.createTempFile("merge_", "_temp", null);
			FileOutputStream fos = new FileOutputStream(of);

			try {
				fmt.formatMerge(fos, result, Arrays.asList(commitNames),
						Constants.CHARACTER_ENCODING);
			} finally {
				fos.close();
			}
			DirCacheEntry dce = new DirCacheEntry(tw.getPathString());
			dce.setFileMode(tw.getFileMode(0));
			dce.setLastModified(of.lastModified());
			dce.setLength((int) of.length());
			InputStream is = new FileInputStream(of);
			try {
				dce.setObjectId(oi.insert(Constants.OBJ_BLOB, of.length(), is));
			} finally {
				is.close();
				of.delete();
			}
			builder.add(dce);
			return true;
		}
	}

	private static RawText getRawText(ObjectId id, Repository db)
			throws IOException {
		if (id.equals(ObjectId.zeroId()))
			return new RawText(new byte[] {});
		return new RawText(db.open(id, Constants.OBJ_BLOB).getCachedBytes());
	}

	private static boolean nonTree(final int mode) {
		return mode != 0 && !FileMode.TREE.equals(mode);
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
	 * @return the paths with conflicts. This is a subset of the files listed by
	 *         {@link #getModifiedFiles()}
	 */
	public List<String> getUnmergedPathes() {
		return unmergedPathes;
	}

	/**
	 * @return the paths of files which have been modified by this merge. A file
	 *         will be modified if a content-merge works on this path or if the
	 *         merge algorithm decides to take the theirs-version. This is a
	 *         superset of the files listed by {@link #getUnmergedPathes()}.
	 */
	public List<String> getModifiedFiles() {
		return modifiedFiles;
	}

	/**
	 * @return the mergeResults
	 */
	public Map<String, MergeResult<? extends Sequence>> getMergeResults() {
		return mergeResults;
	}

	/**
	 * @return lists paths causing this merge to fail abnormally (not because of
	 *         a conflict). <code>null</code> is returned if this merge didn't
	 *         fail abnormally.
	 */
	public Map<String, MergeFailureReason> getFailingPathes() {
		return (failingPathes.size() == 0) ? null : failingPathes;
	}
}
