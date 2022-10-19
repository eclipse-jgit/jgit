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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SimpleLruCacheTest {

	private Path trash;

	private SimpleLruCache<String, String> cache;


	@BeforeEach
	public void setup() throws IOException {
		trash = Files.createTempDirectory("tmp_");
		cache = new SimpleLruCache<>(100, 0.2f);
	}

	@AfterEach
	public void tearDown() throws Exception {
		FileUtils.delete(trash.toFile(),
				FileUtils.RECURSIVE | FileUtils.SKIP_MISSING);
	}

	@Test
	void testPutGet() {
		cache.put("a", "A");
		cache.put("z", "Z");
		assertEquals("A", cache.get("a"));
		assertEquals("Z", cache.get("z"));
	}

	@Test
	void testPurgeFactorTooLarge() {
		assertThrows(IllegalArgumentException.class, () -> {
			cache.configure(5, 1.01f);
		});
	}

	@Test
	void testPurgeFactorTooLarge2() {
		assertThrows(IllegalArgumentException.class, () -> {
			cache.configure(5, 100);
		});
	}

	@Test
	void testPurgeFactorTooSmall() {
		assertThrows(IllegalArgumentException.class, () -> {
			cache.configure(5, 0);
		});
	}

	@Test
	void testPurgeFactorTooSmall2() {
		assertThrows(IllegalArgumentException.class, () -> {
			cache.configure(5, -100);
		});
	}

	@Test
	void testGetMissing() {
		assertNull(cache.get("a"));
	}

	@Test
	void testPurge() {
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
	void testConfigure() {
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
