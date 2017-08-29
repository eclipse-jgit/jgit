/*
 * Copyright (C) 2017, Thomas Wolf <thomas.wolf@paranor.ch>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.events;

import java.util.Collection;
import java.util.Collections;

import org.eclipse.jgit.annotations.NonNull;

/**
 * A {@link RepositoryEvent} describing changes to the working tree. It is fired
 * whenever a {@link org.eclipse.jgit.dircache.DirCacheCheckout} modifies
 * (adds/deletes/updates) files in the working tree.
 *
 * @since 4.9
 */
public class WorkingTreeModifiedEvent
		extends RepositoryEvent<WorkingTreeModifiedListener> {

	private Collection<String> modified;

	private Collection<String> deleted;

	/**
	 * Creates a new {@link WorkingTreeModifiedEvent} with the given
	 * collections.
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
	 * Retrieves the {@link Collection} of repository-relative paths of files
	 * that were modified (added or updated).
	 *
	 * @return the set
	 */
	public @NonNull Collection<String> getModified() {
		Collection<String> result = modified;
		if (result == null) {
			result = Collections.emptyList();
			modified = result;
		}
		return result;
	}

	/**
	 * Retrieves the {@link Collection} of repository-relative paths of files
	 * that were deleted.
	 *
	 * @return the set
	 */
	public @NonNull Collection<String> getDeleted() {
		Collection<String> result = deleted;
		if (result == null) {
			result = Collections.emptyList();
			deleted = result;
		}
		return result;
	}

	@Override
	public Class<WorkingTreeModifiedListener> getListenerType() {
		return WorkingTreeModifiedListener.class;
	}

	@Override
	public void dispatch(WorkingTreeModifiedListener listener) {
		listener.onWorkingTreeModified(this);
	}
}
