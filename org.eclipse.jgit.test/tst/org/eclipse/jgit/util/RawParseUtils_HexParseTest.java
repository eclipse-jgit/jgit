/*
 * Copyright (C) 2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.eclipse.jgit.lib.Constants;
import org.junit.Test;

public class RawParseUtils_HexParseTest {
	@Test
	public void testInt4_1() {
		assertEquals(0, RawParseUtils.parseHexInt4((byte) '0'));
		assertEquals(1, RawParseUtils.parseHexInt4((byte) '1'));
		assertEquals(2, RawParseUtils.parseHexInt4((byte) '2'));
		assertEquals(3, RawParseUtils.parseHexInt4((byte) '3'));
		assertEquals(4, RawParseUtils.parseHexInt4((byte) '4'));
		assertEquals(5, RawParseUtils.parseHexInt4((byte) '5'));
		assertEquals(6, RawParseUtils.parseHexInt4((byte) '6'));
		assertEquals(7, RawParseUtils.parseHexInt4((byte) '7'));
		assertEquals(8, RawParseUtils.parseHexInt4((byte) '8'));
		assertEquals(9, RawParseUtils.parseHexInt4((byte) '9'));
		assertEquals(10, RawParseUtils.parseHexInt4((byte) 'a'));
		assertEquals(11, RawParseUtils.parseHexInt4((byte) 'b'));
		assertEquals(12, RawParseUtils.parseHexInt4((byte) 'c'));
		assertEquals(13, RawParseUtils.parseHexInt4((byte) 'd'));
		assertEquals(14, RawParseUtils.parseHexInt4((byte) 'e'));
		assertEquals(15, RawParseUtils.parseHexInt4((byte) 'f'));

		assertEquals(10, RawParseUtils.parseHexInt4((byte) 'A'));
		assertEquals(11, RawParseUtils.parseHexInt4((byte) 'B'));
		assertEquals(12, RawParseUtils.parseHexInt4((byte) 'C'));
		assertEquals(13, RawParseUtils.parseHexInt4((byte) 'D'));
		assertEquals(14, RawParseUtils.parseHexInt4((byte) 'E'));
		assertEquals(15, RawParseUtils.parseHexInt4((byte) 'F'));

		assertNotHex('q');
		assertNotHex(' ');
		assertNotHex('.');
	}

	private static void assertNotHex(char c) {
		try {
			RawParseUtils.parseHexInt4((byte) c);
			fail("Incorrectly acccepted " + c);
		} catch (ArrayIndexOutOfBoundsException e) {
			// pass
		}
	}

	@Test
	public void testInt16() {
		assertEquals(0x0000, parse16("0000"));
		assertEquals(0x0001, parse16("0001"));
		assertEquals(0x1234, parse16("1234"));
		assertEquals(0xdead, parse16("dead"));
		assertEquals(0xBEEF, parse16("BEEF"));
		assertEquals(0x4321, parse16("4321"));
		assertEquals(0xffff, parse16("ffff"));

		try {
			parse16("noth");
			fail("Incorrectly acccepted \"noth\"");
		} catch (ArrayIndexOutOfBoundsException e) {
			// pass
		}

		try {
			parse16("01");
			fail("Incorrectly acccepted \"01\"");
		} catch (ArrayIndexOutOfBoundsException e) {
			// pass
		}

		try {
			parse16("000.");
			fail("Incorrectly acccepted \"000.\"");
		} catch (ArrayIndexOutOfBoundsException e) {
			// pass
		}
	}

	private static int parse16(final String str) {
		return RawParseUtils.parseHexInt16(Constants.encodeASCII(str), 0);
	}

	@Test
	public void testInt32() {
		assertEquals(0x00000000, parse32("00000000"));
		assertEquals(0x00000001, parse32("00000001"));
		assertEquals(0xc0ffEE42, parse32("c0ffEE42"));
		assertEquals(0xffffffff, parse32("ffffffff"));
		assertEquals(-1, parse32("ffffffff"));

		try {
			parse32("noth");
			fail("Incorrectly acccepted \"noth\"");
		} catch (ArrayIndexOutOfBoundsException e) {
			// pass
		}

		try {
			parse32("notahexs");
			fail("Incorrectly acccepted \"notahexs\"");
		} catch (ArrayIndexOutOfBoundsException e) {
			// pass
		}

		try {
			parse32("01");
			fail("Incorrectly acccepted \"01\"");
		} catch (ArrayIndexOutOfBoundsException e) {
			// pass
		}

		try {
			parse32("0000000.");
			fail("Incorrectly acccepted \"0000000.\"");
		} catch (ArrayIndexOutOfBoundsException e) {
			// pass
		}
	}

	private static int parse32(final String str) {
		return RawParseUtils.parseHexInt32(Constants.encodeASCII(str), 0);
	}
}
