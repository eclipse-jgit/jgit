/*
 * Copyright (C) 2019-2026, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.revwalk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.ReachabilityChecker;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevWalkWithAndWithoutCommitGraphTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public abstract class ReachabilityCheckerTestCase
		extends RevWalkWithAndWithoutCommitGraphTestCase {

	protected abstract ReachabilityChecker createReachabilityChecker(
			RevWalk revWalk) throws Exception;

	public ReachabilityCheckerTestCase(boolean withCommitGraph) {
		super(withCommitGraph);
	}

	@Test
	public void reachable() throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commit(a);
		RevCommit b2 = commit(b1);
		RevCommit c1 = commit(a);
		RevCommit c2 = commit(c1);
		branch(b2, "checker");

		initializeRevWalk();

		assertReachable("reachable from one tip", //
				List.of(a), Stream.of(c2));
		assertReachable("reachable from another tip", //
				List.of(a), Stream.of(b2));
		assertReachable("reachable from itself", //
				List.of(a), Stream.of(a));
	}

	@Test
	public void reachable_merge() throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commit(a);
		RevCommit b2 = commit(b1);
		RevCommit c1 = commit(a);
		RevCommit c2 = commit(c1);
		RevCommit merge = commit(c2, b2);
		branch(merge, "checker");

		initializeRevWalk();

		assertReachable("reachable through one branch", //
				List.of(b1), Stream.of(merge));
		assertReachable("reachable through another branch", //
				List.of(c1), Stream.of(merge));
		assertReachable("reachable, before the branching", //
				List.of(a), Stream.of(merge));
	}

	@Test
	public void unreachable_isLaterCommit() throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commit(a);
		RevCommit b2 = commit(b1);
		branch(b2, "checker");

		initializeRevWalk();

		assertUnreachable("unreachable from the future", //
				List.of(b2), Stream.of(b1));
	}

	@Test
	public void unreachable_differentBranch() throws Exception {
		RevCommit a = commit();
		RevCommit b1 = commit(a);
		RevCommit b2 = commit(b1);
		RevCommit c1 = commit(a);
		branch(b2, "checker");

		initializeRevWalk();

		assertUnreachable("unreachable from different branch", //
				List.of(c1), Stream.of(b2));
	}

	@Test
	public void reachable_longChain() throws Exception {
		RevCommit root = commit();
		ObjectId head = root;
		for (int i = 0; i < 10000; i++) {
			head = unparsedCommit(head);
		}
		branch(head, "master");

		initializeRevWalk();

		assertReachable("reachable with long chain in the middle", //
				List.of(root), Stream.of(head));
	}

	private Optional<RevCommit> areAllReachable(Collection<AnyObjectId> targets,
			Stream<AnyObjectId> starters) throws Exception {
		reinitializeRevWalk();
		RevWalk revWalk = new RevWalk(rw.getObjectReader());
		ReachabilityChecker checker = createReachabilityChecker(revWalk);

		return checker.areAllReachable(
				targets.stream().map(revWalk::lookupCommit).toList(),
				starters.map(revWalk::lookupCommit));
	}

	private void assertReachable(String msg, Collection<AnyObjectId> targets,
			Stream<AnyObjectId> starters) throws Exception {
		assertReachable(msg, areAllReachable(targets, starters));
	}

	private void assertUnreachable(String msg, Collection<AnyObjectId> targets,
			Stream<AnyObjectId> starters) throws Exception {
		assertUnreachable(msg, areAllReachable(targets, starters));
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
