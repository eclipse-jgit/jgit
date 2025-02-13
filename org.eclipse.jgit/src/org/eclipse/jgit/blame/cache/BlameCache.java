/*
 * Copyright (C) 2025 Google LLC
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
 * Store the blame information for a path at certain commit.
 * <p>
 * The returned regions cover the whole file (at that revision).
 */
public interface BlameCache {
	/**
	 * Gets the blame of a path at a given commit.
	 *
	 * @param repo repository containing the commit
	 * @param commitId we are looking at the file in this revision
	 * @param path path a file in the repo
	 *
	 * @return the blame of a path at a given commit.
	 * @throws IOException error retrieving/parsing values from storage
	 */
	List<CacheRegion> get(Repository repo, ObjectId commitId, String path)
			throws IOException;
}
