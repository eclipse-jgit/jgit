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

import java.io.IOException;
import java.util.HashMap;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.storage.dht.DhtReader.ChunkAndOffset;
import org.eclipse.jgit.storage.dht.RefDataUtil.IdWithChunk;

final class RecentChunks {
	private final DhtReader reader;

	private final DhtReader.Statistics stats;

	private final HashMap<ChunkKey, Node> byKey;

	private int maxBytes;

	private int curBytes;

	private Node lruHead;

	private Node lruTail;

	RecentChunks(DhtReader reader) {
		this.reader = reader;
		this.stats = reader.getStatistics();
		this.byKey = new HashMap<ChunkKey, Node>();
		this.maxBytes = reader.getOptions().getChunkLimit();
	}

	void setMaxBytes(int newMax) {
		maxBytes = Math.max(0, newMax);
		if (0 < maxBytes)
			prune();
		else
			clear();
	}

	PackChunk get(ChunkKey key) {
		Node n = byKey.get(key);
		if (n != null) {
			hit(n);
			stats.recentChunks_Hits++;
			return n.chunk;
		}
		stats.recentChunks_Miss++;
		return null;
	}

	void put(PackChunk chunk) {
		Node n = byKey.get(chunk.getChunkKey());
		if (n != null && n.chunk == chunk) {
			hit(n);
			return;
		}

		curBytes += chunk.getTotalSize();
		prune();

		n = new Node();
		n.chunk = chunk;
		byKey.put(chunk.getChunkKey(), n);
		first(n);
	}

	private void prune() {
		while (maxBytes < curBytes) {
			Node n = lruTail;
			if (n == null)
				break;

			PackChunk c = n.chunk;
			curBytes -= c.getTotalSize();
			byKey.remove(c.getChunkKey());
			remove(n);
		}
	}

	ObjectLoader open(RepositoryKey repo, AnyObjectId objId, int typeHint)
			throws IOException {
		if (objId instanceof IdWithChunk) {
			PackChunk chunk = get(((IdWithChunk) objId).getChunkKey());
			if (chunk != null) {
				int pos = chunk.findOffset(repo, objId);
				if (0 <= pos)
					return PackChunk.read(chunk, pos, reader, typeHint);
			}

			// IdWithChunk is only a hint, and can be wrong. Locally
			// searching is faster than looking in the Database.
		}

		for (Node n = lruHead; n != null; n = n.next) {
			int pos = n.chunk.findOffset(repo, objId);
			if (0 <= pos) {
				hit(n);
				stats.recentChunks_Hits++;
				return PackChunk.read(n.chunk, pos, reader, typeHint);
			}
		}

		return null;
	}

	ChunkAndOffset find(RepositoryKey repo, AnyObjectId objId) {
		if (objId instanceof IdWithChunk) {
			PackChunk chunk = get(((IdWithChunk) objId).getChunkKey());
			if (chunk != null) {
				int pos = chunk.findOffset(repo, objId);
				if (0 <= pos)
					return new ChunkAndOffset(chunk, pos);
			}

			// IdWithChunk is only a hint, and can be wrong. Locally
			// searching is faster than looking in the Database.
		}

		for (Node n = lruHead; n != null; n = n.next) {
			int pos = n.chunk.findOffset(repo, objId);
			if (0 <= pos) {
				hit(n);
				stats.recentChunks_Hits++;
				return new ChunkAndOffset(n.chunk, pos);
			}
		}

		return null;
	}

	boolean has(RepositoryKey repo, AnyObjectId objId) {
		for (Node n = lruHead; n != null; n = n.next) {
			int pos = n.chunk.findOffset(repo, objId);
			if (0 <= pos) {
				hit(n);
				stats.recentChunks_Hits++;
				return true;
			}
		}
		return false;
	}

	void clear() {
		curBytes = 0;
		lruHead = null;
		lruTail = null;
		byKey.clear();
	}

	private void hit(Node n) {
		if (lruHead != n) {
			remove(n);
			first(n);
		}
	}

	private void remove(Node node) {
		Node p = node.prev;
		Node n = node.next;

		if (p != null)
			p.next = n;
		if (n != null)
			n.prev = p;

		if (lruHead == node)
			lruHead = n;
		if (lruTail == node)
			lruTail = p;
	}

	private void first(Node node) {
		Node h = lruHead;

		node.prev = null;
		node.next = h;

		if (h != null)
			h.prev = node;
		else
			lruTail = node;

		lruHead = node;
	}

	private static class Node {
		PackChunk chunk;

		Node prev;

		Node next;
	}
}
