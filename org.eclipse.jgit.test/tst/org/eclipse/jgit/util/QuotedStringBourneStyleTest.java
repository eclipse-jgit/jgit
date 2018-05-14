/*
 * Copyright (C) 2008, Google Inc.
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
