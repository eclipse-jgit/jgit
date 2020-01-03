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

import org.eclipse.jgit.events.RepositoryEvent;

/**
 * Describes the {@link org.eclipse.jgit.internal.storage.dfs.DfsPackFile} just
 * before its index is loaded. Currently, DfsPackFile directly dispatches the
 * event on {@link org.eclipse.jgit.lib.Repository#getGlobalListenerList}. Which
 * means the call to {@link #getRepository} will always return null.
 */
public class BeforeDfsPackIndexLoadedEvent
		extends RepositoryEvent<BeforeDfsPackIndexLoadedListener> {
	private final DfsPackFile pack;

	/**
	 * A new event triggered before a PackFile index is loaded.
	 *
	 * @param pack
	 *            the pack
	 */
	public BeforeDfsPackIndexLoadedEvent(DfsPackFile pack) {
		this.pack = pack;
	}

	/**
	 * Get the PackFile containing the index that will be loaded.
	 *
	 * @return the PackFile containing the index that will be loaded.
	 */
	public DfsPackFile getPackFile() {
		return pack;
	}

	/** {@inheritDoc} */
	@Override
	public Class<BeforeDfsPackIndexLoadedListener> getListenerType() {
		return BeforeDfsPackIndexLoadedListener.class;
	}

	/** {@inheritDoc} */
	@Override
	public void dispatch(BeforeDfsPackIndexLoadedListener listener) {
		listener.onBeforeDfsPackIndexLoaded(this);
	}
}
