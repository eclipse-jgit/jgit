/*
 * Copyright (C) 2026, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class ObjectFormatTest {
	@Test
	public void lengths() {
		assertEquals(20, ObjectFormat.SHA1.getByteLength());
		assertEquals(40, ObjectFormat.SHA1.getStringLength());
		assertEquals(32, ObjectFormat.SHA256.getByteLength());
		assertEquals(64, ObjectFormat.SHA256.getStringLength());
		assertEquals(Constants.OBJECT_ID_LENGTH,
				ObjectFormat.SHA1.getByteLength());
		assertEquals(Constants.OBJECT_ID_STRING_LENGTH,
				ObjectFormat.SHA1.getStringLength());
	}

	@Test
	public void lookups() {
		assertSame(ObjectFormat.SHA1, ObjectFormat.fromConfId("sha1"));
		assertSame(ObjectFormat.SHA256, ObjectFormat.fromConfId("SHA256"));
		assertThrows(IllegalArgumentException.class,
				() -> ObjectFormat.fromConfId("md5"));
		assertSame(ObjectFormat.SHA1, ObjectFormat.forStringLength(40));
		assertSame(ObjectFormat.SHA256, ObjectFormat.forStringLength(64));
		assertNull(ObjectFormat.forStringLength(41));
	}

	@Test
	public void sha1Ids() {
		assertEquals(Constants.EMPTY_BLOB_ID,
				ObjectFormat.SHA1.getEmptyBlobId());
		assertEquals(Constants.EMPTY_TREE_ID,
				ObjectFormat.SHA1.getEmptyTreeId());
		assertSame(ObjectId.zeroId(), ObjectFormat.SHA1.zeroId());
	}
}
