/*
 * Copyright (C) 2015, Google Inc.
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

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.eclipse.jgit.internal.storage.dfs.DeltaBaseCache.Entry;
import org.eclipse.jgit.junit.TestRng;
import org.junit.Before;
import org.junit.Test;

public class DeltaBaseCacheTest {
	private static final int SZ = 512;

	private DfsStreamKey key;
	private DeltaBaseCache cache;
	private TestRng rng;

	@Before
	public void setUp() {
		key = new DfsStreamKey();
		cache = new DeltaBaseCache(SZ);
		rng = new TestRng(getClass().getSimpleName());
	}

	@Test
	public void testObjectLargerThanCacheDoesNotEvict() {
		byte[] obj12 = put(12, 32);
		put(24, SZ + 5);
		assertNull("does not store large object", cache.get(key, 24));
		get(obj12, 12);
	}

	@Test
	public void testCacheLruExpires1() {
		byte[] obj1 = put(1, SZ / 4);
		put(2, SZ / 4);
		byte[] obj3 = put(3, SZ / 4);
		put(4, SZ / 4);
		assertEquals(SZ, cache.getMemoryUsed());

		get(obj3, 3);
		get(obj1, 1);
		put(5, SZ / 2);
		assertEquals(SZ, cache.getMemoryUsed());
		assertEquals(SZ, cache.getMemoryUsedByTableForTest());
		assertEquals(SZ, cache.getMemoryUsedByLruChainForTest());
		assertNull(cache.get(key, 4));
		assertNull(cache.get(key, 2));

		get(obj1, 1);
		get(obj3, 3);
	}

	@Test
	public void testCacheLruExpires2() {
		int pos0 = (0 << 10) | 2;
		int pos1 = (1 << 10) | 2;
		int pos2 = (2 << 10) | 2;
		int pos5 = (5 << 10) | 2;
		int pos6 = (6 << 10) | 2;

		put(pos0, SZ / 4);
		put(pos5, SZ / 4);
		byte[] obj1 = put(pos1, SZ / 4);
		byte[] obj2 = put(pos2, SZ / 4);
		assertEquals(SZ, cache.getMemoryUsed());

		byte[] obj6 = put(pos6, SZ / 2);
		assertEquals(SZ, cache.getMemoryUsed());
		assertEquals(SZ, cache.getMemoryUsedByTableForTest());
		assertEquals(SZ, cache.getMemoryUsedByLruChainForTest());
		assertNull(cache.get(key, pos0));
		assertNull(cache.get(key, pos5));

		get(obj1, pos1);
		get(obj2, pos2);
		get(obj6, pos6);
	}

	@Test
	public void testCacheMemoryUsedConsistentWithExpectations() {
		put(1, 32);
		put(2, 32);
		put(3, 32);

		assertNotNull(cache.get(key, 1));
		assertNotNull(cache.get(key, 1));

		assertEquals(32 * 3, cache.getMemoryUsed());
		assertEquals(32 * 3, cache.getMemoryUsedByTableForTest());
		assertEquals(32 * 3, cache.getMemoryUsedByLruChainForTest());
	}

	private void get(byte[] data, int position) {
		Entry e = cache.get(key, position);
		assertNotNull("expected entry at " + position, e);
		assertEquals("expected blob for " + position, OBJ_BLOB, e.type);
		assertSame("expected data for " + position, data, e.data);
	}

	private byte[] put(int position, int sz) {
		byte[] data = rng.nextBytes(sz);
		cache.put(key, position, OBJ_BLOB, data);
		return data;
	}
}
