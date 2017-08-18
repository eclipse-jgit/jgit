/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com>
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

package org.eclipse.jgit.lfs.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.lfs.errors.InvalidLongObjectIdException;
import org.eclipse.jgit.lfs.test.LongObjectIdTestUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/*
 * Ported to SHA-256 from org.eclipse.jgit.lib.ObjectIdTest
 */
public class LongObjectIdTest {
	private static Path tmp;

	@BeforeClass
	public static void setup() throws IOException {
		tmp = Files.createTempDirectory("jgit_test_");
	}

	@AfterClass
	public static void tearDown() throws IOException {
		FileUtils.delete(tmp.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
	}

	@Test
	public void test001_toString() {
		final String x = "8367b0edc81df80e6b42eb1b71f783111224e058cb3da37894d065d2deb7ab0a";
		final LongObjectId oid = LongObjectId.fromString(x);
		assertEquals(x, oid.name());
	}

	@Test
	public void test002_toString() {
		final String x = "140ce71d628cceb78e3709940ba52a651a0c4a9c1400f2e15e998a1a43887edf";
		final LongObjectId oid = LongObjectId.fromString(x);
		assertEquals(x, oid.name());
	}

	@Test
	public void test003_equals() {
		final String x = "8367b0edc81df80e6b42eb1b71f783111224e058cb3da37894d065d2deb7ab0a";
		final LongObjectId a = LongObjectId.fromString(x);
		final LongObjectId b = LongObjectId.fromString(x);
		assertEquals(a.hashCode(), b.hashCode());
		assertEquals("a and b should be equal", b, a);
	}

	@Test
	public void test004_isId() {
		assertTrue("valid id", LongObjectId.isId(
				"8367b0edc81df80e6b42eb1b71f783111224e058cb3da37894d065d2deb7ab0a"));
	}

	@Test
	public void test005_notIsId() {
		assertFalse("bob is not an id", LongObjectId.isId("bob"));
	}

	@Test
	public void test006_notIsId() {
		assertFalse("63 digits is not an id", LongObjectId.isId(
				"8367b0edc81df80e6b42eb1b71f783111224e058cb3da37894d065d2deb7ab0"));
	}

	@Test
	public void test007_isId() {
		assertTrue("uppercase is accepted", LongObjectId.isId(
				"8367b0edc81df80e6b42eb1b71f783111224e058cb3da37894d065d2dEb7ab0A"));
	}

	@Test
	public void test008_notIsId() {
		assertFalse("g is not a valid hex digit", LongObjectId.isId(
				"g367b0edc81df80e6b42eb1b71f783111224e058cb3da37894d065d2deb7ab0a"));
	}

	@Test
	public void test009_toString() {
		final String x = "140ce71d628cceb78e3709940ba52a651a0c4a9c1400f2e15e998a1a43887edf";
		final LongObjectId oid = LongObjectId.fromString(x);
		assertEquals(x, LongObjectId.toString(oid));
	}

	@Test
	public void test010_toString() {
		final String x = "0000000000000000000000000000000000000000000000000000000000000000";
		assertEquals(x, LongObjectId.toString(null));
	}

	@Test
	public void test011_toString() {
		final String x = "0123456789ABCDEFabcdef01234567890123456789ABCDEFabcdef0123456789";
		final LongObjectId oid = LongObjectId.fromString(x);
		assertEquals(x.toLowerCase(Locale.ROOT), oid.name());
	}

	@Test
	public void testGetByte() {
		byte[] raw = new byte[32];
		for (int i = 0; i < 32; i++)
			raw[i] = (byte) (0xa0 + i);
		LongObjectId id = LongObjectId.fromRaw(raw);

		assertEquals(raw[0] & 0xff, id.getFirstByte());
		assertEquals(raw[0] & 0xff, id.getByte(0));
		assertEquals(raw[1] & 0xff, id.getByte(1));
		assertEquals(raw[1] & 0xff, id.getSecondByte());

		for (int i = 2; i < 32; i++) {
			assertEquals("index " + i, raw[i] & 0xff, id.getByte(i));
		}
		try {
			id.getByte(32);
			fail("LongObjectId has 32 byte only");
		} catch (ArrayIndexOutOfBoundsException e) {
			// expected
		}
	}

	@Test
	public void testSetByte() {
		byte[] exp = new byte[32];
		for (int i = 0; i < 32; i++) {
			exp[i] = (byte) (0xa0 + i);
		}

		MutableLongObjectId id = new MutableLongObjectId();
		id.fromRaw(exp);
		assertEquals(LongObjectId.fromRaw(exp).name(), id.name());

		id.setByte(0, 0x10);
		assertEquals(0x10, id.getByte(0));
		exp[0] = 0x10;
		assertEquals(LongObjectId.fromRaw(exp).name(), id.name());

		for (int p = 1; p < 32; p++) {
			id.setByte(p, 0x10 + p);
			assertEquals(0x10 + p, id.getByte(p));
			exp[p] = (byte) (0x10 + p);
			assertEquals(LongObjectId.fromRaw(exp).name(), id.name());
		}

		for (int p = 0; p < 32; p++) {
			id.setByte(p, 0x80 + p);
			assertEquals(0x80 + p, id.getByte(p));
			exp[p] = (byte) (0x80 + p);
			assertEquals(LongObjectId.fromRaw(exp).name(), id.name());
		}
	}

	@Test
	public void testZeroId() {
		AnyLongObjectId zero = new LongObjectId(0L, 0L, 0L, 0L);
		assertEquals(zero, LongObjectId.zeroId());
		assertEquals(
				"0000000000000000000000000000000000000000000000000000000000000000",
				LongObjectId.zeroId().name());
	}

	@Test
	public void testEquals() {
		AnyLongObjectId id1 = LongObjectIdTestUtils.hash("test");
		assertTrue("id should equal itself", id1.equals(id1));
		AnyLongObjectId id2 = new LongObjectId(id1);
		assertEquals("objects should be equals", id1, id2);

		id2 = LongObjectIdTestUtils.hash("other");
		assertNotEquals("objects should be not equal", id1, id2);
	}

	@Test
	public void testCopyRawBytes() {
		AnyLongObjectId id1 = LongObjectIdTestUtils.hash("test");
		AnyLongObjectId id2 = new LongObjectId(id1);

		byte[] buf = new byte[64];
		id1.copyRawTo(buf, 0);
		id2.copyRawTo(buf, 32);
		assertTrue("objects should be equals",
				LongObjectId.equals(buf, 0, buf, 32));
	}

	@Test
	public void testCopyRawLongs() {
		long[] a = new long[4];
		a[0] = 1L;
		a[1] = 2L;
		a[2] = 3L;
		a[3] = 4L;
		AnyLongObjectId id1 = new LongObjectId(a[0], a[1], a[2], a[3]);
		AnyLongObjectId id2 = LongObjectId.fromRaw(a);
		assertEquals("objects should be equals", id1, id2);
	}

	@Test
	public void testCopyFromStringInvalid() {
		AnyLongObjectId id1 = LongObjectIdTestUtils.hash("test");
		try {
			LongObjectId.fromString(id1.name() + "01234");
			fail("expected InvalidLongObjectIdException");
		} catch (InvalidLongObjectIdException e) {
			assertEquals("Invalid id: " + id1.name() + "01234",
					e.getMessage());
		}
	}

	@Test
	public void testCopyFromStringByte() {
		AnyLongObjectId id1 = LongObjectIdTestUtils.hash("test");
		byte[] buf = new byte[64];
		Charset cs = StandardCharsets.US_ASCII;
		cs.encode(id1.name()).get(buf);
		AnyLongObjectId id2 = LongObjectId.fromString(buf, 0);
		assertEquals("objects should be equals", id1, id2);
	}

	@Test
	public void testHashFile() throws IOException {
		AnyLongObjectId id1 = LongObjectIdTestUtils.hash("test");
		Path f = tmp.resolve("test");
		JGitTestUtil.write(f.toFile(), "test");
		AnyLongObjectId id2 = LongObjectIdTestUtils.hash(f);
		assertEquals("objects should be equals", id1, id2);
	}

	@Test
	public void testCompareTo() {
		AnyLongObjectId id1 = LongObjectId.fromString(
				"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
		assertEquals(0, id1.compareTo(LongObjectId.fromString(
				"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")));
		AnyLongObjectId self = id1;
		assertEquals(0, id1.compareTo(self));

		assertEquals(-1, id1.compareTo(LongObjectId.fromString(
				"1123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")));
		assertEquals(-1, id1.compareTo(LongObjectId.fromString(
				"0123456789abcdef1123456789abcdef0123456789abcdef0123456789abcdef")));
		assertEquals(-1, id1.compareTo(LongObjectId.fromString(
				"0123456789abcdef0123456789abcdef1123456789abcdef0123456789abcdef")));
		assertEquals(-1, id1.compareTo(LongObjectId.fromString(
				"0123456789abcdef0123456789abcdef0123456789abcdef1123456789abcdef")));

		assertEquals(1, id1.compareTo(LongObjectId.fromString(
				"0023456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")));
		assertEquals(1, id1.compareTo(LongObjectId.fromString(
				"0123456789abcdef0023456789abcdef0123456789abcdef0123456789abcdef")));
		assertEquals(1, id1.compareTo(LongObjectId.fromString(
				"0123456789abcdef0123456789abcdef0023456789abcdef0123456789abcdef")));
		assertEquals(1, id1.compareTo(LongObjectId.fromString(
				"0123456789abcdef0123456789abcdef0123456789abcdef0023456789abcdef")));
	}

	@Test
	public void testCompareToByte() {
		AnyLongObjectId id1 = LongObjectId.fromString(
				"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
		byte[] buf = new byte[32];
		id1.copyRawTo(buf, 0);
		assertEquals(0, id1.compareTo(buf, 0));

		LongObjectId
				.fromString(
						"1123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
				.copyRawTo(buf, 0);
		assertEquals(-1, id1.compareTo(buf, 0));

		LongObjectId
				.fromString(
						"0023456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
				.copyRawTo(buf, 0);
		assertEquals(1, id1.compareTo(buf, 0));
	}

	@Test
	public void testCompareToLong() {
		AnyLongObjectId id1 = new LongObjectId(1L, 2L, 3L, 4L);
		long[] buf = new long[4];
		id1.copyRawTo(buf, 0);
		assertEquals(0, id1.compareTo(buf, 0));

		new LongObjectId(2L, 2L, 3L, 4L).copyRawTo(buf, 0);
		assertEquals(-1, id1.compareTo(buf, 0));

		new LongObjectId(0L, 2L, 3L, 4L).copyRawTo(buf, 0);
		assertEquals(1, id1.compareTo(buf, 0));
	}

	@Test
	public void testCopyToByte() {
		AnyLongObjectId id1 = LongObjectIdTestUtils.hash("test");
		byte[] buf = new byte[64];
		id1.copyTo(buf, 0);
		assertEquals(id1, LongObjectId.fromString(buf, 0));
	}

	@Test
	public void testCopyRawToByteBuffer() {
		AnyLongObjectId id1 = LongObjectIdTestUtils.hash("test");
		ByteBuffer buf = ByteBuffer.allocate(32);
		id1.copyRawTo(buf);
		assertEquals(id1, LongObjectId.fromRaw(buf.array(), 0));
	}

	@Test
	public void testCopyToByteBuffer() {
		AnyLongObjectId id1 = LongObjectIdTestUtils.hash("test");
		ByteBuffer buf = ByteBuffer.allocate(64);
		id1.copyTo(buf);
		assertEquals(id1, LongObjectId.fromString(buf.array(), 0));
	}

	@Test
	public void testCopyRawToOutputStream() throws IOException {
		AnyLongObjectId id1 = LongObjectIdTestUtils.hash("test");
		ByteArrayOutputStream os = new ByteArrayOutputStream(32);
		id1.copyRawTo(os);
		assertEquals(id1, LongObjectId.fromRaw(os.toByteArray(), 0));
	}

	@Test
	public void testCopyToOutputStream() throws IOException {
		AnyLongObjectId id1 = LongObjectIdTestUtils.hash("test");
		ByteArrayOutputStream os = new ByteArrayOutputStream(64);
		id1.copyTo(os);
		assertEquals(id1, LongObjectId.fromString(os.toByteArray(), 0));
	}

	@Test
	public void testCopyToWriter() throws IOException {
		AnyLongObjectId id1 = LongObjectIdTestUtils.hash("test");
		ByteArrayOutputStream os = new ByteArrayOutputStream(64);
		OutputStreamWriter w = new OutputStreamWriter(os, Constants.CHARSET);
		id1.copyTo(w);
		w.close();
		assertEquals(id1, LongObjectId.fromString(os.toByteArray(), 0));
	}

	@Test
	public void testCopyToWriterWithBuf() throws IOException {
		AnyLongObjectId id1 = LongObjectIdTestUtils.hash("test");
		ByteArrayOutputStream os = new ByteArrayOutputStream(64);
		OutputStreamWriter w = new OutputStreamWriter(os, Constants.CHARSET);
		char[] buf = new char[64];
		id1.copyTo(buf, w);
		w.close();
		assertEquals(id1, LongObjectId.fromString(os.toByteArray(), 0));
	}

	@Test
	public void testCopyToStringBuilder() {
		AnyLongObjectId id1 = LongObjectIdTestUtils.hash("test");
		StringBuilder sb = new StringBuilder();
		char[] buf = new char[64];
		id1.copyTo(buf, sb);
		assertEquals(id1, LongObjectId.fromString(sb.toString()));
	}

	@Test
	public void testCopy() {
		AnyLongObjectId id1 = LongObjectIdTestUtils.hash("test");
		assertEquals(id1.copy(), id1);
		MutableLongObjectId id2 = new MutableLongObjectId();
		id2.fromObjectId(id1);
		assertEquals(id1, id2.copy());
	}
}
