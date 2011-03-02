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
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.storage.pack.CachedPack;
import org.eclipse.jgit.storage.pack.ObjectToPack;
import org.eclipse.jgit.storage.pack.PackOutputStream;
import org.eclipse.jgit.storage.pack.StoredObjectRepresentation;

/** A cached pack stored by the DHT. */
public class DhtCachedPack extends CachedPack {
	private final CachedPackInfo info;

	private Set<ChunkKey> chunkKeySet;

	DhtCachedPack(CachedPackInfo info) {
		this.info = info;
	}

	@Override
	public Set<ObjectId> getTips() {
		return Collections.unmodifiableSet(info.tips);
	}

	@Override
	public long getObjectCount() {
		return info.getObjectsTotal();
	}

	@Override
	public long getDeltaCount() throws IOException {
		return info.getObjectsDelta();
	}

	/** @return information describing this cached pack. */
	public CachedPackInfo getCachedPackInfo() {
		return info;
	}

	@Override
	public boolean hasObject(ObjectToPack obj, StoredObjectRepresentation rep) {
		DhtObjectRepresentation objrep = (DhtObjectRepresentation) rep;
		if (chunkKeySet == null)
			chunkKeySet = new HashSet<ChunkKey>(info.chunks);
		return chunkKeySet.contains(objrep.getChunkKey());
	}

	void copyAsIs(PackOutputStream out, boolean validate, DhtReader ctx)
			throws IOException {
		Prefetcher p = new Prefetcher(ctx, 0);
		p.setCacheLoadedChunks(false);
		p.push(info.chunks);
		copyPack(out, ctx, p, validate);
	}

	private void copyPack(PackOutputStream out, DhtReader ctx,
			Prefetcher prefetcher, boolean validate) throws DhtException,
			DhtMissingChunkException, IOException {
		Map<ChunkKey, Long> startsAt = new HashMap<ChunkKey, Long>();
		for (ChunkKey key : info.chunks) {
			PackChunk chunk = prefetcher.get(key);

			// The prefetcher should always produce the chunk for us, if not
			// there is something seriously wrong with the ordering or
			// within the prefetcher code and aborting is more sane than
			// using slow synchronous lookups.
			//
			if (chunk == null)
				throw new DhtMissingChunkException(key);

			// Verify each long OFS_DELTA chunk appears at the right offset.
			// This is a cheap validation that the cached pack hasn't been
			// incorrectly created and would confuse the client.
			//
			long position = out.length();
			if (chunk.getMeta() != null && chunk.getMeta().baseChunks != null) {
				for (ChunkMeta.BaseChunk base : chunk.getMeta().baseChunks) {
					Long act = startsAt.get(base.getChunkKey());
					long exp = position - base.getRelativeStart();

					if (act == null) {
						throw new DhtException(MessageFormat.format(DhtText
								.get().wrongChunkPositionInCachedPack, info
								.getRowKey(), base.getChunkKey(),
								"[not written]", key, exp));
					}

					if (act.longValue() != exp) {
						throw new DhtException(MessageFormat.format(DhtText
								.get().wrongChunkPositionInCachedPack, info
								.getRowKey(), base.getChunkKey(), //
								act, key, exp));
					}
				}
			}

			startsAt.put(key, Long.valueOf(position));
			chunk.copyEntireChunkAsIs(out, null, validate);
		}
	}
}
