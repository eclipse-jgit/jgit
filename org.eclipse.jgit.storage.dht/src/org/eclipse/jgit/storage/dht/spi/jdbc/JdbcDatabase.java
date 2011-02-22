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

package org.eclipse.jgit.storage.dht.spi.jdbc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.DhtRepository;
import org.eclipse.jgit.storage.dht.DhtRepositoryBuilder;
import org.eclipse.jgit.storage.dht.spi.ChunkTable;
import org.eclipse.jgit.storage.dht.spi.Database;
import org.eclipse.jgit.storage.dht.spi.ObjectIndexTable;
import org.eclipse.jgit.storage.dht.spi.RefTable;
import org.eclipse.jgit.storage.dht.spi.RepositoryIndexTable;
import org.eclipse.jgit.storage.dht.spi.RepositoryTable;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;

/**
 * Stores Git repositories in a standard SQL database.
 */
public class JdbcDatabase implements Database {
	private final ExecutorService executors;

	private final ClientPool pool;

	private final JdbcRepositoryIndexTable repositoryIndex;

	private final JdbcRepositoryTable repository;

	private final JdbcRefTable ref;

	private final JdbcObjectIndexTable objectIndex;

	private final JdbcChunkTable chunk;

	/**
	 * Initialize a database using a connection pool.
	 *
	 * @param executors
	 *            thread service to run operations on.
	 * @param pool
	 *            the pool to borrow connections from during operations.
	 */
	public JdbcDatabase(ExecutorService executors, ClientPool pool) {
		this.executors = executors;
		this.pool = pool;

		repositoryIndex = new JdbcRepositoryIndexTable(this);
		repository = new JdbcRepositoryTable(this);
		ref = new JdbcRefTable(this);
		objectIndex = new JdbcObjectIndexTable(this);
		chunk = new JdbcChunkTable(this);
	}

	/** Shutdown the connection(s) to the SQL database. */
	public void shutdown() {
		pool.shutdown();
	}

	/**
	 * Open a repository by name on this database.
	 *
	 * @param name
	 *            the name of the repository.
	 * @return the repository instance. If the repository does not yet exist,
	 *         the caller can use {@link Repository#create(boolean)} to create.
	 * @throws IOException
	 */
	public DhtRepository open(String name) throws IOException {
		return (DhtRepository) new DhtRepositoryBuilder<DhtRepositoryBuilder, DhtRepository, JdbcDatabase>()
				.setDatabase(this) //
				.setRepositoryName(name) //
				.setMustExist(false) //
				.build();
	}

	/**
	 * @throws SQLException
	 * @throws IOException
	 */
	public void initalizeSchema() throws SQLException, IOException {
		InputStream in = getClass().getResourceAsStream("git_schema.sql");
		BufferedReader br = new BufferedReader(new InputStreamReader(in,
				"UTF-8"));
		StringBuilder buf = new StringBuilder();

		Client conn = get();
		Statement stmt = conn.createStatement();
		String line;
		while ((line = br.readLine()) != null) {
			if (line.startsWith("--") || line.length() == 0)
				continue;
			if (0 < buf.length())
				buf.append("\n");
			buf.append(line);
			if (buf.charAt(buf.length() - 1) == ';') {
				stmt.execute(buf.toString());
				buf.setLength(0);
			}
		}
		stmt.close();
		release(conn);
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

	public WriteBuffer newWriteBuffer() {
		return new JdbcBuffer(executors, 1 * 1024 * 1024, pool);
	}

	Client get() throws DhtException {
		return pool.get();
	}

	void release(Client conn) {
		pool.release(conn);
	}

	void submit(Runnable task) {
		executors.submit(task);
	}
}
