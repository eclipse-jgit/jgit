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

package org.eclipse.jgit.storage.dht.spi.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.storage.dht.AsyncCallback;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.ObjectListChunk;
import org.eclipse.jgit.storage.dht.ObjectListChunkKey;
import org.eclipse.jgit.storage.dht.StreamingCallback;
import org.eclipse.jgit.storage.dht.Sync;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.ObjectListTable;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;
import org.eclipse.jgit.storage.dht.spi.cache.CacheService.Change;

final class CacheObjectListTable implements ObjectListTable {
	private final ObjectListTable db;

	private final CacheService client;

	private final Namespace ns = Namespace.OBJECT_LIST;

	CacheObjectListTable(ObjectListTable db, CacheDatabase mem) {
		this.db = db;
		this.client = mem.getClient();
	}

	public void get(Context options, Set<ObjectListChunkKey> keys,
			AsyncCallback<Collection<ObjectListChunk>> callback) {
		List<CacheKey> toFind = new ArrayList<CacheKey>(keys.size());
		for (ObjectListChunkKey k : keys)
			toFind.add(ns.key(k));
		client.get(toFind, new LoaderFromCache(options, keys, callback));
	}

	public void put(ObjectListChunk chunk, WriteBuffer buffer)
			throws DhtException {
		CacheBuffer buf = (CacheBuffer) buffer;
		db.put(chunk, buf.getWriteBuffer());
		buf.modify(Change.put(ns.key(chunk.getRowKey()), chunk.toBytes()));
	}

	private class LoaderFromCache implements
			StreamingCallback<Map<CacheKey, byte[]>> {
		private final Object lock = new Object();

		private final Context options;

		private final Set<ObjectListChunkKey> remaining;

		private final AsyncCallback<Collection<ObjectListChunk>> normalCallback;

		private final StreamingCallback<Collection<ObjectListChunk>> streamingCallback;

		private final Collection<ObjectListChunk> all;

		LoaderFromCache(Context options, Set<ObjectListChunkKey> objects,
				AsyncCallback<Collection<ObjectListChunk>> callback) {
			this.options = options;
			this.remaining = new HashSet<ObjectListChunkKey>(objects);
			this.normalCallback = callback;

			if (callback instanceof StreamingCallback<?>) {
				streamingCallback = (StreamingCallback<Collection<ObjectListChunk>>) callback;
				all = null;
			} else {
				streamingCallback = null;
				all = new ArrayList<ObjectListChunk>(objects.size());
			}
		}

		public void onPartialResult(Map<CacheKey, byte[]> result) {
			Collection<ObjectListChunk> tmp;
			if (streamingCallback != null)
				tmp = new ArrayList<ObjectListChunk>(result.size());
			else
				tmp = null;

			synchronized (lock) {
				for (Map.Entry<CacheKey, byte[]> e : result.entrySet()) {
					ObjectListChunkKey key;
					ObjectListChunk listChunk;

					key = ObjectListChunkKey.fromBytes(e.getKey().getBytes());
					listChunk = ObjectListChunk.fromBytes(key, e.getValue());

					if (tmp != null)
						tmp.add(listChunk);
					else
						all.add(listChunk);
					remaining.remove(key);
				}

				if (tmp != null)
					streamingCallback.onPartialResult(tmp);
			}
		}

		public void onSuccess(Map<CacheKey, byte[]> result) {
			if (result != null && !result.isEmpty())
				onPartialResult(result);

			synchronized (lock) {
				if (remaining.isEmpty() || options == Context.FAST_MISSING_OK) {
					normalCallback.onSuccess(all);
				} else {
					db.get(options, remaining, new LoaderFromDatabase(all,
							normalCallback, streamingCallback));
				}
			}
		}

		public void onFailure(DhtException error) {
			// TODO(spearce) We may want to just drop to database here.
			normalCallback.onFailure(error);
		}
	}

	private class LoaderFromDatabase implements
			StreamingCallback<Collection<ObjectListChunk>> {
		private final Object lock = new Object();

		private final Collection<ObjectListChunk> all;

		private final AsyncCallback<Collection<ObjectListChunk>> normalCallback;

		private final StreamingCallback<Collection<ObjectListChunk>> streamingCallback;

		LoaderFromDatabase(Collection<ObjectListChunk> all,
				AsyncCallback<Collection<ObjectListChunk>> normalCallback,
				StreamingCallback<Collection<ObjectListChunk>> streamingCallback) {
			this.all = all;
			this.normalCallback = normalCallback;
			this.streamingCallback = streamingCallback;
		}

		public void onPartialResult(Collection<ObjectListChunk> result) {
			if (streamingCallback != null)
				streamingCallback.onPartialResult(result);
			else {
				synchronized (lock) {
					all.addAll(result);
				}
			}

			List<Change> ops = new ArrayList<Change>(result.size());
			for (ObjectListChunk lc : result)
				ops.add(Change.put(ns.key(lc.getRowKey()), lc.toBytes()));
			client.modify(ops, Sync.<Void> none());
		}

		public void onSuccess(Collection<ObjectListChunk> result) {
			if (result != null && !result.isEmpty())
				onPartialResult(result);

			synchronized (lock) {
				normalCallback.onSuccess(all);
			}
		}

		public void onFailure(DhtException error) {
			normalCallback.onFailure(error);
		}
	}
}
