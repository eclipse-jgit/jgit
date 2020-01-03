/*
 * Copyright (C) 2011, 2013 Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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

	private final boolean hasUncommittedChanges;

	/**
	 * Constructor for Status.
	 *
	 * @param diff
	 *            the {@link org.eclipse.jgit.lib.IndexDiff} having the status
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
	 * Whether the status is clean
	 *
	 * @return {@code true} if no differences exist between the working-tree,
	 *         the index, and the current HEAD, {@code false} if differences do
	 *         exist
	 */
	public boolean isClean() {
		return clean;
	}

	/**
	 * Whether there are uncommitted changes
	 *
	 * @return {@code true} if any tracked file is changed
	 * @since 3.2
	 */
	public boolean hasUncommittedChanges() {
		return hasUncommittedChanges;
	}

	/**
	 * Get files added to the index
	 *
	 * @return list of files added to the index, not in HEAD (e.g. what you get
	 *         if you call {@code git add ...} on a newly created file)
	 */
	public Set<String> getAdded() {
		return Collections.unmodifiableSet(diff.getAdded());
	}

	/**
	 * Get changed files from HEAD to index
	 *
	 * @return list of files changed from HEAD to index (e.g. what you get if
	 *         you modify an existing file and call 'git add ...' on it)
	 */
	public Set<String> getChanged() {
		return Collections.unmodifiableSet(diff.getChanged());
	}

	/**
	 * Get removed files
	 *
	 * @return list of files removed from index, but in HEAD (e.g. what you get
	 *         if you call 'git rm ...' on a existing file)
	 */
	public Set<String> getRemoved() {
		return Collections.unmodifiableSet(diff.getRemoved());
	}

	/**
	 * Get missing files
	 *
	 * @return list of files in index, but not filesystem (e.g. what you get if
	 *         you call 'rm ...' on a existing file)
	 */
	public Set<String> getMissing() {
		return Collections.unmodifiableSet(diff.getMissing());
	}

	/**
	 * Get modified files relative to the index
	 *
	 * @return list of files modified on disk relative to the index (e.g. what
	 *         you get if you modify an existing file without adding it to the
	 *         index)
	 */
	public Set<String> getModified() {
		return Collections.unmodifiableSet(diff.getModified());
	}

	/**
	 * Get untracked files
	 *
	 * @return list of files that are not ignored, and not in the index. (e.g.
	 *         what you get if you create a new file without adding it to the
	 *         index)
	 */
	public Set<String> getUntracked() {
		return Collections.unmodifiableSet(diff.getUntracked());
	}

	/**
	 * Get untracked folders
	 *
	 * @return set of directories that are not ignored, and not in the index.
	 */
	public Set<String> getUntrackedFolders() {
		return Collections.unmodifiableSet(diff.getUntrackedFolders());
	}

	/**
	 * Get conflicting files
	 *
	 * @return list of files that are in conflict. (e.g what you get if you
	 *         modify file that was modified by someone else in the meantime)
	 */
	public Set<String> getConflicting() {
		return Collections.unmodifiableSet(diff.getConflicting());
	}

	/**
	 * Get StageState of conflicting files
	 *
	 * @return a map from conflicting path to its
	 *         {@link org.eclipse.jgit.lib.IndexDiff.StageState}.
	 * @since 3.0
	 */
	public Map<String, StageState> getConflictingStageState() {
		return Collections.unmodifiableMap(diff.getConflictingStageStates());
	}

	/**
	 * Get ignored files which are not in the index
	 *
	 * @return set of files and folders that are ignored and not in the index.
	 */
	public Set<String> getIgnoredNotInIndex() {
		return Collections.unmodifiableSet(diff.getIgnoredNotInIndex());
	}

	/**
	 * Get uncommitted changes, i.e. all files changed in the index or working
	 * tree
	 *
	 * @return set of files and folders that are known to the repo and changed
	 *         either in the index or in the working tree.
	 * @since 3.2
	 */
	public Set<String> getUncommittedChanges() {
		Set<String> uncommittedChanges = new HashSet<>();
		uncommittedChanges.addAll(diff.getAdded());
		uncommittedChanges.addAll(diff.getChanged());
		uncommittedChanges.addAll(diff.getRemoved());
		uncommittedChanges.addAll(diff.getMissing());
		uncommittedChanges.addAll(diff.getModified());
		uncommittedChanges.addAll(diff.getConflicting());
		return uncommittedChanges;
	}
}
