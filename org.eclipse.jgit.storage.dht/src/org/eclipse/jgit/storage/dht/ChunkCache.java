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

package org.eclipse.jgit.storage.dht;

import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.lib.AnyObjectId;

/** Caches {@link PackChunk}s for fast reference. */
public class ChunkCache {
	private static final ChunkCache cache = new ChunkCache();

	static ChunkCache get() {
		return cache;
	}

	// TODO Use a better implementation.

	private final ReentrantLock lock;

	private final LinkedHashMap<ChunkKey, PackChunk> chunks;

	private ChunkCache() {
		final int capacity = 32;
		lock = new ReentrantLock(true /* fair */);
		chunks = new LinkedHashMap<ChunkKey, PackChunk>(capacity, 0.75f, true) {
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean removeEldestEntry(Entry<ChunkKey, PackChunk> e) {
				return capacity < size();
			}
		};
	}

	PackChunk get(ChunkKey key) {
		lock.lock();
		try {
			return chunks.get(key);
		} finally {
			lock.unlock();
		}
	}

	DhtReader.ChunkAndOffset find(RepositoryKey repo, AnyObjectId id) {
		lock.lock();
		try {
			for (PackChunk c : chunks.values()) {
				int pos = c.findOffset(repo, id);
				if (0 <= pos) {
					chunks.get(c.getChunkKey());
					return new DhtReader.ChunkAndOffset(c, pos);
				}
			}
			return null;
		} finally {
			lock.unlock();
		}
	}

	PackChunk put(PackChunk chunk) {
		lock.lock();
		try {
			if (chunks.containsKey(chunk.getChunkKey()))
				return chunks.get(chunk.getChunkKey());

			chunks.put(chunk.getChunkKey(), chunk);
			return chunk;
		} finally {
			lock.unlock();
		}
	}
}
