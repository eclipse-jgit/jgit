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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.eclipse.jgit.storage.dht.AsyncCallback;
import org.eclipse.jgit.storage.dht.ChunkKey;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.ObjectIndexKey;
import org.eclipse.jgit.storage.dht.ObjectInfo;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.ObjectIndexTable;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;

final class HBaseObjectIndexTable implements ObjectIndexTable {
	private final HBaseDatabase db;

	private final HTableInterface table;

	private final byte[] colInfo;

	HBaseObjectIndexTable(HBaseDatabase db) throws DhtException {
		this.db = db;
		this.table = db.openTable("OBJECT_INDEX");
		this.colInfo = Bytes.toBytes("info");
	}

	public void get(
			Context options,
			Set<ObjectIndexKey> objects,
			final AsyncCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>> callback) {
		final List<Get> ops = new ArrayList<Get>(objects.size());
		for (ObjectIndexKey obj : objects) {
			Get get = new Get(obj.asBytes());
			get.addFamily(colInfo);
			ops.add(get);
		}

		db.getExecutorService().submit(new Runnable() {
			public void run() {
				try {
					callback.onSuccess(findInfo(table.get(ops)));
				} catch (Throwable err) {
					callback.onFailure(new DhtException(err));
				}
			}
		});
	}

	private Map<ObjectIndexKey, Collection<ObjectInfo>> findInfo(Result[] rows) {
		Map<ObjectIndexKey, Collection<ObjectInfo>> map;

		map = new HashMap<ObjectIndexKey, Collection<ObjectInfo>>();
		for (Result r : rows) {
			if (r == null || r.isEmpty())
				continue;

			ObjectIndexKey key = ObjectIndexKey.fromBytes(r.getRow());
			Collection<ObjectInfo> list = map.get(key);
			if (list == null) {
				list = new ArrayList<ObjectInfo>(r.size());
				map.put(key, list);
			}

			for (KeyValue kv : r.raw()) {
				ChunkKey k = ChunkKey.fromBytes(kv.getQualifier());
				long time = kv.getTimestamp();
				list.add(ObjectInfo.fromBytes(k, kv.getValue(), time));
			}
		}

		return map;
	}

	public void add(ObjectIndexKey objId, ObjectInfo info, WriteBuffer buffer)
			throws DhtException {
		HBaseBuffer buf = (HBaseBuffer) buffer;
		ChunkKey chunk = info.getChunkKey();
		Put put = new Put(objId.asBytes());
		put.add(colInfo, chunk.asBytes(), info.asBytes());
		buf.write(table, put);
	}

	public void remove(ObjectIndexKey objId, ChunkKey chunk, WriteBuffer buffer)
			throws DhtException {
		HBaseBuffer buf = (HBaseBuffer) buffer;
		Delete del = new Delete(objId.asBytes());
		del.deleteColumns(colInfo, chunk.asBytes());
		buf.write(table, del);
	}
}
