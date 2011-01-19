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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.ThreadSafeProgressMonitor;
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
	}

	void select(Iterable<DhtObjectToPack> objects) throws IOException {
		final int batchSize = reader.getOptions()
				.getSelectObjectRepresentationBatchSize();

		Map<ObjectId, DhtObjectToPack> batch = new HashMap<ObjectId, DhtObjectToPack>();
		Iterator<DhtObjectToPack> otpItr = objects.iterator();
		while (otpItr.hasNext()) {
			DhtObjectToPack otp = otpItr.next();

			batch.put(otp, otp);

			if (batch.size() < batchSize && otpItr.hasNext())
				continue;

			if (error.get() != null)
				break;

			try {
				batches.acquire();
			} catch (InterruptedException err) {
				error.compareAndSet(null, new DhtException(err));
				break;
			}

			startFindQuery(batch);
			batch = new HashMap<ObjectId, DhtObjectToPack>();
		}

		try {
			batches.acquire(concurrentBatches);
		} catch (InterruptedException err) {
			error.compareAndSet(null, new DhtException(err));
		}

		if (error.get() != null)
			throw error.get();

		// Make sure changes made are visible to this thread.
		selectLock.lock();
		selectLock.unlock();
	}

	private void startFindQuery(final Map<ObjectId, DhtObjectToPack> batch) {
		HashSet<ObjectIndexKey> keys = new HashSet<ObjectIndexKey>();
		for (ObjectId o : batch.keySet())
			keys.add(ObjectIndexKey.create(repo, o));

		final AsyncCallback<Map<ObjectIndexKey, Collection<ChunkLink>>> cb = new AsyncCallback<Map<ObjectIndexKey, Collection<ChunkLink>>>() {
			public void onSuccess(Map<ObjectIndexKey, Collection<ChunkLink>> r) {
				selectLock.lock();
				try {
					processFindResults(batch, r);
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
		db.objectIndex().get(keys, cb);
	}

	private void processFindResults(Map<ObjectId, DhtObjectToPack> batch,
			Map<ObjectIndexKey, Collection<ChunkLink>> chunks) {
		DhtObjectRepresentation rep = new DhtObjectRepresentation();
		ArrayList<ChunkLink> tmp = new ArrayList<ChunkLink>();

		for (Map.Entry<ObjectIndexKey, Collection<ChunkLink>> entry : all(chunks)) {
			tmp.clear();
			tmp.addAll(entry.getValue());
			ChunkLink.sort(tmp);

			// TODO(spearce) Only use representations proven safe.
			// Normally DhtReader prefers only the oldest chunk (first
			// in the list after sorting). This avoids reading an evil object
			// pushed later whose content differs, but SHA-1 name is the
			// same. Consider doing this after-the-fact in push and setting
			// a flag in the ChunkLink when its verified.

			DhtObjectToPack obj = batch.get(entry.getKey());
			for (ChunkLink key : tmp) {
				rep.set(key);
				packer.select(obj, rep);
			}
			progress.update(1);
		}
	}

	private static Set<Entry<ObjectIndexKey, Collection<ChunkLink>>> all(
			Map<ObjectIndexKey, Collection<ChunkLink>> chunks) {
		return chunks.entrySet();
	}
}
