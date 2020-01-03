/*
 * Copyright (C) 2019, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
