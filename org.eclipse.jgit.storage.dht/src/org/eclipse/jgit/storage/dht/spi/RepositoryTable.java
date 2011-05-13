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
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.generated.storage.dht.proto.GitStore.CachedPackInfo;
import org.eclipse.jgit.storage.dht.CachedPackKey;
import org.eclipse.jgit.storage.dht.ChunkInfo;
import org.eclipse.jgit.storage.dht.ChunkKey;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.RepositoryKey;

/**
 * Tracks high-level information about all known repositories.
 */
public interface RepositoryTable {
	/**
	 * Generate a new unique RepositoryKey.
	 *
	 * @return a new unique key.
	 * @throws DhtException
	 *             keys cannot be generated at this time.
	 */
	public RepositoryKey nextKey() throws DhtException;

	/**
	 * Record the existence of a chunk.
	 *
	 * @param repo
	 *            repository owning the chunk.
	 * @param info
	 *            information about the chunk.
	 * @param buffer
	 *            buffer to enqueue the put onto.
	 * @throws DhtException
	 *             if the buffer flushed and an enqueued operation failed.
	 */
	public void put(RepositoryKey repo, ChunkInfo info, WriteBuffer buffer)
			throws DhtException;

	/**
	 * Remove the information about a chunk.
	 *
	 * @param repo
	 *            repository owning the chunk.
	 * @param chunk
	 *            the chunk that needs to be deleted.
	 * @param buffer
	 *            buffer to enqueue the remove onto.
	 * @throws DhtException
	 *             if the buffer flushed and an enqueued operation failed.
	 */
	public void remove(RepositoryKey repo, ChunkKey chunk, WriteBuffer buffer)
			throws DhtException;

	/**
	 * Get the cached packs, if any.
	 *
	 * @param repo
	 *            repository owning the packs.
	 * @return cached pack descriptions.
	 * @throws DhtException
	 * @throws TimeoutException
	 */
	public Collection<CachedPackInfo> getCachedPacks(RepositoryKey repo)
			throws DhtException, TimeoutException;

	/**
	 * Record the existence of a cached pack.
	 *
	 * @param repo
	 *            repository owning the pack.
	 * @param info
	 *            information about the pack.
	 * @param buffer
	 *            buffer to enqueue the put onto.
	 * @throws DhtException
	 *             if the buffer flushed and an enqueued operation failed.
	 */
	public void put(RepositoryKey repo, CachedPackInfo info, WriteBuffer buffer)
			throws DhtException;

	/**
	 * Remove the existence of a cached pack.
	 *
	 * @param repo
	 *            repository owning the pack.
	 * @param key
	 *            information about the pack.
	 * @param buffer
	 *            buffer to enqueue the put onto.
	 * @throws DhtException
	 *             if the buffer flushed and an enqueued operation failed.
	 */
	public void remove(RepositoryKey repo, CachedPackKey key, WriteBuffer buffer)
			throws DhtException;
}
