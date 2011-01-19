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
import org.eclipse.jgit.storage.dht.RefData;

/**
 * Atomic transaction to modify one reference at a time.
 * <p>
 * The JGit DHT implementation uses this transaction to perform an atomic
 * compare-and-swap operation on the reference value. A transaction completes
 * only if the new value is replacing the old read value returned by
 * {@link #getOldData()}, or is deleting {@link #getOldData()}.
 * <p>
 * Only one of {@link #abort()}, {@link #compareAndPut(RefData)}, or
 * {@link #compareAndRemove()} is invoked on a transaction instance.
 * Implementations must cleanup resources in the method that gets called.
 *
 * @see RefTable#newTransaction(org.eclipse.jgit.storage.dht.RefKey)
 */
public interface RefTransaction {
	/**
	 * Get the reference's current data value, before changes are made.
	 * <p>
	 * This is invoked only once on the transaction.
	 *
	 * @return the old data. Implementations should return either
	 *         {@link RefData#NONE} or {@code null} if the reference did not
	 *         exist at the time the transaction started.
	 */
	public RefData getOldData();

	/**
	 * Attempt to commit new information about the reference.
	 * <p>
	 * This method should only succeed if the reference has not been
	 * concurrently modified to differ from {@link #getOldData()}.
	 *
	 * @param newData
	 *            the new information to store about the reference.
	 * @return true if the put completed (and no other concurrent modification
	 *         occurred); false if another writer modified the reference since
	 *         the last read returned by {@link #getOldData()}.
	 * @throws DhtException
	 *             the database cannot carry out the operation. Locks (if any)
	 *             were already released before throwing the exception up the
	 *             call stack.
	 * @throws TimeoutException
	 *             the operation timed out. Locks (if any) were already released
	 *             before throwing the exception up the call stack.
	 */
	public boolean compareAndPut(RefData newData) throws DhtException,
			TimeoutException;

	/**
	 * Attempt to remove the reference.
	 * <p>
	 * This method should only succeed if the reference has not been
	 * concurrently modified to differ from {@link #getOldData()}.
	 *
	 * @return true if the remove completed (and no other concurrent
	 *         modification occurred); false if another writer modified the
	 *         reference since the last read returned by {@link #getOldData()}.
	 * @throws DhtException
	 *             the database cannot carry out the operation. Locks (if any)
	 *             were already released before throwing the exception up the
	 *             call stack.
	 * @throws TimeoutException
	 *             the operation timed out. Locks (if any) were already released
	 *             before throwing the exception up the call stack.
	 */
	public boolean compareAndRemove() throws DhtException, TimeoutException;

	/** Abort the transaction, releasing any locks that may be held. */
	public void abort();
}
