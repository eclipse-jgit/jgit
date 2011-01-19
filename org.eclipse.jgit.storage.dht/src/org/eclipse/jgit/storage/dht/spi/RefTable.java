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

import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.RefData;
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
	 * Begin a transaction to create, modify or delete.
	 * <p>
	 * The higher level storage implementation invokes this method to start a
	 * transaction that will change a reference to point to a different part of
	 * the graph, or to create or delete
	 * <p>
	 * Implementations of this method should not throw DhtException when a lock
	 * is busy and cannot be acquired. Instead an implementation should perform
	 * a read of the reference's current value, return that as part of the
	 * transaction's {@link RefTransaction#getOldData()} member, and arrange for
	 * all of the compare operations to return false.
	 *
	 * @param refKey
	 *            names the reference that will be created, modified or deleted.
	 * @return a new transaction to perform an atomic compare-and-swap.
	 * @throws DhtException
	 *             the database cannot begin the transaction. This should be
	 *             because of major database failure and not due to failing to
	 *             acquire a resource lock.
	 * @throws TimeoutException
	 *             the operation to read the database timed out.
	 */
	public RefTransaction newTransaction(RefKey refKey) throws DhtException,
			TimeoutException;
}
