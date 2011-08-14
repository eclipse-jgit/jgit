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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.generated.storage.dht.proto.GitStore.ChunkMeta;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.storage.dht.DhtReader.ChunkAndOffset;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.Database;

class Prefetcher implements StreamingCallback<Collection<PackChunk.Members>> {
	private static enum Status {
		ON_QUEUE, LOADING, WAITING, READY, DONE;
	}

	private final Database db;

	private final DhtReader.Statistics stats;

	private final int objectType;

	private final HashMap<ChunkKey, PackChunk> ready;

	private final HashMap<ChunkKey, Status> status;

	private final LinkedList<ChunkKey> queue;

	private final boolean followEdgeHints;

	private final int averageChunkSize;

	private final int highWaterMark;

	private final int lowWaterMark;

	private boolean first = true;

	private boolean automaticallyPushHints = true;

	private ChunkKey stopAt;

	private int bytesReady;

	private int bytesLoading;

	private DhtException error;

	Prefetcher(DhtReader reader, int objectType, int prefetchLimitInBytes) {
		this.db = reader.getDatabase();
		this.stats = reader.getStatistics();
		this.objectType = objectType;
		this.ready = new HashMap<ChunkKey, PackChunk>();
		this.status = new HashMap<ChunkKey, Status>();
		this.queue = new LinkedList<ChunkKey>();
		this.followEdgeHints = reader.getOptions().isPrefetchFollowEdgeHints();
		this.averageChunkSize = reader.getInserterOptions().getChunkSize();
		this.highWaterMark = prefetchLimitInBytes;

		int lwm = (highWaterMark / averageChunkSize) - 4;
		if (lwm <= 0)
			lwm = (highWaterMark / averageChunkSize) / 2;
		lowWaterMark = lwm * averageChunkSize;
	}

	boolean isType(int type) {
		return objectType == type;
	}

	void push(DhtReader ctx, Collection<RevCommit> roots) {
		// Approximate walk by using hints from the most recent commit.
		// Since the commits were recently parsed by the reader, we can
		// ask the reader for their chunk locations and most likely get
		// cache hits.

		int time = -1;
		PackChunk chunk = null;

		for (RevCommit cmit : roots) {
			if (time < cmit.getCommitTime()) {
				ChunkAndOffset p = ctx.getChunkGently(cmit);
				if (p != null && p.chunk.getMeta() != null) {
					time = cmit.getCommitTime();
					chunk = p.chunk;
				}
			}
		}

		if (chunk != null) {
			synchronized (this) {
				status.put(chunk.getChunkKey(), Status.DONE);
				push(chunk.getMeta());
			}
		}
	}

	void push(DhtReader ctx, RevTree start, RevTree end) throws DhtException,
			MissingObjectException {
		// Unlike commits, trees aren't likely to be loaded when they
		// are pushed into the prefetcher. Find the tree and load it
		// as necessary to get the prefetch meta established.
		//
		Sync<Map<ObjectIndexKey, Collection<ObjectInfo>>> sync = Sync.create();
		Set<ObjectIndexKey> toFind = new HashSet<ObjectIndexKey>();
		toFind.add(ObjectIndexKey.create(ctx.getRepositoryKey(), start));
		toFind.add(ObjectIndexKey.create(ctx.getRepositoryKey(), end));
		db.objectIndex().get(Context.READ_REPAIR, toFind, sync);

		Map<ObjectIndexKey, Collection<ObjectInfo>> trees;
		try {
			trees = sync.get(ctx.getOptions().getTimeout());
		} catch (InterruptedException e) {
			throw new DhtTimeoutException(e);
		} catch (TimeoutException e) {
			throw new DhtTimeoutException(e);
		}

		ChunkKey startKey = chunk(trees.get(start));
		if (startKey == null)
			throw DhtReader.missing(start, OBJ_TREE);

		ChunkKey endKey = chunk(trees.get(end));
		if (endKey == null)
			throw DhtReader.missing(end, OBJ_TREE);

		synchronized (this) {
			stopAt = endKey;
			push(startKey);
			maybeStartGet();
		}
	}

	private static ChunkKey chunk(Collection<ObjectInfo> info) {
		if (info == null || info.isEmpty())
			return null;

		List<ObjectInfo> infoList = new ArrayList<ObjectInfo>(info);
		ObjectInfo.sort(infoList);
		return infoList.get(0).getChunkKey();
	}

	void push(ChunkKey key) {
		push(Collections.singleton(key));
	}

	void push(ChunkMeta meta) {
		if (meta == null)
			return;

		ChunkMeta.PrefetchHint hint;
		switch (objectType) {
		case OBJ_COMMIT:
			hint = meta.getCommitPrefetch();
			break;
		case OBJ_TREE:
			hint = meta.getTreePrefetch();
			break;
		default:
			return;
		}

		if (hint != null) {
			synchronized (this) {
				if (followEdgeHints && 0 < hint.getEdgeCount())
					push(hint.getEdgeList());
				else
					push(hint.getSequentialList());
			}
		}
	}

	private void push(List<String> list) {
		List<ChunkKey> keys = new ArrayList<ChunkKey>(list.size());
		for (String keyString : list)
			keys.add(ChunkKey.fromString(keyString));
		push(keys);
	}

	void push(Iterable<ChunkKey> list) {
		synchronized (this) {
			for (ChunkKey key : list) {
				if (status.containsKey(key))
					continue;

				status.put(key, Status.ON_QUEUE);
				queue.add(key);

				if (key.equals(stopAt)) {
					automaticallyPushHints = false;
					break;
				}
			}

			if (!first)
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
			case ON_QUEUE:
				if (queue.isEmpty()) {
					// Should never happen, but let the caller load.
					status.put(key, Status.DONE);
					return null;

				} else if (bytesReady + bytesLoading < highWaterMark) {
					// Make sure its first in the queue, start, and wait.
					if (!queue.getFirst().equals(key)) {
						int idx = queue.indexOf(key);
						if (first && objectType == OBJ_COMMIT) {
							// If the prefetcher has not started yet, skip all
							// chunks up to this first request. Assume this
							// initial out-of-order get occurred because the
							// RevWalk has already parsed all of the commits
							// up to this point and does not need them again.
							//
							for (; 0 < idx; idx--)
								status.put(queue.removeFirst(), Status.DONE);
							forceStartGet();
							continue GET;
						}

						stats.access(key).cntPrefetcher_OutOfOrder++;
						queue.remove(idx);
						queue.addFirst(key);
					}
					forceStartGet();
					continue GET;

				} else {
					// It cannot be moved up to the front of the queue
					// without violating the prefetch size. Let the
					// caller load the chunk out of order.
					stats.access(key).cntPrefetcher_OutOfOrder++;
					status.put(key, Status.DONE);
					return null;
				}

			case LOADING: // Wait for a prefetch that is already started.
				status.put(key, Status.WAITING);
				//$FALL-THROUGH$
			case WAITING:
				stats.access(key).cntPrefetcher_WaitedForLoad++;
				try {
					wait();
				} catch (InterruptedException e) {
					throw new DhtTimeoutException(e);
				}
				continue GET;

			case READY:
				return useReadyChunk(key);

			case DONE:
				stats.access(key).cntPrefetcher_Revisited++;
				return null;

			default:
				throw new IllegalStateException(key + " " + chunkStatus);
			}
		}
	}

	private PackChunk useReadyChunk(ChunkKey key) {
		PackChunk chunk = ready.remove(key);

		status.put(chunk.getChunkKey(), Status.DONE);
		bytesReady -= chunk.getTotalSize();

		if (automaticallyPushHints) {
			push(chunk.getMeta());
			maybeStartGet();
		}

		return chunk;
	}

	private void maybeStartGet() {
		if (!queue.isEmpty() && bytesReady + bytesLoading <= lowWaterMark)
			forceStartGet();
	}

	private void forceStartGet() {
		// Use a LinkedHashSet so insertion order is iteration order.
		// This may help a provider that loads sequentially in the
		// set's iterator order to load in the order we want data.
		//
		LinkedHashSet<ChunkKey> toLoad = new LinkedHashSet<ChunkKey>();

		while (bytesReady + bytesLoading < highWaterMark && !queue.isEmpty()) {
			ChunkKey key = queue.removeFirst();

			stats.access(key).cntPrefetcher_Load++;
			toLoad.add(key);
			status.put(key, Status.LOADING);
			bytesLoading += averageChunkSize;

			// For the first chunk, start immediately to reduce the
			// startup latency associated with additional chunks.
			if (first)
				break;
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
			bytesLoading -= averageChunkSize * res.size();
			for (PackChunk.Members builder : res)
				chunkIsReady(builder.build());
		} catch (DhtException loadError) {
			onError(loadError);
		}
	}

	private void chunkIsReady(PackChunk chunk) {
		ChunkKey key = chunk.getChunkKey();
		ready.put(key, chunk);
		bytesReady += chunk.getTotalSize();

		if (status.put(key, Status.READY) == Status.WAITING)
			notifyAll();
	}

	public synchronized void onSuccess(Collection<PackChunk.Members> result) {
		if (result != null && !result.isEmpty())
			onPartialResult(result);
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
