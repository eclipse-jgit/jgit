/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com>
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

package org.eclipse.jgit.lfs.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.eclipse.jgit.lfs.test.LongObjectIdTestUtils;
import org.junit.Test;

/*
 * Ported to SHA-256 from org.eclipse.jgit.lib.AbbreviatedObjectIdTest
 */
public class AbbreviatedLongObjectIdTest {
	@Test
	public void testEmpty_FromByteArray() {
		final AbbreviatedLongObjectId i;
		i = AbbreviatedLongObjectId.fromString(new byte[] {}, 0, 0);
		assertNotNull(i);
		assertEquals(0, i.length());
		assertFalse(i.isComplete());
		assertEquals("", i.name());
	}

	@Test
	public void testEmpty_FromString() {
		final AbbreviatedLongObjectId i = AbbreviatedLongObjectId
				.fromString("");
		assertNotNull(i);
		assertEquals(0, i.length());
		assertFalse(i.isComplete());
		assertEquals("", i.name());
	}

	@Test
	public void testFull_FromByteArray() {
		final String s = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		final byte[] b = org.eclipse.jgit.lib.Constants.encodeASCII(s);
		final AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(b,
				0, b.length);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertTrue(i.isComplete());
		assertEquals(s, i.name());

		final LongObjectId f = i.toLongObjectId();
		assertNotNull(f);
		assertEquals(LongObjectId.fromString(s), f);
		assertEquals(f.hashCode(), i.hashCode());
	}

	@Test
	public void testFull_FromString() {
		final String s = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		final AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertTrue(i.isComplete());
		assertEquals(s, i.name());

		final LongObjectId f = i.toLongObjectId();
		assertNotNull(f);
		assertEquals(LongObjectId.fromString(s), f);
		assertEquals(f.hashCode(), i.hashCode());
	}

	@Test
	public void test1_FromString() {
		final String s = "2";
		final AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	public void test2_FromString() {
		final String s = "27";
		final AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	public void test3_FromString() {
		final String s = "27e";
		final AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	public void test4_FromString() {
		final String s = "27e1";
		final AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	public void test5_FromString() {
		final String s = "27e15";
		final AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	public void test6_FromString() {
		final String s = "27e15b";
		final AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	public void test7_FromString() {
		final String s = "27e15b7";
		final AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	public void test8_FromString() {
		final String s = "27e15b72";
		final AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	public void test9_FromString() {
		final String s = "27e15b729";
		final AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	public void test15_FromString() {
		final String s = "27e15b72937fc8f";
		final AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	public void test16_FromString() {
		final String s = "27e15b72937fc8f5";
		final AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	public void test17_FromString() {
		final String s = "27e15b72937fc8f55";
		final AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	public void test33_FromString() {
		final String s = "27e15b72937fc8f558da24ac3d50ec203";
		final AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	public void testEquals_Short() {
		final String s = "27e15b72";
		final AbbreviatedLongObjectId a = AbbreviatedLongObjectId.fromString(s);
		final AbbreviatedLongObjectId b = AbbreviatedLongObjectId.fromString(s);
		assertNotSame(a, b);
		assertTrue(a.hashCode() == b.hashCode());
		assertEquals(b, a);
		assertEquals(a, b);
	}

	@Test
	public void testEquals_Full() {
		final String s = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		final AbbreviatedLongObjectId a = AbbreviatedLongObjectId.fromString(s);
		final AbbreviatedLongObjectId b = AbbreviatedLongObjectId.fromString(s);
		assertNotSame(a, b);
		assertTrue(a.hashCode() == b.hashCode());
		assertEquals(b, a);
		assertEquals(a, b);
	}

	@Test
	public void testNotEquals_SameLength() {
		final String sa = "27e15b72";
		final String sb = "27e15b7f";
		final AbbreviatedLongObjectId a = AbbreviatedLongObjectId
				.fromString(sa);
		final AbbreviatedLongObjectId b = AbbreviatedLongObjectId
				.fromString(sb);
		assertFalse(a.equals(b));
		assertFalse(b.equals(a));
	}

	@Test
	public void testNotEquals_DiffLength() {
		final String sa = "27e15b72abcd";
		final String sb = "27e15b72";
		final AbbreviatedLongObjectId a = AbbreviatedLongObjectId
				.fromString(sa);
		final AbbreviatedLongObjectId b = AbbreviatedLongObjectId
				.fromString(sb);
		assertFalse(a.equals(b));
		assertFalse(b.equals(a));
	}

	@Test
	public void testPrefixCompare_Full() {
		final String s1 = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		final AbbreviatedLongObjectId a = AbbreviatedLongObjectId
				.fromString(s1);
		final LongObjectId i1 = LongObjectId.fromString(s1);
		assertEquals(0, a.prefixCompare(i1));
		assertTrue(i1.startsWith(a));

		final String s2 = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b11";
		final LongObjectId i2 = LongObjectId.fromString(s2);
		assertTrue(a.prefixCompare(i2) < 0);
		assertFalse(i2.startsWith(a));

		final String s3 = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b0f";
		final LongObjectId i3 = LongObjectId.fromString(s3);
		assertTrue(a.prefixCompare(i3) > 0);
		assertFalse(i3.startsWith(a));
	}

	@Test
	public void testPrefixCompare_1() {
		final String sa = "2";
		final AbbreviatedLongObjectId a = AbbreviatedLongObjectId
				.fromString(sa);

		final String s1 = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		final LongObjectId i1 = LongObjectId.fromString(s1);
		assertEquals(0, a.prefixCompare(i1));
		assertTrue(i1.startsWith(a));

		final String s2 = "37e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		final LongObjectId i2 = LongObjectId.fromString(s2);
		assertTrue(a.prefixCompare(i2) < 0);
		assertFalse(i2.startsWith(a));

		final String s3 = "17e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		final LongObjectId i3 = LongObjectId.fromString(s3);
		assertTrue(a.prefixCompare(i3) > 0);
		assertFalse(i3.startsWith(a));
	}

	@Test
	public void testPrefixCompare_15() {
		final String sa = "27e15b72937fc8f";
		final AbbreviatedLongObjectId a = AbbreviatedLongObjectId
				.fromString(sa);

		final String s1 = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		final LongObjectId i1 = LongObjectId.fromString(s1);
		assertEquals(0, a.prefixCompare(i1));
		assertTrue(i1.startsWith(a));

		final String s2 = "27e15b72937fc90558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		final LongObjectId i2 = LongObjectId.fromString(s2);
		assertTrue(a.prefixCompare(i2) < 0);
		assertFalse(i2.startsWith(a));

		final String s3 = "27e15b72937fc8e558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		final LongObjectId i3 = LongObjectId.fromString(s3);
		assertTrue(a.prefixCompare(i3) > 0);
		assertFalse(i3.startsWith(a));
	}

	@Test
	public void testPrefixCompare_16() {
		final String sa = "27e15b72937fc8f5";
		final AbbreviatedLongObjectId a = AbbreviatedLongObjectId
				.fromString(sa);

		final String s1 = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		final LongObjectId i1 = LongObjectId.fromString(s1);
		assertEquals(0, a.prefixCompare(i1));
		assertTrue(i1.startsWith(a));

		final String s2 = "27e15b72937fc8f658da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		final LongObjectId i2 = LongObjectId.fromString(s2);
		assertTrue(a.prefixCompare(i2) < 0);
		assertFalse(i2.startsWith(a));

		final String s3 = "27e15b72937fc8f458da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		final LongObjectId i3 = LongObjectId.fromString(s3);
		assertTrue(a.prefixCompare(i3) > 0);
		assertFalse(i3.startsWith(a));
	}

	@Test
	public void testPrefixCompare_17() {
		final String sa = "27e15b72937fc8f55";
		final AbbreviatedLongObjectId a = AbbreviatedLongObjectId
				.fromString(sa);

		final String s1 = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		final LongObjectId i1 = LongObjectId.fromString(s1);
		assertEquals(0, a.prefixCompare(i1));
		assertTrue(i1.startsWith(a));

		final String s2 = "27e15b72937fc8f568da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		final LongObjectId i2 = LongObjectId.fromString(s2);
		assertTrue(a.prefixCompare(i2) < 0);
		assertFalse(i2.startsWith(a));

		final String s3 = "27e15b72937fc8f548da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		final LongObjectId i3 = LongObjectId.fromString(s3);
		assertTrue(a.prefixCompare(i3) > 0);
		assertFalse(i3.startsWith(a));
	}

	@Test
	public void testPrefixCompare_33() {
		final String sa = "27e15b72937fc8f558da24ac3d50ec203";
		final AbbreviatedLongObjectId a = AbbreviatedLongObjectId
				.fromString(sa);

		final String s1 = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		final LongObjectId i1 = LongObjectId.fromString(s1);
		assertEquals(0, a.prefixCompare(i1));
		assertTrue(i1.startsWith(a));

		final String s2 = "27e15b72937fc8f558da24ac3d50ec20402a4cf21e33b87ae8e4ce90e89c4b10";
		final LongObjectId i2 = LongObjectId.fromString(s2);
		assertTrue(a.prefixCompare(i2) < 0);
		assertFalse(i2.startsWith(a));

		final String s3 = "27e15b72937fc8f558da24ac3d50ec20202a4cf21e33b87ae8e4ce90e89c4b10";
		final LongObjectId i3 = LongObjectId.fromString(s3);
		assertTrue(a.prefixCompare(i3) > 0);
		assertFalse(i3.startsWith(a));
	}

	@Test
	public void testIsId() {
		// These are all too short.
		assertFalse(AbbreviatedLongObjectId.isId(""));
		assertFalse(AbbreviatedLongObjectId.isId("a"));

		// These are too long.
		assertFalse(AbbreviatedLongObjectId.isId(LongObjectId
				.fromString(
						"27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10")
				.name() + "0"));
		assertFalse(AbbreviatedLongObjectId.isId(LongObjectId
				.fromString(
						"27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10")
				.name() + "c0ffee"));

		// These contain non-hex characters.
		assertFalse(AbbreviatedLongObjectId.isId("01notahexstring"));

		// These should all work.
		assertTrue(AbbreviatedLongObjectId.isId("ab"));
		assertTrue(AbbreviatedLongObjectId.isId("abc"));
		assertTrue(AbbreviatedLongObjectId.isId("abcd"));
		assertTrue(AbbreviatedLongObjectId.isId("abcd0"));
		assertTrue(AbbreviatedLongObjectId.isId("abcd09"));
		assertTrue(AbbreviatedLongObjectId.isId(LongObjectId
				.fromString(
						"27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10")
				.name()));
	}

	@Test
	public void testAbbreviate() {
		AnyLongObjectId id = LongObjectIdTestUtils.hash("test");
		assertEquals(
				"abbreviated id should match the id it was abbreviated from", 0,
				id.abbreviate(10).prefixCompare(id));
	}

	@Test
	public void testFromStringByteWrongLength() {
		byte[] buf = new byte[65];
		try {
			AbbreviatedLongObjectId.fromString(buf, 0, 65);
			fail("expected IllegalArgumentException for too long AbbreviatedLongObjectId");
		} catch (IllegalArgumentException e) {
			// expected
		}
	}

	@Test
	public void testFromStringWrongLength() {
		AnyLongObjectId id = LongObjectIdTestUtils.hash("test");
		try {
			AbbreviatedLongObjectId.fromString(id.name() + "c0ffee");
			fail("expected IllegalArgumentException for too long AbbreviatedLongObjectId");
		} catch (IllegalArgumentException e) {
			// expected
		}
	}

	@Test
	public void testFromLongObjectId() {
		AnyLongObjectId id = LongObjectIdTestUtils.hash("test");
		assertEquals(0,
				AbbreviatedLongObjectId.fromLongObjectId(id).prefixCompare(id));
	}

	@Test
	public void testPrefixCompareByte() {
		AnyLongObjectId id = LongObjectId.fromString(
				"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
		byte[] buf = new byte[32];
		id.copyRawTo(buf, 0);

		AbbreviatedLongObjectId a = id.abbreviate(62);
		assertEquals(0, a.prefixCompare(buf, 0));

		a = LongObjectId
				.fromString(
						"0023456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
				.abbreviate(16);
		assertEquals(-1, a.prefixCompare(buf, 0));
		a = LongObjectId
				.fromString(
						"0123456789abcdef0023456789abcdef0123456789abcdef0123456789abcdef")
				.abbreviate(32);
		assertEquals(-1, a.prefixCompare(buf, 0));
		a = LongObjectId
				.fromString(
						"0123456789abcdef0123456789abcdef0023456789abcdef0123456789abcdef")
				.abbreviate(48);
		assertEquals(-1, a.prefixCompare(buf, 0));
		a = LongObjectId
				.fromString(
						"0123456789abcdef0123456789abcdef0123456789abcdef0023456789abcdef")
				.abbreviate(64);
		assertEquals(-1, a.prefixCompare(buf, 0));

		a = LongObjectId
				.fromString(
						"1123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
				.abbreviate(16);
		assertEquals(1, a.prefixCompare(buf, 0));
		a = LongObjectId
				.fromString(
						"0123456789abcdef1123456789abcdef0123456789abcdef0123456789abcdef")
				.abbreviate(32);
		assertEquals(1, a.prefixCompare(buf, 0));
		a = LongObjectId
				.fromString(
						"0123456789abcdef0123456789abcdef1123456789abcdef0123456789abcdef")
				.abbreviate(48);
		assertEquals(1, a.prefixCompare(buf, 0));
		a = LongObjectId
				.fromString(
						"0123456789abcdef0123456789abcdef0123456789abcdef1123456789abcdef")
				.abbreviate(64);
		assertEquals(1, a.prefixCompare(buf, 0));
	}

	@Test
	public void testPrefixCompareLong() {
		AnyLongObjectId id = new LongObjectId(1L, 2L, 3L, 4L);
		long[] buf = new long[4];
		id.copyRawTo(buf, 0);

		AbbreviatedLongObjectId a = id.abbreviate(62);
		assertEquals(0, a.prefixCompare(buf, 0));

		a = new LongObjectId(0L, 2L, 3L, 4L).abbreviate(16);
		assertEquals(-1, a.prefixCompare(buf, 0));
		a = new LongObjectId(1L, 1L, 3L, 4L).abbreviate(32);
		assertEquals(-1, a.prefixCompare(buf, 0));
		a = new LongObjectId(1L, 2L, 2L, 4L).abbreviate(48);
		assertEquals(-1, a.prefixCompare(buf, 0));
		a = new LongObjectId(1L, 2L, 3L, 3L).abbreviate(64);
		assertEquals(-1, a.prefixCompare(buf, 0));

		a = new LongObjectId(2L, 2L, 3L, 4L).abbreviate(16);
		assertEquals(1, a.prefixCompare(buf, 0));
		a = new LongObjectId(1L, 3L, 3L, 4L).abbreviate(32);
		assertEquals(1, a.prefixCompare(buf, 0));
		a = new LongObjectId(1L, 2L, 4L, 4L).abbreviate(48);
		assertEquals(1, a.prefixCompare(buf, 0));
		a = new LongObjectId(1L, 2L, 3L, 5L).abbreviate(64);
		assertEquals(1, a.prefixCompare(buf, 0));
	}

	@Test
	public void testGetFirstByte() {
		AnyLongObjectId id = LongObjectId.fromString(
				"f423456789abcdef0123456789abcdef0123456789abcdef1123456789abcdef");
		AbbreviatedLongObjectId a = id.abbreviate(10);
		assertEquals(0xf4, a.getFirstByte());
		assertEquals(id.getFirstByte(), a.getFirstByte());
	}

	@Test
	public void testNotEquals() {
		AbbreviatedLongObjectId a = new LongObjectId(1L, 2L, 3L, 4L)
				.abbreviate(10);
		assertFalse(a.equals("different"));
	}
}
