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

package org.eclipse.jgit.storage.hbase;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.HConnectionManager;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.spi.ChunkTable;
import org.eclipse.jgit.storage.dht.spi.Database;
import org.eclipse.jgit.storage.dht.spi.ObjectIndexTable;
import org.eclipse.jgit.storage.dht.spi.RefTable;
import org.eclipse.jgit.storage.dht.spi.RepositoryIndexTable;
import org.eclipse.jgit.storage.dht.spi.RepositoryTable;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;

/**
 * Stores Git repositories in an Apache HBase database.
 * <p>
 * To construct a database connection to the HBase cluster, use
 * {@link HBaseDatabaseBuilder}.
 */
public class HBaseDatabase implements Database {
	private final Configuration configuration;

	private final String schemaPrefix;

	private final ExecutorService executors;

	private final HBaseRepositoryIndexTable repositoryIndex;

	private final HBaseRepositoryTable repository;

	private final HBaseRefTable ref;

	private final HBaseChunkTable chunk;

	private final HBaseObjectIndexTable objectIndex;

	HBaseDatabase(HBaseDatabaseBuilder builder) throws DhtException {
		this.configuration = builder.getConfiguration();
		this.schemaPrefix = builder.getSchemaPrefix();
		this.executors = builder.getExecutorService();

		repositoryIndex = new HBaseRepositoryIndexTable(this);
		repository = new HBaseRepositoryTable(this);
		ref = new HBaseRefTable(this);
		chunk = new HBaseChunkTable(this);
		objectIndex = new HBaseObjectIndexTable(this);
	}

	/** Shutdown the connection(s) to the cluster. */
	public void shutdown() {
		HConnectionManager.deleteConnection(configuration, true);
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

	public ChunkTable chunk() {
		return chunk;
	}

	public ObjectIndexTable objectIndex() {
		return objectIndex;
	}

	public WriteBuffer newWriteBuffer() {
		return new HBaseBuffer(this, 10 * 1024 * 1024);
	}

	HTableInterface openTable(String tableName) throws DhtException {
		if (schemaPrefix != null && schemaPrefix.length() > 0)
			tableName = schemaPrefix + "." + tableName;
		try {
			return new HTable(getConfiguration(), tableName);
		} catch (IOException e) {
			throw new DhtException("Cannot open table " + tableName, e);
		}
	}

	Configuration getConfiguration() {
		return configuration;
	}

	ExecutorService getExecutorService() {
		return executors;
	}
}
