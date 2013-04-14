/*
 * Copyright (C) 2011, 2013 Robin Rosenberg
 * Copyright (C) 2013 Robin Stocker
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

package org.eclipse.jgit.util.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
		assertNoCrLf("\r\n\r", "\n\r");
		assertNoCrLf("\r\n\r\r", "\r\n\r\r");
		assertNoCrLf("\r\n\r\n", "\r\n\r\n");
		assertNoCrLf("\r\n\r\n\r", "\n\r\n\r");
	}

	@Test
	public void testBoundary() throws IOException {
		assertBoundaryCorrect(AutoCRLFOutputStream.BUFFER_SIZE - 5);
		assertBoundaryCorrect(AutoCRLFOutputStream.BUFFER_SIZE - 4);
		assertBoundaryCorrect(AutoCRLFOutputStream.BUFFER_SIZE - 3);
		assertBoundaryCorrect(AutoCRLFOutputStream.BUFFER_SIZE - 2);
		assertBoundaryCorrect(AutoCRLFOutputStream.BUFFER_SIZE - 1);
		assertBoundaryCorrect(AutoCRLFOutputStream.BUFFER_SIZE);
		assertBoundaryCorrect(AutoCRLFOutputStream.BUFFER_SIZE + 1);
		assertBoundaryCorrect(AutoCRLFOutputStream.BUFFER_SIZE + 2);
		assertBoundaryCorrect(AutoCRLFOutputStream.BUFFER_SIZE + 3);
		assertBoundaryCorrect(AutoCRLFOutputStream.BUFFER_SIZE + 4);
		assertBoundaryCorrect(AutoCRLFOutputStream.BUFFER_SIZE + 5);
	}

	private void assertBoundaryCorrect(int size) throws IOException {
		StringBuilder sb = new StringBuilder(size);
		for (int i = 0; i < size; i++)
			sb.append('a');
		String s = sb.toString();
		assertNoCrLf(s, s);
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
		byte[] inbytes = input.getBytes();
		byte[] expectBytes = expect.getBytes();
		for (int i = 0; i < 5; ++i) {
			byte[] buf = new byte[i];
			InputStream in = new ByteArrayInputStream(inbytes);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			OutputStream out = new AutoCRLFOutputStream(bos);
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
			in.close();
			out.close();
			byte[] actualBytes = bos.toByteArray();
			Assert.assertEquals("bufsize=" + i, encode(expectBytes),
					encode(actualBytes));
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
