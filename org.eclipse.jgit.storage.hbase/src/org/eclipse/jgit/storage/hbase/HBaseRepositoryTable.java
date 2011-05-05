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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.eclipse.jgit.storage.dht.CachedPackInfo;
import org.eclipse.jgit.storage.dht.CachedPackKey;
import org.eclipse.jgit.storage.dht.ChunkInfo;
import org.eclipse.jgit.storage.dht.ChunkKey;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.RepositoryKey;
import org.eclipse.jgit.storage.dht.spi.RepositoryTable;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;

class HBaseRepositoryTable implements RepositoryTable {
	private static final byte[] NONE = {};

	private final HTableInterface repository;

	private final byte[] colCachedPack;

	private final HTableInterface chunkInfo;

	private final byte[] colChunkInfo;

	private final HTableInterface sequence;

	private final byte[] rowNextKey;

	private final byte[] colNext;

	HBaseRepositoryTable(HBaseDatabase db) throws DhtException {
		this.repository = db.openTable("REPOSITORY");
		this.colCachedPack = Bytes.toBytes("cached-pack");

		this.chunkInfo = db.openTable("CHUNK_INFO");
		this.colChunkInfo = Bytes.toBytes("chunk-info");

		this.sequence = db.openTable("SEQUENCE");
		this.rowNextKey = Bytes.toBytes("RepositoryKey");
		this.colNext = Bytes.toBytes("next");
	}

	public RepositoryKey nextKey() throws DhtException {
		long id;
		try {
			id = sequence.incrementColumnValue(rowNextKey, colNext, NONE, 1);
		} catch (IOException err) {
			throw new DhtException(err);
		}
		if (Integer.MAX_VALUE < id)
			throw new DhtException("No more available RepositoryKeys");
		return RepositoryKey.create((int) id);
	}

	public Collection<CachedPackInfo> getCachedPacks(RepositoryKey repo)
			throws DhtException, TimeoutException {
		Get get = new Get(repo.asBytes());
		get.addFamily(colCachedPack);

		Result row;
		try {
			row = repository.get(get);
		} catch (IOException e) {
			throw new DhtException(e);
		}
		if (row == null || row.isEmpty())
			return Collections.emptyList();

		int estCnt = row.size();
		List<CachedPackInfo> info = new ArrayList<CachedPackInfo>(estCnt);
		for (KeyValue kv : row.raw())
			info.add(CachedPackInfo.fromBytes(kv.getValue()));
		return info;
	}

	public void put(RepositoryKey repo, CachedPackInfo info, WriteBuffer buffer)
			throws DhtException {
		HBaseBuffer buf = (HBaseBuffer) buffer;
		CachedPackKey packName = info.getRowKey();
		Put put = new Put(repo.asBytes());
		put.add(colCachedPack, packName.asBytes(), info.asBytes());
		buf.write(repository, put);
	}

	public void remove(RepositoryKey repo, CachedPackKey packName,
			WriteBuffer buffer) throws DhtException {
		HBaseBuffer buf = (HBaseBuffer) buffer;
		Delete del = new Delete(repo.asBytes());
		del.deleteColumns(colCachedPack, packName.asBytes());
		buf.write(repository, del);
	}

	public void put(RepositoryKey repo, ChunkInfo info, WriteBuffer buffer)
			throws DhtException {
		HBaseBuffer buf = (HBaseBuffer) buffer;
		ChunkKey chunk = info.getChunkKey();
		Put put = new Put(repo.asBytes());
		put.add(colChunkInfo, chunk.asBytes(), info.asBytes());
		buf.write(chunkInfo, put);
	}

	public void remove(RepositoryKey repo, ChunkKey chunk, WriteBuffer buffer)
			throws DhtException {
		HBaseBuffer buf = (HBaseBuffer) buffer;
		Delete del = new Delete(repo.asBytes());
		del.deleteColumns(colChunkInfo, chunk.asBytes());
		buf.write(chunkInfo, del);
	}
}
