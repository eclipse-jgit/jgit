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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.storage.dht.CachedPackInfo;
import org.eclipse.jgit.storage.dht.CachedPackKey;
import org.eclipse.jgit.storage.dht.ChunkInfo;
import org.eclipse.jgit.storage.dht.ChunkKey;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.RepositoryKey;
import org.eclipse.jgit.storage.dht.spi.RepositoryTable;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;

final class JdbcRepositoryTable implements RepositoryTable {
	private final JdbcDatabase db;

	JdbcRepositoryTable(JdbcDatabase db) {
		this.db = db;
	}

	public RepositoryKey nextKey() throws DhtException {
		Client conn = db.get();
		try {
			long next = conn.nextval("repo_id");
			if (Integer.MAX_VALUE < next)
				throw new DhtException(
						JdbcText.get().noMoreRepositoryKeysAvailable);
			return RepositoryKey.create((int) next);
		} catch (SQLException err) {
			throw new DhtException(err);
		} finally {
			db.release(conn);
		}
	}

	public void put(RepositoryKey repo, ChunkInfo info, WriteBuffer buffer)
			throws DhtException {
		JdbcBuffer buf = (JdbcBuffer) buffer;
		buf.submit(PutChunkInfo.OP, new PutChunkInfo.Item(info));
	}

	private static class PutChunkInfo extends
			JdbcBuffer.Task<PutChunkInfo.Item> {
		static final PutChunkInfo OP = new PutChunkInfo();

		static class Item {
			final ChunkKey key;

			final byte[] data;

			Item(ChunkInfo info) {
				this.key = info.getChunkKey();
				this.data = info.asBytes();
			}
		}

		@Override
		int size(Item item) {
			return 8 + 4 + 2 * (8 + 20) + item.data.length;
		}

		@Override
		void run(Client conn, List<Item> itemList) throws SQLException {
			// TODO Fix DHT to not replace info during the same batch.
			Map<ChunkKey, Item> itemMap = new LinkedHashMap<ChunkKey, Item>();
			for (Item item : itemList) {
				itemMap.put(item.key, item);
			}
			itemList = new ArrayList<Item>(itemMap.values());

			PreparedStatement stmt = conn.prepare("UPDATE chunk_info"
					+ " SET chunk_info = ?" //
					+ " WHERE repo_id = ?" //
					+ " AND chunk_hash = ?");
			for (Item item : itemList) {
				conn.setBytes(stmt, 1, item.data);
				stmt.setInt(2, item.key.getRepositoryKey().asInt());
				stmt.setString(3, item.key.getChunkHash().name());
				stmt.addBatch();
			}

			int[] status = conn.executeBatch(stmt);
			int updated = 0;
			for (int s : status) {
				if (s == 1)
					updated++;
			}
			if (updated < itemList.size()) {
				stmt = conn.prepare("INSERT INTO chunk_info"
						+ " (repo_id, chunk_hash, chunk_info)" //
						+ " VALUES (?, ?, ?)");
				for (int i = 0; i < itemList.size(); i++) {
					if (status[i] != 0)
						continue;
					Item item = itemList.get(i);
					stmt.setInt(1, item.key.getRepositoryKey().asInt());
					stmt.setString(2, item.key.getChunkHash().name());
					conn.setBytes(stmt, 3, item.data);
					stmt.addBatch();
				}
				conn.executeBatch(stmt);
			}
		}
	}

	public void remove(RepositoryKey repo, ChunkKey chunk, WriteBuffer buffer)
			throws DhtException {
		JdbcBuffer buf = (JdbcBuffer) buffer;
		buf.submit(RemoveChunkInfo.OP, chunk);
	}

	private static class RemoveChunkInfo extends JdbcBuffer.Task<ChunkKey> {
		static final RemoveChunkInfo OP = new RemoveChunkInfo();

		@Override
		int size(ChunkKey data) {
			return 8 + 4 + 8 + 20;
		}

		@Override
		void run(Client conn, List<ChunkKey> keyList) throws SQLException {
			PreparedStatement stmt = conn.prepare("DELETE FROM chunk_info"
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

	public Collection<CachedPackInfo> getCachedPacks(RepositoryKey repo)
			throws DhtException, TimeoutException {
		Client conn = db.get();
		try {
			String sql = "SELECT pack_data FROM cached_pack WHERE repo_id = ?";
			PreparedStatement stmt = conn.prepare(sql);
			try {
				stmt.setInt(1, repo.asInt());

				ResultSet rs = stmt.executeQuery();
				try {
					List<CachedPackInfo> info = new ArrayList<CachedPackInfo>(2);
					while (rs.next()) {
						info.add(CachedPackInfo.fromBytes(conn.getBytes(rs, 1)));
					}
					return info;
				} finally {
					rs.close();
				}
			} finally {
				conn.release(stmt);
			}
		} catch (SQLException err) {
			throw new DhtException(err);
		} finally {
			db.release(conn);
		}
	}

	public void put(RepositoryKey repo, CachedPackInfo info, WriteBuffer buffer)
			throws DhtException {
		JdbcBuffer buf = (JdbcBuffer) buffer;
		buf.submit(PutCachedPack.OP, new PutCachedPack.Item(repo, info));
	}

	private static class PutCachedPack extends
			JdbcBuffer.Task<PutCachedPack.Item> {
		static final PutCachedPack OP = new PutCachedPack();

		static class Item {
			final RepositoryKey repo;

			final CachedPackKey key;

			final byte[] data;

			Item(RepositoryKey repo, CachedPackInfo info) {
				this.repo = repo;
				this.key = info.getRowKey();
				this.data = info.asBytes();
			}
		}

		@Override
		int size(Item item) {
			return 8 + 4 + 2 * (8 + 20) + item.data.length;
		}

		@Override
		void run(Client conn, List<Item> itemList) throws SQLException {
			PreparedStatement stmt = conn.prepare("INSERT INTO cached_pack"
					+ " (repo_id, pack_name, pack_version, pack_data)" //
					+ " VALUES (?, ?, ?, ?)");
			for (Item item : itemList) {
				stmt.setInt(1, item.repo.asInt());
				stmt.setString(2, item.key.getName().name());
				stmt.setString(3, item.key.getVersion().name());
				conn.setBytes(stmt, 4, item.data);
				stmt.addBatch();
			}
			conn.executeBatch(stmt);
		}
	}

	public void remove(RepositoryKey repo, CachedPackKey key, WriteBuffer buffer)
			throws DhtException {
		JdbcBuffer buf = (JdbcBuffer) buffer;
		buf.submit(DeleteCachedPack.OP, new DeleteCachedPack.Item(repo, key));
	}

	private static class DeleteCachedPack extends
			JdbcBuffer.Task<DeleteCachedPack.Item> {
		static final DeleteCachedPack OP = new DeleteCachedPack();

		static class Item {
			final RepositoryKey repo;

			final CachedPackKey key;

			Item(RepositoryKey repo, CachedPackKey key) {
				this.repo = repo;
				this.key = key;
			}
		}

		@Override
		int size(Item data) {
			return 8 + 4 + 2 * (8 + 20);
		}

		@Override
		void run(Client conn, List<Item> itemList) throws SQLException {
			PreparedStatement stmt = conn.prepare("DELETE FROM cached_pack"
					+ " WHERE repo_id = ?" //
					+ " AND pack_name = ?" //
					+ " AND pack_version = ?");
			for (Item item : itemList) {
				stmt.setInt(1, item.repo.asInt());
				stmt.setString(2, item.key.getName().name());
				stmt.setString(3, item.key.getVersion().name());
				stmt.addBatch();
			}
			conn.executeBatch(stmt);
		}
	}
}
