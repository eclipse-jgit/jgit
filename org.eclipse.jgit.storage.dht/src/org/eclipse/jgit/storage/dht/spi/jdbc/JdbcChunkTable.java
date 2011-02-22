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

import static java.util.Collections.singleton;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.storage.dht.AsyncCallback;
import org.eclipse.jgit.storage.dht.ChunkKey;
import org.eclipse.jgit.storage.dht.ChunkMeta;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.PackChunk;
import org.eclipse.jgit.storage.dht.PackChunk.Members;
import org.eclipse.jgit.storage.dht.StreamingCallback;
import org.eclipse.jgit.storage.dht.spi.ChunkTable;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;

final class JdbcChunkTable implements ChunkTable {
	private final JdbcDatabase db;

	JdbcChunkTable(JdbcDatabase db) {
		this.db = db;
	}

	public void get(Context options, Set<ChunkKey> keys,
			AsyncCallback<Collection<PackChunk.Members>> callback) {
		db.submit(new Loader(keys, callback));
	}

	private class Loader implements Runnable {
		private final Set<ChunkKey> keys;

		private final AsyncCallback<Collection<PackChunk.Members>> normal;

		private final StreamingCallback<Collection<PackChunk.Members>> streaming;

		private final List<PackChunk.Members> results;

		Loader(Set<ChunkKey> keys, AsyncCallback<Collection<Members>> callback) {
			this.keys = keys;
			this.normal = callback;

			if (callback instanceof StreamingCallback<?>) {
				streaming = (StreamingCallback<Collection<Members>>) callback;
				results = null;
			} else {
				streaming = null;
				results = new ArrayList<PackChunk.Members>(keys.size());
			}
		}

		public void run() {
			Client conn;
			try {
				conn = db.get();
			} catch (DhtException err) {
				normal.onFailure(err);
				return;
			}
			try {
				try {
					// Since chunks are typically large (> 1 MiB in size)
					// load them one at a time in the order given.
					for (ChunkKey key : keys) {
						PackChunk.Members chunk = load(conn, key);
						if (chunk == null)
							continue;
						if (streaming != null)
							streaming.onPartialResult(singleton(chunk));
						else
							results.add(chunk);
					}
					normal.onSuccess(results);
				} catch (SQLException err) {
					normal.onFailure(new DhtException(err));
				}
			} finally {
				db.release(conn);
			}
		}

		private PackChunk.Members load(Client conn, ChunkKey key)
				throws SQLException {
			String sql = "SELECT chunk_data, chunk_index, chunk_meta FROM chunk WHERE repo_id = ? AND chunk_hash = ?";
			PreparedStatement stmt = conn.prepare(sql);
			try {
				stmt.setInt(1, key.getRepositoryKey().asInt());
				stmt.setString(2, key.getChunkHash().name());
				ResultSet rs = stmt.executeQuery();
				try {
					if (!rs.next())
						return null;

					PackChunk.Members m = new PackChunk.Members();
					m.setChunkKey(key);
					m.setChunkData(conn.getBytes(rs, 1));
					m.setChunkIndex(conn.getBytes(rs, 2));

					byte[] meta = conn.getBytes(rs, 3);
					if (meta != null)
						m.setMeta(ChunkMeta.fromBytes(key, meta));
					return m;
				} finally {
					rs.close();
				}
			} finally {
				conn.release(stmt);
			}
		}
	}

	public void put(PackChunk.Members chunk, WriteBuffer buffer)
			throws DhtException {
		JdbcBuffer buf = (JdbcBuffer) buffer;
		buf.submit(Put.OP, chunk);
	}

	private static class Put extends JdbcBuffer.Task<PackChunk.Members> {
		static final Put OP = new Put();

		@Override
		int size(PackChunk.Members data) {
			int sz = 24;
			if (data.getChunkData() != null)
				sz += data.getChunkData().length;
			if (data.getChunkIndex() != null)
				sz += data.getChunkIndex().length;
			if (data.getMeta() != null)
				sz += 1024; // rough estimate
			return sz;
		}

		@Override
		void run(Client conn, List<PackChunk.Members> chunkList)
				throws SQLException {
			// Since chunks are large, insert or update one at a time.
			// If data is present, this is an insert, else update.
			for (PackChunk.Members chunk : chunkList) {
				if (chunk.getChunkData() != null)
					insert(conn, chunk);
				else
					update(conn, chunk);
			}
		}

		private void insert(Client conn, PackChunk.Members chunk)
				throws SQLException {
			StringBuffer sql = new StringBuffer();
			sql.append("INSERT INTO chunk (repo_id, chunk_hash, chunk_data");
			if (chunk.getChunkIndex() != null)
				sql.append(", chunk_index");
			if (chunk.getMeta() != null)
				sql.append(", chunk_meta");
			sql.append(") VALUES (?, ?, ?");
			if (chunk.getChunkIndex() != null)
				sql.append(", ?");
			if (chunk.getMeta() != null)
				sql.append(", ?");
			sql.append(")");

			PreparedStatement stmt = conn.prepare(sql.toString());
			stmt.setInt(1, chunk.getChunkKey().getRepositoryKey().asInt());
			stmt.setString(2, chunk.getChunkKey().getChunkHash().name());
			conn.setBytes(stmt, 3, chunk.getChunkData());

			int col = 4;
			if (chunk.getChunkIndex() != null)
				conn.setBytes(stmt, col++, chunk.getChunkIndex());
			if (chunk.getMeta() != null)
				conn.setBytes(stmt, col++, chunk.getMeta().asBytes());
			if (stmt.executeUpdate() != 1)
				throw new SQLException(JdbcText.get().chunkInsertFailed);
			conn.release(stmt);
		}

		private void update(Client conn, PackChunk.Members chunk)
				throws SQLException {
			StringBuffer sql = new StringBuffer();
			sql.append("UPDATE chunk SET");
			if (chunk.getChunkIndex() != null)
				sql.append(" chunk_index = ?");
			if (chunk.getMeta() != null) {
				if (chunk.getChunkIndex() != null)
					sql.append(",");
				sql.append(" chunk_meta = ?");
			}
			sql.append(" WHERE repo_id = ? AND chunk_hash = ?");

			PreparedStatement stmt = conn.prepare(sql.toString());
			int col = 1;
			if (chunk.getChunkIndex() != null)
				conn.setBytes(stmt, col++, chunk.getChunkIndex());
			if (chunk.getMeta() != null)
				conn.setBytes(stmt, col++, chunk.getMeta().asBytes());
			stmt.setInt(col++, chunk.getChunkKey().getRepositoryKey().asInt());
			stmt.setString(col++, chunk.getChunkKey().getChunkHash().name());
			if (stmt.executeUpdate() != 1)
				throw new SQLException(JdbcText.get().chunkUpdateFailed);
			conn.release(stmt);
		}
	}

	public void remove(ChunkKey key, WriteBuffer buffer) throws DhtException {
		JdbcBuffer buf = (JdbcBuffer) buffer;
		buf.submit(Remove.OP, key);
	}

	private static class Remove extends JdbcBuffer.Task<ChunkKey> {
		static final Remove OP = new Remove();

		@Override
		int size(ChunkKey data) {
			return 8 + 4 + 8 + 20;
		}

		@Override
		void run(Client conn, List<ChunkKey> keyList) throws SQLException {
			PreparedStatement stmt = conn.prepare("DELETE FROM chunk"
					+ " WHERE repo_id = ?" //
					+ " AND chunk_hash = ?");
			for (ChunkKey key : keyList) {
				stmt.setInt(1, key.getRepositoryKey().asInt());
				stmt.setString(2, key.getChunkHash().name());
				stmt.addBatch();
			}
			conn.executeBatch(stmt);
		}
	}
}
