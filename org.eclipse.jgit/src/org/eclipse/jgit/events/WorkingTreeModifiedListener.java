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

/**
 * Receives {@link org.eclipse.jgit.events.WorkingTreeModifiedEvent}s, which are
 * fired whenever a {@link org.eclipse.jgit.dircache.DirCacheCheckout} modifies
 * (adds/deletes/updates) files in the working tree.
 *
 * @since 4.9
 */
public interface WorkingTreeModifiedListener extends RepositoryListener {

	/**
	 * Respond to working tree modifications.
	 *
	 * @param event
	 *            a {@link org.eclipse.jgit.events.WorkingTreeModifiedEvent}
	 *            object.
	 */
	void onWorkingTreeModified(WorkingTreeModifiedEvent event);
}
