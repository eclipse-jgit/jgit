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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import junit.framework.TestCase;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.RawParseUtils;

public class RawTextTest extends TestCase {
	public void testEmpty() {
		final RawText r = new RawText(new byte[0]);
		assertEquals(0, r.size());
	}

	public void testEquals() {
		final RawText a = new RawText(Constants.encodeASCII("foo-a\nfoo-b\n"));
		final RawText b = new RawText(Constants.encodeASCII("foo-b\nfoo-c\n"));

		assertEquals(2, a.size());
		assertEquals(2, b.size());

		// foo-a != foo-b
		assertFalse(a.equals(0, b, 0));
		assertFalse(b.equals(0, a, 0));

		// foo-b == foo-b
		assertTrue(a.equals(1, b, 0));
		assertTrue(b.equals(0, a, 1));
	}

	public void testWriteLine1() throws IOException {
		final RawText a = new RawText(Constants.encodeASCII("foo-a\nfoo-b\n"));
		final ByteArrayOutputStream o = new ByteArrayOutputStream();
		a.writeLine(o, 0);
		final byte[] r = o.toByteArray();
		assertEquals("foo-a", RawParseUtils.decode(r));
	}

	public void testWriteLine2() throws IOException {
		final RawText a = new RawText(Constants.encodeASCII("foo-a\nfoo-b"));
		final ByteArrayOutputStream o = new ByteArrayOutputStream();
		a.writeLine(o, 1);
		final byte[] r = o.toByteArray();
		assertEquals("foo-b", RawParseUtils.decode(r));
	}

	public void testWriteLine3() throws IOException {
		final RawText a = new RawText(Constants.encodeASCII("a\n\nb\n"));
		final ByteArrayOutputStream o = new ByteArrayOutputStream();
		a.writeLine(o, 1);
		final byte[] r = o.toByteArray();
		assertEquals("", RawParseUtils.decode(r));
	}
}
