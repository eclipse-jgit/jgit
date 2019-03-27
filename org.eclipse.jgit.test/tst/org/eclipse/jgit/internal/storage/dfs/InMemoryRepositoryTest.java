/*
 * Copyright (C) 2019, Google LLC.
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
package org.eclipse.jgit.internal.storage.dfs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Ref.Storage;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.junit.Test;

public class InMemoryRepositoryTest {

	@Test
	public void keepUpdateIndexPeelingTag() throws Exception {
		InMemoryRepository repo = new InMemoryRepository(
				new DfsRepositoryDescription());
		try (TestRepository<InMemoryRepository> git = new TestRepository<>(
				repo)) {
			RevCommit commit = git.branch("master").commit()
					.message("first commit").create();
			RevTag tag = git.tag("v0.1", commit);
			git.update("refs/tags/v0.1", tag);

			Ref unpeeledTag = new ObjectIdRef.Unpeeled(Storage.LOOSE,
					"refs/tags/v0.1", tag.getId(), 1000);

			Ref peeledTag = repo.getRefDatabase().peel(unpeeledTag);
			assertTrue(peeledTag instanceof ObjectIdRef.PeeledTag);
			assertEquals(1000, peeledTag.getUpdateIndex());
		}
	}

	@Test
	public void keepUpdateIndexPeelingNonTag() throws Exception {
		InMemoryRepository repo = new InMemoryRepository(
				new DfsRepositoryDescription());
		try (TestRepository<InMemoryRepository> git = new TestRepository<>(
				repo)) {
			RevCommit commit = git.branch("master").commit()
					.message("first commit").create();

			Ref unpeeledRef = new ObjectIdRef.Unpeeled(Storage.LOOSE,
					"refs/heads/master", commit.getId(), 1000);
			Ref peeledRef = repo.getRefDatabase().peel(unpeeledRef);
			assertTrue(peeledRef instanceof ObjectIdRef.PeeledNonTag);
			assertEquals(1000, peeledRef.getUpdateIndex());
		}
	}
}
