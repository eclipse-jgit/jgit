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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jgit.storage.dht.DhtException;

/**
 * An open connection with a JDBC server.
 * <p>
 * Client instances should be obtained from and returned to the database's
 * {@link ClientPool}.
 */
public class Client {
	private final Connection conn;

	private final Map<String, PreparedStatement> statementCache;

	private boolean pending;

	Client(Connection conn) throws SQLException {
		this.conn = conn;

		final int max = 10;
		statementCache = new LinkedHashMap<String, PreparedStatement>(max, 0.75f, true) {
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean removeEldestEntry(Entry<String, PreparedStatement> e) {
				if (size() < max)
					return false;
				try {
					e.getValue().close();
				} catch (SQLException err) {
					// Ignore close failures on cache eviction.
				}
				return true;
			}
		};

		conn.setAutoCommit(true);
	}

	Statement createStatement() throws SQLException {
		return conn.createStatement();
	}

	boolean hasPending() {
		return pending;
	}

	void clear() {
		try {
			for (PreparedStatement stmt : statementCache.values()) {
				stmt.clearBatch();
				stmt.close();
			}
			pending = false;
		} catch (SQLException err) {
			// Keep pending, the caller will close instead.
			pending = true;
		}
	}

	PreparedStatement prepare(String sql) throws SQLException {
		PreparedStatement stmt = statementCache.get(sql);
		if (stmt == null) {
			stmt = conn.prepareStatement(sql);
			statementCache.put(sql, stmt);
		}
		pending = true;
		return stmt;
	}

	int[] executeBatch(PreparedStatement stmt) throws SQLException {
		int[] status = stmt.executeBatch();
		release(stmt);
		return status;
	}

	@SuppressWarnings("unused")
	void release(PreparedStatement stmt) throws SQLException {
		pending = false;
	}

	byte[] getBytes(ResultSet rs, int idx) throws SQLException {
		return rs.getBytes(idx);
	}

	void setBytes(PreparedStatement stmt, int idx, byte[] val)
			throws SQLException {
		stmt.setBytes(idx, val);
	}

	long nextval(String sequenceName) throws SQLException {
		String sql = "SELECT NEXTVAL('" + sequenceName + "')";
		PreparedStatement stmt = prepare(sql);
		ResultSet rs = stmt.executeQuery();
		try {
			if (rs.next())
				return rs.getLong(1);
			else
				throw new SQLException();
		} finally {
			rs.close();
			release(stmt);
		}
	}

	void close() throws DhtException {
		try {
			try {
				for (PreparedStatement stmt : statementCache.values())
					stmt.close();
			} finally {
				conn.close();
			}
		} catch (SQLException err) {
			throw new DhtException(err);
		}
	}
}
