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

import java.text.MessageFormat;
import java.util.List;

import org.eclipse.jgit.generated.storage.dht.proto.GitStore.ChunkMeta;
import org.eclipse.jgit.generated.storage.dht.proto.GitStore.ChunkMeta.BaseChunk;

class ChunkMetaUtil {
	static BaseChunk getBaseChunk(ChunkKey chunkKey, ChunkMeta meta,
			long position) throws DhtException {
		// Chunks are sorted by ascending relative_start order.
		// Thus for a pack sequence of: A B C, we have:
		//
		// -- C relative_start = 10,000
		// -- B relative_start = 20,000
		// -- A relative_start = 30,000
		//
		// Indicating that chunk C starts 10,000 bytes before us,
		// chunk B starts 20,000 bytes before us (and 10,000 before C),
		// chunk A starts 30,000 bytes before us (and 10,000 before B),
		//
		// If position falls within:
		//
		// -- C (10k), then position is between 0..10,000
		// -- B (20k), then position is between 10,000 .. 20,000
		// -- A (30k), then position is between 20,000 .. 30,000

		List<BaseChunk> baseChunks = meta.getBaseChunkList();
		int high = baseChunks.size();
		int low = 0;
		while (low < high) {
			final int mid = (low + high) >>> 1;
			final BaseChunk base = baseChunks.get(mid);

			if (position > base.getRelativeStart()) {
				low = mid + 1;

			} else if (mid == 0 || position == base.getRelativeStart()) {
				return base;

			} else if (baseChunks.get(mid - 1).getRelativeStart() < position) {
				return base;

			} else {
				high = mid;
			}
		}

		throw new DhtException(MessageFormat.format(
				DhtText.get().missingLongOffsetBase, chunkKey,
				Long.valueOf(position)));
	}

	static ChunkKey getNextFragment(ChunkMeta meta, ChunkKey chunkKey) {
		int cnt = meta.getFragmentCount();
		for (int i = 0; i < cnt - 1; i++) {
			ChunkKey key = ChunkKey.fromString(meta.getFragment(i));
			if (chunkKey.equals(key))
				return ChunkKey.fromString(meta.getFragment(i + 1));
		}
		return null;
	}

	private ChunkMetaUtil() {
		// Static utilities only, do not create instances.
	}
}
