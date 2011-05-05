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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.eclipse.jgit.storage.dht.AsyncCallback;
import org.eclipse.jgit.storage.dht.ChunkKey;
import org.eclipse.jgit.storage.dht.ChunkMeta;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.PackChunk;
import org.eclipse.jgit.storage.dht.spi.ChunkTable;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;

final class HBaseChunkTable implements ChunkTable {
	private final HBaseDatabase db;

	private final HTableInterface table;

	private final byte[] colChunk;

	private final byte[] colIndex;

	private final byte[] colMeta;

	HBaseChunkTable(HBaseDatabase db) throws DhtException {
		this.db = db;
		this.table = db.openTable("CHUNK");
		this.colChunk = Bytes.toBytes("chunk");
		this.colIndex = Bytes.toBytes("index");
		this.colMeta = Bytes.toBytes("meta");
	}

	public void get(Context options, Set<ChunkKey> keys,
			final AsyncCallback<Collection<PackChunk.Members>> callback) {
		final List<Get> ops = new ArrayList<Get>(keys.size());
		for (ChunkKey key : keys) {
			Get get = new Get(key.asBytes());
			get.addFamily(colChunk);
			get.addFamily(colIndex);
			get.addFamily(colMeta);
			ops.add(get);
		}

		db.getExecutorService().submit(new Runnable() {
			public void run() {
				try {
					callback.onSuccess(parseChunks(table.get(ops)));
				} catch (Throwable err) {
					callback.onFailure(new DhtException(err));
				}
			}
		});
	}

	private List<PackChunk.Members> parseChunks(Result[] rows)
			throws DhtException {
		List<PackChunk.Members> chunkList =
			new ArrayList<PackChunk.Members>(rows.length);

		for (Result r : rows) {
			if (r == null || r.isEmpty())
				continue;

			ChunkKey key = ChunkKey.fromBytes(r.getRow());
			PackChunk.Members m = new PackChunk.Members();
			m.setChunkKey(key);

			byte[] chunk = r.getValue(colChunk, null);
			byte[] index = r.getValue(colIndex, null);
			byte[] meta = r.getValue(colMeta, null);

			if (chunk != null)
				m.setChunkData(chunk);
			if (index != null)
				m.setChunkIndex(index);
			if (meta != null)
				m.setMeta(ChunkMeta.fromBytes(key, meta));

			chunkList.add(m);
		}
		return chunkList;
	}

	public void getMeta(Context options, Set<ChunkKey> keys,
			final AsyncCallback<Collection<ChunkMeta>> callback) {
		final List<Get> ops = new ArrayList<Get>(keys.size());
		for (ChunkKey key : keys) {
			Get get = new Get(key.asBytes());
			get.addFamily(colMeta);
			ops.add(get);
		}

		db.getExecutorService().submit(new Runnable() {
			public void run() {
				try {
					callback.onSuccess(parseMeta(table.get(ops)));
				} catch (Throwable err) {
					callback.onFailure(new DhtException(err));
				}
			}
		});
	}

	private List<ChunkMeta> parseMeta(Result[] rows)
			throws DhtException {
		List<ChunkMeta> metaList = new ArrayList<ChunkMeta>(rows.length);
		for (Result r : rows) {
			if (r == null || r.isEmpty())
				continue;

			ChunkKey key = ChunkKey.fromBytes(r.getRow());
			byte[] meta = r.getValue(colMeta, null);
			metaList.add(ChunkMeta.fromBytes(key, meta));
		}
		return metaList;
	}

	public void put(PackChunk.Members chunk, WriteBuffer buffer)
			throws DhtException {
		HBaseBuffer buf = (HBaseBuffer) buffer;
		Put put = new Put(chunk.getChunkKey().asBytes());

		if (chunk.getChunkData() != null)
			put.add(colChunk, null, chunk.getChunkData());

		if (chunk.getChunkIndex() != null)
			put.add(colIndex, null, chunk.getChunkIndex());

		if (chunk.getMeta() != null)
			put.add(colMeta, null, chunk.getMeta().asBytes());

		buf.write(table, put);
	}

	public void remove(ChunkKey key, WriteBuffer buffer) throws DhtException {
		HBaseBuffer buf = (HBaseBuffer) buffer;
		buf.write(table, new Delete(key.asBytes()));
	}
}
