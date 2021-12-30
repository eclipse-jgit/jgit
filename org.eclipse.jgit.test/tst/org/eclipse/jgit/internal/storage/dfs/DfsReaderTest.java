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
	public void isNotLargerThan_noObjectSizeIndex() throws IOException {
		setObjectSizeIndexMinBytes(-1);
		ObjectId obj = insertBlobWithSize(10);
		try (DfsReader ctx = db.getObjectDatabase().newReader()) {
			assertFalse(ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 0));
			assertTrue(ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 10));
			assertTrue(ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 40));
			assertTrue(ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 50));
			assertTrue(ctx.isNotLargerThan(obj, Constants.OBJ_BLOB, 100));

			assertEquals(5, ctx.stats.isNotLargerThanCallCount);
			assertEquals(0, ctx.stats.objectSizeIndexMiss);
			assertEquals(0, ctx.stats.objectSizeIndexHit);
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
