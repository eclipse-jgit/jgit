/*
 * Copyright (C) 2014, Andrey Loskutov <loskutov@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.ignore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.ignore.internal.Strings;
import org.junit.Test;

public class BasicRuleTest {

	@Test
	public void test() {
		FastIgnoreRule rule1 = new FastIgnoreRule("/hello/[a]/");
		FastIgnoreRule rule2 = new FastIgnoreRule("/hello/[a]/");
		FastIgnoreRule rule3 = new FastIgnoreRule("!/hello/[a]/");
		FastIgnoreRule rule4 = new FastIgnoreRule("/hello/[a]");
		assertTrue(rule1.dirOnly());
		assertTrue(rule3.dirOnly());
		assertFalse(rule4.dirOnly());
		assertFalse(rule1.getNegation());
		assertTrue(rule3.getNegation());
		assertNotEquals(rule1, null);
		assertEquals(rule1, rule1);
		assertEquals(rule1, rule2);
		assertNotEquals(rule1, rule3);
		assertNotEquals(rule1, rule4);
		assertEquals(rule1.hashCode(), rule2.hashCode());
		assertNotEquals(rule1.hashCode(), rule3.hashCode());
		assertEquals(rule1.toString(), rule2.toString());
		assertNotEquals(rule1.toString(), rule3.toString());
	}

	@Test
	public void testDirectoryPattern() {
		assertTrue(Strings.isDirectoryPattern("/"));
		assertTrue(Strings.isDirectoryPattern("/ "));
		assertTrue(Strings.isDirectoryPattern("/     "));
		assertFalse(Strings.isDirectoryPattern("     "));
		assertFalse(Strings.isDirectoryPattern(""));
	}

	@Test
	public void testStripTrailingChar() {
		assertEquals("", Strings.stripTrailing("/", '/'));
		assertEquals("", Strings.stripTrailing("///", '/'));
		assertEquals("a", Strings.stripTrailing("a/", '/'));
		assertEquals("a", Strings.stripTrailing("a///", '/'));
		assertEquals("a/ ", Strings.stripTrailing("a/ ", '/'));
	}

	@Test
	public void testStripTrailingWhitespace() {
		assertEquals("", Strings.stripTrailingWhitespace(""));
		assertEquals("", Strings.stripTrailingWhitespace("   "));
		assertEquals("a", Strings.stripTrailingWhitespace("a"));
		assertEquals("a", Strings.stripTrailingWhitespace("a "));
		assertEquals("a", Strings.stripTrailingWhitespace("a  "));
		assertEquals("a", Strings.stripTrailingWhitespace("a \t"));
	}
}
