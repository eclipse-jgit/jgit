/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Jonas Fonseca <fonseca@diku.dk>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ObjectIdTest {
	@Test
	public void test001_toString() {
		final String x = "def4c620bc3713bb1bb26b808ec9312548e73946";
		final ObjectId oid = ObjectId.fromString(x);
		assertEquals(x, oid.name());
	}

	@Test
	public void test002_toString() {
		final String x = "ff00eedd003713bb1bb26b808ec9312548e73946";
		final ObjectId oid = ObjectId.fromString(x);
		assertEquals(x, oid.name());
	}

	@Test
	public void test003_equals() {
		final String x = "def4c620bc3713bb1bb26b808ec9312548e73946";
		final ObjectId a = ObjectId.fromString(x);
		final ObjectId b = ObjectId.fromString(x);
		assertEquals(a.hashCode(), b.hashCode());
		assertEquals("a and b are same", b, a);
	}

	@Test
	public void test004_isId() {
		assertTrue("valid id", ObjectId
				.isId("def4c620bc3713bb1bb26b808ec9312548e73946"));
	}

	@Test
	public void test005_notIsId() {
		assertFalse("bob is not an id", ObjectId.isId("bob"));
	}

	@Test
	public void test006_notIsId() {
		assertFalse("39 digits is not an id", ObjectId
				.isId("def4c620bc3713bb1bb26b808ec9312548e7394"));
	}

	@Test
	public void test007_isId() {
		assertTrue("uppercase is accepted", ObjectId
				.isId("Def4c620bc3713bb1bb26b808ec9312548e73946"));
	}

	@Test
	public void test008_notIsId() {
		assertFalse("g is not a valid hex digit", ObjectId
				.isId("gef4c620bc3713bb1bb26b808ec9312548e73946"));
	}

	@Test
	public void test009_toString() {
		final String x = "ff00eedd003713bb1bb26b808ec9312548e73946";
		final ObjectId oid = ObjectId.fromString(x);
		assertEquals(x, ObjectId.toString(oid));
	}

	@Test
	public void test010_toString() {
		final String x = "0000000000000000000000000000000000000000";
		assertEquals(x, ObjectId.toString(null));
	}

	@Test
	public void test011_toString() {
		final String x = "0123456789ABCDEFabcdef1234567890abcdefAB";
		final ObjectId oid = ObjectId.fromString(x);
		assertEquals(x.toLowerCase(), oid.name());
	}

	@Test
	public void testGetByte() {
		byte[] raw = new byte[20];
		for (int i = 0; i < 20; i++)
			raw[i] = (byte) (0xa0 + i);
		ObjectId id = ObjectId.fromRaw(raw);

		assertEquals(raw[0] & 0xff, id.getFirstByte());
		assertEquals(raw[0] & 0xff, id.getByte(0));
		assertEquals(raw[1] & 0xff, id.getByte(1));

		for (int i = 2; i < 20; i++)
			assertEquals("index " + i, raw[i] & 0xff, id.getByte(i));
	}

	@Test
	public void testSetByte() {
		byte[] exp = new byte[20];
		for (int i = 0; i < 20; i++)
			exp[i] = (byte) (0xa0 + i);

		MutableObjectId id = new MutableObjectId();
		id.fromRaw(exp);
		assertEquals(ObjectId.fromRaw(exp).name(), id.name());

		id.setByte(0, 0x10);
		assertEquals(0x10, id.getByte(0));
		exp[0] = 0x10;
		assertEquals(ObjectId.fromRaw(exp).name(), id.name());

		for (int p = 1; p < 20; p++) {
			id.setByte(p, 0x10 + p);
			assertEquals(0x10 + p, id.getByte(p));
			exp[p] = (byte) (0x10 + p);
			assertEquals(ObjectId.fromRaw(exp).name(), id.name());
		}

		for (int p = 0; p < 20; p++) {
			id.setByte(p, 0x80 + p);
			assertEquals(0x80 + p, id.getByte(p));
			exp[p] = (byte) (0x80 + p);
			assertEquals(ObjectId.fromRaw(exp).name(), id.name());
		}
	}
}
