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
}
