/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
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
	 * automatically set by {@link Repository#fireEvent(RepositoryEvent)}.
	 *
	 * @param r
	 *            the repository.
	 */
	public void setRepository(Repository r) {
		if (repository == null)
			repository = r;
	}

	/** @return the repository that was changed. */
	public Repository getRepository() {
		return repository;
	}

	/** @return type of listener this event dispatches to. */
	public abstract Class<T> getListenerType();

	/**
	 * Dispatch this event to the given listener.
	 *
	 * @param listener
	 *            listener that wants this event.
	 */
	public abstract void dispatch(T listener);

	@SuppressWarnings("nls")
	@Override
	public String toString() {
		String type = getClass().getSimpleName();
		if (repository == null)
			return type;
		return type + "[" + repository + "]";
	}
}
