/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.lang.ref.SoftReference;

import org.eclipse.jgit.storage.file.WindowCacheConfig;

class DeltaBaseCache {
	private static final int CACHE_SZ = 1024;

	static final SoftReference<Entry> DEAD;

	private static int hash(long position) {
		return (((int) position) << 22) >>> 22;
	}

	private static volatile int defaultMaxByteCount;

	private final int maxByteCount;

	private final Slot[] cache;

	private Slot lruHead;

	private Slot lruTail;

	private int openByteCount;

	static {
		DEAD = new SoftReference<>(null);
		reconfigure(new WindowCacheConfig());
	}

	static void reconfigure(WindowCacheConfig cfg) {
		defaultMaxByteCount = cfg.getDeltaBaseCacheLimit();
	}

	DeltaBaseCache() {
		maxByteCount = defaultMaxByteCount;
		cache = new Slot[CACHE_SZ];
	}

	Entry get(PackFile pack, long position) {
		Slot e = cache[hash(position)];
		if (e == null)
			return null;
		if (e.provider == pack && e.position == position) {
			final Entry buf = e.data.get();
			if (buf != null) {
				moveToHead(e);
				return buf;
			}
		}
		return null;
	}

	void store(final PackFile pack, final long position,
			final byte[] data, final int objectType) {
		if (data.length > maxByteCount)
			return; // Too large to cache.

		Slot e = cache[hash(position)];
		if (e == null) {
			e = new Slot();
			cache[hash(position)] = e;
		} else {
			clearEntry(e);
		}

		openByteCount += data.length;
		releaseMemory();

		e.provider = pack;
		e.position = position;
		e.sz = data.length;
		e.data = new SoftReference<>(new Entry(data, objectType));
		moveToHead(e);
	}

	private void releaseMemory() {
		while (openByteCount > maxByteCount && lruTail != null) {
			final Slot currOldest = lruTail;
			final Slot nextOldest = currOldest.lruPrev;

			clearEntry(currOldest);
			currOldest.lruPrev = null;
			currOldest.lruNext = null;

			if (nextOldest == null)
				lruHead = null;
			else
				nextOldest.lruNext = null;
			lruTail = nextOldest;
		}
	}

	private void moveToHead(Slot e) {
		unlink(e);
		e.lruPrev = null;
		e.lruNext = lruHead;
		if (lruHead != null)
			lruHead.lruPrev = e;
		else
			lruTail = e;
		lruHead = e;
	}

	private void unlink(Slot e) {
		final Slot prev = e.lruPrev;
		final Slot next = e.lruNext;
		if (prev != null)
			prev.lruNext = next;
		if (next != null)
			next.lruPrev = prev;
	}

	private void clearEntry(Slot e) {
		openByteCount -= e.sz;
		e.provider = null;
		e.data = DEAD;
		e.sz = 0;
	}

	static class Entry {
		final byte[] data;

		final int type;

		Entry(byte[] aData, int aType) {
			data = aData;
			type = aType;
		}
	}

	private static class Slot {
		Slot lruPrev;

		Slot lruNext;

		PackFile provider;

		long position;

		int sz;

		SoftReference<Entry> data = DEAD;
	}
}
