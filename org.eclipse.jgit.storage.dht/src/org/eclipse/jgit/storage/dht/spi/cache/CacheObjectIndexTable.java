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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.eclipse.jgit.generated.storage.dht.proto.GitCache.CachedObjectIndex;
import org.eclipse.jgit.storage.dht.AsyncCallback;
import org.eclipse.jgit.storage.dht.ChunkKey;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.ObjectIndexKey;
import org.eclipse.jgit.storage.dht.ObjectInfo;
import org.eclipse.jgit.storage.dht.StreamingCallback;
import org.eclipse.jgit.storage.dht.Sync;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.ObjectIndexTable;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;
import org.eclipse.jgit.storage.dht.spi.cache.CacheService.Change;

import com.google.protobuf.InvalidProtocolBufferException;

/** Cache wrapper around ObjectIndexTable. */
public class CacheObjectIndexTable implements ObjectIndexTable {
	private final ObjectIndexTable db;

	private final ExecutorService executor;

	private final CacheService client;

	private final Namespace ns = Namespace.OBJECT_INDEX;

	/**
	 * Initialize a new wrapper.
	 *
	 * @param dbTable
	 *            the underlying database's corresponding table.
	 * @param cacheDatabase
	 *            the cache database.
	 */
	public CacheObjectIndexTable(ObjectIndexTable dbTable,
			CacheDatabase cacheDatabase) {
		this.db = dbTable;
		this.executor = cacheDatabase.getExecutorService();
		this.client = cacheDatabase.getClient();
	}

	public void get(Context options, Set<ObjectIndexKey> objects,
			AsyncCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>> callback) {
		List<CacheKey> toFind = new ArrayList<CacheKey>(objects.size());
		for (ObjectIndexKey k : objects)
			toFind.add(ns.key(k));
		client.get(toFind, new LoaderFromCache(options, objects, callback));
	}

	public void add(ObjectIndexKey objId, ObjectInfo info, WriteBuffer buffer)
			throws DhtException {
		// During addition, the cache is not populated. This prevents a
		// race condition when the cache is cold. Readers need to scan
		// the database and ensure the oldest ObjectInfo is loaded into
		// the cache in order to allow PackChunk to break delta cycles.
		//
		// This does have a small performance penalty, as recently added
		// objects are often read not long after they were written. But
		// without good multi-system transaction support between the
		// cache and the underlying storage we cannot do better.
		//
		db.add(objId, info, ((CacheBuffer) buffer).getWriteBuffer());
	}

	public void remove(ObjectIndexKey objId, ChunkKey chunk, WriteBuffer buffer)
			throws DhtException {
		CacheBuffer buf = (CacheBuffer) buffer;
		db.remove(objId, chunk, buf.getWriteBuffer());

		// TODO This suffers from a race condition. The removal from the
		// cache can occur before the database update takes place, and a
		// concurrent reader might re-populate the cache with the stale data.
		//
		buf.remove(ns.key(objId));
	}

	private class LoaderFromCache implements
			StreamingCallback<Map<CacheKey, byte[]>> {
		private final Object lock = new Object();

		private final Context options;

		private final Set<ObjectIndexKey> remaining;

		private final AsyncCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>> normalCallback;

		private final StreamingCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>> streamingCallback;

		private final Map<ObjectIndexKey, Collection<ObjectInfo>> all;

		LoaderFromCache(
				Context options,
				Set<ObjectIndexKey> objects,
				AsyncCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>> callback) {
			this.options = options;
			this.remaining = new HashSet<ObjectIndexKey>(objects);
			this.normalCallback = callback;

			if (callback instanceof StreamingCallback<?>) {
				streamingCallback = (StreamingCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>>) callback;
				all = null;
			} else {
				streamingCallback = null;
				all = new HashMap<ObjectIndexKey, Collection<ObjectInfo>>();
			}
		}

		public void onPartialResult(Map<CacheKey, byte[]> result) {
			Map<ObjectIndexKey, Collection<ObjectInfo>> tmp;
			if (streamingCallback != null)
				tmp = new HashMap<ObjectIndexKey, Collection<ObjectInfo>>();
			else
				tmp = null;

			for (Map.Entry<CacheKey, byte[]> e : result.entrySet()) {
				ObjectIndexKey objKey;
				Collection<ObjectInfo> list;
				try {
					list = decode(e.getValue());
				} catch (InvalidProtocolBufferException badCell) {
					client.modify(
							Collections.singleton(Change.remove(e.getKey())),
							Sync.<Void> none());
					continue;
				}
				objKey = ObjectIndexKey.fromBytes(e.getKey().getBytes());

				if (tmp != null)
					tmp.put(objKey, list);
				else {
					synchronized (lock) {
						all.put(objKey, list);
						remaining.remove(objKey);
					}
				}
			}

			if (tmp != null) {
				streamingCallback.onPartialResult(tmp);
				synchronized (lock) {
					remaining.removeAll(tmp.keySet());
				}
			}
		}

		private Collection<ObjectInfo> decode(byte[] value)
				throws InvalidProtocolBufferException {
			CachedObjectIndex cacheEntry = CachedObjectIndex.parseFrom(value);
			int sz = cacheEntry.getItemCount();
			ObjectInfo[] r = new ObjectInfo[sz];
			for (int i = 0; i < sz; i++) {
				CachedObjectIndex.Item item = cacheEntry.getItem(i);
				r[i] = new ObjectInfo(
						ChunkKey.fromString(item.getChunkKey()),
						item.getTime(),
						item.getObjectInfo());
			}
			return Arrays.asList(r);
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
			StreamingCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>> {
		private final Object lock = new Object();

		private final Map<ObjectIndexKey, Collection<ObjectInfo>> all;

		private final AsyncCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>> normalCallback;

		private final StreamingCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>> streamingCallback;

		LoaderFromDatabase(
				Map<ObjectIndexKey, Collection<ObjectInfo>> all,
				AsyncCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>> normalCallback,
				StreamingCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>> streamingCallback) {
			this.all = all;
			this.normalCallback = normalCallback;
			this.streamingCallback = streamingCallback;
		}

		public void onPartialResult(
				Map<ObjectIndexKey, Collection<ObjectInfo>> result) {
			final Map<ObjectIndexKey, Collection<ObjectInfo>> toPut = copy(result);

			if (streamingCallback != null)
				streamingCallback.onPartialResult(result);
			else {
				synchronized (lock) {
					all.putAll(result);
				}
			}

			// Encoding is rather expensive, so move the cache population
			// into it a different background thread to prevent the current
			// database task from being starved of time.
			//
			executor.submit(new Runnable() {
				public void run() {
					List<Change> ops = new ArrayList<Change>(toPut.size());

					for (Map.Entry<ObjectIndexKey, Collection<ObjectInfo>> e : all(toPut)) {
						List<ObjectInfo> items = copy(e.getValue());
						ObjectInfo.sort(items);
						ops.add(Change.put(ns.key(e.getKey()), encode(items)));
					}

					client.modify(ops, Sync.<Void> none());
				}

				private byte[] encode(List<ObjectInfo> items) {
					CachedObjectIndex.Builder b;
					b = CachedObjectIndex.newBuilder();
					for (ObjectInfo info : items) {
						CachedObjectIndex.Item.Builder i = b.addItemBuilder();
						i.setChunkKey(info.getChunkKey().asString());
						i.setObjectInfo(info.getData());
						if (0 < info.getTime())
							i.setTime(info.getTime());
					}
					return b.build().toByteArray();
				}
			});
		}

		private <K, V> Map<K, V> copy(Map<K, V> map) {
			return new HashMap<K, V>(map);
		}

		private <T> List<T> copy(Collection<T> result) {
			return new ArrayList<T>(result);
		}

		private <K, V> Set<Map.Entry<K, V>> all(final Map<K, V> toPut) {
			return toPut.entrySet();
		}

		public void onSuccess(Map<ObjectIndexKey, Collection<ObjectInfo>> result) {
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
