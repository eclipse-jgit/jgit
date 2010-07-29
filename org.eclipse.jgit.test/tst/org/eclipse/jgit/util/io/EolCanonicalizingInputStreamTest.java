/*
 * Copyright (C) 2010, Marc Strapetz <marc.strapetz@syntevo.com>
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
import java.io.IOException;

import junit.framework.TestCase;

public class EolCanonicalizingInputStreamTest extends TestCase {

	public void testLF() throws IOException {
		final byte[] bytes = new byte[] { (byte)1, (byte)'\n', (byte)2, (byte)'\n', (byte)3};
		test(bytes, bytes);
	}

	public void testCR() throws IOException {
		final byte[] input = new byte[] { (byte)1, (byte)'\r', (byte)2, (byte)'\r', (byte)3};
		final byte[] expected = new byte[] { (byte)1, (byte)'\r', (byte)2, (byte)'\r', (byte)3};
		test(input, expected);
	}

	public void testCRLF() throws IOException {
		final byte[] input = new byte[] { (byte)1, (byte)'\r', (byte)'\n', (byte)2, (byte)'\r', (byte)'\n', (byte)3};
		final byte[] expected = new byte[] { (byte)1, (byte)'\n', (byte)2, (byte)'\n', (byte)3};
		test(input, expected);
	}

	public void testLFCR() throws IOException {
		final byte[] input = new byte[] { (byte)1, (byte)'\n', (byte)'\r', (byte)2, (byte)'\n', (byte)'\r', (byte)3};
		final byte[] expected = new byte[] { (byte)1, (byte)'\n', (byte)'\r', (byte)2, (byte)'\n', (byte)'\r', (byte)3};
		test(input, expected);
	}

	private void test(byte[] input, byte[] expected) throws IOException {
		final ByteArrayInputStream bis1 = new ByteArrayInputStream(input);
		final EolCanonicalizingInputStream cis1 = new EolCanonicalizingInputStream(bis1);
		int index1 = 0;
		for (int b = cis1.read(); b != -1; b = cis1.read()) {
			assertEquals(expected[index1], (byte)b);
			index1++;
		}

		assertEquals(expected.length, index1);

		for (int bufferSize = 1; bufferSize < 10; bufferSize++) {
			final byte[] buffer = new byte[bufferSize];
			final ByteArrayInputStream bis2 = new ByteArrayInputStream(input);
			final EolCanonicalizingInputStream cis2 = new EolCanonicalizingInputStream(bis2);

			int read = 0;
			for (int readNow = cis2.read(buffer, 0, buffer.length); readNow != -1 && read < expected.length; readNow = cis2.read(buffer, 0, buffer.length)) {
				for (int index2 = 0; index2 < readNow; index2++) {
					assertEquals(expected[read + index2], buffer[index2]);
				}
				read += readNow;
			}

			assertEquals(expected.length, read);
		}
	}
}
