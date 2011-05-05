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
import java.util.concurrent.TimeoutException;

import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.RepositoryKey;
import org.eclipse.jgit.storage.dht.RepositoryName;
import org.eclipse.jgit.storage.dht.spi.RepositoryIndexTable;

class HBaseRepositoryIndexTable implements RepositoryIndexTable {
	private static final byte[] QUAL = {};

	private static final byte[] TRUE = { '1' };

	private final HTableInterface repositoryIndex;

	private final HTableInterface repository;

	private final byte[] colId;

	private final byte[] colName;

	HBaseRepositoryIndexTable(HBaseDatabase db) throws DhtException {
		this.repositoryIndex = db.openTable("REPOSITORY_INDEX");
		this.repository = db.openTable("REPOSITORY");
		this.colId = Bytes.toBytes("id");
		this.colName = Bytes.toBytes("name");
	}

	public RepositoryKey get(RepositoryName name) throws DhtException,
			TimeoutException {
		Get get = new Get(name.asBytes());
		get.addColumn(colId, QUAL);

		Result row;
		try {
			row = repositoryIndex.get(get);
		} catch (IOException e) {
			throw new DhtException(e);
		}

		if (row != null && !row.isEmpty())
			return RepositoryKey.fromBytes(row.value());
		return null;
	}

	public void putUnique(RepositoryName name, RepositoryKey key)
			throws DhtException, TimeoutException {
		Put put = new Put(name.asBytes());
		put.add(colId, QUAL, key.asBytes());

		boolean ok;
		try {
			ok = repositoryIndex.checkAndPut( //
					name.asBytes(), //
					colId, //
					QUAL, //
					null, //
					put);
			if (ok) {
				put = new Put(key.asBytes());
				put.add(colName, name.asBytes(), TRUE);
				repository.put(put);
			}
		} catch (IOException e) {
			throw new DhtException(e);
		}

		if (!ok)
			throw new DhtException("repository exists " + name.asString());
	}
}
