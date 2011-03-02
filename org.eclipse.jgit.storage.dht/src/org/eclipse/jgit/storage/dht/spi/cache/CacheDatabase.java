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

import java.util.concurrent.ExecutorService;

import org.eclipse.jgit.storage.dht.spi.ChunkTable;
import org.eclipse.jgit.storage.dht.spi.Database;
import org.eclipse.jgit.storage.dht.spi.ObjectIndexTable;
import org.eclipse.jgit.storage.dht.spi.RefTable;
import org.eclipse.jgit.storage.dht.spi.RepositoryIndexTable;
import org.eclipse.jgit.storage.dht.spi.RepositoryTable;

/**
 * Uses a cache for fast-lookups, but falls-back to another Database.
 * <p>
 * On a read miss, this database falls back to read another Database, and then
 * puts the read value into the cache for later access.
 */
public class CacheDatabase implements Database {
	private final Database database;

	private final ExecutorService executorService;

	private final CacheService client;

	private final CacheOptions options;

	private final CacheRepositoryIndexTable repositoryIndex;

	private final CacheRepositoryTable repository;

	private final CacheRefTable ref;

	private final CacheObjectIndexTable objectIndex;

	private final CacheChunkTable chunk;

	/**
	 * Initialize a cache database.
	 *
	 * @param database
	 *            underlying storage database, used for read-misses and all
	 *            writes.
	 * @param executor
	 *            executor service to perform expensive cache updates in the
	 *            background.
	 * @param client
	 *            implementation of the cache service.
	 * @param options
	 *            configuration of the cache.
	 */
	public CacheDatabase(Database database, ExecutorService executor,
			CacheService client, CacheOptions options) {
		this.database = database;
		this.executorService = executor;
		this.client = client;
		this.options = options;

		repositoryIndex = new CacheRepositoryIndexTable(database
				.repositoryIndex(), this);

		repository = new CacheRepositoryTable(database.repository(), this);
		ref = new CacheRefTable(database.ref(), this);
		objectIndex = new CacheObjectIndexTable(database.objectIndex(), this);
		chunk = new CacheChunkTable(database.chunk(), this);
	}

	/** @return the underlying database the cache wraps. */
	public Database getDatabase() {
		return database;
	}

	/** @return executor pool for long operations. */
	public ExecutorService getExecutorService() {
		return executorService;
	}

	/** @return client connecting to the cache service. */
	public CacheService getClient() {
		return client;
	}

	/** @return connection options for the cache service. */
	public CacheOptions getOptions() {
		return options;
	}

	public RepositoryIndexTable repositoryIndex() {
		return repositoryIndex;
	}

	public RepositoryTable repository() {
		return repository;
	}

	public RefTable ref() {
		return ref;
	}

	public ObjectIndexTable objectIndex() {
		return objectIndex;
	}

	public ChunkTable chunk() {
		return chunk;
	}

	public CacheBuffer newWriteBuffer() {
		return new CacheBuffer(database.newWriteBuffer(), client, options);
	}
}
