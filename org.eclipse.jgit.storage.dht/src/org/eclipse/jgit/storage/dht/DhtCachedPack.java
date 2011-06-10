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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.generated.storage.dht.proto.GitStore.CachedPackInfo;
import org.eclipse.jgit.generated.storage.dht.proto.GitStore.CachedPackInfo.ChunkList;
import org.eclipse.jgit.generated.storage.dht.proto.GitStore.ChunkMeta;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.storage.pack.CachedPack;
import org.eclipse.jgit.storage.pack.ObjectToPack;
import org.eclipse.jgit.storage.pack.PackOutputStream;
import org.eclipse.jgit.storage.pack.StoredObjectRepresentation;

/** A cached pack stored by the DHT. */
public class DhtCachedPack extends CachedPack {
	private final CachedPackInfo info;

	private Set<ObjectId> tips;

	private Set<ChunkKey> keySet;

	private ChunkKey[] keyList;

	DhtCachedPack(CachedPackInfo info) {
		this.info = info;
	}

	@Override
	public Set<ObjectId> getTips() {
		if (tips == null) {
			tips = new HashSet<ObjectId>();
			for (String idString : info.getTipList().getObjectNameList())
				tips.add(ObjectId.fromString(idString));
			tips = Collections.unmodifiableSet(tips);
		}
		return tips;
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
		if (keySet == null)
			init();
		return keySet.contains(objrep.getChunkKey());
	}

	private void init() {
		ChunkList chunkList = info.getChunkList();
		int cnt = chunkList.getChunkKeyCount();
		keySet = new HashSet<ChunkKey>();
		keyList = new ChunkKey[cnt];
		for (int i = 0; i < cnt; i++) {
			ChunkKey key = ChunkKey.fromString(chunkList.getChunkKey(i));
			keySet.add(key);
			keyList[i] = key;
		}
	}

	void copyAsIs(PackOutputStream out, boolean validate, DhtReader ctx)
			throws IOException {
		if (keyList == null)
			init();

		// Clear the recent chunks because all of the reader's
		// chunk limit should be made available for prefetch.
		int cacheLimit = ctx.getOptions().getChunkLimit();
		ctx.getRecentChunks().setMaxBytes(0);
		try {
			Prefetcher p = new Prefetcher(ctx, 0, cacheLimit);
			p.push(Arrays.asList(keyList));
			copyPack(out, p, validate);
		} finally {
			ctx.getRecentChunks().setMaxBytes(cacheLimit);
		}
	}

	private void copyPack(PackOutputStream out, Prefetcher prefetcher,
			boolean validate) throws DhtException, DhtMissingChunkException,
			IOException {
		Map<String, Long> startsAt = new HashMap<String, Long>();
		for (ChunkKey key : keyList) {
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
			ChunkMeta meta = chunk.getMeta();
			if (meta != null && meta.getBaseChunkCount() != 0) {
				for (ChunkMeta.BaseChunk base : meta.getBaseChunkList()) {
					Long act = startsAt.get(base.getChunkKey());
					long exp = position - base.getRelativeStart();

					if (act == null) {
						throw new DhtException(MessageFormat.format(DhtText
								.get().wrongChunkPositionInCachedPack,
								rowKey(), base.getChunkKey(),
								"[not written]", key, Long.valueOf(exp)));
					}

					if (act.longValue() != exp) {
						throw new DhtException(MessageFormat.format(DhtText
								.get().wrongChunkPositionInCachedPack,
								rowKey(), base.getChunkKey(),
								act, key, Long.valueOf(exp)));
					}
				}
			}

			startsAt.put(key.asString(), Long.valueOf(position));
			chunk.copyEntireChunkAsIs(out, null, validate);
		}
	}

	private String rowKey() {
		return info.getName() + "." + info.getVersion();
	}
}
