/*
 * Copyright (C) 2009, Google Inc.
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
		final UnionInputStream u = new UnionInputStream();
		assertTrue(u.isEmpty());
		assertEquals(-1, u.read());
		assertEquals(-1, u.read(new byte[1], 0, 1));
		assertEquals(0, u.available());
		assertEquals(0, u.skip(1));
		u.close();
	}

	@Test
	public void testReadSingleBytes() throws IOException {
		final UnionInputStream u = new UnionInputStream();

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

	@Test
	public void testReadByteBlocks() throws IOException {
		final UnionInputStream u = new UnionInputStream();
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

	private static byte[] slice(byte[] in, int len) {
		byte[] r = new byte[len];
		System.arraycopy(in, 0, r, 0, len);
		return r;
	}

	@Test
	public void testArrayConstructor() throws IOException {
		final UnionInputStream u = new UnionInputStream(
				new ByteArrayInputStream(new byte[] { 1, 0, 2 }),
				new ByteArrayInputStream(new byte[] { 3 }),
				new ByteArrayInputStream(new byte[] { 4, 5 }));

		final byte[] r = new byte[5];
		assertEquals(3, u.read(r, 0, 5));
		assertTrue(Arrays.equals(new byte[] { 1, 0, 2, }, slice(r, 3)));
		assertEquals(1, u.read(r, 0, 5));
		assertEquals(3, r[0]);
		assertEquals(2, u.read(r, 0, 5));
		assertTrue(Arrays.equals(new byte[] { 4, 5, }, slice(r, 2)));
		assertEquals(-1, u.read(r, 0, 5));
	}

	@Test
	public void testMarkSupported() {
		final UnionInputStream u = new UnionInputStream();
		assertFalse(u.markSupported());
		u.add(new ByteArrayInputStream(new byte[] { 1, 0, 2 }));
		assertFalse(u.markSupported());
	}

	@Test
	public void testSkip() throws IOException {
		final UnionInputStream u = new UnionInputStream();
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
			public long skip(long n) {
				return 0;
			}
		});
		assertEquals(2, u.skip(8));
		assertEquals(-1, u.read());
	}

	@Test
	public void testAutoCloseDuringRead() throws IOException {
		final UnionInputStream u = new UnionInputStream();
		final boolean closed[] = new boolean[2];
		u.add(new ByteArrayInputStream(new byte[] { 1 }) {
			public void close() {
				closed[0] = true;
			}
		});
		u.add(new ByteArrayInputStream(new byte[] { 2 }) {
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

	@Test
	public void testCloseDuringClose() throws IOException {
		final UnionInputStream u = new UnionInputStream();
		final boolean closed[] = new boolean[2];
		u.add(new ByteArrayInputStream(new byte[] { 1 }) {
			public void close() {
				closed[0] = true;
			}
		});
		u.add(new ByteArrayInputStream(new byte[] { 2 }) {
			public void close() {
				closed[1] = true;
			}
		});

		assertFalse(closed[0]);
		assertFalse(closed[1]);

		u.close();

		assertTrue(closed[0]);
		assertTrue(closed[1]);
	}

	@Test
	public void testExceptionDuringClose() {
		final UnionInputStream u = new UnionInputStream();
		u.add(new ByteArrayInputStream(new byte[] { 1 }) {
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
		};
		final UnionInputStream u = new UnionInputStream(
				new ByteArrayInputStream(new byte[]{1,2,3}),
				errorReadStream);
		byte buf[] = new byte[10];
		assertEquals(3, u.read(buf, 0, 10));
		assertTrue(Arrays.equals(new byte[] {1,2,3}, slice(buf, 3)));
		try {
			u.read(buf, 0, 1);
			fail("Expected exception from errorReadStream");
		} catch (IOException e) {
			assertEquals("Expected", e.getMessage());
		}
	}
}
