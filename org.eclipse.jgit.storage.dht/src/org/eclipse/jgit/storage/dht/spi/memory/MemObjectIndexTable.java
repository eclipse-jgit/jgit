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

package org.eclipse.jgit.storage.dht.spi.memory;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.generated.storage.dht.proto.GitStore;
import org.eclipse.jgit.storage.dht.AsyncCallback;
import org.eclipse.jgit.storage.dht.ChunkKey;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.DhtText;
import org.eclipse.jgit.storage.dht.ObjectIndexKey;
import org.eclipse.jgit.storage.dht.ObjectInfo;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.ObjectIndexTable;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;
import org.eclipse.jgit.storage.dht.spi.util.ColumnMatcher;

import com.google.protobuf.InvalidProtocolBufferException;

final class MemObjectIndexTable implements ObjectIndexTable {
	private final MemTable table = new MemTable();

	private final ColumnMatcher colInfo = new ColumnMatcher("info:");

	public void get(Context options, Set<ObjectIndexKey> objects,
			AsyncCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>> callback) {
		Map<ObjectIndexKey, Collection<ObjectInfo>> out = new HashMap<ObjectIndexKey, Collection<ObjectInfo>>();

		for (ObjectIndexKey objId : objects) {
			for (MemTable.Cell cell : table.scanFamily(objId.asBytes(), colInfo)) {
				Collection<ObjectInfo> chunks = out.get(objId);
				ChunkKey chunkKey;
				if (chunks == null) {
					chunks = new ArrayList<ObjectInfo>(4);
					out.put(objId, chunks);
				}

				chunkKey = ChunkKey.fromBytes(colInfo.suffix(cell.getName()));
				try {
					chunks.add(new ObjectInfo(
							chunkKey,
							cell.getTimestamp(),
							GitStore.ObjectInfo.parseFrom(cell.getValue())));
				} catch (InvalidProtocolBufferException badCell) {
					callback.onFailure(new DhtException(MessageFormat.format(
							DhtText.get().invalidObjectInfo, objId, chunkKey),
							badCell));
					return;
				}
			}
		}

		callback.onSuccess(out);
	}

	public void add(ObjectIndexKey objId, ObjectInfo info, WriteBuffer buffer)
			throws DhtException {
		ChunkKey chunk = info.getChunkKey();
		table.put(objId.asBytes(), colInfo.append(chunk.asBytes()),
				info.getData().toByteArray());
	}

	public void remove(ObjectIndexKey objId, ChunkKey chunk, WriteBuffer buffer)
			throws DhtException {
		table.delete(objId.asBytes(), colInfo.append(chunk.asBytes()));
	}
}
