/*
 * Copyright (C) 2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.junit.Test;

public class UnionInputStreamTest {
	@Test
	public void testEmptyStream() throws IOException {
		try (UnionInputStream u = new UnionInputStream()) {
			assertTrue(u.isEmpty());
			assertEquals(-1, u.read());
			assertEquals(-1, u.read(new byte[1], 0, 1));
			assertEquals(0, u.available());
			assertEquals(0, u.skip(1));
		}
	}

	@Test
	public void testReadSingleBytes() throws IOException {
		try (UnionInputStream u = new UnionInputStream()) {
			assertTrue(u.isEmpty());
			u.add(new ByteArrayInputStream(new byte[] { 1, 0, 2 }));
			u.add(new ByteArrayInputStream(new byte[] { 3 }));
			u.add(new ByteArrayInputStream(new byte[] { 4, 5 }));

			assertFalse(u.isEmpty());
			assertEquals(3, u.available());
			assertEquals(1, u.read());
			assertEquals(0, u.read());
			assertEquals(2, u.read());
			assertEquals(0, u.available());

			assertEquals(3, u.read());
			assertEquals(0, u.available());

			assertEquals(4, u.read());
			assertEquals(1, u.available());
			assertEquals(5, u.read());
			assertEquals(0, u.available());
			assertEquals(-1, u.read());

			assertTrue(u.isEmpty());
			u.add(new ByteArrayInputStream(new byte[] { (byte) 255 }));
			assertEquals(255, u.read());
			assertEquals(-1, u.read());
			assertTrue(u.isEmpty());
		}
	}

	@Test
	public void testReadByteBlocks() throws IOException {
		try (UnionInputStream u = new UnionInputStream()) {
			u.add(new ByteArrayInputStream(new byte[] { 1, 0, 2 }));
			u.add(new ByteArrayInputStream(new byte[] { 3 }));
			u.add(new ByteArrayInputStream(new byte[] { 4, 5 }));

			final byte[] r = new byte[5];
			assertEquals(3, u.read(r, 0, 5));
			assertTrue(Arrays.equals(new byte[] { 1, 0, 2, }, slice(r, 3)));
			assertEquals(1, u.read(r, 0, 5));
			assertEquals(3, r[0]);
			assertEquals(2, u.read(r, 0, 5));
			assertTrue(Arrays.equals(new byte[] { 4, 5, }, slice(r, 2)));
			assertEquals(-1, u.read(r, 0, 5));
		}
	}

	private static byte[] slice(byte[] in, int len) {
		byte[] r = new byte[len];
		System.arraycopy(in, 0, r, 0, len);
		return r;
	}

	@Test
	public void testArrayConstructor() throws IOException {
		try (UnionInputStream u = new UnionInputStream(
				new ByteArrayInputStream(new byte[] { 1, 0, 2 }),
				new ByteArrayInputStream(new byte[] { 3 }),
				new ByteArrayInputStream(new byte[] { 4, 5 }))) {
			final byte[] r = new byte[5];
			assertEquals(3, u.read(r, 0, 5));
			assertTrue(Arrays.equals(new byte[] { 1, 0, 2, }, slice(r, 3)));
			assertEquals(1, u.read(r, 0, 5));
			assertEquals(3, r[0]);
			assertEquals(2, u.read(r, 0, 5));
			assertTrue(Arrays.equals(new byte[] { 4, 5, }, slice(r, 2)));
			assertEquals(-1, u.read(r, 0, 5));
		}
	}

	@Test
	public void testMarkSupported() throws IOException {
		try (UnionInputStream u = new UnionInputStream()) {
			assertFalse(u.markSupported());
			u.add(new ByteArrayInputStream(new byte[] { 1, 0, 2 }));
			assertFalse(u.markSupported());
		}
	}

	@Test
	public void testSkip() throws IOException {
		try (UnionInputStream u = new UnionInputStream()) {
			u.add(new ByteArrayInputStream(new byte[] { 1, 0, 2 }));
			u.add(new ByteArrayInputStream(new byte[] { 3 }));
			u.add(new ByteArrayInputStream(new byte[] { 4, 5 }));
			assertEquals(0, u.skip(0));
			assertEquals(3, u.skip(3));
			assertEquals(3, u.read());
			assertEquals(2, u.skip(5));
			assertEquals(0, u.skip(5));
			assertEquals(-1, u.read());

			u.add(new ByteArrayInputStream(new byte[] { 20, 30 }) {
				@Override
				@SuppressWarnings("UnsynchronizedOverridesSynchronized")
				// This is only used in tests and is thread-safe
				public long skip(long n) {
					return 0;
				}
			});
			assertEquals(2, u.skip(8));
			assertEquals(-1, u.read());
		}
	}

	@Test
	public void testAutoCloseDuringRead() throws IOException {
		try (UnionInputStream u = new UnionInputStream()) {
			final boolean closed[] = new boolean[2];
			u.add(new ByteArrayInputStream(new byte[] { 1 }) {
				@Override
				public void close() {
					closed[0] = true;
				}
			});
			u.add(new ByteArrayInputStream(new byte[] { 2 }) {
				@Override
				public void close() {
					closed[1] = true;
				}
			});

			assertFalse(closed[0]);
			assertFalse(closed[1]);

			assertEquals(1, u.read());
			assertFalse(closed[0]);
			assertFalse(closed[1]);

			assertEquals(2, u.read());
			assertTrue(closed[0]);
			assertFalse(closed[1]);

			assertEquals(-1, u.read());
			assertTrue(closed[0]);
			assertTrue(closed[1]);
		}
	}

	@Test
	public void testCloseDuringClose() throws IOException {
		final boolean closed[] = new boolean[2];
		try (UnionInputStream u = new UnionInputStream()) {
			u.add(new ByteArrayInputStream(new byte[] { 1 }) {
				@Override
				public void close() {
					closed[0] = true;
				}
			});
			u.add(new ByteArrayInputStream(new byte[] { 2 }) {
				@Override
				public void close() {
					closed[1] = true;
				}
			});

			assertFalse(closed[0]);
			assertFalse(closed[1]);
		}

		assertTrue(closed[0]);
		assertTrue(closed[1]);
	}

	@Test
	public void testExceptionDuringClose() {
		@SuppressWarnings("resource") // We are testing the close() method
		final UnionInputStream u = new UnionInputStream();
		u.add(new ByteArrayInputStream(new byte[] { 1 }) {
			@Override
			public void close() throws IOException {
				throw new IOException("I AM A TEST");
			}
		});
		try {
			u.close();
			fail("close ignored inner stream exception");
		} catch (IOException e) {
			assertEquals("I AM A TEST", e.getMessage());
		}
	}

	@Test
	public void testNonBlockingPartialRead() throws Exception {
		InputStream errorReadStream = new InputStream() {
			@Override
			public int read() throws IOException {
				throw new IOException("Expected");
			}

			@Override
			public int read(byte b[], int off, int len) throws IOException {
				throw new IOException("Expected");
			}
		};
		try (UnionInputStream u = new UnionInputStream(
				new ByteArrayInputStream(new byte[] { 1, 2, 3 }),
				errorReadStream)) {
			byte buf[] = new byte[10];
			assertEquals(3, u.read(buf, 0, 10));
			assertTrue(Arrays.equals(new byte[] { 1, 2, 3 }, slice(buf, 3)));
			try {
				u.read(buf, 0, 1);
				fail("Expected exception from errorReadStream");
			} catch (IOException e) {
				assertEquals("Expected", e.getMessage());
			}
		}
	}
}
