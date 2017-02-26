/*
 * Copyright (C) 2017, Google Inc.
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

package org.eclipse.jgit.util.sha1;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class SHA1Test {
	private static final String TEST1 = "abc";

	private static final String TEST2a = "abcdbcdecdefdefgefghfghighijhi";
	private static final String TEST2b = "jkijkljklmklmnlmnomnopnopq";
	private static final String TEST2 = TEST2a + TEST2b;

	@Test
	public void test0() throws NoSuchAlgorithmException {
		ObjectId exp = ObjectId
				.fromString("da39a3ee5e6b4b0d3255bfef95601890afd80709");

		MessageDigest m = MessageDigest.getInstance("SHA-1");
		m.update(new byte[] {});
		ObjectId m1 = ObjectId.fromRaw(m.digest());

		SHA1 s = SHA1.newInstance();
		s.update(new byte[] {});
		ObjectId s1 = ObjectId.fromRaw(s.digest());

		s.reset();
		s.update(new byte[] {});
		ObjectId s2 = s.toObjectId();

		assertEquals(m1, s1);
		assertEquals(exp, s1);
		assertEquals(exp, s2);
	}

	@Test
	public void test1() throws NoSuchAlgorithmException {
		ObjectId exp = ObjectId
				.fromString("a9993e364706816aba3e25717850c26c9cd0d89d");

		MessageDigest m = MessageDigest.getInstance("SHA-1");
		m.update(TEST1.getBytes(StandardCharsets.UTF_8));
		ObjectId m1 = ObjectId.fromRaw(m.digest());

		SHA1 s = SHA1.newInstance();
		s.update(TEST1.getBytes(StandardCharsets.UTF_8));
		ObjectId s1 = ObjectId.fromRaw(s.digest());

		s.reset();
		s.update(TEST1.getBytes(StandardCharsets.UTF_8));
		ObjectId s2 = s.toObjectId();

		assertEquals(m1, s1);
		assertEquals(exp, s1);
		assertEquals(exp, s2);
	}

	@Test
	public void test2() throws NoSuchAlgorithmException {
		ObjectId exp = ObjectId
				.fromString("84983e441c3bd26ebaae4aa1f95129e5e54670f1");

		MessageDigest m = MessageDigest.getInstance("SHA-1");
		m.update(TEST2.getBytes(StandardCharsets.UTF_8));
		ObjectId m1 = ObjectId.fromRaw(m.digest());

		SHA1 s = SHA1.newInstance();
		s.update(TEST2.getBytes(StandardCharsets.UTF_8));
		ObjectId s1 = ObjectId.fromRaw(s.digest());

		s.reset();
		s.update(TEST2.getBytes(StandardCharsets.UTF_8));
		ObjectId s2 = s.toObjectId();

		assertEquals(m1, s1);
		assertEquals(exp, s1);
		assertEquals(exp, s2);
	}
}
