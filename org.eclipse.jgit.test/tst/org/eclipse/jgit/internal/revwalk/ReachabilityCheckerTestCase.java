/*
 * Copyright (C) 2019, Google LLC. and others
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

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.ReachabilityChecker;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class ReachabilityCheckerTestCase
		extends LocalDiskRepositoryTestCase {

	protected abstract ReachabilityChecker createReachabilityChecker(
			RevWalk revWalk) throws Exception;

	private final boolean commitGraphEnabled;

	TestRepository<FileRepository> repo;

	@Parameters(name = "[{index}] commitGraphEnabled: {0}")
	public static Object[] parameters() {
		return new Object[] { Boolean.FALSE, Boolean.TRUE };
	}

	public ReachabilityCheckerTestCase(boolean withCommitGraph) {
		this.commitGraphEnabled = withCommitGraph;
	}

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
		setCommitGraph(repo.getRepository(), commitGraphEnabled);


		assertReachable("reachable from one tip", List.of(a),
				Stream.of(c2));
		assertReachable("reachable from another tip", List.of(a),
				Stream.of(b2));
		assertReachable("reachable from itself", List.of(a),
				Stream.of(a));
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
		setCommitGraph(repo.getRepository(), commitGraphEnabled);

		assertReachable("reachable through one branch", List.of(b1),
				Stream.of(merge));
		assertReachable("reachable through another branch", List.of(c1),
				Stream.of(merge));
		assertReachable("reachable, before the branching", List.of(a),
				Stream.of(merge));
	}

	@Test
	public void unreachable_isLaterCommit() throws Exception {
		RevCommit a = repo.commit().create();
		RevCommit b1 = repo.commit(a);
		RevCommit b2 = repo.commit(b1);
		repo.update("refs/heads/checker", b2);
		setCommitGraph(repo.getRepository(), commitGraphEnabled);

		assertUnreachable("unreachable from the future", List.of(b2),
				Stream.of(b1));
	}

	@Test
	public void unreachable_differentBranch() throws Exception {
		RevCommit a = repo.commit().create();
		RevCommit b1 = repo.commit(a);
		RevCommit b2 = repo.commit(b1);
		RevCommit c1 = repo.commit(a);
		repo.update("refs/heads/checker", b2);
		setCommitGraph(repo.getRepository(), commitGraphEnabled);

		assertUnreachable("unreachable from different branch", List.of(c1),
				Stream.of(b2));
	}

	@Test
	public void reachable_longChain() throws Exception {
		RevCommit root = repo.commit().create();
		ObjectId head = root;
		for (int i = 0; i < 10000; i++) {
			head = repo.unparsedCommit(head);
		}
		repo.update("refs/heads/master", head);
		setCommitGraph(repo.getRepository(), commitGraphEnabled);

		assertReachable("reachable with long chain in the middle",
				List.of(root), Stream.of(head));
	}

	private static void setCommitGraph(FileRepository repository,
			boolean enabled) throws Exception {
		StoredConfig config = repository.getConfig();

		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_COMMIT_GRAPH, enabled);
		config.setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_WRITE_COMMIT_GRAPH, enabled);
		config.setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_WRITE_CHANGED_PATHS, enabled);

		if (enabled) {
			new GC(repository).gc().get();
		}

	}

	private Optional<RevCommit> areAllReachable(Collection<AnyObjectId> targets,
			Stream<AnyObjectId> starters) throws Exception {
		RevWalk walk = new RevWalk(repo.getRevWalk().getObjectReader());
		ReachabilityChecker checker = createReachabilityChecker(walk);

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
