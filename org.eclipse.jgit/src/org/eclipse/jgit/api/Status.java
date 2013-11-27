/*
 * Copyright (C) 2011, 2013 Christian Halstrick <christian.halstrick@sap.com>
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
package org.eclipse.jgit.api;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.IndexDiff.StageState;

/**
 * A class telling where the working-tree, the index and the current HEAD differ
 * from each other. Collections are exposed containing the paths of the modified
 * files. E.g. to find out which files are dirty in the working tree (modified
 * but not added) you would inspect the collection returned by
 * {@link #getModified()}.
 * <p>
 * The same path can be returned by multiple getters. E.g. if a modification has
 * been added to the index and afterwards the corresponding working tree file is
 * again modified this path will be returned by {@link #getModified()} and
 * {@link #getChanged()}
 */
public class Status {
	private final IndexDiff diff;

	private final boolean clean;

	private final boolean hasUncommittedChanges;;

	/**
	 * @param diff
	 */
	public Status(IndexDiff diff) {
		super();
		this.diff = diff;
		hasUncommittedChanges = !diff.getAdded().isEmpty() //
				|| !diff.getChanged().isEmpty() //
				|| !diff.getRemoved().isEmpty() //
				|| !diff.getMissing().isEmpty() //
				|| !diff.getModified().isEmpty() //
				|| !diff.getConflicting().isEmpty();
		clean = !hasUncommittedChanges //
				&& diff.getUntracked().isEmpty();
	}

	/**
	 * @return true if no differences exist between the working-tree, the index,
	 *         and the current HEAD, false if differences do exist
	 */
	public boolean isClean() {
		return clean;
	}

	/**
	 * @return true all the tracked files are unchanged
	 */
	public boolean hasUncommittedChanges() {
		return hasUncommittedChanges;
	}

	/**
	 * @return list of files added to the index, not in HEAD (e.g. what you get
	 *         if you call 'git add ...' on a newly created file)
	 */
	public Set<String> getAdded() {
		return Collections.unmodifiableSet(diff.getAdded());
	}

	/**
	 * @return list of files changed from HEAD to index (e.g. what you get if
	 *         you modify an existing file and call 'git add ...' on it)
	 */
	public Set<String> getChanged() {
		return Collections.unmodifiableSet(diff.getChanged());
	}

	/**
	 * @return list of files removed from index, but in HEAD (e.g. what you get
	 *         if you call 'git rm ...' on a existing file)
	 */
	public Set<String> getRemoved() {
		return Collections.unmodifiableSet(diff.getRemoved());
	}

	/**
	 * @return list of files in index, but not filesystem (e.g. what you get if
	 *         you call 'rm ...' on a existing file)
	 */
	public Set<String> getMissing() {
		return Collections.unmodifiableSet(diff.getMissing());
	}

	/**
	 * @return list of files modified on disk relative to the index (e.g. what
	 *         you get if you modify an existing file without adding it to the
	 *         index)
	 */
	public Set<String> getModified() {
		return Collections.unmodifiableSet(diff.getModified());
	}

	/**
	 * @return list of files that are not ignored, and not in the index. (e.g.
	 *         what you get if you create a new file without adding it to the
	 *         index)
	 */
	public Set<String> getUntracked() {
		return Collections.unmodifiableSet(diff.getUntracked());
	}

	/**
	 * @return set of directories that are not ignored, and not in the index.
	 */
	public Set<String> getUntrackedFolders() {
		return Collections.unmodifiableSet(diff.getUntrackedFolders());
	}

	/**
	 * @return list of files that are in conflict. (e.g what you get if you
	 *         modify file that was modified by someone else in the meantime)
	 */
	public Set<String> getConflicting() {
		return Collections.unmodifiableSet(diff.getConflicting());
	}

	/**
	 * @return a map from conflicting path to its {@link StageState}.
	 * @since 3.0
	 */
	public Map<String, StageState> getConflictingStageState() {
		return Collections.unmodifiableMap(diff.getConflictingStageStates());
	}

	/**
	 * @return set of files and folders that are ignored and not in the index.
	 */
	public Set<String> getIgnoredNotInIndex() {
		return Collections.unmodifiableSet(diff.getIgnoredNotInIndex());
	}

	/**
	 * @return set of files and folders that are known to the repo and changed
	 *         either in the index or in the working tree.
	 */
	public Set<String> getUncommittedChanges() {
		Set<String> uncommittedChanges = new HashSet<String>();
		uncommittedChanges.addAll(diff.getAdded());
		uncommittedChanges.addAll(diff.getChanged());
		uncommittedChanges.addAll(diff.getRemoved());
		uncommittedChanges.addAll(diff.getMissing());
		uncommittedChanges.addAll(diff.getMissing());
		uncommittedChanges.addAll(diff.getConflicting());
		return uncommittedChanges;
	}
}
