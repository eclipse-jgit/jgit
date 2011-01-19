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

import java.util.ArrayList;
import java.util.List;

/** List of all chunks that contain a large fragmented object. */
public class ChunkFragments {
	static ChunkFragments fromRaw(byte[] raw) {
		List<ChunkKey> chunks = new ArrayList<ChunkKey>(4);
		TinyProtobuf.Decoder d = TinyProtobuf.decode(raw);
		PARSE: for (;;) {
			switch (d.next()) {
			case 0:
				break PARSE;
			case 1:
				chunks.add(ChunkKey.fromBytes(d.bytes()));
				continue;
			default:
				d.skip();
				continue;
			}
		}
		return new ChunkFragments(chunks);
	}

	static byte[] toByteArray(List<ChunkKey> fragmentList) {
		int cnt = fragmentList.size();
		int max = cnt * (2 + ChunkKey.KEYLEN);
		TinyProtobuf.Encoder e = TinyProtobuf.encode(max);
		for (ChunkKey key : fragmentList)
			e.bytes(1, key.asByteArray());
		return e.asByteArray();
	}

	private final List<ChunkKey> chunks;

	ChunkFragments(List<ChunkKey> chunks) {
		this.chunks = chunks;
	}

	/** @return number of fragments for the object. */
	public int size() {
		return chunks.size();
	}

	/**
	 * Get the key of a specific chunk.
	 *
	 * @param idx
	 *            the index of the chunk to get. 0 is the first chunk, which
	 *            also should contain the fragment list as part of its row.
	 * @return the key.
	 */
	public ChunkKey getChunkKey(int idx) {
		return chunks.get(idx);
	}

	/**
	 * Find the key of the fragment that occurs after this chunk.
	 *
	 * @param chunkKey
	 *            the current chunk key.
	 * @return next chunk after this; null if there isn't one.
	 */
	public ChunkKey getNextFor(ChunkKey chunkKey) {
		for (int i = 0; i < chunks.size() - 1; i++) {
			if (chunks.get(i).equals(chunkKey))
				return chunks.get(i + 1);
		}
		return null;
	}
}
