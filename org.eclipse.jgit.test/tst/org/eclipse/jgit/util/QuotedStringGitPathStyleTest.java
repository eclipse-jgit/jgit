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

import static org.eclipse.jgit.util.QuotedString.GIT_PATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.io.UnsupportedEncodingException;

import org.eclipse.jgit.lib.Constants;
import org.junit.Test;

public class QuotedStringGitPathStyleTest {
	private static void assertQuote(final String exp, final String in) {
		final String r = GIT_PATH.quote(in);
		assertNotSame(in, r);
		assertFalse(in.equals(r));
		assertEquals('"' + exp + '"', r);
	}

	private static void assertDequote(final String exp, final String in) {
		final byte[] b;
		try {
			b = ('"' + in + '"').getBytes("ISO-8859-1");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		final String r = GIT_PATH.dequote(b, 0, b.length);
		assertEquals(exp, r);
	}

	@Test
	public void testQuote_Empty() {
		assertEquals("\"\"", GIT_PATH.quote(""));
	}

	@Test
	public void testDequote_Empty1() {
		assertEquals("", GIT_PATH.dequote(new byte[0], 0, 0));
	}

	@Test
	public void testDequote_Empty2() {
		assertEquals("", GIT_PATH.dequote(new byte[] { '"', '"' }, 0, 2));
	}

	@Test
	public void testDequote_SoleDq() {
		assertEquals("\"", GIT_PATH.dequote(new byte[] { '"' }, 0, 1));
	}

	@Test
	public void testQuote_BareA() {
		final String in = "a";
		assertSame(in, GIT_PATH.quote(in));
	}

	@Test
	public void testDequote_BareA() {
		final String in = "a";
		final byte[] b = Constants.encode(in);
		assertEquals(in, GIT_PATH.dequote(b, 0, b.length));
	}

	@Test
	public void testDequote_BareABCZ_OnlyBC() {
		final String in = "abcz";
		final byte[] b = Constants.encode(in);
		final int p = in.indexOf('b');
		assertEquals("bc", GIT_PATH.dequote(b, p, p + 2));
	}

	@Test
	public void testDequote_LoneBackslash() {
		assertDequote("\\", "\\");
	}

	@Test
	public void testQuote_NamedEscapes() {
		assertQuote("\\a", "\u0007");
		assertQuote("\\b", "\b");
		assertQuote("\\f", "\f");
		assertQuote("\\n", "\n");
		assertQuote("\\r", "\r");
		assertQuote("\\t", "\t");
		assertQuote("\\v", "\u000B");
		assertQuote("\\\\", "\\");
		assertQuote("\\\"", "\"");
	}

	@Test
	public void testDequote_NamedEscapes() {
		assertDequote("\u0007", "\\a");
		assertDequote("\b", "\\b");
		assertDequote("\f", "\\f");
		assertDequote("\n", "\\n");
		assertDequote("\r", "\\r");
		assertDequote("\t", "\\t");
		assertDequote("\u000B", "\\v");
		assertDequote("\\", "\\\\");
		assertDequote("\"", "\\\"");
	}

	@Test
	public void testDequote_OctalAll() {
		for (int i = 0; i < 127; i++) {
			assertDequote("" + (char) i, octalEscape(i));
		}
		for (int i = 128; i < 256; i++) {
			int f = 0xC0 | (i >> 6);
			int s = 0x80 | (i & 0x3f);
			assertDequote("" + (char) i, octalEscape(f)+octalEscape(s));
		}
	}

	private String octalEscape(int i) {
		String s = Integer.toOctalString(i);
		while (s.length() < 3) {
			s = "0" + s;
		}
		return "\\"+s;
	}

	@Test
	public void testQuote_OctalAll() {
		assertQuote("\\001", "\1");
		assertQuote("\\177", "\u007f");
		assertQuote("\\303\\277", "\u00ff"); // \u00ff in UTF-8
	}

	@Test
	public void testDequote_UnknownEscapeQ() {
		assertDequote("\\q", "\\q");
	}

	@Test
	public void testDequote_FooTabBar() {
		assertDequote("foo\tbar", "foo\\tbar");
	}

	@Test
	public void testDequote_Latin1() {
		assertDequote("\u00c5ngstr\u00f6m", "\\305ngstr\\366m"); // Latin1
	}

	@Test
	public void testDequote_UTF8() {
		assertDequote("\u00c5ngstr\u00f6m", "\\303\\205ngstr\\303\\266m");
	}

	@Test
	public void testDequote_RawUTF8() {
		assertDequote("\u00c5ngstr\u00f6m", "\303\205ngstr\303\266m");
	}

	@Test
	public void testDequote_RawLatin1() {
		assertDequote("\u00c5ngstr\u00f6m", "\305ngstr\366m");
	}

	@Test
	public void testQuote_Ang() {
		assertQuote("\\303\\205ngstr\\303\\266m", "\u00c5ngstr\u00f6m");
	}

	@Test
	public void testQuoteAtAndNumber() {
		assertSame("abc@2x.png", GIT_PATH.quote("abc@2x.png"));
		assertDequote("abc@2x.png", "abc\\1002x.png");
	}
}
