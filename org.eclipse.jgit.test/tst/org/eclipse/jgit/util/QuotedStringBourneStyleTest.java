/*
 * Copyright (C) 2008, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static org.eclipse.jgit.util.QuotedString.BOURNE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;

import org.eclipse.jgit.lib.Constants;
import org.junit.Test;

public class QuotedStringBourneStyleTest {
	private static void assertQuote(String in, String exp) {
		final String r = BOURNE.quote(in);
		assertNotSame(in, r);
		assertFalse(in.equals(r));
		assertEquals('\'' + exp + '\'', r);
	}

	private static void assertDequote(String exp, String in) {
		final byte[] b = Constants.encode('\'' + in + '\'');
		final String r = BOURNE.dequote(b, 0, b.length);
		assertEquals(exp, r);
	}

	@Test
	public void testQuote_Empty() {
		assertEquals("''", BOURNE.quote(""));
	}

	@Test
	public void testDequote_Empty1() {
		assertEquals("", BOURNE.dequote(new byte[0], 0, 0));
	}

	@Test
	public void testDequote_Empty2() {
		assertEquals("", BOURNE.dequote(new byte[] { '\'', '\'' }, 0, 2));
	}

	@Test
	public void testDequote_SoleSq() {
		assertEquals("", BOURNE.dequote(new byte[] { '\'' }, 0, 1));
	}

	@Test
	public void testQuote_BareA() {
		assertQuote("a", "a");
	}

	@Test
	public void testDequote_BareA() {
		final String in = "a";
		final byte[] b = Constants.encode(in);
		assertEquals(in, BOURNE.dequote(b, 0, b.length));
	}

	@Test
	public void testDequote_BareABCZ_OnlyBC() {
		final String in = "abcz";
		final byte[] b = Constants.encode(in);
		final int p = in.indexOf('b');
		assertEquals("bc", BOURNE.dequote(b, p, p + 2));
	}

	@Test
	public void testDequote_LoneBackslash() {
		assertDequote("\\", "\\");
	}

	@Test
	public void testQuote_NamedEscapes() {
		assertQuote("'", "'\\''");
		assertQuote("!", "'\\!'");

		assertQuote("a'b", "a'\\''b");
		assertQuote("a!b", "a'\\!'b");
	}

	@Test
	public void testDequote_NamedEscapes() {
		assertDequote("'", "'\\''");
		assertDequote("!", "'\\!'");

		assertDequote("a'b", "a'\\''b");
		assertDequote("a!b", "a'\\!'b");
	}
}
