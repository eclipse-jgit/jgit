/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.events;

/**
 * Describes a change to one or more paths in the index file.
 */
public class IndexChangedEvent extends RepositoryEvent<IndexChangedListener> {
	private boolean internal;

	/**
	 * Notify that the index changed
	 *
	 * @param internal
	 *                     {@code true} if the index was changed by the same
	 *                     JGit process
	 * @since 5.0
	 */
	public IndexChangedEvent(boolean internal) {
		this.internal = internal;
	}

	/**
	 * @return {@code true} if the index was changed by the same JGit process
	 * @since 5.0
	 */
	public boolean isInternal() {
		return internal;
	}

	/** {@inheritDoc} */
	@Override
	public Class<IndexChangedListener> getListenerType() {
		return IndexChangedListener.class;
	}

	/** {@inheritDoc} */
	@Override
	public void dispatch(IndexChangedListener listener) {
		listener.onIndexChanged(this);
	}
}
