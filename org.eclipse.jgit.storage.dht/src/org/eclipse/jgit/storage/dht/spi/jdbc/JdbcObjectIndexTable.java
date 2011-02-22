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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.storage.dht.AsyncCallback;
import org.eclipse.jgit.storage.dht.ChunkKey;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.ObjectIndexKey;
import org.eclipse.jgit.storage.dht.ObjectInfo;
import org.eclipse.jgit.storage.dht.RepositoryKey;
import org.eclipse.jgit.storage.dht.StreamingCallback;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.ObjectIndexTable;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;

final class JdbcObjectIndexTable implements ObjectIndexTable {
	private final JdbcDatabase db;

	JdbcObjectIndexTable(JdbcDatabase db) {
		this.db = db;
	}

	public void get(Context options, Set<ObjectIndexKey> objects,
			AsyncCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>> callback) {
		db.submit(new Loader(new ArrayList<ObjectIndexKey>(objects), callback));
	}

	private class Loader implements Runnable {
		private final List<ObjectIndexKey> keys;

		private final AsyncCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>> normal;

		private final StreamingCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>> streaming;

		private final Map<ObjectIndexKey, Collection<ObjectInfo>> results;

		Loader(List<ObjectIndexKey> keys,
				AsyncCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>> callback) {
			this.keys = keys;
			this.normal = callback;

			if (callback instanceof StreamingCallback<?>) {
				streaming = (StreamingCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>>) callback;
				results = null;
			} else {
				streaming = null;
				results = new HashMap<ObjectIndexKey, Collection<ObjectInfo>>(
						keys.size());
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
					select(conn);
					normal.onSuccess(results);
				} catch (SQLException err) {
					normal.onFailure(new DhtException(err));
				}
			} finally {
				db.release(conn);
			}
		}

		private void select(Client conn) throws SQLException {
			String sqlPrefix = "SELECT object_name, chunk_hash, object_info FROM object_index WHERE repo_id = ? AND object_name IN (?";
			int batchSize = 20;

			for (int keyIdx = 0; keyIdx < keys.size();) {
				int first = keyIdx++;
				RepositoryKey repo = keys.get(first).getRepositoryKey();
				StringBuilder sql = new StringBuilder();
				sql.append(sqlPrefix);
				while (keyIdx < keys.size() && keyIdx - first < batchSize
						&& repo.equals(keys.get(keyIdx).getRepositoryKey())) {
					keyIdx++;
					sql.append(",?");
				}
				sql.append(")");

				Map<ObjectIndexKey, Collection<ObjectInfo>> out;
				if (streaming != null)
					out = new HashMap<ObjectIndexKey, Collection<ObjectInfo>>();
				else
					out = results;

				PreparedStatement stmt = conn.prepare(sql.toString());
				stmt.setInt(1, repo.asInt());
				for (int i = first; i < keyIdx; i++)
					stmt.setString(2 + (i - first), keys.get(i).name());
				ResultSet rs = stmt.executeQuery();
				try {
					while (rs.next()) {
						ObjectId objId = ObjectId.fromString(rs.getString(1));
						ObjectId chunkHash = ObjectId.fromString(rs.getString(2));
						byte[] data = conn.getBytes(rs, 3);

						ObjectIndexKey objKey = ObjectIndexKey.create(repo, objId);
						Collection<ObjectInfo> list = out.get(objKey);
						if (list == null) {
							list = new ArrayList<ObjectInfo>(2);
							out.put(objKey, list);
						}

						ChunkKey chunk = ChunkKey.create(repo, chunkHash);
						list.add(ObjectInfo.fromBytes(chunk, data, -1));
					}
				} finally {
					rs.close();
					conn.release(stmt);
				}

				if (streaming != null)
					streaming.onPartialResult(out);
			}
		}
	}

	public void add(ObjectIndexKey objId, ObjectInfo info, WriteBuffer buffer)
			throws DhtException {
		JdbcBuffer buf = (JdbcBuffer) buffer;
		buf.submit(Add.OP, new Add.Item(objId, info));
	}

	private static class Add extends JdbcBuffer.Task<Add.Item> {
		static final Add OP = new Add();

		static class Item {
			final ObjectIndexKey objectName;

			final ObjectId chunkHash;

			final byte[] info;

			Item(ObjectIndexKey objId, ObjectInfo info) {
				this.objectName = objId;
				this.chunkHash = info.getChunkKey().getChunkHash();
				this.info = info.asBytes();
			}
		}

		@Override
		int size(Item data) {
			return 3 * 8 + 24 + 20 + 12 + data.info.length;
		}

		@Override
		void run(Client conn, List<Item> itemList) throws SQLException {
			// TODO If the record already exists, this may throw
			PreparedStatement stmt = conn.prepare("INSERT INTO object_index"
					+ " (repo_id, object_name, chunk_hash, object_info)"
					+ " VALUES (?, ?, ?, ?)");
			for (Item item : itemList) {
				stmt.setInt(1, item.objectName.getRepositoryKey().asInt());
				stmt.setString(2, item.objectName.name());
				stmt.setString(3, item.chunkHash.name());
				conn.setBytes(stmt, 4, item.info);
				stmt.addBatch();
			}
			conn.executeBatch(stmt);
		}
	}

	public void remove(ObjectIndexKey objId, ChunkKey chunk, WriteBuffer buffer)
			throws DhtException {
		JdbcBuffer buf = (JdbcBuffer) buffer;
		buf.submit(Remove.OP, new Remove.Item(objId, chunk));
	}

	private static class Remove extends JdbcBuffer.Task<Remove.Item> {
		static final Remove OP = new Remove();

		static class Item {
			final ObjectIndexKey objectName;

			final ObjectId chunkHash;

			Item(ObjectIndexKey objId, ChunkKey chunk) {
				this.objectName = objId;
				this.chunkHash = chunk.getChunkHash();
			}
		}

		@Override
		int size(Item data) {
			return 3 * 8 + 24 + 20;
		}

		@Override
		void run(Client conn, List<Item> itemList) throws SQLException {
			PreparedStatement stmt = conn.prepare("DELETE FROM object_index"
					+ " WHERE repo_id = ?" //
					+ " AND object_name = ?" //
					+ " AND chunk_hash = ?");
			for (Item item : itemList) {
				stmt.setInt(1, item.objectName.getRepositoryKey().asInt());
				stmt.setString(2, item.objectName.name());
				stmt.setString(3, item.chunkHash.name());
				stmt.addBatch();
			}
			conn.executeBatch(stmt);
		}
	}
}
