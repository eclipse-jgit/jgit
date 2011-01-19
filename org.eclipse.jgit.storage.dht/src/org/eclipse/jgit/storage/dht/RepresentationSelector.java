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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.ThreadSafeProgressMonitor;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.Database;
import org.eclipse.jgit.storage.pack.PackWriter;

final class RepresentationSelector {
	private final PackWriter packer;

	private final RepositoryKey repo;

	private final Database db;

	private final DhtReader reader;

	private final ThreadSafeProgressMonitor progress;

	private final Semaphore batches;

	private final ReentrantLock selectLock;

	private final AtomicReference<DhtException> error;

	private final int concurrentBatches;

	private final List<DhtObjectToPack> retry;

	RepresentationSelector(PackWriter packer, RepositoryKey repo, Database db,
			ProgressMonitor monitor, DhtReader reader) {
		this.packer = packer;
		this.repo = repo;
		this.db = db;
		this.reader = reader;
		this.progress = new ThreadSafeProgressMonitor(monitor);

		this.concurrentBatches = reader.getOptions()
				.getSelectObjectRepresentationConcurrentBatches();

		this.batches = new Semaphore(concurrentBatches);
		this.selectLock = new ReentrantLock(true /* fair */);
		this.error = new AtomicReference<DhtException>();
		this.retry = new ArrayList<DhtObjectToPack>();
	}

	void select(Iterable<DhtObjectToPack> objects) throws IOException {
		selectInBatches(Context.FAST_MISSING_OK, objects);

		// Not all of the selection ran with fast options.
		if (!retry.isEmpty()) {
			batches.release(concurrentBatches);
			selectInBatches(Context.READ_REPAIR, retry);
		}

		progress.pollForUpdates();
	}

	private void selectInBatches(Context options,
			Iterable<DhtObjectToPack> objects) throws DhtException {
		final int batchSize = reader.getOptions()
				.getSelectObjectRepresentationBatchSize();

		Map<ObjectIndexKey, DhtObjectToPack> batch = new HashMap<ObjectIndexKey, DhtObjectToPack>();
		Iterator<DhtObjectToPack> otpItr = objects.iterator();
		while (otpItr.hasNext()) {
			DhtObjectToPack otp = otpItr.next();

			batch.put(ObjectIndexKey.create(repo, otp), otp);

			if (batch.size() < batchSize && otpItr.hasNext())
				continue;

			if (error.get() != null)
				break;

			try {
				while (!batches.tryAcquire(500, MILLISECONDS))
					progress.pollForUpdates();
				progress.pollForUpdates();
			} catch (InterruptedException err) {
				error.compareAndSet(null, new DhtTimeoutException(err));
				break;
			}

			startFindQuery(options, batch);
			batch = new HashMap<ObjectIndexKey, DhtObjectToPack>();
		}
		try {
			while (!batches.tryAcquire(concurrentBatches, 500, MILLISECONDS))
				progress.pollForUpdates();
			progress.pollForUpdates();
		} catch (InterruptedException err) {
			error.compareAndSet(null, new DhtTimeoutException(err));
		}

		if (error.get() != null)
			throw error.get();

		// Make sure retry changes are visible to us.
		selectLock.lock();
		selectLock.unlock();
	}

	private void startFindQuery(final Context options,
			final Map<ObjectIndexKey, DhtObjectToPack> batch) {
		final AsyncCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>> cb = new AsyncCallback<Map<ObjectIndexKey, Collection<ObjectInfo>>>() {
			public void onSuccess(Map<ObjectIndexKey, Collection<ObjectInfo>> r) {
				selectLock.lock();
				try {
					processFindResults(options, batch, r);
				} finally {
					selectLock.unlock();
					batches.release();
				}
			}

			public void onFailure(DhtException e) {
				error.compareAndSet(null, e);
				batches.release();
			}
		};
		db.objectIndex().get(options, batch.keySet(), cb);
	}

	private void processFindResults(Context options,
			Map<ObjectIndexKey, DhtObjectToPack> batch,
			Map<ObjectIndexKey, Collection<ObjectInfo>> chunks) {
		DhtObjectRepresentation rep = new DhtObjectRepresentation();
		ArrayList<ObjectInfo> tmp = new ArrayList<ObjectInfo>();

		for (Map.Entry<ObjectIndexKey, Collection<ObjectInfo>> entry : all(chunks)) {
			tmp.clear();
			tmp.addAll(entry.getValue());
			if (!tmp.isEmpty()) {
				DhtObjectToPack obj = batch.remove(entry.getKey());

				// Only use the oldest (aka first) representation, as
				// that is the only known safe copy.
				//
				ObjectInfo.sort(tmp);
				rep.set(tmp.get(0));
				packer.select(obj, rep);
			}
			progress.update(1);
		}

		if (options == Context.FAST_MISSING_OK)
			retry.addAll(batch.values());
	}

	private static Set<Map.Entry<ObjectIndexKey, Collection<ObjectInfo>>> all(
			Map<ObjectIndexKey, Collection<ObjectInfo>> chunks) {
		return chunks.entrySet();
	}
}
