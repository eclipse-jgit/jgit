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

import java.util.HashMap;
import java.util.Map;

/**
 * Caches recently used objects for {@link DhtReader}.
 * <p>
 * This cache is not thread-safe. Each reader should have its own cache.
 */
final class DeltaBaseCache {
	private final DhtReaderOptions options;

	private final Map<KeyAndOffset, Entry> map;

	private int size;

	private Entry lruHead;

	private Entry lruTail;

	DeltaBaseCache(DhtReaderOptions options) {
		this.options = options;
		this.map = new HashMap<KeyAndOffset, Entry>(
				options.getDeltaBaseCacheEntryCount());
	}

	Entry get(ChunkKey key, int offset) {
		// TODO(spearce) Avoid memory allocation on search.
		Entry ent = map.get(new KeyAndOffset(key, offset));
		if (ent != null && lruHead != ent)
			moveToHead(ent);
		return ent;
	}

	void put(ChunkKey key, int offset, int type, byte[] data) {
		int len = data.length;
		if (options.getDeltaBaseCacheSize() < len)
			return;

		while (lruTail != null && options.getDeltaBaseCacheSize() < size + len) {
			Entry t = lruTail;
			unlink(t);
			map.remove(t.key);
			size -= t.data.length;
		}

		KeyAndOffset k = new KeyAndOffset(key, offset);
		Entry ent = new Entry(k, type, data);
		map.put(k, ent);
		moveToHead(ent);
		size += len;
	}

	private void moveToHead(final Entry e) {
		unlink(e);

		e.lruPrev = null;
		e.lruNext = lruHead;

		if (lruHead != null)
			lruHead.lruPrev = e;
		else
			lruTail = e;

		lruHead = e;
	}

	private void unlink(final Entry e) {
		final Entry prev = e.lruPrev;
		final Entry next = e.lruNext;

		if (prev != null)
			prev.lruNext = next;
		else
			lruHead = next;

		if (next != null)
			next.lruPrev = prev;
		else
			lruTail = prev;
	}

	static class Entry {
		final KeyAndOffset key;

		final int type;

		final byte[] data;

		private Entry lruPrev;

		private Entry lruNext;

		Entry(KeyAndOffset key, int type, byte[] data) {
			this.key = key;
			this.type = type;
			this.data = data;
		}
	}

	private static class KeyAndOffset {
		final ChunkKey key;

		final int offset;

		KeyAndOffset(ChunkKey key, int offset) {
			this.key = key;
			this.offset = offset;
		}

		@Override
		public int hashCode() {
			return key.hashCode() * 31 + offset;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof KeyAndOffset) {
				KeyAndOffset o = (KeyAndOffset) obj;
				return key.equals(o.key) && offset == o.offset;
			}
			return false;
		}
	}
}
