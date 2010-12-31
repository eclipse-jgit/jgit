/*
 * Copyright (C) 2008, Google Inc.
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

package org.eclipse.jgit.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NBTest {
	@Test
	public void testCompareUInt32() {
		assertTrue(NB.compareUInt32(0, 0) == 0);
		assertTrue(NB.compareUInt32(1, 0) > 0);
		assertTrue(NB.compareUInt32(0, 1) < 0);
		assertTrue(NB.compareUInt32(-1, 0) > 0);
		assertTrue(NB.compareUInt32(0, -1) < 0);
		assertTrue(NB.compareUInt32(-1, 1) > 0);
		assertTrue(NB.compareUInt32(1, -1) < 0);
	}

	@Test
	public void testDecodeUInt16() {
		assertEquals(0, NB.decodeUInt16(b(0, 0), 0));
		assertEquals(0, NB.decodeUInt16(padb(3, 0, 0), 3));

		assertEquals(3, NB.decodeUInt16(b(0, 3), 0));
		assertEquals(3, NB.decodeUInt16(padb(3, 0, 3), 3));

		assertEquals(0xde03, NB.decodeUInt16(b(0xde, 3), 0));
		assertEquals(0xde03, NB.decodeUInt16(padb(3, 0xde, 3), 3));

		assertEquals(0x03de, NB.decodeUInt16(b(3, 0xde), 0));
		assertEquals(0x03de, NB.decodeUInt16(padb(3, 3, 0xde), 3));

		assertEquals(0xffff, NB.decodeUInt16(b(0xff, 0xff), 0));
		assertEquals(0xffff, NB.decodeUInt16(padb(3, 0xff, 0xff), 3));
	}

	@Test
	public void testDecodeInt32() {
		assertEquals(0, NB.decodeInt32(b(0, 0, 0, 0), 0));
		assertEquals(0, NB.decodeInt32(padb(3, 0, 0, 0, 0), 3));

		assertEquals(3, NB.decodeInt32(b(0, 0, 0, 3), 0));
		assertEquals(3, NB.decodeInt32(padb(3, 0, 0, 0, 3), 3));

		assertEquals(0xdeadbeef, NB.decodeInt32(b(0xde, 0xad, 0xbe, 0xef), 0));
		assertEquals(0xdeadbeef, NB.decodeInt32(
				padb(3, 0xde, 0xad, 0xbe, 0xef), 3));

		assertEquals(0x0310adef, NB.decodeInt32(b(0x03, 0x10, 0xad, 0xef), 0));
		assertEquals(0x0310adef, NB.decodeInt32(
				padb(3, 0x03, 0x10, 0xad, 0xef), 3));

		assertEquals(0xffffffff, NB.decodeInt32(b(0xff, 0xff, 0xff, 0xff), 0));
		assertEquals(0xffffffff, NB.decodeInt32(
				padb(3, 0xff, 0xff, 0xff, 0xff), 3));
	}

	@Test
	public void testDecodeUInt32() {
		assertEquals(0L, NB.decodeUInt32(b(0, 0, 0, 0), 0));
		assertEquals(0L, NB.decodeUInt32(padb(3, 0, 0, 0, 0), 3));

		assertEquals(3L, NB.decodeUInt32(b(0, 0, 0, 3), 0));
		assertEquals(3L, NB.decodeUInt32(padb(3, 0, 0, 0, 3), 3));

		assertEquals(0xdeadbeefL, NB.decodeUInt32(b(0xde, 0xad, 0xbe, 0xef), 0));
		assertEquals(0xdeadbeefL, NB.decodeUInt32(padb(3, 0xde, 0xad, 0xbe,
				0xef), 3));

		assertEquals(0x0310adefL, NB.decodeUInt32(b(0x03, 0x10, 0xad, 0xef), 0));
		assertEquals(0x0310adefL, NB.decodeUInt32(padb(3, 0x03, 0x10, 0xad,
				0xef), 3));

		assertEquals(0xffffffffL, NB.decodeUInt32(b(0xff, 0xff, 0xff, 0xff), 0));
		assertEquals(0xffffffffL, NB.decodeUInt32(padb(3, 0xff, 0xff, 0xff,
				0xff), 3));
	}

	@Test
	public void testDecodeUInt64() {
		assertEquals(0L, NB.decodeUInt64(b(0, 0, 0, 0, 0, 0, 0, 0), 0));
		assertEquals(0L, NB.decodeUInt64(padb(3, 0, 0, 0, 0, 0, 0, 0, 0), 3));

		assertEquals(3L, NB.decodeUInt64(b(0, 0, 0, 0, 0, 0, 0, 3), 0));
		assertEquals(3L, NB.decodeUInt64(padb(3, 0, 0, 0, 0, 0, 0, 0, 3), 3));

		assertEquals(0xdeadbeefL, NB.decodeUInt64(b(0, 0, 0, 0, 0xde, 0xad,
				0xbe, 0xef), 0));
		assertEquals(0xdeadbeefL, NB.decodeUInt64(padb(3, 0, 0, 0, 0, 0xde,
				0xad, 0xbe, 0xef), 3));

		assertEquals(0x0310adefL, NB.decodeUInt64(b(0, 0, 0, 0, 0x03, 0x10,
				0xad, 0xef), 0));
		assertEquals(0x0310adefL, NB.decodeUInt64(padb(3, 0, 0, 0, 0, 0x03,
				0x10, 0xad, 0xef), 3));

		assertEquals(0xc0ffee78deadbeefL, NB.decodeUInt64(b(0xc0, 0xff, 0xee,
				0x78, 0xde, 0xad, 0xbe, 0xef), 0));
		assertEquals(0xc0ffee78deadbeefL, NB.decodeUInt64(padb(3, 0xc0, 0xff,
				0xee, 0x78, 0xde, 0xad, 0xbe, 0xef), 3));

		assertEquals(0x00000000ffffffffL, NB.decodeUInt64(b(0, 0, 0, 0, 0xff,
				0xff, 0xff, 0xff), 0));
		assertEquals(0x00000000ffffffffL, NB.decodeUInt64(padb(3, 0, 0, 0, 0,
				0xff, 0xff, 0xff, 0xff), 3));
		assertEquals(0xffffffffffffffffL, NB.decodeUInt64(b(0xff, 0xff, 0xff,
				0xff, 0xff, 0xff, 0xff, 0xff), 0));
		assertEquals(0xffffffffffffffffL, NB.decodeUInt64(padb(3, 0xff, 0xff,
				0xff, 0xff, 0xff, 0xff, 0xff, 0xff), 3));
	}

	@Test
	public void testEncodeInt16() {
		final byte[] out = new byte[16];

		prepareOutput(out);
		NB.encodeInt16(out, 0, 0);
		assertOutput(b(0, 0), out, 0);

		prepareOutput(out);
		NB.encodeInt16(out, 3, 0);
		assertOutput(b(0, 0), out, 3);

		prepareOutput(out);
		NB.encodeInt16(out, 0, 3);
		assertOutput(b(0, 3), out, 0);

		prepareOutput(out);
		NB.encodeInt16(out, 3, 3);
		assertOutput(b(0, 3), out, 3);

		prepareOutput(out);
		NB.encodeInt16(out, 0, 0xdeac);
		assertOutput(b(0xde, 0xac), out, 0);

		prepareOutput(out);
		NB.encodeInt16(out, 3, 0xdeac);
		assertOutput(b(0xde, 0xac), out, 3);

		prepareOutput(out);
		NB.encodeInt16(out, 3, -1);
		assertOutput(b(0xff, 0xff), out, 3);
	}

	@Test
	public void testEncodeInt32() {
		final byte[] out = new byte[16];

		prepareOutput(out);
		NB.encodeInt32(out, 0, 0);
		assertOutput(b(0, 0, 0, 0), out, 0);

		prepareOutput(out);
		NB.encodeInt32(out, 3, 0);
		assertOutput(b(0, 0, 0, 0), out, 3);

		prepareOutput(out);
		NB.encodeInt32(out, 0, 3);
		assertOutput(b(0, 0, 0, 3), out, 0);

		prepareOutput(out);
		NB.encodeInt32(out, 3, 3);
		assertOutput(b(0, 0, 0, 3), out, 3);

		prepareOutput(out);
		NB.encodeInt32(out, 0, 0xdeac);
		assertOutput(b(0, 0, 0xde, 0xac), out, 0);

		prepareOutput(out);
		NB.encodeInt32(out, 3, 0xdeac);
		assertOutput(b(0, 0, 0xde, 0xac), out, 3);

		prepareOutput(out);
		NB.encodeInt32(out, 0, 0xdeac9853);
		assertOutput(b(0xde, 0xac, 0x98, 0x53), out, 0);

		prepareOutput(out);
		NB.encodeInt32(out, 3, 0xdeac9853);
		assertOutput(b(0xde, 0xac, 0x98, 0x53), out, 3);

		prepareOutput(out);
		NB.encodeInt32(out, 3, -1);
		assertOutput(b(0xff, 0xff, 0xff, 0xff), out, 3);
	}

	@Test
	public void testEncodeInt64() {
		final byte[] out = new byte[16];

		prepareOutput(out);
		NB.encodeInt64(out, 0, 0L);
		assertOutput(b(0, 0, 0, 0, 0, 0, 0, 0), out, 0);

		prepareOutput(out);
		NB.encodeInt64(out, 3, 0L);
		assertOutput(b(0, 0, 0, 0, 0, 0, 0, 0), out, 3);

		prepareOutput(out);
		NB.encodeInt64(out, 0, 3L);
		assertOutput(b(0, 0, 0, 0, 0, 0, 0, 3), out, 0);

		prepareOutput(out);
		NB.encodeInt64(out, 3, 3L);
		assertOutput(b(0, 0, 0, 0, 0, 0, 0, 3), out, 3);

		prepareOutput(out);
		NB.encodeInt64(out, 0, 0xdeacL);
		assertOutput(b(0, 0, 0, 0, 0, 0, 0xde, 0xac), out, 0);

		prepareOutput(out);
		NB.encodeInt64(out, 3, 0xdeacL);
		assertOutput(b(0, 0, 0, 0, 0, 0, 0xde, 0xac), out, 3);

		prepareOutput(out);
		NB.encodeInt64(out, 0, 0xdeac9853L);
		assertOutput(b(0, 0, 0, 0, 0xde, 0xac, 0x98, 0x53), out, 0);

		prepareOutput(out);
		NB.encodeInt64(out, 3, 0xdeac9853L);
		assertOutput(b(0, 0, 0, 0, 0xde, 0xac, 0x98, 0x53), out, 3);

		prepareOutput(out);
		NB.encodeInt64(out, 0, 0xac431242deac9853L);
		assertOutput(b(0xac, 0x43, 0x12, 0x42, 0xde, 0xac, 0x98, 0x53), out, 0);

		prepareOutput(out);
		NB.encodeInt64(out, 3, 0xac431242deac9853L);
		assertOutput(b(0xac, 0x43, 0x12, 0x42, 0xde, 0xac, 0x98, 0x53), out, 3);

		prepareOutput(out);
		NB.encodeInt64(out, 3, -1L);
		assertOutput(b(0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff), out, 3);
	}

	private static void prepareOutput(final byte[] buf) {
		for (int i = 0; i < buf.length; i++)
			buf[i] = (byte) (0x77 + i);
	}

	private static void assertOutput(final byte[] expect, final byte[] buf,
			final int offset) {
		for (int i = 0; i < offset; i++)
			assertEquals((byte) (0x77 + i), buf[i]);
		for (int i = 0; i < expect.length; i++)
			assertEquals(expect[i], buf[offset + i]);
		for (int i = offset + expect.length; i < buf.length; i++)
			assertEquals((byte) (0x77 + i), buf[i]);
	}

	private static byte[] b(final int a, final int b) {
		return new byte[] { (byte) a, (byte) b };
	}

	private static byte[] padb(final int len, final int a, final int b) {
		final byte[] r = new byte[len + 2];
		for (int i = 0; i < len; i++)
			r[i] = (byte) 0xaf;
		r[len] = (byte) a;
		r[len + 1] = (byte) b;
		return r;
	}

	private static byte[] b(final int a, final int b, final int c, final int d) {
		return new byte[] { (byte) a, (byte) b, (byte) c, (byte) d };
	}

	private static byte[] padb(final int len, final int a, final int b,
			final int c, final int d) {
		final byte[] r = new byte[len + 4];
		for (int i = 0; i < len; i++)
			r[i] = (byte) 0xaf;
		r[len] = (byte) a;
		r[len + 1] = (byte) b;
		r[len + 2] = (byte) c;
		r[len + 3] = (byte) d;
		return r;
	}

	private static byte[] b(final int a, final int b, final int c, final int d,
			final int e, final int f, final int g, final int h) {
		return new byte[] { (byte) a, (byte) b, (byte) c, (byte) d, (byte) e,
				(byte) f, (byte) g, (byte) h };
	}

	private static byte[] padb(final int len, final int a, final int b,
			final int c, final int d, final int e, final int f, final int g,
			final int h) {
		final byte[] r = new byte[len + 8];
		for (int i = 0; i < len; i++)
			r[i] = (byte) 0xaf;
		r[len] = (byte) a;
		r[len + 1] = (byte) b;
		r[len + 2] = (byte) c;
		r[len + 3] = (byte) d;
		r[len + 4] = (byte) e;
		r[len + 5] = (byte) f;
		r[len + 6] = (byte) g;
		r[len + 7] = (byte) h;
		return r;
	}
}
