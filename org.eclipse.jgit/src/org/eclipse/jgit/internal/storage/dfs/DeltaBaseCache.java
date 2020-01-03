/*
 * Copyright (C) 2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;


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
	private int curByteCount;

	private final Entry[] table;

	private Entry lruHead;
	private Entry lruTail;

	DeltaBaseCache(DfsReader reader) {
		this(reader.getOptions().getDeltaBaseCacheLimit());
	}

	DeltaBaseCache(int maxBytes) {
		maxByteCount = maxBytes;
		table = new Entry[1 << TABLE_BITS];
	}

	Entry get(DfsStreamKey key, long position) {
		Entry e = table[hash(position)];
		for (; e != null; e = e.tableNext) {
			if (e.offset == position && key.equals(e.pack)) {
				moveToHead(e);
				return e;
			}
		}
		return null;
	}

	void put(DfsStreamKey key, long offset, int objectType, byte[] data) {
		if (data.length > maxByteCount)
			return; // Too large to cache.

		curByteCount += data.length;
		releaseMemory();

		int tableIdx = hash(offset);
		Entry e = new Entry(key, offset, objectType, data);
		e.tableNext = table[tableIdx];
		table[tableIdx] = e;
		lruPushHead(e);
	}

	private void releaseMemory() {
		while (curByteCount > maxByteCount && lruTail != null) {
			Entry e = lruTail;
			curByteCount -= e.data.length;
			lruRemove(e);
			removeFromTable(e);
		}
	}

	private void removeFromTable(Entry e) {
		int tableIdx = hash(e.offset);
		Entry p = table[tableIdx];

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

		throw new IllegalStateException(String.format(
				"entry for %s:%d not in table", //$NON-NLS-1$
				e.pack, Long.valueOf(e.offset)));
	}

	private void moveToHead(Entry e) {
		if (e != lruHead) {
			lruRemove(e);
			lruPushHead(e);
		}
	}

	private void lruRemove(Entry e) {
		Entry p = e.lruPrev;
		Entry n = e.lruNext;

		if (p != null) {
			p.lruNext = n;
		} else {
			lruHead = n;
		}

		if (n != null) {
			n.lruPrev = p;
		} else {
			lruTail = p;
		}
	}

	private void lruPushHead(Entry e) {
		Entry n = lruHead;
		e.lruNext = n;
		if (n != null)
			n.lruPrev = e;
		else
			lruTail = e;

		e.lruPrev = null;
		lruHead = e;
	}

	int getMemoryUsed() {
		return curByteCount;
	}

	int getMemoryUsedByLruChainForTest() {
		int r = 0;
		for (Entry e = lruHead; e != null; e = e.lruNext) {
			r += e.data.length;
		}
		return r;
	}

	int getMemoryUsedByTableForTest() {
		int r = 0;
		for (Entry t : table) {
			for (Entry e = t; e != null; e = e.tableNext) {
				r += e.data.length;
			}
		}
		return r;
	}

	static class Entry {
		final DfsStreamKey pack;
		final long offset;
		final int type;
		final byte[] data;

		Entry tableNext;
		Entry lruPrev;
		Entry lruNext;

		Entry(DfsStreamKey key, long offset, int type, byte[] data) {
			this.pack = key;
			this.offset = offset;
			this.type = type;
			this.data = data;
		}
	}
}
