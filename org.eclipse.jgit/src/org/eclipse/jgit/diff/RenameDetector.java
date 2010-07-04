/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.diff;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;

/** Detect and resolve object renames. */
public class RenameDetector {
	private static final int EXACT_RENAME_SCORE = 100;

	private static final Comparator<DiffEntry> DIFF_COMPARATOR = new Comparator<DiffEntry>() {
		public int compare(DiffEntry a, DiffEntry b) {
			int cmp = nameOf(a).compareTo(nameOf(b));
			if (cmp == 0)
				cmp = sortOf(a.getChangeType()) - sortOf(b.getChangeType());
			return cmp;
		}

		private String nameOf(DiffEntry ent) {
			// Sort by the new name, unless the change is a delete. On
			// deletes the new name is /dev/null, so we sort instead by
			// the old name.
			//
			if (ent.changeType == ChangeType.DELETE)
				return ent.oldName;
			return ent.newName;
		}

		private int sortOf(ChangeType changeType) {
			// Sort deletes before adds so that a major type change for
			// a file path (such as symlink to regular file) will first
			// remove the path, then add it back with the new type.
			//
			switch (changeType) {
			case DELETE:
				return 1;
			case ADD:
				return 2;
			default:
				return 10;
			}
		}
	};

	private final List<DiffEntry> entries = new ArrayList<DiffEntry>();

	private List<DiffEntry> deleted = new ArrayList<DiffEntry>();

	private List<DiffEntry> added = new ArrayList<DiffEntry>();

	private boolean done;

	private final Repository repo;

	/** Similarity score required to pair an add/delete as a rename. */
	private int renameScore = 60;

	/** Limit in the number of files to consider for renames. */
	private int renameLimit;

	/** Set if the number of adds or deletes was over the limit. */
	private boolean overRenameLimit;

	/**
	 * Create a new rename detector for the given repository
	 *
	 * @param repo
	 *            the repository to use for rename detection
	 */
	public RenameDetector(Repository repo) {
		this.repo = repo;

		DiffConfig cfg = repo.getConfig().get(DiffConfig.KEY);
		renameLimit = cfg.getRenameLimit();
	}

	/**
	 * @return minimum score required to pair an add/delete as a rename. The
	 *         score ranges are within the bounds of (0, 100).
	 */
	public int getRenameScore() {
		return renameScore;
	}

	/**
	 * Set the minimum score required to pair an add/delete as a rename.
	 * <p>
	 * When comparing two files together their score must be greater than or
	 * equal to the rename score for them to be considered a rename match. The
	 * score is computed based on content similarity, so a score of 60 implies
	 * that approximately 60% of the bytes in the files are identical.
	 *
	 * @param score
	 *            new rename score, must be within (0, 100).
	 */
	public void setRenameScore(int score) {
		if (score < 0 || score > 100)
			throw new IllegalArgumentException(
					JGitText.get().similarityScoreMustBeWithinBounds);
		renameScore = score;
	}

	/** @return limit on number of paths to perform inexact rename detection. */
	public int getRenameLimit() {
		return renameLimit;
	}

	/**
	 * Set the limit on the number of files to perform inexact rename detection.
	 * <p>
	 * The rename detector has to build a square matrix of the rename limit on
	 * each side, then perform that many file compares to determine similarity.
	 * If 1000 files are added, and 1000 files are deleted, a 1000*1000 matrix
	 * must be allocated, and 1,000,000 file compares may need to be performed.
	 *
	 * @param limit
	 *            new file limit.
	 */
	public void setRenameLimit(int limit) {
		renameLimit = limit;
	}

	/**
	 * Check if the detector is over the rename limit.
	 * <p>
	 * This method can be invoked either before or after {@code getEntries} has
	 * been used to perform rename detection.
	 *
	 * @return true if the detector has more file additions or removals than the
	 *         rename limit is currently set to. In such configurations the
	 *         detector will skip expensive computation.
	 */
	public boolean isOverRenameLimit() {
		if (done)
			return overRenameLimit;
		int cnt = Math.max(added.size(), deleted.size());
		return getRenameLimit() != 0 && getRenameLimit() < cnt;
	}

	/**
	 * Add entries to be considered for rename detection.
	 *
	 * @param entriesToAdd
	 *            one or more entries to add.
	 * @throws IllegalStateException
	 *             if {@code getEntries} was already invoked.
	 */
	public void addAll(Collection<DiffEntry> entriesToAdd) {
		if (done)
			throw new IllegalStateException(JGitText.get().renamesAlreadyFound);

		for (DiffEntry entry : entriesToAdd) {
			switch (entry.getChangeType()) {
			case ADD:
				added.add(entry);
				break;

			case DELETE:
				deleted.add(entry);
				break;

			case MODIFY:
				if (sameType(entry.getOldMode(), entry.getNewMode()))
					entries.add(entry);
				else
					entries.addAll(DiffEntry.breakModify(entry));
				break;

			case COPY:
			case RENAME:
			default:
				entriesToAdd.add(entry);
			}
		}
	}

	/**
	 * Add an entry to be considered for rename detection.
	 *
	 * @param entry
	 *            to add.
	 * @throws IllegalStateException
	 *             if {@code getEntries} was already invoked.
	 */
	public void add(DiffEntry entry) {
		addAll(Collections.singletonList(entry));
	}

	/**
	 * Detect renames in the current file set.
	 * <p>
	 * This convenience function runs without a progress monitor.
	 *
	 * @return an unmodifiable list of {@link DiffEntry}s representing all files
	 *         that have been changed.
	 * @throws IOException
	 *             file contents cannot be read from the repository.
	 */
	public List<DiffEntry> compute() throws IOException {
		return compute(NullProgressMonitor.INSTANCE);
	}

	/**
	 * Detect renames in the current file set.
	 *
	 * @param pm
	 *            report progress during the detection phases.
	 * @return an unmodifiable list of {@link DiffEntry}s representing all files
	 *         that have been changed.
	 * @throws IOException
	 *             file contents cannot be read from the repository.
	 */
	public List<DiffEntry> compute(ProgressMonitor pm) throws IOException {
		if (!done) {
			done = true;

			if (pm == null)
				pm = NullProgressMonitor.INSTANCE;
			findExactRenames(pm);
			findContentRenames(pm);

			entries.addAll(added);
			added = null;

			entries.addAll(deleted);
			deleted = null;

			Collections.sort(entries, DIFF_COMPARATOR);
		}
		return Collections.unmodifiableList(entries);
	}

	private void findContentRenames(ProgressMonitor pm) throws IOException {
		int cnt = Math.max(added.size(), deleted.size());
		if (cnt == 0)
			return;

		if (getRenameLimit() == 0 || cnt <= getRenameLimit()) {
			SimilarityRenameDetector d;

			d = new SimilarityRenameDetector(repo, deleted, added);
			d.setRenameScore(getRenameScore());
			d.compute(pm);
			deleted = d.getLeftOverSources();
			added = d.getLeftOverDestinations();
			entries.addAll(d.getMatches());
		} else {
			overRenameLimit = true;
		}
	}

	@SuppressWarnings("unchecked")
	private void findExactRenames(ProgressMonitor pm) {
		if (added.isEmpty() || deleted.isEmpty())
			return;

		pm.beginTask(JGitText.get().renamesFindingExact, //
				added.size() + deleted.size());

		HashMap<AbbreviatedObjectId, Object> map = new HashMap<AbbreviatedObjectId, Object>();
		for (DiffEntry del : deleted) {
			Object old = map.put(del.oldId, del);
			if (old instanceof DiffEntry) {
				ArrayList<DiffEntry> list = new ArrayList<DiffEntry>(2);
				list.add((DiffEntry) old);
				list.add(del);
				map.put(del.oldId, list);

			} else if (old != null) {
				// Must be a list of DiffEntries
				((List) old).add(del);
				map.put(del.oldId, old);
			}
			pm.update(1);
		}

		ArrayList<DiffEntry> left = new ArrayList<DiffEntry>(added.size());
		for (DiffEntry dst : added) {
			Object del = map.get(dst.newId);
			if (del instanceof DiffEntry) {
				DiffEntry e = (DiffEntry) del;
				if (sameType(e.oldMode, dst.newMode)) {
					if (e.changeType == ChangeType.DELETE) {
						e.changeType = ChangeType.RENAME;
						entries.add(exactRename(e, dst));
					} else {
						entries.add(exactCopy(e, dst));
					}
				} else {
					left.add(dst);
				}

			} else if (del != null) {
				List<DiffEntry> list = (List<DiffEntry>) del;
				DiffEntry best = null;
				for (DiffEntry e : list) {
					if (best == null && sameType(e.oldMode, dst.newMode))
						best = e;
				}
				if (best != null) {
					if (best.changeType == ChangeType.DELETE) {
						best.changeType = ChangeType.RENAME;
						entries.add(exactRename(best, dst));
					} else {
						entries.add(exactCopy(best, dst));
					}
				} else {
					left.add(dst);
				}

			} else {
				left.add(dst);
			}
			pm.update(1);
		}
		added = left;

		deleted = new ArrayList<DiffEntry>(map.size());
		for (Object o : map.values()) {
			if (o instanceof DiffEntry) {
				DiffEntry e = (DiffEntry) o;
				if (e.changeType == ChangeType.DELETE)
					deleted.add(e);
			} else {
				List<DiffEntry> list = (List<DiffEntry>) o;
				for (DiffEntry e : list) {
					if (e.changeType == ChangeType.DELETE)
						deleted.add(e);
				}
			}
		}
		pm.endTask();
	}

	static boolean sameType(FileMode a, FileMode b) {
		// Files have to be of the same type in order to rename them.
		// We would never want to rename a file to a gitlink, or a
		// symlink to a file.
		//
		int aType = a.getBits() & FileMode.TYPE_MASK;
		int bType = b.getBits() & FileMode.TYPE_MASK;
		return aType == bType;
	}

	private static DiffEntry exactRename(DiffEntry src, DiffEntry dst) {
		return DiffEntry.pair(ChangeType.RENAME, src, dst, EXACT_RENAME_SCORE);
	}

	private static DiffEntry exactCopy(DiffEntry src, DiffEntry dst) {
		return DiffEntry.pair(ChangeType.COPY, src, dst, EXACT_RENAME_SCORE);
	}
}
