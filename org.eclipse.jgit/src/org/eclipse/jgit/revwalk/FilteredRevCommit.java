/*
 * Copyright (C) 2022, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.revwalk;

import org.eclipse.jgit.lib.AnyObjectId;

/**
 * A filtered commit reference that overrides its parent in the DAG.
 *
 * @since 6.5
 */
public class FilteredRevCommit extends RevCommit {
	private RevCommit[] overriddenParents;

	/**
	 * Create a new commit reference wrapping an underlying commit reference.
	 *
	 * @param commit
	 *            commit that is being wrapped
	 */
	public FilteredRevCommit(AnyObjectId commit) {
		this(commit, NO_PARENTS);
	}


	/**
	 * Create a new commit reference wrapping an underlying commit reference.
	 *
	 * @param commit
	 *            commit that is being wrapped
	 */
	public FilteredRevCommit(AnyObjectId commit) {
		this(commit, NO_PARENTS);
	}

	/**
	 * Create a new commit reference wrapping a newly created commit from the
	 * given id.
	 *
	 * @param id
	 *            A new commit is created for the given id.
	 * @param parents
	 *            overridden parents for the commit
	 */
	public FilteredRevCommit(AnyObjectId id, RevCommit... parents) {
		super(id);
		this.overriddenParents = parents;
		this.parents = NO_PARENTS;
	}

	/**
	 * Update parents on the commit
	 *
	 * @param overriddenParents
	 *            parents to be overwritten
	 */
	public void setParents(RevCommit... overriddenParents) {
		this.overriddenParents = overriddenParents;
	}

	/**
	 * Get the number of parent commits listed in this commit.
	 *
	 * @return number of parents; always a positive value but can be 0 if it has
	 *         no parents.
	 */
	@Override
	public int getParentCount() {
		return overriddenParents.length;
	}

	/**
	 * Get the nth parent from this commit's parent list.
	 *
	 * @param nth
	 *            parent index to obtain. Must be in the range 0 through
	 *            {@link #getParentCount()}-1.
	 * @return the specified parent.
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *             an invalid parent index was specified.
	 */
	@Override
	public RevCommit getParent(int nth) {
		return overriddenParents[nth];
	}

	/**
	 * Obtain an array of all parents (<b>NOTE - THIS IS NOT A COPY</b>).
	 *
	 * <p>
	 * This method is exposed only to provide very fast, efficient access to
	 * this commit's parent list. Applications relying on this list should be
	 * very careful to ensure they do not modify its contents during their use
	 * of it.
	 *
	 * @return the array of parents.
	 */
	@Override
	public RevCommit[] getParents() {
		return overriddenParents;
	}
}
