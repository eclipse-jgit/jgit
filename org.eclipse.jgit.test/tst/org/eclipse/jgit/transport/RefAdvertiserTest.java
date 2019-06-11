/*
 * Copyright (C) 2016, Google Inc.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;
import org.eclipse.jgit.util.NB;
import org.junit.Test;

public class RefAdvertiserTest {
	@Test
	public void advertiser() throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		PacketLineOut pckOut = new PacketLineOut(buf);
		PacketLineOutRefAdvertiser adv = new PacketLineOutRefAdvertiser(pckOut);

		// Advertisement of capability and id both happen in order of call,
		// which may not match Git standards. Callers are responsible for
		// making calls in the correct ordering. Here in this test we do them
		// in a "wrong" order to assert the method just writes to the network.

		adv.advertiseCapability("test-1");
		adv.advertiseCapability("sideband");
		adv.advertiseId(id(1), "refs/heads/master");
		adv.advertiseId(id(4), "refs/" + padStart("F", 987));
		adv.advertiseId(id(2), "refs/heads/next");
		adv.advertiseId(id(3), "refs/Iñtërnâtiônàlizætiøn☃💩");
		adv.end();
		assertFalse(adv.isEmpty());

		PacketLineIn pckIn = new PacketLineIn(
				new ByteArrayInputStream(buf.toByteArray()));
		String s = pckIn.readStringRaw();
		assertEquals(id(1).name() + " refs/heads/master\0 test-1 sideband\n",
				s);

		s = pckIn.readStringRaw();
		assertEquals(id(4).name() + " refs/" + padStart("F", 987) + '\n', s);

		s = pckIn.readStringRaw();
		assertEquals(id(2).name() + " refs/heads/next\n", s);

		s = pckIn.readStringRaw();
		assertEquals(id(3).name() + " refs/Iñtërnâtiônàlizætiøn☃💩\n", s);

		s = pckIn.readStringRaw();
		assertTrue(PacketLineIn.isEnd(s));
	}

	private static ObjectId id(int i) {
		try (ObjectInserter.Formatter f = new ObjectInserter.Formatter()) {
			byte[] tmp = new byte[4];
			NB.encodeInt32(tmp, 0, i);
			return f.idFor(Constants.OBJ_BLOB, tmp);
		}
	}

	private static String padStart(String s, int len) {
		StringBuilder b = new StringBuilder(len);
		for (int i = s.length(); i < len; i++) {
			b.append((char) ('a' + (i % 26)));
		}
		return b.append(s).toString();
	}
}
