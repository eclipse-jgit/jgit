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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

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
		RevCommit a = repo.commit().create();
		RevCommit b1 = repo.commit(a);
		RevCommit b2 = repo.commit(b1);
		RevCommit c1 = repo.commit(a);
		RevCommit c2 = repo.commit(c1);
		repo.update("refs/heads/checker", b2);

		ReachabilityChecker checker = getChecker(repo);

		assertReachable("reachable from one tip",
				checker.areAllReachable(Arrays.asList(a), Stream.of(c2)));
		assertReachable("reachable from another tip",
				checker.areAllReachable(Arrays.asList(a), Stream.of(b2)));
		assertReachable("reachable from itself",
				checker.areAllReachable(Arrays.asList(a), Stream.of(b2)));
	}

	@Test
	public void reachable_merge() throws Exception {
		RevCommit a = repo.commit().create();
		RevCommit b1 = repo.commit(a);
		RevCommit b2 = repo.commit(b1);
		RevCommit c1 = repo.commit(a);
		RevCommit c2 = repo.commit(c1);
		RevCommit merge = repo.commit(c2, b2);
		repo.update("refs/heads/checker", merge);

		ReachabilityChecker checker = getChecker(repo);

		assertReachable("reachable through one branch",
				checker.areAllReachable(Arrays.asList(b1),
						Stream.of(merge)));
		assertReachable("reachable through another branch",
				checker.areAllReachable(Arrays.asList(c1),
						Stream.of(merge)));
		assertReachable("reachable, before the branching",
				checker.areAllReachable(Arrays.asList(a),
						Stream.of(merge)));
	}

	@Test
	public void unreachable_isLaterCommit() throws Exception {
		RevCommit a = repo.commit().create();
		RevCommit b1 = repo.commit(a);
		RevCommit b2 = repo.commit(b1);
		repo.update("refs/heads/checker", b2);

		ReachabilityChecker checker = getChecker(repo);

		assertUnreachable("unreachable from the future",
				checker.areAllReachable(Arrays.asList(b2), Stream.of(b1)));
	}

	@Test
	public void unreachable_differentBranch() throws Exception {
		RevCommit a = repo.commit().create();
		RevCommit b1 = repo.commit(a);
		RevCommit b2 = repo.commit(b1);
		RevCommit c1 = repo.commit(a);
		repo.update("refs/heads/checker", b2);

		ReachabilityChecker checker = getChecker(repo);

		assertUnreachable("unreachable from different branch",
				checker.areAllReachable(Arrays.asList(c1), Stream.of(b2)));
	}

	@Test
	public void reachable_longChain() throws Exception {
		RevCommit root = repo.commit().create();
		RevCommit head = root;
		for (int i = 0; i < 10000; i++) {
			head = repo.commit(head);
		}
		repo.update("refs/heads/master", head);

		ReachabilityChecker checker = getChecker(repo);

		assertReachable("reachable with long chain in the middle", checker
				.areAllReachable(Arrays.asList(root), Stream.of(head)));
	}

	private static void assertReachable(String msg,
			Optional<RevCommit> result) {
		assertFalse(msg, result.isPresent());
	}

	private static void assertUnreachable(String msg,
			Optional<RevCommit> result) {
		assertTrue(msg, result.isPresent());
	}
}
