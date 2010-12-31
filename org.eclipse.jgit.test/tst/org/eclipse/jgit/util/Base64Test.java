/*
 * Copyright (C) 2010, Google Inc.
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
