/*
 * Copyright (C) 2020, Michael Dardis. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.eclipse.jgit.lib.Constants;
import org.junit.jupiter.api.Test;

public class TeeOutputStreamTest {

	@Test
	void test() throws IOException {
		byte[] data = Constants.encode("Hello World");

		TestOutput first = new TestOutput();
		TestOutput second = new TestOutput();
		try (TeeOutputStream tee = new TeeOutputStream(first, second)) {
			tee.write(data);
			assertArrayEquals(first.toByteArray(),
					second.toByteArray(),
					"Stream output must match");

			tee.write(1);
			assertArrayEquals(first.toByteArray(),
					second.toByteArray(),
					"Stream output must match");

			tee.write(data, 1, 4); // Test partial write methods
			assertArrayEquals(first.toByteArray(),
					second.toByteArray(),
					"Stream output must match");
		}
		assertTrue(first.closed, "First stream should be closed");
		assertTrue(second.closed, "Second stream should be closed");
	}

	@Test
	void testCloseException() {
		TestOutput first = new TestOutput() {
			@Override
			public void close() throws IOException {
				throw new IOException();
			}

		};
		TestOutput second = new TestOutput();

		@SuppressWarnings("resource")
		TeeOutputStream tee = new TeeOutputStream(first, second);
		try {
			tee.close();
		} catch (IOException ex) {
			// Expected from first closed
		}
		assertFalse(first.closed, "First stream should not be closed");
		assertTrue(second.closed,
				"Second stream should still be closed if first close failed");
	}

	private static class TestOutput extends ByteArrayOutputStream {

		private boolean closed;

		@Override
		public void close() throws IOException {
			closed = true;
			super.close();
		}
	}

}
