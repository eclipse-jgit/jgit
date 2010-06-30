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

import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.FileMode;

/** A value class representing a change to a file */
public class DiffEntry {

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

}