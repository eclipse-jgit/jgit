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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/*
 * Ported to SHA-256 from org.eclipse.jgit.lib.MutableObjectIdTest
 */
public class MutableLongObjectIdTest {

	@Test
	public void testFromRawLong() {
		MutableLongObjectId m = new MutableLongObjectId();
		m.fromRaw(new long[] { 1L, 2L, 3L, 4L });
		assertEquals(new LongObjectId(1L, 2L, 3L, 4L), m);
	}

	@Test
	public void testFromString() {
		AnyLongObjectId id = new LongObjectId(1L, 2L, 3L, 4L);
		MutableLongObjectId m = new MutableLongObjectId();
		m.fromString(id.name());
		assertEquals(id, m);
	}

	@Test
	public void testFromStringByte() {
		AnyLongObjectId id = new LongObjectId(1L, 2L, 3L, 4L);
		MutableLongObjectId m = new MutableLongObjectId();
		byte[] buf = new byte[64];
		id.copyTo(buf, 0);
		m.fromString(buf, 0);
		assertEquals(id, m);
	}

	@Test
	public void testCopy() {
		MutableLongObjectId m = new MutableLongObjectId();
		m.fromRaw(new long[] { 1L, 2L, 3L, 4L });
		assertEquals(m, new MutableLongObjectId(m));
	}

	@Test
	public void testToObjectId() {
		MutableLongObjectId m = new MutableLongObjectId();
		m.fromRaw(new long[] { 1L, 2L, 3L, 4L });
		assertEquals(m, m.toObjectId());
	}
}
