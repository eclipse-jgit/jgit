/*
 * Copyright (C) 2011, Google Inc.
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

package org.eclipse.jgit.storage.dht.spi;

import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.RepositoryKey;
import org.eclipse.jgit.storage.dht.RepositoryName;

/**
 * Maps a repository name from a URL, to the internal {@link RepositoryKey}.
 * <p>
 * The internal identifier is used for all data storage, as its part of the row
 * keys for each data row that makes up the repository. By using an internal
 * key, repositories can be efficiently renamed in O(1) time, without changing
 * existing data rows.
 */
public interface RepositoryIndexTable {
	/**
	 * Find a repository by name.
	 *
	 * @param name
	 *            name of the repository, from the URL.
	 * @return the internal key; null if not found.
	 * @throws DhtException
	 * @throws TimeoutException
	 */
	public RepositoryKey get(RepositoryName name) throws DhtException,
			TimeoutException;

	/**
	 * Atomically record the association of name to identifier.
	 * <p>
	 * This method must use some sort of transaction system to ensure the name
	 * either points at {@code key} when complete, or fails fast with an
	 * exception if the name is used by a different key. This may require
	 * running some sort of lock management service in parallel to the database.
	 *
	 * @param name
	 *            name of the repository.
	 * @param key
	 *            internal key used to find the repository's data.
	 * @throws DhtException
	 * @throws TimeoutException
	 */
	public void putUnique(RepositoryName name, RepositoryKey key)
			throws DhtException, TimeoutException;

	/**
	 * Remove the association of a name to an identifier.
	 * <p>
	 * This method must use some sort of transaction system to ensure the name
	 * is removed only if it currently references {@code key}. This may require
	 * running some sort of lock management service in parallel to the database.
	 *
	 * @param name
	 *            name of the repository.
	 * @param key
	 *            internal key defining the repository.
	 * @throws DhtException
	 * @throws TimeoutException
	 */
	public void remove(RepositoryName name, RepositoryKey key)
			throws DhtException, TimeoutException;
}
