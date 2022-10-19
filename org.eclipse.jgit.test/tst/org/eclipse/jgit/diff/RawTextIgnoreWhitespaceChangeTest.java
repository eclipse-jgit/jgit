/*
 * Copyright (C) 2009-2010, Google Inc.
 * Copyright (C) 2009, Johannes E. Schindelin <johannes.schindelin@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jgit.lib.Constants;
import org.junit.jupiter.api.Test;

public class RawTextIgnoreWhitespaceChangeTest {
	private final RawTextComparator cmp = RawTextComparator.WS_IGNORE_CHANGE;

	@Test
	void testEqualsWithoutWhitespace() {
		final RawText a = new RawText(Constants
				.encodeASCII("foo-a\nfoo-b\nfoo\n"));
		final RawText b = new RawText(Constants
				.encodeASCII("foo-b\nfoo-c\nf\n"));

		assertEquals(3, a.size());
		assertEquals(3, b.size());

		// foo-a != foo-b
		assertNotEquals(cmp, a);
		assertNotEquals(cmp, b);

		// foo-b == foo-b
		assertTrue(cmp.equals(a, 1, b, 0));
		assertTrue(cmp.equals(b, 0, a, 1));

		// foo != f
		assertNotEquals(cmp, a);
		assertNotEquals(cmp, b);
	}

	@Test
	void testEqualsWithWhitespace() {
		final RawText a = new RawText(Constants
				.encodeASCII("foo-a\n         \n a b c\na      \n  foo\na  b  c\n"));
		final RawText b = new RawText(Constants
				.encodeASCII("foo-a        b\n\nab  c\na\nfoo\na b     c  \n"));

		// "foo-a" != "foo-a        b"
		assertNotEquals(cmp, a);
		assertNotEquals(cmp, b);

		// "         " == ""
		assertTrue(cmp.equals(a, 1, b, 1));
		assertTrue(cmp.equals(b, 1, a, 1));

		// " a b c" != "ab  c"
		assertNotEquals(cmp, a);
		assertNotEquals(cmp, b);

		// "a      " == "a"
		assertTrue(cmp.equals(a, 3, b, 3));
		assertTrue(cmp.equals(b, 3, a, 3));

		// "  foo" != "foo"
		assertNotEquals(cmp, a);
		assertNotEquals(cmp, b);

		// "a  b  c" == "a b     c  "
		assertTrue(cmp.equals(a, 5, b, 5));
		assertTrue(cmp.equals(b, 5, a, 5));
	}

	@Test
	void testEqualsWithTabs() {
		RawText a = new RawText(
				Constants.encodeASCII("a\tb\t \na\tb\t c \n  foo\na b\na  b"));
		RawText b = new RawText(
				Constants.encodeASCII("a b \na b c\n\tfoo\nab\na \tb"));

		// "a\tb\t \n" == "a b \n"
		assertTrue(cmp.equals(a, 0, b, 0));
		assertTrue(cmp.equals(b, 0, a, 0));

		// "a\tb\t c \n" == "a b c\n"
		assertTrue(cmp.equals(a, 1, b, 1));
		assertTrue(cmp.equals(b, 1, a, 1));

		// " foo" == "\tfoo"
		assertTrue(cmp.equals(a, 2, b, 2));
		assertTrue(cmp.equals(b, 2, a, 2));

		// "a b" != "ab"
		assertNotEquals(cmp, a);
		assertNotEquals(cmp, b);

		// "a b" == "a \tb "
		assertTrue(cmp.equals(a, 4, b, 4));
		assertTrue(cmp.equals(b, 4, a, 4));
	}

	@Test
	void testHashCode() {
		RawText a = new RawText(Constants
				.encodeASCII("a b  c\n\nab  c d  \n\ta bc d\nxyz\na  b  c"));
		RawText b = new RawText(Constants.encodeASCII(
				"a b  c\na   b c\nab  c d\na bc d\n  \t a bc d\na b c\n"));

		// Same line gives equal hash
		assertEquals(cmp.hash(a, 0), cmp.hash(a, 0));

		// Empty lines produce the same hash
		assertEquals(cmp.hash(a, 1), cmp.hash(a, 1));

		// Equal lines from different RawTexts get the same hash (RawText
		// instance is not part of the hash)
		assertEquals(cmp.hash(a, 0), cmp.hash(b, 0));

		// A blank produces the same hash as a TAB
		assertEquals(cmp.hash(new RawText(Constants.encodeASCII(" ")), 0),
				cmp.hash(new RawText(Constants.encodeASCII("\t")), 0));

		// Lines with only differing whitespace produce same hash
		assertEquals(cmp.hash(a, 0), cmp.hash(b, 1));

		// Lines with different trailing whitespace produce the same hash
		assertEquals(cmp.hash(a, 2), cmp.hash(b, 2));

		// A line with leading whitespace produces a hash different from the
		// same line without leading whitespace
		assertNotEquals(cmp.hash(a, 3), cmp.hash(b, 3));

		// Lines with different leading whitespace produce equal hashes
		assertEquals(cmp.hash(a, 3), cmp.hash(b, 4));

		// While different lines _should_ produce different hashes, that may not
		// always be the case. But for these two lines, it is.
		assertNotEquals(cmp.hash(a, 4), cmp.hash(b, 4));

		// A line without trailing \n produces the same hash as one without
		assertEquals(cmp.hash(a, 5), cmp.hash(b, 5));

	}
}
