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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.storage.dht.spi.memory.MemoryDatabase;
import org.junit.Before;
import org.junit.Test;

public class DhtRepositoryBuilderTest {
	private MemoryDatabase db;

	@Before
	public void setUpDatabase() {
		db = new MemoryDatabase();
	}

	@Test
	public void testCreateAndOpen() throws IOException {
		String name = "test.git";

		DhtRepository repo1 = db.open(name);
		assertSame(db, repo1.getDatabase());
		assertSame(repo1, repo1.getRefDatabase().getRepository());
		assertSame(repo1, repo1.getObjectDatabase().getRepository());

		assertEquals(name, repo1.getRepositoryName().asString());
		assertNull(repo1.getRepositoryKey());
		assertFalse(repo1.getObjectDatabase().exists());

		repo1.create(true);
		assertNotNull(repo1.getRepositoryKey());
		assertTrue(repo1.getObjectDatabase().exists());

		DhtRepository repo2 = db.open(name);
		assertNotNull(repo2.getRepositoryKey());
		assertTrue(repo2.getObjectDatabase().exists());
		assertEquals(0, repo2.getAllRefs().size());

		Ref HEAD = repo2.getRef(Constants.HEAD);
		assertTrue(HEAD.isSymbolic());
		assertEquals(Constants.R_HEADS + Constants.MASTER, //
				HEAD.getLeaf().getName());
	}
}
