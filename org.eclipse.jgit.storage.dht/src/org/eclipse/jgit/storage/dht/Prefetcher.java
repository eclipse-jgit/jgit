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

import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;
import static org.eclipse.jgit.lib.Constants.OBJ_TREE;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.storage.dht.DhtReader.ChunkAndOffset;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.Database;

class Prefetcher implements StreamingCallback<Collection<PackChunk.Members>> {
	private static enum Status {
		ON_QUEUE, LOADING, WAITING, READY, DONE;
	}

	private final Database db;

	private final int objectType;

	private final HashMap<ChunkKey, PackChunk> ready;

	private final HashMap<ChunkKey, Status> status;

	private final LinkedList<ChunkKey> queue;

	private final int highWaterMark;

	private final int lowWaterMark;

	private boolean includeFragments;

	private boolean first = true;

	private int cntReady;

	private int cntLoading;

	private DhtException error;

	Prefetcher(Database db, DhtReaderOptions options, int objectType) {
		this.db = db;
		this.objectType = objectType;
		this.ready = new HashMap<ChunkKey, PackChunk>();
		this.status = new HashMap<ChunkKey, Status>();
		this.queue = new LinkedList<ChunkKey>();
		this.highWaterMark = options.getPrefetchDepth();

		int lwm = highWaterMark - 4;
		if (lwm <= 0)
			lwm = highWaterMark / 2;
		lowWaterMark = lwm;
	}

	boolean isType(int type) {
		return objectType == type;
	}

	synchronized void setIncludeFragments(boolean includeFragments) {
		this.includeFragments = includeFragments;
	}

	synchronized void cancel() {
		ready.clear();
		status.clear();
		queue.clear();

		cntReady = 0;
		cntLoading = 0;
		error = null;
	}

	void push(DhtReader ctx, Collection<RevCommit> roots) throws DhtException,
			MissingObjectException {
		// Approximate walk by using hints from the most recent commit.

		int time = -1;
		PackChunk chunk = null;

		for (RevCommit cmit : roots) {
			if (time < cmit.getCommitTime()) {
				ChunkAndOffset p = ctx.getChunkGently(cmit, cmit.getType());
				if (p == null || p.chunk.getPrefetch() == null)
					continue;
				time = cmit.getCommitTime();
				chunk = p.chunk;
			}
		}

		if (chunk != null) {
			synchronized (this) {
				status.put(chunk.getChunkKey(), Status.DONE);
				push(chunk.getPrefetch());
			}
		}
	}

	void push(DhtReader ctx, RevObject start) throws DhtException,
			MissingObjectException {

		ChunkAndOffset p = ctx.getChunkGently(start, start.getType());
		if (p == null || p.chunk.getPrefetch() == null)
			return;

		synchronized (this) {
			status.put(p.chunk.getChunkKey(), Status.DONE);
			push(p.chunk.getPrefetch());
		}
	}

	void push(ChunkKey key) {
		push(Collections.singleton(key));
	}

	void push(ChunkPrefetch prefetch) {
		if (prefetch == null)
			return;

		ChunkPrefetch.Hint hint;
		switch (objectType) {
		case OBJ_COMMIT:
			hint = prefetch.getCommits();
			break;
		case OBJ_TREE:
			hint = prefetch.getTrees();
			break;
		default:
			return;
		}

		if (hint != null) {
			synchronized (this) {
				push(hint.getEdge());
				push(hint.getSequential());
			}
		}
	}

	void push(Iterable<ChunkKey> list) {
		synchronized (this) {
			for (ChunkKey key : list) {
				if (status.containsKey(key))
					continue;
				status.put(key, Status.ON_QUEUE);
				queue.add(key);
			}
			maybeStartGet();
		}
	}

	synchronized ChunkAndOffset find(RepositoryKey repo, AnyObjectId objId) {
		for (PackChunk c : ready.values()) {
			int p = c.findOffset(repo, objId);
			if (0 <= p)
				return new ChunkAndOffset(useReadyChunk(c.getChunkKey()), p);
		}
		return null;
	}

	synchronized PackChunk get(ChunkKey key) throws DhtException {
		GET: for (;;) {
			if (error != null)
				throw error;

			Status chunkStatus = status.get(key);
			if (chunkStatus == null)
				return null;

			switch (chunkStatus) {
			case ON_QUEUE: // Don't prefetch out-of-order requests.
				queue.remove(key);
				//$FALL-THROUGH$
			case DONE:
				status.put(key, Status.DONE);
				return null;

			case LOADING: // Wait for a prefetch that is already started.
				status.put(key, Status.WAITING);
				//$FALL-THROUGH$
			case WAITING:
				try {
					wait();
				} catch (InterruptedException e) {
					throw new DhtTimeoutException(e);
				}
				continue GET;

			case READY:
				return useReadyChunk(key);

			default:
				throw new IllegalStateException(key + " " + chunkStatus);
			}
		}
	}

	private PackChunk useReadyChunk(ChunkKey key) {
		PackChunk chunk = ChunkCache.get().put(ready.remove(key));
		status.put(chunk.getChunkKey(), Status.DONE);
		cntReady--;
		push(chunk.getPrefetch());
		maybeStartGet();
		return chunk;
	}

	private void maybeStartGet() {
		if (queue.isEmpty())
			return;

		if (lowWaterMark < cntReady + cntLoading)
			return;

		ChunkCache cache = ChunkCache.get();
		HashSet<ChunkKey> toLoad = new HashSet<ChunkKey>();
		while (cntReady + cntLoading < highWaterMark && !queue.isEmpty()) {
			ChunkKey key = queue.removeFirst();
			PackChunk chunk = cache.get(key);

			if (chunk != null) {
				chunkIsReady(chunk);
			} else {
				toLoad.add(key);
				status.put(key, Status.LOADING);
				cntLoading++;

				// For the first chunk, start immediately to reduce the
				// startup latency associated with additional chunks.
				if (first)
					break;
			}
		}

		if (!toLoad.isEmpty() && error == null)
			db.chunk().get(Context.LOCAL, toLoad, this);

		if (first) {
			first = false;
			maybeStartGet();
		}
	}

	public synchronized void onPartialResult(Collection<PackChunk.Members> res) {
		try {
			cntLoading -= res.size();
			for (PackChunk.Members builder : res)
				chunkIsReady(builder.build());
		} catch (DhtException loadError) {
			onError(loadError);
		}
	}

	private void chunkIsReady(PackChunk chunk) {
		ChunkKey key = chunk.getChunkKey();
		ready.put(key, chunk);
		cntReady++;

		if (status.put(key, Status.READY) == Status.WAITING)
			notifyAll();

		// If the chunk is fragmented, push the fragments onto
		// the front of the queue so they are fetched in order.
		//
		ChunkFragments fragments = chunk.getFragments();
		if (includeFragments && fragments != null) {
			for (int i = fragments.size() - 1; 0 <= i; i--) {
				key = fragments.getChunkKey(i);
				if (status.containsKey(key))
					continue;
				status.put(key, Status.ON_QUEUE);
				queue.addFirst(key);
			}
		}
	}

	public synchronized void onSuccess(Collection<PackChunk.Members> result) {
		if (result != null && !result.isEmpty())
			onPartialResult(result);
		maybeStartGet();
	}

	public synchronized void onFailure(DhtException asyncError) {
		onError(asyncError);
	}

	private void onError(DhtException asyncError) {
		if (error == null) {
			error = asyncError;
			notifyAll();
		}
	}
}
