/*
 * Copyright (C) 2008-2009, Google Inc.
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

package org.eclipse.jgit.util;

import static org.eclipse.jgit.junit.JGitTestUtil.getName;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.util.TemporaryBuffer.Block;
import org.junit.Test;

public class TemporaryBufferTest {
	@Test
	public void testEmpty() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer.LocalFile(null);
		try {
			b.close();
			assertEquals(0, b.length());
			final byte[] r = b.toByteArray();
			assertNotNull(r);
			assertEquals(0, r.length);
		} finally {
			b.destroy();
		}
	}

	@Test
	public void testOneByte() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer.LocalFile(null);
		final byte test = (byte) new TestRng(getName()).nextInt();
		try {
			b.write(test);
			b.close();
			assertEquals(1, b.length());
			{
				final byte[] r = b.toByteArray();
				assertNotNull(r);
				assertEquals(1, r.length);
				assertEquals(test, r[0]);
			}
			{
				final ByteArrayOutputStream o = new ByteArrayOutputStream();
				b.writeTo(o, null);
				o.close();
				final byte[] r = o.toByteArray();
				assertEquals(1, r.length);
				assertEquals(test, r[0]);
			}
		} finally {
			b.destroy();
		}
	}

	@Test
	public void testOneBlock_BulkWrite() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer.LocalFile(null);
		final byte[] test = new TestRng(getName())
				.nextBytes(TemporaryBuffer.Block.SZ);
		try {
			b.write(test, 0, 2);
			b.write(test, 2, 4);
			b.write(test, 6, test.length - 6 - 2);
			b.write(test, test.length - 2, 2);
			b.close();
			assertEquals(test.length, b.length());
			{
				final byte[] r = b.toByteArray();
				assertNotNull(r);
				assertEquals(test.length, r.length);
				assertArrayEquals(test, r);
			}
			{
				final ByteArrayOutputStream o = new ByteArrayOutputStream();
				b.writeTo(o, null);
				o.close();
				final byte[] r = o.toByteArray();
				assertEquals(test.length, r.length);
				assertArrayEquals(test, r);
			}
		} finally {
			b.destroy();
		}
	}

	@Test
	public void testOneBlockAndHalf_BulkWrite() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer.LocalFile(null);
		final byte[] test = new TestRng(getName())
				.nextBytes(TemporaryBuffer.Block.SZ * 3 / 2);
		try {
			b.write(test, 0, 2);
			b.write(test, 2, 4);
			b.write(test, 6, test.length - 6 - 2);
			b.write(test, test.length - 2, 2);
			b.close();
			assertEquals(test.length, b.length());
			{
				final byte[] r = b.toByteArray();
				assertNotNull(r);
				assertEquals(test.length, r.length);
				assertArrayEquals(test, r);
			}
			{
				final ByteArrayOutputStream o = new ByteArrayOutputStream();
				b.writeTo(o, null);
				o.close();
				final byte[] r = o.toByteArray();
				assertEquals(test.length, r.length);
				assertArrayEquals(test, r);
			}
		} finally {
			b.destroy();
		}
	}

	@Test
	public void testOneBlockAndHalf_SingleWrite() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer.LocalFile(null);
		final byte[] test = new TestRng(getName())
				.nextBytes(TemporaryBuffer.Block.SZ * 3 / 2);
		try {
			for (int i = 0; i < test.length; i++)
				b.write(test[i]);
			b.close();
			assertEquals(test.length, b.length());
			{
				final byte[] r = b.toByteArray();
				assertNotNull(r);
				assertEquals(test.length, r.length);
				assertArrayEquals(test, r);
			}
			{
				final ByteArrayOutputStream o = new ByteArrayOutputStream();
				b.writeTo(o, null);
				o.close();
				final byte[] r = o.toByteArray();
				assertEquals(test.length, r.length);
				assertArrayEquals(test, r);
			}
		} finally {
			b.destroy();
		}
	}

	@Test
	public void testOneBlockAndHalf_Copy() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer.LocalFile(null);
		final byte[] test = new TestRng(getName())
				.nextBytes(TemporaryBuffer.Block.SZ * 3 / 2);
		try {
			final ByteArrayInputStream in = new ByteArrayInputStream(test);
			b.write(in.read());
			b.copy(in);
			b.close();
			assertEquals(test.length, b.length());
			{
				final byte[] r = b.toByteArray();
				assertNotNull(r);
				assertEquals(test.length, r.length);
				assertArrayEquals(test, r);
			}
			{
				final ByteArrayOutputStream o = new ByteArrayOutputStream();
				b.writeTo(o, null);
				o.close();
				final byte[] r = o.toByteArray();
				assertEquals(test.length, r.length);
				assertArrayEquals(test, r);
			}
		} finally {
			b.destroy();
		}
	}

	@Test
	public void testLarge_SingleWrite() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer.LocalFile(null);
		final byte[] test = new TestRng(getName())
				.nextBytes(TemporaryBuffer.DEFAULT_IN_CORE_LIMIT * 3);
		try {
			b.write(test);
			b.close();
			assertEquals(test.length, b.length());
			{
				final byte[] r = b.toByteArray();
				assertNotNull(r);
				assertEquals(test.length, r.length);
				assertArrayEquals(test, r);
			}
			{
				final ByteArrayOutputStream o = new ByteArrayOutputStream();
				b.writeTo(o, null);
				o.close();
				final byte[] r = o.toByteArray();
				assertEquals(test.length, r.length);
				assertArrayEquals(test, r);
			}
		} finally {
			b.destroy();
		}
	}

	@Test
	public void testInCoreInputStream() throws IOException {
		final int cnt = 256;
		final byte[] test = new TestRng(getName()).nextBytes(cnt);
		final TemporaryBuffer.Heap b = new TemporaryBuffer.Heap(cnt + 4);
		b.write(test);
		b.close();

		InputStream in = b.openInputStream();
		byte[] act = new byte[cnt];
		IO.readFully(in, act, 0, cnt);
		assertArrayEquals(test, act);
	}

	@Test
	public void testInCoreLimit_SwitchOnAppendByte() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer.LocalFile(null);
		final byte[] test = new TestRng(getName())
				.nextBytes(TemporaryBuffer.DEFAULT_IN_CORE_LIMIT + 1);
		try {
			b.write(test, 0, test.length - 1);
			b.write(test[test.length - 1]);
			b.close();
			assertEquals(test.length, b.length());
			{
				final byte[] r = b.toByteArray();
				assertNotNull(r);
				assertEquals(test.length, r.length);
				assertArrayEquals(test, r);
			}
			{
				final ByteArrayOutputStream o = new ByteArrayOutputStream();
				b.writeTo(o, null);
				o.close();
				final byte[] r = o.toByteArray();
				assertEquals(test.length, r.length);
				assertArrayEquals(test, r);
			}
		} finally {
			b.destroy();
		}
	}

	@Test
	public void testInCoreLimit_SwitchBeforeAppendByte() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer.LocalFile(null);
		final byte[] test = new TestRng(getName())
				.nextBytes(TemporaryBuffer.DEFAULT_IN_CORE_LIMIT * 3);
		try {
			b.write(test, 0, test.length - 1);
			b.write(test[test.length - 1]);
			b.close();
			assertEquals(test.length, b.length());
			{
				final byte[] r = b.toByteArray();
				assertNotNull(r);
				assertEquals(test.length, r.length);
				assertArrayEquals(test, r);
			}
			{
				final ByteArrayOutputStream o = new ByteArrayOutputStream();
				b.writeTo(o, null);
				o.close();
				final byte[] r = o.toByteArray();
				assertEquals(test.length, r.length);
				assertArrayEquals(test, r);
			}
		} finally {
			b.destroy();
		}
	}

	@Test
	public void testInCoreLimit_SwitchOnCopy() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer.LocalFile(null);
		final byte[] test = new TestRng(getName())
				.nextBytes(TemporaryBuffer.DEFAULT_IN_CORE_LIMIT * 2);
		try {
			final ByteArrayInputStream in = new ByteArrayInputStream(test,
					TemporaryBuffer.DEFAULT_IN_CORE_LIMIT, test.length
							- TemporaryBuffer.DEFAULT_IN_CORE_LIMIT);
			b.write(test, 0, TemporaryBuffer.DEFAULT_IN_CORE_LIMIT);
			b.copy(in);
			b.close();
			assertEquals(test.length, b.length());
			{
				final byte[] r = b.toByteArray();
				assertNotNull(r);
				assertEquals(test.length, r.length);
				assertArrayEquals(test, r);
			}
			{
				final ByteArrayOutputStream o = new ByteArrayOutputStream();
				b.writeTo(o, null);
				o.close();
				final byte[] r = o.toByteArray();
				assertEquals(test.length, r.length);
				assertArrayEquals(test, r);
			}
		} finally {
			b.destroy();
		}
	}

	@Test
	public void testDestroyWhileOpen() throws IOException {
		@SuppressWarnings("resource" /* java 7 */)
		final TemporaryBuffer b = new TemporaryBuffer.LocalFile(null);
		try {
			b.write(new TestRng(getName())
					.nextBytes(TemporaryBuffer.DEFAULT_IN_CORE_LIMIT * 2));
		} finally {
			b.destroy();
		}
	}

	@Test
	public void testRandomWrites() throws IOException {
		final TemporaryBuffer b = new TemporaryBuffer.LocalFile(null);
		final TestRng rng = new TestRng(getName());
		final int max = TemporaryBuffer.DEFAULT_IN_CORE_LIMIT * 2;
		final byte[] expect = new byte[max];
		try {
			int written = 0;
			boolean onebyte = true;
			while (written < max) {
				if (onebyte) {
					final byte v = (byte) rng.nextInt();
					b.write(v);
					expect[written++] = v;
				} else {
					final int len = Math
							.min(rng.nextInt() & 127, max - written);
					final byte[] tmp = rng.nextBytes(len);
					b.write(tmp, 0, len);
					System.arraycopy(tmp, 0, expect, written, len);
					written += len;
				}
				onebyte = !onebyte;
			}
			assertEquals(expect.length, written);
			b.close();

			assertEquals(expect.length, b.length());
			{
				final byte[] r = b.toByteArray();
				assertNotNull(r);
				assertEquals(expect.length, r.length);
				assertArrayEquals(expect, r);
			}
			{
				final ByteArrayOutputStream o = new ByteArrayOutputStream();
				b.writeTo(o, null);
				o.close();
				final byte[] r = o.toByteArray();
				assertEquals(expect.length, r.length);
				assertArrayEquals(expect, r);
			}
		} finally {
			b.destroy();
		}
	}

	@Test
	public void testHeap() throws IOException {
		@SuppressWarnings("resource" /* java 7 */)
		final TemporaryBuffer b = new TemporaryBuffer.Heap(2 * 8 * 1024);
		final byte[] r = new byte[8 * 1024];
		b.write(r);
		b.write(r);
		try {
			b.write(1);
			fail("accepted too many bytes of data");
		} catch (IOException e) {
			assertEquals("In-memory buffer limit exceeded", e.getMessage());
		}
	}

	@Test
	public void testHeapWithEstimatedSize() throws IOException {
		int sz = 2 * Block.SZ;
		try (TemporaryBuffer b = new TemporaryBuffer.Heap(sz / 2, sz)) {
			for (int i = 0; i < sz; i++) {
				b.write('x');
			}
			try {
				b.write(1);
				fail("accepted too many bytes of data");
			} catch (IOException e) {
				assertEquals("In-memory buffer limit exceeded", e.getMessage());
			}

			try (InputStream in = b.openInputStream()) {
				for (int i = 0; i < sz; i++) {
					assertEquals('x', in.read());
				}
				assertEquals(-1, in.read());
			}
		}
	}
}
