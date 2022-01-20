/*
 * Copyright (C) 2021 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util.stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

/**
 * Tests for {@link BinaryHunkInputStream} and {@link BinaryHunkOutputStream}.
 */
public class BinaryHunkStreamTest {

	@Test
	public void testRoundtripWholeBuffer() throws IOException {
		for (int length = 1; length < 520 + 52; length++) {
			byte[] data = new byte[length];
			for (int i = 0; i < data.length; i++) {
				data[i] = (byte) (255 - (i % 256));
			}
			try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
					BinaryHunkOutputStream out = new BinaryHunkOutputStream(
							bos)) {
				out.write(data);
				out.flush();
				byte[] encoded = bos.toByteArray();
				assertFalse(Arrays.equals(data, encoded));
				try (BinaryHunkInputStream in = new BinaryHunkInputStream(
						new ByteArrayInputStream(encoded))) {
					byte[] decoded = new byte[data.length];
					int newLength = in.read(decoded);
					assertEquals(newLength, decoded.length);
					assertEquals(-1, in.read());
					assertArrayEquals(data, decoded);
				}
			}
		}
	}

	@Test
	public void testRoundtripChunks() throws IOException {
		for (int length = 1; length < 520 + 52; length++) {
			byte[] data = new byte[length];
			for (int i = 0; i < data.length; i++) {
				data[i] = (byte) (255 - (i % 256));
			}
			try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
					BinaryHunkOutputStream out = new BinaryHunkOutputStream(
							bos)) {
				out.write(data, 0, data.length / 2);
				out.write(data, data.length / 2, data.length - data.length / 2);
				out.flush();
				byte[] encoded = bos.toByteArray();
				assertFalse(Arrays.equals(data, encoded));
				try (BinaryHunkInputStream in = new BinaryHunkInputStream(
						new ByteArrayInputStream(encoded))) {
					byte[] decoded = new byte[data.length];
					int p = 0;
					int n;
					while ((n = in.read(decoded, p,
							Math.min(decoded.length - p, 57))) >= 0) {
						p += n;
						if (p == decoded.length) {
							break;
						}
					}
					assertEquals(p, decoded.length);
					assertEquals(-1, in.read());
					assertArrayEquals(data, decoded);
				}
			}
		}
	}

	@Test
	public void testRoundtripBytes() throws IOException {
		for (int length = 1; length < 520 + 52; length++) {
			byte[] data = new byte[length];
			for (int i = 0; i < data.length; i++) {
				data[i] = (byte) (255 - (i % 256));
			}
			try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
					BinaryHunkOutputStream out = new BinaryHunkOutputStream(
							bos)) {
				for (int i = 0; i < data.length; i++) {
					out.write(data[i]);
				}
				out.flush();
				byte[] encoded = bos.toByteArray();
				assertFalse(Arrays.equals(data, encoded));
				try (BinaryHunkInputStream in = new BinaryHunkInputStream(
						new ByteArrayInputStream(encoded))) {
					byte[] decoded = new byte[data.length];
					for (int i = 0; i < decoded.length; i++) {
						int val = in.read();
						assertTrue(0 <= val && val <= 255);
						decoded[i] = (byte) val;
					}
					assertEquals(-1, in.read());
					assertArrayEquals(data, decoded);
				}
			}
		}
	}

	@Test
	public void testRoundtripWithClose() throws IOException {
		for (int length = 1; length < 520 + 52; length++) {
			byte[] data = new byte[length];
			for (int i = 0; i < data.length; i++) {
				data[i] = (byte) (255 - (i % 256));
			}
			try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
				try (BinaryHunkOutputStream out = new BinaryHunkOutputStream(
						bos)) {
					out.write(data);
				}
				byte[] encoded = bos.toByteArray();
				assertFalse(Arrays.equals(data, encoded));
				try (BinaryHunkInputStream in = new BinaryHunkInputStream(
						new ByteArrayInputStream(encoded))) {
					byte[] decoded = new byte[data.length];
					int newLength = in.read(decoded);
					assertEquals(newLength, decoded.length);
					assertEquals(-1, in.read());
					assertArrayEquals(data, decoded);
				}
			}
		}
	}
}
