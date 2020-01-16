/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static org.eclipse.jgit.util.Paths.compare;
import static org.eclipse.jgit.util.Paths.compareSameName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.junit.Test;

public class PathsTest {
	@Test
	public void testStripTrailingSeparator() {
		assertNull(Paths.stripTrailingSeparator(null));
		assertEquals("", Paths.stripTrailingSeparator(""));
		assertEquals("a", Paths.stripTrailingSeparator("a"));
		assertEquals("a/boo", Paths.stripTrailingSeparator("a/boo"));
		assertEquals("a/boo", Paths.stripTrailingSeparator("a/boo/"));
		assertEquals("a/boo", Paths.stripTrailingSeparator("a/boo//"));
		assertEquals("a/boo", Paths.stripTrailingSeparator("a/boo///"));
	}

	@Test
	public void testPathCompare() {
		byte[] a = Constants.encode("afoo/bar.c");
		byte[] b = Constants.encode("bfoo/bar.c");

		assertEquals(0, compare(a, 1, a.length, 0, b, 1, b.length, 0));
		assertEquals(-1, compare(a, 0, a.length, 0, b, 0, b.length, 0));
		assertEquals(1, compare(b, 0, b.length, 0, a, 0, a.length, 0));

		a = Constants.encode("a");
		b = Constants.encode("aa");
		assertEquals(-97, compare(a, 0, a.length, 0, b, 0, b.length, 0));
		assertEquals(0, compare(a, 0, a.length, 0, b, 0, 1, 0));
		assertEquals(0, compare(a, 0, a.length, 0, b, 1, 2, 0));
		assertEquals(0, compareSameName(a, 0, a.length, b, 1, b.length, 0));
		assertEquals(0, compareSameName(a, 0, a.length, b, 0, 1, 0));
		assertEquals(-50, compareSameName(a, 0, a.length, b, 0, b.length, 0));
		assertEquals(97, compareSameName(b, 0, b.length, a, 0, a.length, 0));

		a = Constants.encode("a");
		b = Constants.encode("a");
		assertEquals(0, compare(
				a, 0, a.length, FileMode.TREE.getBits(),
				b, 0, b.length, FileMode.TREE.getBits()));
		assertEquals(0, compare(
				a, 0, a.length, FileMode.REGULAR_FILE.getBits(),
				b, 0, b.length, FileMode.REGULAR_FILE.getBits()));
		assertEquals(-47, compare(
				a, 0, a.length, FileMode.REGULAR_FILE.getBits(),
				b, 0, b.length, FileMode.TREE.getBits()));
		assertEquals(47, compare(
				a, 0, a.length, FileMode.TREE.getBits(),
				b, 0, b.length, FileMode.REGULAR_FILE.getBits()));

		assertEquals(0, compareSameName(
				a, 0, a.length,
				b, 0, b.length, FileMode.TREE.getBits()));
		assertEquals(0, compareSameName(
				a, 0, a.length,
				b, 0, b.length, FileMode.REGULAR_FILE.getBits()));

		a = Constants.encode("a.c");
		b = Constants.encode("a");
		byte[] c = Constants.encode("a0c");
		assertEquals(-1, compare(
				a, 0, a.length, FileMode.REGULAR_FILE.getBits(),
				b, 0, b.length, FileMode.TREE.getBits()));
		assertEquals(-1, compare(
				b, 0, b.length, FileMode.TREE.getBits(),
				c, 0, c.length, FileMode.REGULAR_FILE.getBits()));
	}
}
