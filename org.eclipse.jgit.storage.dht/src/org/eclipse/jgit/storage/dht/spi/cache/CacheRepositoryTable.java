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

package org.eclipse.jgit.storage.dht.spi.cache;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.generated.storage.dht.proto.GitCache.CachedPackInfoList;
import org.eclipse.jgit.generated.storage.dht.proto.GitStore.CachedPackInfo;
import org.eclipse.jgit.storage.dht.CachedPackKey;
import org.eclipse.jgit.storage.dht.ChunkInfo;
import org.eclipse.jgit.storage.dht.ChunkKey;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.RepositoryKey;
import org.eclipse.jgit.storage.dht.Sync;
import org.eclipse.jgit.storage.dht.spi.RepositoryTable;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;
import org.eclipse.jgit.storage.dht.spi.cache.CacheService.Change;

import com.google.protobuf.InvalidProtocolBufferException;

/** Cache wrapper around RepositoryTable. */
public class CacheRepositoryTable implements RepositoryTable {
	private final RepositoryTable db;

	private final CacheService client;

	private final CacheOptions options;

	private final Namespace nsCachedPack = Namespace.CACHED_PACK;

	private final Sync<Void> none;

	/**
	 * Initialize a new wrapper.
	 *
	 * @param dbTable
	 *            the underlying database's corresponding table.
	 * @param cacheDatabase
	 *            the cache database.
	 */
	public CacheRepositoryTable(RepositoryTable dbTable,
			CacheDatabase cacheDatabase) {
		this.db = dbTable;
		this.client = cacheDatabase.getClient();
		this.options = cacheDatabase.getOptions();
		this.none = Sync.none();
	}

	public RepositoryKey nextKey() throws DhtException {
		return db.nextKey();
	}

	public void put(RepositoryKey repo, ChunkInfo info, WriteBuffer buffer)
			throws DhtException {
		CacheBuffer buf = (CacheBuffer) buffer;
		db.put(repo, info, buf.getWriteBuffer());
	}

	public void remove(RepositoryKey repo, ChunkKey chunk, WriteBuffer buffer)
			throws DhtException {
		CacheBuffer buf = (CacheBuffer) buffer;
		db.remove(repo, chunk, buf.getWriteBuffer());
	}

	public Collection<CachedPackInfo> getCachedPacks(RepositoryKey repo)
			throws DhtException, TimeoutException {
		CacheKey memKey = nsCachedPack.key(repo);
		Sync<Map<CacheKey, byte[]>> sync = Sync.create();
		client.get(singleton(memKey), sync);

		Map<CacheKey, byte[]> result;
		try {
			result = sync.get(options.getTimeout());
		} catch (InterruptedException e) {
			throw new TimeoutException();
		} catch (TimeoutException timeout) {
			// Fall through and read the database directly.
			result = emptyMap();
		}

		byte[] data = result.get(memKey);
		if (data != null) {
			try {
				return CachedPackInfoList.parseFrom(data).getPackList();
			} catch (InvalidProtocolBufferException e) {
				// Invalidate the cache entry and fall through.
				client.modify(singleton(Change.remove(memKey)), none);
			}
		}

		Collection<CachedPackInfo> r = db.getCachedPacks(repo);
		CachedPackInfoList.Builder list = CachedPackInfoList.newBuilder();
		list.addAllPack(r);
		client.modify(
				singleton(Change.put(memKey, list.build().toByteArray())),
				none);
		return r;
	}

	public void put(RepositoryKey repo, CachedPackInfo info, WriteBuffer buffer)
			throws DhtException {
		CacheBuffer buf = (CacheBuffer) buffer;
		db.put(repo, info, buf.getWriteBuffer());
		buf.removeAfterFlush(nsCachedPack.key(repo));
	}

	public void remove(RepositoryKey repo, CachedPackKey key, WriteBuffer buffer)
			throws DhtException {
		CacheBuffer buf = (CacheBuffer) buffer;
		db.remove(repo, key, buf.getWriteBuffer());
		buf.removeAfterFlush(nsCachedPack.key(repo));
	}
}
