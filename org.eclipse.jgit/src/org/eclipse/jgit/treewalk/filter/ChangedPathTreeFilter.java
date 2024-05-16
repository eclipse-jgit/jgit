/*
 * Copyright (C) 2025, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.treewalk.filter;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.commitgraph.ChangedPathFilter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Filter tree entries that modified the contents of particular file paths.
 * <p>
 * Equivalent to AndTreeFilter(PathFilter, AnyDiffFilter). This filter uses
 * {@link org.eclipse.jgit.internal.storage.commitgraph.ChangedPathFilter}
 * (bloom filters) when available to discard commits without diffing their
 * trees.
 *
 * @since 7.3
 */

public class ChangedPathTreeFilter extends TreeFilter {

	private TreeFilter pathFilter;

	private List<String> paths;

	private List<byte[]> rawPaths;

	/**
	 * Create a TreeFilter for trees modifying one or more user supplied paths.
	 * <p>
	 * Path strings are relative to the root of the repository. If the user's
	 * input should be assumed relative to a subdirectory of the repository the
	 * caller must prepend the subdirectory's path prior to creating the filter.
	 * <p>
	 * Path strings use '/' to delimit directories on all platforms.
	 * <p>
	 * Paths may appear in any order within the collection. Sorting may be done
	 * internally when the group is constructed if doing so will improve path
	 * matching performance.
	 *
	 * @param paths
	 *            the paths to test against. Must have at least one entry.
	 * @return a new filter for the list of paths supplied.
	 */
	public static ChangedPathTreeFilter create(String... paths) {
		return new ChangedPathTreeFilter(paths);
	}

	private ChangedPathTreeFilter(String... paths) {
		setPaths(paths);
	}

	@Override
	public boolean shouldTreeWalk(RevCommit c, RevWalk rw,
			MutableBoolean cpfUsed) {
		// don't apply cpf to root commits
		// other logic might have overwritten the parentList
		// to shortcut the walk.
		if (c.getParentCount() == 0) {
			return true;
		}

		ChangedPathFilter cpf = c.getChangedPathFilter(rw);
		if (cpf == null) {
			return true;
		}
		if (cpfUsed != null) {
			cpfUsed.orValue(true);
		}
		// return true if at least one path might exist in cpf
		if (rawPaths.stream().anyMatch(cpf::maybeContains)) {
			return true;
		}

		if (c.getParentCount() == 1) {
			return false;
		}

		// only for merge commits
		RevCommit baseParent = c.getParent(0);
		if (baseParent.has(RevFlag.UNINTERESTING)) {
			// merge commit CPF can only redirect to the base parent
			return true;
		}
		return false;
	}

	@Override
	public boolean include(TreeWalk walker) throws IOException {
		return pathFilter.include(walker) && ANY_DIFF.include(walker);
	}

	@Override
	public boolean shouldBeRecursive() {
		return pathFilter.shouldBeRecursive() || ANY_DIFF.shouldBeRecursive();
	}

	@Override
	public ChangedPathTreeFilter clone() {
		return this;
	}

	/**
	 * Get the paths this filter matches.
	 *
	 * @return the paths this filter matches.
	 */
	public List<String> getPaths() {
		return paths;
	}

	/**
	 * Reset the paths this filter matches to a new array of paths.
	 *
	 * @param paths
	 *            new paths that this filter matches with.
	 */
	public void setPaths(String... paths) {
		List<String> filtered = Arrays.stream(paths)
				.map(s -> StringUtils.trim(s, '/'))
				.collect(Collectors.toList());

		if (filtered.size() == 0)
			throw new IllegalArgumentException(
					JGitText.get().atLeastOnePathIsRequired);

		if (filtered.stream().anyMatch(s -> s.isEmpty() || s.isBlank())) {
			throw new IllegalArgumentException(
					JGitText.get().emptyPathNotPermitted);
		}

		this.paths = filtered;
		this.rawPaths = this.paths.stream().map(Constants::encode)
				.collect(Collectors.toList());
		if (filtered.size() == 1) {
			this.pathFilter = PathFilter.create(paths[0]);
		} else {
			this.pathFilter = OrTreeFilter.create(Arrays.stream(paths)
					.map(PathFilter::create).collect(Collectors.toList()));
		}
	}

	@Override
	public String toString() {
		return "(CHANGED_PATH(" + pathFilter.toString() + ")" //
				+ " AND " //
				+ ANY_DIFF.toString() + ")";
	}
}
