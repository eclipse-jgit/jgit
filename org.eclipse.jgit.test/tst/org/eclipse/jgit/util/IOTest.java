/*
 * Copyright (C) 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Test;

public class IOTest {

	private static final byte[] DATA = "abcdefghijklmnopqrstuvwxyz"
			.getBytes(StandardCharsets.US_ASCII);

	private byte[] initBuffer(int size) {
		byte[] buffer = new byte[size];
		for (int i = 0; i < size; i++) {
			buffer[i] = (byte) ('0' + (i % 10));
		}
		return buffer;
	}

	private int read(byte[] buffer, int from) throws IOException {
		try (InputStream in = new ByteArrayInputStream(DATA)) {
			return IO.readFully(in, buffer, from);
		}
	}

	@Test
	public void readFullyBufferShorter() throws Exception {
		byte[] buffer = initBuffer(9);
		int length = read(buffer, 0);
		assertEquals(buffer.length, length);
		assertArrayEquals(buffer, Arrays.copyOfRange(DATA, 0, length));
	}

	@Test
	public void readFullyBufferLonger() throws Exception {
		byte[] buffer = initBuffer(50);
		byte[] initial = Arrays.copyOf(buffer, buffer.length);
		int length = read(buffer, 0);
		assertEquals(DATA.length, length);
		assertArrayEquals(Arrays.copyOfRange(buffer, 0, length), DATA);
		assertArrayEquals(Arrays.copyOfRange(buffer, length, buffer.length),
				Arrays.copyOfRange(initial, length, initial.length));
	}

	@Test
	public void readFullyBufferShorterOffset() throws Exception {
		byte[] buffer = initBuffer(9);
		byte[] initial = Arrays.copyOf(buffer, buffer.length);
		int length = read(buffer, 6);
		assertEquals(3, length);
		assertArrayEquals(Arrays.copyOfRange(buffer, 0, 6),
				Arrays.copyOfRange(initial, 0, 6));
		assertArrayEquals(Arrays.copyOfRange(buffer, 6, buffer.length),
				Arrays.copyOfRange(DATA, 0, 3));
	}

	@Test
	public void readFullyBufferLongerOffset() throws Exception {
		byte[] buffer = initBuffer(50);
		byte[] initial = Arrays.copyOf(buffer, buffer.length);
		int length = read(buffer, 40);
		assertEquals(10, length);
		assertArrayEquals(Arrays.copyOfRange(buffer, 0, 40),
				Arrays.copyOfRange(initial, 0, 40));
		assertArrayEquals(Arrays.copyOfRange(buffer, 40, buffer.length),
				Arrays.copyOfRange(DATA, 0, 10));
	}
}
