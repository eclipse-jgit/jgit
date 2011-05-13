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

import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.eclipse.jgit.generated.storage.dht.proto.GitStore.ChunkMeta;
import org.eclipse.jgit.storage.dht.AsyncCallback;
import org.eclipse.jgit.storage.dht.ChunkKey;
import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.PackChunk;
import org.eclipse.jgit.storage.dht.StreamingCallback;
import org.eclipse.jgit.storage.dht.Sync;
import org.eclipse.jgit.storage.dht.spi.ChunkTable;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;
import org.eclipse.jgit.storage.dht.spi.cache.CacheService.Change;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.WireFormat;

/** Cache wrapper around ChunkTable. */
public class CacheChunkTable implements ChunkTable {
	private final ChunkTable db;

	private final ExecutorService executor;

	private final CacheService client;

	private final Sync<Void> none;

	private final Namespace nsChunk = Namespace.CHUNK;

	private final Namespace nsMeta = Namespace.CHUNK_META;

	/**
	 * Initialize a new wrapper.
	 *
	 * @param dbTable
	 *            the underlying database's corresponding table.
	 * @param cacheDatabase
	 *            the cache database.
	 */
	public CacheChunkTable(ChunkTable dbTable, CacheDatabase cacheDatabase) {
		this.db = dbTable;
		this.executor = cacheDatabase.getExecutorService();
		this.client = cacheDatabase.getClient();
		this.none = Sync.none();
	}

	public void get(Context options, Set<ChunkKey> keys,
			AsyncCallback<Collection<PackChunk.Members>> callback) {
		List<CacheKey> toFind = new ArrayList<CacheKey>(keys.size());
		for (ChunkKey k : keys)
			toFind.add(nsChunk.key(k));
		client.get(toFind, new ChunkFromCache(options, keys, callback));
	}

	public void getMeta(Context options, Set<ChunkKey> keys,
			AsyncCallback<Map<ChunkKey, ChunkMeta>> callback) {
		List<CacheKey> toFind = new ArrayList<CacheKey>(keys.size());
		for (ChunkKey k : keys)
			toFind.add(nsMeta.key(k));
		client.get(toFind, new MetaFromCache(options, keys, callback));
	}

	public void put(PackChunk.Members chunk, WriteBuffer buffer)
			throws DhtException {
		CacheBuffer buf = (CacheBuffer) buffer;
		db.put(chunk, buf.getWriteBuffer());

		// Only store fragmented meta. This is all callers should ask for.
		if (chunk.hasMeta() && chunk.getMeta().getFragmentCount() != 0) {
			buf.put(nsMeta.key(chunk.getChunkKey()),
					chunk.getMeta().toByteArray());
		}

		if (chunk.hasChunkData())
			buf.put(nsChunk.key(chunk.getChunkKey()), encode(chunk));
		else
			buf.removeAfterFlush(nsChunk.key(chunk.getChunkKey()));
	}

	public void remove(ChunkKey key, WriteBuffer buffer) throws DhtException {
		CacheBuffer buf = (CacheBuffer) buffer;
		buf.remove(nsChunk.key(key));
		buf.remove(nsMeta.key(key));
		db.remove(key, buf.getWriteBuffer());
	}

	private static byte[] encode(PackChunk.Members members) {
		// Its too slow to encode ByteBuffer through the standard code.
		// Since the message is only 3 fields, do it by hand.
		ByteBuffer data = members.getChunkDataAsByteBuffer();
		ByteBuffer index = members.getChunkIndexAsByteBuffer();
		ChunkMeta meta = members.getMeta();

		int sz = 0;
		if (data != null)
			sz += computeByteBufferSize(1, data);
		if (index != null)
			sz += computeByteBufferSize(2, index);
		if (meta != null)
			sz += CodedOutputStream.computeMessageSize(3, meta);

		byte[] r = new byte[sz];
		CodedOutputStream out = CodedOutputStream.newInstance(r);
		try {
			if (data != null)
				writeByteBuffer(out, 1, data);
			if (index != null)
				writeByteBuffer(out, 2, index);
			if (meta != null)
				out.writeMessage(3, meta);
		} catch (IOException err) {
			throw new RuntimeException("Cannot buffer chunk", err);
		}
		return r;
	}

	private static int computeByteBufferSize(int fieldNumber, ByteBuffer data) {
		int n = data.remaining();
		return CodedOutputStream.computeTagSize(fieldNumber)
				+ CodedOutputStream.computeRawVarint32Size(n)
				+ n;
	}

	private static void writeByteBuffer(CodedOutputStream out, int fieldNumber,
			ByteBuffer data) throws IOException {
		byte[] d = data.array();
		int p = data.arrayOffset() + data.position();
		int n = data.remaining();
		out.writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
		out.writeRawVarint32(n);
		out.writeRawBytes(d, p, n);
	}

	private static PackChunk.Members decode(ChunkKey key, byte[] raw) {
		PackChunk.Members members = new PackChunk.Members();
		members.setChunkKey(key);

		// Its too slow to convert using the standard code, as copies
		// are made. Instead find offsets in the stream and use that.
		CodedInputStream in = CodedInputStream.newInstance(raw);
		try {
			int tag = in.readTag();
			for (;;) {
				switch (WireFormat.getTagFieldNumber(tag)) {
				case 0:
					return members;
				case 1: {
					int cnt = in.readRawVarint32();
					int ptr = in.getTotalBytesRead();
					members.setChunkData(raw, ptr, cnt);
					in.skipRawBytes(cnt);
					tag = in.readTag();
					if (WireFormat.getTagFieldNumber(tag) != 2)
						continue;
				}
				//$FALL-THROUGH$
				case 2: {
					int cnt = in.readRawVarint32();
					int ptr = in.getTotalBytesRead();
					members.setChunkIndex(raw, ptr, cnt);
					in.skipRawBytes(cnt);
					tag = in.readTag();
					if (WireFormat.getTagFieldNumber(tag) != 3)
						continue;
				}
				//$FALL-THROUGH$
				case 3: {
					int cnt = in.readRawVarint32();
					int oldLimit = in.pushLimit(cnt);
					members.setMeta(ChunkMeta.parseFrom(in));
					in.popLimit(oldLimit);
					tag = in.readTag();
					continue;
				}
				default:
					in.skipField(tag);
				}
			}
		} catch (IOException err) {
			throw new RuntimeException("Cannot decode chunk", err);
		}
	}

	private class ChunkFromCache implements
			StreamingCallback<Map<CacheKey, byte[]>> {
		private final Object lock = new Object();

		private final Context options;

		private final Set<ChunkKey> remaining;

		private final AsyncCallback<Collection<PackChunk.Members>> normalCallback;

		private final StreamingCallback<Collection<PackChunk.Members>> streamingCallback;

		private final List<PackChunk.Members> all;

		ChunkFromCache(Context options, Set<ChunkKey> keys,
				AsyncCallback<Collection<PackChunk.Members>> callback) {
			this.options = options;
			this.remaining = new HashSet<ChunkKey>(keys);
			this.normalCallback = callback;

			if (callback instanceof StreamingCallback<?>) {
				streamingCallback = (StreamingCallback<Collection<PackChunk.Members>>) callback;
				all = null;
			} else {
				streamingCallback = null;
				all = new ArrayList<PackChunk.Members>(keys.size());
			}
		}

		public void onPartialResult(Map<CacheKey, byte[]> result) {
			for (Map.Entry<CacheKey, byte[]> ent : result.entrySet()) {
				ChunkKey key = ChunkKey.fromBytes(ent.getKey().getBytes());
				PackChunk.Members members = decode(key, ent.getValue());

				if (streamingCallback != null) {
					streamingCallback.onPartialResult(singleton(members));

					synchronized (lock) {
						remaining.remove(key);
					}
				} else {
					synchronized (lock) {
						all.add(members);
						remaining.remove(key);
					}
				}
			}
		}

		public void onSuccess(Map<CacheKey, byte[]> result) {
			if (result != null && !result.isEmpty())
				onPartialResult(result);

			synchronized (lock) {
				if (remaining.isEmpty() || options == Context.FAST_MISSING_OK) {
					normalCallback.onSuccess(all);
				} else {
					db.get(options, remaining, new ChunkFromDatabase(all,
							normalCallback, streamingCallback));
				}
			}
		}

		public void onFailure(DhtException error) {
			// TODO(spearce) We may want to just drop to database here.
			normalCallback.onFailure(error);
		}
	}

	private class ChunkFromDatabase implements
			StreamingCallback<Collection<PackChunk.Members>> {
		private final Object lock = new Object();

		private final List<PackChunk.Members> all;

		private final AsyncCallback<Collection<PackChunk.Members>> normalCallback;

		private final StreamingCallback<Collection<PackChunk.Members>> streamingCallback;

		ChunkFromDatabase(
				List<PackChunk.Members> all,
				AsyncCallback<Collection<PackChunk.Members>> normalCallback,
				StreamingCallback<Collection<PackChunk.Members>> streamingCallback) {
			this.all = all;
			this.normalCallback = normalCallback;
			this.streamingCallback = streamingCallback;
		}

		public void onPartialResult(Collection<PackChunk.Members> result) {
			final List<PackChunk.Members> toPutIntoCache = copy(result);

			if (streamingCallback != null)
				streamingCallback.onPartialResult(result);
			else {
				synchronized (lock) {
					all.addAll(result);
				}
			}

			// Encoding is rather expensive, so move the cache population
			// into it a different background thread to prevent the current
			// database task from being starved of time.
			//
			executor.submit(new Runnable() {
				public void run() {
					for (PackChunk.Members members : toPutIntoCache) {
						ChunkKey key = members.getChunkKey();
						Change op = Change.put(nsChunk.key(key), encode(members));
						client.modify(singleton(op), none);
					}
				}
			});
		}

		private <T> List<T> copy(Collection<T> result) {
			return new ArrayList<T>(result);
		}

		public void onSuccess(Collection<PackChunk.Members> result) {
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

	private class MetaFromCache implements
			StreamingCallback<Map<CacheKey, byte[]>> {
		private final Object lock = new Object();

		private final Context options;

		private final Set<ChunkKey> remaining;

		private final AsyncCallback<Map<ChunkKey, ChunkMeta>> normalCallback;

		private final StreamingCallback<Map<ChunkKey, ChunkMeta>> streamingCallback;

		private final Map<ChunkKey, ChunkMeta> all;

		MetaFromCache(Context options, Set<ChunkKey> keys,
				AsyncCallback<Map<ChunkKey, ChunkMeta>> callback) {
			this.options = options;
			this.remaining = new HashSet<ChunkKey>(keys);
			this.normalCallback = callback;

			if (callback instanceof StreamingCallback<?>) {
				streamingCallback = (StreamingCallback<Map<ChunkKey, ChunkMeta>>) callback;
				all = null;
			} else {
				streamingCallback = null;
				all = new HashMap<ChunkKey, ChunkMeta>();
			}
		}

		public void onPartialResult(Map<CacheKey, byte[]> result) {
			for (Map.Entry<CacheKey, byte[]> ent : result.entrySet()) {
				ChunkKey key = ChunkKey.fromBytes(ent.getKey().getBytes());
				ChunkMeta meta;
				try {
					meta = ChunkMeta.parseFrom(ent.getValue());
				} catch (InvalidProtocolBufferException e) {
					// Invalid meta message, remove the cell from cache.
					client.modify(singleton(Change.remove(ent.getKey())),
							Sync.<Void> none());
					continue;
				}

				if (streamingCallback != null) {
					streamingCallback.onPartialResult(singletonMap(key, meta));

					synchronized (lock) {
						remaining.remove(key);
					}
				} else {
					synchronized (lock) {
						all.put(key, meta);
						remaining.remove(key);
					}
				}
			}
		}

		public void onSuccess(Map<CacheKey, byte[]> result) {
			if (result != null && !result.isEmpty())
				onPartialResult(result);

			synchronized (lock) {
				if (remaining.isEmpty() || options == Context.FAST_MISSING_OK) {
					normalCallback.onSuccess(all);
				} else {
					db.getMeta(options, remaining, new MetaFromDatabase(all,
							normalCallback, streamingCallback));
				}
			}
		}

		public void onFailure(DhtException error) {
			// TODO(spearce) We may want to just drop to database here.
			normalCallback.onFailure(error);
		}
	}

	private class MetaFromDatabase implements
			StreamingCallback<Map<ChunkKey, ChunkMeta>> {
		private final Object lock = new Object();

		private final Map<ChunkKey, ChunkMeta> all;

		private final AsyncCallback<Map<ChunkKey, ChunkMeta>> normalCallback;

		private final StreamingCallback<Map<ChunkKey, ChunkMeta>> streamingCallback;

		MetaFromDatabase(Map<ChunkKey, ChunkMeta> all,
				AsyncCallback<Map<ChunkKey, ChunkMeta>> normalCallback,
				StreamingCallback<Map<ChunkKey, ChunkMeta>> streamingCallback) {
			this.all = all;
			this.normalCallback = normalCallback;
			this.streamingCallback = streamingCallback;
		}

		public void onPartialResult(Map<ChunkKey, ChunkMeta> result) {
			final Map<ChunkKey, ChunkMeta> toPutIntoCache = copy(result);

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
					for (Map.Entry<ChunkKey, ChunkMeta> ent
							: toPutIntoCache.entrySet()) {
						ChunkKey key = ent.getKey();
						Change op = Change.put(nsMeta.key(key),
								ent.getValue().toByteArray());
						client.modify(singleton(op), none);
					}
				}
			});
		}

		private <K, V> Map<K, V> copy(Map<K, V> result) {
			return new HashMap<K, V>(result);
		}

		public void onSuccess(Map<ChunkKey, ChunkMeta> result) {
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
