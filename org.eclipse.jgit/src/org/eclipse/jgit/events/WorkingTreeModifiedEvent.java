/*
 * Copyright (C) 2017, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.events;

import java.util.Collection;
import java.util.Collections;

import org.eclipse.jgit.annotations.NonNull;

/**
 * A {@link org.eclipse.jgit.events.RepositoryEvent} describing changes to the
 * working tree. It is fired whenever a
 * {@link org.eclipse.jgit.dircache.DirCacheCheckout} modifies
 * (adds/deletes/updates) files in the working tree.
 *
 * @since 4.9
 */
public class WorkingTreeModifiedEvent
		extends RepositoryEvent<WorkingTreeModifiedListener> {

	private Collection<String> modified;

	private Collection<String> deleted;

	/**
	 * Creates a new {@link org.eclipse.jgit.events.WorkingTreeModifiedEvent}
	 * with the given collections.
	 *
	 * @param modified
	 *            repository-relative paths that were added or updated
	 * @param deleted
	 *            repository-relative paths that were deleted
	 */
	public WorkingTreeModifiedEvent(Collection<String> modified,
			Collection<String> deleted) {
		this.modified = modified;
		this.deleted = deleted;
	}

	/**
	 * Determines whether there are any changes recorded in this event.
	 *
	 * @return {@code true} if no files were modified or deleted, {@code false}
	 *         otherwise
	 */
	public boolean isEmpty() {
		return (modified == null || modified.isEmpty())
				&& (deleted == null || deleted.isEmpty());
	}

	/**
	 * Retrieves the {@link java.util.Collection} of repository-relative paths
	 * of files that were modified (added or updated).
	 *
	 * @return the set
	 */
	@NonNull
	public Collection<String> getModified() {
		Collection<String> result = modified;
		if (result == null) {
			result = Collections.emptyList();
			modified = result;
		}
		return result;
	}

	/**
	 * Retrieves the {@link java.util.Collection} of repository-relative paths
	 * of files that were deleted.
	 *
	 * @return the set
	 */
	@NonNull
	public Collection<String> getDeleted() {
		Collection<String> result = deleted;
		if (result == null) {
			result = Collections.emptyList();
			deleted = result;
		}
		return result;
	}

	/** {@inheritDoc} */
	@Override
	public Class<WorkingTreeModifiedListener> getListenerType() {
		return WorkingTreeModifiedListener.class;
	}

	/** {@inheritDoc} */
	@Override
	public void dispatch(WorkingTreeModifiedListener listener) {
		listener.onWorkingTreeModified(this);
	}
}
