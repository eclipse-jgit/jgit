/*
 * Copyright (C) 2025, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.blame.cache;

import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

/**
 * Keeps the blame information for a path at certain commit.
 * <p>
 * If there is a result, it covers the whole file at that revision
 *
 * @since 7.2
 */
public interface BlameCache {
	/**
	 * Gets the blame of a path at a given commit if available.
	 * <p>
	 * Since this cache is used in blame calculation, this get() method should
	 * only retrieve the cache value, and not re-trigger blame calculation. In
	 * other words, this acts as "getIfPresent", and not "computeIfAbsent".
	 *
	 * @param repo
	 *            repository containing the commit
	 * @param commitId
	 *            we are looking at the file in this revision
	 * @param path
	 *            path a file in the repo
	 *
	 * @return the blame of a path at a given commit or null if not in cache
	 * @throws IOException
	 *             error retrieving/parsing values from storage
	 */
	List<CacheRegion> get(Repository repo, ObjectId commitId, String path)
			throws IOException;
}
