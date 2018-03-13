/*
 * Copyright (C) 2010, Marc Strapetz <marc.strapetz@syntevo.com>
 * Copyright (C) 2015, Ivan Motsch <ivan.motsch@bsiag.com>
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

import static org.eclipse.jgit.lib.Constants.CHARSET;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

public class AutoLFInputStreamTest {

	@Test
	public void testLF() throws IOException {
		final byte[] bytes = asBytes("1\n2\n3");
		test(bytes, bytes, false);
	}

	@Test
	public void testCR() throws IOException {
		final byte[] bytes = asBytes("1\r2\r3");
		test(bytes, bytes, false);
	}

	@Test
	public void testCRLF() throws IOException {
		test(asBytes("1\r\n2\r\n3"), asBytes("1\n2\n3"), false);
	}

	@Test
	public void testLFCR() throws IOException {
		final byte[] bytes = asBytes("1\n\r2\n\r3");
		test(bytes, bytes, false);
	}

	@Test
	public void testEmpty() throws IOException {
		final byte[] bytes = asBytes("");
		test(bytes, bytes, false);
	}

	@Test
	public void testBinaryDetect() throws IOException {
		final byte[] bytes = asBytes("1\r\n2\r\n3\0");
		test(bytes, bytes, true);
	}

	@Test
	public void testBinaryDontDetect() throws IOException {
		test(asBytes("1\r\n2\r\n3\0"), asBytes("1\n2\n3\0"), false);
	}

	private static void test(byte[] input, byte[] expected,
			boolean detectBinary) throws IOException {
		try (InputStream bis1 = new ByteArrayInputStream(input);
				InputStream cis1 = new AutoLFInputStream(bis1, detectBinary)) {
			int index1 = 0;
			for (int b = cis1.read(); b != -1; b = cis1.read()) {
				assertEquals(expected[index1], (byte) b);
				index1++;
			}

			assertEquals(expected.length, index1);

			for (int bufferSize = 1; bufferSize < 10; bufferSize++) {
				final byte[] buffer = new byte[bufferSize];
				try (InputStream bis2 = new ByteArrayInputStream(input);
						InputStream cis2 = new AutoLFInputStream(bis2,
								detectBinary)) {

					int read = 0;
					for (int readNow = cis2.read(buffer, 0,
							buffer.length); readNow != -1
									&& read < expected.length; readNow = cis2
											.read(buffer, 0, buffer.length)) {
						for (int index2 = 0; index2 < readNow; index2++) {
							assertEquals(expected[read + index2],
									buffer[index2]);
						}
						read += readNow;
					}

					assertEquals(expected.length, read);
				}
			}
		}
	}

	private static byte[] asBytes(String in) {
		return in.getBytes(CHARSET);
	}
}
