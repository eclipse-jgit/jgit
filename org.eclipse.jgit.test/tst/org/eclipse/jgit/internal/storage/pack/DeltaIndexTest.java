/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.pack;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.lib.Constants;
import org.junit.Before;
import org.junit.Test;

public class DeltaIndexTest {
	private TestRng rng;

	private ByteArrayOutputStream actDeltaBuf;

	private ByteArrayOutputStream expDeltaBuf;

	private DeltaEncoder expDeltaEnc;

	private byte[] src;

	private byte[] dst;

	private ByteArrayOutputStream dstBuf;

	private TestRng getRng() {
		if (rng == null)
			rng = new TestRng(JGitTestUtil.getName());
		return rng;
	}

	@Before
	public void setUp() throws Exception {
		actDeltaBuf = new ByteArrayOutputStream();
		expDeltaBuf = new ByteArrayOutputStream();
		expDeltaEnc = new DeltaEncoder(expDeltaBuf, 0, 0);
		dstBuf = new ByteArrayOutputStream();
	}

	@Test
	public void testInsertWholeObject_Length12() throws IOException {
		src = getRng().nextBytes(12);
		insert(src);
		doTest();
	}

	@Test
	public void testCopyWholeObject_Length128() throws IOException {
		src = getRng().nextBytes(128);
		copy(0, 128);
		doTest();
	}

	@Test
	public void testCopyWholeObject_Length123() throws IOException {
		src = getRng().nextBytes(123);
		copy(0, 123);
		doTest();
	}

	@Test
	public void testCopyZeros_Length128() throws IOException {
		src = new byte[2048];
		copy(0, src.length);
		doTest();

		// The index should be smaller than expected due to the chain
		// being truncated. Without truncation we would expect to have
		// more than 3584 bytes used.
		//
		assertEquals(2636, new DeltaIndex(src).getIndexSize());
	}

	@Test
	public void testShuffleSegments() throws IOException {
		src = getRng().nextBytes(128);
		copy(64, 64);
		copy(0, 64);
		doTest();
	}

	@Test
	public void testInsertHeadMiddle() throws IOException {
		src = getRng().nextBytes(1024);
		insert("foo");
		copy(0, 512);
		insert("yet more fooery");
		copy(0, 512);
		doTest();
	}

	@Test
	public void testInsertTail() throws IOException {
		src = getRng().nextBytes(1024);
		copy(0, 512);
		insert("bar");
		doTest();
	}

	@Test
	public void testIndexSize() {
		src = getRng().nextBytes(1024);
		DeltaIndex di = new DeltaIndex(src);
		assertEquals(1860, di.getIndexSize());
		assertEquals("DeltaIndex[2 KiB]", di.toString());
	}

	@Test
	public void testLimitObjectSize_Length12InsertFails() throws IOException {
		src = getRng().nextBytes(12);
		dst = src;

		DeltaIndex di = new DeltaIndex(src);
		assertFalse(di.encode(actDeltaBuf, dst, src.length));
	}

	@Test
	public void testLimitObjectSize_Length130InsertFails() throws IOException {
		src = getRng().nextBytes(130);
		dst = getRng().nextBytes(130);

		DeltaIndex di = new DeltaIndex(src);
		assertFalse(di.encode(actDeltaBuf, dst, src.length));
	}

	@Test
	public void testLimitObjectSize_Length130CopyOk() throws IOException {
		src = getRng().nextBytes(130);
		copy(0, 130);
		dst = dstBuf.toByteArray();

		DeltaIndex di = new DeltaIndex(src);
		assertTrue(di.encode(actDeltaBuf, dst, dst.length));

		byte[] actDelta = actDeltaBuf.toByteArray();
		byte[] expDelta = expDeltaBuf.toByteArray();

		assertEquals(BinaryDelta.format(expDelta, false), //
				BinaryDelta.format(actDelta, false));
	}

	@Test
	public void testLimitObjectSize_Length130CopyFails() throws IOException {
		src = getRng().nextBytes(130);
		copy(0, 130);
		dst = dstBuf.toByteArray();

		// The header requires 4 bytes for these objects, so a target length
		// of 5 is bigger than the copy instruction and should cause an abort.
		//
		DeltaIndex di = new DeltaIndex(src);
		assertFalse(di.encode(actDeltaBuf, dst, 5));
		assertEquals(4, actDeltaBuf.size());
	}

	@Test
	public void testLimitObjectSize_InsertFrontFails() throws IOException {
		src = getRng().nextBytes(130);
		insert("eight");
		copy(0, 130);
		dst = dstBuf.toByteArray();

		// The header requires 4 bytes for these objects, so a target length
		// of 5 is bigger than the copy instruction and should cause an abort.
		//
		DeltaIndex di = new DeltaIndex(src);
		assertFalse(di.encode(actDeltaBuf, dst, 5));
		assertEquals(4, actDeltaBuf.size());
	}

	private void copy(int offset, int len) throws IOException {
		dstBuf.write(src, offset, len);
		expDeltaEnc.copy(offset, len);
	}

	private void insert(String text) throws IOException {
		insert(Constants.encode(text));
	}

	private void insert(byte[] text) throws IOException {
		dstBuf.write(text);
		expDeltaEnc.insert(text);
	}

	private void doTest() throws IOException {
		dst = dstBuf.toByteArray();

		DeltaIndex di = new DeltaIndex(src);
		di.encode(actDeltaBuf, dst);

		byte[] actDelta = actDeltaBuf.toByteArray();
		byte[] expDelta = expDeltaBuf.toByteArray();

		assertEquals(BinaryDelta.format(expDelta, false), //
				BinaryDelta.format(actDelta, false));

		assertTrue("delta is not empty", actDelta.length > 0);
		assertEquals(src.length, BinaryDelta.getBaseSize(actDelta));
		assertEquals(dst.length, BinaryDelta.getResultSize(actDelta));
		assertArrayEquals(dst, BinaryDelta.apply(src, actDelta));
	}
}
