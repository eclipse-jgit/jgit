/*
 * Copyright (C) 2021 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

/**
 * Tests for {@link Base85}.
 */
public class Base85Test {

	private static final String VALID_CHARS = "0123456789"
			+ "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
			+ "!#$%&()*+-;<=>?@^_`{|}~";

	@Test
	public void testChars() {
		for (int i = 0; i < 256; i++) {
			byte[] testData = { '1', '2', '3', '4', (byte) i };
			if (VALID_CHARS.indexOf(i) >= 0) {
				byte[] decoded = Base85.decode(testData, 4);
				assertNotNull(decoded);
			} else {
				assertThrows(IllegalArgumentException.class,
						() -> Base85.decode(testData, 4));
			}
		}
	}

	private void roundtrip(byte[] data, int expectedLength) {
		byte[] encoded = Base85.encode(data);
		assertEquals(expectedLength, encoded.length);
		assertArrayEquals(data, Base85.decode(encoded, data.length));
	}

	private void roundtrip(String data, int expectedLength) {
		roundtrip(data.getBytes(StandardCharsets.US_ASCII), expectedLength);
	}

	@Test
	public void testPadding() {
		roundtrip("", 0);
		roundtrip("a", 5);
		roundtrip("ab", 5);
		roundtrip("abc", 5);
		roundtrip("abcd", 5);
		roundtrip("abcde", 10);
		roundtrip("abcdef", 10);
		roundtrip("abcdefg", 10);
		roundtrip("abcdefgh", 10);
		roundtrip("abcdefghi", 15);
	}

	@Test
	public void testBinary() {
		roundtrip(new byte[] { 1 }, 5);
		roundtrip(new byte[] { 1, 2 }, 5);
		roundtrip(new byte[] { 1, 2, 3 }, 5);
		roundtrip(new byte[] { 1, 2, 3, 4 }, 5);
		roundtrip(new byte[] { 1, 2, 3, 4, 5 }, 10);
		roundtrip(new byte[] { 1, 2, 3, 4, 5, 0, 0, 0 }, 10);
		roundtrip(new byte[] { 1, 2, 3, 4, 0, 0, 0, 5 }, 10);
	}

	@Test
	public void testOverflow() {
		IllegalArgumentException e = assertThrows(
				IllegalArgumentException.class,
				() -> Base85.decode(new byte[] { '~', '~', '~', '~', '~' }, 4));
		assertTrue(e.getMessage().contains("overflow"));
	}
}
