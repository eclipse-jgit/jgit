/*
 * Copyright (C) 2014, Arthur Daussy <arthur.daussy@obeo.fr>
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

import org.eclipse.jgit.junit.JGitTestUtil;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test class for {@link IdentOutputStream}.
 */
public class IdentOutputStreamTest {

	private static final String BLOB_NAME_VALUE = "azertyuiopqsdfghjklmwxcvbnAZERTYUIOPQSDF";

	@Test
	public void testEmptyStream() throws IOException {
		String empty = "";
		assertExpectedOutput(empty, empty);
	}

	@Test
	public void testNoChange() throws IOException {
		String noPatternText = "azertyuiopqsdfghjklmwxcvbn";
		assertExpectedOutput(noPatternText, noPatternText);
	}

	@Test
	public void testBasicUseCase() throws IOException {
		assertExpectedOutput("$Id: " + BLOB_NAME_VALUE + " $", "$Id$");
	}

	@Test
	public void testBasicUseCaseInfBuffSize() throws IOException {
		String extraText = Strings.repeat("x", 100);
		String pattern = "$Id: " + BLOB_NAME_VALUE + " $";
		assertExpectedOutput(extraText + pattern + extraText, extraText
				+ "$Id$" + extraText);
	}

	@Test
	public void testBasicUseCaseSupBuffSize() throws IOException {
		String extraText = Strings.repeat("x",
				IdentOutputStream.BUFFER_SIZE / 2);
		String pattern = "$Id: " + BLOB_NAME_VALUE + " $";
		StringBuilder input = new StringBuilder();
		input.append(extraText);
		input.append(pattern);
		input.append(extraText);
		StringBuilder expected = new StringBuilder();
		expected.append(extraText);
		expected.append("$Id$");
		expected.append(extraText);
		assertExpectedOutput(input.toString(), expected.toString());
	}

	/**
	 * Test {@link IdentOutputStream} with an input text with several "iden"
	 * pattern.
	 *
	 * @throws IOException
	 */
	@Test
	public void testNPattern() throws IOException {
		String pattern = "$Id$";
		String expectedPattern = "$Id: " + BLOB_NAME_VALUE + " $";

		StringBuilder input = new StringBuilder();
		StringBuilder expected = new StringBuilder();
		for (int separatorSize = 1; separatorSize < 11; separatorSize++) {
			String separator = Strings.repeat("x", separatorSize);
			for (int i = 1; i < 10; i++) {
				input.append(separator).append(pattern).append(separator);
				expected.append(separator).append(expectedPattern)
						.append(separator);
			}
			assertExpectedOutput(expected.toString(), input.toString());
		}
	}

	/**
	 * Inspired by {@link AutoCRLFOutputStreamTest#assertNoCrLfHelper}
	 *
	 * @param expect
	 * @param input
	 * @throws IOException
	 */
	private void assertExpectedOutput(String expect, String input)
			throws IOException {
		byte[] inbytes = input.getBytes("UTF-8");
		byte[] expectBytes = expect.getBytes("UTF-8");
		for (int i = -10; i < 10; ++i) {
			int size = Math.abs(i);
			byte[] buf = new byte[size];
			InputStream in = new ByteArrayInputStream(inbytes);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			OutputStream out = new IdentOutputStream(bos,
					BLOB_NAME_VALUE.getBytes());
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
			in.close();
			out.close();
			byte[] actualBytes = bos.toByteArray();
			Assert.assertEquals("bufsize=" + i,
					JGitTestUtil.encode(expectBytes),
					JGitTestUtil.encode(actualBytes));
		}
	}

}
