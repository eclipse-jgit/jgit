/*
 * Copyright (C) 2012, Google Inc.
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

package org.eclipse.jgit.transport;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;

/**
 * Resolve from Repository back to the unique, human-readable name for that
 * repository.
 */
public class PublisherReverseResolver {
	/** Object needs to implement hashCode() and equals() */
	private final Map<Object, String> repositoryNameLookup;

	/** Create a new resolver. */
	public PublisherReverseResolver() {
		repositoryNameLookup = new ConcurrentHashMap<Object, String>();
	}

	/**
	 * Register a name to a repository.
	 *
	 * @param r
	 * @param name
	 * @throws NotSupportedException
	 */
	public void register(Repository r, String name)
			throws NotSupportedException {
		repositoryNameLookup.put(getKey(r), name);
	}

	/**
	 * @param r
	 * @return the human-readable name for this repository
	 * @throws NotSupportedException
	 */
	public String find(Repository r) throws NotSupportedException {
		return repositoryNameLookup.get(getKey(r));
	}

	/**
	 * @param r
	 * @return an Object that uniquely identifies this repository with respect
	 *         to {@link #hashCode()} and {#link {@link #equals(Object)}.
	 * @throws NotSupportedException
	 */
	protected Object getKey(Repository r) throws NotSupportedException {
		if (r.getDirectory() == null)
			throw new NotSupportedException(JGitText
					.get().repositoryMustHaveDirectory);
		return r.getDirectory().getAbsolutePath();
	}
}
