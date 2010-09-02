/*
 * Copyright (C) 2009-2010, Google Inc.
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

import org.eclipse.jgit.lib.Constants;

import junit.framework.TestCase;

public class RawTextIgnoreWhitespaceChangeTest extends TestCase {
	private final RawTextComparator cmp = RawTextComparator.WS_IGNORE_CHANGE;

	public void testEqualsWithoutWhitespace() {
		final RawText a = new RawText(cmp, Constants
				.encodeASCII("foo-a\nfoo-b\nfoo\n"));
		final RawText b = new RawText(cmp, Constants
				.encodeASCII("foo-b\nfoo-c\nf\n"));

		assertEquals(3, a.size());
		assertEquals(3, b.size());

		// foo-a != foo-b
		assertFalse(cmp.equals(a, 0, b, 0));
		assertFalse(cmp.equals(b, 0, a, 0));

		// foo-b == foo-b
		assertTrue(cmp.equals(a, 1, b, 0));
		assertTrue(cmp.equals(b, 0, a, 1));

		// foo != f
		assertFalse(cmp.equals(a, 2, b, 2));
		assertFalse(cmp.equals(b, 2, a, 2));
	}

	public void testEqualsWithWhitespace() {
		final RawText a = new RawText(cmp, Constants
				.encodeASCII("foo-a\n         \n a b c\na      \n  foo\na  b  c\n"));
		final RawText b = new RawText(cmp, Constants
				.encodeASCII("foo-a        b\n\nab  c\na\nfoo\na b     c  \n"));

		// "foo-a" != "foo-a        b"
		assertFalse(cmp.equals(a, 0, b, 0));
		assertFalse(cmp.equals(b, 0, a, 0));

		// "         " == ""
		assertTrue(cmp.equals(a, 1, b, 1));
		assertTrue(cmp.equals(b, 1, a, 1));

		// " a b c" != "ab  c"
		assertFalse(cmp.equals(a, 2, b, 2));
		assertFalse(cmp.equals(b, 2, a, 2));

		// "a      " == "a"
		assertTrue(cmp.equals(a, 3, b, 3));
		assertTrue(cmp.equals(b, 3, a, 3));

		// "  foo" != "foo"
		assertFalse(cmp.equals(a, 4, b, 4));
		assertFalse(cmp.equals(b, 4, a, 4));

		// "a  b  c" == "a b     c  "
		assertTrue(cmp.equals(a, 5, b, 5));
		assertTrue(cmp.equals(b, 5, a, 5));
	}
}
