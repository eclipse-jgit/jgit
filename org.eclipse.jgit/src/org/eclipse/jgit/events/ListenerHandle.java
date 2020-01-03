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
 * Tracks a previously registered {@link org.eclipse.jgit.events.RepositoryListener}.
 */
public class ListenerHandle {
	private final ListenerList parent;

	final Class<? extends RepositoryListener> type;

	final RepositoryListener listener;

	ListenerHandle(ListenerList parent,
			Class<? extends RepositoryListener> type,
			RepositoryListener listener) {
		this.parent = parent;
		this.type = type;
		this.listener = listener;
	}

	/**
	 * Remove the listener and stop receiving events.
	 */
	public void remove() {
		parent.remove(this);
	}

	/** {@inheritDoc} */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return type.getSimpleName() + "[" + listener + "]";
	}
}
