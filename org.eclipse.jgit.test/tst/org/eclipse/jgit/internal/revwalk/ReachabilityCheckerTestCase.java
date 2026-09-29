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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
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
		RevWalk walk = createWalkForReachabilityCheck();
		ReachabilityChecker checker = createReachabilityChecker(walk);

		assertReachable("reachable from one tip", //
				areAllReachable(List.of(a), Stream.of(c2), walk, checker));
		assertReachable("reachable from another tip", //
				areAllReachable(List.of(a), Stream.of(b2), walk, checker));
		assertReachable("reachable from itself", //
				areAllReachable(List.of(a), Stream.of(a), walk, checker));
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
		RevWalk walk = createWalkForReachabilityCheck();
		ReachabilityChecker checker = createReachabilityChecker(walk);

		assertReachable("reachable through one branch", //
				areAllReachable(List.of(b1), Stream.of(merge), walk, checker));
		assertReachable("reachable through another branch", //
				areAllReachable(List.of(c1), Stream.of(merge), walk, checker));
		assertReachable("reachable, before the branching", //
				areAllReachable(List.of(a), Stream.of(merge), walk, checker));
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

	@Test
	public void reachable_oldLongBranchAndRecentShortBranch() throws Exception {
		final int oldBranchLength = 10000;
		final int recentBranchLength = 3;

		RevCommit root = commit();
		RevCommit target = commit(root);

		// Long branch with old timestamps
		ObjectId oldTip = target;
		ObjectId oldBranchMiddle = null;
		for (int i = 0; i < oldBranchLength; i++) {
			oldTip = unparsedCommit(oldTip);

			if (i == (oldBranchLength / 2)) {
				oldBranchMiddle = oldTip;
			}
		}

		// Short branch with much more recent timestamps
		tick(1_000_000);
		ObjectId recentTip = target;
		for (int i = 0; i < recentBranchLength; i++) {
			recentTip = unparsedCommit(recentTip);
		}

		branch(oldTip, "old");
		branch(recentTip, "recent");

		initializeRevWalk();
		RevWalk walk = createWalkForReachabilityCheck();
		ReachabilityChecker checker = createReachabilityChecker(walk);

		List<AnyObjectId> targets = List.of(target);
		Stream<AnyObjectId> starters = Stream.of(oldTip, recentTip);

		long start = System.nanoTime();
		Optional<RevCommit> unreachable = areAllReachable(targets, starters,
				walk, checker);
		Duration duration = Duration.ofNanos(System.nanoTime() - start);

		assertTrue("Reachability check timed out",
				duration.compareTo(Duration.ofSeconds(5)) < 0);
		assertReachable("target is reachable from both tips", unreachable);

		if (!commitGraphEnabled()) {
			assertNull("Old branch is not parsed",
					walk.lookupCommit(oldBranchMiddle).getParents());
		}
	}

	private Optional<RevCommit> areAllReachable(Collection<AnyObjectId> targets,
			Stream<AnyObjectId> starters) throws Exception {
		RevWalk walk = createWalkForReachabilityCheck();
		ReachabilityChecker checker = createReachabilityChecker(walk);

		return areAllReachable(targets, starters, walk, checker);
	}

	private RevWalk createWalkForReachabilityCheck() {
		RevWalk walk = new RevWalk(rw.getObjectReader());
		walk.setRetainBody(false);
		return walk;
	}

	private Optional<RevCommit> areAllReachable(Collection<AnyObjectId> targets,
			Stream<AnyObjectId> starters, RevWalk walk,
			ReachabilityChecker checker) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		return checker.areAllReachable(
				targets.stream().map(walk::lookupCommit).toList(),
				starters.map(walk::lookupCommit));
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
