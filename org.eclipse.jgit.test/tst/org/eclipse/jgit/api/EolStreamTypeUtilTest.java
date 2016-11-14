/*
 * Copyright (C) 2015, Ivan Motsch <ivan.motsch@bsiag.com>
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

package org.eclipse.jgit.api;

import static org.eclipse.jgit.lib.CoreConfig.EolStreamType.AUTO_CRLF;
import static org.eclipse.jgit.lib.CoreConfig.EolStreamType.AUTO_LF;
import static org.eclipse.jgit.lib.CoreConfig.EolStreamType.DIRECT;
import static org.eclipse.jgit.lib.CoreConfig.EolStreamType.TEXT_CRLF;
import static org.eclipse.jgit.lib.CoreConfig.EolStreamType.TEXT_LF;
import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.eclipse.jgit.lib.CoreConfig.EolStreamType;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.io.EolStreamTypeUtil;
import org.junit.Test;

/**
 * Unit tests for end-of-line conversion streams
 */
public class EolStreamTypeUtilTest {

	@Test
	public void testCheckoutDirect() throws Exception {
		testCheckout(DIRECT, DIRECT, "", "");
		testCheckout(DIRECT, DIRECT, "\r", "\r");
		testCheckout(DIRECT, DIRECT, "\n", "\n");

		testCheckout(DIRECT, DIRECT, "\r\n", "\r\n");
		testCheckout(DIRECT, DIRECT, "\n\r", "\n\r");

		testCheckout(DIRECT, DIRECT, "\n\r\n", "\n\r\n");
		testCheckout(DIRECT, DIRECT, "\r\n\r", "\r\n\r");

		testCheckout(DIRECT, DIRECT, "a\nb\n", "a\nb\n");
		testCheckout(DIRECT, DIRECT, "a\rb\r", "a\rb\r");
		testCheckout(DIRECT, DIRECT, "a\n\rb\n\r", "a\n\rb\n\r");
		testCheckout(DIRECT, DIRECT, "a\r\nb\r\n", "a\r\nb\r\n");
	}

	@Test
	public void testCheckoutLF() throws Exception {
		testCheckout(TEXT_LF, AUTO_LF, "", "");
		testCheckout(TEXT_LF, AUTO_LF, "\r", "\r");
		testCheckout(TEXT_LF, AUTO_LF, "\n", "\n");

		testCheckout(TEXT_LF, AUTO_LF, "\r\n", "\n");
		testCheckout(TEXT_LF, AUTO_LF, "\n\r", "\n\r");

		testCheckout(TEXT_LF, AUTO_LF, "\n\r\n", "\n\n");
		testCheckout(TEXT_LF, AUTO_LF, "\r\n\r", "\n\r");

		testCheckout(TEXT_LF, AUTO_LF, "a\nb\n", "a\nb\n");
		testCheckout(TEXT_LF, AUTO_LF, "a\rb\r", "a\rb\r");
		testCheckout(TEXT_LF, AUTO_LF, "a\n\rb\n\r", "a\n\rb\n\r");
		testCheckout(TEXT_LF, AUTO_LF, "a\r\nb\r\n", "a\nb\n");
	}

	@Test
	public void testCheckoutCRLF() throws Exception {
		testCheckout(TEXT_CRLF, AUTO_CRLF, "", "");
		testCheckout(TEXT_CRLF, AUTO_CRLF, "\r", "\r");
		testCheckout(TEXT_CRLF, AUTO_CRLF, "\n", "\r\n");

		testCheckout(TEXT_CRLF, AUTO_CRLF, "\r\n", "\r\n");
		testCheckout(TEXT_CRLF, AUTO_CRLF, "\n\r", "\r\n\r");

		testCheckout(TEXT_CRLF, AUTO_CRLF, "\n\r\n", "\r\n\r\n");
		testCheckout(TEXT_CRLF, AUTO_CRLF, "\r\n\r", "\r\n\r");

		testCheckout(TEXT_CRLF, AUTO_CRLF, "a\nb\n", "a\r\nb\r\n");
		testCheckout(TEXT_CRLF, AUTO_CRLF, "a\rb\r", "a\rb\r");
		testCheckout(TEXT_CRLF, AUTO_CRLF, "a\n\rb\n\r", "a\r\n\rb\r\n\r");
		testCheckout(TEXT_CRLF, AUTO_CRLF, "a\r\nb\r\n", "a\r\nb\r\n");
	}

	/**
	 * Test stream type detection based on stream content.
	 * <p>
	 * Tests three things with the output text:
	 * <p>
	 * 1) conversion if output was declared as text
	 * <p>
	 * 2) conversion if output was declared as potentially text (AUTO_...) and
	 * is in fact text
	 * <p>
	 * 3) conversion if modified output (now with binary characters) was
	 * declared as potentially text but now contains binary characters
	 * <p>
	 *
	 * @param streamTypeText
	 *            is the enum meaning that the output is definitely text (no
	 *            binary check at all)
	 * @param streamTypeWithBinaryCheck
	 *            is the enum meaning that the output may be text (binary check
	 *            is done)
	 * @param output
	 *            is a text output without binary characters
	 * @param expectedConversion
	 *            is the expected converted output without binary characters
	 * @throws Exception
	 */
	private void testCheckout(EolStreamType streamTypeText,
			EolStreamType streamTypeWithBinaryCheck, String output,
			String expectedConversion) throws Exception {
		ByteArrayOutputStream b;
		byte[] outputBytes = output.getBytes(StandardCharsets.UTF_8);
		byte[] expectedConversionBytes = expectedConversion
				.getBytes(StandardCharsets.UTF_8);

		// test using output text and assuming it was declared TEXT
		b = new ByteArrayOutputStream();
		try (OutputStream out = EolStreamTypeUtil.wrapOutputStream(b,
				streamTypeText)) {
			out.write(outputBytes);
		}
		assertArrayEquals(expectedConversionBytes, b.toByteArray());

		// test using ouput text and assuming it was declared AUTO, using binary
		// detection
		b = new ByteArrayOutputStream();
		try (OutputStream out = EolStreamTypeUtil.wrapOutputStream(b,
				streamTypeWithBinaryCheck)) {
			out.write(outputBytes);
		}
		assertArrayEquals(expectedConversionBytes, b.toByteArray());

		// now pollute output text with some binary bytes
		outputBytes = extendWithBinaryData(outputBytes);
		expectedConversionBytes = extendWithBinaryData(expectedConversionBytes);

		// again, test using output text and assuming it was declared TEXT
		b = new ByteArrayOutputStream();
		try (OutputStream out = EolStreamTypeUtil.wrapOutputStream(b,
				streamTypeText)) {
			out.write(outputBytes);
		}
		assertArrayEquals(expectedConversionBytes, b.toByteArray());

		// again, test using ouput text and assuming it was declared AUTO, using
		// binary
		// detection
		b = new ByteArrayOutputStream();
		try (OutputStream out = EolStreamTypeUtil.wrapOutputStream(b,
				streamTypeWithBinaryCheck)) {
			out.write(outputBytes);
		}
		// expect no conversion
		assertArrayEquals(outputBytes, b.toByteArray());
	}

	@Test
	public void testCheckinDirect() throws Exception {
		testCheckin(DIRECT, DIRECT, "", "");
		testCheckin(DIRECT, DIRECT, "\r", "\r");
		testCheckin(DIRECT, DIRECT, "\n", "\n");

		testCheckin(DIRECT, DIRECT, "\r\n", "\r\n");
		testCheckin(DIRECT, DIRECT, "\n\r", "\n\r");

		testCheckin(DIRECT, DIRECT, "\n\r\n", "\n\r\n");
		testCheckin(DIRECT, DIRECT, "\r\n\r", "\r\n\r");

		testCheckin(DIRECT, DIRECT, "a\nb\n", "a\nb\n");
		testCheckin(DIRECT, DIRECT, "a\rb\r", "a\rb\r");
		testCheckin(DIRECT, DIRECT, "a\n\rb\n\r", "a\n\rb\n\r");
		testCheckin(DIRECT, DIRECT, "a\r\nb\r\n", "a\r\nb\r\n");
	}

	@Test
	public void testCheckinLF() throws Exception {
		testCheckin(TEXT_LF, AUTO_LF, "", "");
		testCheckin(TEXT_LF, AUTO_LF, "\r", "\r");
		testCheckin(TEXT_LF, AUTO_LF, "\n", "\n");

		testCheckin(TEXT_LF, AUTO_LF, "\r\n", "\n");
		testCheckin(TEXT_LF, AUTO_LF, "\n\r", "\n\r");

		testCheckin(TEXT_LF, AUTO_LF, "\n\r\n", "\n\n");
		testCheckin(TEXT_LF, AUTO_LF, "\r\n\r", "\n\r");

		testCheckin(TEXT_LF, AUTO_LF, "a\nb\n", "a\nb\n");
		testCheckin(TEXT_LF, AUTO_LF, "a\rb\r", "a\rb\r");
		testCheckin(TEXT_LF, AUTO_LF, "a\n\rb\n\r", "a\n\rb\n\r");
		testCheckin(TEXT_LF, AUTO_LF, "a\r\nb\r\n", "a\nb\n");
	}

	@Test
	public void testCheckinCRLF() throws Exception {
		testCheckin(TEXT_CRLF, AUTO_CRLF, "", "");
		testCheckin(TEXT_CRLF, AUTO_CRLF, "\r", "\r");
		testCheckin(TEXT_CRLF, AUTO_CRLF, "\n", "\r\n");

		testCheckin(TEXT_CRLF, AUTO_CRLF, "\r\n", "\r\n");
		testCheckin(TEXT_CRLF, AUTO_CRLF, "\n\r", "\r\n\r");

		testCheckin(TEXT_CRLF, AUTO_CRLF, "\n\r\n", "\r\n\r\n");
		testCheckin(TEXT_CRLF, AUTO_CRLF, "\r\n\r", "\r\n\r");

		testCheckin(TEXT_CRLF, AUTO_CRLF, "a\nb\n", "a\r\nb\r\n");
		testCheckin(TEXT_CRLF, AUTO_CRLF, "a\rb\r", "a\rb\r");
		testCheckin(TEXT_CRLF, AUTO_CRLF, "a\n\rb\n\r", "a\r\n\rb\r\n\r");
		testCheckin(TEXT_CRLF, AUTO_CRLF, "a\r\nb\r\n", "a\r\nb\r\n");
	}

	/**
	 * Test stream type detection based on stream content.
	 * <p>
	 * Tests three things with the input text:
	 * <p>
	 * 1) conversion if input was declared as text
	 * <p>
	 * 2) conversion if input was declared as potentially text (AUTO_...) and is
	 * in fact text
	 * <p>
	 * 3) conversion if modified input (now with binary characters) was declared
	 * as potentially text but now contains binary characters
	 * <p>
	 *
	 * @param streamTypeText
	 *            is the enum meaning that the input is definitely text (no
	 *            binary check at all)
	 * @param streamTypeWithBinaryCheck
	 *            is the enum meaning that the input may be text (binary check
	 *            is done)
	 * @param input
	 *            is a text input without binary characters
	 * @param expectedConversion
	 *            is the expected converted input without binary characters
	 * @throws Exception
	 */
	private void testCheckin(EolStreamType streamTypeText,
			EolStreamType streamTypeWithBinaryCheck, String input,
			String expectedConversion) throws Exception {
		byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
		byte[] expectedConversionBytes = expectedConversion
				.getBytes(StandardCharsets.UTF_8);

		// test using input text and assuming it was declared TEXT
		try (InputStream in = EolStreamTypeUtil.wrapInputStream(
				new ByteArrayInputStream(inputBytes),
				streamTypeText)) {
			byte[] b = new byte[1024];
			int len = IO.readFully(in, b, 0);
			assertArrayEquals(expectedConversionBytes, Arrays.copyOf(b, len));
		}

		// test using input text and assuming it was declared AUTO, using binary
		// detection
		try (InputStream in = EolStreamTypeUtil.wrapInputStream(
				new ByteArrayInputStream(inputBytes),
				streamTypeWithBinaryCheck)) {
			byte[] b = new byte[1024];
			int len = IO.readFully(in, b, 0);
			assertArrayEquals(expectedConversionBytes, Arrays.copyOf(b, len));
		}

		// now pollute input text with some binary bytes
		inputBytes = extendWithBinaryData(inputBytes);
		expectedConversionBytes = extendWithBinaryData(expectedConversionBytes);

		// again, test using input text and assuming it was declared TEXT
		try (InputStream in = EolStreamTypeUtil.wrapInputStream(
				new ByteArrayInputStream(inputBytes), streamTypeText)) {
			byte[] b = new byte[1024];
			int len = IO.readFully(in, b, 0);
			assertArrayEquals(expectedConversionBytes, Arrays.copyOf(b, len));
		}

		// again, test using input text and assuming it was declared AUTO, using
		// binary
		// detection
		try (InputStream in = EolStreamTypeUtil.wrapInputStream(
				new ByteArrayInputStream(inputBytes),
				streamTypeWithBinaryCheck)) {
			byte[] b = new byte[1024];
			int len = IO.readFully(in, b, 0);
			// expect no conversion
			assertArrayEquals(inputBytes, Arrays.copyOf(b, len));
		}
	}

	private byte[] extendWithBinaryData(byte[] data) throws Exception {
		int n = 3;
		byte[] dataEx = new byte[data.length + n];
		System.arraycopy(data, 0, dataEx, 0, data.length);
		for (int i = 0; i < n; i++) {
			dataEx[data.length + i] = (byte) i;
		}
		return dataEx;
	}

}
