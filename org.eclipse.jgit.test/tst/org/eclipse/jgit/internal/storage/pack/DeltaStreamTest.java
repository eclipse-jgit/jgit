/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.internal.storage.pack;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.pack.BinaryDelta;
import org.eclipse.jgit.internal.storage.pack.DeltaEncoder;
import org.eclipse.jgit.internal.storage.pack.DeltaStream;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.IO;
import org.junit.Before;
import org.junit.Test;

public class DeltaStreamTest {
	private TestRng rng;

	private ByteArrayOutputStream deltaBuf;

	private DeltaEncoder deltaEnc;

	private byte[] base;

	private byte[] data;

	private int dataPtr;

	private byte[] delta;

	private TestRng getRng() {
		if (rng == null)
			rng = new TestRng(JGitTestUtil.getName());
		return rng;
	}

	@Before
	public void setUp() throws Exception {
		deltaBuf = new ByteArrayOutputStream();
	}

	@Test
	public void testCopy_SingleOp() throws IOException {
		init((1 << 16) + 1, (1 << 8) + 1);
		copy(0, data.length);
		assertValidState();
	}

	@Test
	public void testCopy_MaxSize() throws IOException {
		int max = (0xff << 16) + (0xff << 8) + 0xff;
		init(1 + max, max);
		copy(1, max);
		assertValidState();
	}

	@Test
	public void testCopy_64k() throws IOException {
		init(0x10000 + 2, 0x10000 + 1);
		copy(1, 0x10000);
		copy(0x10001, 1);
		assertValidState();
	}

	@Test
	public void testCopy_Gap() throws IOException {
		init(256, 8);
		copy(4, 4);
		copy(128, 4);
		assertValidState();
	}

	@Test
	public void testCopy_OutOfOrder() throws IOException {
		init((1 << 16) + 1, (1 << 16) + 1);
		copy(1 << 8, 1 << 8);
		copy(0, data.length - dataPtr);
		assertValidState();
	}

	@Test
	public void testInsert_SingleOp() throws IOException {
		init((1 << 16) + 1, 2);
		insert("hi");
		assertValidState();
	}

	@Test
	public void testInsertAndCopy() throws IOException {
		init(8, 512);
		insert(new byte[127]);
		insert(new byte[127]);
		insert(new byte[127]);
		insert(new byte[125]);
		copy(2, 6);
		assertValidState();
	}

	@Test
	public void testSkip() throws IOException {
		init(32, 15);
		copy(2, 2);
		insert("ab");
		insert("cd");
		copy(4, 4);
		copy(0, 2);
		insert("efg");
		assertValidState();

		for (int p = 0; p < data.length; p++) {
			byte[] act = new byte[data.length];
			System.arraycopy(data, 0, act, 0, p);
			DeltaStream in = open();
			IO.skipFully(in, p);
			assertEquals(data.length - p, in.read(act, p, data.length - p));
			assertEquals(-1, in.read());
			assertTrue("skipping " + p, Arrays.equals(data, act));
		}

		// Skip all the way to the end should still recognize EOF.
		DeltaStream in = open();
		IO.skipFully(in, data.length);
		assertEquals(-1, in.read());
		assertEquals(0, in.skip(1));

		// Skip should not open the base as we move past it, but it
		// will open when we need to start copying data from it.
		final boolean[] opened = new boolean[1];
		in = new DeltaStream(new ByteArrayInputStream(delta)) {
			@Override
			protected long getBaseSize() throws IOException {
				return base.length;
			}

			@Override
			protected InputStream openBase() throws IOException {
				opened[0] = true;
				return new ByteArrayInputStream(base);
			}
		};
		IO.skipFully(in, 7);
		assertFalse("not yet open", opened[0]);
		assertEquals(data[7], in.read());
		assertTrue("now open", opened[0]);
	}

	@Test
	public void testIncorrectBaseSize() throws IOException {
		init(4, 4);
		copy(0, 4);
		assertValidState();

		DeltaStream in = new DeltaStream(new ByteArrayInputStream(delta)) {
			@Override
			protected long getBaseSize() throws IOException {
				return 128;
			}

			@Override
			protected InputStream openBase() throws IOException {
				return new ByteArrayInputStream(base);
			}
		};
		try {
			in.read(new byte[4]);
			fail("did not throw an exception");
		} catch (CorruptObjectException e) {
			assertEquals(JGitText.get().baseLengthIncorrect, e.getMessage());
		}

		in = new DeltaStream(new ByteArrayInputStream(delta)) {
			@Override
			protected long getBaseSize() throws IOException {
				return 4;
			}

			@Override
			protected InputStream openBase() throws IOException {
				return new ByteArrayInputStream(new byte[0]);
			}
		};
		try {
			in.read(new byte[4]);
			fail("did not throw an exception");
		} catch (CorruptObjectException e) {
			assertEquals(JGitText.get().baseLengthIncorrect, e.getMessage());
		}
	}

	private void init(int baseSize, int dataSize) throws IOException {
		base = getRng().nextBytes(baseSize);
		data = new byte[dataSize];
		deltaEnc = new DeltaEncoder(deltaBuf, baseSize, dataSize);
	}

	private void copy(int offset, int len) throws IOException {
		System.arraycopy(base, offset, data, dataPtr, len);
		deltaEnc.copy(offset, len);
		assertEquals(deltaBuf.size(), deltaEnc.getSize());
		dataPtr += len;
	}

	private void insert(String text) throws IOException {
		insert(Constants.encode(text));
	}

	private void insert(byte[] text) throws IOException {
		System.arraycopy(text, 0, data, dataPtr, text.length);
		deltaEnc.insert(text);
		assertEquals(deltaBuf.size(), deltaEnc.getSize());
		dataPtr += text.length;
	}

	private void assertValidState() throws IOException {
		assertEquals("test filled example result", data.length, dataPtr);

		delta = deltaBuf.toByteArray();
		assertEquals(base.length, BinaryDelta.getBaseSize(delta));
		assertEquals(data.length, BinaryDelta.getResultSize(delta));
		assertArrayEquals(data, BinaryDelta.apply(base, delta));

		// Assert that a single bulk read produces the correct result.
		//
		byte[] act = new byte[data.length];
		DeltaStream in = open();
		assertEquals(data.length, in.getSize());
		assertEquals(data.length, in.read(act));
		assertEquals(-1, in.read());
		assertTrue("bulk read has same content", Arrays.equals(data, act));

		// Assert that smaller tiny reads have the same result too.
		//
		act = new byte[data.length];
		in = open();
		int read = 0;
		while (read < data.length) {
			int n = in.read(act, read, 128);
			if (n <= 0)
				break;
			read += n;
		}
		assertEquals(data.length, read);
		assertEquals(-1, in.read());
		assertTrue("small reads have same content", Arrays.equals(data, act));
	}

	private DeltaStream open() throws IOException {
		return new DeltaStream(new ByteArrayInputStream(delta)) {
			@Override
			protected long getBaseSize() throws IOException {
				return base.length;
			}

			@Override
			protected InputStream openBase() throws IOException {
				return new ByteArrayInputStream(base);
			}
		};
	}
}
