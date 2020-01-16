/*
 * Copyright (C) 2012, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import org.eclipse.jgit.events.RepositoryListener;

/**
 * Receives
 * {@link org.eclipse.jgit.internal.storage.dfs.BeforeDfsPackIndexLoadedEvent}s.
 */
public interface BeforeDfsPackIndexLoadedListener extends RepositoryListener {
	/**
	 * Invoked just before a pack index is loaded from the block cache.
	 *
	 * @param event
	 *            information about the packs.
	 */
	void onBeforeDfsPackIndexLoaded(BeforeDfsPackIndexLoadedEvent event);
}
