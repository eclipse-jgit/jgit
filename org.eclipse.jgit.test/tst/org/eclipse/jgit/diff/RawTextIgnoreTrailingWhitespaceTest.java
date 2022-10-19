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

public class RawTextIgnoreTrailingWhitespaceTest {
	private final RawTextComparator cmp = RawTextComparator.WS_IGNORE_TRAILING;

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
				.encodeASCII("foo-a\n         \n a b c\na      \n    b\n"));
		final RawText b = new RawText(Constants
				.encodeASCII("foo-a        b\n\nab  c\na\nb\n"));

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

		// "    b" != "b"
		assertNotEquals(cmp, a);
		assertNotEquals(cmp, b);
	}
}
