/*
 * Copyright (C) 2011, 2013 Robin Rosenberg
 * Copyright (C) 2013 Robin Stocker and others
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
import java.io.OutputStream;

import org.eclipse.jgit.diff.RawText;
import org.junit.Assert;
import org.junit.Test;

public class AutoCRLFOutputStreamTest {

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
		assertNoCrLf("\n\r\n\r", "\n\r\n\r");
		assertNoCrLf("\0\n", "\0\n");
	}

	@Test
	public void testBoundary() throws IOException {
		int bufferSize = RawText.getBufferSize();
		for (int i = bufferSize - 10; i < bufferSize + 10; i++) {
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
		for (int i = -4; i < 5; ++i) {
			int size = Math.abs(i);
			byte[] buf = new byte[size];
			try (InputStream in = new ByteArrayInputStream(inbytes);
					ByteArrayOutputStream bos = new ByteArrayOutputStream();
					OutputStream out = new AutoCRLFOutputStream(bos)) {
				if (i > 0) {
					int n;
					while ((n = in.read(buf)) >= 0) {
						out.write(buf, 0, n);
					}
				} else if (i < 0) {
					int n;
					while ((n = in.read(buf)) >= 0) {
						byte[] b = new byte[n];
						System.arraycopy(buf, 0, b, 0, n);
						out.write(b);
					}
				} else {
					int c;
					while ((c = in.read()) != -1)
						out.write(c);
				}
				out.flush();
				byte[] actualBytes = bos.toByteArray();
				Assert.assertEquals("bufsize=" + size, encode(expectBytes),
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
