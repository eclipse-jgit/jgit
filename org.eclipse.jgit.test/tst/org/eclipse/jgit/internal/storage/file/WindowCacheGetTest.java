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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class WindowCacheGetTest extends SampleDataRepositoryTestCase {
	private List<TestObject> toLoad;
	private WindowCacheConfig cfg;
	private boolean useStrongRefs;

	public static Collection<Object[]> data() {
		return Arrays
				.asList(new Object[][]{{Boolean.TRUE}, {Boolean.FALSE}});
	}

	public void initWindowCacheGetTest(Boolean useStrongRef) {
		this.useStrongRefs = useStrongRef.booleanValue();
	}

	@Override
	@BeforeEach
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
		cfg = new WindowCacheConfig();
		cfg.setPackedGitUseStrongRefs(useStrongRefs);
	}

	@MethodSource("data")
	@ParameterizedTest(name = "useStrongRefs={0}")
	void testCache_Defaults(Boolean useStrongRef) throws IOException {
		initWindowCacheGetTest(useStrongRef);
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

	@MethodSource("data")
	@ParameterizedTest(name = "useStrongRefs={0}")
	void testCache_TooFewFiles(Boolean useStrongRef) throws IOException {
		initWindowCacheGetTest(useStrongRef);
		cfg.setPackedGitOpenFiles(2);
		cfg.install();
		doCacheTests();
		checkLimits(cfg);
	}

	@MethodSource("data")
	@ParameterizedTest(name = "useStrongRefs={0}")
	void testCache_TooSmallLimit(Boolean useStrongRef) throws IOException {
		initWindowCacheGetTest(useStrongRef);
		cfg.setPackedGitWindowSize(4096);
		cfg.setPackedGitLimit(4096);
		cfg.install();
		doCacheTests();
		checkLimits(cfg);
	}

	private static void checkLimits(WindowCacheConfig cfg) {
		final WindowCache cache = WindowCache.getInstance();
		WindowCacheStats s = cache.getStats();
		assertTrue(0 < s.getAverageLoadTime(),
				"average load time should be > 0");
		assertTrue(0 < s.getOpenByteCount(), "open byte count should be > 0");
		assertTrue(0 <= s.getEvictionCount(), "eviction count should be >= 0");
		assertTrue(0 < s.getHitCount(), "hit count should be > 0");
		assertTrue(0 < s.getHitRatio(), "hit ratio should be > 0");
		assertTrue(1 > s.getHitRatio(), "hit ratio should be < 1");
		assertTrue(0 < s.getLoadCount(), "load count should be > 0");
		assertTrue(0 <= s.getLoadFailureCount(),
				"load failure count should be >= 0");
		assertTrue(0.0 <= s.getLoadFailureRatio(),
				"load failure ratio should be >= 0");
		assertTrue(1 > s.getLoadFailureRatio(),
				"load failure ratio should be < 1");
		assertTrue(0 < s.getLoadSuccessCount(),
				"load success count should be > 0");
		assertTrue(s.getOpenByteCount() <= cfg.getPackedGitLimit(),
				"open byte count should be <= core.packedGitLimit");
		assertTrue(s.getOpenFileCount() <= cfg.getPackedGitOpenFiles(),
				"open file count should be <= core.packedGitOpenFiles");
		assertTrue(0 <= s.getMissCount(), "miss success count should be >= 0");
		assertTrue(0 <= s.getMissRatio(), "miss ratio should be > 0");
		assertTrue(1 > s.getMissRatio(), "miss ratio should be < 1");
		assertTrue(0 < s.getRequestCount(), "request count should be > 0");
		assertTrue(0 < s.getTotalLoadTime(), "total load time should be > 0");
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
