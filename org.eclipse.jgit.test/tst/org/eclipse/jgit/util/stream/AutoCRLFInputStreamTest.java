/*
 * Copyright (C) 2012, Robin Rosenberg and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util.stream;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.diff.RawText;
import org.junit.Assert;
import org.junit.Test;

public class AutoCRLFInputStreamTest {

	@Test
	public void test() throws IOException {
		assertNoCrLf("", "");
		assertNoCrLf("\r", "\r");
		assertNoCrLf("\r\n", "\n");
		assertNoCrLf("\r\n", "\r\n");
		assertNoCrLf("\r\r", "\r\r");
		assertNoCrLf("\n\r", "\n\r"); // Lone CR
		assertNoCrLf("\r\n\r\r", "\r\n\r\r");
		assertNoCrLf("\r\n\r\n", "\r\n\r\n");
		assertNoCrLf("\n\r\n\r", "\n\r\n\r"); // Lone CR
		assertNoCrLf("\0\n", "\0\n");
	}

	@Test
	public void testBoundary() throws IOException {
		int boundary = RawText.getBufferSize();
		for (int i = boundary - 10; i < boundary + 10; i++) {
			String s1 = Strings.repeat("a", i);
			assertNoCrLf(s1, s1);
			String s2 = Strings.repeat("\0", i);
			assertNoCrLf(s2, s2);
		}
	}

	private void assertNoCrLf(String string, String string2) throws IOException {
		assertNoCrLfHelper(string, string2);
		// \u00e5 = LATIN SMALL LETTER A WITH RING ABOVE
		// the byte value is negative
		assertNoCrLfHelper("\u00e5" + string, "\u00e5" + string2);
		assertNoCrLfHelper("\u00e5" + string + "\u00e5", "\u00e5" + string2
				+ "\u00e5");
		assertNoCrLfHelper(string + "\u00e5", string2 + "\u00e5");
	}

	private void assertNoCrLfHelper(String expect, String input)
			throws IOException {
		byte[] inbytes = input.getBytes(UTF_8);
		byte[] expectBytes = expect.getBytes(UTF_8);
		for (int i = 0; i < 5; ++i) {
			byte[] buf = new byte[i];
			try (ByteArrayInputStream bis = new ByteArrayInputStream(inbytes);
					InputStream in = new AutoCRLFInputStream(bis, true);
					ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				if (i > 0) {
					int n;
					while ((n = in.read(buf)) >= 0) {
						out.write(buf, 0, n);
					}
				} else {
					int c;
					while ((c = in.read()) != -1)
						out.write(c);
				}
				out.flush();
				byte[] actualBytes = out.toByteArray();
				Assert.assertEquals("bufsize=" + i, encode(expectBytes),
						encode(actualBytes));
			}
		}
	}

	String encode(byte[] in) {
		StringBuilder str = new StringBuilder();
		for (byte b : in) {
			if (b < 32)
				str.append(0xFF & b);
			else {
				str.append("'");
				str.append((char) b);
				str.append("'");
			}
			str.append(' ');
		}
		return str.toString();
	}

}
