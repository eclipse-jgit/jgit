/*
 * Copyright (C) 2022, Simeon Andreev and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.diff;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.filter.PathFilter;

/**
 * Provides rename detection in special cases such as blame, where only a subset
 * of the renames detected by {@link RenameDetector} is of interest.
 */
public class FilteredRenameDetector {

	private final RenameDetector renameDetector;

	/**
	 * @param repository
	 *            The repository in which to check for renames.
	 */
	public FilteredRenameDetector(Repository repository) {
		this(new RenameDetector(repository));
	}

	/**
	 * @param renameDetector
	 *            The {@link RenameDetector} to use when checking for renames.
	 */
	public FilteredRenameDetector(RenameDetector renameDetector) {
		this.renameDetector = renameDetector;
	}

	/**
	 * Compute diff entries
	 *
	 * @param diffs
	 *            The set of changes to check.
	 * @param pathFilter
	 *            Filter out changes that didn't affect this path.
	 * @return The subset of changes that affect only the filtered path.
	 * @throws IOException
	 *             if an IO error occurred
	 */
	public List<DiffEntry> compute(List<DiffEntry> diffs,
			PathFilter pathFilter) throws IOException {
		return compute(diffs, Arrays.asList(pathFilter));
	}

	/**
	 * Tries to avoid computation overhead in {@link RenameDetector#compute()}
	 * by filtering diffs related to the path filters only.
	 * <p>
	 * Note: current implementation only optimizes added or removed diffs,
	 * further optimization is possible.
	 *
	 * @param changes
	 *            The set of changes to check.
	 * @param pathFilters
	 *            Filter out changes that didn't affect these paths.
	 * @return The subset of changes that affect only the filtered paths.
	 * @throws IOException
	 *             if an IO error occurred
	 * @see RenameDetector#compute()
	 */
	public List<DiffEntry> compute(List<DiffEntry> changes,
			List<PathFilter> pathFilters) throws IOException {

		if (pathFilters == null) {
			throw new IllegalArgumentException("Must specify path filters"); //$NON-NLS-1$
		}

		Set<String> paths = new HashSet<>(pathFilters.size());
		for (PathFilter pathFilter : pathFilters) {
			paths.add(pathFilter.getPath());
		}

		List<DiffEntry> filtered = new ArrayList<>();

		// For new path: skip ADD's that don't match given paths
		for (DiffEntry diff : changes) {
			ChangeType changeType = diff.getChangeType();
			if (changeType != ChangeType.ADD
					|| paths.contains(diff.getNewPath())) {
				filtered.add(diff);
			}
		}

		renameDetector.reset();
		renameDetector.addAll(filtered);
		List<DiffEntry> sourceChanges = renameDetector.compute();

		filtered.clear();

		// For old path: skip DELETE's that don't match given paths
		for (DiffEntry diff : changes) {
			ChangeType changeType = diff.getChangeType();
			if (changeType != ChangeType.DELETE
					|| paths.contains(diff.getOldPath())) {
				filtered.add(diff);
			}
		}

		renameDetector.reset();
		renameDetector.addAll(filtered);
		List<DiffEntry> targetChanges = renameDetector.compute();

		List<DiffEntry> result = new ArrayList<>();

		for (DiffEntry sourceChange : sourceChanges) {
			if (paths.contains(sourceChange.getNewPath())) {
				result.add(sourceChange);
			}
		}
		for (DiffEntry targetChange : targetChanges) {
			if (paths.contains(targetChange.getOldPath())) {
				result.add(targetChange);
			}
		}

		renameDetector.reset();
		return result;
	}
}
