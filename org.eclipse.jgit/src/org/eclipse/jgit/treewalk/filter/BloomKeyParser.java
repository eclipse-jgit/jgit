/*
 * Copyright (C) 2021, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.treewalk.filter;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.BloomFilter;
import org.eclipse.jgit.lib.BloomFilter.Key;
import org.eclipse.jgit.lib.CommitGraph;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup.Group;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup.Single;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Parses TreeFilter into {@link org.eclipse.jgit.lib.BloomFilter.Key}s.
 */
public class BloomKeyParser {

	/**
	 * Parses TreeFilter into {@link org.eclipse.jgit.lib.BloomFilter.Key}s.
	 *
	 * <p>
	 * Checks whether TreeFilter is used for {@code git log -- <path>}. If true,
	 * the parser will get the path inside and uses it to generate the
	 * corresponding bloom filter keys.
	 * </p>
	 *
	 * @param filter
	 *            the tree filter to be parsed.
	 * @param commitGraph
	 *            the commit-graph, which is used to help generate bloom filter
	 *            keys.
	 * @return the bloom filter keys, or null if TreeFilter is not used for
	 *         {@code git log -- <path>} or commitGraph has no bloom filter.
	 */
	public static BloomFilter.Key[] parse(TreeFilter filter,
			CommitGraph commitGraph) {
		if (!(filter instanceof AndTreeFilter)) {
			return null;
		}
		TreeFilter[] filters = ((AndTreeFilter) filter).getTreeFilters();
		if (filters == null || filters.length != 2) {
			return null;
		}

		boolean diff = false;
		List<String> paths = new ArrayList<>();

		for (TreeFilter tf : filters) {
			if (tf == TreeFilter.ANY_DIFF) {
				diff = true;
			}
			if (tf instanceof PathFilter) {
				String path = ((PathFilter) tf).getPath();
				paths.add(path);
			}
			if (tf instanceof Single) {
				String path = ((Single) tf).getPath();
				paths.add(path);
			}
			if (tf instanceof Group) {
				byte[][] pathsByte = ((Group) tf).getFullpaths().toArray();
				for (int i = 0; i < pathsByte.length; i ++){
					paths.add(RawParseUtils.decode(pathsByte[i]));
				}
			}
		}

		if (!diff || paths.size() <= 0) {
			return null;
		}

		BloomFilter.Key[] bloomKeys = new Key[paths.size()];
		for (int i = 0; i < paths.size(); i++) {
			BloomFilter.Key key = commitGraph.newBloomKey(paths.get(i));
			if (key == null) {
				return null;
			}
			bloomKeys[i] = key;
		}
		return bloomKeys;
	}
}
