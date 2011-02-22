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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.RefData;
import org.eclipse.jgit.storage.dht.RefKey;
import org.eclipse.jgit.storage.dht.RepositoryKey;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.RefTable;
import org.eclipse.jgit.util.Base64;

final class JdbcRefTable implements RefTable {
	private final JdbcDatabase db;

	JdbcRefTable(JdbcDatabase db) {
		this.db = db;
	}

	public Map<RefKey, RefData> getAll(Context options, RepositoryKey repo)
			throws DhtException, TimeoutException {
		Client conn = db.get();
		try {
			String sql = "SELECT ref_name, ref_data FROM ref WHERE repo_id = ?";
			PreparedStatement stmt = conn.prepare(sql);
			try {
				stmt.setInt(1, repo.asInt());

				ResultSet rs = stmt.executeQuery();
				try {
					Map<RefKey, RefData> all = new HashMap<RefKey, RefData>();
					while (rs.next()) {
						String name = rs.getString(1);
						String encData = rs.getString(2);
						byte[] rawData = Base64.decode(encData);
						RefData data = RefData.fromBytes(rawData);
						all.put(RefKey.create(repo, name), data);
					}
					return all;
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

	public boolean compareAndPut(RefKey refKey, RefData oldData, RefData newData)
			throws DhtException, TimeoutException {
		Client conn = db.get();
		try {
			String sql;

			if (oldData == RefData.NONE)
				sql = "INSERT INTO ref (repo_id, ref_name, ref_data) VALUES (?, ?, ?)";
			else
				sql = "UPDATE ref SET ref_data = ? WHERE repo_id = ? AND ref_name = ? AND ref_data = ?";

			PreparedStatement stmt = conn.prepare(sql);
			try {
				if (oldData == RefData.NONE) {
					stmt.setInt(1, refKey.getRepositoryKey().asInt());
					stmt.setString(2, refKey.getName());
					stmt.setString(3, Base64.encodeBytes(newData.asBytes()));
				} else {
					stmt.setString(1, Base64.encodeBytes(newData.asBytes()));
					stmt.setInt(2, refKey.getRepositoryKey().asInt());
					stmt.setString(3, refKey.getName());
					stmt.setString(4, Base64.encodeBytes(oldData.asBytes()));
				}
				return 1 == stmt.executeUpdate();
			} finally {
				conn.release(stmt);
			}
		} catch (SQLException err) {
			throw new DhtException(err);
		} finally {
			db.release(conn);
		}
	}

	public boolean compareAndRemove(RefKey refKey, RefData oldData)
			throws DhtException, TimeoutException {
		Client conn = db.get();
		try {
			String sql = "DELETE FROM ref WHERE repo_id = ? AND ref_name = ? AND ref_data = ?";
			PreparedStatement stmt = conn.prepare(sql);
			try {
				stmt.setInt(1, refKey.getRepositoryKey().asInt());
				stmt.setString(2, refKey.getName());
				stmt.setString(3, Base64.encodeBytes(oldData.asBytes()));
				return 1 == stmt.executeUpdate();
			} finally {
				conn.release(stmt);
			}
		} catch (SQLException err) {
			throw new DhtException(err);
		} finally {
			db.release(conn);
		}
	}
}
