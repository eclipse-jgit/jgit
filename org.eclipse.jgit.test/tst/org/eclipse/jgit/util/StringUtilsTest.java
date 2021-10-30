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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StringUtilsTest {
	@Test
	public void testToLowerCaseChar() {
		assertEquals('a', StringUtils.toLowerCase('A'));
		assertEquals('z', StringUtils.toLowerCase('Z'));

		assertEquals('a', StringUtils.toLowerCase('a'));
		assertEquals('z', StringUtils.toLowerCase('z'));

		assertEquals((char) 0, StringUtils.toLowerCase((char) 0));
		assertEquals((char) 0xffff, StringUtils.toLowerCase((char) 0xffff));
	}

	@Test
	public void testToLowerCaseString() {
		assertEquals("\n abcdefghijklmnopqrstuvwxyz\n", StringUtils
				.toLowerCase("\n ABCDEFGHIJKLMNOPQRSTUVWXYZ\n"));
	}

	@Test
	public void testEqualsIgnoreCase1() {
		final String a = "FOO";
		assertTrue(StringUtils.equalsIgnoreCase(a, a));
	}

	@Test
	public void testEqualsIgnoreCase2() {
		assertFalse(StringUtils.equalsIgnoreCase("a", ""));
	}

	@Test
	public void testEqualsIgnoreCase3() {
		assertFalse(StringUtils.equalsIgnoreCase("a", "b"));
		assertFalse(StringUtils.equalsIgnoreCase("ac", "ab"));
	}

	@Test
	public void testEqualsIgnoreCase4() {
		assertTrue(StringUtils.equalsIgnoreCase("a", "a"));
		assertTrue(StringUtils.equalsIgnoreCase("A", "a"));
		assertTrue(StringUtils.equalsIgnoreCase("a", "A"));
	}

	@Test
	public void testReplaceLineBreaks() {
		assertEquals("a b c ",
				StringUtils.replaceLineBreaksWithSpace("a b\nc\r"));
		assertEquals("a b c ",
				StringUtils.replaceLineBreaksWithSpace("a b\nc\n"));
		assertEquals("a b c ",
				StringUtils.replaceLineBreaksWithSpace("a b\nc\r\n"));
		assertEquals("a b c d",
				StringUtils.replaceLineBreaksWithSpace("a\r\nb\nc d"));
	}

	@Test
	public void testFormatWithSuffix() {
		assertEquals("1023", StringUtils.formatWithSuffix(1023));
		assertEquals("1k", StringUtils.formatWithSuffix(1024));
		assertEquals("1025", StringUtils.formatWithSuffix(1025));
		assertEquals("1048575", StringUtils.formatWithSuffix(1024 * 1024 - 1));
		assertEquals("1m", StringUtils.formatWithSuffix(1024 * 1024));
		assertEquals("1048577", StringUtils.formatWithSuffix(1024 * 1024 + 1));
		assertEquals("1073741823",
				StringUtils.formatWithSuffix(1024 * 1024 * 1024 - 1));
		assertEquals("1g", StringUtils.formatWithSuffix(1024 * 1024 * 1024));
		assertEquals("1073741825",
				StringUtils.formatWithSuffix(1024 * 1024 * 1024 + 1));
		assertEquals("3k", StringUtils.formatWithSuffix(3 * 1024));
		assertEquals("3m", StringUtils.formatWithSuffix(3 * 1024 * 1024));
		assertEquals("2050k",
				StringUtils.formatWithSuffix(2 * 1024 * 1024 + 2048));
		assertEquals("3g",
				StringUtils.formatWithSuffix(3L * 1024 * 1024 * 1024));
		assertEquals("3000", StringUtils.formatWithSuffix(3000));
		assertEquals("3000000", StringUtils.formatWithSuffix(3_000_000));
		assertEquals("1953125k", StringUtils.formatWithSuffix(2_000_000_000));
		assertEquals("2000000010", StringUtils.formatWithSuffix(2_000_000_010));
		assertEquals("3000000000",
				StringUtils.formatWithSuffix(3_000_000_000L));
	}

	@Test
	public void testParseWithSuffix() {
		assertEquals(1024, StringUtils.parseIntWithSuffix("1k", true));
		assertEquals(1024, StringUtils.parseIntWithSuffix("1 k", true));
		assertEquals(1024, StringUtils.parseIntWithSuffix("1  k", true));
		assertEquals(1024, StringUtils.parseIntWithSuffix(" \t1  k  \n", true));
		assertEquals(1024, StringUtils.parseIntWithSuffix("1k", false));
		assertEquals(1024, StringUtils.parseIntWithSuffix("1K", false));
		assertEquals(1024 * 1024, StringUtils.parseIntWithSuffix("1m", false));
		assertEquals(1024 * 1024, StringUtils.parseIntWithSuffix("1M", false));
		assertEquals(-1024 * 1024,
				StringUtils.parseIntWithSuffix("-1M", false));
		assertEquals(1_000_000,
				StringUtils.parseIntWithSuffix("  1000000\r\n", false));
		assertEquals(1024 * 1024 * 1024,
				StringUtils.parseIntWithSuffix("1g", false));
		assertEquals(1024 * 1024 * 1024,
				StringUtils.parseIntWithSuffix("1G", false));
		assertEquals(3L * 1024 * 1024 * 1024,
				StringUtils.parseLongWithSuffix("3g", false));
		assertEquals(3L * 1024 * 1024 * 1024,
				StringUtils.parseLongWithSuffix("3G", false));
		assertThrows(NumberFormatException.class,
				() -> StringUtils.parseIntWithSuffix("2G", false));
		assertEquals(2L * 1024 * 1024 * 1024,
				StringUtils.parseLongWithSuffix("2G", false));
		assertThrows(NumberFormatException.class,
				() -> StringUtils.parseLongWithSuffix("-1m", true));
		assertThrows(NumberFormatException.class,
				() -> StringUtils.parseLongWithSuffix("-1000", true));
		assertThrows(StringIndexOutOfBoundsException.class,
				() -> StringUtils.parseLongWithSuffix("", false));
		assertThrows(StringIndexOutOfBoundsException.class,
				() -> StringUtils.parseLongWithSuffix("   \t   \n", false));
		assertThrows(StringIndexOutOfBoundsException.class,
				() -> StringUtils.parseLongWithSuffix("k", false));
		assertThrows(StringIndexOutOfBoundsException.class,
				() -> StringUtils.parseLongWithSuffix("m", false));
		assertThrows(StringIndexOutOfBoundsException.class,
				() -> StringUtils.parseLongWithSuffix("g", false));
		assertThrows(NumberFormatException.class,
				() -> StringUtils.parseLongWithSuffix("1T", false));
		assertThrows(NumberFormatException.class,
				() -> StringUtils.parseLongWithSuffix("1t", false));
		assertThrows(NumberFormatException.class,
				() -> StringUtils.parseLongWithSuffix("Nonumber", false));
		assertThrows(NumberFormatException.class,
				() -> StringUtils.parseLongWithSuffix("0x001f", false));
		assertThrows(NumberFormatException.class,
				() -> StringUtils.parseLongWithSuffix("beef", false));
		assertThrows(NumberFormatException.class,
				() -> StringUtils.parseLongWithSuffix("8000000000000000000G",
						false));
	}
}
