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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.storage.dht.DhtException;
import org.eclipse.jgit.storage.dht.Sync;
import org.eclipse.jgit.storage.dht.spi.WriteBuffer;
import org.eclipse.jgit.storage.dht.spi.cache.CacheService.Change;
import org.eclipse.jgit.storage.dht.spi.util.AbstractWriteBuffer;

/** WriteBuffer implementation for a {@link CacheDatabase}. */
public class CacheBuffer extends AbstractWriteBuffer {
	private final WriteBuffer dbBuffer;

	private final CacheService client;

	private final Sync<Void> none;

	private List<CacheService.Change> pending;

	private List<CacheService.Change> afterFlush;

	/**
	 * Initialize a new buffer.
	 *
	 * @param dbBuffer
	 *            the underlying database's own buffer.
	 * @param client
	 *            connection to the cache service.
	 * @param options
	 *            options controlling cache operations.
	 */
	public CacheBuffer(WriteBuffer dbBuffer, CacheService client,
			CacheOptions options) {
		super(null, options.getWriteBufferSize());
		this.dbBuffer = dbBuffer;
		this.client = client;
		this.none = Sync.none();
	}

	/**
	 * Schedule removal of a key from the cache.
	 * <p>
	 * Unlike {@link #removeAfterFlush(CacheKey)}, these removals can be flushed
	 * when the cache buffer is full, potentially before any corresponding
	 * removal is written to the underlying database.
	 *
	 * @param key
	 *            key to remove.
	 * @throws DhtException
	 *             a prior flush failed.
	 */
	public void remove(CacheKey key) throws DhtException {
		modify(CacheService.Change.remove(key));
	}

	/**
	 * Schedule a removal only after the underlying database flushes.
	 * <p>
	 * Unlike {@link #remove(CacheKey)}, these removals are buffered until the
	 * application calls {@link #flush()} and aren't sent to the cache service
	 * until after the underlying database flush() operation is completed
	 * successfully.
	 *
	 * @param key
	 *            key to remove.
	 */
	public void removeAfterFlush(CacheKey key) {
		if (afterFlush == null)
			afterFlush = newList();
		afterFlush.add(CacheService.Change.remove(key));
	}

	/**
	 * Schedule storing (or replacing) a key in the cache.
	 *
	 * @param key
	 *            key to store.
	 * @param value
	 *            new value to store.
	 * @throws DhtException
	 *             a prior flush failed.
	 */
	public void put(CacheKey key, byte[] value) throws DhtException {
		modify(CacheService.Change.put(key, value));
	}

	/**
	 * Schedule any cache change.
	 *
	 * @param op
	 *            the cache operation.
	 * @throws DhtException
	 *             a prior flush failed.
	 */
	public void modify(CacheService.Change op) throws DhtException {
		int sz = op.getKey().getBytes().length;
		if (op.getData() != null)
			sz += op.getData().length;
		if (add(sz)) {
			if (pending == null)
				pending = newList();
			pending.add(op);
			queued(sz);
		} else {
			client.modify(singleton(op), wrap(none, sz));
		}
	}

	/** @return the underlying database's own write buffer. */
	public WriteBuffer getWriteBuffer() {
		return dbBuffer;
	}

	@Override
	protected void startQueuedOperations(int bytes) throws DhtException {
		client.modify(pending, wrap(none, bytes));
		pending = null;
	}

	public void flush() throws DhtException {
		dbBuffer.flush();

		if (afterFlush != null) {
			for (CacheService.Change op : afterFlush)
				modify(op);
			afterFlush = null;
		}

		super.flush();
	}

	@Override
	public void abort() throws DhtException {
		pending = null;
		afterFlush = null;

		dbBuffer.abort();
		super.abort();
	}

	private static List<Change> newList() {
		return new ArrayList<CacheService.Change>();
	}
}
