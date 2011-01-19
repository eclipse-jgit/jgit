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

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.storage.dht.AsyncCallback;
import org.eclipse.jgit.storage.dht.ChunkKey;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.ObjectIndexKey;
import org.eclipse.jgit.storage.dht.ObjectInfo;

/**
 * Associates an {@link ObjectId} to the {@link ChunkKey} its stored in.
 * <p>
 * This table provides a global index listing every single object within the
 * repository, and which chunks the object can be found it. Readers use this
 * table to find an object when they are forced to start from a bare SHA-1 that
 * was input by a user, or supplied over the network from a client.
 */
public interface ObjectIndexTable {
	/**
	 * Asynchronously locate one or more objects in the repository.
	 * <p>
	 * Callers are responsible for breaking up very large collections of objects
	 * into smaller units, based on the reader's batch size option. 1,000 to
	 * 10,000 is a reasonable range for the reader to batch on.
	 *
	 * @param options
	 *            options to control reading.
	 * @param objects
	 *            set of object names to locate the chunks of.
	 * @param callback
	 *            receives the results when ready.
	 */
	public void get(Context options, Set<ObjectIndexKey> objects,
			AsyncCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>> callback);

	/**
	 * Record the fact that {@code objId} can be found by {@code info}.
	 * <p>
	 * If there is already data for {@code objId} in the table, this method
	 * should add the new chunk onto the existing data list.
	 * <p>
	 * This method should use batched asynchronous puts as much as possible.
	 * Initial imports of an existing repository may require millions of add
	 * operations to this table, one for each object being imported.
	 *
	 * @param objId
	 *            the unique ObjectId.
	 * @param info
	 *            a chunk that is known to store {@code objId}.
	 * @param buffer
	 *            buffer to enqueue the put onto.
	 * @throws DhtException
	 *             if the buffer flushed and an enqueued operation failed.
	 */
	public void add(ObjectIndexKey objId, ObjectInfo info, WriteBuffer buffer)
			throws DhtException;

	/**
	 * Remove a single chunk from an object.
	 * <p>
	 * If this is the last remaining chunk for the object, the object should
	 * also be removed from the table. Removal can be deferred, or can occur
	 * immediately. That is, {@code get()} may return the object with an empty
	 * collection, but to prevent unlimited disk usage the database should
	 * eventually remove the object.
	 *
	 * @param objId
	 *            the unique ObjectId.
	 * @param chunk
	 *            the chunk that needs to be removed from this object.
	 * @param buffer
	 *            buffer to enqueue the remove onto.
	 * @throws DhtException
	 *             if the buffer flushed and an enqueued operation failed.
	 */
	public void remove(ObjectIndexKey objId, ChunkKey chunk, WriteBuffer buffer)
			throws DhtException;
}
