/*
 * Copyright (C) 2026, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd.proxy;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.junit.Test;

/**
 * Basic tests for {@link Accumulator}.
 */
public class AccumulatorTest {

	private static final byte[] END = { '\r', '\n', '\r', '\n' };

	@Test
	public void normalInput() throws Exception {
		byte[] data = "Complete line\r\nHeader:foo\r\nOther:bar\r\n\r\nSomething else\r\n\r\nanother line"
				.getBytes(StandardCharsets.US_ASCII);
		Accumulator acc = new Accumulator(100, END, "Headers too long");
		assertTrue(acc.accumulate(new ByteArrayBuffer(data)));
		String headers = new String(acc.getData(), StandardCharsets.US_ASCII);
		String body = new String(acc.getRest(), StandardCharsets.US_ASCII);
		assertEquals("Something else\r\n\r\nanother line", body);
		assertArrayEquals(data,
				(headers + body).getBytes(StandardCharsets.US_ASCII));
	}

	@Test
	public void noBody() throws Exception {
		byte[] data = "Complete line\r\nHeader:foo\r\nOther:bar\r\n\r\n"
				.getBytes(StandardCharsets.US_ASCII);
		Accumulator acc = new Accumulator(100, END, "Headers too long");
		assertTrue(acc.accumulate(new ByteArrayBuffer(data)));
		String headers = new String(acc.getData(), StandardCharsets.US_ASCII);
		String body = new String(acc.getRest(), StandardCharsets.US_ASCII);
		assertEquals("", body);
		assertArrayEquals(data, headers.getBytes(StandardCharsets.US_ASCII));
	}

	@Test
	public void fragmentedInput() throws Exception {
		byte[] data = "Complete line\r\nHeader:foo\r\nOther:bar\r\n\r\nSomething else\r\n\r\nanother line"
				.getBytes(StandardCharsets.US_ASCII);
		Accumulator acc = new Accumulator(100, END, "Headers too long");
		for (int i = 0; i < data.length; i++) {
			if (i < 39) {
				assertFalse("Failed false at " + i,
						acc.accumulate(new ByteArrayBuffer(data, i, 1)));
			} else {
				assertTrue("Failed true at " + i,
						acc.accumulate(new ByteArrayBuffer(data, i, 1)));
			}
		}
		String headers = new String(acc.getData(), StandardCharsets.US_ASCII);
		String body = new String(acc.getRest(), StandardCharsets.US_ASCII);
		assertEquals("Something else\r\n\r\nanother line", body);
		assertArrayEquals(data,
				(headers + body).getBytes(StandardCharsets.US_ASCII));
	}

	@Test
	public void limitExceeded() throws Exception {
		byte[] data = new byte[11];
		Accumulator acc = new Accumulator(10, END, "Headers too long");
		assertThrows(Exception.class,
				() -> acc.accumulate(new ByteArrayBuffer(data)));
	}

	@Test
	public void limitExceededFragmented() throws Exception {
		byte[] data = new byte[8];
		Accumulator acc = new Accumulator(10, END, "Headers too long");
		assertFalse(acc.accumulate(new ByteArrayBuffer(data)));
		assertThrows(Exception.class,
				() -> acc.accumulate(new ByteArrayBuffer(data)));
	}

}
