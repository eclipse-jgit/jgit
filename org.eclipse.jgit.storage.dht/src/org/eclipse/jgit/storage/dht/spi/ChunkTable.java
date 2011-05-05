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

import org.eclipse.jgit.generated.storage.dht.proto.GitStore.ChunkMeta;
import org.eclipse.jgit.storage.dht.AsyncCallback;
import org.eclipse.jgit.storage.dht.ChunkKey;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.PackChunk;
import org.eclipse.jgit.storage.dht.StreamingCallback;

/**
 * Stores object data in compressed pack format.
 * <p>
 * Each chunk stores multiple objects, using the highly compressed and Git
 * native pack file format. Chunks are sized during insertion, but average
 * around 1 MB for historical chunks, and may be as small as a few KB for very
 * recent chunks that were written in small bursts.
 * <p>
 * Objects whose compressed form is too large to fit into a single chunk are
 * fragmented across multiple chunks, and the fragment information is used to
 * put them back together in the correct order. Since the fragmenting occurs
 * after data compression, random access to bytes of the large object is not
 * currently possible.
 * <p>
 * Chunk keys are very well distributed, by embedding a uniformly random number
 * at the start of the key, and also including a small time component. This
 * layout permits chunks to be evenly spread across a cluster of disks or
 * servers in a round-robin fashion (based on a hash of the leading bytes), but
 * also offers some chance for older chunks to be located near each other and
 * have that part of the storage system see less activity over time.
 */
public interface ChunkTable {
	/**
	 * Asynchronously load one or more chunks
	 * <p>
	 * Callers are responsible for breaking up very large collections of chunk
	 * keys into smaller units, based on the reader's batch size option. Since
	 * chunks typically 1 MB each, 10-20 keys is a reasonable batch size, but
	 * depends on available JVM memory and performance of this method obtaining
	 * chunks from the database.
	 *
	 * @param options
	 *            options to control reading.
	 * @param keys
	 *            the chunk keys to obtain.
	 * @param callback
	 *            receives the results when ready. If this is an instance of
	 *            {@link StreamingCallback}, implementors should try to deliver
	 *            results early.
	 */
	public void get(Context options, Set<ChunkKey> keys,
			AsyncCallback<Collection<PackChunk.Members>> callback);

	/**
	 * Asynchronously load one or more chunk meta fields.
	 * <p>
	 * Usually meta is loaded by {@link #get(Context, Set, AsyncCallback)}, but
	 * some uses may require looking up the fragment data without having the
	 * entire chunk.
	 *
	 * @param options
	 *            options to control reading.
	 * @param keys
	 *            the chunk keys to obtain.
	 * @param callback
	 *            receives the results when ready. If this is an instance of
	 *            {@link StreamingCallback}, implementors should try to deliver
	 *            results early.
	 */
	public void getMeta(Context options, Set<ChunkKey> keys,
			AsyncCallback<Map<ChunkKey, ChunkMeta>> callback);

	/**
	 * Put some (or all) of a single chunk.
	 * <p>
	 * The higher level storage layer typically stores chunks in pieces. Its
	 * common to first store the data, then much later store the fragments and
	 * index. Sometimes all of the members are ready at once, and can be put
	 * together as a single unit. This method handles both approaches to storing
	 * a chunk.
	 * <p>
	 * Implementors must use a partial writing approach, for example:
	 *
	 * <pre>
	 *   ColumnUpdateList list = ...;
	 *   if (chunk.getChunkData() != null)
	 *     list.addColumn(&quot;chunk_data&quot;, chunk.getChunkData());
	 *   if (chunk.getChunkIndex() != null)
	 *     list.addColumn(&quot;chunk_index&quot;, chunk.getChunkIndex());
	 *   if (chunk.getFragments() != null)
	 *     list.addColumn(&quot;fragments&quot;, chunk.getFragments());
	 *   createOrUpdateRow(chunk.getChunkKey(), list);
	 * </pre>
	 *
	 * @param chunk
	 *            description of the chunk to be stored.
	 * @param buffer
	 *            buffer to enqueue the put onto.
	 * @throws DhtException
	 *             if the buffer flushed and an enqueued operation failed.
	 */
	public void put(PackChunk.Members chunk, WriteBuffer buffer)
			throws DhtException;

	/**
	 * Completely remove a chunk and all of its data elements.
	 * <p>
	 * Chunk removal should occur as quickly as possible after the flush has
	 * completed, as the caller has already ensured the chunk is not in use.
	 *
	 * @param key
	 *            key of the chunk to remove.
	 * @param buffer
	 *            buffer to enqueue the remove onto.
	 * @throws DhtException
	 *             if the buffer flushed and an enqueued operation failed.
	 */
	public void remove(ChunkKey key, WriteBuffer buffer) throws DhtException;
}
