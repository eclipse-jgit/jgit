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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.eclipse.jgit.lib.Constants;
import org.junit.Test;

public class TeeOutputStreamTest {

	@Test
	public void test() throws IOException {
		byte[] data = Constants.encode("Hello World");

		TestOutput first = new TestOutput();
		TestOutput second = new TestOutput();
		TeeOutputStream tee = new TeeOutputStream(first, second);

		tee.write(data);
		assertArrayEquals("Stream output must match", first.toByteArray(), second.toByteArray());

		tee.write(1);
		assertArrayEquals("Stream output must match", first.toByteArray(), second.toByteArray());

		tee.write(data, 1, 4); // Test partial write methods
		assertArrayEquals("Stream output must match", first.toByteArray(), second.toByteArray());

		tee.close();
		assertTrue("First stream closed", first.closed);
		assertTrue("Second stream closed", second.closed);
	}

	@Test
	public void testCloseException() {
		TestOutput first = new TestOutput() {
			@Override
			public void close() throws IOException {
				throw new IOException();
			}

		};
		TestOutput second = new TestOutput();

		TeeOutputStream tee = new TeeOutputStream(first, second);
		try {
			tee.close();
		} catch (IOException ex) {
			// Expected from first closed
		}
		assertFalse("First stream not closed", first.closed);
		assertTrue("Second stream closed", second.closed);
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
