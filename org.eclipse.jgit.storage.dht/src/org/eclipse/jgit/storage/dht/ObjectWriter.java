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

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.generated.storage.dht.proto.GitStore.ChunkMeta;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.util.BlockList;

/**
 * Re-orders objects destined for a pack stream by chunk locality.
 * <p>
 * By re-ordering objects according to chunk locality, and then the original
 * order the PackWriter intended to use, objects can be copied quickly from
 * chunks, and each chunk is visited at most once. A {@link Prefetcher} for the
 * {@link DhtReader} is used to fetch chunks in the order they will be used,
 * improving throughput by reducing the number of round-trips required to the
 * storage system.
 */
final class ObjectWriter {
	private final DhtReader ctx;

	private final Prefetcher prefetch;

	private final int batchSize;

	private final Semaphore metaBatches;

	private final AtomicReference<DhtException> metaError;

	private final LinkedHashMap<ChunkKey, Integer> allVisits;

	private final Map<ChunkKey, ChunkMeta> allMeta;

	private final Set<ChunkKey> metaMissing;

	private Set<ChunkKey> metaToRead;

	private int curVisit;

	ObjectWriter(DhtReader ctx, Prefetcher prefetch) {
		this.ctx = ctx;
		this.prefetch = prefetch;

		batchSize = ctx.getOptions().getObjectIndexBatchSize();
		metaBatches = new Semaphore(batchSize);
		metaError = new AtomicReference<DhtException>();

		allVisits = new LinkedHashMap<ChunkKey, Integer>();
		allMeta = new HashMap<ChunkKey, ChunkMeta>();
		metaMissing = new HashSet<ChunkKey>();
		metaToRead = new HashSet<ChunkKey>();
		curVisit = 1;
	}

	void plan(List<DhtObjectToPack> list) throws DhtException {
		try {
			for (DhtObjectToPack obj : list)
				visit(obj);

			if (!metaToRead.isEmpty())
				startBatch(Context.FAST_MISSING_OK);
			awaitPendingBatches();

			synchronized (metaMissing) {
				if (!metaMissing.isEmpty()) {
					metaBatches.release(batchSize);
					resolveMissing();
					awaitPendingBatches();
				}
			}
		} catch (InterruptedException err) {
			throw new DhtTimeoutException(err);
		}

		Iterable<ChunkKey> order;
		synchronized (allMeta) {
			if (allMeta.isEmpty()) {
				order = allVisits.keySet();
			} else {
				BlockList<ChunkKey> keys = new BlockList<ChunkKey>();
				for (ChunkKey key : allVisits.keySet()) {
					keys.add(key);

					ChunkMeta meta = allMeta.remove(key);
					if (meta != null) {
						for (int i = 1; i < meta.getFragmentCount(); i++)
							keys.add(ChunkKey.fromString(meta.getFragment(i)));
					}
				}
				order = keys;
			}
		}
		prefetch.push(order);

		Collections.sort(list, new Comparator<DhtObjectToPack>() {
			public int compare(DhtObjectToPack a, DhtObjectToPack b) {
				return a.visitOrder - b.visitOrder;
			}
		});
	}

	private void visit(DhtObjectToPack obj) throws InterruptedException,
			DhtTimeoutException {
		// Plan the visit to the delta base before the object. This
		// ensures the base is in the stream first, and OFS_DELTA can
		// be used for the delta.
		//
		DhtObjectToPack base = (DhtObjectToPack) obj.getDeltaBase();
		if (base != null && base.visitOrder == 0) {
			// Use the current visit, even if its wrong. This will
			// prevent infinite recursion when there is a cycle in the
			// delta chain. Cycles are broken during writing, not in
			// the earlier planning phases.
			//
			obj.visitOrder = curVisit;
			visit(base);
		}

		ChunkKey key = obj.chunk;
		if (key != null) {
			Integer i = allVisits.get(key);
			if (i == null) {
				i = Integer.valueOf(1 + allVisits.size());
				allVisits.put(key, i);
			}
			curVisit = i.intValue();
		}

		if (obj.isFragmented()) {
			metaToRead.add(key);
			if (metaToRead.size() == batchSize)
				startBatch(Context.FAST_MISSING_OK);
		}
		obj.visitOrder = curVisit;
	}

	private void resolveMissing() throws DhtTimeoutException,
			InterruptedException {
		metaToRead = new HashSet<ChunkKey>();
		for (ChunkKey key : metaMissing) {
			metaToRead.add(key);
			if (metaToRead.size() == batchSize)
				startBatch(Context.LOCAL);
		}
		if (!metaToRead.isEmpty())
			startBatch(Context.LOCAL);
	}

	private void startBatch(Context context) throws InterruptedException,
			DhtTimeoutException {
		Timeout to = ctx.getOptions().getTimeout();
		if (!metaBatches.tryAcquire(1, to.getTime(), to.getUnit()))
			throw new DhtTimeoutException(DhtText.get().timeoutChunkMeta);

		Set<ChunkKey> keys = metaToRead;
		ctx.getDatabase().chunk().getMeta(
				context,
				keys,
				new MetaLoader(context, keys));
		metaToRead = new HashSet<ChunkKey>();
	}

	private void awaitPendingBatches() throws InterruptedException,
			DhtTimeoutException, DhtException {
		Timeout to = ctx.getOptions().getTimeout();
		if (!metaBatches.tryAcquire(batchSize, to.getTime(), to.getUnit()))
			throw new DhtTimeoutException(DhtText.get().timeoutChunkMeta);
		if (metaError.get() != null)
			throw metaError.get();
	}

	private class MetaLoader implements AsyncCallback<Map<ChunkKey, ChunkMeta>> {
		private final Context context;

		private final Set<ChunkKey> keys;

		MetaLoader(Context context, Set<ChunkKey> keys) {
			this.context = context;
			this.keys = keys;
		}

		public void onSuccess(Map<ChunkKey, ChunkMeta> result) {
			try {
				synchronized (allMeta) {
					allMeta.putAll(result);
					keys.removeAll(result.keySet());
				}
				if (context == Context.FAST_MISSING_OK && !keys.isEmpty()) {
					synchronized (metaMissing) {
						metaMissing.addAll(keys);
					}
				}
			} finally {
				metaBatches.release(1);
			}
		}

		public void onFailure(DhtException error) {
			metaError.compareAndSet(null, error);
			metaBatches.release(1);
		}
	}
}
