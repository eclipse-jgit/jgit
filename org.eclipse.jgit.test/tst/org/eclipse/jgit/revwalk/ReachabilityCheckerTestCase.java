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
package org.eclipse.jgit.revwalk;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Before;
import org.junit.Test;

public abstract class ReachabilityCheckerTestCase
		extends LocalDiskRepositoryTestCase {

	protected abstract ReachabilityChecker getChecker(
			TestRepository<FileRepository> repository) throws Exception;

	TestRepository<FileRepository> repo;

	/** {@inheritDoc} */
	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		FileRepository db = createWorkRepository();
		repo = new TestRepository<>(db);
	}

	@Test
	public void reachable() throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commit(a);
		RevCommit b2 = commit(b1);
		RevCommit c1 = commit(a);
		RevCommit c2 = commit(c1);
		updateRef("refs/heads/checker", b2);

		ReachabilityChecker checker = getChecker(repo);

		assertThat("reachable from one tip",
				checker.isReachable(a, Arrays.asList(c2)));
		assertThat("reachable from another tip",
				checker.isReachable(a, Arrays.asList(b2)));
		assertThat("reachable from itself",
				checker.isReachable(a, Arrays.asList(b2)));
	}

	@Test
	public void reachable_merge() throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commit(a);
		RevCommit b2 = commit(b1);
		RevCommit c1 = commit(a);
		RevCommit c2 = commit(c1);
		RevCommit merge = commit(c2, b2);
		updateRef("refs/heads/checker", merge);

		ReachabilityChecker checker = getChecker(repo);

		assertThat("reachable through one branch",
				checker.isReachable(b1, Arrays.asList(merge)));
		assertThat("reachable through another branch",
				checker.isReachable(c1, Arrays.asList(merge)));
		assertThat("reachable, before the branching",
				checker.isReachable(a, Arrays.asList(merge)));
	}

	@Test
	public void unreachable_isLaterCommit() throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commit(a);
		RevCommit b2 = commit(b1);
		updateRef("refs/heads/checker", b2);

		ReachabilityChecker checker = getChecker(repo);

		assertFalse("unreachable from the future",
				checker.isReachable(b2, Arrays.asList(b1)));
	}

	@Test
	public void unreachable_differentBranch() throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commit(a);
		RevCommit b2 = commit(b1);
		RevCommit c1 = commit(a);
		updateRef("refs/heads/checker", b2);

		ReachabilityChecker checker = getChecker(repo);

		assertFalse("unreachable from different branch",
				checker.isReachable(c1, Arrays.asList(b2)));
	}

	@Test
	public void reachable_longChain() throws Exception {
		RevCommit root = commit();
		RevCommit head = root;
		for (int i = 0; i < 10000; i++) {
			head = repo.commit(head);
		}
		repo.update("refs/heads/master", head);

		ReachabilityChecker checker = getChecker(repo);

		assertTrue(checker.isReachable(root, Arrays.asList(head)));
	}

	private RevCommit commit(RevCommit... parents) throws Exception {
		return repo.commit(parents);
	}

	private void updateRef(String ref, RevCommit commit) throws Exception {
		repo.update(ref, commit);
	}
}
