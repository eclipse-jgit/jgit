/*
 * Copyright (C) 2023, SAP SE and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.io.ByteBufferInputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ByteBufferInputStreamTest {

	private static byte data[] = { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06,
			0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F };

	private ByteBuffer buf;

	private ByteBufferInputStream is;

	@Before
	public void setup() {
		buf = ByteBuffer.wrap(data);
		is = new ByteBufferInputStream(buf);
	}

	@After
	public void tearDown() {
		is.close();
	}

	@Test
	public void testRead() throws IOException {
		assertEquals(0x00, is.read());
		assertEquals(0x01, is.read());
		assertEquals(0x02, is.read());
		assertEquals(0x03, is.read());
		assertEquals(0x04, is.read());
		assertEquals(0x05, is.read());
		assertEquals(0x06, is.read());
		assertEquals(0x07, is.read());
		assertEquals(0x08, is.read());
		assertEquals(0x09, is.read());
		assertEquals(0x0A, is.read());
		assertEquals(0x0B, is.read());
		assertEquals(0x0C, is.read());
		assertEquals(0x0D, is.read());
		assertEquals(0x0E, is.read());
		assertEquals(0x0F, is.read());
	}

	@Test
	public void testReadMultiple() throws IOException {
		byte[] x = new byte[5];
		int n = is.read(x);
		assertEquals(5, n);
		assertArrayEquals(new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04 }, x);
	}

	@Test
	public void testReadMultipleOffset() throws IOException {
		byte[] x = new byte[7];
		int n = is.read(x, 4, 3);
		assertEquals(3, n);
		assertArrayEquals(
				new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02 },
				x);
	}

	@Test
	public void testReadAll() throws IOException {
		byte[] x = is.readAllBytes();
		assertEquals(16, x.length);
		assertArrayEquals(data, x);
	}

	@Test
	public void testMarkReset() throws IOException {
		byte[] x = new byte[5];
		int n = is.read(x);
		assertEquals(11, is.available());
		is.mark(is.available());
		is.reset();
		byte[] y = new byte[5];
		int m = is.read(y);
		assertEquals(n, m);
		assertArrayEquals(new byte[] { 0x05, 0x06, 0x07, 0x08, 0x09 }, y);
	}

	@Test
	public void testClosed() {
		is.close();
		Exception e = assertThrows(IOException.class, () -> is.read());
		assertEquals(JGitText.get().inputStreamClosed, e.getMessage());
	}
}
