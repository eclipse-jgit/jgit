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
		test(bytes, bytes, false);
	}

	@Test
	public void testNoChange() throws IOException {
		String input = "Case read they must it of cold that.\n Speaking trifling an to unpacked moderate debating learning.\n An particular contrasted he excellence favourable on. Nay preference dispatched difficulty continuing joy one. Songs it be if ought hoped of.\n Too carriage attended him entrance desirous the saw.\n Twenty sister hearts garden limits put gay has. We hill lady will both sang room by. Desirous men exercise overcame procured speaking her followed. ";
		final byte[] bytes = asBytes(input);
		test(bytes, bytes, false);
	}

	@Test
	public void testBasicUseCase() throws IOException {
		test(asBytes("$Id: fakeId0000000000000000000000000000000000 $"),
				asBytes("$Id$"), false);
	}

	@Test
	public void testIncorrectBlobName() throws IOException {
		final byte[] bytes = asBytes("$Id:fakeId$kkkk");
		test(bytes, bytes, false);
	}

	@Test
	public void testNonWorkingNestedPattern() throws IOException {
		final byte[] bytes = asBytes("$Id:fake$Id:$");
		test(bytes, bytes, false);
	}

	@Test
	public void testNonWorkingNestedPattern2() throws IOException {
		final byte[] bytes = asBytes("$Id:fake$Id:xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx$");
		test(bytes, bytes, false);
	}

	@Test
	public void testWorkingIncludingNestedPattern() throws IOException {
		String blobName = "xxfake$Id: xxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
		final byte[] bytes = asBytes("$Id: " + blobName + " $xxxxx");
		test(bytes, asBytes("$Id$xxxxx"), false);
	}

	@Test
	public void testWorkingIncludedNestedPattern() throws IOException {
		String blobName = "$Id: xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx $";
		final byte[] bytes = asBytes("$Id: xxfake" + blobName + "xxxxxx");
		test(bytes, asBytes("$Id: xxfake$Id$xxxxxx"), false);
	}

	@Test
	public void testBoundary() throws IOException {
		for (int i = IdentInputStream.BUFFER_SIZE - 10; i < IdentInputStream.BUFFER_SIZE + 10; i++) {
			String s1 = Strings.repeat("a", i);
			test(asBytes(s1), asBytes(s1), true);
		}
	}

	private static void test(byte[] input, byte[] expected, boolean detectBinary)
			throws IOException {
		final InputStream inputStream = new ByteArrayInputStream(input);
		final InputStream identStream = new IdentInputStream(inputStream,
				detectBinary);
		int index1 = 0;
		for (int b = identStream.read(); b != -1; b = identStream.read()) {
			assertEquals(new Character((char) expected[index1]), new Character(
					(char) b));
			index1++;
		}

		assertEquals(expected.length, index1);

		for (int bufferSize = 1; bufferSize < 10; bufferSize++) {
			final byte[] buffer = new byte[bufferSize];
			final InputStream inputStream2 = new ByteArrayInputStream(input);
			final InputStream identStream2 = new IdentInputStream(inputStream2,
					detectBinary);

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
