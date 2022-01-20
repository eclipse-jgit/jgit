/*
 * Copyright (C) 2020, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util.stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.junit.Test;

public class AutoLFOutputStreamTest {

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
	public void testCRLFNoDetect() throws IOException {
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

	@Test
	public void testCrLfDetect() throws IOException {
		byte[] bytes = asBytes("1\r\n2\n3\r\n\r");
		test(bytes, bytes, true);
	}

	private static void test(byte[] input, byte[] expected,
			boolean detectBinary) throws IOException {
		try (ByteArrayOutputStream result = new ByteArrayOutputStream();
				OutputStream out = new AutoLFOutputStream(result,
						detectBinary)) {
			out.write(input);
			out.close();
			assertArrayEquals(expected, result.toByteArray());
		}
	}

	private static byte[] asBytes(String in) {
		return in.getBytes(UTF_8);
	}
}
