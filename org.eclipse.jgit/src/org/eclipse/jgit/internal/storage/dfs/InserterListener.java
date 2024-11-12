/*
 * Copyright (C) 2024, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

/**
 * Listener for {@link DfsInserter}
 */
interface InserterListener {
	/**
	 * Called right before the inserter is flushed.
	 *
	 * <p>This is called when the inserter is flushed, which means that all the data that was added to
	 * the inserter has been written to disk.
	 *
	 * @param packDescription the pack description that was flushed
	 */
	void onFlush(DfsPackDescription packDescription);
}
