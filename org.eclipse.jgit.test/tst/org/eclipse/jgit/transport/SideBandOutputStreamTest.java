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

package org.eclipse.jgit.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import junit.framework.TestCase;

import org.eclipse.jgit.lib.Constants;

// Note, test vectors created with:
//
// perl -e 'printf "%4.4x%s\n", 4+length($ARGV[0]),$ARGV[0]'

public class SideBandOutputStreamTest extends TestCase {
	private ByteArrayOutputStream rawOut;

	private PacketLineOut pckOut;

	protected void setUp() throws Exception {
		super.setUp();
		rawOut = new ByteArrayOutputStream();
		pckOut = new PacketLineOut(rawOut);
	}

	public void testWrite_CH_DATA() throws IOException {
		final SideBandOutputStream out;
		out = new SideBandOutputStream(SideBandOutputStream.CH_DATA, pckOut);
		out.write(new byte[] { 'a', 'b', 'c' });
		assertBuffer("0008\001abc");
	}

	public void testWrite_CH_PROGRESS() throws IOException {
		final SideBandOutputStream out;
		out = new SideBandOutputStream(SideBandOutputStream.CH_PROGRESS, pckOut);
		out.write(new byte[] { 'a', 'b', 'c' });
		assertBuffer("0008\002abc");
	}

	public void testWrite_CH_ERROR() throws IOException {
		final SideBandOutputStream out;
		out = new SideBandOutputStream(SideBandOutputStream.CH_ERROR, pckOut);
		out.write(new byte[] { 'a', 'b', 'c' });
		assertBuffer("0008\003abc");
	}

	public void testWrite_Small() throws IOException {
		final SideBandOutputStream out;
		out = new SideBandOutputStream(SideBandOutputStream.CH_DATA, pckOut);
		out.write('a');
		out.write('b');
		out.write('c');
		assertBuffer("0006\001a0006\001b0006\001c");
	}

	public void testWrite_Large() throws IOException {
		final int buflen = SideBandOutputStream.MAX_BUF
				- SideBandOutputStream.HDR_SIZE;
		final byte[] buf = new byte[buflen];
		for (int i = 0; i < buf.length; i++) {
			buf[i] = (byte) i;
		}

		final SideBandOutputStream out;
		out = new SideBandOutputStream(SideBandOutputStream.CH_DATA, pckOut);
		out.write(buf);

		final byte[] act = rawOut.toByteArray();
		final String explen = Integer.toString(buf.length + 5, 16);
		assertEquals(5 + buf.length, act.length);
		assertEquals(new String(act, 0, 4, "UTF-8"), explen);
		assertEquals(1, act[4]);
		for (int i = 0, j = 5; i < buf.length; i++, j++) {
			assertEquals(buf[i], act[j]);
		}
	}

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

		new SideBandOutputStream(SideBandOutputStream.CH_DATA,
				new PacketLineOut(mockout)).flush();
		assertEquals(0, flushCnt[0]);

		new SideBandOutputStream(SideBandOutputStream.CH_ERROR,
				new PacketLineOut(mockout)).flush();
		assertEquals(1, flushCnt[0]);

		new SideBandOutputStream(SideBandOutputStream.CH_PROGRESS,
				new PacketLineOut(mockout)).flush();
		assertEquals(2, flushCnt[0]);
	}

	private void assertBuffer(final String exp) throws IOException {
		assertEquals(exp, new String(rawOut.toByteArray(),
				Constants.CHARACTER_ENCODING));
	}
}
