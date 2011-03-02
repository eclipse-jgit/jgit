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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.ThreadSafeProgressMonitor;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.Database;

abstract class BatchObjectLookup<T extends ObjectId> {
	private final RepositoryKey repo;

	private final Database db;

	private final DhtReader reader;

	private final ThreadSafeProgressMonitor progress;

	private final Semaphore batches;

	private final ReentrantLock resultLock;

	private final AtomicReference<DhtException> error;

	private final int concurrentBatches;

	private final List<T> retry;

	private final ArrayList<ObjectInfo> tmp;

	private boolean retryMissingObjects;

	private boolean cacheLoadedInfo;

	BatchObjectLookup(DhtReader reader) {
		this(reader, null);
	}

	BatchObjectLookup(DhtReader reader, ProgressMonitor monitor) {
		this.repo = reader.getRepositoryKey();
		this.db = reader.getDatabase();
		this.reader = reader;

		if (monitor != null && monitor != NullProgressMonitor.INSTANCE)
			this.progress = new ThreadSafeProgressMonitor(monitor);
		else
			this.progress = null;

		this.concurrentBatches = reader.getOptions()
				.getObjectIndexConcurrentBatches();

		this.batches = new Semaphore(concurrentBatches);
		this.resultLock = new ReentrantLock();
		this.error = new AtomicReference<DhtException>();
		this.retry = new ArrayList<T>();
		this.tmp = new ArrayList<ObjectInfo>(4);
	}

	void setRetryMissingObjects(boolean on) {
		retryMissingObjects = on;
	}

	void setCacheLoadedInfo(boolean on) {
		cacheLoadedInfo = on;
	}

	void select(Iterable<T> objects) throws IOException {
		selectInBatches(Context.FAST_MISSING_OK, lookInCache(objects));

		// Not all of the selection ran with fast options.
		if (retryMissingObjects && !retry.isEmpty()) {
			batches.release(concurrentBatches);
			selectInBatches(Context.READ_REPAIR, retry);
		}

		if (progress != null)
			progress.pollForUpdates();
	}

	private Iterable<T> lookInCache(Iterable<T> objects) {
		RecentInfoCache infoCache = reader.getRecentInfoCache();
		List<T> missing = null;
		for (T obj : objects) {
			List<ObjectInfo> info = infoCache.get(obj);
			if (info != null) {
				onResult(obj, info);
				if (progress != null)
					progress.update(1);
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

	private void selectInBatches(Context options, Iterable<T> objects)
			throws DhtException {
		final int batchSize = reader.getOptions()
				.getObjectIndexBatchSize();

		Map<ObjectIndexKey, T> batch = new HashMap<ObjectIndexKey, T>();
		Iterator<T> otpItr = objects.iterator();
		while (otpItr.hasNext()) {
			T otp = otpItr.next();

			batch.put(ObjectIndexKey.create(repo, otp), otp);

			if (batch.size() < batchSize && otpItr.hasNext())
				continue;

			if (error.get() != null)
				break;

			try {
				if (progress != null) {
					while (!batches.tryAcquire(500, MILLISECONDS))
						progress.pollForUpdates();
					progress.pollForUpdates();
				} else {
					batches.acquire();
				}
			} catch (InterruptedException err) {
				error.compareAndSet(null, new DhtTimeoutException(err));
				break;
			}

			startQuery(options, batch);
			batch = new HashMap<ObjectIndexKey, T>();
		}

		try {
			if (progress != null) {
				while (!batches.tryAcquire(concurrentBatches, 500, MILLISECONDS))
					progress.pollForUpdates();
				progress.pollForUpdates();
			} else {
				batches.acquire(concurrentBatches);
			}
		} catch (InterruptedException err) {
			error.compareAndSet(null, new DhtTimeoutException(err));
		}

		if (error.get() != null)
			throw error.get();

		// Make sure retry changes are visible to us.
		resultLock.lock();
		resultLock.unlock();
	}

	private void startQuery(final Context context,
			final Map<ObjectIndexKey, T> batch) {
		final AsyncCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>> cb;

		cb = new AsyncCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>>() {
			public void onSuccess(Map<ObjectIndexKey, Collection<ObjectInfo>> r) {
				resultLock.lock();
				try {
					processResults(context, batch, r);
				} finally {
					resultLock.unlock();
					batches.release();
				}
			}

			public void onFailure(DhtException e) {
				error.compareAndSet(null, e);
				batches.release();
			}
		};
		db.objectIndex().get(context, batch.keySet(), cb);
	}

	private void processResults(Context context, Map<ObjectIndexKey, T> batch,
			Map<ObjectIndexKey, Collection<ObjectInfo>> objects) {
		for (T obj : batch.values()) {
			Collection<ObjectInfo> matches = objects.get(obj);

			if (matches == null || matches.isEmpty()) {
				if (retryMissingObjects && context == Context.FAST_MISSING_OK)
					retry.add(obj);
				continue;
			}

			tmp.clear();
			tmp.addAll(matches);
			ObjectInfo.sort(tmp);
			if (cacheLoadedInfo)
				reader.getRecentInfoCache().put(obj, tmp);

			onResult(obj, tmp);
		}

		if (progress != null)
			progress.update(objects.size());
	}

	protected abstract void onResult(T obj, List<ObjectInfo> info);
}
