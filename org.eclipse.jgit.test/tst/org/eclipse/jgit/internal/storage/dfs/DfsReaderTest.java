/*
 * Copyright (C) 2023, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_MIN_BYTES_OBJ_SIZE_INDEX;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_PACK_SECTION;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource;
import org.eclipse.jgit.internal.storage.dfs.DfsReader.DataLoadListener;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.junit.Before;
import org.junit.Test;

public class DfsReaderTest {
	InMemoryRepository db;

	@Before
	public void setUp() {
		db = new InMemoryRepository(new DfsRepositoryDescription("test"));
	}

	@Test
	public void isNotLargerThan_objAboveThreshold()
			throws IOException {
		setObjectSizeIndexMinBytes(100);
		ObjectId obj = insertBlobWithSize(200);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertFalse("limit < threshold < obj",
					ctx.isNotLargerThan(obj, OBJ_BLOB, 50));
			assertEquals(1, ctx.stats.isNotLargerThanCallCount);
			assertEquals(1, ctx.stats.objectSizeIndexHit);
			assertEquals(0, ctx.stats.objectSizeIndexMiss);

			assertFalse("limit = threshold < obj",
					ctx.isNotLargerThan(obj, OBJ_BLOB, 100));
			assertEquals(2, ctx.stats.isNotLargerThanCallCount);
			assertEquals(2, ctx.stats.objectSizeIndexHit);
			assertEquals(0, ctx.stats.objectSizeIndexMiss);

			assertFalse("threshold < limit < obj",
					ctx.isNotLargerThan(obj, OBJ_BLOB, 150));
			assertEquals(3, ctx.stats.isNotLargerThanCallCount);
			assertEquals(3, ctx.stats.objectSizeIndexHit);
			assertEquals(0, ctx.stats.objectSizeIndexMiss);

			assertTrue("threshold < limit = obj",
					ctx.isNotLargerThan(obj, OBJ_BLOB, 200));
			assertEquals(4, ctx.stats.isNotLargerThanCallCount);
			assertEquals(4, ctx.stats.objectSizeIndexHit);
			assertEquals(0, ctx.stats.objectSizeIndexMiss);

			assertTrue("threshold < obj < limit",
					ctx.isNotLargerThan(obj, OBJ_BLOB, 250));
			assertEquals(5, ctx.stats.isNotLargerThanCallCount);
			assertEquals(5, ctx.stats.objectSizeIndexHit);
			assertEquals(0, ctx.stats.objectSizeIndexMiss);
		}
	}


	@Test
	public void isNotLargerThan_objBelowThreshold()
			throws IOException {
		setObjectSizeIndexMinBytes(100);
		insertBlobWithSize(1000); // index not empty
		ObjectId obj = insertBlobWithSize(50);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertFalse("limit < obj < threshold",
					ctx.isNotLargerThan(obj, OBJ_BLOB, 10));
			assertEquals(1, ctx.stats.isNotLargerThanCallCount);
			assertEquals(0, ctx.stats.objectSizeIndexHit);
			assertEquals(1, ctx.stats.objectSizeIndexMiss);

			assertTrue("limit = obj < threshold",
					ctx.isNotLargerThan(obj, OBJ_BLOB, 50));
			assertEquals(2, ctx.stats.isNotLargerThanCallCount);
			assertEquals(0, ctx.stats.objectSizeIndexHit);
			assertEquals(2, ctx.stats.objectSizeIndexMiss);

			assertTrue("obj < limit < threshold",
					ctx.isNotLargerThan(obj, OBJ_BLOB, 80));
			assertEquals(3, ctx.stats.isNotLargerThanCallCount);
			assertEquals(0, ctx.stats.objectSizeIndexHit);
			assertEquals(3, ctx.stats.objectSizeIndexMiss);

			assertTrue("obj < limit = threshold",
					ctx.isNotLargerThan(obj, OBJ_BLOB, 100));
			assertEquals(4, ctx.stats.isNotLargerThanCallCount);
			assertEquals(0, ctx.stats.objectSizeIndexHit);
			assertEquals(4, ctx.stats.objectSizeIndexMiss);

			assertTrue("obj < threshold < limit",
					ctx.isNotLargerThan(obj, OBJ_BLOB, 120));
			assertEquals(5, ctx.stats.isNotLargerThanCallCount);
			assertEquals(0, ctx.stats.objectSizeIndexHit);
			assertEquals(5, ctx.stats.objectSizeIndexMiss);
		}
	}

	@Test
	public void isNotLargerThan_emptyIdx() throws IOException {
		setObjectSizeIndexMinBytes(100);
		ObjectId obj = insertBlobWithSize(10);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertFalse(ctx.isNotLargerThan(obj, OBJ_BLOB, 0));
			assertTrue(ctx.isNotLargerThan(obj, OBJ_BLOB, 10));
			assertTrue(ctx.isNotLargerThan(obj, OBJ_BLOB, 40));
			assertTrue(ctx.isNotLargerThan(obj, OBJ_BLOB, 50));
			assertTrue(ctx.isNotLargerThan(obj, OBJ_BLOB, 100));

			assertEquals(5, ctx.stats.isNotLargerThanCallCount);
			assertEquals(5, ctx.stats.objectSizeIndexMiss);
			assertEquals(0, ctx.stats.objectSizeIndexHit);
		}
	}

	@Test
	public void isNotLargerThan_noObjectSizeIndex() throws IOException {
		setObjectSizeIndexMinBytes(-1);
		ObjectId obj = insertBlobWithSize(10);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertFalse(ctx.isNotLargerThan(obj, OBJ_BLOB, 0));
			assertTrue(ctx.isNotLargerThan(obj, OBJ_BLOB, 10));
			assertTrue(ctx.isNotLargerThan(obj, OBJ_BLOB, 40));
			assertTrue(ctx.isNotLargerThan(obj, OBJ_BLOB, 50));
			assertTrue(ctx.isNotLargerThan(obj, OBJ_BLOB, 100));

			assertEquals(5, ctx.stats.isNotLargerThanCallCount);
			assertEquals(0, ctx.stats.objectSizeIndexMiss);
			assertEquals(0, ctx.stats.objectSizeIndexHit);
		}
	}

	@Test
	public void dataLoadListener_noInvocations() throws IOException {
		ObjectId obj = insertBlobWithSize(100);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			CounterDataLoadListener listener = new CounterDataLoadListener();
			ctx.addDataLoadListener(listener);
			assertEquals(null, listener.callsPerExt.get(PackExt.INDEX));
		}
	}

	@Test
	public void dataLoadListener_has_openIdx() throws IOException {
		ObjectId obj = insertBlobWithSize(100);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			CounterDataLoadListener listener = new CounterDataLoadListener();
			ctx.addDataLoadListener(listener);
			boolean has = ctx.has(obj);
			assertTrue(has);
			assertEquals(Integer.valueOf(1), listener.callsPerExt.get(PackExt.INDEX));
		}
	}

	@Test
	public void dataLoadListener_notLargerThan_openMultipleIndices() throws IOException {
			setObjectSizeIndexMinBytes(100);
			ObjectId obj = insertBlobWithSize(200);
			try (DfsReader ctx = db.getObjectDatabase().newReader()) {
				CounterDataLoadListener listener = new CounterDataLoadListener();
				ctx.addDataLoadListener(listener);
				boolean notLargerThan = ctx.isNotLargerThan(obj, OBJ_BLOB, 1000);
				assertTrue(notLargerThan);
				assertEquals(Integer.valueOf(1), listener.callsPerExt.get(PackExt.INDEX));
				assertEquals(Integer.valueOf(1), listener.callsPerExt.get(PackExt.OBJECT_SIZE_INDEX));
			}
	}

	@Test
	public void dataLoadListener_has_openMultipleIndices() throws IOException {
		setObjectSizeIndexMinBytes(100);
		insertBlobWithSize(200);
		insertBlobWithSize(230);
		insertBlobWithSize(100);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			CounterDataLoadListener listener = new CounterDataLoadListener();
			ctx.addDataLoadListener(listener);
			ObjectId oid = ObjectId.fromString("aa48de2aa61d9dffa8a05439dc115fe82f10f129");
			boolean has = ctx.has(oid);
			assertFalse(has);
			// Open 3 indices trying to find the pack
			assertEquals(Integer.valueOf(3), listener.callsPerExt.get(PackExt.INDEX));
		}
	}


	@Test
	public void dataLoadListener_has_repeatedCalls_openMultipleIndices() throws IOException {
		// Two objects NOT in the repo
		ObjectId oid = ObjectId.fromString("aa48de2aa61d9dffa8a05439dc115fe82f10f129");
		ObjectId oid2 = ObjectId.fromString("aa48de2aa61d9dffa8a05439dc115fe82f10f130");

		setObjectSizeIndexMinBytes(100);
		insertBlobWithSize(200);
		insertBlobWithSize(230);
		insertBlobWithSize(100);
		CounterDataLoadListener listener = new CounterDataLoadListener();
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			ctx.addDataLoadListener(listener);
			boolean has = ctx.has(oid);
			ctx.has(oid);
			ctx.has(oid2);
			assertFalse(has);
			// The 3 indices were loaded only once each
			assertEquals(Integer.valueOf(3), listener.callsPerExt.get(PackExt.INDEX));
		}
	}

	@Test
	public void dataLoadListener_loadBlock() throws IOException {
		ObjectId objectId = insertBlobWithSize(200);
		CounterDataLoadListener listener = new CounterDataLoadListener();
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			ctx.addDataLoadListener(listener);
			ctx.newReader().open(objectId).getBytes();
			assertEquals(1, listener.blocksLoaded);
		}
	}

	private static class CounterDataLoadListener implements DataLoadListener {
		Map<PackExt, Integer> callsPerExt = new HashMap<>();

		int blocksLoaded = 0;

		@Override
		public void refLoad(String packName, PackSource src, PackExt ext,
				long size, int refHash) {
			callsPerExt.merge(ext, 1, Integer::sum);
		}

		@Override
		public void blockLoad(String packName, PackSource src, PackExt ext, long size, int refHash, long position) {
      blocksLoaded += 1;
		}
	}

	private ObjectId insertBlobWithSize(int size)
			throws IOException {
		TestRng testRng = new TestRng(JGitTestUtil.getName());
		ObjectId oid;
		try (ObjectInserter ins = db.newObjectInserter()) {
				oid = ins.insert(OBJ_BLOB,
						testRng.nextBytes(size));
			ins.flush();
		}
		return oid;
	}

	private void setObjectSizeIndexMinBytes(int threshold) {
		db.getConfig().setInt(CONFIG_PACK_SECTION, null,
				CONFIG_KEY_MIN_BYTES_OBJ_SIZE_INDEX, threshold);
	}
}
