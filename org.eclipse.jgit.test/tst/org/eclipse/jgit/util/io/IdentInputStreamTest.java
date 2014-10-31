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

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import org.junit.Test;

/**
 * Test for {@link IdentInputStream} class.
 *
 * @author <a href="mailto:arthur.daussy@obeo.fr">Arthur Daussy</a>
 *
 */
public class IdentInputStreamTest {

	@Test
	public void testEmtyStream() throws IOException {
		final byte[] bytes = asBytes("");
		test(bytes, bytes);
	}

	@Test
	public void testNoChange() throws IOException {
		String input = "Case read they must it of cold that.\n" //
				+ " Speaking trifling an to unpacked moderate debating learning.\n" //
				+ " An particular contrasted he excellence favourable on. Nay preference dispatched difficulty continuing joy one. Songs it be if ought hoped of.\n" //
				+ " Too carriage attended him entrance desirous the saw.\n" //
				+ " Twenty sister hearts garden limits put gay has. We hill lady will both sang room by. Desirous men exercise overcame procured speaking her followed. ";
		final byte[] bytes = asBytes(input);
		test(bytes, bytes);
		test(bytes, bytes, input.length() / 10);
	}

	@Test
	public void testBasicUseCase() throws IOException {
		String input = "$Id: 3.14159265358979323846264338327950288419 $";
		String expected = "$Id$";
		test(asBytes(input), asBytes(expected));
		test(asBytes(input), asBytes(expected), input.length() / 3);
	}

	@Test
	public void testEmptyPattern() throws IOException {
		final byte[] bytes = asBytes("$Id$");
		test(bytes, asBytes("$Id$"));
	}

	@Test
	public void testNonCompletePattern() throws IOException {
		final byte[] bytes = asBytes("$Id:abcdefg");
		test(bytes, asBytes("$Id:abcdefg"));
	}

	@Test
	public void testSuccessivePattern() throws IOException {
		final byte[] bytes = asBytes("$Id:fake$Id:3.14159265358979323846264338327950288419716939937510582097494459230$");
		test(bytes,
				asBytes("$Id$Id:3.14159265358979323846264338327950288419716939937510582097494459230$"));
		// Sets the auxiliary buffer size smaller than the pattern size. It
		// should still work
		test(bytes,
				asBytes("$Id$Id:3.14159265358979323846264338327950288419716939937510582097494459230$"),
				8);
		test(bytes,
				asBytes("$Id$Id:3.14159265358979323846264338327950288419716939937510582097494459230$"),
				9);
	}

	@Test
	public void testSuccessivePattern2() throws IOException {
		final byte[] bytes = asBytes("$Id: xxfake$$Id: 3.1415926535897932384626433832795028841971693993751 $3.1415926535897932384626433832795028841971693993751");
		test(bytes,
				asBytes("$Id$$Id$3.1415926535897932384626433832795028841971693993751"));
		// Sets the auxiliary buffer size smaller than the pattern size. It
		// still should work for both pattern
		test(bytes,
				asBytes("$Id$$Id$3.1415926535897932384626433832795028841971693993751"),
				11);
		test(bytes,
				asBytes("$Id$$Id$3.1415926535897932384626433832795028841971693993751"),
				12);
	}

	@Test
	public void testSuccessivePattern3() throws IOException {
		final byte[] bytes = asBytes("$Id: xxfake3.141592653589793$$Id: 3.1415926535897932384626433832795028841971693993751 $3.1415926535897932384626433832795028841971693993751");
		test(bytes,
				asBytes("$Id$$Id$3.1415926535897932384626433832795028841971693993751"));
		// Set the memory buffer smaller than the content pattern. The stream
		// will not replace the pattern id. However it should no modified the
		// content.
		test(bytes,
				asBytes("$Id: xxfake3.141592653589793$$Id: 3.1415926535897932384626433832795028841971693993751 $3.1415926535897932384626433832795028841971693993751"),
				11, 22);
	}

	@Test
	public void testMemoryBounderies() throws IOException {
		StringBuilder contentBuilder = new StringBuilder();
		contentBuilder.append("$Id: ");
		contentBuilder.append(Strings.repeat("x",
				IdentInputStream.DEFAULT_MEMORY_BUFFER_SIZE + 1));
		String content = contentBuilder.toString();
		// No change since the content inside the pattern is bigger than the
		// memory buffer size
		// This is a limit of the current implementation
		test(asBytes(content), asBytes(content));
	}

	@Test
	public void testMemoryBounderies2() throws IOException {
		StringBuilder contentBuilder = new StringBuilder();
		contentBuilder.append("$Id:");
		contentBuilder.append(Strings.repeat("x",
				IdentInputStream.DEFAULT_MEMORY_BUFFER_SIZE - 1));
		contentBuilder.append("$");
		String content = contentBuilder.toString();
		test(asBytes(content), asBytes("$Id$"));
		test(asBytes(content), asBytes("$Id$"), content.length() / 3);
	}

	@Test
	public void testBoundaries() throws IOException {
		for (int i = IdentInputStream.DEFAULT_BUFFER_SIZE - 10; i < IdentInputStream.DEFAULT_BUFFER_SIZE + 10; i++) {
			String s1 = Strings.repeat("a", i);
			test(asBytes(s1), asBytes(s1));
		}
	}

	static String toString(java.io.InputStream is) throws IOException {
		java.util.Scanner scanner = new java.util.Scanner(is, "UTF-8");
		scanner.useDelimiter("\\A");
		try {
			String result = scanner.hasNext() ? scanner.next() : "";
			return result;
		} finally {
			scanner.close();
			is.close();
		}
	}

	private static void test(byte[] input, byte[] expected)
			throws IOException {
		test(input, expected,
				IdentInputStream.DEFAULT_BUFFER_SIZE,
				IdentInputStream.DEFAULT_MEMORY_BUFFER_SIZE);
	}

	private static void test(byte[] input, byte[] expected, int streamBuffer)
			throws IOException {
		test(input, expected, streamBuffer,
				IdentInputStream.DEFAULT_MEMORY_BUFFER_SIZE);
	}

	private static void test(byte[] input, byte[] expected,
			int streamBufferSize,
			int streamMemoryBufferSize)
			throws IOException {
		final InputStream inputStream = new ByteArrayInputStream(input);
		final InputStream identStream = new IdentInputStream(inputStream,
				false, false, streamBufferSize, streamMemoryBufferSize);
		assertEquals(toString(new ByteArrayInputStream(expected)),
				toString(identStream));


		for (int bufferSize = 1; bufferSize < 10; bufferSize++) {
			final byte[] buffer = new byte[bufferSize];
			final InputStream inputStream2 = new ByteArrayInputStream(input);
			final InputStream identStream2 = new IdentInputStream(inputStream2,
					false, false, streamBufferSize, streamMemoryBufferSize);

			int read = 0;
			for (int readNow = identStream2.read(buffer, 0, buffer.length); readNow != -1
					&& read < expected.length; readNow = identStream2.read(
					buffer, 0, buffer.length)) {
				for (int index2 = 0; index2 < readNow; index2++) {
					assertEquals(expected[read + index2], buffer[index2]);
				}
				read += readNow;
			}

			assertEquals(expected.length, read);
			identStream2.close();
		}
		identStream.close();
	}

	private static byte[] asBytes(String in) {
		try {
			return in.getBytes("UTF-8");
		} catch (UnsupportedEncodingException ex) {
			throw new AssertionError();
		}
	}
}
