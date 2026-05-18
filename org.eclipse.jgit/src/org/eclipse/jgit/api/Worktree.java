/*
 * Copyright (C) 2011, GitHub Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import java.io.File;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Represents a Git worktree.
 */
public class Worktree {
	private final File path;
	private final String branch;
	private final ObjectId commit;
	private boolean bare;
	private boolean locked;
	private String lockReason;
	private boolean prunable;
	private String prunableReason;

	/**
	 * Constructor for Worktree.
	 *
	 * @param path
	 *            path to the worktree
	 * @param branch
	 *            branch checked out in the worktree
	 * @param commit
	 *            current commit of the worktree
	 */
	public Worktree(File path, String branch, ObjectId commit) {
		this.path = path;
		this.branch = branch;
		this.commit = commit;
	}

	/**
	 * Get the path to the worktree.
	 *
	 * @return path to the worktree
	 */
	public File getPath() {
		return path;
	}

	/**
	 * Get the branch checked out in the worktree.
	 *
	 * @return branch checked out in the worktree, or null if detached
	 */
	public String getBranch() {
		return branch;
	}

	/**
	 * Get the current commit of the worktree.
	 *
	 * @return current commit of the worktree
	 */
	public ObjectId getCommit() {
		return commit;
	}

	/**
	 * Check if worktree is bare.
	 *
	 * @return true if bare
	 * @since 7.7
	 */
	public boolean isBare() {
		return bare;
	}

	/**
	 * Set whether worktree is bare.
	 *
	 * @param bare
	 *            true if bare
	 * @since 7.7
	 */
	void setBare(boolean bare) {
		this.bare = bare;
	}

	/**
	 * Check if worktree is locked.
	 *
	 * @return true if locked
	 * @since 7.7
	 */
	public boolean isLocked() {
		return locked;
	}

	/**
	 * Set whether worktree is locked.
	 *
	 * @param locked
	 *            true if locked
	 * @since 7.7
	 */
	void setLocked(boolean locked) {
		this.locked = locked;
	}

	/**
	 * Get reason why worktree is locked.
	 *
	 * @return lock reason or null
	 * @since 7.7
	 */
	public String getLockReason() {
		return lockReason;
	}

	/**
	 * Set reason why worktree is locked.
	 *
	 * @param lockReason
	 *            lock reason
	 * @since 7.7
	 */
	void setLockReason(String lockReason) {
		this.lockReason = lockReason;
	}

	/**
	 * Check if worktree is prunable.
	 *
	 * @return true if prunable
	 * @since 7.7
	 */
	public boolean isPrunable() {
		return prunable;
	}

	/**
	 * Set whether worktree is prunable.
	 *
	 * @param prunable
	 *            true if prunable
	 * @since 7.7
	 */
	void setPrunable(boolean prunable) {
		this.prunable = prunable;
	}

	/**
	 * Get reason why worktree is prunable.
	 *
	 * @return prunable reason or null
	 * @since 7.7
	 */
	public String getPrunableReason() {
		return prunableReason;
	}

	/**
	 * Set reason why worktree is prunable.
	 *
	 * @param prunableReason
	 *            prunable reason
	 * @since 7.7
	 */
	void setPrunableReason(String prunableReason) {
		this.prunableReason = prunableReason;
	}
}
