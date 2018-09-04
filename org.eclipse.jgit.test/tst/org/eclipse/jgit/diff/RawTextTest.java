/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2009, Johannes E. Schindelin <johannes.schindelin@gmx.de>
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

package org.eclipse.jgit.diff;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Test;

public class RawTextTest {
	@Test
	public void testEmpty() {
		final RawText r = new RawText(new byte[0]);
		assertEquals(0, r.size());
	}

	@Test
	public void testBinary() {
		String input = "foo-a\nf\0o-b\n";
		byte[] data = Constants.encodeASCII(input);
		final RawText a = new RawText(data);
		assertArrayEquals(a.content, data);
		assertEquals(a.size(), 1);
		assertEquals(a.getString(0, 1, false), input);
	}

	@Test
	public void testEquals() {
		final RawText a = new RawText(Constants.encodeASCII("foo-a\nfoo-b\n"));
		final RawText b = new RawText(Constants.encodeASCII("foo-b\nfoo-c\n"));
		RawTextComparator cmp = RawTextComparator.DEFAULT;

		assertEquals(2, a.size());
		assertEquals(2, b.size());

		// foo-a != foo-b
		assertFalse(cmp.equals(a, 0, b, 0));
		assertFalse(cmp.equals(b, 0, a, 0));

		// foo-b == foo-b
		assertTrue(cmp.equals(a, 1, b, 0));
		assertTrue(cmp.equals(b, 0, a, 1));
	}

	@Test
	public void testWriteLine1() throws IOException {
		final RawText a = new RawText(Constants.encodeASCII("foo-a\nfoo-b\n"));
		final ByteArrayOutputStream o = new ByteArrayOutputStream();
		a.writeLine(o, 0);
		final byte[] r = o.toByteArray();
		assertEquals("foo-a", RawParseUtils.decode(r));
	}

	@Test
	public void testWriteLine2() throws IOException {
		final RawText a = new RawText(Constants.encodeASCII("foo-a\nfoo-b"));
		final ByteArrayOutputStream o = new ByteArrayOutputStream();
		a.writeLine(o, 1);
		final byte[] r = o.toByteArray();
		assertEquals("foo-b", RawParseUtils.decode(r));
	}

	@Test
	public void testWriteLine3() throws IOException {
		final RawText a = new RawText(Constants.encodeASCII("a\n\nb\n"));
		final ByteArrayOutputStream o = new ByteArrayOutputStream();
		a.writeLine(o, 1);
		final byte[] r = o.toByteArray();
		assertEquals("", RawParseUtils.decode(r));
	}

	@Test
	public void testComparatorReduceCommonStartEnd() {
		final RawTextComparator c = RawTextComparator.DEFAULT;
		Edit e;

		e = c.reduceCommonStartEnd(t(""), t(""), new Edit(0, 0, 0, 0));
		assertEquals(new Edit(0, 0, 0, 0), e);

		e = c.reduceCommonStartEnd(t("a"), t("b"), new Edit(0, 1, 0, 1));
		assertEquals(new Edit(0, 1, 0, 1), e);

		e = c.reduceCommonStartEnd(t("a"), t("a"), new Edit(0, 1, 0, 1));
		assertEquals(new Edit(1, 1, 1, 1), e);

		e = c.reduceCommonStartEnd(t("axB"), t("axC"), new Edit(0, 3, 0, 3));
		assertEquals(new Edit(2, 3, 2, 3), e);

		e = c.reduceCommonStartEnd(t("Bxy"), t("Cxy"), new Edit(0, 3, 0, 3));
		assertEquals(new Edit(0, 1, 0, 1), e);

		e = c.reduceCommonStartEnd(t("bc"), t("Abc"), new Edit(0, 2, 0, 3));
		assertEquals(new Edit(0, 0, 0, 1), e);

		e = new Edit(0, 5, 0, 5);
		e = c.reduceCommonStartEnd(t("abQxy"), t("abRxy"), e);
		assertEquals(new Edit(2, 3, 2, 3), e);

		RawText a = new RawText("p\na b\nQ\nc d\n".getBytes(UTF_8));
		RawText b = new RawText("p\na  b \nR\n c  d \n".getBytes(UTF_8));
		e = new Edit(0, 4, 0, 4);
		e = RawTextComparator.WS_IGNORE_ALL.reduceCommonStartEnd(a, b, e);
		assertEquals(new Edit(2, 3, 2, 3), e);
	}

	@Test
	public void testComparatorReduceCommonStartEnd_EmptyLine() {
		RawText a;
		RawText b;
		Edit e;

		a = new RawText("R\n y\n".getBytes(UTF_8));
		b = new RawText("S\n\n y\n".getBytes(UTF_8));
		e = new Edit(0, 2, 0, 3);
		e = RawTextComparator.DEFAULT.reduceCommonStartEnd(a, b, e);
		assertEquals(new Edit(0, 1, 0, 2), e);

		a = new RawText("S\n\n y\n".getBytes(UTF_8));
		b = new RawText("R\n y\n".getBytes(UTF_8));
		e = new Edit(0, 3, 0, 2);
		e = RawTextComparator.DEFAULT.reduceCommonStartEnd(a, b, e);
		assertEquals(new Edit(0, 2, 0, 1), e);
	}

	@Test
	public void testComparatorReduceCommonStartButLastLineNoEol() {
		RawText a;
		RawText b;
		Edit e;
		a = new RawText("start".getBytes(UTF_8));
		b = new RawText("start of line".getBytes(UTF_8));
		e = new Edit(0, 1, 0, 1);
		e = RawTextComparator.DEFAULT.reduceCommonStartEnd(a, b, e);
		assertEquals(new Edit(0, 1, 0, 1), e);
	}

	@Test
	public void testComparatorReduceCommonStartButLastLineNoEol_2() {
		RawText a;
		RawText b;
		Edit e;
		a = new RawText("start".getBytes(UTF_8));
		b = new RawText("start of\nlastline".getBytes(UTF_8));
		e = new Edit(0, 1, 0, 2);
		e = RawTextComparator.DEFAULT.reduceCommonStartEnd(a, b, e);
		assertEquals(new Edit(0, 1, 0, 2), e);
	}

	@Test
	public void testLineDelimiter() throws Exception {
		RawText rt = new RawText(Constants.encodeASCII("foo\n"));
		assertEquals("\n", rt.getLineDelimiter());
		assertFalse(rt.isMissingNewlineAtEnd());
		rt = new RawText(Constants.encodeASCII("foo\r\n"));
		assertEquals("\r\n", rt.getLineDelimiter());
		assertFalse(rt.isMissingNewlineAtEnd());

		rt = new RawText(Constants.encodeASCII("foo\nbar"));
		assertEquals("\n", rt.getLineDelimiter());
		assertTrue(rt.isMissingNewlineAtEnd());
		rt = new RawText(Constants.encodeASCII("foo\r\nbar"));
		assertEquals("\r\n", rt.getLineDelimiter());
		assertTrue(rt.isMissingNewlineAtEnd());

		rt = new RawText(Constants.encodeASCII("foo\nbar\r\n"));
		assertEquals("\n", rt.getLineDelimiter());
		assertFalse(rt.isMissingNewlineAtEnd());
		rt = new RawText(Constants.encodeASCII("foo\r\nbar\n"));
		assertEquals("\r\n", rt.getLineDelimiter());
		assertFalse(rt.isMissingNewlineAtEnd());

		rt = new RawText(Constants.encodeASCII("foo"));
		assertNull(rt.getLineDelimiter());
		assertTrue(rt.isMissingNewlineAtEnd());

		rt = new RawText(Constants.encodeASCII(""));
		assertNull(rt.getLineDelimiter());
		assertTrue(rt.isMissingNewlineAtEnd());

		rt = new RawText(Constants.encodeASCII("\n"));
		assertEquals("\n", rt.getLineDelimiter());
		assertFalse(rt.isMissingNewlineAtEnd());

		rt = new RawText(Constants.encodeASCII("\r\n"));
		assertEquals("\r\n", rt.getLineDelimiter());
		assertFalse(rt.isMissingNewlineAtEnd());
	}

	@Test
	public void testLineDelimiter2() throws Exception {
		RawText rt = new RawText(Constants.encodeASCII("\nfoo"));
		assertEquals("\n", rt.getLineDelimiter());
		assertTrue(rt.isMissingNewlineAtEnd());
	}

	private static RawText t(String text) {
		StringBuilder r = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			r.append(text.charAt(i));
			r.append('\n');
		}
		return new RawText(r.toString().getBytes(UTF_8));
	}
}
