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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AsyncObjectLoaderQueue;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.storage.dht.spi.Database;

/**
 * Locates objects in large batches, then opens them clustered by chunk.
 * <p>
 * To simplify the implementation this method does not consult the local
 * {@link ChunkCache} for objects. Instead it performs lookups for the
 * {@link ObjectInfo} in large batches, clusters those by ChunkKey, and loads
 * the chunks with a {@link Prefetcher}.
 * <p>
 * The lookup queue is completely spun out during the first invocation of
 * {@link #next()}, ensuring all chunks are known before any single chunk is
 * accessed. This is necessary to improve access locality and prevent thrashing
 * of the local ChunkCache. It also causes {@link MissingObjectException} to be
 * thrown at the start of traversal, until the lookup queue is exhausted.
 *
 * @param <T>
 *            type of object to associate with the loader.
 */
final class OpenQueue<T extends ObjectId> extends QueueObjectLookup<T>
		implements AsyncObjectLoaderQueue<T> {
	private final DhtReader reader;

	private Map<ChunkKey, Collection<ObjectWithInfo<T>>> byChunk;

	private Iterator<Collection<ObjectWithInfo<T>>> chunkItr;

	private Iterator<ObjectWithInfo<T>> objectItr;

	private Prefetcher prefetcher;

	private ObjectWithInfo<T> current;

	OpenQueue(RepositoryKey repo, Database db, DhtReader reader,
			Iterable<T> objectIds, boolean reportMissing) {
		super(repo, db, reader.getOptions(), objectIds, reportMissing);
		this.reader = reader;

		byChunk = new LinkedHashMap<ChunkKey, Collection<ObjectWithInfo<T>>>();
		objectItr = Collections.<ObjectWithInfo<T>> emptyList().iterator();
	}

	public boolean next() throws MissingObjectException, IOException {
		if (chunkItr == null)
			init();

		if (!objectItr.hasNext()) {
			if (!chunkItr.hasNext()) {
				release();
				return false;
			}
			objectItr = chunkItr.next().iterator();
		}

		current = objectItr.next();
		return true;
	}

	public T getCurrent() {
		return current.object;
	}

	public ObjectId getObjectId() {
		return getCurrent();
	}

	public ObjectLoader open() throws IOException {
		ObjectInfo info = current.info;
		ChunkKey chunkKey = info.getChunkKey();
		PackChunk chunk = prefetcher.get(chunkKey);
		if (chunk == null)
			chunk = reader.getChunk(chunkKey);

		ObjectLoader ldr = open(chunk, info);
		reader.recentChunk(chunk);
		return ldr;
	}

	private ObjectLoader open(PackChunk chunk, ObjectInfo info)
			throws IOException {
		return PackChunk.read(chunk, info.getOffset(), reader, info.getType());
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		release();
		return true;
	}

	@Override
	public void release() {
		if (prefetcher != null) {
			prefetcher.cancel();
			prefetcher = null;
		}
	}

	private void init() throws IOException {
		ObjectWithInfo<T> c;

		while ((c = nextObjectWithInfo()) != null) {
			ChunkKey chunkKey = c.info.getChunkKey();
			Collection<ObjectWithInfo<T>> list = byChunk.get(chunkKey);
			if (list == null) {
				list = new ArrayList<ObjectWithInfo<T>>();
				byChunk.put(chunkKey, list);

				if (prefetcher == null)
					prefetcher = new Prefetcher(db, reader.getOptions(), 0);
				prefetcher.push(chunkKey);
			}
			list.add(c);
		}

		chunkItr = byChunk.values().iterator();
	}
}
