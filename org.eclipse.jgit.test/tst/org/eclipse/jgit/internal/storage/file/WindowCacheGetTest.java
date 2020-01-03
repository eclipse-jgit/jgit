/*
 * Copyright (C) 2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
		assertEquals(6, cache.getOpenFiles());
		assertEquals(17346, cache.getOpenBytes());
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
		assertTrue(cache.getOpenFiles() <= cfg.getPackedGitOpenFiles());
		assertTrue(cache.getOpenBytes() <= cfg.getPackedGitLimit());
		assertTrue(0 < cache.getOpenFiles());
		assertTrue(0 < cache.getOpenBytes());
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
