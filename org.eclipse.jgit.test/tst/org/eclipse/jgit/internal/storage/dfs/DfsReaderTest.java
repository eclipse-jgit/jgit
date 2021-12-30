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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.lib.Constants;
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
					ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 50));
			assertEquals(ctx.stats.isNotLargerThanCallCount, 1);
			assertEquals(ctx.stats.objectSizeIndexHit, 1);

			assertFalse("limit = threshold < obj",
					ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 100));
			assertEquals(ctx.stats.isNotLargerThanCallCount, 2);
			assertEquals(ctx.stats.objectSizeIndexHit, 2);

			assertFalse("threshold < limit < obj",
					ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 150));
			assertEquals(ctx.stats.isNotLargerThanCallCount, 3);
			assertEquals(ctx.stats.objectSizeIndexHit, 3);

			assertTrue("threshold < limit = obj",
					ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 200));
			assertEquals(ctx.stats.isNotLargerThanCallCount, 4);
			assertEquals(ctx.stats.objectSizeIndexHit, 4);

			assertTrue("threshold < obj < limit",
					ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 250));
			assertEquals(ctx.stats.isNotLargerThanCallCount, 5);
			assertEquals(ctx.stats.objectSizeIndexHit, 5);
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
					ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 10));
			assertEquals(ctx.stats.isNotLargerThanCallCount, 1);
			assertEquals(ctx.stats.objectSizeIndexMiss, 1);

			assertTrue("limit = obj < threshold",
					ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 50));
			assertEquals(ctx.stats.isNotLargerThanCallCount, 2);
			assertEquals(ctx.stats.objectSizeIndexMiss, 2);

			assertTrue("obj < limit < threshold",
					ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 80));
			assertEquals(ctx.stats.isNotLargerThanCallCount, 3);
			assertEquals(ctx.stats.objectSizeIndexMiss, 3);

			assertTrue("obj < limit = threshold",
					ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 100));
			assertEquals(ctx.stats.isNotLargerThanCallCount, 4);
			assertEquals(ctx.stats.objectSizeIndexMiss, 4);

			assertTrue("obj < threshold < limit",
					ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 120));
			assertEquals(ctx.stats.isNotLargerThanCallCount, 5);
			assertEquals(ctx.stats.objectSizeIndexMiss, 5);
		}
	}

	@Test
	public void isNotLargerThan_emptyIdx() throws IOException {
		setObjectSizeIndexMinBytes(100);
		ObjectId obj = insertBlobWithSize(10);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertFalse(ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 0));
			assertTrue(ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 10));
			assertTrue(ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 40));
			assertTrue(ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 50));
			assertTrue(ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 100));

			assertEquals(ctx.stats.isNotLargerThanCallCount, 5);
			assertEquals(ctx.stats.objectSizeIndexMiss, 5);
			assertEquals(ctx.stats.objectSizeIndexHit, 0);
		}
	}

	@Test
	public void isNotLargerThan_noObjectSizeIndex() throws IOException {
		setObjectSizeIndexMinBytes(-1);
		ObjectId obj = insertBlobWithSize(10);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertFalse(ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 0));
			assertTrue(ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 10));
			assertTrue(ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 40));
			assertTrue(ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 50));
			assertTrue(ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 100));

			assertEquals(ctx.stats.isNotLargerThanCallCount, 5);
			assertEquals(ctx.stats.objectSizeIndexMiss, 0);
			assertEquals(ctx.stats.objectSizeIndexHit, 0);
		}
	}

	private ObjectId insertBlobWithSize(int size)
			throws IOException {
		TestRng testRng = new TestRng(JGitTestUtil.getName());
		ObjectId oid;
		try (ObjectInserter ins = db.newObjectInserter()) {
				oid = ins.insert(Constants.OBJ_BLOB,
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
