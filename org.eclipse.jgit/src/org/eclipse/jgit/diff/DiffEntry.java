/*
 * Copyright (C) 2008-2013, Google Inc.
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
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.attributes.Attribute;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilterMarker;

/**
 * A value class representing a change to a file
 */
public class DiffEntry {
	/** Magical SHA1 used for file adds or deletes */
	static final AbbreviatedObjectId A_ZERO = AbbreviatedObjectId
			.fromObjectId(ObjectId.zeroId());

	/** Magical file name used for file adds or deletes. */
	public static final String DEV_NULL = "/dev/null"; //$NON-NLS-1$

	/** General type of change a single file-level patch describes. */
	public enum ChangeType {
		/** Add a new file to the project */
		ADD,

		/** Modify an existing file in the project (content and/or mode) */
		MODIFY,

		/** Delete an existing file from the project */
		DELETE,

		/** Rename an existing file to a new location */
		RENAME,

		/** Copy an existing file to a new location, keeping the original */
		COPY;
	}

	/** Specify the old or new side for more generalized access. */
	public enum Side {
		/** The old side of a DiffEntry. */
		OLD,

		/** The new side of a DiffEntry. */
		NEW;
	}

	/**
	 * Create an empty DiffEntry
	 */
	protected DiffEntry(){
		// reduce the visibility of the default constructor
	}

	/**
	 * Convert the TreeWalk into DiffEntry headers.
	 *
	 * @param walk
	 *            the TreeWalk to walk through. Must have exactly two trees.
	 * @return headers describing the changed files.
	 * @throws java.io.IOException
	 *             the repository cannot be accessed.
	 * @throws java.lang.IllegalArgumentException
	 *             When given TreeWalk doesn't have exactly two trees.
	 */
	public static List<DiffEntry> scan(TreeWalk walk) throws IOException {
		return scan(walk, false);
	}

	/**
	 * Convert the TreeWalk into DiffEntry headers, depending on
	 * {@code includeTrees} it will add tree objects into result or not.
	 *
	 * @param walk
	 *            the TreeWalk to walk through. Must have exactly two trees and
	 *            when {@code includeTrees} parameter is {@code true} it can't
	 *            be recursive.
	 * @param includeTrees
	 *            include tree objects.
	 * @return headers describing the changed files.
	 * @throws java.io.IOException
	 *             the repository cannot be accessed.
	 * @throws java.lang.IllegalArgumentException
	 *             when {@code includeTrees} is true and given TreeWalk is
	 *             recursive. Or when given TreeWalk doesn't have exactly two
	 *             trees
	 */
	public static List<DiffEntry> scan(TreeWalk walk, boolean includeTrees)
			throws IOException {
		return scan(walk, includeTrees, null);
	}

	/**
	 * Convert the TreeWalk into DiffEntry headers, depending on
	 * {@code includeTrees} it will add tree objects into result or not.
	 *
	 * @param walk
	 *            the TreeWalk to walk through. Must have exactly two trees and
	 *            when {@code includeTrees} parameter is {@code true} it can't
	 *            be recursive.
	 * @param includeTrees
	 *            include tree objects.
	 * @param markTreeFilters
	 *            array of tree filters which will be tested for each entry. If
	 *            an entry matches, the entry will later return true when
	 *            queried through {{@link #isMarked(int)} (with the index from
	 *            this passed array).
	 * @return headers describing the changed files.
	 * @throws java.io.IOException
	 *             the repository cannot be accessed.
	 * @throws java.lang.IllegalArgumentException
	 *             when {@code includeTrees} is true and given TreeWalk is
	 *             recursive. Or when given TreeWalk doesn't have exactly two
	 *             trees
	 * @since 2.3
	 */
	public static List<DiffEntry> scan(TreeWalk walk, boolean includeTrees,
			TreeFilter[] markTreeFilters)
			throws IOException {
		if (walk.getTreeCount() != 2)
			throw new IllegalArgumentException(
					JGitText.get().treeWalkMustHaveExactlyTwoTrees);
		if (includeTrees && walk.isRecursive())
			throw new IllegalArgumentException(
					JGitText.get().cannotBeRecursiveWhenTreesAreIncluded);

		TreeFilterMarker treeFilterMarker;
		if (markTreeFilters != null && markTreeFilters.length > 0)
			treeFilterMarker = new TreeFilterMarker(markTreeFilters);
		else
			treeFilterMarker = null;

		List<DiffEntry> r = new ArrayList<>();
		MutableObjectId idBuf = new MutableObjectId();
		while (walk.next()) {
			DiffEntry entry = new DiffEntry();

			walk.getObjectId(idBuf, 0);
			entry.oldId = AbbreviatedObjectId.fromObjectId(idBuf);

			walk.getObjectId(idBuf, 1);
			entry.newId = AbbreviatedObjectId.fromObjectId(idBuf);

			entry.oldMode = walk.getFileMode(0);
			entry.newMode = walk.getFileMode(1);
			entry.newPath = entry.oldPath = walk.getPathString();

			if (walk.getAttributesNodeProvider() != null) {
				entry.diffAttribute = walk.getAttributes()
						.get(Constants.ATTR_DIFF);
			}

			if (treeFilterMarker != null)
				entry.treeFilterMarks = treeFilterMarker.getMarks(walk);

			if (entry.oldMode == FileMode.MISSING) {
				entry.oldPath = DiffEntry.DEV_NULL;
				entry.changeType = ChangeType.ADD;
				r.add(entry);

			} else if (entry.newMode == FileMode.MISSING) {
				entry.newPath = DiffEntry.DEV_NULL;
				entry.changeType = ChangeType.DELETE;
				r.add(entry);

			} else if (!entry.oldId.equals(entry.newId)) {
				entry.changeType = ChangeType.MODIFY;
				if (RenameDetector.sameType(entry.oldMode, entry.newMode))
					r.add(entry);
				else
					r.addAll(breakModify(entry));
			} else if (entry.oldMode != entry.newMode) {
				entry.changeType = ChangeType.MODIFY;
				r.add(entry);
			}

			if (includeTrees && walk.isSubtree())
				walk.enterSubtree();
		}
		return r;
	}

	static DiffEntry add(String path, AnyObjectId id) {
		DiffEntry e = new DiffEntry();
		e.oldId = A_ZERO;
		e.oldMode = FileMode.MISSING;
		e.oldPath = DEV_NULL;

		e.newId = AbbreviatedObjectId.fromObjectId(id);
		e.newMode = FileMode.REGULAR_FILE;
		e.newPath = path;
		e.changeType = ChangeType.ADD;
		return e;
	}

	static DiffEntry delete(String path, AnyObjectId id) {
		DiffEntry e = new DiffEntry();
		e.oldId = AbbreviatedObjectId.fromObjectId(id);
		e.oldMode = FileMode.REGULAR_FILE;
		e.oldPath = path;

		e.newId = A_ZERO;
		e.newMode = FileMode.MISSING;
		e.newPath = DEV_NULL;
		e.changeType = ChangeType.DELETE;
		return e;
	}

	static DiffEntry modify(String path) {
		DiffEntry e = new DiffEntry();
		e.oldMode = FileMode.REGULAR_FILE;
		e.oldPath = path;

		e.newMode = FileMode.REGULAR_FILE;
		e.newPath = path;
		e.changeType = ChangeType.MODIFY;
		return e;
	}

	/**
	 * Breaks apart a DiffEntry into two entries, one DELETE and one ADD.
	 *
	 * @param entry
	 *            the DiffEntry to break apart.
	 * @return a list containing two entries. Calling {@link #getChangeType()}
	 *         on the first entry will return ChangeType.DELETE. Calling it on
	 *         the second entry will return ChangeType.ADD.
	 */
	static List<DiffEntry> breakModify(DiffEntry entry) {
		DiffEntry del = new DiffEntry();
		del.oldId = entry.getOldId();
		del.oldMode = entry.getOldMode();
		del.oldPath = entry.getOldPath();

		del.newId = A_ZERO;
		del.newMode = FileMode.MISSING;
		del.newPath = DiffEntry.DEV_NULL;
		del.changeType = ChangeType.DELETE;
		del.diffAttribute = entry.diffAttribute;

		DiffEntry add = new DiffEntry();
		add.oldId = A_ZERO;
		add.oldMode = FileMode.MISSING;
		add.oldPath = DiffEntry.DEV_NULL;

		add.newId = entry.getNewId();
		add.newMode = entry.getNewMode();
		add.newPath = entry.getNewPath();
		add.changeType = ChangeType.ADD;
		add.diffAttribute = entry.diffAttribute;
		return Arrays.asList(del, add);
	}

	static DiffEntry pair(ChangeType changeType, DiffEntry src, DiffEntry dst,
			int score) {
		DiffEntry r = new DiffEntry();

		r.oldId = src.oldId;
		r.oldMode = src.oldMode;
		r.oldPath = src.oldPath;

		r.newId = dst.newId;
		r.newMode = dst.newMode;
		r.newPath = dst.newPath;
		r.diffAttribute = dst.diffAttribute;

		r.changeType = changeType;
		r.score = score;

		r.treeFilterMarks = src.treeFilterMarks | dst.treeFilterMarks;

		return r;
	}

	/** File name of the old (pre-image). */
	protected String oldPath;

	/** File name of the new (post-image). */
	protected String newPath;

	/**
	 * diff filter attribute
	 *
	 * @since 4.11
	 */
	protected Attribute diffAttribute;

	/** Old mode of the file, if described by the patch, else null. */
	protected FileMode oldMode;

	/** New mode of the file, if described by the patch, else null. */
	protected FileMode newMode;

	/** General type of change indicated by the patch. */
	protected ChangeType changeType;

	/** Similarity score if {@link #changeType} is a copy or rename. */
	protected int score;

	/** ObjectId listed on the index line for the old (pre-image) */
	protected AbbreviatedObjectId oldId;

	/** ObjectId listed on the index line for the new (post-image) */
	protected AbbreviatedObjectId newId;

	/**
	 * Bitset for marked flags of tree filters passed to
	 * {@link #scan(TreeWalk, boolean, TreeFilter...)}
	 */
	private int treeFilterMarks = 0;

	/**
	 * Get the old name associated with this file.
	 * <p>
	 * The meaning of the old name can differ depending on the semantic meaning
	 * of this patch:
	 * <ul>
	 * <li><i>file add</i>: always <code>/dev/null</code></li>
	 * <li><i>file modify</i>: always {@link #getNewPath()}</li>
	 * <li><i>file delete</i>: always the file being deleted</li>
	 * <li><i>file copy</i>: source file the copy originates from</li>
	 * <li><i>file rename</i>: source file the rename originates from</li>
	 * </ul>
	 *
	 * @return old name for this file.
	 */
	public String getOldPath() {
		return oldPath;
	}

	/**
	 * Get the new name associated with this file.
	 * <p>
	 * The meaning of the new name can differ depending on the semantic meaning
	 * of this patch:
	 * <ul>
	 * <li><i>file add</i>: always the file being created</li>
	 * <li><i>file modify</i>: always {@link #getOldPath()}</li>
	 * <li><i>file delete</i>: always <code>/dev/null</code></li>
	 * <li><i>file copy</i>: destination file the copy ends up at</li>
	 * <li><i>file rename</i>: destination file the rename ends up at</li>
	 * </ul>
	 *
	 * @return new name for this file.
	 */
	public String getNewPath() {
		return newPath;
	}

	/**
	 * Get the path associated with this file.
	 *
	 * @param side
	 *            which path to obtain.
	 * @return name for this file.
	 */
	public String getPath(Side side) {
		return side == Side.OLD ? getOldPath() : getNewPath();
	}

	/**
	 * @return the {@link Attribute} determining filters to be applied.
	 * @since 4.11
	 */
	public Attribute getDiffAttribute() {
		return diffAttribute;
	}

	/**
	 * Get the old file mode
	 *
	 * @return the old file mode, if described in the patch
	 */
	public FileMode getOldMode() {
		return oldMode;
	}

	/**
	 * Get the new file mode
	 *
	 * @return the new file mode, if described in the patch
	 */
	public FileMode getNewMode() {
		return newMode;
	}

	/**
	 * Get the mode associated with this file.
	 *
	 * @param side
	 *            which mode to obtain.
	 * @return the mode.
	 */
	public FileMode getMode(Side side) {
		return side == Side.OLD ? getOldMode() : getNewMode();
	}

	/**
	 * Get the change type
	 *
	 * @return the type of change this patch makes on {@link #getNewPath()}
	 */
	public ChangeType getChangeType() {
		return changeType;
	}

	/**
	 * Get similarity score
	 *
	 * @return similarity score between {@link #getOldPath()} and
	 *         {@link #getNewPath()} if {@link #getChangeType()} is
	 *         {@link org.eclipse.jgit.diff.DiffEntry.ChangeType#COPY} or
	 *         {@link org.eclipse.jgit.diff.DiffEntry.ChangeType#RENAME}.
	 */
	public int getScore() {
		return score;
	}

	/**
	 * Get the old object id from the <code>index</code>.
	 *
	 * @return the object id; null if there is no index line
	 */
	public AbbreviatedObjectId getOldId() {
		return oldId;
	}

	/**
	 * Get the new object id from the <code>index</code>.
	 *
	 * @return the object id; null if there is no index line
	 */
	public AbbreviatedObjectId getNewId() {
		return newId;
	}

	/**
	 * Whether the mark tree filter with the specified index matched during scan
	 * or not, see {@link #scan(TreeWalk, boolean, TreeFilter...)}. Example:
	 * <p>
	 *
	 * <pre>
	 * TreeFilter filterA = ...;
	 * TreeFilter filterB = ...;
	 * List&lt;DiffEntry&gt; entries = DiffEntry.scan(walk, false, filterA, filterB);
	 * DiffEntry entry = entries.get(0);
	 * boolean filterAMatched = entry.isMarked(0);
	 * boolean filterBMatched = entry.isMarked(1);
	 * </pre>
	 * <p>
	 * Note that 0 corresponds to filterA because it was the first filter that
	 * was passed to scan.
	 * <p>
	 * To query more than one flag at once, see {@link #getTreeFilterMarks()}.
	 *
	 * @param index
	 *            the index of the tree filter to check for (must be between 0
	 *            and {@link java.lang.Integer#SIZE}).
	 * @since 2.3
	 * @return a boolean.
	 */
	public boolean isMarked(int index) {
		return (treeFilterMarks & (1L << index)) != 0;
	}

	/**
	 * Get the raw tree filter marks, as set during
	 * {@link #scan(TreeWalk, boolean, TreeFilter...)}. See
	 * {@link #isMarked(int)} to query each mark individually.
	 *
	 * @return the bitset of tree filter marks
	 * @since 2.3
	 */
	public int getTreeFilterMarks() {
		return treeFilterMarks;
	}

	/**
	 * Get the object id.
	 *
	 * @param side
	 *            the side of the id to get.
	 * @return the object id; null if there is no index line
	 */
	public AbbreviatedObjectId getId(Side side) {
		return side == Side.OLD ? getOldId() : getNewId();
	}

	/** {@inheritDoc} */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("DiffEntry[");
		buf.append(changeType);
		buf.append(" ");
		switch (changeType) {
		case ADD:
			buf.append(newPath);
			break;
		case COPY:
			buf.append(oldPath + "->" + newPath);
			break;
		case DELETE:
			buf.append(oldPath);
			break;
		case MODIFY:
			buf.append(oldPath);
			break;
		case RENAME:
			buf.append(oldPath + "->" + newPath);
			break;
		}
		buf.append("]");
		return buf.toString();
	}
}
