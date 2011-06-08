/*
 * Copyright (C) 2011, Google Inc.
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

package org.eclipse.jgit.storage.dht;

import static org.junit.Assert.assertEquals;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class ObjectIndexKeyTest {
	@Test
	public void testKey() {
		RepositoryKey repo = RepositoryKey.fromInt(0x41234567);
		ObjectId id = ObjectId
				.fromString("3e64b928d51b3a28e89cfe2a3f0eeae35ef07839");

		ObjectIndexKey key1 = ObjectIndexKey.create(repo, id);
		assertEquals(repo.asInt(), key1.getRepositoryId());
		assertEquals(key1, id);
		assertEquals("41234567.3e64b928d51b3a28e89cfe2a3f0eeae35ef07839",
				key1.asString());

		ObjectIndexKey key2 = ObjectIndexKey.fromBytes(key1.asBytes());
		assertEquals(repo.asInt(), key2.getRepositoryId());
		assertEquals(key2, id);
		assertEquals("41234567.3e64b928d51b3a28e89cfe2a3f0eeae35ef07839",
				key2.asString());

		ObjectIndexKey key3 = ObjectIndexKey.fromString(key1.asString());
		assertEquals(repo.asInt(), key3.getRepositoryId());
		assertEquals(key3, id);
		assertEquals("41234567.3e64b928d51b3a28e89cfe2a3f0eeae35ef07839",
				key3.asString());
	}
}
