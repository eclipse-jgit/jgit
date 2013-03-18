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

package org.eclipse.jgit.internal.storage.dfs;

import java.lang.ref.SoftReference;

/**
 * Caches recently used objects for {@link DfsReader}.
 * <p>
 * This cache is not thread-safe. Each reader should have its own cache.
 */
final class DeltaBaseCache {
	private static final int TABLE_BITS = 10;

	private static final int MASK_BITS = 32 - TABLE_BITS;

	private static int hash(long position) {
		return (((int) position) << MASK_BITS) >>> MASK_BITS;
	}

	private int maxByteCount;

	private final Slot[] table;

	private Slot lruHead;

	private Slot lruTail;

	private int curByteCount;

	DeltaBaseCache(DfsReader reader) {
		DfsReaderOptions options = reader.getOptions();
		maxByteCount = options.getDeltaBaseCacheLimit();
		table = new Slot[1 << TABLE_BITS];
	}

	Entry get(DfsPackKey key, long position) {
		Slot e = table[hash(position)];
		for (; e != null; e = e.tableNext) {
			if (e.offset == position && key.equals(e.pack)) {
				Entry buf = e.data.get();
				if (buf != null) {
					moveToHead(e);
					return buf;
				}
			}
		}
		return null;
	}

	void put(DfsPackKey key, long offset, int objectType, byte[] data) {
		if (data.length > maxByteCount)
			return; // Too large to cache.

		curByteCount += data.length;
		releaseMemory();

		int tableIdx = hash(offset);
		Slot e = new Slot(key, offset, data.length);
		e.data = new SoftReference<Entry>(new Entry(data, objectType));
		e.tableNext = table[tableIdx];
		table[tableIdx] = e;
		moveToHead(e);
	}

	private void releaseMemory() {
		while (curByteCount > maxByteCount && lruTail != null) {
			Slot currOldest = lruTail;
			Slot nextOldest = currOldest.lruPrev;

			curByteCount -= currOldest.size;
			unlink(currOldest);
			removeFromTable(currOldest);

			if (nextOldest == null)
				lruHead = null;
			else
				nextOldest.lruNext = null;
			lruTail = nextOldest;
		}
	}

	private void removeFromTable(Slot e) {
		int tableIdx = hash(e.offset);
		Slot p = table[tableIdx];

		if (p == e) {
			table[tableIdx] = e.tableNext;
			return;
		}

		for (; p != null; p = p.tableNext) {
			if (p.tableNext == e) {
				p.tableNext = e.tableNext;
				return;
			}
		}
	}

	private void moveToHead(final Slot e) {
		unlink(e);
		e.lruPrev = null;
		e.lruNext = lruHead;
		if (lruHead != null)
			lruHead.lruPrev = e;
		else
			lruTail = e;
		lruHead = e;
	}

	private void unlink(final Slot e) {
		Slot prev = e.lruPrev;
		Slot next = e.lruNext;

		if (prev != null)
			prev.lruNext = next;
		if (next != null)
			next.lruPrev = prev;
	}

	static class Entry {
		final byte[] data;

		final int type;

		Entry(final byte[] aData, final int aType) {
			data = aData;
			type = aType;
		}
	}

	private static class Slot {
		final DfsPackKey pack;

		final long offset;

		final int size;

		Slot tableNext;

		Slot lruPrev;

		Slot lruNext;

		SoftReference<Entry> data;

		Slot(DfsPackKey key, long offset, int size) {
			this.pack = key;
			this.offset = offset;
			this.size = size;
		}
	}
}
