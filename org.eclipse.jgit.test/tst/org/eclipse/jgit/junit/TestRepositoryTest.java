/*
 * Copyright (C) 2015, Google Inc.
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

package org.eclipse.jgit.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestRepositoryTest {
	private TestRepository<InMemoryRepository> tr;
	private InMemoryRepository repo;
	private RevWalk rw;

	@Before
	public void setUp() throws Exception {
		tr = new TestRepository<>(new InMemoryRepository(
				new DfsRepositoryDescription("test")));
		repo = tr.getRepository();
		rw = tr.getRevWalk();
	}

	@After
	public void tearDown() {
		rw.close();
		tr.getRepository().close();
	}

	@Test
	public void insertChangeId() throws Exception {
		RevCommit c1 = tr.commit().message("message").insertChangeId().create();
		rw.parseBody(c1);
		assertTrue(Pattern.matches(
				"^message\n\nChange-Id: I[0-9a-f]{40}\n$", c1.getFullMessage()));

		RevCommit c2 = tr.commit().message("").insertChangeId().create();
		rw.parseBody(c2);
		assertEquals("\n\nChange-Id: I0000000000000000000000000000000000000000\n",
				c2.getFullMessage());
	}

	@Test
	public void resetFromSymref() throws Exception {
		repo.updateRef("HEAD").link("refs/heads/master");
		Ref head = repo.getRef("HEAD");
		RevCommit master = tr.branch("master").commit().create();
		RevCommit branch = tr.branch("branch").commit().create();
		RevCommit detached = tr.commit().create();

		assertTrue(head.isSymbolic());
		assertEquals("refs/heads/master", head.getTarget().getName());
		assertEquals(master, repo.getRef("refs/heads/master").getObjectId());
		assertEquals(branch, repo.getRef("refs/heads/branch").getObjectId());

		// Reset to branches preserves symref.
		tr.reset("master");
		head = repo.getRef("HEAD");
		assertEquals(master, head.getObjectId());
		assertTrue(head.isSymbolic());
		assertEquals("refs/heads/master", head.getTarget().getName());

		tr.reset("branch");
		head = repo.getRef("HEAD");
		assertEquals(branch, head.getObjectId());
		assertTrue(head.isSymbolic());
		assertEquals("refs/heads/master", head.getTarget().getName());
		ObjectId lastHeadBeforeDetach = head.getObjectId().copy();

		// Reset to a SHA-1 detaches.
		tr.reset(detached);
		head = repo.getRef("HEAD");
		assertEquals(detached, head.getObjectId());
		assertFalse(head.isSymbolic());

		tr.reset(detached.name());
		head = repo.getRef("HEAD");
		assertEquals(detached, head.getObjectId());
		assertFalse(head.isSymbolic());

		// Reset back to a branch remains detached.
		tr.reset("master");
		head = repo.getRef("HEAD");
		assertEquals(lastHeadBeforeDetach, head.getObjectId());
		assertFalse(head.isSymbolic());
	}

	@Test
	public void resetFromDetachedHead() throws Exception {
		Ref head = repo.getRef("HEAD");
		RevCommit master = tr.branch("master").commit().create();
		RevCommit branch = tr.branch("branch").commit().create();
		RevCommit detached = tr.commit().create();

		assertNull(head);
		assertEquals(master, repo.getRef("refs/heads/master").getObjectId());
		assertEquals(branch, repo.getRef("refs/heads/branch").getObjectId());

		tr.reset("master");
		head = repo.getRef("HEAD");
		assertEquals(master, head.getObjectId());
		assertFalse(head.isSymbolic());

		tr.reset("branch");
		head = repo.getRef("HEAD");
		assertEquals(branch, head.getObjectId());
		assertFalse(head.isSymbolic());

		tr.reset(detached);
		head = repo.getRef("HEAD");
		assertEquals(detached, head.getObjectId());
		assertFalse(head.isSymbolic());

		tr.reset(detached.name());
		head = repo.getRef("HEAD");
		assertEquals(detached, head.getObjectId());
		assertFalse(head.isSymbolic());
	}
}
