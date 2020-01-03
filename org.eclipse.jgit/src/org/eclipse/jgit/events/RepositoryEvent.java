/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.events;

import org.eclipse.jgit.lib.Repository;

/**
 * Describes a modification made to a repository.
 *
 * @param <T>
 *            type of listener this event dispatches to.
 */
public abstract class RepositoryEvent<T extends RepositoryListener> {
	private Repository repository;

	/**
	 * Set the repository this event occurred on.
	 * <p>
	 * This method should only be invoked once on each event object, and is
	 * automatically set by
	 * {@link org.eclipse.jgit.lib.Repository#fireEvent(RepositoryEvent)}.
	 *
	 * @param r
	 *            the repository.
	 */
	public void setRepository(Repository r) {
		if (repository == null)
			repository = r;
	}

	/**
	 * Get the repository that was changed
	 *
	 * @return the repository that was changed
	 */
	public Repository getRepository() {
		return repository;
	}

	/**
	 * Get type of listener this event dispatches to
	 *
	 * @return type of listener this event dispatches to
	 */
	public abstract Class<T> getListenerType();

	/**
	 * Dispatch this event to the given listener.
	 *
	 * @param listener
	 *            listener that wants this event.
	 */
	public abstract void dispatch(T listener);

	/** {@inheritDoc} */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		String type = getClass().getSimpleName();
		if (repository == null)
			return type;
		return type + "[" + repository + "]";
	}
}
