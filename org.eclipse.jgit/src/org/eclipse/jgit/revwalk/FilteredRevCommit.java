/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.revwalk;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;

/** A filtered commit reference that overrides its parent in the DAG. */
public class FilteredRevCommit extends RevCommit {
	private static final List<RevCommit> EMPTY = Collections.emptyList();

	List<RevCommit> overriddenParents;

	/**
	 * Create a new commit reference for the given id.
	 *
	 * @param id
	 *            object name for the commit.
	 */
	public FilteredRevCommit(ObjectId id) {
		this(id, EMPTY);
	}

	/**
	 * Create a new commit reference wrapping an underlying commit reference.
	 *
	 * @param commit
	 *            commit that is being wrapped
	 */
	public FilteredRevCommit(RevCommit commit) {
		this(commit, EMPTY);
	}

	/**
	 * Create a new commit reference wrapping an underlying commit reference.
	 *
	 * @param commit
	 *            commit that is being wrapped
	 * @param parents
	 *            overridden parents for the commit
	 */
	public FilteredRevCommit(RevCommit commit, List<RevCommit> parents) {
		this(commit.getId(), parents);
	}

	/**
	 * Create a new commit reference wrapping an underlying commit reference.
	 *
	 * @param id
	 *            object name for the commit.
	 * @param parents
	 *            overridden parents for the commit
	 */
	public FilteredRevCommit(ObjectId id, List<RevCommit> parents) {
		super(id);
		this.overriddenParents = parents;
	}

	/**
	 * Tells if the commit has an overridden parent.
	 *
	 * @return {@code true} if the commit's parent is overridden; {@code false}
	 *         otherwise.
	 *
	 * @since 6.3
	 */
	@Override
	public boolean hasOverriddenParents() {
		return true;
	}

	/**
	 * Update parents on the commit
	 *
	 * @param overriddenParents
	 *            parents to be overwritten
	 */
	@Override
	public void setParents(RevCommit... overriddenParents) {
		this.overriddenParents = Arrays.asList(overriddenParents);
	}

	/**
	 * Get the number of parent commits listed in this commit.
	 *
	 * @return number of parents; always a positive value but can be 0 if it has
	 *         no parents.
	 */
	@Override
	public int getParentCount() {
		return overriddenParents == null ? 0 : overriddenParents.size();
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
		return overriddenParents.get(nth);
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
		return overriddenParents.toArray(RevCommit[]::new);
	}
}
