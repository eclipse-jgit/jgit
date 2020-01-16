/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.eclipse.jgit.util.RawCharUtil.isWhitespace;
import static org.eclipse.jgit.util.RawCharUtil.trimLeadingWhitespace;
import static org.eclipse.jgit.util.RawCharUtil.trimTrailingWhitespace;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RawCharUtilTest {

	/**
	 * Test method for {@link RawCharUtil#isWhitespace(byte)}.
	 */
	@Test
	public void testIsWhitespace() {
		for (byte c = -128; c < 127; c++) {
			switch (c) {
			case (byte) '\r':
			case (byte) '\n':
			case (byte) '\t':
			case (byte) ' ':
				assertTrue(isWhitespace(c));
				break;
			default:
				assertFalse(isWhitespace(c));
			}
		}
	}

	/**
	 * Test method for
	 * {@link RawCharUtil#trimTrailingWhitespace(byte[], int, int)}.
	 */
	@Test
	public void testTrimTrailingWhitespace() {
		assertEquals(0, trimTrailingWhitespace("".getBytes(US_ASCII), 0, 0));
		assertEquals(0, trimTrailingWhitespace(" ".getBytes(US_ASCII), 0, 1));
		assertEquals(1, trimTrailingWhitespace("a ".getBytes(US_ASCII), 0, 2));
		assertEquals(2, trimTrailingWhitespace(" a ".getBytes(US_ASCII), 0, 3));
		assertEquals(3, trimTrailingWhitespace("  a".getBytes(US_ASCII), 0, 3));
		assertEquals(6,
				trimTrailingWhitespace("  test   ".getBytes(US_ASCII), 2, 9));
	}

	/**
	 * Test method for
	 * {@link RawCharUtil#trimLeadingWhitespace(byte[], int, int)}.
	 */
	@Test
	public void testTrimLeadingWhitespace() {
		assertEquals(0, trimLeadingWhitespace("".getBytes(US_ASCII), 0, 0));
		assertEquals(1, trimLeadingWhitespace(" ".getBytes(US_ASCII), 0, 1));
		assertEquals(0, trimLeadingWhitespace("a ".getBytes(US_ASCII), 0, 2));
		assertEquals(1, trimLeadingWhitespace(" a ".getBytes(US_ASCII), 0, 3));
		assertEquals(2, trimLeadingWhitespace("  a".getBytes(US_ASCII), 0, 3));
		assertEquals(2,
				trimLeadingWhitespace("  test   ".getBytes(US_ASCII),
				2, 9));
	}

}
