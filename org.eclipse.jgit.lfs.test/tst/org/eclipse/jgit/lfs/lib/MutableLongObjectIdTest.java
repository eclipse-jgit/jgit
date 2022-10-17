/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs.lib;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/*
 * Ported to SHA-256 from org.eclipse.jgit.lib.MutableObjectIdTest
 */
public class MutableLongObjectIdTest {

	@Test
	void testFromRawLong() {
		MutableLongObjectId m = new MutableLongObjectId();
		m.fromRaw(new long[] { 1L, 2L, 3L, 4L });
		assertEquals(new LongObjectId(1L, 2L, 3L, 4L), m);
	}

	@Test
	void testFromString() {
		AnyLongObjectId id = new LongObjectId(1L, 2L, 3L, 4L);
		MutableLongObjectId m = new MutableLongObjectId();
		m.fromString(id.name());
		assertEquals(id, m);
	}

	@Test
	void testFromStringByte() {
		AnyLongObjectId id = new LongObjectId(1L, 2L, 3L, 4L);
		MutableLongObjectId m = new MutableLongObjectId();
		byte[] buf = new byte[64];
		id.copyTo(buf, 0);
		m.fromString(buf, 0);
		assertEquals(id, m);
	}

	@Test
	void testCopy() {
		MutableLongObjectId m = new MutableLongObjectId();
		m.fromRaw(new long[] { 1L, 2L, 3L, 4L });
		assertEquals(m, new MutableLongObjectId(m));
	}

	@Test
	void testToObjectId() {
		MutableLongObjectId m = new MutableLongObjectId();
		m.fromRaw(new long[] { 1L, 2L, 3L, 4L });
		assertEquals(m, m.toObjectId());
	}
}
