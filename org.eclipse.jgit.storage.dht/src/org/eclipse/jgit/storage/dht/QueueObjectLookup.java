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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AsyncOperation;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.Database;

class QueueObjectLookup<T extends ObjectId> implements AsyncOperation {
	protected final RepositoryKey repo;

	protected final Database db;

	protected final DhtReader reader;

	private final DhtReaderOptions options;

	private final boolean reportMissing;

	private final ArrayList<ObjectInfo> tmp;

	private final int concurrentBatches;

	private int runningBatches;

	private Context context;

	private Iterator<T> toFind;

	private List<T> toRetry;

	private ObjectWithInfo<T> nextResult;

	private DhtException error;

	private boolean needChunkOnly;

	private boolean cacheLoadedInfo;

	QueueObjectLookup(DhtReader reader, boolean reportMissing) {
		this.repo = reader.getRepositoryKey();
		this.db = reader.getDatabase();
		this.reader = reader;
		this.options = reader.getOptions();
		this.reportMissing = reportMissing;
		this.tmp = new ArrayList<ObjectInfo>(4);
		this.context = Context.FAST_MISSING_OK;
		this.toRetry = new ArrayList<T>();

		this.concurrentBatches = options.getObjectIndexConcurrentBatches();
	}

	void setCacheLoadedInfo(boolean on) {
		cacheLoadedInfo = on;
	}

	void setNeedChunkOnly(boolean on) {
		needChunkOnly = on;
	}

	void init(Iterable<T> objectIds) {
		toFind = lookInCache(objectIds).iterator();
	}

	private Iterable<T> lookInCache(Iterable<T> objects) {
		RecentInfoCache infoCache = reader.getRecentInfoCache();
		List<T> missing = null;
		for (T obj : objects) {
			if (needChunkOnly && obj instanceof RefDataUtil.IdWithChunk) {
				push(obj, ((RefDataUtil.IdWithChunk) obj).getChunkKey());
				continue;
			}

			List<ObjectInfo> info = infoCache.get(obj);
			if (info != null && !info.isEmpty()) {
				push(obj, info.get(0));
			} else {
				if (missing == null) {
					if (objects instanceof List<?>)
						missing = new ArrayList<T>(((List<?>) objects).size());
					else
						missing = new ArrayList<T>();
				}
				missing.add(obj);
			}
		}
		if (missing != null)
			return missing;
		return Collections.emptyList();
	}

	synchronized ObjectWithInfo<T> nextObjectWithInfo()
			throws MissingObjectException, IOException {
		for (;;) {
			if (error != null)
				throw error;

			// Consider starting another batch before popping a result.
			// This ensures lookup is running while results are being
			// consumed by the calling application.
			//
			while (runningBatches < concurrentBatches) {
				if (!toFind.hasNext() // reached end of original input
						&& runningBatches == 0 // all batches finished
						&& toRetry != null // haven't yet retried
						&& !toRetry.isEmpty()) {
					toFind = toRetry.iterator();
					toRetry = null;
					context = Context.READ_REPAIR;
				}

				if (toFind.hasNext())
					startBatch(context);
				else
					break;
			}

			ObjectWithInfo<T> c = pop();
			if (c != null) {
				if (c.chunkKey != null)
					return c;
				else
					throw missing(c.object);

			} else if (!toFind.hasNext() && runningBatches == 0)
				return null;

			try {
				wait();
			} catch (InterruptedException e) {
				throw new DhtTimeoutException(e);
			}
		}
	}

	private synchronized void startBatch(final Context ctx) {
		final int batchSize = options.getObjectIndexBatchSize();
		final Map<ObjectIndexKey, T> batch = new HashMap<ObjectIndexKey, T>();
		while (toFind.hasNext() && batch.size() < batchSize) {
			T obj = toFind.next();
			batch.put(ObjectIndexKey.create(repo, obj), obj);
		}

		final AsyncCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>> cb;

		cb = new AsyncCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>>() {
			public void onSuccess(Map<ObjectIndexKey, Collection<ObjectInfo>> r) {
				processResults(ctx, batch, r);
			}

			public void onFailure(DhtException e) {
				processFailure(e);
			}
		};
		db.objectIndex().get(ctx, batch.keySet(), cb);
		runningBatches++;
	}

	private synchronized void processResults(Context ctx,
			Map<ObjectIndexKey, T> batch,
			Map<ObjectIndexKey, Collection<ObjectInfo>> objects) {
		for (T obj : batch.values()) {
			Collection<ObjectInfo> matches = objects.get(obj);

			if (matches == null || matches.isEmpty()) {
				if (ctx == Context.FAST_MISSING_OK)
					toRetry.add(obj);
				else if (reportMissing)
					push(obj, (ChunkKey) null);
				continue;
			}

			tmp.clear();
			tmp.addAll(matches);
			ObjectInfo.sort(tmp);
			if (cacheLoadedInfo)
				reader.getRecentInfoCache().put(obj, tmp);

			push(obj, tmp.get(0));
		}

		runningBatches--;
		notify();
	}

	private synchronized void processFailure(DhtException e) {
		runningBatches--;
		error = e;
		notify();
	}

	private void push(T obj, ChunkKey chunkKey) {
		nextResult = new ObjectWithInfo<T>(obj, chunkKey, nextResult);
	}

	private void push(T obj, ObjectInfo info) {
		nextResult = new ObjectWithInfo<T>(obj, info, nextResult);
	}

	private ObjectWithInfo<T> pop() {
		ObjectWithInfo<T> r = nextResult;
		if (r == null)
			return null;
		nextResult = r.next;
		return r;
	}

	public boolean cancel(boolean mayInterruptIfRunning) {
		return true;
	}

	public void release() {
		// Do nothing, there is nothing to abort or discard.
	}

	private static <T extends ObjectId> MissingObjectException missing(T id) {
		return new MissingObjectException(id, DhtText.get().objectTypeUnknown);
	}

	static class ObjectWithInfo<T extends ObjectId> {
		final T object;

		final ObjectInfo info;

		final ChunkKey chunkKey;

		final ObjectWithInfo<T> next;

		ObjectWithInfo(T object, ObjectInfo info, ObjectWithInfo<T> next) {
			this.object = object;
			this.info = info;
			this.chunkKey = info.getChunkKey();
			this.next = next;
		}

		ObjectWithInfo(T object, ChunkKey chunkKey, ObjectWithInfo<T> next) {
			this.object = object;
			this.info = null;
			this.chunkKey = chunkKey;
			this.next = next;
		}
	}
}
