/*
 * Copyright (C) 2009-2010, Google Inc.
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

package org.eclipse.jgit.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.jgit.lib.Constants;
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

	private void assertBuffer(final String exp) throws IOException {
		assertEquals(exp, new String(rawOut.toByteArray(),
				Constants.CHARACTER_ENCODING));
	}
}
