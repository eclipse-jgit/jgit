/*
 * Copyright (C) 2019, Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SimpleLruCacheTest {

	private Path trash;

	private SimpleLruCache<String, String> cache;


	@Before
	public void setup() throws IOException {
		trash = Files.createTempDirectory("tmp_");
		cache = new SimpleLruCache<>(100, 0.2f);
	}

	@Before
	@After
	public void tearDown() throws Exception {
		FileUtils.delete(trash.toFile(),
				FileUtils.RECURSIVE | FileUtils.SKIP_MISSING);
	}

	@Test
	public void testPutGet() {
		cache.put("a", "A");
		cache.put("z", "Z");
		assertEquals("A", cache.get("a"));
		assertEquals("Z", cache.get("z"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testPurgeFactorTooLarge() {
		cache.configure(5, 1.01f);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testPurgeFactorTooLarge2() {
		cache.configure(5, 100);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testPurgeFactorTooSmall() {
		cache.configure(5, 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testPurgeFactorTooSmall2() {
		cache.configure(5, -100);
	}

	@Test
	public void testGetMissing() {
		assertEquals(null, cache.get("a"));
	}

	@Test
	public void testPurge() {
		for (int i = 0; i < 101; i++) {
			cache.put("a" + i, "a" + i);
		}
		assertEquals(80, cache.size());
		assertNull(cache.get("a0"));
		assertNull(cache.get("a20"));
		assertNotNull(cache.get("a21"));
		assertNotNull(cache.get("a99"));
	}

	@Test
	public void testConfigure() {
		for (int i = 0; i < 100; i++) {
			cache.put("a" + i, "a" + i);
		}
		assertEquals(100, cache.size());
		cache.configure(10, 0.3f);
		assertEquals(7, cache.size());
		assertNull(cache.get("a0"));
		assertNull(cache.get("a92"));
		assertNotNull(cache.get("a93"));
		assertNotNull(cache.get("a99"));
	}
}
