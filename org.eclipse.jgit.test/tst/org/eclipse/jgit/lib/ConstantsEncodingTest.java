/*
 * Copyright (C) 2008, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.UnsupportedEncodingException;

import org.junit.jupiter.api.Test;

public class ConstantsEncodingTest {
	@Test
	void testEncodeASCII_SimpleASCII()
			throws UnsupportedEncodingException {
		final String src = "abc";
		final byte[] exp = {'a', 'b', 'c'};
		final byte[] res = Constants.encodeASCII(src);
		assertArrayEquals(exp, res);
		assertEquals(src, new String(res, 0, res.length, "UTF-8"));
	}

	@Test
	void testEncodeASCII_FailOnNonASCII() {
		final String src = "Ūnĭcōde̽";
		try {
			Constants.encodeASCII(src);
			fail("Incorrectly accepted a Unicode character");
		} catch (IllegalArgumentException err) {
			assertEquals("Not ASCII string: " + src, err.getMessage());
		}
	}

	@Test
	void testEncodeASCII_Number13() {
		final long src = 13;
		final byte[] exp = {'1', '3'};
		final byte[] res = Constants.encodeASCII(src);
		assertArrayEquals(exp, res);
	}

	@Test
	void testEncode_SimpleASCII() throws UnsupportedEncodingException {
		final String src = "abc";
		final byte[] exp = {'a', 'b', 'c'};
		final byte[] res = Constants.encode(src);
		assertArrayEquals(exp, res);
		assertEquals(src, new String(res, 0, res.length, "UTF-8"));
	}

	@Test
	void testEncode_Unicode() throws UnsupportedEncodingException {
		final String src = "Ūnĭcōde̽";
		final byte[] exp = {(byte) 0xC5, (byte) 0xAA, 0x6E, (byte) 0xC4,
				(byte) 0xAD, 0x63, (byte) 0xC5, (byte) 0x8D, 0x64, 0x65,
				(byte) 0xCC, (byte) 0xBD};
		final byte[] res = Constants.encode(src);
		assertArrayEquals(exp, res);
		assertEquals(src, new String(res, 0, res.length, "UTF-8"));
	}
}
