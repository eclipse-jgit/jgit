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
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.generated.storage.dht.proto.GitStore.CachedPackInfo;
import org.eclipse.jgit.storage.dht.CachedPackKey;
import org.eclipse.jgit.storage.dht.ChunkInfo;
import org.eclipse.jgit.storage.dht.ChunkKey;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.DhtText;
import org.eclipse.jgit.storage.dht.RepositoryKey;
import org.eclipse.jgit.storage.dht.spi.RepositoryTable;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;
import org.eclipse.jgit.storage.dht.spi.util.ColumnMatcher;

import com.google.protobuf.InvalidProtocolBufferException;

final class MemRepositoryTable implements RepositoryTable {
	private final AtomicInteger nextId = new AtomicInteger();

	private final MemTable table = new MemTable();

	private final ColumnMatcher colChunkInfo = new ColumnMatcher("chunk-info:");

	private final ColumnMatcher colCachedPack = new ColumnMatcher("cached-pack:");

	public RepositoryKey nextKey() throws DhtException {
		return RepositoryKey.create(nextId.incrementAndGet());
	}

	public void put(RepositoryKey repo, ChunkInfo info, WriteBuffer buffer)
			throws DhtException {
		table.put(repo.asBytes(),
				colChunkInfo.append(info.getChunkKey().asBytes()),
				info.getData().toByteArray());
	}

	public void remove(RepositoryKey repo, ChunkKey chunk, WriteBuffer buffer)
			throws DhtException {
		table.delete(repo.asBytes(), colChunkInfo.append(chunk.asBytes()));
	}

	public Collection<CachedPackInfo> getCachedPacks(RepositoryKey repo)
			throws DhtException, TimeoutException {
		List<CachedPackInfo> out = new ArrayList<CachedPackInfo>(4);
		for (MemTable.Cell cell : table.scanFamily(repo.asBytes(), colCachedPack)) {
			try {
				out.add(CachedPackInfo.parseFrom(cell.getValue()));
			} catch (InvalidProtocolBufferException e) {
				throw new DhtException(MessageFormat.format(
						DhtText.get().invalidCachedPackInfo, repo,
						CachedPackKey.fromBytes(cell.getName())), e);
			}
		}
		return out;
	}

	public void put(RepositoryKey repo, CachedPackInfo info, WriteBuffer buffer)
			throws DhtException {
		CachedPackKey key = CachedPackKey.fromInfo(info);
		table.put(repo.asBytes(),
				colCachedPack.append(key.asBytes()),
				info.toByteArray());
	}

	public void remove(RepositoryKey repo, CachedPackKey key, WriteBuffer buffer)
			throws DhtException {
		table.delete(repo.asBytes(), colCachedPack.append(key.asBytes()));
	}
}
