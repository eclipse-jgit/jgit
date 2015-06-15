/*
 * Copyright (C) 2015, Google Inc.
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
 *	 notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *	 copyright notice, this list of conditions and the following
 *	 disclaimer in the documentation and/or other materials provided
 *	 with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *	 names of its contributors may be used to endorse or promote
 *	 products derived from this software without specific prior
 *	 written permission.
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
import static org.junit.Assert.assertNotEquals;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushCertificate.NonceStatus;
import org.junit.Before;
import org.junit.Test;

/** Test for HMAC SHA-1 certificate verifier. */
public class HMACSHA1NonceGeneratorTest {
	private static final long TS = 1433954361;

	private HMACSHA1NonceGenerator gen;
	private Repository db;

	@Before
	public void setUp() {
		gen = new HMACSHA1NonceGenerator("sekret");
		db = new InMemoryRepository(new DfsRepositoryDescription("db"));
	}

	@Test
	public void missing() throws Exception {
		assertEquals(NonceStatus.MISSING, gen.verify("", "1234", db, false, 0));
	}

	@Test
	public void unsolicited() throws Exception {
		assertEquals(NonceStatus.UNSOLICITED, gen.verify("1234", "", db, false, 0));
	}

	@Test
	public void invalidFormat() throws Exception {
		String sent = gen.createNonce(db, TS);
		int idx = sent.indexOf('-');
		String sig = sent.substring(idx, sent.length() - idx);
		assertEquals(NonceStatus.BAD,
				gen.verify(Long.toString(TS), sent, db, true, 100));
		assertEquals(NonceStatus.BAD, gen.verify(sig, sent, db, true, 100));
		assertEquals(NonceStatus.BAD, gen.verify("xxx-" + sig, sent, db, true, 100));
		assertEquals(NonceStatus.BAD, gen.verify(sent, "xxx-" + sig, db, true, 100));
	}

	@Test
	public void slop() throws Exception {
		String sent = gen.createNonce(db, TS - 10);
		String received = gen.createNonce(db, TS);
		assertEquals(NonceStatus.BAD,
				gen.verify(received, sent, db, false, 0));
		assertEquals(NonceStatus.BAD,
				gen.verify(received, sent, db, false, 11));
		assertEquals(NonceStatus.SLOP,
				gen.verify(received, sent, db, true, 0));
		assertEquals(NonceStatus.SLOP,
				gen.verify(received, sent, db, true, 9));
		assertEquals(NonceStatus.OK,
				gen.verify(received, sent, db, true, 10));
		assertEquals(NonceStatus.OK,
				gen.verify(received, sent, db, true, 11));
	}

	@Test
	public void ok() throws Exception {
		String sent = gen.createNonce(db, TS);
		assertEquals(NonceStatus.OK, gen.verify(sent, sent, db, false, 0));
	}

	@Test
	public void signedByDifferentKey() throws Exception {
		HMACSHA1NonceGenerator other = new HMACSHA1NonceGenerator("other");
		String sent = gen.createNonce(db, TS);
		String received = other.createNonce(db, TS);
		assertNotEquals(received, sent);
		assertEquals(NonceStatus.BAD,
				gen.verify(received, sent, db, false, 0));
	}

	@Test
	public void signedByDifferentKeyWithSlop() throws Exception {
		HMACSHA1NonceGenerator other = new HMACSHA1NonceGenerator("other");
		String sent = gen.createNonce(db, TS - 10);
		String received = other.createNonce(db, TS);
		assertEquals(NonceStatus.BAD, gen.verify(received, sent, db, true, 100));
	}
}
