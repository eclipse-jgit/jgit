/*
 * Copyright (C) 2008-2010, Google Inc.
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

import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.treewalk.TreeWalk;

/** A value class representing a change to a file */
public class DiffEntry {
	/**	Magical SHA1 used for file adds or deletes */
	static final AbbreviatedObjectId A_ZERO = AbbreviatedObjectId
			.fromObjectId(ObjectId.zeroId());

	/** Magical file name used for file adds or deletes. */
	public static final String DEV_NULL = "/dev/null";

	/** General type of change a single file-level patch describes. */
	public static enum ChangeType {
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
	 * @throws IOException
	 *             the repository cannot be accessed.
	 */
	public static List<DiffEntry> scan(TreeWalk walk) throws IOException {
		List<DiffEntry> r = new ArrayList<DiffEntry>();
		MutableObjectId idBuf = new MutableObjectId();
		while (walk.next()) {
			DiffEntry entry = new DiffEntry();

			walk.getObjectId(idBuf, 0);
			entry.oldId = AbbreviatedObjectId.fromObjectId(idBuf);

			walk.getObjectId(idBuf, 1);
			entry.newId = AbbreviatedObjectId.fromObjectId(idBuf);

			entry.oldMode = walk.getFileMode(0);
			entry.newMode = walk.getFileMode(1);
			entry.newName = entry.oldName = walk.getPathString();

			if (entry.oldMode == FileMode.MISSING) {
				entry.oldName = DiffEntry.DEV_NULL;
				entry.changeType = ChangeType.ADD;
				r.add(entry);

			} else if (entry.newMode == FileMode.MISSING) {
				entry.newName = DiffEntry.DEV_NULL;
				entry.changeType = ChangeType.DELETE;
				r.add(entry);

			} else {
				entry.changeType = ChangeType.MODIFY;
				if (RenameDetector.sameType(entry.oldMode, entry.newMode))
					r.add(entry);
				else
					r.addAll(breakModify(entry));
			}
		}
		return r;
	}

	static DiffEntry add(String path, AnyObjectId id) {
		DiffEntry e = new DiffEntry();
		e.oldId = A_ZERO;
		e.oldMode = FileMode.MISSING;
		e.oldName = DEV_NULL;

		e.newId = AbbreviatedObjectId.fromObjectId(id);
		e.newMode = FileMode.REGULAR_FILE;
		e.newName = path;
		e.changeType = ChangeType.ADD;
		return e;
	}

	static DiffEntry delete(String path, AnyObjectId id) {
		DiffEntry e = new DiffEntry();
		e.oldId = AbbreviatedObjectId.fromObjectId(id);
		e.oldMode = FileMode.REGULAR_FILE;
		e.oldName = path;

		e.newId = A_ZERO;
		e.newMode = FileMode.MISSING;
		e.newName = DEV_NULL;
		e.changeType = ChangeType.DELETE;
		return e;
	}

	static DiffEntry modify(String path) {
		DiffEntry e = new DiffEntry();
		e.oldMode = FileMode.REGULAR_FILE;
		e.oldName = path;

		e.newMode = FileMode.REGULAR_FILE;
		e.newName = path;
		e.changeType = ChangeType.MODIFY;
		return e;
	}

	/**
	 * Breaks apart a DiffEntry two DiffEntrys, one ADD and one DELETE.
	 *
	 * @param entry
	 *            the DiffEntry to break apart.
	 * @return a list containing two DiffEntrys. Calling
	 *         {@link #getChangeType()} on the first entry will return
	 *         ChangeType.DELETE. Calling it on the second entry will return
	 *         ChangeType.ADD.
	 */
	static List<DiffEntry> breakModify(DiffEntry entry) {
		DiffEntry del = new DiffEntry();
		del.oldId = entry.getOldId();
		del.oldMode = entry.getOldMode();
		del.oldName = entry.getOldName();

		del.newId = A_ZERO;
		del.newMode = FileMode.MISSING;
		del.newName = DiffEntry.DEV_NULL;
		del.changeType = ChangeType.DELETE;

		DiffEntry add = new DiffEntry();
		add.oldId = A_ZERO;
		add.oldMode = FileMode.MISSING;
		add.oldName = DiffEntry.DEV_NULL;

		add.newId = entry.getNewId();
		add.newMode = entry.getNewMode();
		add.newName = entry.getNewName();
		add.changeType = ChangeType.ADD;
		return Arrays.asList(del, add);
	}

	static DiffEntry pair(ChangeType changeType, DiffEntry src, DiffEntry dst,
			int score) {
		DiffEntry r = new DiffEntry();

		r.oldId = src.oldId;
		r.oldMode = src.oldMode;
		r.oldName = src.oldName;

		r.newId = dst.newId;
		r.newMode = dst.newMode;
		r.newName = dst.newName;

		r.changeType = changeType;
		r.score = score;

		return r;
	}

	/** File name of the old (pre-image). */
	protected String oldName;

	/** File name of the new (post-image). */
	protected String newName;

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
	 * Get the old name associated with this file.
	 * <p>
	 * The meaning of the old name can differ depending on the semantic meaning
	 * of this patch:
	 * <ul>
	 * <li><i>file add</i>: always <code>/dev/null</code></li>
	 * <li><i>file modify</i>: always {@link #getNewName()}</li>
	 * <li><i>file delete</i>: always the file being deleted</li>
	 * <li><i>file copy</i>: source file the copy originates from</li>
	 * <li><i>file rename</i>: source file the rename originates from</li>
	 * </ul>
	 *
	 * @return old name for this file.
	 */
	public String getOldName() {
		return oldName;
	}

	/**
	 * Get the new name associated with this file.
	 * <p>
	 * The meaning of the new name can differ depending on the semantic meaning
	 * of this patch:
	 * <ul>
	 * <li><i>file add</i>: always the file being created</li>
	 * <li><i>file modify</i>: always {@link #getOldName()}</li>
	 * <li><i>file delete</i>: always <code>/dev/null</code></li>
	 * <li><i>file copy</i>: destination file the copy ends up at</li>
	 * <li><i>file rename</i>: destination file the rename ends up at/li>
	 * </ul>
	 *
	 * @return new name for this file.
	 */
	public String getNewName() {
		return newName;
	}

	/** @return the old file mode, if described in the patch */
	public FileMode getOldMode() {
		return oldMode;
	}

	/** @return the new file mode, if described in the patch */
	public FileMode getNewMode() {
		return newMode;
	}

	/** @return the type of change this patch makes on {@link #getNewName()} */
	public ChangeType getChangeType() {
		return changeType;
	}

	/**
	 * @return similarity score between {@link #getOldName()} and
	 *         {@link #getNewName()} if {@link #getChangeType()} is
	 *         {@link ChangeType#COPY} or {@link ChangeType#RENAME}.
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

	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("DiffEntry[");
		buf.append(changeType);
		buf.append(" ");
		switch (changeType) {
		case ADD:
			buf.append(newName);
			break;
		case COPY:
			buf.append(oldName + "->" + newName);
			break;
		case DELETE:
			buf.append(oldName);
			break;
		case MODIFY:
			buf.append(oldName);
			break;
		case RENAME:
			buf.append(oldName + "->" + newName);
			break;
		}
		buf.append("]");
		return buf.toString();
	}
}