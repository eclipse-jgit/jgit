/*
 * Copyright (C) 2024, Google Inc and others
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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Includes tree entries only if they match the configured path and differ
 * between at least 2 trees. Only include commits that have changed contents
 * within particular paths.
 * <p>
 * Logically equivalent of AndTreeFilter(PathFilter, AnyDiffFilter). This filter
 * uses {@link org.eclipse.jgit.internal.storage.commitgraph.ChangedPathFilter}
 * As a shortcut to determine whether to use {@link org.eclipse.jgit.treewalk}
 * when filtering by paths.
 */

public class PathAnyDiffFilter extends TreeFilter {

	TreeFilter pathFilter;

	List<String> paths;

	List<byte[]> rawPaths;

	/**
	 * Create a collection of pathAnyDiffFilters from Java strings.
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
	public static PathAnyDiffFilter create(String... paths) {
		return new PathAnyDiffFilter(paths);
	}

	PathAnyDiffFilter(String... paths) {
		if (paths.length == 0)
			throw new IllegalArgumentException(
					JGitText.get().atLeastOnePathIsRequired);
		if (paths.length == 1) {
			this.pathFilter = PathFilter.create(paths[0]);
		} else {
			this.pathFilter = OrTreeFilter.create(Arrays.stream(paths)
					.map(PathFilter::create).collect(Collectors.toList()));
		}
		this.paths = List.of(paths);
		this.rawPaths = this.paths.stream().map(Constants::encode)
				.collect(Collectors.toList());
	}

	@Override
	public boolean shouldTreeWalk(RevCommit c, RevWalk rw) {
		ChangedPathFilter cpf = c.getChangedPathFilter(rw);
		if (cpf == null) {
			return true;
		}
		c.add(RevFlag.CHANGED_PATHS_FILTER_APPLIED);
		return rawPaths.stream().anyMatch(cpf::maybeContains);
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
	public PathAnyDiffFilter clone() {
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

	@Override
	public String toString() {
		return "(PATH_ANY_DIFF(" + pathFilter.toString() + ")" //
				+ " AND " //
				+ ANY_DIFF.toString() + ")";
	}
}
