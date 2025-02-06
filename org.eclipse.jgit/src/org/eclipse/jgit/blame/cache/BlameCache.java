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

public interface BlameCache {
	/**
	 * Gets the blame of a path at a given commit.
	 *
	 * @return the blame of a path at a given commit.
	 */
	List<CacheRegion> get(Repository repo, ObjectId commitId, String path)
			throws IOException;
}
