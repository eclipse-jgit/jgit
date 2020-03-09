/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2020 Michael Dardis and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static org.eclipse.jgit.util.Hex.decode;
import static org.eclipse.jgit.util.Hex.toHexString;
import static org.junit.Assert.assertEquals;

import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.lib.Constants;
import org.junit.Test;

public class HexTest {
	@Test
	public void testEncode() {
		assertEquals("68690a", toHexString(b("hi\n")));
		assertEquals("0001020d0a0971", toHexString(b("\0\1\2\r\n\tq")));
	}

	@Test
	public void testDecode() {
		JGitTestUtil.assertEquals(b("hi\n"), decode("68690a"));
		JGitTestUtil.assertEquals(b("\0\1\2\r\n\tq"), decode("0001020d0a0971"));
		JGitTestUtil.assertEquals(b("\u000EB"), decode("0E42"));
	}

	@Test
	public void testEncodeMatchesDecode() {
		String[] testStrings = { "", "cow", "a", "a secret string",
				"\0\1\2\r\n\t" };
		for (String e : testStrings) {
			JGitTestUtil.assertEquals(b(e), decode(toHexString(b(e))));
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIllegal() {
		decode("0011test00");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIllegal2() {
		decode("0123456789abcdefgh");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIllegal3() {
		decode("0123456789abcdef-_+*");
	}

	@Test
	public void testLegal() {
		decode("0123456789abcdef");
	}

	@Test
	public void testLegal2() {
		decode("deadbeef");
	}

	private static byte[] b(String str) {
		return Constants.encode(str);
	}

}
