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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.generated.storage.dht.proto.GitStore.ChunkMeta;
import org.eclipse.jgit.storage.dht.AsyncCallback;
import org.eclipse.jgit.storage.dht.ChunkKey;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.DhtText;
import org.eclipse.jgit.storage.dht.PackChunk;
import org.eclipse.jgit.storage.dht.spi.ChunkTable;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;
import org.eclipse.jgit.storage.dht.spi.util.ColumnMatcher;

import com.google.protobuf.InvalidProtocolBufferException;

final class MemChunkTable implements ChunkTable {
	private final MemTable table = new MemTable();

	private final ColumnMatcher colData = new ColumnMatcher("data");

	private final ColumnMatcher colIndex = new ColumnMatcher("index");

	private final ColumnMatcher colMeta = new ColumnMatcher("meta");

	public void get(Context options, Set<ChunkKey> keys,
			AsyncCallback<Collection<PackChunk.Members>> callback) {
		int cnt = keys.size();
		List<PackChunk.Members> out = new ArrayList<PackChunk.Members>(cnt);

		for (ChunkKey chunk : keys) {
			byte[] row = chunk.asBytes();
			MemTable.Cell cell;

			cell = table.get(row, colData.name());
			if (cell == null)
				continue;

			PackChunk.Members m = new PackChunk.Members();
			m.setChunkKey(chunk);
			m.setChunkData(cell.getValue());

			cell = table.get(row, colIndex.name());
			if (cell != null)
				m.setChunkIndex(cell.getValue());

			cell = table.get(row, colMeta.name());
			if (cell != null) {
				try {
					m.setMeta(ChunkMeta.parseFrom(cell.getValue()));
				} catch (InvalidProtocolBufferException err) {
					callback.onFailure(new DhtException(MessageFormat.format(
							DhtText.get().invalidChunkMeta, chunk), err));
					return;
				}
			}

			out.add(m);
		}

		callback.onSuccess(out);
	}

	public void getMeta(Context options, Set<ChunkKey> keys,
			AsyncCallback<Map<ChunkKey, ChunkMeta>> callback) {
		Map<ChunkKey, ChunkMeta> out = new HashMap<ChunkKey, ChunkMeta>();

		for (ChunkKey chunk : keys) {
			byte[] row = chunk.asBytes();
			MemTable.Cell cell = table.get(row, colMeta.name());
			if (cell != null) {
				try {
					out.put(chunk, ChunkMeta.parseFrom(cell.getValue()));
				} catch (InvalidProtocolBufferException err) {
					callback.onFailure(new DhtException(MessageFormat.format(
							DhtText.get().invalidChunkMeta, chunk), err));
					return;
				}
			}
		}

		callback.onSuccess(out);
	}

	public void put(PackChunk.Members chunk, WriteBuffer buffer)
			throws DhtException {
		byte[] row = chunk.getChunkKey().asBytes();

		if (chunk.hasChunkData())
			table.put(row, colData.name(), chunk.getChunkData());

		if (chunk.hasChunkIndex())
			table.put(row, colIndex.name(), chunk.getChunkIndex());

		if (chunk.hasMeta())
			table.put(row, colMeta.name(), chunk.getMeta().toByteArray());
	}

	public void remove(ChunkKey key, WriteBuffer buffer) throws DhtException {
		table.deleteRow(key.asBytes());
	}
}
