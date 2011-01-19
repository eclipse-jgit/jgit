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

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.revwalk.ObjectListIterator;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.storage.dht.spi.Context;
import org.eclipse.jgit.storage.dht.spi.Database;

final class DhtObjectListIterator extends ObjectListIterator implements
		StreamingCallback<Collection<ObjectListChunk>> {
	private final Database db;

	private final ObjectListInfo info;

	private final DhtReaderOptions options;

	private final MutableObjectId idBuf;

	private final HashMap<ObjectListChunkKey, ObjectListChunk> ready;

	private final ArrayList<ObjectListChunkKey> keys;

	private final ReentrantLock lock;

	private final Condition cond;

	private final int highWaterMark;

	private final int lowWaterMark;

	/** Next index in {@link #keys} to request from the database. */
	private int nextRequestIndex;

	/** Next index in {@link #keys} to consume and return to caller. */
	private int nextUseIndex;

	private int cntReady;

	private int cntLoading;

	private DhtException error;

	private int cType;

	private ObjectListChunk cChunk;

	private int cPtr;

	DhtObjectListIterator(Database db, ObjectListInfo info, ObjectWalk walker,
			DhtReaderOptions options) {
		super(walker);
		this.db = db;
		this.info = info;
		this.options = options;
		this.idBuf = new MutableObjectId();
		this.lock = new ReentrantLock();
		this.cond = lock.newCondition();

		this.highWaterMark = options.getPrefetchDepth();
		int lwm = highWaterMark - 4;
		if (lwm <= 0)
			lwm = highWaterMark / 2;
		lowWaterMark = lwm;

		ready = new HashMap<ObjectListChunkKey, ObjectListChunk>();
		keys = new ArrayList<ObjectListChunkKey>(info.chunkCount);
		addKeys(info.commits);
		addKeys(info.trees);
		addKeys(info.blobs);
		maybeStartGet();

		cType = OBJ_COMMIT;
		cChunk = null;
	}

	private void addKeys(ObjectListInfo.Segment segment) {
		int id = segment.chunkStart;
		for (int i = 0; i < segment.chunkCount; i++)
			keys.add(info.getChunkKey(id++));
	}

	@Override
	public RevCommit next() throws IOException {
		if (cChunk == null || cPtr == cChunk.size())
			nextListChunk();
		if (cChunk == null || cType != OBJ_COMMIT)
			return null;
		cChunk.getObjectId(idBuf, cPtr++);
		return (RevCommit) lookupAny(idBuf, cType);
	}

	@Override
	public RevObject nextObject() throws IOException {
		if (cChunk == null || cPtr == cChunk.size())
			nextListChunk();
		if (cChunk == null)
			return null;
		cChunk.getObjectId(idBuf, cPtr++);
		return lookupAny(idBuf, cType);
	}

	@Override
	public int getPathHashCode() {
		return cChunk.getPathHashCode(cPtr - 1);
	}

	@Override
	public void release() {
		// Nothing to clean up.
	}

	private void nextListChunk() throws DhtException {
		lock.lock();
		try {
			if (nextUseIndex == keys.size()) {
				cChunk = null;
				return;
			}

			ObjectListChunkKey key = keys.get(nextUseIndex++);
			while (error == null && !ready.containsKey(key)) {
				try {
					Timeout t = options.getObjectListChunkTimeout();
					if (!cond.await(t.getTime(), t.getUnit())) {
						throw new DhtTimeoutException(MessageFormat.format(
								DhtText.get().timeoutWaitingForObjectList, key));
					}
				} catch (InterruptedException e) {
					throw new DhtTimeoutException(e);
				}
			}
			if (error != null)
				throw error;

			cChunk = ready.remove(key);
			cPtr = 0;
			cType = key.getObjectType();
			cntReady--;

			maybeStartGet();
		} finally {
			lock.unlock();
		}
	}

	private void maybeStartGet() {
		if (nextRequestIndex == keys.size())
			return;

		if (lowWaterMark < cntReady + cntLoading)
			return;

		boolean first = nextRequestIndex == 0;
		HashSet<ObjectListChunkKey> toLoad = new HashSet<ObjectListChunkKey>();
		while (cntReady + cntLoading + toLoad.size() < highWaterMark
				&& nextRequestIndex < keys.size()) {
			toLoad.add(keys.get(nextRequestIndex++));

			// If this is the first chunk, break and start loading the
			// single entity to reduce latency.
			if (first)
				break;
		}
		cntLoading += toLoad.size();

		if (!toLoad.isEmpty() && error == null)
			db.objectList().get(Context.LOCAL, toLoad, this);

		if (first)
			maybeStartGet();
	}

	public void onPartialResult(Collection<ObjectListChunk> res) {
		lock.lock();
		try {
			for (ObjectListChunk listChunk : res)
				ready.put(listChunk.getRowKey(), listChunk);
			cntReady += res.size();
			cntLoading -= res.size();
			cond.signal();
		} finally {
			lock.unlock();
		}
	}

	public void onSuccess(Collection<ObjectListChunk> result) {
		if (result != null && !result.isEmpty())
			onPartialResult(result);
	}

	public void onFailure(DhtException asyncError) {
		lock.lock();
		try {
			if (error == null)
				error = asyncError;
			cond.signal();
		} finally {
			lock.unlock();
		}
	}
}
