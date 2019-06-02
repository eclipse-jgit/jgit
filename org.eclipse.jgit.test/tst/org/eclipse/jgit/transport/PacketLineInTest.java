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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

// Note, test vectors created with:
//
// perl -e 'printf "%4.4x%s\n", 4+length($ARGV[0]),$ARGV[0]'

public class PacketLineInTest {
	private ByteArrayInputStream rawIn;

	private PacketLineIn in;

	// readString

	@Test
	public void testReadString1() throws IOException {
		init("0006a\n0007bc\n");
		assertEquals("a", in.readString());
		assertEquals("bc", in.readString());
		assertEOF();
	}

	@Test
	public void testReadString2() throws IOException {
		init("0032want fcfcfb1fd94829c1a1704f894fc111d14770d34e\n");
		final String act = in.readString();
		assertEquals("want fcfcfb1fd94829c1a1704f894fc111d14770d34e", act);
		assertEOF();
	}

	@Test
	public void testReadString4() throws IOException {
		init("0005a0006bc");
		assertEquals("a", in.readString());
		assertEquals("bc", in.readString());
		assertEOF();
	}

	@Test
	public void testReadString5() throws IOException {
		// accept both upper and lower case
		init("000Fhi i am a s");
		assertEquals("hi i am a s", in.readString());
		assertEOF();

		init("000fhi i am a s");
		assertEquals("hi i am a s", in.readString());
		assertEOF();
	}

	@Test
	public void testReadString_LenHELO() {
		init("HELO");
		try {
			in.readString();
			fail("incorrectly accepted invalid packet header");
		} catch (IOException e) {
			assertEquals("Invalid packet line header: HELO", e.getMessage());
		}
	}

	@Test
	public void testReadString_Len0002() {
		init("0002");
		try {
			in.readString();
			fail("incorrectly accepted invalid packet header");
		} catch (IOException e) {
			assertEquals("Invalid packet line header: 0002", e.getMessage());
		}
	}

	@Test
	public void testReadString_Len0003() {
		init("0003");
		try {
			in.readString();
			fail("incorrectly accepted invalid packet header");
		} catch (IOException e) {
			assertEquals("Invalid packet line header: 0003", e.getMessage());
		}
	}

	@Test
	public void testReadString_Len0004() throws IOException {
		init("0004");
		final String act = in.readString();
		assertEquals("", act);
		assertFalse(PacketLineIn.isEnd(act));
		assertEOF();
	}

	@Test
	public void testReadString_End() throws IOException {
		init("0000");
		assertTrue(PacketLineIn.isEnd(in.readString()));
		assertEOF();
	}

	@Test
	public void testReadString_Delim() throws IOException {
		init("0001");
		assertTrue(PacketLineIn.isDelimiter(in.readString()));
		assertEOF();
	}

	// readStringNoLF

	@Test
	public void testReadStringRaw1() throws IOException {
		init("0005a0006bc");
		assertEquals("a", in.readStringRaw());
		assertEquals("bc", in.readStringRaw());
		assertEOF();
	}

	@Test
	public void testReadStringRaw2() throws IOException {
		init("0031want fcfcfb1fd94829c1a1704f894fc111d14770d34e");
		final String act = in.readStringRaw();
		assertEquals("want fcfcfb1fd94829c1a1704f894fc111d14770d34e", act);
		assertEOF();
	}

	@Test
	public void testReadStringRaw3() throws IOException {
		init("0004");
		final String act = in.readStringRaw();
		assertEquals("", act);
		assertFalse(PacketLineIn.isEnd(act));
		assertEOF();
	}

	@Test
	public void testReadStringRaw_End() throws IOException {
		init("0000");
		assertTrue(PacketLineIn.isEnd(in.readString()));
		assertEOF();
	}

	@Test
	public void testReadStringRaw4() {
		init("HELO");
		try {
			in.readStringRaw();
			fail("incorrectly accepted invalid packet header");
		} catch (IOException e) {
			assertEquals("Invalid packet line header: HELO", e.getMessage());
		}
	}

	// readACK

	@Test
	public void testReadACK_NAK() throws IOException {
		final ObjectId expid = ObjectId
				.fromString("fcfcfb1fd94829c1a1704f894fc111d14770d34e");
		final MutableObjectId actid = new MutableObjectId();
		actid.fromString(expid.name());

		init("0008NAK\n");
		assertSame(PacketLineIn.AckNackResult.NAK, in.readACK(actid));
		assertEquals(expid, actid);
		assertEOF();
	}

	@Test
	public void testReadACK_ACK1() throws IOException {
		final ObjectId expid = ObjectId
				.fromString("fcfcfb1fd94829c1a1704f894fc111d14770d34e");
		final MutableObjectId actid = new MutableObjectId();

		init("0031ACK fcfcfb1fd94829c1a1704f894fc111d14770d34e\n");
		assertSame(PacketLineIn.AckNackResult.ACK, in.readACK(actid));
		assertEquals(expid, actid);
		assertEOF();
	}

	@Test
	public void testReadACK_ACKcontinue1() throws IOException {
		final ObjectId expid = ObjectId
				.fromString("fcfcfb1fd94829c1a1704f894fc111d14770d34e");
		final MutableObjectId actid = new MutableObjectId();

		init("003aACK fcfcfb1fd94829c1a1704f894fc111d14770d34e continue\n");
		assertSame(PacketLineIn.AckNackResult.ACK_CONTINUE, in.readACK(actid));
		assertEquals(expid, actid);
		assertEOF();
	}

	@Test
	public void testReadACK_ACKcommon1() throws IOException {
		final ObjectId expid = ObjectId
				.fromString("fcfcfb1fd94829c1a1704f894fc111d14770d34e");
		final MutableObjectId actid = new MutableObjectId();

		init("0038ACK fcfcfb1fd94829c1a1704f894fc111d14770d34e common\n");
		assertSame(PacketLineIn.AckNackResult.ACK_COMMON, in.readACK(actid));
		assertEquals(expid, actid);
		assertEOF();
	}

	@Test
	public void testReadACK_ACKready1() throws IOException {
		final ObjectId expid = ObjectId
				.fromString("fcfcfb1fd94829c1a1704f894fc111d14770d34e");
		final MutableObjectId actid = new MutableObjectId();

		init("0037ACK fcfcfb1fd94829c1a1704f894fc111d14770d34e ready\n");
		assertSame(PacketLineIn.AckNackResult.ACK_READY, in.readACK(actid));
		assertEquals(expid, actid);
		assertEOF();
	}

	@Test
	public void testReadACK_Invalid1() {
		init("HELO");
		try {
			in.readACK(new MutableObjectId());
			fail("incorrectly accepted invalid packet header");
		} catch (IOException e) {
			assertEquals("Invalid packet line header: HELO", e.getMessage());
		}
	}

	@Test
	public void testReadACK_Invalid2() {
		init("0009HELO\n");
		try {
			in.readACK(new MutableObjectId());
			fail("incorrectly accepted invalid ACK/NAK");
		} catch (IOException e) {
			assertEquals("Expected ACK/NAK, got: HELO", e.getMessage());
		}
	}

	@Test
	public void testReadACK_Invalid3() {
		String s = "ACK fcfcfb1fd94829c1a1704f894fc111d14770d34e neverhappen";
		init("003d" + s + "\n");
		try {
			in.readACK(new MutableObjectId());
			fail("incorrectly accepted unsupported ACK status");
		} catch (IOException e) {
			assertEquals("Expected ACK/NAK, got: " + s, e.getMessage());
		}
	}

	@Test
	public void testReadACK_Invalid4() {
		init("0000");
		try {
			in.readACK(new MutableObjectId());
			fail("incorrectly accepted no ACK/NAK");
		} catch (IOException e) {
			assertEquals("Expected ACK/NAK, found EOF", e.getMessage());
		}
	}

	@Test
	public void testReadACK_ERR() throws IOException {
		init("001aERR want is not valid\n");
		try {
			in.readACK(new MutableObjectId());
			fail("incorrectly accepted ERR");
		} catch (PackProtocolException e) {
			assertEquals("want is not valid", e.getMessage());
		}
	}

	// test support

	private void init(String msg) {
		rawIn = new ByteArrayInputStream(Constants.encodeASCII(msg));
		in = new PacketLineIn(rawIn);
	}

	private void assertEOF() {
		assertEquals(-1, rawIn.read());
	}
}
