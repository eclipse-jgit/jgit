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

import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.generated.storage.dht.proto.GitStore.RefData;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.RefDataUtil;
import org.eclipse.jgit.storage.dht.RefKey;
import org.eclipse.jgit.storage.dht.RepositoryKey;

/**
 * Tracks all branches and tags for a repository.
 * <p>
 * Each repository has one or more references, pointing to the most recent
 * revision on that branch, or to the tagged revision if its a tag.
 */
public interface RefTable {
	/**
	 * Read all known references in the repository.
	 *
	 * @param options
	 *            options to control reading.
	 * @param repository
	 *            the repository to load the references from.
	 * @return map of all references. Empty map if there are no references.
	 * @throws DhtException
	 *             the database cannot be read.
	 * @throws TimeoutException
	 *             the operation to read the database timed out.
	 */
	public Map<RefKey, RefData> getAll(Context options, RepositoryKey repository)
			throws DhtException, TimeoutException;

	/**
	 * Compare a reference, and delete if it matches.
	 *
	 * @param refKey
	 *            reference to delete.
	 * @param oldData
	 *            the old data for the reference. The delete only occurs if the
	 *            value is still equal to {@code oldData}.
	 * @return true if the delete was successful; false if the current value
	 *         does not match {@code oldData}.
	 * @throws DhtException
	 *             the database cannot be updated.
	 * @throws TimeoutException
	 *             the operation to modify the database timed out.
	 */
	public boolean compareAndRemove(RefKey refKey, RefData oldData)
			throws DhtException, TimeoutException;

	/**
	 * Compare a reference, and put if it matches.
	 *
	 * @param refKey
	 *            reference to create or replace.
	 * @param oldData
	 *            the old data for the reference. The put only occurs if the
	 *            value is still equal to {@code oldData}. Use
	 *            {@link RefDataUtil#NONE} if the reference should not exist and
	 *            is being created.
	 * @param newData
	 *            new value to store.
	 * @return true if the put was successful; false if the current value does
	 *         not match {@code prior}.
	 * @throws DhtException
	 *             the database cannot be updated.
	 * @throws TimeoutException
	 *             the operation to modify the database timed out.
	 */
	public boolean compareAndPut(RefKey refKey, RefData oldData, RefData newData)
			throws DhtException, TimeoutException;
}
