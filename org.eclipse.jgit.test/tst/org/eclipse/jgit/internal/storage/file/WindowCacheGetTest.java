/*
 * Copyright (C) 2009, Google Inc.
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

package org.eclipse.jgit.internal.storage.file;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.storage.file.WindowCacheStats;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.eclipse.jgit.util.MutableInteger;
import org.junit.Before;
import org.junit.Test;

public class WindowCacheGetTest extends SampleDataRepositoryTestCase {
	private List<TestObject> toLoad;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		toLoad = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(
				new FileInputStream(JGitTestUtil
						.getTestResourceFile("all_packed_objects.txt")),
				UTF_8))) {
			String line;
			while ((line = br.readLine()) != null) {
				final String[] parts = line.split(" {1,}");
				final TestObject o = new TestObject();
				o.id = ObjectId.fromString(parts[0]);
				o.setType(parts[1]);
				// parts[2] is the inflate size
				// parts[3] is the size-in-pack
				// parts[4] is the offset in the pack
				toLoad.add(o);
			}
		}
		assertEquals(96, toLoad.size());
	}

	@Test
	public void testCache_Defaults() throws IOException {
		WindowCacheConfig cfg = new WindowCacheConfig();
		cfg.install();
		doCacheTests();
		checkLimits(cfg);

		final WindowCache cache = WindowCache.getInstance();
		WindowCacheStats s = cache.getStats();
		assertEquals(6, s.getOpenFileCount());
		assertEquals(17346, s.getOpenByteCount());
		assertEquals(0, s.getEvictionCount());
		assertEquals(90, s.getHitCount());
		assertTrue(s.getHitRatio() > 0.0 && s.getHitRatio() < 1.0);
		assertEquals(6, s.getLoadCount());
		assertEquals(0, s.getLoadFailureCount());
		assertEquals(0, s.getLoadFailureRatio(), 0.001);
		assertEquals(6, s.getLoadSuccessCount());
		assertEquals(6, s.getMissCount());
		assertTrue(s.getMissRatio() > 0.0 && s.getMissRatio() < 1.0);
		assertEquals(96, s.getRequestCount());
		assertTrue(s.getAverageLoadTime() > 0.0);
		assertTrue(s.getTotalLoadTime() > 0.0);
	}

	@Test
	public void testCache_TooFewFiles() throws IOException {
		final WindowCacheConfig cfg = new WindowCacheConfig();
		cfg.setPackedGitOpenFiles(2);
		cfg.install();
		doCacheTests();
		checkLimits(cfg);
	}

	@Test
	public void testCache_TooSmallLimit() throws IOException {
		final WindowCacheConfig cfg = new WindowCacheConfig();
		cfg.setPackedGitWindowSize(4096);
		cfg.setPackedGitLimit(4096);
		cfg.install();
		doCacheTests();
		checkLimits(cfg);
	}

	private static void checkLimits(WindowCacheConfig cfg) {
		final WindowCache cache = WindowCache.getInstance();
		WindowCacheStats s = cache.getStats();
		assertTrue(0 < s.getAverageLoadTime());
		assertTrue(0 < s.getOpenByteCount());
		assertTrue(0 < s.getOpenByteCount());
		assertTrue(0.0 < s.getAverageLoadTime());
		assertTrue(0 <= s.getEvictionCount());
		assertTrue(0 < s.getHitCount());
		assertTrue(0 < s.getHitRatio());
		assertTrue(1 > s.getHitRatio());
		assertTrue(0 < s.getLoadCount());
		assertTrue(0 <= s.getLoadFailureCount());
		assertTrue(0.0 <= s.getLoadFailureRatio());
		assertTrue(1 > s.getLoadFailureRatio());
		assertTrue(0 < s.getLoadSuccessCount());
		assertTrue(s.getOpenByteCount() <= cfg.getPackedGitLimit());
		assertTrue(s.getOpenFileCount() <= cfg.getPackedGitOpenFiles());
		assertTrue(0 <= s.getMissCount());
		assertTrue(0 <= s.getMissRatio());
		assertTrue(1 > s.getMissRatio());
		assertTrue(0 < s.getRequestCount());
		assertTrue(0 < s.getTotalLoadTime());
	}

	private void doCacheTests() throws IOException {
		for (TestObject o : toLoad) {
			final ObjectLoader or = db.open(o.id, o.type);
			assertNotNull(or);
			assertEquals(o.type, or.getType());
		}
	}

	private static class TestObject {
		ObjectId id;

		int type;

		void setType(String typeStr) throws CorruptObjectException {
			final byte[] typeRaw = Constants.encode(typeStr + " ");
			final MutableInteger ptr = new MutableInteger();
			type = Constants.decodeTypeString(id, typeRaw, (byte) ' ', ptr);
		}
	}
}
