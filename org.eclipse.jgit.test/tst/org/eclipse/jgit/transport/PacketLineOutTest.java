/*
 * Copyright (C) 2009-2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.junit.Before;
import org.junit.Test;

// Note, test vectors created with:
//
// perl -e 'printf "%4.4x%s\n", 4+length($ARGV[0]),$ARGV[0]'

public class PacketLineOutTest {
	private ByteArrayOutputStream rawOut;

	private PacketLineOut out;

	@Before
	public void setUp() throws Exception {
		rawOut = new ByteArrayOutputStream();
		out = new PacketLineOut(rawOut);
	}

	// writeString

	@Test
	public void testWriteString1() throws IOException {
		out.writeString("a");
		out.writeString("bc");
		assertBuffer("0005a0006bc");
	}

	@Test
	public void testWriteString2() throws IOException {
		out.writeString("a\n");
		out.writeString("bc\n");
		assertBuffer("0006a\n0007bc\n");
	}

	@Test
	public void testWriteString3() throws IOException {
		out.writeString("");
		assertBuffer("0004");
	}

	// end

	@Test
	public void testWriteEnd() throws IOException {
		final int[] flushCnt = new int[1];
		final OutputStream mockout = new OutputStream() {
			@Override
			public void write(int arg0) throws IOException {
				rawOut.write(arg0);
			}

			@Override
			public void flush() throws IOException {
				flushCnt[0]++;
			}
		};

		new PacketLineOut(mockout).end();
		assertBuffer("0000");
		assertEquals(1, flushCnt[0]);
	}

	@Test
	public void testWriteDelim() throws IOException {
		out.writeDelim();
		assertBuffer("0001");
	}

	// writePacket

	@Test
	public void testWritePacket1() throws IOException {
		out.writePacket(new byte[] { 'a' });
		assertBuffer("0005a");
	}

	@Test
	public void testWritePacket2() throws IOException {
		out.writePacket(new byte[] { 'a', 'b', 'c', 'd' });
		assertBuffer("0008abcd");
	}

	@Test
	public void testWritePacket3() throws IOException {
		final int buflen = SideBandOutputStream.MAX_BUF - 5;
		final byte[] buf = new byte[buflen];
		for (int i = 0; i < buf.length; i++) {
			buf[i] = (byte) i;
		}
		out.writePacket(buf);
		out.flush();

		final byte[] act = rawOut.toByteArray();
		final String explen = Integer.toString(buf.length + 4, 16);
		assertEquals(4 + buf.length, act.length);
		assertEquals(new String(act, 0, 4, "UTF-8"), explen);
		for (int i = 0, j = 4; i < buf.length; i++, j++) {
			assertEquals(buf[i], act[j]);
		}
	}

	// flush

	@Test
	public void testFlush() throws IOException {
		final int[] flushCnt = new int[1];
		final OutputStream mockout = new OutputStream() {
			@Override
			public void write(int arg0) throws IOException {
				fail("should not write");
			}

			@Override
			public void flush() throws IOException {
				flushCnt[0]++;
			}
		};

		new PacketLineOut(mockout).flush();
		assertEquals(1, flushCnt[0]);
	}

	private void assertBuffer(String exp) {
		assertEquals(exp, new String(rawOut.toByteArray(),
				UTF_8));
	}
}
