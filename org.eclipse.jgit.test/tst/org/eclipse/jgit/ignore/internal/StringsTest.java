/*
 * Copyright (C) 2017, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.ignore.internal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StringsTest {

	private void testString(String string, int n, int m) {
		assertEquals(string, n, Strings.count(string, '/', false));
		assertEquals(string, m, Strings.count(string, '/', true));
	}

	@Test
	public void testCount() {
		testString("", 0, 0);
		testString("/", 1, 0);
		testString("//", 2, 0);
		testString("///", 3, 1);
		testString("////", 4, 2);
		testString("foo", 0, 0);
		testString("/foo", 1, 0);
		testString("foo/", 1, 0);
		testString("/foo/", 2, 0);
		testString("foo/bar", 1, 1);
		testString("/foo/bar/", 3, 1);
		testString("/foo/bar//", 4, 2);
		testString("/foo//bar/", 4, 2);
		testString(" /foo/ ", 2, 2);
	}
}
