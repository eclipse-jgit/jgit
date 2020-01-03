/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static org.eclipse.jgit.util.Base64.decode;
import static org.eclipse.jgit.util.Base64.encodeBytes;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.lib.Constants;
import org.junit.Test;

public class Base64Test {
	@Test
	public void testEncode() {
		assertEquals("aGkK", encodeBytes(b("hi\n")));
		assertEquals("AAECDQoJcQ==", encodeBytes(b("\0\1\2\r\n\tq")));
	}

	@Test
	public void testDecode() {
		JGitTestUtil.assertEquals(b("hi\n"), decode("aGkK"));
		JGitTestUtil.assertEquals(b("\0\1\2\r\n\tq"), decode("AAECDQoJcQ=="));
		JGitTestUtil.assertEquals(b("\0\1\2\r\n\tq"),
				decode("A A E\tC D\rQ o\nJ c Q=="));
		JGitTestUtil.assertEquals(b("\u000EB"), decode("DkL="));
	}

	@Test
	public void testDecodeFail_NonBase64Character() {
		try {
			decode("! a bad base64 string !");
			fail("Accepted bad string in decode");
		} catch (IllegalArgumentException fail) {
			// Expected
		}
	}

	@Test
	public void testEncodeMatchesDecode() {
		String[] testStrings = { "", //
				"cow", //
				"a", //
				"a secret string", //
				"\0\1\2\r\n\t" //
		};
		for (String e : testStrings)
			JGitTestUtil.assertEquals(b(e), decode(encodeBytes(b(e))));
	}

	private static byte[] b(String str) {
		return Constants.encode(str);
	}

}
