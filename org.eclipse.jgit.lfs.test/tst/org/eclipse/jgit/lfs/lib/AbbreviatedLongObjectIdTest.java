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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.eclipse.jgit.lfs.test.LongObjectIdTestUtils;
import org.junit.jupiter.api.Test;

/*
 * Ported to SHA-256 from org.eclipse.jgit.lib.AbbreviatedObjectIdTest
 */
public class AbbreviatedLongObjectIdTest {
	@Test
	void testEmpty_FromByteArray() {
		AbbreviatedLongObjectId i;
		i = AbbreviatedLongObjectId.fromString(new byte[] {}, 0, 0);
		assertNotNull(i);
		assertEquals(0, i.length());
		assertFalse(i.isComplete());
		assertEquals("", i.name());
	}

	@Test
	void testEmpty_FromString() {
		AbbreviatedLongObjectId i = AbbreviatedLongObjectId
				.fromString("");
		assertNotNull(i);
		assertEquals(0, i.length());
		assertFalse(i.isComplete());
		assertEquals("", i.name());
	}

	@Test
	void testFull_FromByteArray() {
		String s = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		byte[] b = org.eclipse.jgit.lib.Constants.encodeASCII(s);
		AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(b,
				0, b.length);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertTrue(i.isComplete());
		assertEquals(s, i.name());

		LongObjectId f = i.toLongObjectId();
		assertNotNull(f);
		assertEquals(LongObjectId.fromString(s), f);
		assertEquals(f.hashCode(), i.hashCode());
	}

	@Test
	void testFull_FromString() {
		String s = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertTrue(i.isComplete());
		assertEquals(s, i.name());

		LongObjectId f = i.toLongObjectId();
		assertNotNull(f);
		assertEquals(LongObjectId.fromString(s), f);
		assertEquals(f.hashCode(), i.hashCode());
	}

	@Test
	void test1_FromString() {
		String s = "2";
		AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	void test2_FromString() {
		String s = "27";
		AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	void test3_FromString() {
		String s = "27e";
		AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	void test4_FromString() {
		String s = "27e1";
		AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	void test5_FromString() {
		String s = "27e15";
		AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	void test6_FromString() {
		String s = "27e15b";
		AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	void test7_FromString() {
		String s = "27e15b7";
		AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	void test8_FromString() {
		String s = "27e15b72";
		AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	void test9_FromString() {
		String s = "27e15b729";
		AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	void test15_FromString() {
		String s = "27e15b72937fc8f";
		AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	void test16_FromString() {
		String s = "27e15b72937fc8f5";
		AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	void test17_FromString() {
		String s = "27e15b72937fc8f55";
		AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	void test33_FromString() {
		String s = "27e15b72937fc8f558da24ac3d50ec203";
		AbbreviatedLongObjectId i = AbbreviatedLongObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toLongObjectId());
	}

	@Test
	void testEquals_Short() {
		String s = "27e15b72";
		AbbreviatedLongObjectId a = AbbreviatedLongObjectId.fromString(s);
		AbbreviatedLongObjectId b = AbbreviatedLongObjectId.fromString(s);
		assertNotSame(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertEquals(b, a);
		assertEquals(a, b);
	}

	@Test
	void testEquals_Full() {
		String s = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		AbbreviatedLongObjectId a = AbbreviatedLongObjectId.fromString(s);
		AbbreviatedLongObjectId b = AbbreviatedLongObjectId.fromString(s);
		assertNotSame(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertEquals(b, a);
		assertEquals(a, b);
	}

	@Test
	void testNotEquals_SameLength() {
		String sa = "27e15b72";
		String sb = "27e15b7f";
		AbbreviatedLongObjectId a = AbbreviatedLongObjectId
				.fromString(sa);
		AbbreviatedLongObjectId b = AbbreviatedLongObjectId
				.fromString(sb);
		assertNotEquals(a, b);
		assertNotEquals(b, a);
	}

	@Test
	void testNotEquals_DiffLength() {
		String sa = "27e15b72abcd";
		String sb = "27e15b72";
		AbbreviatedLongObjectId a = AbbreviatedLongObjectId
				.fromString(sa);
		AbbreviatedLongObjectId b = AbbreviatedLongObjectId
				.fromString(sb);
		assertNotEquals(a, b);
		assertNotEquals(b, a);
	}

	@Test
	void testPrefixCompare_Full() {
		String s1 = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		AbbreviatedLongObjectId a = AbbreviatedLongObjectId
				.fromString(s1);
		LongObjectId i1 = LongObjectId.fromString(s1);
		assertEquals(0, a.prefixCompare(i1));
		assertTrue(i1.startsWith(a));

		String s2 = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b11";
		LongObjectId i2 = LongObjectId.fromString(s2);
		assertTrue(a.prefixCompare(i2) < 0);
		assertFalse(i2.startsWith(a));

		String s3 = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b0f";
		LongObjectId i3 = LongObjectId.fromString(s3);
		assertTrue(a.prefixCompare(i3) > 0);
		assertFalse(i3.startsWith(a));
	}

	@Test
	void testPrefixCompare_1() {
		String sa = "2";
		AbbreviatedLongObjectId a = AbbreviatedLongObjectId
				.fromString(sa);

		String s1 = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		LongObjectId i1 = LongObjectId.fromString(s1);
		assertEquals(0, a.prefixCompare(i1));
		assertTrue(i1.startsWith(a));

		String s2 = "37e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		LongObjectId i2 = LongObjectId.fromString(s2);
		assertTrue(a.prefixCompare(i2) < 0);
		assertFalse(i2.startsWith(a));

		String s3 = "17e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		LongObjectId i3 = LongObjectId.fromString(s3);
		assertTrue(a.prefixCompare(i3) > 0);
		assertFalse(i3.startsWith(a));
	}

	@Test
	void testPrefixCompare_15() {
		String sa = "27e15b72937fc8f";
		AbbreviatedLongObjectId a = AbbreviatedLongObjectId
				.fromString(sa);

		String s1 = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		LongObjectId i1 = LongObjectId.fromString(s1);
		assertEquals(0, a.prefixCompare(i1));
		assertTrue(i1.startsWith(a));

		String s2 = "27e15b72937fc90558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		LongObjectId i2 = LongObjectId.fromString(s2);
		assertTrue(a.prefixCompare(i2) < 0);
		assertFalse(i2.startsWith(a));

		String s3 = "27e15b72937fc8e558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		LongObjectId i3 = LongObjectId.fromString(s3);
		assertTrue(a.prefixCompare(i3) > 0);
		assertFalse(i3.startsWith(a));
	}

	@Test
	void testPrefixCompare_16() {
		String sa = "27e15b72937fc8f5";
		AbbreviatedLongObjectId a = AbbreviatedLongObjectId
				.fromString(sa);

		String s1 = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		LongObjectId i1 = LongObjectId.fromString(s1);
		assertEquals(0, a.prefixCompare(i1));
		assertTrue(i1.startsWith(a));

		String s2 = "27e15b72937fc8f658da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		LongObjectId i2 = LongObjectId.fromString(s2);
		assertTrue(a.prefixCompare(i2) < 0);
		assertFalse(i2.startsWith(a));

		String s3 = "27e15b72937fc8f458da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		LongObjectId i3 = LongObjectId.fromString(s3);
		assertTrue(a.prefixCompare(i3) > 0);
		assertFalse(i3.startsWith(a));
	}

	@Test
	void testPrefixCompare_17() {
		String sa = "27e15b72937fc8f55";
		AbbreviatedLongObjectId a = AbbreviatedLongObjectId
				.fromString(sa);

		String s1 = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		LongObjectId i1 = LongObjectId.fromString(s1);
		assertEquals(0, a.prefixCompare(i1));
		assertTrue(i1.startsWith(a));

		String s2 = "27e15b72937fc8f568da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		LongObjectId i2 = LongObjectId.fromString(s2);
		assertTrue(a.prefixCompare(i2) < 0);
		assertFalse(i2.startsWith(a));

		String s3 = "27e15b72937fc8f548da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		LongObjectId i3 = LongObjectId.fromString(s3);
		assertTrue(a.prefixCompare(i3) > 0);
		assertFalse(i3.startsWith(a));
	}

	@Test
	void testPrefixCompare_33() {
		String sa = "27e15b72937fc8f558da24ac3d50ec203";
		AbbreviatedLongObjectId a = AbbreviatedLongObjectId
				.fromString(sa);

		String s1 = "27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10";
		LongObjectId i1 = LongObjectId.fromString(s1);
		assertEquals(0, a.prefixCompare(i1));
		assertTrue(i1.startsWith(a));

		String s2 = "27e15b72937fc8f558da24ac3d50ec20402a4cf21e33b87ae8e4ce90e89c4b10";
		LongObjectId i2 = LongObjectId.fromString(s2);
		assertTrue(a.prefixCompare(i2) < 0);
		assertFalse(i2.startsWith(a));

		String s3 = "27e15b72937fc8f558da24ac3d50ec20202a4cf21e33b87ae8e4ce90e89c4b10";
		LongObjectId i3 = LongObjectId.fromString(s3);
		assertTrue(a.prefixCompare(i3) > 0);
		assertFalse(i3.startsWith(a));
	}

	@Test
	void testIsId() {
		// These are all too short.
		assertFalse(AbbreviatedLongObjectId.isId(""));
		assertFalse(AbbreviatedLongObjectId.isId("a"));

		// These are too long.
		assertFalse(AbbreviatedLongObjectId.isId(LongObjectId.fromString(
				"27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10")
				.name() + "0"));
		assertFalse(AbbreviatedLongObjectId.isId(LongObjectId.fromString(
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
		assertTrue(AbbreviatedLongObjectId.isId(LongObjectId.fromString(
				"27e15b72937fc8f558da24ac3d50ec20302a4cf21e33b87ae8e4ce90e89c4b10")
				.name()));
	}

	@Test
	void testAbbreviate() {
		AnyLongObjectId id = LongObjectIdTestUtils.hash("test");
		assertEquals(0, id.abbreviate(10).prefixCompare(id),
				"abbreviated id should match the id it was abbreviated from");
	}

	@Test
	void testFromStringByteWrongLength() {
		byte[] buf = new byte[65];
		try {
			AbbreviatedLongObjectId.fromString(buf, 0, 65);
			fail("expected IllegalArgumentException for too long AbbreviatedLongObjectId");
		} catch (IllegalArgumentException e) {
			// expected
		}
	}

	@Test
	void testFromStringWrongLength() {
		AnyLongObjectId id = LongObjectIdTestUtils.hash("test");
		try {
			AbbreviatedLongObjectId.fromString(id.name() + "c0ffee");
			fail("expected IllegalArgumentException for too long AbbreviatedLongObjectId");
		} catch (IllegalArgumentException e) {
			// expected
		}
	}

	@Test
	void testFromLongObjectId() {
		AnyLongObjectId id = LongObjectIdTestUtils.hash("test");
		assertEquals(0,
				AbbreviatedLongObjectId.fromLongObjectId(id).prefixCompare(id));
	}

	@Test
	void testPrefixCompareByte() {
		AnyLongObjectId id = LongObjectId.fromString(
				"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
		byte[] buf = new byte[32];
		id.copyRawTo(buf, 0);

		AbbreviatedLongObjectId a = id.abbreviate(62);
		assertEquals(0, a.prefixCompare(buf, 0));

		a = LongObjectId.fromString(
				"0023456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
				.abbreviate(16);
		assertEquals(-1, a.prefixCompare(buf, 0));
		a = LongObjectId.fromString(
				"0123456789abcdef0023456789abcdef0123456789abcdef0123456789abcdef")
				.abbreviate(32);
		assertEquals(-1, a.prefixCompare(buf, 0));
		a = LongObjectId.fromString(
				"0123456789abcdef0123456789abcdef0023456789abcdef0123456789abcdef")
				.abbreviate(48);
		assertEquals(-1, a.prefixCompare(buf, 0));
		a = LongObjectId.fromString(
				"0123456789abcdef0123456789abcdef0123456789abcdef0023456789abcdef")
				.abbreviate(64);
		assertEquals(-1, a.prefixCompare(buf, 0));

		a = LongObjectId.fromString(
				"1123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
				.abbreviate(16);
		assertEquals(1, a.prefixCompare(buf, 0));
		a = LongObjectId.fromString(
				"0123456789abcdef1123456789abcdef0123456789abcdef0123456789abcdef")
				.abbreviate(32);
		assertEquals(1, a.prefixCompare(buf, 0));
		a = LongObjectId.fromString(
				"0123456789abcdef0123456789abcdef1123456789abcdef0123456789abcdef")
				.abbreviate(48);
		assertEquals(1, a.prefixCompare(buf, 0));
		a = LongObjectId.fromString(
				"0123456789abcdef0123456789abcdef0123456789abcdef1123456789abcdef")
				.abbreviate(64);
		assertEquals(1, a.prefixCompare(buf, 0));
	}

	@Test
	void testPrefixCompareLong() {
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
	void testGetFirstByte() {
		AnyLongObjectId id = LongObjectId.fromString(
				"f423456789abcdef0123456789abcdef0123456789abcdef1123456789abcdef");
		AbbreviatedLongObjectId a = id.abbreviate(10);
		assertEquals(0xf4, a.getFirstByte());
		assertEquals(id.getFirstByte(), a.getFirstByte());
	}

	@Test
	void testNotEquals() {
		AbbreviatedLongObjectId a = new LongObjectId(1L, 2L, 3L, 4L)
				.abbreviate(10);
		assertNotEquals(a, "different");
	}
}
